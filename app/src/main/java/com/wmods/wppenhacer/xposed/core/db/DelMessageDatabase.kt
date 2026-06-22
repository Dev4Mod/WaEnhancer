package com.wmods.wppenhacer.xposed.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wmods.wppenhacer.xposed.core.db.dao.DelMessageDao
import com.wmods.wppenhacer.xposed.core.db.entity.DelMessage

@Database(entities = [DelMessage::class], version = 12, exportSchema = false)
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
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
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

            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS deleted_for_me")
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
                        MIGRATION_10_11,
                        MIGRATION_11_12
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
