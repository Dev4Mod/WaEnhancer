package com.wmods.wppenhacer.xposed.core.db

import android.content.Context
import com.wmods.wppenhacer.xposed.core.db.entity.DelMessage

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

    fun getMessagesByJid(jid: String?): java.util.HashSet<String> {
        if (jid == null) return java.util.HashSet()
        return HashSet(dao.getMessagesByJid(jid))
    }

    fun getTimestampByMessageId(msgid: String): Long {
        return dao.getTimestampByMessageId(msgid) ?: 0L
    }

}
