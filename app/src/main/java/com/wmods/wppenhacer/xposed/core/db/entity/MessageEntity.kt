package com.wmods.wppenhacer.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "MessageHistory")
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
