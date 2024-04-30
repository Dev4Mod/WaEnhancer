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
        super(context, "delmessages.db", null, 1);
    }

    public static DelMessageStore getInstance(Context ctx) {
        synchronized (DelMessageStore.class) {
            if (mInstance == null) {
                mInstance = new DelMessageStore(ctx);
            }
        }
        return mInstance;
    }

    public void insertMessage(String jid, String msgid) {
        SQLiteDatabase dbWrite = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("jid", jid);
        values.put("msgid", msgid);
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


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS delmessages (_id INTEGER PRIMARY KEY AUTOINCREMENT, jid TEXT, msgid TEXT, UNIQUE(jid, msgid))");
    }


    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS delmessages");
        sqLiteDatabase.execSQL("CREATE TABLE IF NOT EXISTS delmessages (_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT, msgid TEXT)");
    }
}
