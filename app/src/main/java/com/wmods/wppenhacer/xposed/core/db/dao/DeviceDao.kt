package com.wmods.wppenhacer.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wmods.wppenhacer.xposed.core.db.entity.DeviceEntity

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(entity: DeviceEntity): Long

    @Query("SELECT device_type FROM device_history WHERE message_id = :messageId LIMIT 1")
    fun getDeviceTypeByMessageId(messageId: String): Int?

    @Query("SELECT device_type FROM device_history WHERE userjid = :userjid AND message_id = :messageId LIMIT 1")
    fun getDeviceType(userjid: String, messageId: String): Int?

    @Query("DELETE FROM device_history WHERE message_id IN (:messageIds)")
    fun deleteByMessageIds(messageIds: List<String>): Int
}
