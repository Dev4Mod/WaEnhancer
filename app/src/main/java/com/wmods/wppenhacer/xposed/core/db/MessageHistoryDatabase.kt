package com.wmods.wppenhacer.xposed.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wmods.wppenhacer.xposed.core.db.dao.DeviceDao
import com.wmods.wppenhacer.xposed.core.db.dao.HideSeenDao
import com.wmods.wppenhacer.xposed.core.db.dao.MessageDao
import com.wmods.wppenhacer.xposed.core.db.entity.DeviceEntity
import com.wmods.wppenhacer.xposed.core.db.entity.HideSeenEntity
import com.wmods.wppenhacer.xposed.core.db.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, HideSeenEntity::class, DeviceEntity::class],
    version = 7,
    exportSchema = false
)
abstract class MessageHistoryDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun hideSeenDao(): HideSeenDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS device_history (
                        _id INTEGER PRIMARY KEY AUTOINCREMENT,
                        userjid TEXT NOT NULL,
                        message_id TEXT NOT NULL,
                        device_type INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_device_userjid_message_id_unique " +
                            "ON device_history (userjid, message_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_device_message_id " +
                            "ON device_history (message_id)"
                )
            }
        }
    }
}
