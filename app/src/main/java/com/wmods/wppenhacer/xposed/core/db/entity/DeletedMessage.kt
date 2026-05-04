package com.wmods.wppenhacer.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deleted_for_me",
    indices = [Index(value = ["key_id", "chat_jid"], unique = true)]
)
data class DeletedMessage(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = "key_id")
    var keyId: String? = null,

    @ColumnInfo(name = "chat_jid")
    var chatJid: String? = null,

    @ColumnInfo(name = "sender_jid")
    var senderJid: String? = null,

    @ColumnInfo(name = "timestamp")
    var timestamp: Long? = null,

    @ColumnInfo(name = "original_timestamp", defaultValue = "0")
    var originalTimestamp: Long? = 0L,

    @ColumnInfo(name = "media_type")
    var mediaType: Int? = null,

    @ColumnInfo(name = "text_content")
    var textContent: String? = null,

    @ColumnInfo(name = "media_path")
    var mediaPath: String? = null,

    @ColumnInfo(name = "media_caption")
    var mediaCaption: String? = null,

    // Mantemos como Int para alinhar perfeitamente com a coluna "INTEGER DEFAULT 0" do SQLite antigo
    @ColumnInfo(name = "is_from_me", defaultValue = "0")
    var isFromMeInt: Int? = 0,

    @ColumnInfo(name = "contact_name")
    var contactName: String? = null,

    @ColumnInfo(name = "package_name", defaultValue = "'com.whatsapp'")
    var packageName: String? = "com.whatsapp"
) {
    // Getter prático para manter a compatibilidade com o seu código anterior
    fun isFromMe(): Boolean {
        return isFromMeInt == 1
    }

    fun setFromMe(isFrom: Boolean) {
        this.isFromMeInt = if (isFrom) 1 else 0
    }
}