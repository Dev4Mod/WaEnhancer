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
        super(context, "delmessages.db", null, 3);
    }

    public static DelMessageStore getInstance(Context ctx) {
        synchronized (DelMessageStore.class) {
            if (mInstance == null) {
                mInstance = new DelMessageStore(ctx);
            }
        }
        return mInstance;
    }

    public void insertMessage(String jid, String msgid,long timestamp) {
        SQLiteDatabase dbWrite = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("jid", jid);
        values.put("msgid", msgid);
        values.put("timestamp", timestamp);
        dbWrite.insert("delmessages", null, values);
    }


    public HashSet<String> getMessagesByJid(String jid) {
        SQLiteDatabase dbReader = this.getReadableDatabase();
        Cursor query = dbReader.query("delmessages", new String[]{"_id", "jid", "msgid"}, "jid=?", new String[]{jid}, null, null, null);
        if (!query.moveToFirst()) {
            query.close();
            return null;
        }
        var messages = new HashSet<String>();
        do {
            messages.add(query.getString(query.getColumnIndexOrThrow("msgid")));
        } while (query.moveToNext());
        query.close();
        return messages;
    }

    public long getTimestampByMessageId(String msgid) {
        SQLiteDatabase dbReader = this.getReadableDatabase();
        Cursor query = dbReader.query("delmessages", new String[]{"timestamp"}, "msgid=?", new String[]{msgid}, null, null, null);
        if (!query.moveToFirst()) {
            query.close();
            return 0;
        }
        return query.getLong(query.getColumnIndexOrThrow("timestamp"));
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS delmessages (_id INTEGER PRIMARY KEY AUTOINCREMENT, jid TEXT, msgid TEXT, timestamp INTEGER DEFAULT 0, UNIQUE(jid, msgid))");
    }


    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            sqLiteDatabase.execSQL("ALTER TABLE delmessages ADD COLUMN timestamp INTEGER DEFAULT 0;");
        }
    }
}
