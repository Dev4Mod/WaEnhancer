package com.wmods.wppenhacer.xposed.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wmods.wppenhacer.xposed.core.db.dao.DelMessageDao
import com.wmods.wppenhacer.xposed.core.db.entity.DelMessage
import com.wmods.wppenhacer.xposed.core.db.entity.DeletedMessage

@Database(entities = [DelMessage::class, DeletedMessage::class], version = 11, exportSchema = false)
abstract class DelMessageDatabase : RoomDatabase() {

    abstract fun delMessageDao(): DelMessageDao

    companion object {
        @Volatile
        private var INSTANCE: DelMessageDatabase? = null

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE delmessages ADD COLUMN timestamp INTEGER DEFAULT 0;")
                } catch (_: Exception) {
                }
            }
        }

        private val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS deleted_for_me (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "key_id TEXT, " +
                            "chat_jid TEXT, " +
                            "sender_jid TEXT, " +
                            "timestamp INTEGER, " +
                            "original_timestamp INTEGER DEFAULT 0, " +
                            "media_type INTEGER, " +
                            "text_content TEXT, " +
                            "media_path TEXT, " +
                            "media_caption TEXT, " +
                            "is_from_me INTEGER DEFAULT 0, " +
                            "contact_name TEXT, " +
                            "package_name TEXT, " +
                            "UNIQUE(key_id, chat_jid))"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS deleted_for_me (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "key_id TEXT, " +
                            "chat_jid TEXT, " +
                            "sender_jid TEXT, " +
                            "timestamp INTEGER, " +
                            "original_timestamp INTEGER DEFAULT 0, " +
                            "media_type INTEGER, " +
                            "text_content TEXT, " +
                            "media_path TEXT, " +
                            "media_caption TEXT, " +
                            "is_from_me INTEGER DEFAULT 0, " +
                            "contact_name TEXT, " +
                            "package_name TEXT, " +
                            "UNIQUE(key_id, chat_jid))"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE deleted_for_me ADD COLUMN is_from_me INTEGER DEFAULT 0;")
                } catch (_: Exception) {
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE deleted_for_me ADD COLUMN contact_name TEXT;")
                } catch (_: Exception) {
                }
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE deleted_for_me ADD COLUMN package_name TEXT DEFAULT 'com.whatsapp';")
                } catch (_: Exception) {
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE deleted_for_me ADD COLUMN original_timestamp INTEGER DEFAULT 0;")
                } catch (_: Exception) {
                }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- Fix delmessages table ---
                db.execSQL(
                    "CREATE TABLE delmessages_new (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "jid TEXT, " +
                            "msgid TEXT, " +
                            "timestamp INTEGER DEFAULT 0)"
                )
                db.execSQL(
                    "INSERT INTO delmessages_new (_id, jid, msgid, timestamp) " +
                            "SELECT _id, jid, msgid, timestamp FROM delmessages"
                )
                db.execSQL("DROP TABLE delmessages")
                db.execSQL("ALTER TABLE delmessages_new RENAME TO delmessages")
                db.execSQL("CREATE UNIQUE INDEX index_delmessages_jid_msgid ON delmessages (jid, msgid)")

                // --- Fix deleted_for_me table ---
                db.execSQL(
                    "CREATE TABLE deleted_for_me_new (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "key_id TEXT, " +
                            "chat_jid TEXT, " +
                            "sender_jid TEXT, " +
                            "timestamp INTEGER, " +
                            "original_timestamp INTEGER DEFAULT 0, " +
                            "media_type INTEGER, " +
                            "text_content TEXT, " +
                            "media_path TEXT, " +
                            "media_caption TEXT, " +
                            "is_from_me INTEGER DEFAULT 0, " +
                            "contact_name TEXT, " +
                            "package_name TEXT DEFAULT 'com.whatsapp')"
                )
                db.execSQL(
                    "INSERT INTO deleted_for_me_new (" +
                            "_id, key_id, chat_jid, sender_jid, timestamp, original_timestamp, " +
                            "media_type, text_content, media_path, media_caption, is_from_me, " +
                            "contact_name, package_name) " +
                            "SELECT _id, key_id, chat_jid, sender_jid, timestamp, original_timestamp, " +
                            "media_type, text_content, media_path, media_caption, is_from_me, " +
                            "contact_name, COALESCE(package_name, 'com.whatsapp') FROM deleted_for_me"
                )
                db.execSQL("DROP TABLE deleted_for_me")
                db.execSQL("ALTER TABLE deleted_for_me_new RENAME TO deleted_for_me")
                db.execSQL("CREATE UNIQUE INDEX index_deleted_for_me_key_id_chat_jid ON deleted_for_me (key_id, chat_jid)")
            }
        }

        fun getInstance(context: Context): DelMessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DelMessageDatabase::class.java,
                    "delmessages.db"
                )
                    .addMigrations(
                        MIGRATION_1_4,
                        MIGRATION_4_6,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11
                    )
                    .fallbackToDestructiveMigrationOnDowngrade(false)
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
