package com.wmods.wppenhacer.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wmods.wppenhacer.xposed.core.db.entity.DelMessage

@Dao
interface DelMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMessage(message: DelMessage)

    @Query("SELECT msgid FROM delmessages WHERE jid = :jid")
    fun getMessagesByJid(jid: String): List<String>

    @Query("SELECT timestamp FROM delmessages WHERE msgid = :msgid LIMIT 1")
    fun getTimestampByMessageId(msgid: String): Long?

}
