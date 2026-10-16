package com.wmods.wppenhacer.toolkit.deleted

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Lee de msgstore.db los mensajes que se borraron "para todos" y quién lo hizo.
 *
 * - Chat individual: solo el autor puede borrar su mensaje para todos.
 * - Grupo: lo borra el autor o un administrador; WhatsApp guarda el administrador en
 *   `message_revoked.admin_jid_row_id`.
 *
 * Los borrados "solo para mí" no dejan rastro y no se pueden detectar.
 */
class DeletedMessagesReader(private val dbFile: File) {

    enum class Source {
        /** Tabla `message_revoked` (trae la fecha del borrado y el administrador). */
        REVOKED_TABLE,

        /** Alternativa: mensajes marcados como borrados en `message` (sin fecha ni administrador). */
        MESSAGE_TYPE,

        /** No se encontró ningún dato de borrados. */
        NONE
    }

    data class Entry(
        val messageRowId: Long,
        val chatRowId: Long,
        val chatJid: String,
        val chatIsGroup: Boolean,
        val chatSubject: String?,
        /** El mensaje era tuyo. */
        val fromMe: Boolean,
        /** JID del autor; null si es tuyo o no se conoce. */
        val authorJid: String?,
        /** JID del administrador que lo borró, si no lo borró el autor. */
        val adminJid: String?,
        val sentAt: Long,
        /** Momento del borrado, si WhatsApp lo guardó. */
        val deletedAt: Long?,
        val keyId: String? = null,
        /** Posición del mensaje en el chat (para abrirlo en esa posición). */
        val sortId: Long? = null,
        /** Texto original, si se pudo recuperar (registro de WaImport o índice de búsqueda). */
        val text: String? = null
    ) {
        val deletedByAdmin: Boolean get() = adminJid != null && adminJid != authorJid
    }

    class Result(val entries: List<Entry>, val source: Source)

    private fun openRead(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun columns(db: SQLiteDatabase, table: String): Set<String> {
        val out = HashSet<String>()
        try {
            db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                while (c.moveToNext()) c.getString(1)?.let { out += it }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Para los chats que WhatsApp guarda con LID: LID → teléfono del mismo contacto. */
    fun phoneByLid(): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            openRead().use { db ->
                db.rawQuery(
                    "SELECT l.raw_string, p.raw_string FROM jid_map m " +
                            "JOIN jid l ON l._id = m.lid_row_id JOIN jid p ON p._id = m.jid_row_id", null
                ).use { c ->
                    while (c.moveToNext()) {
                        val lid = c.getString(0) ?: continue
                        val phone = c.getString(1) ?: continue
                        out[lid] = phone
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Los borrados más recientes primero. */
    fun list(limit: Int = 500): Result {
        if (!dbFile.exists()) return Result(emptyList(), Source.NONE)
        return openRead().use { db ->
            val chatCols = columns(db, "chat")
            val msgCols = columns(db, "message")
            val subject = if ("subject" in chatCols) "c.subject" else "NULL"
            val sender = if ("sender_jid_row_id" in msgCols)
                "LEFT JOIN jid sj ON sj._id = NULLIF(m.sender_jid_row_id, 0)" else ""
            val senderRaw = if ("sender_jid_row_id" in msgCols) "sj.raw_string" else "NULL"
            val sortCol = if ("sort_id" in msgCols) "m.sort_id" else "NULL"

            val rc = columns(db, "message_revoked")
            if ("message_row_id" in rc) {
                val hasAdmin = "admin_jid_row_id" in rc
                val adminJoin = if (hasAdmin) "LEFT JOIN jid aj ON aj._id = NULLIF(r.admin_jid_row_id, 0)" else ""
                val adminRaw = if (hasAdmin) "aj.raw_string" else "NULL"
                val rts = if ("revoke_timestamp" in rc) "NULLIF(r.revoke_timestamp, 0)" else "NULL"
                val found = query(
                    db,
                    "SELECT m._id, m.chat_row_id, cj.raw_string, cj.server, $subject, m.from_me, " +
                            "$senderRaw, $adminRaw, m.timestamp, $rts, m.key_id, $sortCol " +
                            "FROM message_revoked r JOIN message m ON m._id = r.message_row_id " +
                            "JOIN chat c ON c._id = m.chat_row_id JOIN jid cj ON cj._id = c.jid_row_id " +
                            "$sender $adminJoin ORDER BY COALESCE($rts, m.timestamp) DESC LIMIT ?",
                    limit
                )
                if (found.isNotEmpty()) return@use Result(enrich(db, found), Source.REVOKED_TABLE)
            }

            // Sin tabla de borrados: mensajes marcados como "eliminado" (tipos 15 y 64).
            val fallback = query(
                db,
                "SELECT m._id, m.chat_row_id, cj.raw_string, cj.server, $subject, m.from_me, " +
                        "$senderRaw, NULL, m.timestamp, NULL, m.key_id, $sortCol " +
                        "FROM message m JOIN chat c ON c._id = m.chat_row_id " +
                        "JOIN jid cj ON cj._id = c.jid_row_id $sender " +
                        "WHERE m.message_type IN (15, 64) ORDER BY m.timestamp DESC LIMIT ?",
                limit
            )
            Result(enrich(db, fallback), if (fallback.isEmpty()) Source.NONE else Source.MESSAGE_TYPE)
        }
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean = try {
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE name = ? AND type = 'table'", arrayOf(name))
            .use { it.moveToFirst() }
    } catch (_: Exception) {
        false
    }

    /** Añade el texto original: primero el registro de WaImport y, si no, el índice de búsqueda. */
    private fun enrich(db: SQLiteDatabase, entries: List<Entry>): List<Entry> {
        val hasLog = tableExists(db, DeletedLog.TABLE)
        val hasFts = tableExists(db, "message_ftsv2")
        if (!hasLog && !hasFts) return entries
        return entries.map { e ->
            var text: String? = null
            try {
                if (hasLog) {
                    db.rawQuery(
                        "SELECT text FROM ${DeletedLog.TABLE} WHERE message_row_id = ? " +
                                "OR (key_id = ? AND chat_row_id = ?) LIMIT 1",
                        arrayOf(e.messageRowId.toString(), e.keyId ?: "", e.chatRowId.toString())
                    ).use { c -> if (c.moveToFirst()) text = c.getString(0) }
                }
                if (text.isNullOrEmpty() && hasFts) {
                    db.rawQuery(
                        "SELECT content FROM message_ftsv2 WHERE docid = ?", arrayOf(e.messageRowId.toString())
                    ).use { c -> if (c.moveToFirst()) text = c.getString(0) }
                }
            } catch (_: Exception) {
            }
            if (text.isNullOrEmpty()) e else e.copy(text = text)
        }
    }

    private fun query(db: SQLiteDatabase, sql: String, limit: Int): List<Entry> {
        val out = ArrayList<Entry>()
        try {
            db.rawQuery(sql, arrayOf(limit.toString())).use { c ->
                while (c.moveToNext()) {
                    val chatJid = c.getString(2) ?: continue
                    val isGroup = c.getString(3) == "g.us"
                    val fromMe = c.getInt(5) == 1
                    val senderJid = c.getString(6)
                    // En un chat individual el autor de un mensaje recibido es el propio chat.
                    val author = when {
                        fromMe -> null
                        senderJid != null -> senderJid
                        !isGroup -> chatJid
                        else -> null
                    }
                    out += Entry(
                        c.getLong(0), c.getLong(1), chatJid, isGroup, c.getString(4), fromMe,
                        author, c.getString(7),
                        if (c.isNull(8)) 0L else c.getLong(8),
                        if (c.isNull(9)) null else c.getLong(9),
                        c.getString(10),
                        if (c.isNull(11)) null else c.getLong(11)
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return out
    }
}
