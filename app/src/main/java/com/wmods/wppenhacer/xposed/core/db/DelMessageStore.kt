package com.wmods.wppenhacer.xposed.core.db

import android.content.Context
import com.wmods.wppenhacer.xposed.core.db.entity.DelMessage

class DelMessageStore private constructor(private val context: Context) {

    private var database = DelMessageDatabase.getInstance(context)
    private var dao = database.delMessageDao()

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
