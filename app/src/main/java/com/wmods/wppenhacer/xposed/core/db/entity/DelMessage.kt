package com.wmods.wppenhacer.xposed.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "delmessages",
    indices = [Index(value = ["jid", "msgid"], unique = true)]
)
data class DelMessage(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = "jid")
    var jid: String? = null,

    @ColumnInfo(name = "msgid")
    var msgid: String? = null,

    @ColumnInfo(name = "timestamp", defaultValue = "0")
    var timestamp: Long? = 0L
)
