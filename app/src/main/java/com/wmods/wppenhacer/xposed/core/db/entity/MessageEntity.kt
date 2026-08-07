package com.wmods.wppenhacer.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "MessageHistory",
    indices = [Index(name = "idx_message_history_row_id", value = ["row_id"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long? = null,

    @ColumnInfo(name = "row_id")
    val rowId: Long,

    @ColumnInfo(name = "text_data")
    val textData: String,

    @ColumnInfo(name = "editTimestamp", defaultValue = "0")
    val editTimestamp: Long? = 0L
)
