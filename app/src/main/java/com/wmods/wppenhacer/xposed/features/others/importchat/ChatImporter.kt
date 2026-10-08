package com.wmods.wppenhacer.xposed.features.others.importchat

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inserta en msgstore.db de WhatsApp los mensajes de un chat exportado.
 *
 * No conoce de antemano el esquema de la base de datos (cambia entre versiones de WhatsApp): lee
 * las columnas reales con PRAGMA table_info y solo escribe las que existen. Para la fila de
 * `message` se parte de un mensaje real del mismo tipo (para heredar los valores que WhatsApp
 * espera) y se sobrescribe lo propio del mensaje importado.
 *
 * Reglas de seguridad:
 *  - Solo se importan mensajes anteriores al primero que ya tiene el chat (o todos si está vacío),
 *    de modo que ningún mensaje existente cambia de sitio ni se duplica.
 *  - Los archivos se copian antes de tocar la base de datos, y la base de datos se escribe en
 *    lotes cortos para no bloquear a WhatsApp. Si algo falla (o se cancela), los mensajes ya
 *    insertados se deshacen y los archivos copiados se borran.
 */
class ChatImporter(
    private val dbFile: File,
    /** Carpeta raíz de WhatsApp en Android/media (la que contiene "Media/"). */
    private val mediaRoot: File,
    private val probe: MediaProbe = AndroidMediaProbe
) {

    data class ChatInfo(
        val rowId: Long,
        val rawJid: String,
        val isGroup: Boolean,
        val subject: String?
    )

    class Options(
        val chatRowId: Long,
        /** Remitente del TXT que eres tú (sus mensajes se importan como enviados). */
        val meName: String,
        val importMedia: Boolean,
        val backup: Boolean
    )

    class Result(
        val imported: Int,
        val importedMedia: Int,
        /** Adjuntos que no se pudieron copiar y se importaron como texto. */
        val mediaFailed: Int,
        /** Mensajes que no se importaron por no ser anteriores al primero del chat. */
        val skippedNotOlder: Int,
        val backupFile: File?
    )

    enum class Phase { BACKUP, MEDIA, MESSAGES }

    fun interface Progress {
        fun update(phase: Phase, done: Int, total: Int)
    }

    enum class Code {
        DB_MISSING, CHAT_NOT_FOUND, NOTHING_TO_IMPORT, NO_SPACE_DB, NO_SPACE_MEDIA, CANCELLED, DB_ERROR
    }

    class ImportException(val code: Code, message: String, cause: Throwable? = null) :
        Exception(message, cause)

    // ------------------------------------------------------------------ lectura

    /** Chats que se pueden elegir como destino (sin estados ni listas de difusión). */
    fun listChats(): List<ChatInfo> {
        if (!dbFile.exists()) throw ImportException(Code.DB_MISSING, "No existe msgstore.db")
        val chatCols = openRead().use { db -> columnsOf(db, "chat").map { it.name }.toSet() }
        val order = if ("sort_timestamp" in chatCols) "ORDER BY c.sort_timestamp DESC" else ""
        val subject = if ("subject" in chatCols) "c.subject" else "NULL"
        val out = ArrayList<ChatInfo>()
        openRead().use { db ->
            db.rawQuery(
                "SELECT c._id, j.raw_string, j.server, $subject FROM chat c " +
                        "JOIN jid j ON j._id = c.jid_row_id " +
                        "WHERE j.server NOT IN ('broadcast') $order", null
            ).use { c ->
                while (c.moveToNext()) {
                    out += ChatInfo(
                        c.getLong(0), c.getString(1) ?: continue,
                        c.getString(2) == "g.us", c.getString(3)
                    )
                }
            }
        }
        return out
    }

    private fun openRead(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun openWrite(): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.rawQuery("PRAGMA busy_timeout = 5000;", null).close()
        } catch (_: Exception) {
        }
        return db
    }

    // ------------------------------------------------------------------ importación

    private class Prepared(
        val msg: ParsedMessage,
        val fromMe: Boolean,
        var kind: MediaKind?,
        var relPath: String?,
        var file: File?,
        var info: MediaInfo?
    )

    fun import(
        chat: ParsedChat,
        source: ChatImportSource?,
        options: Options,
        progress: Progress = Progress { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Result {
        if (!dbFile.exists()) throw ImportException(Code.DB_MISSING, "No existe msgstore.db")

        val state = inspect(options.chatRowId)
        val info = state.chat
        val chatHasMessages = state.hasMessages
        val firstTimestamp = state.firstTimestamp
        val globalMin = state.globalMinSort
        val globalMax = state.globalMaxSort

        val candidates = chat.messages.filter {
            !chatHasMessages || firstTimestamp == null || it.timestamp < firstTimestamp
        }
        val skipped = chat.messages.size - candidates.size
        if (candidates.isEmpty()) {
            throw ImportException(
                Code.NOTHING_TO_IMPORT,
                if (skipped > 0) "Todos los mensajes son posteriores al primero del chat"
                else "No hay mensajes que importar"
            )
        }

        val prepared = candidates.map {
            Prepared(it, it.sender == options.meName, null, null, null, null)
        }

        var backup: File? = null
        val createdFiles = ArrayList<File>()
        try {
            if (options.backup) backup = backupDatabase(progress, isCancelled)
            if (options.importMedia && source != null) {
                copyMedia(prepared, source, progress, isCancelled, createdFiles)
            }
            val sortIds = ImportRules.planSortIds(prepared.size, chatHasMessages, globalMin, globalMax)
            val (importedCount, mediaCount, mediaFailed) =
                insertAll(info, prepared, sortIds, !chatHasMessages, options, progress, isCancelled)
            return Result(importedCount, mediaCount, mediaFailed, skipped, backup)
        } catch (e: Exception) {
            createdFiles.forEach { it.delete() }
            throw e
        }
    }

    private class ChatState(
        val chat: ChatInfo,
        val hasMessages: Boolean,
        val firstTimestamp: Long?,
        val globalMinSort: Long?,
        val globalMaxSort: Long?
    )

    /** Lee el estado actual del chat destino y del conjunto de mensajes. */
    private fun inspect(chatRowId: Long): ChatState = openRead().use { db ->
        val chat = findChat(db, chatRowId)
            ?: throw ImportException(Code.CHAT_NOT_FOUND, "El chat elegido ya no existe")
        val msgCols = columnsOf(db, "message").map { it.name }.toSet()
        var has = false
        var first: Long? = null
        db.rawQuery(
            "SELECT COUNT(*), MIN(timestamp) FROM message WHERE chat_row_id = ?",
            arrayOf(chatRowId.toString())
        ).use { c ->
            c.moveToFirst()
            has = c.getLong(0) > 0
            first = if (c.isNull(1)) null else c.getLong(1)
        }
        var gMin: Long? = null
        var gMax: Long? = null
        if ("sort_id" in msgCols) {
            db.rawQuery("SELECT MIN(sort_id), MAX(sort_id) FROM message", null).use { c ->
                c.moveToFirst()
                gMin = if (c.isNull(0)) null else c.getLong(0)
                gMax = if (c.isNull(1)) null else c.getLong(1)
            }
        }
        ChatState(chat, has, first, gMin, gMax)
    }

    private fun findChat(db: SQLiteDatabase, rowId: Long): ChatInfo? {
        val hasSubject = columnsOf(db, "chat").any { it.name == "subject" }
        db.rawQuery(
            "SELECT c._id, j.raw_string, j.server, ${if (hasSubject) "c.subject" else "NULL"} " +
                    "FROM chat c JOIN jid j ON j._id = c.jid_row_id WHERE c._id = ?",
            arrayOf(rowId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            return ChatInfo(c.getLong(0), c.getString(1) ?: "", c.getString(2) == "g.us", c.getString(3))
        }
    }

    // ------------------------------------------------------------------ copia de seguridad

    private fun backupDatabase(progress: Progress, isCancelled: () -> Boolean): File {
        val dir = dbFile.parentFile!!
        val needed = dbFile.length() + dbFile.length() / 10
        if (dir.usableSpace < needed) {
            throw ImportException(Code.NO_SPACE_DB, "No hay espacio para la copia de seguridad")
        }
        openWrite().use { db ->
            try {
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).close()
            } catch (_: Exception) {
            }
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val target = File(dir, "${dbFile.name}.pre-import-$stamp.bak")
        val total = dbFile.length()
        var copied = 0L
        dbFile.inputStream().use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(1 shl 20)
                while (true) {
                    if (isCancelled()) {
                        target.delete()
                        throw ImportException(Code.CANCELLED, "Cancelado")
                    }
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    copied += n
                    progress.update(Phase.BACKUP, (copied * 100 / maxOf(total, 1)).toInt(), 100)
                }
            }
        }
        // Solo se conservan las dos últimas copias para no llenar el almacenamiento.
        dir.listFiles { f -> f.name.startsWith("${dbFile.name}.pre-import-") && f.name.endsWith(".bak") }
            ?.sortedByDescending { it.name }?.drop(2)?.forEach { it.delete() }
        return target
    }

    // ------------------------------------------------------------------ adjuntos

    private fun copyMedia(
        prepared: List<Prepared>, source: ChatImportSource, progress: Progress,
        isCancelled: () -> Boolean, created: MutableList<File>
    ) {
        val withMedia = prepared.filter { it.msg.attachmentName != null }
        if (withMedia.isEmpty()) return

        val totalBytes = withMedia.sumOf { maxOf(source.mediaSize(it.msg.attachmentName!!), 0L) }
        mediaRoot.mkdirs()
        if (mediaRoot.usableSpace < totalBytes + (50L shl 20)) {
            throw ImportException(Code.NO_SPACE_MEDIA, "No hay espacio para los adjuntos")
        }

        var done = 0
        for (p in withMedia) {
            if (isCancelled()) throw ImportException(Code.CANCELLED, "Cancelado")
            val name = p.msg.attachmentName!!
            val kind = ImportRules.kindFor(name)
            val wantedRel = ImportRules.relativeMediaPath(kind, name, p.fromMe, p.msg.timestamp)
            var part: File? = null
            try {
                val size = source.mediaSize(name)
                val (dest, rel, alreadyThere) = uniqueTarget(wantedRel, size)
                if (!alreadyThere) {
                    dest.parentFile?.mkdirs()
                    part = File(dest.parentFile, dest.name + ".part")
                    source.openMedia(name)!!.use { input ->
                        part.outputStream().use { input.copyTo(it) }
                    }
                    if (!part.renameTo(dest)) throw java.io.IOException("No se pudo crear ${dest.name}")
                    dest.setLastModified(p.msg.timestamp)
                    created += dest
                }
                p.kind = kind
                p.relPath = rel
                p.file = dest
                p.info = try {
                    probe.probe(dest, kind)
                } catch (_: Throwable) {
                    MediaInfo()
                }
            } catch (_: Exception) {
                // Se importará como texto con el nombre del archivo.
                part?.delete()
                p.kind = null
            }
            done++
            progress.update(Phase.MEDIA, done, withMedia.size)
        }
    }

    /** Evita pisar un archivo distinto que ya exista con ese nombre. */
    private fun uniqueTarget(relPath: String, size: Long): Triple<File, String, Boolean> {
        var rel = relPath
        var n = 1
        while (true) {
            val f = File(mediaRoot, rel)
            if (!f.exists()) return Triple(f, rel, false)
            if (size >= 0 && f.length() == size) return Triple(f, rel, true)
            val dot = relPath.lastIndexOf('.')
            val base = if (dot > relPath.lastIndexOf('/')) relPath.substring(0, dot) else relPath
            val ext = if (dot > relPath.lastIndexOf('/')) relPath.substring(dot) else ""
            rel = "$base ($n)$ext"
            n++
        }
    }

    // ------------------------------------------------------------------ base de datos

    private class Col(
        val name: String, val type: String, val notNull: Boolean,
        val default: String?, val pk: Boolean
    )

    private fun columnsOf(db: SQLiteDatabase, table: String): List<Col> {
        val out = ArrayList<Col>()
        try {
            db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                while (c.moveToNext()) {
                    out += Col(
                        c.getString(1), c.getString(2) ?: "", c.getInt(3) == 1,
                        c.getString(4), c.getInt(5) > 0
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Rellena con ceros las columnas NOT NULL sin valor por defecto que no se han informado. */
    private fun fillRequired(cols: List<Col>, cv: ContentValues) {
        for (c in cols) {
            if (c.pk || !c.notNull || c.default != null || cv.containsKey(c.name)) continue
            val t = c.type.uppercase(Locale.ROOT)
            when {
                t.contains("INT") -> cv.put(c.name, 0L)
                t.contains("REAL") || t.contains("FLOA") || t.contains("DOUB") -> cv.put(c.name, 0.0)
                t.contains("BLOB") -> cv.put(c.name, ByteArray(0))
                else -> cv.put(c.name, "")
            }
        }
    }

    private fun cursorToValues(c: Cursor): ContentValues {
        val cv = ContentValues()
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> cv.putNull(name)
                Cursor.FIELD_TYPE_INTEGER -> cv.put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> cv.put(name, c.getDouble(i))
                Cursor.FIELD_TYPE_BLOB -> cv.put(name, c.getBlob(i))
                else -> cv.put(name, c.getString(i))
            }
        }
        return cv
    }

    private class Template(val values: ContentValues, val sameChat: Boolean)

    /** Mensaje real del mismo tipo y dirección, preferentemente del propio chat. */
    private fun findTemplate(db: SQLiteDatabase, chatRowId: Long, fromMe: Boolean, type: Int): Template? {
        val args = arrayOf(chatRowId.toString(), if (fromMe) "1" else "0", type.toString())
        db.rawQuery(
            "SELECT * FROM message WHERE chat_row_id = ? AND from_me = ? AND message_type = ? " +
                    "ORDER BY _id DESC LIMIT 1", args
        ).use { c -> if (c.moveToFirst()) return Template(cursorToValues(c), true) }
        db.rawQuery(
            "SELECT * FROM message WHERE from_me = ? AND message_type = ? ORDER BY _id DESC LIMIT 1",
            arrayOf(args[1], args[2])
        ).use { c -> if (c.moveToFirst()) return Template(cursorToValues(c), false) }
        return null
    }

    /** Pone a cero lo que no debe heredarse de un mensaje ajeno (reenviado, efímero, destacado...). */
    private fun sanitize(cv: ContentValues) {
        val zero = listOf(
            "starred", "message_add_on_flags", "origination_flags", "broadcast", "recipient_count"
        )
        for (key in cv.keySet().toList()) {
            val lower = key.lowercase(Locale.ROOT)
            val reset = key in zero || lower.contains("ephemeral") || lower.contains("expir") ||
                    lower.contains("forward")
            if (!reset) continue
            if (cv.get(key) is Long || cv.get(key) is Int) cv.put(key, 0L) else cv.putNull(key)
        }
    }

    private class Env(
        val db: SQLiteDatabase,
        val chat: ChatInfo,
        val msgCols: List<Col>,
        val mediaCols: List<Col>,
        val thumbCols: List<Col>,
        val ftsCols: Set<String>,
        val chatCols: List<Col>
    ) {
        val msgNames = msgCols.map { it.name }.toSet()
        val templates = HashMap<Pair<Boolean, Int>, Template?>()
        var ftsEnabled = ftsCols.isNotEmpty()
        val participantCache = HashMap<String, Long?>()
        var groupUsesLid: Boolean? = null
        var knownIncomingSender: Long? = null
    }

    private fun insertAll(
        chat: ChatInfo, items: List<Prepared>, sortIds: LongArray, chatWasEmpty: Boolean,
        options: Options, progress: Progress, isCancelled: () -> Boolean
    ): Triple<Int, Int, Int> {
        val db = openWrite()
        var maxIdBefore = 0L
        var insertedAny = false
        try {
            val env = Env(
                db, chat,
                columnsOf(db, "message"), columnsOf(db, "message_media"),
                columnsOf(db, "message_thumbnail"),
                columnsOf(db, "message_ftsv2").map { it.name }.toSet(),
                columnsOf(db, "chat")
            )
            db.rawQuery("SELECT COALESCE(MAX(_id), 0) FROM message", null).use {
                it.moveToFirst(); maxIdBefore = it.getLong(0)
            }
            if (!chat.isGroup) env.knownIncomingSender = lastIncomingSender(db, chat.rowId)

            var imported = 0
            var media = 0
            var mediaFailed = 0
            var lastRowId = 0L
            var lastSort: Long? = null
            var lastTs = 0L

            for (batch in items.indices.chunked(BATCH)) {
                if (isCancelled()) throw ImportException(Code.CANCELLED, "Cancelado")
                db.beginTransaction()
                try {
                    insertedAny = true
                    for (i in batch) {
                        val p = items[i]
                        val result = insertOne(env, p, sortIds.getOrNull(i).takeIf { "sort_id" in env.msgNames })
                        lastRowId = result.rowId
                        lastSort = result.sortId
                        lastTs = p.msg.timestamp
                        imported++
                        if (result.mediaInserted) media++
                        else if (p.msg.attachmentName != null) mediaFailed++
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                progress.update(Phase.MESSAGES, imported, items.size)
            }

            if (chatWasEmpty && lastRowId > 0) {
                db.beginTransaction()
                try {
                    updateChat(env, lastRowId, lastSort, lastTs)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            return Triple(imported, media, mediaFailed)
        } catch (e: Exception) {
            if (insertedAny) undo(db, chat.rowId, maxIdBefore)
            if (e is ImportException) throw e
            throw ImportException(Code.DB_ERROR, e.message ?: e.javaClass.simpleName, e)
        } finally {
            db.close()
        }
    }

    /** Deshace lo insertado por esta importación (claves WAEI... posteriores al estado inicial). */
    private fun undo(db: SQLiteDatabase, chatRowId: Long, maxIdBefore: Long) {
        try {
            db.beginTransaction()
            try {
                val sel = "SELECT _id FROM message WHERE chat_row_id = ? AND _id > ? AND key_id LIKE 'WAEI%'"
                val args = arrayOf(chatRowId.toString(), maxIdBefore.toString())
                for (table in listOf("message_media", "message_thumbnail")) {
                    try {
                        db.execSQL("DELETE FROM $table WHERE message_row_id IN ($sel)", args)
                    } catch (_: Exception) {
                    }
                }
                try {
                    db.execSQL("DELETE FROM message_ftsv2 WHERE docid IN ($sel)", args)
                } catch (_: Exception) {
                }
                db.execSQL(
                    "DELETE FROM message WHERE chat_row_id = ? AND _id > ? AND key_id LIKE 'WAEI%'", args
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
        }
    }

    private fun lastIncomingSender(db: SQLiteDatabase, chatRowId: Long): Long? {
        db.rawQuery(
            "SELECT sender_jid_row_id FROM message WHERE chat_row_id = ? AND from_me = 0 " +
                    "ORDER BY _id DESC LIMIT 1", arrayOf(chatRowId.toString())
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0) }
        return null
    }

    private class Inserted(val rowId: Long, val sortId: Long?, val mediaInserted: Boolean)

    private fun insertOne(env: Env, p: Prepared, sortId: Long?): Inserted {
        val db = env.db
        val m = p.msg
        val kind = p.kind
        val type = kind?.messageType ?: 0
        var template = templateFor(env, p.fromMe, type)
        // Sin mensaje del que copiar la estructura, un adjunto se importa como texto.
        val asMedia = kind != null && template != null && env.mediaCols.isNotEmpty()
        val finalType = if (asMedia) type else 0
        if (!asMedia) template = templateFor(env, p.fromMe, 0)

        var text = m.text
        if (!asMedia && m.attachmentName != null) {
            text = if (text.isBlank()) "📎 ${m.attachmentName}" else "📎 ${m.attachmentName}\n$text"
        }
        var senderRow = 0L
        if (!p.fromMe) {
            if (env.chat.isGroup) {
                senderRow = resolveParticipant(env, m.sender) ?: 0L
                if (senderRow == 0L) text = "[${m.sender}] $text"
            } else {
                senderRow = if (template?.sameChat == true) {
                    (template.values.get("sender_jid_row_id") as? Long) ?: env.knownIncomingSender ?: 0L
                } else env.knownIncomingSender ?: 0L
            }
        }

        val cv = ContentValues()
        template?.let {
            cv.putAll(it.values)
            cv.remove("_id")
            sanitize(cv)
        }
        val ts = m.timestamp
        val key = ImportRules.messageKey(
            env.chat.rowId, ts, p.fromMe, m.sender, m.text, m.attachmentName
        )
        fun put(name: String, v: Any?) {
            if (name !in env.msgNames) return
            when (v) {
                null -> cv.putNull(name)
                is Long -> cv.put(name, v)
                is Int -> cv.put(name, v.toLong())
                is String -> cv.put(name, v)
                else -> error("tipo no soportado")
            }
        }
        put("chat_row_id", env.chat.rowId)
        put("from_me", if (p.fromMe) 1L else 0L)
        put("key_id", key)
        put("sender_jid_row_id", senderRow)
        put("status", if (p.fromMe) 13L else 0L)
        put("timestamp", ts)
        put("received_timestamp", ts)
        put("receipt_server_timestamp", ts)
        put("message_type", finalType.toLong())
        put("text_data", text.ifEmpty { null })
        put("starred", 0L)
        if (sortId != null) put("sort_id", sortId)
        fillRequired(env.msgCols, cv)

        val rowId = db.insertOrThrow("message", null, cv)

        if (text.isNotEmpty()) insertFts(env, rowId, text)

        var mediaInserted = false
        if (asMedia && p.relPath != null && p.file != null) {
            insertMedia(env, rowId, p, kind)
            mediaInserted = true
        }
        return Inserted(rowId, sortId, mediaInserted)
    }

    private fun templateFor(env: Env, fromMe: Boolean, type: Int): Template? =
        env.templates.getOrPut(fromMe to type) { findTemplate(env.db, env.chat.rowId, fromMe, type) }

    private fun insertMedia(env: Env, rowId: Long, p: Prepared, kind: MediaKind) {
        val file = p.file!!
        val info = p.info ?: MediaInfo()
        val names = env.mediaCols.map { it.name }.toSet()
        val cv = ContentValues()
        fun put(name: String, v: Any?) {
            if (name !in names) return
            when (v) {
                null -> cv.putNull(name)
                is Long -> cv.put(name, v)
                is Int -> cv.put(name, v.toLong())
                is String -> cv.put(name, v)
                else -> error("tipo no soportado")
            }
        }
        put("message_row_id", rowId)
        put("chat_row_id", env.chat.rowId)
        put("file_path", p.relPath)
        put("file_size", file.length())
        put("file_length", file.length())
        put("mime_type", ImportRules.mimeFor(file.name))
        put("media_name", if (kind == MediaKind.DOCUMENT) file.name else null)
        put("width", info.width)
        put("height", info.height)
        put("media_duration", info.durationSec)
        put("media_caption", p.msg.text.ifEmpty { null })
        put("transferred", 1L)
        put("autotransfer_retry_enabled", 0L)
        fillRequired(env.mediaCols, cv)
        env.db.insertOrThrow("message_media", null, cv)

        val thumb = info.thumbnail
        if (kind.hasThumbnail && thumb != null && env.thumbCols.isNotEmpty()) {
            val tv = ContentValues()
            val tn = env.thumbCols.map { it.name }.toSet()
            if ("message_row_id" in tn && "thumbnail" in tn) {
                tv.put("message_row_id", rowId)
                tv.put("thumbnail", thumb)
                fillRequired(env.thumbCols, tv)
                try {
                    env.db.insertOrThrow("message_thumbnail", null, tv)
                } catch (_: Exception) {
                    // Sin miniatura WhatsApp la genera al abrir el chat.
                }
            }
        }
    }

    /** Índice de búsqueda; es opcional, si falla (p. ej. tokenizador propio) se deja de intentar. */
    private fun insertFts(env: Env, rowId: Long, text: String) {
        if (!env.ftsEnabled) return
        try {
            val cv = ContentValues()
            cv.put("docid", rowId)
            if ("content" in env.ftsCols) cv.put("content", text)
            if ("fts_jid" in env.ftsCols) cv.put("fts_jid", env.chat.rawJid)
            if ("fts_namespace" in env.ftsCols) cv.put("fts_namespace", "com.whatsapp")
            env.db.insertOrThrow("message_ftsv2", null, cv)
        } catch (_: Exception) {
            env.ftsEnabled = false
        }
    }

    /** Fila de `jid` del participante de un grupo a partir del número que aparece en el TXT. */
    private fun resolveParticipant(env: Env, speaker: String): Long? {
        val digits = ChatTxtParser.phoneDigits(speaker) ?: return null
        return env.participantCache.getOrPut(digits) {
            val db = env.db
            var phoneRow: Long? = null
            db.rawQuery(
                "SELECT _id FROM jid WHERE user = ? AND server = 's.whatsapp.net' LIMIT 1",
                arrayOf(digits)
            ).use { c -> if (c.moveToFirst()) phoneRow = c.getLong(0) }
            val phone = phoneRow ?: return@getOrPut null

            if (env.groupUsesLid == null) {
                env.groupUsesLid = false
                try {
                    db.rawQuery(
                        "SELECT j.server FROM message m JOIN jid j ON j._id = m.sender_jid_row_id " +
                                "WHERE m.chat_row_id = ? AND m.from_me = 0 AND m.sender_jid_row_id > 0 " +
                                "ORDER BY m._id DESC LIMIT 1", arrayOf(env.chat.rowId.toString())
                    ).use { c -> if (c.moveToFirst()) env.groupUsesLid = c.getString(0) == "lid" }
                } catch (_: Exception) {
                }
            }
            if (env.groupUsesLid == true) {
                try {
                    db.rawQuery(
                        "SELECT lid_row_id FROM jid_map WHERE jid_row_id = ? LIMIT 1",
                        arrayOf(phone.toString())
                    ).use { c -> if (c.moveToFirst()) return@getOrPut c.getLong(0) }
                } catch (_: Exception) {
                }
                return@getOrPut null
            }
            phone
        }
    }

    /** Si el chat estaba vacío, apunta sus "último mensaje" a lo importado. */
    private fun updateChat(env: Env, lastRowId: Long, lastSortId: Long?, lastTs: Long) {
        val names = env.chatCols.map { it.name }.toSet()
        val cv = ContentValues()
        for (c in listOf("display_message_row_id", "last_message_row_id", "last_important_message_row_id")) {
            if (c in names) cv.put(c, lastRowId)
        }
        if (lastSortId != null) {
            for (c in listOf("display_message_sort_id", "last_message_sort_id")) {
                if (c in names) cv.put(c, lastSortId)
            }
        }
        if ("sort_timestamp" in names) cv.put("sort_timestamp", lastTs)
        if (cv.size() > 0) {
            env.db.update("chat", cv, "_id = ?", arrayOf(env.chat.rowId.toString()))
        }
    }

    private companion object {
        const val BATCH = 500
    }
}
