package com.wmods.wppenhacer.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wmods.wppenhacer.xposed.core.db.RestoreState

@Entity(
    tableName = "deleted_message_backup",
    indices = [
        Index(
            value = ["message_unique_hash"],
            unique = true
        ),
        Index(
            value = ["restore_status", "deleted_at"],
            name = "idx_deleted_backup_status_deleted_at"
        )
    ]
)
data class DeletedMessageBackupEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0L,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long,

    @ColumnInfo(name = "restore_status")
    val restoreStatus: String = RestoreState.Pending.value,

    @ColumnInfo(name = "restore_error")
    val restoreError: String? = null,

    @ColumnInfo(name = "restored_at")
    val restoredAt: Long = 0L,

    @ColumnInfo(name = "message_unique_hash")
    val messageUniqueHash: String,

    @ColumnInfo(name = "key_id")
    val keyId: String,

    @ColumnInfo(name = "chat_raw_string")
    val chatRawString: String? = null,

    @ColumnInfo(name = "chat_phone_raw_string")
    val chatPhoneRawString: String? = null,

    @ColumnInfo(name = "sender_raw_string")
    val senderRawString: String? = null,

    @ColumnInfo(name = "sender_phone_raw_string")
    val senderPhoneRawString: String? = null,

    @ColumnInfo(name = "from_me")
    val fromMe: Int? = null,

    @ColumnInfo(name = "message_type")
    val messageType: Int? = null,

    @ColumnInfo(name = "message_timestamp")
    val messageTimestamp: Long? = null,

    @ColumnInfo(name = "message_json")
    val messageJson: String,

    @ColumnInfo(name = "message_media_json")
    val messageMediaJson: String? = null,

    @ColumnInfo(name = "message_text_json")
    val messageTextJson: String? = null
)