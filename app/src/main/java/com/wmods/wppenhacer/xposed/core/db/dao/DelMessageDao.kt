package com.wmods.wppenhacer.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.wmods.wppenhacer.xposed.core.db.entity.DelMessage
import com.wmods.wppenhacer.xposed.core.db.entity.DeletedMessageBackupEntity

@Dao
interface DelMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMessage(message: DelMessage)

    @Query("SELECT msgid FROM delmessages WHERE jid = :jid")
    fun getMessagesByJid(jid: String): List<String>

    @Query("SELECT timestamp FROM delmessages WHERE msgid = :msgid LIMIT 1")
    fun getTimestampByMessageId(msgid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDeletedMessageBackup(backup: DeletedMessageBackupEntity)

    @Query("SELECT * FROM deleted_message_backup WHERE _id = :backupId LIMIT 1")
    fun getBackupById(backupId: Long): DeletedMessageBackupEntity?

    @Query(
        "UPDATE deleted_message_backup SET restore_status = :status, restore_error = :error, " +
                "restored_at = :restoredAt WHERE _id = :backupId"
    )
    fun updateRestoreState(backupId: Long, status: String, error: String?, restoredAt: Long)

    @Query("DELETE FROM deleted_message_backup WHERE _id = :backupId")
    fun deleteBackupById(backupId: Long): Int

    @Query("DELETE FROM deleted_message_backup WHERE _id IN (:backupIds)")
    fun deleteBackupsByIds(backupIds: LongArray): Int

    @Query(
        "DELETE FROM deleted_message_backup " +
                "WHERE chat_raw_string IN (:chatKeys) OR chat_phone_raw_string IN (:chatKeys)"
    )
    fun deleteBackupsByChatKeys(chatKeys: List<String>): Int

    @RawQuery(observedEntities = [DeletedMessageBackupEntity::class])
    fun listDeletedMessageBackups(query: SupportSQLiteQuery): List<DeletedMessageBackupEntity>

}
