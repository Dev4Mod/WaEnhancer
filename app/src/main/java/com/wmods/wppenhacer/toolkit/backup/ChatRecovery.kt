package com.wmods.wppenhacer.toolkit.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.wmods.wppenhacer.toolkit.importer.ChatImporter
import com.wmods.wppenhacer.toolkit.importer.ImportRules
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException

/**
 * Recupera un chat (o solo los mensajes que faltan) desde una copia antigua de msgstore.db.
 *
 * Se copian a la base actual los mensajes de la copia que ya no están (se comparan por key_id),
 * con sus adjuntos (si el archivo sigue en el teléfono), miniaturas y ubicación. Si el chat ya no
 * existe, se crea vacío antes.
 */
class ChatRecovery(private val currentDb: File, private val mediaRoot: File) {

    class BackupChat(val rowId: Long, val rawJid: String, val isGroup: Boolean, val subject: String?, val messages: Int)
    class Preview(val total: Int, val present: Int) { val toRestore get() = total - present }
    class Result(val restored: Int, val mediaMissing: Int, val chatCreated: Boolean)

    // ------------------------------------------------------------------ utilidades

    private fun openRead(f: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(f.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun openWrite(): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(currentDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.rawQuery("PRAGMA busy_timeout = 5000;", null).close()
        } catch (_: Exception) {
        }
        return db
    }

    private class Col(val name: String, val type: String, val notNull: Boolean, val default: String?, val pk: Boolean)

    private fun columnsOf(db: SQLiteDatabase, table: String): List<Col> {
        val out = ArrayList<Col>()
        try {
            db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                while (c.moveToNext()) {
                    out += Col(c.getString(1), c.getString(2) ?: "", c.getInt(3) == 1, c.getString(4), c.getInt(5) > 0)
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

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

    /** Identificador estable de un mensaje para saber si ya existe. */
    private fun idOf(keyId: String?, ts: Long, fromMe: Boolean, text: String?) =
        if (!keyId.isNullOrEmpty()) keyId else "$ts|$fromMe|$text"

    private fun readChat(db: SQLiteDatabase, rowId: Long): BackupChat {
        val hasSubject = columnsOf(db, "chat").any { it.name == "subject" }
        db.rawQuery(
            "SELECT c._id, j.raw_string, j.server, ${if (hasSubject) "c.subject" else "NULL"}, " +
                    "(SELECT COUNT(*) FROM message m WHERE m.chat_row_id = c._id) " +
                    "FROM chat c JOIN jid j ON j._id = c.jid_row_id WHERE c._id = ?", arrayOf(rowId.toString())
        ).use { c ->
            if (!c.moveToFirst()) throw IllegalArgumentException("El chat no existe en la copia")
            return BackupChat(c.getLong(0), c.getString(1) ?: "", c.getString(2) == "g.us", c.getString(3), c.getInt(4))
        }
    }

    /** JID del chat más los equivalentes (número ↔ LID) según el jid_map de ambas bases. */
    private fun jidCandidates(raw: String, vararg dbs: SQLiteDatabase): List<String> {
        val out = linkedSetOf(raw)
        for (db in dbs) {
            try {
                db.rawQuery(
                    "SELECT l.raw_string, p.raw_string FROM jid_map m " +
                            "JOIN jid l ON l._id = m.lid_row_id JOIN jid p ON p._id = m.jid_row_id " +
                            "WHERE l.raw_string = ? OR p.raw_string = ?", arrayOf(raw, raw)
                ).use { c ->
                    while (c.moveToNext()) {
                        c.getString(0)?.let { out += it }
                        c.getString(1)?.let { out += it }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out.toList()
    }

    private fun findCurrentChat(cur: SQLiteDatabase, candidates: List<String>): Long? {
        for (jid in candidates) {
            cur.rawQuery(
                "SELECT c._id FROM chat c JOIN jid j ON j._id = c.jid_row_id WHERE j.raw_string = ? LIMIT 1",
                arrayOf(jid)
            ).use { c -> if (c.moveToFirst()) return c.getLong(0) }
        }
        return null
    }

    // ------------------------------------------------------------------ consulta

    /** Chats de la copia con mensajes, del más al menos reciente. */
    fun listChats(backupDb: File): List<BackupChat> = openRead(backupDb).use { db ->
        val cols = columnsOf(db, "chat").map { it.name }.toSet()
        val order = if ("sort_timestamp" in cols) "ORDER BY c.sort_timestamp DESC" else ""
        val subject = if ("subject" in cols) "c.subject" else "NULL"
        val out = ArrayList<BackupChat>()
        db.rawQuery(
            "SELECT c._id, j.raw_string, j.server, $subject, " +
                    "(SELECT COUNT(*) FROM message m WHERE m.chat_row_id = c._id) FROM chat c " +
                    "JOIN jid j ON j._id = c.jid_row_id WHERE j.server NOT IN ('broadcast') $order", null
        ).use { c ->
            while (c.moveToNext()) {
                val n = c.getInt(4)
                if (n > 0) out += BackupChat(c.getLong(0), c.getString(1) ?: continue, c.getString(2) == "g.us", c.getString(3), n)
            }
        }
        out
    }

    private class Pending(val ids: LongArray, val timestamps: LongArray)

    /** Mensajes de la copia que no están en el chat actual. */
    private fun missing(bdb: SQLiteDatabase, cur: SQLiteDatabase, backupChat: Long, currentChat: Long?): Pending {
        val present = HashSet<String>()
        if (currentChat != null) {
            cur.rawQuery(
                "SELECT key_id, timestamp, from_me, text_data FROM message WHERE chat_row_id = ?",
                arrayOf(currentChat.toString())
            ).use { c ->
                while (c.moveToNext()) present += idOf(c.getString(0), c.getLong(1), c.getInt(2) != 0, c.getString(3))
            }
        }
        val ids = ArrayList<Long>()
        val ts = ArrayList<Long>()
        bdb.rawQuery(
            "SELECT _id, key_id, timestamp, from_me, text_data FROM message WHERE chat_row_id = ? " +
                    "ORDER BY timestamp, _id", arrayOf(backupChat.toString())
        ).use { c ->
            while (c.moveToNext()) {
                if (idOf(c.getString(1), c.getLong(2), c.getInt(3) != 0, c.getString(4)) in present) continue
                ids += c.getLong(0)
                ts += c.getLong(2)
            }
        }
        return Pending(ids.toLongArray(), ts.toLongArray())
    }

    fun preview(backupDb: File, backupChatRowId: Long): Preview = openRead(backupDb).use { bdb ->
        openRead(currentDb).use { cur ->
            val chat = readChat(bdb, backupChatRowId)
            val row = findCurrentChat(cur, jidCandidates(chat.rawJid, bdb, cur))
            Preview(chat.messages, chat.messages - missing(bdb, cur, backupChatRowId, row).ids.size)
        }
    }

    // ------------------------------------------------------------------ recuperación

    fun recover(
        backupDb: File,
        backupChatRowId: Long,
        progress: (done: Int, total: Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Result {
        val bdb = openRead(backupDb)
        try {
            val chat = readChat(bdb, backupChatRowId)
            val importer = ChatImporter(currentDb, mediaRoot)

            val existing = openRead(currentDb).use { cur ->
                findCurrentChat(cur, jidCandidates(chat.rawJid, bdb, cur))
            }
            val created = existing == null
            val targetRow = existing ?: importer.ensureChat(chat.rawJid, chat.subject)

            val db = openWrite()
            try {
                return copyMessages(bdb, db, chat, targetRow, created, progress, isCancelled)
            } catch (e: Throwable) {
                if (created) {
                    try {
                        db.execSQL(
                            "DELETE FROM chat WHERE _id = ? AND NOT EXISTS (SELECT 1 FROM message WHERE chat_row_id = ?)",
                            arrayOf(targetRow, targetRow)
                        )
                    } catch (_: Exception) {
                    }
                }
                throw e
            } finally {
                db.close()
            }
        } finally {
            bdb.close()
        }
    }

    private class Ctx(val bdb: SQLiteDatabase, val db: SQLiteDatabase) {
        val jidCache = HashMap<Long, Long>()
    }

    private fun copyMessages(
        bdb: SQLiteDatabase, db: SQLiteDatabase, chat: BackupChat, target: Long, created: Boolean,
        progress: (Int, Int) -> Unit, isCancelled: () -> Boolean
    ): Result {
        val msgCols = columnsOf(db, "message")
        val msgNames = msgCols.map { it.name }.toSet()
        val hasSort = "sort_id" in msgNames
        val pending = missing(bdb, db, chat.rowId, target)
        val n = pending.ids.size
        if (n == 0) return Result(0, 0, created)

        // Mensajes actuales del chat (para colocar los recuperados en su sitio) y sort_id globales.
        val curTs = ArrayList<Long>()
        val curSort = ArrayList<Long>()
        if (hasSort) {
            db.rawQuery(
                "SELECT timestamp, sort_id FROM message WHERE chat_row_id = ? ORDER BY timestamp, _id",
                arrayOf(target.toString())
            ).use { c -> while (c.moveToNext()) { curTs += c.getLong(0); curSort += c.getLong(1) } }
        }
        var globalMin: Long? = null
        var globalMax: Long? = null
        if (hasSort) {
            db.rawQuery("SELECT MIN(sort_id), MAX(sort_id) FROM message", null).use { c ->
                c.moveToFirst()
                if (!c.isNull(0)) globalMin = c.getLong(0)
                if (!c.isNull(1)) globalMax = c.getLong(1)
            }
        }
        val chatHasMessages = curTs.isNotEmpty()
        val firstTs = curTs.firstOrNull()
        val olderCount = if (chatHasMessages) pending.timestamps.count { it < firstTs!! } else 0
        val olderIds = if (hasSort && chatHasMessages) ImportRules.planSortIds(olderCount, true, globalMin, globalMax) else LongArray(0)
        val freshIds = if (hasSort && !chatHasMessages) ImportRules.planSortIds(n, false, globalMin, globalMax) else LongArray(0)
        var nextFallback = (globalMax ?: 0L) + 1 + n

        val mediaCols = columnsOf(db, "message_media")
        val ftsCols = columnsOf(db, "message_ftsv2").map { it.name }.toSet()
        var ftsEnabled = ftsCols.isNotEmpty()
        val chatRawJid = db.rawQuery(
            "SELECT j.raw_string FROM chat c JOIN jid j ON j._id = c.jid_row_id WHERE c._id = ?", arrayOf(target.toString())
        ).use { if (it.moveToFirst()) it.getString(0) else chat.rawJid }
        val ctx = Ctx(bdb, db)
        val related = listOf("message_thumbnail", "message_location").filter { columnsOf(db, it).any { c -> c.name == "message_row_id" } }

        var restored = 0
        var mediaMissing = 0
        var olderUsed = 0
        var lastRow = 0L
        var lastSort: Long? = null
        var lastTs = 0L
        db.beginTransaction()
        try {
            for (i in 0 until n) {
                if (isCancelled()) throw CancellationException()
                val oldId = pending.ids[i]
                val ts = pending.timestamps[i]
                val cv = bdb.rawQuery("SELECT * FROM message WHERE _id = ?", arrayOf(oldId.toString())).use {
                    if (it.moveToFirst()) cursorToValues(it) else null
                } ?: continue
                cv.remove("_id")
                for (k in cv.keySet().toList()) if (k !in msgNames) cv.remove(k)
                for (k in cv.keySet().toList()) {
                    if (k.endsWith("_row_id") && k != "chat_row_id" && k != "sender_jid_row_id") {
                        if (cv.get(k) is Long || cv.get(k) is Int) cv.put(k, 0L) else cv.putNull(k)
                    }
                }
                cv.put("chat_row_id", target)
                val sender = (cv.get("sender_jid_row_id") as? Number)?.toLong() ?: 0L
                if ("sender_jid_row_id" in msgNames) cv.put("sender_jid_row_id", if (sender > 0) mapJid(ctx, sender) else 0L)

                var sort: Long? = null
                if (hasSort) {
                    sort = when {
                        !chatHasMessages -> freshIds[i]
                        ts < firstTs!! -> olderIds[olderUsed++]
                        else -> {
                            var lo = 0; var hi = curTs.size
                            while (lo < hi) { val mid = (lo + hi) ushr 1; if (curTs[mid] <= ts) lo = mid + 1 else hi = mid }
                            curSort[lo - 1]
                        }
                    }
                    cv.put("sort_id", sort)
                }
                fillRequired(msgCols, cv)
                var newId = try {
                    db.insertOrThrow("message", null, cv)
                } catch (e: Exception) {
                    if (!hasSort) throw e
                    sort = nextFallback++
                    cv.put("sort_id", sort)
                    db.insertOrThrow("message", null, cv)
                }
                restored++
                if (ts >= lastTs) { lastRow = newId; lastSort = sort; lastTs = ts }

                // Adjunto
                if (mediaCols.isNotEmpty()) {
                    bdb.rawQuery("SELECT * FROM message_media WHERE message_row_id = ?", arrayOf(oldId.toString())).use { c ->
                        if (c.moveToFirst()) {
                            val mv = cursorToValues(c)
                            val path = mv.get("file_path") as? String
                            if (!path.isNullOrBlank() && !File(mediaRoot, path).isFile) mediaMissing++
                            mv.remove("_id")
                            for (k in mv.keySet().toList()) if (mediaCols.none { it.name == k }) mv.remove(k)
                            mv.put("message_row_id", newId)
                            if (mediaCols.any { it.name == "chat_row_id" }) mv.put("chat_row_id", target)
                            fillRequired(mediaCols, mv)
                            try { db.insertOrThrow("message_media", null, mv) } catch (_: Exception) {}
                        }
                    }
                }
                for (table in related) {
                    val tcols = columnsOf(db, table)
                    bdb.rawQuery("SELECT * FROM $table WHERE message_row_id = ?", arrayOf(oldId.toString())).use { c ->
                        if (c.moveToFirst()) {
                            val tv = cursorToValues(c)
                            tv.remove("_id")
                            for (k in tv.keySet().toList()) if (tcols.none { it.name == k }) tv.remove(k)
                            tv.put("message_row_id", newId)
                            if (tcols.any { it.name == "chat_row_id" }) tv.put("chat_row_id", target)
                            fillRequired(tcols, tv)
                            try { db.insertOrThrow(table, null, tv) } catch (_: Exception) {}
                        }
                    }
                }
                // Índice de búsqueda
                val text = cv.get("text_data") as? String
                if (ftsEnabled && !text.isNullOrEmpty()) {
                    try {
                        val fv = ContentValues()
                        fv.put("docid", newId)
                        if ("content" in ftsCols) fv.put("content", text)
                        if ("fts_jid" in ftsCols) fv.put("fts_jid", chatRawJid)
                        if ("fts_namespace" in ftsCols) fv.put("fts_namespace", "com.whatsapp")
                        db.insertOrThrow("message_ftsv2", null, fv)
                    } catch (_: Exception) {
                        ftsEnabled = false
                    }
                }
                if (restored % 100 == 0) progress(restored, n)
            }
            if (!chatHasMessages && lastRow > 0) updateChat(db, target, lastRow, lastSort, lastTs)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        progress(n, n)
        return Result(restored, mediaMissing, created)
    }

    /** Fila de `jid` en la base actual equivalente a la de la copia (se crea si no existe). */
    private fun mapJid(ctx: Ctx, backupRow: Long): Long = ctx.jidCache.getOrPut(backupRow) {
        var raw: String? = null
        var user: String? = null
        var server: String? = null
        ctx.bdb.rawQuery("SELECT raw_string, user, server FROM jid WHERE _id = ?", arrayOf(backupRow.toString())).use {
            if (it.moveToFirst()) { raw = it.getString(0); user = it.getString(1); server = it.getString(2) }
        }
        val r = raw ?: (if (user != null && server != null) "$user@$server" else return@getOrPut 0L)
        ctx.db.rawQuery("SELECT _id FROM jid WHERE raw_string = ? LIMIT 1", arrayOf(r)).use {
            if (it.moveToFirst()) return@getOrPut it.getLong(0)
        }
        val u = user ?: r.substringBefore('@')
        val s = server ?: r.substringAfter('@', "s.whatsapp.net")
        ctx.db.rawQuery("SELECT _id FROM jid WHERE user = ? AND server = ? LIMIT 1", arrayOf(u, s)).use {
            if (it.moveToFirst()) return@getOrPut it.getLong(0)
        }
        val cv = ctx.db.rawQuery("SELECT * FROM jid WHERE server = ? ORDER BY _id DESC LIMIT 1", arrayOf(s)).use {
            if (it.moveToFirst()) cursorToValues(it) else ContentValues()
        }
        cv.remove("_id")
        cv.put("user", u); cv.put("server", s); cv.put("raw_string", r)
        fillRequired(columnsOf(ctx.db, "jid"), cv)
        ctx.db.insertOrThrow("jid", null, cv)
    }

    /** Si el chat estaba vacío, apunta su "último mensaje" a lo recuperado. */
    private fun updateChat(db: SQLiteDatabase, chatRow: Long, lastRow: Long, lastSort: Long?, lastTs: Long) {
        val names = columnsOf(db, "chat").map { it.name }.toSet()
        val cv = ContentValues()
        for (c in listOf("display_message_row_id", "last_message_row_id", "last_important_message_row_id")) {
            if (c in names) cv.put(c, lastRow)
        }
        if (lastSort != null) for (c in listOf("display_message_sort_id", "last_message_sort_id")) {
            if (c in names) cv.put(c, lastSort)
        }
        if ("sort_timestamp" in names) cv.put("sort_timestamp", lastTs)
        if (cv.size() > 0) db.update("chat", cv, "_id = ?", arrayOf(chatRow.toString()))
    }
}
