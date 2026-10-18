package com.wmods.wppenhacer.xposed.features.general

import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import android.util.Base64
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import org.json.JSONObject

/**
 * Saves a copy of every message row (and its media/text rows) right before WhatsApp deletes it,
 * so [DeletedBackupViewer] can show and restore it later.
 */
class DeletedMessageCapture(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        if (!xprefs.getBoolean("deleted_message_capture", false)) return

        SQLiteDatabase::class.java.resolve().firstMethod {
            name = "delete"
            parameters(String::class.java, String::class.java, Array<String>::class.java)
        }.hook {
            before {
                val database = instance as SQLiteDatabase
                if (!isMsgStoreDatabase(database.path)) return@before
                val table = args[0] as String?
                if (!isMessageTable(table)) return@before
                @Suppress("UNCHECKED_CAST")
                captureSafely(database, args[1] as String?, args[2] as Array<String>?)
            }
        }

        SQLiteDatabase::class.java.resolve().firstMethod {
            name = "execSQL"
            parameters(String::class.java)
        }.hook {
            before {
                val database = instance as SQLiteDatabase
                if (!isMsgStoreDatabase(database.path)) return@before
                val parsedDelete = parseDeleteMessageSql(args[0] as String?) ?: return@before
                captureSafely(database, parsedDelete.whereClause, null)
            }
        }

        SQLiteDatabase::class.java.resolve().firstMethod {
            name = "execSQL"
            parameters(String::class.java, Array<Any>::class.java)
        }.hook {
            before {
                val database = instance as SQLiteDatabase
                if (!isMsgStoreDatabase(database.path)) return@before
                val parsedDelete = parseDeleteMessageSql(args[0] as String?) ?: return@before
                @Suppress("UNCHECKED_CAST")
                captureSafely(
                    database,
                    parsedDelete.whereClause,
                    toSelectionArgs(args[1] as Array<Any?>?)
                )
            }
        }
    }

    override fun getPluginName(): String {
        return "Deleted Message Capture"
    }

    private class ParsedDeleteSql(private val tableName: String, val whereClause: String?)

    companion object {
        private const val MSGSTORE_DB_NAME = "msgstore.db"
        private const val MESSAGE_TABLE = "message"
        private const val STATUS_BROADCAST_JID = "status@broadcast"

        private const val SELECT_DELETE_CANDIDATES_SQL =
            "SELECT message._id, message.key_id, message.from_me, message.message_type, message.timestamp, " +
                    "chat_jid.raw_string AS chat_raw_string, " +
                    "chat_phone_jid.raw_string AS chat_phone_raw_string, " +
                    "sender_jid.raw_string AS sender_raw_string, " +
                    "sender_phone_jid.raw_string AS sender_phone_raw_string " +
                    "FROM message " +
                    "LEFT JOIN chat c ON c._id = message.chat_row_id " +
                    "LEFT JOIN jid chat_jid ON chat_jid._id = c.jid_row_id " +
                    "LEFT JOIN jid chat_phone_jid ON chat_phone_jid._id = COALESCE(" +
                    "(SELECT jm.jid_row_id FROM jid_map jm WHERE jm.lid_row_id = c.jid_row_id ORDER BY jm.sort_id DESC LIMIT 1), " +
                    "(SELECT jm.jid_row_id FROM jid_map jm WHERE jm.jid_row_id = c.jid_row_id ORDER BY jm.sort_id DESC LIMIT 1), " +
                    "c.jid_row_id) " +
                    "LEFT JOIN jid sender_jid ON sender_jid._id = message.sender_jid_row_id " +
                    "LEFT JOIN jid sender_phone_jid ON sender_phone_jid._id = COALESCE(" +
                    "(SELECT jm.jid_row_id FROM jid_map jm WHERE jm.lid_row_id = message.sender_jid_row_id ORDER BY jm.sort_id DESC LIMIT 1), " +
                    "(SELECT jm.jid_row_id FROM jid_map jm WHERE jm.jid_row_id = message.sender_jid_row_id ORDER BY jm.sort_id DESC LIMIT 1), " +
                    "message.sender_jid_row_id)"

        private fun captureSafely(
            database: SQLiteDatabase,
            whereClause: String?,
            whereArgs: Array<String>?
        ) {
            try {
                captureDeleteCandidates(database, whereClause, whereArgs)
            } catch (e: Exception) {
                YukiLog.log(e)
            }
        }

        private fun captureDeleteCandidates(
            database: SQLiteDatabase,
            whereClause: String?,
            whereArgs: Array<String>?
        ) {
            if (!database.isOpen) {
                return
            }

            val idFilterBuilder = StringBuilder("SELECT _id FROM message")
            if (!TextUtils.isEmpty(whereClause)) {
                idFilterBuilder.append(" WHERE ").append(whereClause)
            }

            val sqlBuilder = StringBuilder(SELECT_DELETE_CANDIDATES_SQL)
                .append(" WHERE message._id IN (")
                .append(idFilterBuilder)
                .append(")")

            val deletedAt = System.currentTimeMillis()
            database.rawQuery(sqlBuilder.toString(), whereArgs).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return
                }
                val rowIdIndex = cursor.getColumnIndexOrThrow("_id")
                val keyIdIndex = cursor.getColumnIndexOrThrow("key_id")
                val fromMeIndex = cursor.getColumnIndexOrThrow("from_me")
                val messageTypeIndex = cursor.getColumnIndexOrThrow("message_type")
                val timestampIndex = cursor.getColumnIndexOrThrow("timestamp")
                val chatRawIndex = cursor.getColumnIndexOrThrow("chat_raw_string")
                val chatPhoneRawIndex = cursor.getColumnIndexOrThrow("chat_phone_raw_string")
                val senderRawIndex = cursor.getColumnIndexOrThrow("sender_raw_string")
                val senderPhoneRawIndex = cursor.getColumnIndexOrThrow("sender_phone_raw_string")
                do {
                    val rowId = cursor.getLong(rowIdIndex)
                    val keyId = cursor.getString(keyIdIndex)
                    if (TextUtils.isEmpty(keyId)) {
                        continue
                    }

                    val chatRaw: String? = normalizeRawJid(cursor.getString(chatRawIndex))
                    val chatPhoneRaw: String? = normalizeRawJid(cursor.getString(chatPhoneRawIndex))
                    if (isStatusBroadcast(chatRaw) || isStatusBroadcast(chatPhoneRaw)) {
                        continue
                    }

                    val messageValues: MutableMap<String?, Any?>? =
                        readRowAsValues(database, "message", "_id", rowId)
                    if (messageValues.isNullOrEmpty()) {
                        continue
                    }

                    val messageMediaValues: MutableMap<String?, Any?>? = readRowAsValues(
                        database,
                        "message_media",
                        "message_row_id",
                        rowId
                    )
                    val messageTextValues: MutableMap<String?, Any?>? = readRowAsValues(
                        database,
                        "message_text",
                        "message_row_id",
                        rowId
                    )
                    val senderRaw = cursor.getString(senderRawIndex)
                    val senderPhoneRaw = cursor.getString(senderPhoneRawIndex)
                    val fromMe = cursor.getInt(fromMeIndex)
                    val messageType = cursor.getInt(messageTypeIndex)
                    val messageTimestamp = cursor.getLong(timestampIndex)

                    Utils.executor.execute {
                        try {
                            DelMessageStore.getInstance(Utils.application)
                                .insertDeletedMessageBackupAsync(
                                    keyId,
                                    chatRaw,
                                    chatPhoneRaw,
                                    senderRaw,
                                    senderPhoneRaw,
                                    fromMe,
                                    messageType,
                                    messageTimestamp,
                                    rowValuesToJson(messageValues),
                                    rowValuesToJson(messageMediaValues),
                                    rowValuesToJson(messageTextValues),
                                    deletedAt
                                )
                        } catch (throwable: Throwable) {
                            YukiLog.log(throwable)
                        }
                    }
                } while (cursor.moveToNext())
            }
        }

        private fun readRowAsValues(
            database: SQLiteDatabase,
            table: String,
            keyColumn: String,
            rowId: Long
        ): MutableMap<String?, Any?>? {
            try {
                database.query(
                    table,
                    null,
                    "$keyColumn=?",
                    arrayOf(rowId.toString()),
                    null,
                    null,
                    null,
                    "1"
                ).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return null
                    }
                    val values = LinkedHashMap<String?, Any?>()
                    val columnNames = cursor.columnNames
                    for (i in columnNames.indices) {
                        val columnName = columnNames[i]
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> values[columnName] = null
                            Cursor.FIELD_TYPE_INTEGER -> values[columnName] = cursor.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> values[columnName] = cursor.getDouble(i)
                            Cursor.FIELD_TYPE_STRING -> values[columnName] = cursor.getString(i)
                            Cursor.FIELD_TYPE_BLOB -> values[columnName] = cursor.getBlob(i)
                            else -> values[columnName] = null
                        }
                    }
                    return values
                }
            } catch (e: Exception) {
                YukiLog.log(e)
                return null
            }
        }

        private fun rowValuesToJson(values: MutableMap<String?, Any?>?): String {
            if (values.isNullOrEmpty()) {
                return ""
            }

            try {
                val json = JSONObject()
                for (entry in values.entries) {
                    val value = entry.value
                    if (value == null) {
                        json.put(entry.key!!, JSONObject.NULL)
                    } else if (value is ByteArray) {
                        if (value.isEmpty()) {
                            json.put(entry.key!!, JSONObject.NULL)
                        } else {
                            json.put(
                                entry.key!!,
                                "B64:" + Base64.encodeToString(value, Base64.NO_WRAP)
                            )
                        }
                    } else {
                        json.put(entry.key!!, value)
                    }
                }
                return json.toString()
            } catch (e: Exception) {
                YukiLog.log(e)
                return ""
            }
        }

        private fun parseDeleteMessageSql(sql: String?): ParsedDeleteSql? {
            if (TextUtils.isEmpty(sql)) {
                return null
            }

            val sourceSql = sql!!.trim { it <= ' ' }
            val lowerSql = sourceSql.lowercase()
            if (!lowerSql.startsWith("delete")) {
                return null
            }

            val fromIndex = lowerSql.indexOf(" from ")
            if (fromIndex < 0) {
                return null
            }

            val afterFrom = sourceSql.substring(fromIndex + 6).trim { it <= ' ' }
            if (afterFrom.isEmpty()) {
                return null
            }

            val splitIndex: Int = indexOfSeparator(afterFrom)
            val tableToken = if (splitIndex < 0) afterFrom else afterFrom.substring(0, splitIndex)
            val tableName: String = normalizeTableName(tableToken)
            if (!isMessageTable(tableName)) {
                return null
            }

            var whereClause: String? = null
            val whereIndex = lowerSql.indexOf(" where ")
            if (whereIndex >= 0 && whereIndex + 7 <= sourceSql.length) {
                whereClause = sourceSql.substring(whereIndex + 7).trim { it <= ' ' }
                if (whereClause.endsWith(";")) {
                    whereClause =
                        whereClause.substring(0, whereClause.length - 1).trim { it <= ' ' }
                }
                if (whereClause.isEmpty()) {
                    whereClause = null
                }
            }

            return ParsedDeleteSql(tableName, whereClause)
        }

        private fun indexOfSeparator(value: String): Int {
            for ((i, element) in value.withIndex()) {
                val c = element
                if (Character.isWhitespace(c) || c == ';') {
                    return i
                }
            }
            return -1
        }

        private fun normalizeTableName(value: String?): String {
            if (value == null) {
                return ""
            }
            var cleaned = value.trim { it <= ' ' }
            if (cleaned.endsWith(";")) {
                cleaned = cleaned.substring(0, cleaned.length - 1)
            }
            cleaned = cleaned.replace("`", "").replace("\"", "").replace("[", "").replace("]", "")
            val dotIndex = cleaned.lastIndexOf('.')
            if (dotIndex >= 0 && dotIndex < cleaned.length - 1) {
                cleaned = cleaned.substring(dotIndex + 1)
            }
            return cleaned.trim { it <= ' ' }.lowercase()
        }

        private fun isMessageTable(table: String?): Boolean {
            if (table == null) {
                return false
            }
            return MESSAGE_TABLE == normalizeTableName(table)
        }

        private fun isMsgStoreDatabase(databasePath: String?): Boolean {
            if (databasePath == null) {
                return false
            }
            val normalized = databasePath.lowercase()
            return normalized.endsWith("/$MSGSTORE_DB_NAME") || normalized.endsWith("\\$MSGSTORE_DB_NAME")
        }

        private fun normalizeRawJid(rawJid: String?): String? {
            if (rawJid == null) {
                return null
            }
            val normalized = rawJid.trim { it <= ' ' }.lowercase()
            if (normalized.isEmpty()) {
                return null
            }
            return normalized.replaceFirst("\\\\.[\\\\d:]+@".toRegex(), "@")
        }

        private fun isStatusBroadcast(rawJid: String?): Boolean {
            return STATUS_BROADCAST_JID == rawJid
        }

        private fun toSelectionArgs(bindArgs: Array<Any?>?): Array<String>? {
            if (bindArgs.isNullOrEmpty()) {
                return null
            }

            val args = Array(bindArgs.size) { i ->
                val value = bindArgs[i]
                value?.toString() ?: ""
            }
            return args
        }
    }
}
