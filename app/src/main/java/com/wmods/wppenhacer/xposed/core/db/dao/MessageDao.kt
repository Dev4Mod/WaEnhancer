package com.wmods.wppenhacer.xposed.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wmods.wppenhacer.xposed.core.db.entity.MessageEntity

@Dao
interface MessageDao {
    @Insert
    fun insert(message: MessageEntity)

    @Query("SELECT _id, row_id, text_data, editTimestamp FROM MessageHistory WHERE row_id = :rowId")
    fun getMessagesByRowId(rowId: Long): List<MessageEntity>
}