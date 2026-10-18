package com.wmods.wppenhacer.xposed.core.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.sqlite.db.SimpleSQLiteQuery
import com.wmods.wppenhacer.xposed.core.db.entity.DelMessage
import com.wmods.wppenhacer.xposed.core.db.entity.DeletedMessageBackupEntity
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import org.json.JSONObject
import java.util.Locale

class DelMessageStore private constructor(private val context: Context) {

    data class DeletedBackupEntry(
        @JvmField
        val id: Long,
        @JvmField
        val deletedAt: Long,
        @JvmField
        val restoreStatus: String?,
        @JvmField
        val restoreError: String?,
        @JvmField
        val keyId: String?,
        @JvmField
        val chatRawString: String?,
        @JvmField
        val chatPhoneRawString: String?,
        @JvmField
        val senderRawString: String?,
        @JvmField
        val senderPhoneRawString: String?,
        @JvmField
        val fromMe: Int,
        @JvmField
        val messageType: Int,
        @JvmField
        val messageTimestamp: Long,
        @JvmField
        val messageJson: String?,
        @JvmField
        val messageMediaJson: String?,
        @JvmField
        val messageTextJson: String?
    )


    private var database = DelMessageDatabase.getInstance(context)
    private var dao = database.delMessageDao()

    private fun <T> executeBlocking(fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (exception: Exception) {
            YukiLog.log(exception)
            fallback
        }
    }

    private fun executeAsync(block: () -> Unit) {
        Utils.executor.execute {
            try {
                block()
            } catch (t: Throwable) {
                YukiLog.log(t)
            }
        }
    }

    fun insertDeletedMessageBackup(
        keyId: String?,
        chatRawString: String?,
        chatPhoneRawString: String?,
        senderRawString: String?,
        senderPhoneRawString: String?,
        fromMe: Int,
        messageType: Int,
        messageTimestamp: Long,
        messageJson: String?,
        messageMediaJson: String?,
        messageTextJson: String?,
        deletedAt: Long
    ) {
        if (keyId.isNullOrBlank() || messageJson.isNullOrBlank()) {
            return
        }

        val uniqueHash = buildBackupUniqueHash(
            chatRaw = chatRawString,
            keyId = keyId,
            fromMe = fromMe,
            senderRaw = senderRawString,
            messageTimestamp = messageTimestamp
        )

        executeBlocking(Unit) {
            dao.upsertDeletedMessageBackup(
                DeletedMessageBackupEntity(
                    deletedAt = deletedAt,
                    messageUniqueHash = uniqueHash,
                    keyId = keyId,
                    chatRawString = normalizeRawJid(chatRawString),
                    chatPhoneRawString = normalizeRawJid(chatPhoneRawString),
                    senderRawString = normalizeRawJid(senderRawString),
                    senderPhoneRawString = normalizeRawJid(senderPhoneRawString),
                    fromMe = fromMe,
                    messageType = messageType,
                    messageTimestamp = messageTimestamp,
                    messageJson = messageJson,
                    messageMediaJson = messageMediaJson,
                    messageTextJson = messageTextJson
                )
            )
        }
        notifyBackupInserted()
    }

    fun insertDeletedMessageBackupAsync(
        keyId: String?,
        chatRawString: String?,
        chatPhoneRawString: String?,
        senderRawString: String?,
        senderPhoneRawString: String?,
        fromMe: Int,
        messageType: Int,
        messageTimestamp: Long,
        messageJson: String?,
        messageMediaJson: String?,
        messageTextJson: String?,
        deletedAt: Long
    ) {
        executeAsync {
            insertDeletedMessageBackup(
                keyId = keyId,
                chatRawString = chatRawString,
                chatPhoneRawString = chatPhoneRawString,
                senderRawString = senderRawString,
                senderPhoneRawString = senderPhoneRawString,
                fromMe = fromMe,
                messageType = messageType,
                messageTimestamp = messageTimestamp,
                messageJson = messageJson,
                messageMediaJson = messageMediaJson,
                messageTextJson = messageTextJson,
                deletedAt = deletedAt
            )
        }
    }

    fun removeBackupById(backupId: Long): Boolean {
        return executeBlocking(false) {
            dao.deleteBackupById(backupId) > 0
        }
    }

    fun removeBackupsByIds(backupIds: LongArray): Int {
        if (backupIds.isEmpty()) {
            return 0
        }

        return executeBlocking(0) {
            dao.deleteBackupsByIds(backupIds)
        }
    }

    fun removeBackupsByChat(
        chatKey: String?,
        chatRawString: String?,
        chatPhoneRawString: String?
    ): Int {
        val chatKeys = listOfNotNull(
            normalizeRawJid(chatKey),
            normalizeRawJid(chatRawString),
            normalizeRawJid(chatPhoneRawString)
        ).distinct()

        if (chatKeys.isEmpty()) {
            return 0
        }

        return executeBlocking(0) {
            dao.deleteBackupsByChatKeys(chatKeys)
        }
    }

    private fun buildBackupUniqueHash(
        chatRaw: String?,
        keyId: String,
        fromMe: Int,
        senderRaw: String?,
        messageTimestamp: Long
    ): String {
        val normalizedChat = normalizeRawJid(chatRaw).orEmpty()
        val normalizedSender = normalizeRawJid(senderRaw).orEmpty()

        return "$normalizedChat|$fromMe|$keyId|$normalizedSender|$messageTimestamp"
    }

    private fun normalizeRawJid(rawJid: String?): String? {
        val normalized = rawJid
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotEmpty() }

        return normalized?.replaceFirst("\\.[\\d:]+@".toRegex(), "@")
    }

    fun listDeletedMessageBackups(
        search: String?,
        includeContacts: Boolean,
        includeGroups: Boolean,
        limit: Int
    ): List<DeletedBackupEntry> {
        if (!includeContacts && !includeGroups) {
            return emptyList()
        }

        return executeBlocking(emptyList()) {
            val query = buildListDeletedMessageBackupsQuery(
                search = search,
                includeContacts = includeContacts,
                includeGroups = includeGroups,
                limit = limit
            )

            dao.listDeletedMessageBackups(query).map { entity ->
                entity.toDeletedBackupEntry()
            }
        }
    }

    private fun buildListDeletedMessageBackupsQuery(
        search: String?,
        includeContacts: Boolean,
        includeGroups: Boolean,
        limit: Int
    ): SimpleSQLiteQuery {
        val selection = StringBuilder()
        val args = mutableListOf<Any>()
        if (!includeContacts || !includeGroups) {
            val chatAddressExpression =
                "COALESCE(chat_raw_string, chat_phone_raw_string, '')"

            val scopeFilter = if (includeGroups) {
                "$chatAddressExpression LIKE '%@g.us'"
            } else {
                "$chatAddressExpression NOT LIKE '%@g.us'"
            }

            selection.append(scopeFilter)
        }

        val normalizedSearch = search
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotEmpty() }

        if (normalizedSearch != null) {
            if (selection.isNotEmpty()) {
                selection.append(" AND ")
            }

            selection.append(
                """
            (
                LOWER(COALESCE(key_id, '')) LIKE ?
                OR LOWER(COALESCE(chat_raw_string, '')) LIKE ?
                OR LOWER(COALESCE(chat_phone_raw_string, '')) LIKE ?
                OR LOWER(COALESCE(sender_raw_string, '')) LIKE ?
                OR LOWER(COALESCE(sender_phone_raw_string, '')) LIKE ?
            )
            """.trimIndent()
            )

            val searchArg = "%$normalizedSearch%"
            repeat(5) {
                args += searchArg
            }
        }

        val sql = buildString {
            append("SELECT * FROM deleted_message_backup")

            if (selection.isNotEmpty()) {
                append(" WHERE ")
                append(selection)
            }

            append(" ORDER BY deleted_at DESC")
            if (limit > 0) {
                append(" LIMIT ?")
            }
        }

        if (limit > 0) {
            args += limit
        }

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun DeletedMessageBackupEntity.toDeletedBackupEntry(): DeletedBackupEntry {
        return DeletedBackupEntry(
            id = id,
            deletedAt = deletedAt,
            restoreStatus = restoreStatus,
            restoreError = restoreError,
            keyId = keyId,
            chatRawString = chatRawString,
            chatPhoneRawString = chatPhoneRawString,
            senderRawString = senderRawString,
            senderPhoneRawString = senderPhoneRawString,
            fromMe = fromMe ?: 0,
            messageType = messageType ?: 0,
            messageTimestamp = messageTimestamp ?: 0L,
            messageJson = messageJson,
            messageMediaJson = messageMediaJson,
            messageTextJson = messageTextJson
        )
    }

    fun restoreBackupById(
        msgStoreDb: SQLiteDatabase?,
        backupId: Long
    ): Boolean {
        if (msgStoreDb == null || !msgStoreDb.isOpen) {
            return false
        }

        return executeBlocking(false) {
            val backup = dao.getBackupById(backupId) ?: return@executeBlocking false
            restoreBackupEntry(
                msgStoreDb = msgStoreDb,
                backup = backup
            )
        }
    }

    fun restoreBackupsByIds(
        msgStoreDb: SQLiteDatabase?,
        backupIds: LongArray
    ): Int {
        if (msgStoreDb == null || !msgStoreDb.isOpen || backupIds.isEmpty()) {
            return 0
        }

        return executeBlocking(0) {
            var restoredCount = 0

            for (backupId in backupIds) {
                val backup = dao.getBackupById(backupId) ?: continue

                val restored = restoreBackupEntry(
                    msgStoreDb = msgStoreDb,
                    backup = backup
                )

                if (restored) {
                    restoredCount++
                }
            }

            restoredCount
        }
    }

    private fun restoreBackupEntry(
        msgStoreDb: SQLiteDatabase,
        backup: DeletedMessageBackupEntity
    ): Boolean {
        try {
            val chatRowId = resolveChatRowId(
                msgStoreDb = msgStoreDb,
                chatRaw = backup.chatRawString,
                chatPhoneRaw = backup.chatPhoneRawString
            )

            if (chatRowId <= 0L) {
                dao.updateRestoreState(
                    backupId = backup.id,
                    status = RestoreState.Pending.value,
                    error = "chat_not_found",
                    restoredAt = 0L
                )
                return false
            }

            val senderJidRowId = resolveSenderJidRowId(
                msgStoreDb = msgStoreDb,
                senderRaw = backup.senderRawString,
                senderPhoneRaw = backup.senderPhoneRawString
            )

            val messageValues = buildRestoredMessageValues(
                backup = backup,
                chatRowId = chatRowId,
                senderJidRowId = senderJidRowId
            )

            var conflictStatus: RestoreConflictStatus? = null
            var newMessageRowId = -1L

            msgStoreDb.beginTransaction()
            try {
                newMessageRowId = msgStoreDb.insertWithOnConflict(
                    "message",
                    null,
                    messageValues,
                    SQLiteDatabase.CONFLICT_IGNORE
                )

                if (newMessageRowId == -1L) {
                    conflictStatus = resolveRestoreConflictStatus(
                        msgStoreDb = msgStoreDb,
                        backup = backup,
                        chatRowId = chatRowId,
                        senderJidRowId = senderJidRowId
                    )
                } else {
                    restoreAuxiliaryTable(
                        msgStoreDb = msgStoreDb,
                        tableName = "message_media",
                        jsonPayload = backup.messageMediaJson,
                        newMessageRowId = newMessageRowId
                    )

                    restoreAuxiliaryTable(
                        msgStoreDb = msgStoreDb,
                        tableName = "message_text",
                        jsonPayload = backup.messageTextJson,
                        newMessageRowId = newMessageRowId
                    )

                    msgStoreDb.setTransactionSuccessful()
                }
            } finally {
                msgStoreDb.endTransaction()
            }

            if (newMessageRowId == -1L) {
                val status = conflictStatus ?: RestoreConflictStatus(
                    restoreState = RestoreState.Failed,
                    error = "insert_failed",
                    restoredAt = 0L
                )

                dao.updateRestoreState(
                    backupId = backup.id,
                    status = status.restoreState.value,
                    error = status.error,
                    restoredAt = status.restoredAt
                )

                return false
            }

            dao.deleteBackupById(backup.id)
            return true
        } catch (exception: Exception) {
            dao.updateRestoreState(
                backupId = backup.id,
                status = RestoreState.Failed.value,
                error = exception.message,
                restoredAt = 0L
            )

            YukiLog.log(exception)
            return false
        }
    }

    private fun buildRestoredMessageValues(
        backup: DeletedMessageBackupEntity,
        chatRowId: Long,
        senderJidRowId: Int?
    ): ContentValues {
        val messageObject = JSONObject(backup.messageJson)

        return jsonToContentValues(
            objectValue = messageObject,
            ignoredKeys = setOf("_id", "chat_row_id", "sender_jid_row_id")
        ).apply {
            put("chat_row_id", chatRowId)

            if (senderJidRowId == null) {
                putNull("sender_jid_row_id")
            } else {
                put("sender_jid_row_id", senderJidRowId)
            }
        }
    }

    private fun resolveRestoreConflictStatus(
        msgStoreDb: SQLiteDatabase,
        backup: DeletedMessageBackupEntity,
        chatRowId: Long,
        senderJidRowId: Int?
    ): RestoreConflictStatus {
        val messageExists = messageExists(
            msgStoreDb = msgStoreDb,
            chatRowId = chatRowId,
            fromMe = backup.fromMe ?: 0,
            keyId = backup.keyId,
            senderJidRowId = senderJidRowId
        )

        if (messageExists) {
            return RestoreConflictStatus(
                restoreState = RestoreState.SkippedExists,
                error = null,
                restoredAt = System.currentTimeMillis()
            )
        }

        return RestoreConflictStatus(
            restoreState = RestoreState.Failed,
            error = "insert_failed",
            restoredAt = 0L
        )
    }

    private fun restoreAuxiliaryTable(
        msgStoreDb: SQLiteDatabase,
        tableName: String,
        jsonPayload: String?,
        newMessageRowId: Long
    ) {
        if (jsonPayload.isNullOrBlank()) {
            return
        }

        val payload = JSONObject(jsonPayload)

        val values = jsonToContentValues(
            objectValue = payload,
            ignoredKeys = setOf("message_row_id")
        ).apply {
            put("message_row_id", newMessageRowId)

            if (tableName == "message_media") {
                put("transferred", 0)
            }
        }

        msgStoreDb.insertWithOnConflict(
            tableName,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun messageExists(
        msgStoreDb: SQLiteDatabase,
        chatRowId: Long,
        fromMe: Int,
        keyId: String?,
        senderJidRowId: Int?
    ): Boolean {
        if (keyId.isNullOrBlank()) {
            return false
        }

        val selection: String
        val selectionArgs: Array<String>

        if (senderJidRowId == null) {
            selection = "chat_row_id=? AND from_me=? AND key_id=? AND sender_jid_row_id IS NULL"
            selectionArgs = arrayOf(
                chatRowId.toString(),
                fromMe.toString(),
                keyId
            )
        } else {
            selection = "chat_row_id=? AND from_me=? AND key_id=? AND sender_jid_row_id=?"
            selectionArgs = arrayOf(
                chatRowId.toString(),
                fromMe.toString(),
                keyId,
                senderJidRowId.toString()
            )
        }

        return try {
            msgStoreDb.query(
                "message",
                arrayOf("_id"),
                selection,
                selectionArgs,
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                cursor.moveToFirst()
            }
        } catch (exception: Exception) {
            YukiLog.log(exception)
            false
        }
    }

    private fun resolveChatRowId(
        msgStoreDb: SQLiteDatabase,
        chatRaw: String?,
        chatPhoneRaw: String?
    ): Long {
        val normalizedChatRaw = normalizeRawJid(chatRaw)
        val normalizedChatPhoneRaw = normalizeRawJid(chatPhoneRaw)

        findChatRowIdByRaw(msgStoreDb, normalizedChatRaw)?.let {
            return it
        }

        findChatRowIdByRaw(msgStoreDb, normalizedChatPhoneRaw)?.let {
            return it
        }

        findChatRowIdByMappedRaw(msgStoreDb, normalizedChatRaw)?.let {
            return it
        }

        return findChatRowIdByMappedRaw(
            msgStoreDb = msgStoreDb,
            raw = normalizedChatPhoneRaw
        ) ?: -1L
    }

    private fun resolveSenderJidRowId(
        msgStoreDb: SQLiteDatabase,
        senderRaw: String?,
        senderPhoneRaw: String?
    ): Int? {
        val normalizedSenderRaw = normalizeRawJid(senderRaw)
        val normalizedSenderPhoneRaw = normalizeRawJid(senderPhoneRaw)

        findJidRowIdByRaw(msgStoreDb, normalizedSenderRaw)?.let {
            return it.toInt()
        }

        findJidRowIdByRaw(msgStoreDb, normalizedSenderPhoneRaw)?.let {
            return it.toInt()
        }

        findMappedJidRowId(msgStoreDb, normalizedSenderRaw)?.let {
            return it.toInt()
        }

        return findMappedJidRowId(
            msgStoreDb = msgStoreDb,
            raw = normalizedSenderPhoneRaw
        )?.toInt()
    }

    private fun findChatRowIdByRaw(
        msgStoreDb: SQLiteDatabase,
        raw: String?
    ): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }

        return try {
            msgStoreDb.rawQuery(
                """
            SELECT c._id
            FROM chat c
            INNER JOIN jid j ON j._id = c.jid_row_id
            WHERE j.raw_string = ?
            LIMIT 1
            """.trimIndent(),
                arrayOf(raw)
            ).useFirstLongOrNull()
        } catch (exception: Exception) {
            YukiLog.log(exception)
            null
        }
    }

    private fun findChatRowIdByMappedRaw(
        msgStoreDb: SQLiteDatabase,
        raw: String?
    ): Long? {
        val mappedJidId = findMappedJidRowId(
            msgStoreDb = msgStoreDb,
            raw = raw
        ) ?: return null

        return try {
            msgStoreDb.rawQuery(
                """
            SELECT _id
            FROM chat
            WHERE jid_row_id = ?
            LIMIT 1
            """.trimIndent(),
                arrayOf(mappedJidId.toString())
            ).useFirstLongOrNull()
        } catch (exception: Exception) {
            YukiLog.log(exception)
            null
        }
    }

    private fun findMappedJidRowId(
        msgStoreDb: SQLiteDatabase,
        raw: String?
    ): Long? {
        val jidRowId = findJidRowIdByRaw(
            msgStoreDb = msgStoreDb,
            raw = raw
        ) ?: return null

        val mappedFromLid = try {
            msgStoreDb.rawQuery(
                """
            SELECT jid_row_id
            FROM jid_map
            WHERE lid_row_id = ?
            ORDER BY sort_id DESC
            LIMIT 1
            """.trimIndent(),
                arrayOf(jidRowId.toString())
            ).useFirstLongOrNull()
        } catch (exception: Exception) {
            YukiLog.log(exception)
            null
        }

        if (mappedFromLid != null) {
            return mappedFromLid
        }

        return try {
            msgStoreDb.rawQuery(
                """
            SELECT lid_row_id
            FROM jid_map
            WHERE jid_row_id = ?
            ORDER BY sort_id DESC
            LIMIT 1
            """.trimIndent(),
                arrayOf(jidRowId.toString())
            ).useFirstLongOrNull()
        } catch (exception: Exception) {
            YukiLog.log(exception)
            null
        }
    }

    private fun findJidRowIdByRaw(
        msgStoreDb: SQLiteDatabase,
        raw: String?
    ): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }

        return try {
            msgStoreDb.rawQuery(
                """
            SELECT _id
            FROM jid
            WHERE raw_string = ?
            LIMIT 1
            """.trimIndent(),
                arrayOf(raw)
            ).useFirstLongOrNull()
        } catch (exception: Exception) {
            YukiLog.log(exception)
            null
        }
    }

    private fun jsonToContentValues(
        objectValue: JSONObject,
        ignoredKeys: Set<String>
    ): ContentValues {
        val values = ContentValues()
        val keys = objectValue.keys()

        while (keys.hasNext()) {
            val key = keys.next()

            if (key in ignoredKeys) {
                continue
            }

            when (val value = objectValue.opt(key)) {
                null,
                JSONObject.NULL -> values.putNull(key)

                is Int -> values.put(key, value)
                is Long -> values.put(key, value)
                is Double -> values.put(key, value)
                is Float -> values.put(key, value)
                is Boolean -> values.put(key, if (value) 1 else 0)

                is String -> {
                    if (value.startsWith("B64:")) {
                        putBase64OrString(
                            values = values,
                            key = key,
                            value = value
                        )
                    } else {
                        values.put(key, value)
                    }
                }

                else -> values.put(key, value.toString())
            }
        }

        return values
    }

    private fun putBase64OrString(
        values: ContentValues,
        key: String,
        value: String
    ) {
        try {
            values.put(
                key,
                Base64.decode(
                    value.removePrefix("B64:"),
                    Base64.NO_WRAP
                )
            )
        } catch (exception: Exception) {
            values.put(key, value)
        }
    }

    private fun Cursor.useFirstLongOrNull(): Long? {
        return use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(0)
            } else {
                null
            }
        }
    }

    private data class RestoreConflictStatus(
        val restoreState: RestoreState,
        val error: String?,
        val restoredAt: Long
    )

    fun interface BackupListener {
        fun onBackupInserted()
    }

    private fun notifyBackupInserted() {
        for (listener in backupListeners) {
            try {
                listener.onBackupInserted()
            } catch (t: Throwable) {
                YukiLog.log(t)
            }
        }
    }

    companion object {
        private val backupListeners = java.util.concurrent.CopyOnWriteArrayList<BackupListener>()

        @JvmStatic
        fun addBackupListener(listener: BackupListener) {
            backupListeners.addIfAbsent(listener)
        }

        @JvmStatic
        fun removeBackupListener(listener: BackupListener) {
            backupListeners.remove(listener)
        }

        @Volatile
        private var instance: DelMessageStore? = null

        @JvmStatic
        fun getInstance(context: Context): DelMessageStore {
            return instance ?: synchronized(this) {
                instance ?: DelMessageStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun <T> safeDbCall(fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: IllegalStateException) {
            if (e.message?.contains("Migration didn't properly handle") == true) {
                resetDatabase()
                try {
                    block()
                } catch (_: Exception) {
                    fallback
                }
            } else {
                fallback
            }
        }
    }

    private fun resetDatabase() {
        DelMessageDatabase.resetInstance()
        context.deleteDatabase("delmessages.db")
        database = DelMessageDatabase.getInstance(context)
        dao = database.delMessageDao()
    }

    fun insertMessage(jid: String, msgid: String, timestamp: Long) {
        safeDbCall(Unit) {
            val message = DelMessage(jid = jid, msgid = msgid, timestamp = timestamp)
            dao.insertMessage(message)
        }
    }

    fun getMessagesByJid(jid: String?): java.util.HashSet<String> {
        if (jid == null) return java.util.HashSet()
        return safeDbCall(java.util.HashSet()) {
            HashSet(dao.getMessagesByJid(jid))
        }
    }

    fun getTimestampByMessageId(msgid: String): Long {
        return safeDbCall(0L) {
            dao.getTimestampByMessageId(msgid) ?: 0L
        }
    }

}
