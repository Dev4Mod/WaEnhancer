package com.wmods.wppenhacer.xposed.core.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.ArrayList;

public class MessageHistory extends SQLiteOpenHelper {
    private static MessageHistory mInstance;
    private SQLiteDatabase dbWrite;

    public MessageHistory(Context context) {
        super(context, "MessageHistory.db", null, 1);
    }

    public static MessageHistory getInstance() {
        synchronized(MessageHistory.class) {
            if (mInstance == null || !mInstance.getReadableDatabase().isOpen()) {
                mInstance = new MessageHistory(Utils.getApplication());
                mInstance.dbWrite = mInstance.getWritableDatabase();
            }
        }
        return mInstance;
    }

    public final void insertMessage(long id, String message, long timestamp) {
        synchronized(this) {
            ContentValues contentValues0 = new ContentValues();
            contentValues0.put("row_id", id);
            contentValues0.put("text_data", message);
            contentValues0.put("editTimestamp", timestamp);
            dbWrite.insert("MessageHistory", null, contentValues0);
        }
    }
    public ArrayList<MessageItem> getMessages(long v) {
        Cursor history = dbWrite.query("MessageHistory", new String[]{"_id", "row_id", "text_data", "editTimestamp"}, "row_id=?", new String[]{String.valueOf(v)}, null, null, null);
        if(history == null) {
            return null;
        }
        if(!history.moveToFirst()) {
            history.close();
            return null;
        }
        ArrayList<MessageItem> messages = new ArrayList<>();
        do {
            long id = history.getLong(history.getColumnIndexOrThrow("row_id"));
            long timestamp = history.getLong(history.getColumnIndexOrThrow("editTimestamp"));
            String message = history.getString(history.getColumnIndexOrThrow("text_data"));
            messages.add(new MessageItem(id, message, timestamp));
        }
        while(history.moveToNext());
        return messages;
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("create table MessageHistory(_id INTEGER PRIMARY KEY AUTOINCREMENT, row_id INTEGER NOT NULL, text_data TEXT NOT NULL, editTimestamp BIGINT DEFAULT 0 );");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
    }

    public static class MessageItem {
        public long id;
        public String message;
        public long timestamp;

        public MessageItem(long id, String message, long timestamp) {
            this.id = id;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}
