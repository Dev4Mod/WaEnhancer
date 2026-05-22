package com.wmods.wppenhacer.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wmods.wppenhacer.xposed.core.db.entity.DelMessage
import com.wmods.wppenhacer.xposed.core.db.entity.DeletedMessage

@Dao
interface DelMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMessage(message: DelMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDeletedMessage(message: DeletedMessage)

    @Query("SELECT * FROM deleted_for_me WHERE chat_jid = :chatJid ORDER BY timestamp ASC")
    fun getDeletedMessagesByChat(chatJid: String): List<DeletedMessage>

    // Para isGroup = false
    @Query("SELECT * FROM deleted_for_me WHERE chat_jid NOT LIKE '%@g.us' ORDER BY timestamp DESC")
    fun getNonGroupDeletedMessages(): List<DeletedMessage>

    // Para isGroup = true
    @Query("SELECT * FROM deleted_for_me WHERE chat_jid LIKE '%@g.us' ORDER BY timestamp DESC")
    fun getGroupDeletedMessages(): List<DeletedMessage>

    @Query("SELECT * FROM deleted_for_me ORDER BY timestamp DESC")
    fun getAllDeletedMessagesInternal(): List<DeletedMessage>

    @Query("DELETE FROM deleted_for_me WHERE key_id = :keyId")
    fun deleteMessage(keyId: String)

    @Query("DELETE FROM deleted_for_me WHERE key_id IN (:keyIds)")
    fun deleteMessages(keyIds: List<String>)

    @Query("DELETE FROM deleted_for_me WHERE chat_jid = :chatJid")
    fun deleteMessagesByChat(chatJid: String)

    @Query("SELECT msgid FROM delmessages WHERE jid = :jid")
    fun getMessagesByJid(jid: String): List<String>

    @Query("SELECT timestamp FROM delmessages WHERE msgid = :msgid LIMIT 1")
    fun getTimestampByMessageId(msgid: String): Long?

    @Query("UPDATE deleted_for_me SET contact_name = :newContactName WHERE chat_jid = :chatJid")
    fun updateContactNameByChat(chatJid: String, newContactName: String)

}