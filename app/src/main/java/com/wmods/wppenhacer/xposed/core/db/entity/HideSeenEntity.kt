package com.wmods.wppenhacer.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hide_seen_messages",
    indices = [
        Index(
            name = "idx_hide_seen_jid_message_unique",
            value = ["jid", "message_id"],
            unique = true
        ),
        Index(name = "idx_hide_seen_jid_read", value = ["jid", "read"]),
        Index(name = "idx_hide_seen_jid_played", value = ["jid", "played"])
    ]
)
data class HideSeenEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long? = null,

    @ColumnInfo(name = "jid") val jid: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "read", defaultValue = "0") val read: Int = 0,
    @ColumnInfo(name = "played", defaultValue = "0") val played: Int = 0
)