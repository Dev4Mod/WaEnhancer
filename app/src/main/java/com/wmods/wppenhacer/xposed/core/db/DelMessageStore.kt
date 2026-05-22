package com.wmods.wppenhacer.xposed.core.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.wmods.wppenhacer.xposed.core.db.entity.DelMessage
import com.wmods.wppenhacer.xposed.core.db.entity.DeletedMessage

class DelMessageStore private constructor(context: Context) {

    private val database = DelMessageDatabase.getInstance(context)
    private val dao = database.delMessageDao()

    companion object {
        @Volatile
        private var instance: DelMessageStore? = null

        @JvmStatic
        fun getInstance(context: Context): DelMessageStore {
            return instance ?: synchronized(this) {
                instance ?: DelMessageStore(context.applicationContext).also { instance = it }
            }
        }
    }

    fun insertMessage(jid: String, msgid: String, timestamp: Long) {
        val message = DelMessage(jid = jid, msgid = msgid, timestamp = timestamp)
        dao.insertMessage(message)
    }

    fun insertDeletedMessage(message: DeletedMessage) {
        dao.insertDeletedMessage(message)
    }

    fun getDeletedMessagesByChat(chatJid: String): List<DeletedMessage?> {
        return dao.getDeletedMessagesByChat(chatJid)
    }

    fun getAllDeletedMessages(): List<DeletedMessage> {
        return getDeletedMessages(false)
    }

    fun getDeletedMessages(isGroup: Boolean): List<DeletedMessage> {
        return if (isGroup) {
            dao.getGroupDeletedMessages()
        } else {
            dao.getNonGroupDeletedMessages()
        }
    }

    fun getAllDeletedMessagesInternal(): java.util.ArrayList<DeletedMessage> {
        return java.util.ArrayList(dao.getAllDeletedMessagesInternal())
    }

    fun deleteMessage(keyId: String) {
        dao.deleteMessage(keyId)
    }

    fun deleteMessages(keyIds: List<String>?) {
        if (keyIds.isNullOrEmpty()) return
        dao.deleteMessages(keyIds)
    }

    fun deleteMessagesByChat(chatJid: String) {
        dao.deleteMessagesByChat(chatJid)
    }

    fun getMessagesByJid(jid: String?): java.util.HashSet<String> {
        if (jid == null) return java.util.HashSet()
        return java.util.HashSet(dao.getMessagesByJid(jid))
    }

    fun getTimestampByMessageId(msgid: String): Long {
        return dao.getTimestampByMessageId(msgid) ?: 0L
    }

    fun updateContactName(chatJid: String, newContactName: String) {
        dao.updateContactNameByChat(chatJid, newContactName)
    }

    fun insertDeletedMessages(values: ContentValues): Long {
        val dbWrite = database.openHelper.writableDatabase
        return dbWrite.insert(
            "deleted_for_me",
            SQLiteDatabase.CONFLICT_REPLACE,
            values
        )
    }


}