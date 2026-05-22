package com.wmods.wppenhacer.xposed.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wmods.wppenhacer.xposed.core.db.dao.HideSeenDao
import com.wmods.wppenhacer.xposed.core.db.dao.MessageDao
import com.wmods.wppenhacer.xposed.core.db.entity.HideSeenEntity
import com.wmods.wppenhacer.xposed.core.db.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, HideSeenEntity::class],
    version = 5,
    exportSchema = false
)
abstract class MessageHistoryDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun hideSeenDao(): HideSeenDao
}
