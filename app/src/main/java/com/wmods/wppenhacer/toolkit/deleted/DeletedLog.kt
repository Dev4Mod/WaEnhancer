package com.wmods.wppenhacer.toolkit.deleted

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Registro local con el texto de los mensajes.
 *
 * WhatsApp elimina el contenido de un mensaje cuando se borra para todos, así que después ya no
 * se puede leer. Para poder mostrarlo, este registro guarda una copia del texto de cada mensaje en
 * una tabla propia (`waimport_log`) de msgstore.db. La copia la hace SQLite con dos triggers, así
 * que no depende de ningún hook ni de la versión de WhatsApp. Nada sale del dispositivo.
 *
 * Se puede quitar en cualquier momento: [disable] borra los triggers y la tabla.
 */
class DeletedLog(private val dbFile: File) {

    class Status(val enabled: Boolean, val messages: Long)

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

    fun status(): Status {
        if (!dbFile.exists()) return Status(false, 0)
        return try {
            openRead().use { db ->
                var triggers = 0
                db.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name IN ('$TRG_INSERT', '$TRG_UPDATE')",
                    null
                ).use { c -> if (c.moveToFirst()) triggers = c.getInt(0) }
                var table = false
                var count = 0L
                try {
                    db.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
                        if (c.moveToFirst()) { table = true; count = c.getLong(0) }
                    }
                } catch (_: Exception) {
                }
                Status(triggers == 2 && table, count)
            }
        } catch (_: Exception) {
            Status(false, 0)
        }
    }

    /**
     * Activa el registro y copia los textos de los últimos [backfillDays] días (los mensajes
     * futuros se copian solos). Devuelve cuántos mensajes se copiaron ahora.
     */
    fun enable(backfillDays: Int = 90): Long {
        if (!dbFile.exists()) throw IllegalStateException("No existe msgstore.db")
        val db = openWrite()
        try {
            val cols = HashSet<String>()
            db.rawQuery("PRAGMA table_info(message)", null).use { c ->
                while (c.moveToNext()) c.getString(1)?.let { cols += it }
            }
            val needed = listOf("_id", "key_id", "chat_row_id", "from_me", "sender_jid_row_id", "timestamp", "message_type", "text_data")
            val missing = needed.filter { it !in cols }
            if (missing.isNotEmpty()) {
                throw IllegalStateException("Esta versión de WhatsApp no tiene las columnas esperadas: $missing")
            }

            val copy = "INSERT OR REPLACE INTO $TABLE(message_row_id, key_id, chat_row_id, from_me, " +
                    "sender_jid_row_id, message_type, timestamp, text) VALUES(" +
                    "NEW._id, NEW.key_id, NEW.chat_row_id, NEW.from_me, NEW.sender_jid_row_id, " +
                    "NEW.message_type, NEW.timestamp, NEW.text_data);"
            val cond = "WHEN NEW.text_data IS NOT NULL AND NEW.text_data != ''"

            db.beginTransaction()
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS $TABLE (message_row_id INTEGER PRIMARY KEY, key_id TEXT, " +
                            "chat_row_id INTEGER, from_me INTEGER, sender_jid_row_id INTEGER, " +
                            "message_type INTEGER, timestamp INTEGER, text TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS ${TABLE}_key ON $TABLE(key_id)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS $TRG_INSERT AFTER INSERT ON message $cond BEGIN $copy END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS $TRG_UPDATE AFTER UPDATE OF text_data ON message $cond BEGIN $copy END")

                val cutoff = System.currentTimeMillis() - backfillDays.toLong() * 24 * 60 * 60 * 1000
                db.execSQL(
                    "INSERT OR IGNORE INTO $TABLE(message_row_id, key_id, chat_row_id, from_me, " +
                            "sender_jid_row_id, message_type, timestamp, text) " +
                            "SELECT _id, key_id, chat_row_id, from_me, sender_jid_row_id, message_type, timestamp, text_data " +
                            "FROM message WHERE text_data IS NOT NULL AND text_data != '' AND timestamp >= $cutoff"
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return status().messages
        } finally {
            db.close()
        }
    }

    /** Quita los triggers y la tabla. Los triggers primero, para que nunca apunten a una tabla que no existe. */
    fun disable() {
        if (!dbFile.exists()) return
        val db = openWrite()
        try {
            db.beginTransaction()
            try {
                db.execSQL("DROP TRIGGER IF EXISTS $TRG_INSERT")
                db.execSQL("DROP TRIGGER IF EXISTS $TRG_UPDATE")
                db.execSQL("DROP TABLE IF EXISTS $TABLE")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }

    companion object {
        const val TABLE = "waimport_log"
        const val TRG_INSERT = "waimport_log_ins"
        const val TRG_UPDATE = "waimport_log_upd"
    }
}
