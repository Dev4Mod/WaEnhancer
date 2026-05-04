package com.wmods.wppenhacer.xposed.core.db

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.util.stream.Collectors

class MessageStore private constructor() {

    private var sqLiteDatabase: SQLiteDatabase? = null

    init {
        val dbFile = File(Utils.getApplication().filesDir.parentFile, "/databases/msgstore.db")
        if (dbFile.exists()) {
            sqLiteDatabase = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
        }
    }

    companion object {
        @Volatile
        private var mInstance: MessageStore? = null

        @JvmStatic
        fun getInstance(): MessageStore {
            return mInstance?.takeIf { it.sqLiteDatabase?.isOpen == true }
                ?: synchronized(this) {
                    mInstance?.takeIf { it.sqLiteDatabase?.isOpen == true }
                        ?: MessageStore().also { mInstance = it }
                }
        }
    }

    fun getMessageById(id: Long): String {
        val db = sqLiteDatabase ?: return ""
        var message = ""
        try {
            val columns = arrayOf("c0content")
            val selection = "docid=?"
            val selectionArgs = arrayOf(id.toString())

            db.query("message_ftsv2_content", columns, selection, selectionArgs, null, null, null)
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        message = cursor.getString(cursor.getColumnIndexOrThrow("c0content"))
                    }
                }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return message
    }

    fun getCurrentMessageByKey(message_key: String): String {
        val db = sqLiteDatabase ?: return ""
        val columns = arrayOf("text_data")
        val selection = "key_id=?"
        val selectionArgs = arrayOf(message_key)
        try {
            db.query("message", columns, selection, selectionArgs, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return ""
    }

    fun getIdfromKey(message_key: String): Long {
        val db = sqLiteDatabase ?: return -1
        val columns = arrayOf("_id")
        val selection = "key_id=?"
        val selectionArgs = arrayOf(message_key)
        try {
            db.query("message", columns, selection, selectionArgs, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return -1
    }

    fun getMediaFromID(id: Long): String? {
        val db = sqLiteDatabase ?: return null
        val columns = arrayOf("file_path")
        val selection = "message_row_id=?"
        val selectionArgs = arrayOf(id.toString())
        try {
            db.query("message_media", columns, selection, selectionArgs, null, null, null)
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(0)
                    }
                }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return null
    }

    fun getCurrentMessageByID(row_id: Long): String {
        val db = sqLiteDatabase ?: return ""
        val columns = arrayOf("text_data")
        val selection = "_id=?"
        val selectionArgs = arrayOf(row_id.toString())
        try {
            db.query("message", columns, selection, selectionArgs, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return ""
    }

    fun getOriginalMessageKey(id: Long): String {
        val db = sqLiteDatabase ?: return ""
        var message = ""
        val sql =
            "SELECT parent_message_row_id, key_id FROM message_add_on WHERE parent_message_row_id=\"$id\""
        try {
            db.rawQuery(sql, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    message = cursor.getString(1)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return message
    }

    fun getAudioListByMessageList(messageList: List<String>?): List<String> {
        val db = sqLiteDatabase
        if (db == null || messageList.isNullOrEmpty()) {
            return ArrayList()
        }

        val list = ArrayList<String>()
        val placeholders = messageList.stream().map { "?" }.collect(Collectors.joining(","))
        val sql = "SELECT message_type FROM message WHERE key_id IN ($placeholders)"
        try {
            db.rawQuery(sql, messageList.toTypedArray()).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        if (cursor.getInt(0) == 2) {
                            list.add(cursor.getString(0))
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }

        return list
    }

    @Synchronized
    fun executeSQL(sql: String) {
        try {
            sqLiteDatabase?.execSQL(sql)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    fun storeMessageRead(messageId: String) {
        val db = sqLiteDatabase ?: return
        XposedBridge.log("storeMessageRead: $messageId")
        try {
            db.execSQL("UPDATE message SET status = 1 WHERE key_id = \"$messageId\"")
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    fun isReadMessageStatus(messageId: String): Boolean {
        val db = sqLiteDatabase ?: return false
        var result = false
        var cursor: Cursor? = null
        try {
            val columns = arrayOf("status")
            val selection = "key_id=?"
            val selectionArgs = arrayOf(messageId)

            cursor = db.query("message", columns, selection, selectionArgs, null, null, null)
            if (cursor.moveToFirst()) {
                result = cursor.getInt(cursor.getColumnIndexOrThrow("status")) == 1
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        } finally {
            cursor?.close()
        }
        return result
    }

    fun getDatabase(): SQLiteDatabase? {
        return sqLiteDatabase
    }

    @SuppressLint("Recycle")
    @Synchronized
    fun getFirstMessageInfoByChatRawJid(rawJid: String): MessageInfo? {
        val db = getDatabase()
        if (db == null || TextUtils.isEmpty(rawJid)) {
            return null
        }

        val sql = """
            WITH resolved(jid_row_id) AS (
                SELECT _id FROM jid WHERE raw_string=?
                UNION
                SELECT jm.jid_row_id FROM jid_map jm
                INNER JOIN jid j ON j._id = jm.lid_row_id
                WHERE j.raw_string=?
                UNION
                SELECT jm.lid_row_id FROM jid_map jm
                INNER JOIN jid j ON j._id = jm.jid_row_id
                WHERE j.raw_string=?
            ), chat_target AS (
                SELECT _id FROM chat WHERE jid_row_id IN (SELECT jid_row_id FROM resolved)
            )
            SELECT m._id, m.sort_id, m.chat_row_id
            FROM message m
            INNER JOIN chat_target c ON c._id = m.chat_row_id
            ORDER BY m.sort_id ASC, m._id ASC
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(sql, arrayOf(rawJid, rawJid, rawJid)).use { cursor ->
                if (cursor.moveToFirst()) {
                    return MessageInfo(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2))
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return null
    }

    data class MessageInfo(val rowId: Long, val sortId: Long, val chatRowId: Long)
}