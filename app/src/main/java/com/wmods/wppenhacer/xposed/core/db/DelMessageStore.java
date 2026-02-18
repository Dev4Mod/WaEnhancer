package com.wmods.wppenhacer.xposed.core.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import java.util.HashSet;

public class DelMessageStore extends SQLiteOpenHelper {
    private static DelMessageStore mInstance;

    private static final int DATABASE_VERSION = 8;
    public static final String TABLE_DELETED_FOR_ME = "deleted_for_me";

    private DelMessageStore(@NonNull Context context) {
        super(context, "delmessages.db", null, DATABASE_VERSION);
    }

    public static DelMessageStore getInstance(Context ctx) {
        synchronized (DelMessageStore.class) {
            if (mInstance == null || !mInstance.getWritableDatabase().isOpen()) {
                mInstance = new DelMessageStore(ctx);
            }
        }
        return mInstance;
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion < 4) {
            if (!checkColumnExists(sqLiteDatabase, "delmessages", "timestamp")) {
                sqLiteDatabase.execSQL("ALTER TABLE delmessages ADD COLUMN timestamp INTEGER DEFAULT 0;");
            }
        }
        if (oldVersion < 6) {
            createDeletedForMeTable(sqLiteDatabase);
        }
        if (oldVersion < 7) {
             if (!checkColumnExists(sqLiteDatabase, TABLE_DELETED_FOR_ME, "is_from_me")) {
                try {
                    sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_DELETED_FOR_ME + " ADD COLUMN is_from_me INTEGER DEFAULT 0;");
                } catch (Exception e) {
                    // Ignore if fails
                }
            }
        }
        if (oldVersion < 8) {
             if (!checkColumnExists(sqLiteDatabase, TABLE_DELETED_FOR_ME, "contact_name")) {
                try {
                    sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_DELETED_FOR_ME + " ADD COLUMN contact_name TEXT;");
                } catch (Exception e) {
                    // Ignore if fails
                }
            }
        }
    }

    private void createDeletedForMeTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DELETED_FOR_ME + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "key_id TEXT, " +
                "chat_jid TEXT, " +
                "sender_jid TEXT, " +
                "timestamp INTEGER, " +
                "media_type INTEGER, " +
                "text_content TEXT, " +
                "media_path TEXT, " +
                "media_caption TEXT, " +
                "is_from_me INTEGER DEFAULT 0, " +
                "contact_name TEXT, " +
                "UNIQUE(key_id, chat_jid))");
    }

    public void insertMessage(String jid, String msgid, long timestamp) {
        try (SQLiteDatabase dbWrite = this.getWritableDatabase()) {
            ContentValues values = new ContentValues();
            values.put("jid", jid);
            values.put("msgid", msgid);
            values.put("timestamp", timestamp);
            dbWrite.insertWithOnConflict("delmessages", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    public void insertDeletedMessage(DeletedMessage message) {
        try (SQLiteDatabase dbWrite = this.getWritableDatabase()) {
            ContentValues values = new ContentValues();
            values.put("key_id", message.getKeyId());
            values.put("chat_jid", message.getChatJid());
            values.put("sender_jid", message.getSenderJid());
            values.put("timestamp", message.getTimestamp());
            values.put("media_type", message.getMediaType());
            values.put("text_content", message.getTextContent());
            values.put("media_path", message.getMediaPath());
            values.put("media_caption", message.getMediaCaption());
            values.put("is_from_me", message.isFromMe() ? 1 : 0);
            values.put("contact_name", message.getContactName());
            dbWrite.insertWithOnConflict(TABLE_DELETED_FOR_ME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    public java.util.ArrayList<DeletedMessage> getDeletedMessagesByChat(String chatJid) {
        java.util.ArrayList<DeletedMessage> messages = new java.util.ArrayList<>();
        SQLiteDatabase dbReader = this.getReadableDatabase();
        try (dbReader; Cursor cursor = dbReader.query(TABLE_DELETED_FOR_ME, null, "chat_jid=?", new String[]{chatJid}, null, null, "timestamp ASC")) { 
            if (cursor.moveToFirst()) {
                do {
                    messages.add(new DeletedMessage(
                            cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                            cursor.getString(cursor.getColumnIndexOrThrow("key_id")),
                            cursor.getString(cursor.getColumnIndexOrThrow("chat_jid")),
                            cursor.getString(cursor.getColumnIndexOrThrow("sender_jid")),
                            cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("media_type")),
                            cursor.getString(cursor.getColumnIndexOrThrow("text_content")),
                            cursor.getString(cursor.getColumnIndexOrThrow("media_path")),
                            cursor.getString(cursor.getColumnIndexOrThrow("media_caption")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("is_from_me")) == 1,
                            cursor.getString(cursor.getColumnIndexOrThrow("contact_name"))
                    ));
                } while (cursor.moveToNext());
            }
        }
        return messages;
    }

    public java.util.ArrayList<DeletedMessage> getAllDeletedMessages() {
        return getDeletedMessages(false);
    }

    public java.util.ArrayList<DeletedMessage> getDeletedMessages(boolean isGroup) {
        java.util.ArrayList<DeletedMessage> messages = new java.util.ArrayList<>();
        SQLiteDatabase dbReader = this.getReadableDatabase();
        String selection = isGroup ? "chat_jid LIKE '%@g.us'" : "chat_jid NOT LIKE '%@g.us'";
        try (dbReader; Cursor cursor = dbReader.query(TABLE_DELETED_FOR_ME, null, selection, null, null, null, "timestamp DESC")) {
            if (cursor.moveToFirst()) {
                do {
                    messages.add(new DeletedMessage(
                            cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                            cursor.getString(cursor.getColumnIndexOrThrow("key_id")),
                            cursor.getString(cursor.getColumnIndexOrThrow("chat_jid")),
                            cursor.getString(cursor.getColumnIndexOrThrow("sender_jid")),
                            cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("media_type")),
                            cursor.getString(cursor.getColumnIndexOrThrow("text_content")),
                            cursor.getString(cursor.getColumnIndexOrThrow("media_path")),
                            cursor.getString(cursor.getColumnIndexOrThrow("media_caption")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("is_from_me")) == 1,
                            cursor.getString(cursor.getColumnIndexOrThrow("contact_name"))
                    ));
                } while (cursor.moveToNext());
            }
        }
        return messages;
    }

    public java.util.ArrayList<DeletedMessage> getAllDeletedMessagesInternal() {
        java.util.ArrayList<DeletedMessage> messages = new java.util.ArrayList<>();
        SQLiteDatabase dbReader = this.getReadableDatabase();
        try (dbReader; Cursor cursor = dbReader.query(TABLE_DELETED_FOR_ME, null, null, null, null, null, "timestamp DESC")) {
            if (cursor.moveToFirst()) {
                do {
                    messages.add(new DeletedMessage(
                            cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                            cursor.getString(cursor.getColumnIndexOrThrow("key_id")),
                            cursor.getString(cursor.getColumnIndexOrThrow("chat_jid")),
                            cursor.getString(cursor.getColumnIndexOrThrow("sender_jid")),
                            cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("media_type")),
                            cursor.getString(cursor.getColumnIndexOrThrow("text_content")),
                            cursor.getString(cursor.getColumnIndexOrThrow("media_path")),
                            cursor.getString(cursor.getColumnIndexOrThrow("media_caption")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("is_from_me")) == 1,
                            cursor.getString(cursor.getColumnIndexOrThrow("contact_name"))
                    ));
                } while (cursor.moveToNext());
            }
        }
        return messages;
    }

    public void deleteMessage(String keyId) {
        try (SQLiteDatabase dbWrite = this.getWritableDatabase()) {
            dbWrite.delete(TABLE_DELETED_FOR_ME, "key_id=?", new String[]{keyId});
        }
    }


    public HashSet<String> getMessagesByJid(String jid) {
        HashSet<String> messages = new HashSet<>();
        if (jid == null) return messages;
        SQLiteDatabase dbReader = this.getReadableDatabase();
        try (dbReader; Cursor query = dbReader.query("delmessages", new String[]{"_id", "jid", "msgid"}, "jid=?", new String[]{jid}, null, null, null)) {
            if (query.moveToFirst()) {
                do {
                    messages.add(query.getString(query.getColumnIndexOrThrow("msgid")));
                } while (query.moveToNext());
            }
        }
        return messages;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS delmessages (_id INTEGER PRIMARY KEY AUTOINCREMENT, jid TEXT, msgid TEXT, timestamp INTEGER DEFAULT 0, UNIQUE(jid, msgid))");
        createDeletedForMeTable(sqLiteDatabase);
    }

    public long getTimestampByMessageId(String msgid) {
        SQLiteDatabase dbReader = this.getReadableDatabase();
        try (dbReader; Cursor query = dbReader.query("delmessages", new String[]{"timestamp"}, "msgid=?", new String[]{msgid}, null, null, null)) {
            if (query.moveToFirst()) {
                return query.getLong(query.getColumnIndexOrThrow("timestamp"));
            }
            return 0;
        }
    }

    private boolean checkColumnExists(SQLiteDatabase db, String tableName, String columnName) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    String currentColumnName = cursor.getString(nameIndex);
                    if (columnName.equals(currentColumnName)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}