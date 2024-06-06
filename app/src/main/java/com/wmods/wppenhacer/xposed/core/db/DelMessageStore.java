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

    private DelMessageStore(@NonNull Context context) {
        super(context, "delmessages.db", null, 4);
    }

    public static DelMessageStore getInstance(Context ctx) {
        synchronized (DelMessageStore.class) {
            if (mInstance == null) {
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
    }

    public void insertMessage(String jid, String msgid, long timestamp) {
        SQLiteDatabase dbWrite = this.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("jid", jid);
            values.put("msgid", msgid);
            values.put("timestamp", timestamp);
            dbWrite.insert("delmessages", null, values);
        } finally {
            dbWrite.close();
        }
    }

    public HashSet<String> getMessagesByJid(String jid) {
        SQLiteDatabase dbReader = this.getReadableDatabase();
        Cursor query = dbReader.query("delmessages", new String[]{"_id", "jid", "msgid"}, "jid=?", new String[]{jid}, null, null, null);
        HashSet<String> messages = new HashSet<>();
        try {
            if (query.moveToFirst()) {
                do {
                    messages.add(query.getString(query.getColumnIndexOrThrow("msgid")));
                } while (query.moveToNext());
            }
        } finally {
            query.close();
            dbReader.close();
        }
        return messages;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS delmessages (_id INTEGER PRIMARY KEY AUTOINCREMENT, jid TEXT, msgid TEXT, timestamp INTEGER DEFAULT 0, UNIQUE(jid, msgid))");
    }

    public long getTimestampByMessageId(String msgid) {
        SQLiteDatabase dbReader = this.getReadableDatabase();
        Cursor query = dbReader.query("delmessages", new String[]{"timestamp"}, "msgid=?", new String[]{msgid}, null, null, null);
        try {
            if (query.moveToFirst()) {
                return query.getLong(query.getColumnIndexOrThrow("timestamp"));
            }
            return 0;
        } finally {
            query.close();
            dbReader.close();
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