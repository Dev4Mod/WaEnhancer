package com.wmods.wppenhacer.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_history",
    indices = [
        Index(
            name = "idx_device_userjid_message_id_unique",
            value = ["userjid", "message_id"],
            unique = true
        ),
        Index(name = "idx_device_message_id", value = ["message_id"])
    ]
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long? = null,

    @ColumnInfo(name = "userjid") val userjid: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "device_type") val deviceType: Int
)
