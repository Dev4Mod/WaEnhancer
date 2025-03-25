package com.wmods.wppenhacer.xposed.core.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;

public class MessageHistory extends SQLiteOpenHelper {
    private static MessageHistory mInstance;
    private SQLiteDatabase dbWrite;

    public enum MessageType {
        MESSAGE_TYPE,
        VIEW_ONCE_TYPE
    }

    public MessageHistory(Context context) {
        super(context, "MessageHistory.db", null, 2);
    }

    public static MessageHistory getInstance() {
        synchronized (MessageHistory.class) {
            if (mInstance == null || !mInstance.getReadableDatabase().isOpen()) {
                mInstance = new MessageHistory(Utils.getApplication());
                mInstance.dbWrite = mInstance.getWritableDatabase();
            }
        }
        return mInstance;
    }

    public final void insertMessage(long id, String message, long timestamp) {
        synchronized (this) {
            ContentValues contentValues0 = new ContentValues();
            contentValues0.put("row_id", id);
            contentValues0.put("text_data", message);
            contentValues0.put("editTimestamp", timestamp);
            dbWrite.insert("MessageHistory", null, contentValues0);
        }
    }

    public ArrayList<MessageItem> getMessages(long v) {
        Cursor history = dbWrite.query("MessageHistory", new String[]{"_id", "row_id", "text_data", "editTimestamp"}, "row_id=?", new String[]{String.valueOf(v)}, null, null, null);
        if (!history.moveToFirst()) {
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
        while (history.moveToNext());
        history.close();
        return messages;
    }

    public final void insertHideSeenMessage(String jid, String message_id, MessageType type, boolean viewed) {
        synchronized (this) {
            if (updateViewedMessage(jid, message_id, type, viewed)) {
                return;
            }
            ContentValues content = new ContentValues();
            content.put("jid", jid);
            content.put("message_id", message_id);
            content.put("type", type.ordinal());
            dbWrite.insert("hide_seen_messages", null, content);
        }
    }

    public boolean updateViewedMessage(String jid, String message_id, MessageType type, boolean viewed) {
        Cursor cursor = dbWrite.query("hide_seen_messages", new String[]{"_id"}, "jid=? AND message_id=? AND type =?", new String[]{jid, message_id, String.valueOf(type.ordinal())}, null, null, null);
        if (!cursor.moveToFirst()) {
            cursor.close();
            return false;
        }
        synchronized (this) {
            ContentValues content = new ContentValues();
            content.put("viewed", viewed ? 1 : 0);
            dbWrite.update("hide_seen_messages", content, "_id=?", new String[]{cursor.getString(cursor.getColumnIndexOrThrow("_id"))});
        }
        cursor.close();
        return true;
    }

    public MessageSeenItem getHideSeenMessage(String jid, String message_id, MessageType type) {
        Cursor cursor = dbWrite.query("hide_seen_messages", new String[]{"viewed"}, "jid=? AND message_id=? AND type=?", new String[]{jid, message_id, String.valueOf(type.ordinal())}, null, null, null);
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        var viewed = cursor.getInt(cursor.getColumnIndexOrThrow("viewed")) == 1;
        var message = new MessageSeenItem(jid, message_id, viewed);
        cursor.close();
        return message;
    }

    public List<MessageSeenItem> getHideSeenMessages(String jid, MessageType type, boolean viewed) {
        Cursor cursor = dbWrite.query("hide_seen_messages", new String[]{"jid", "message_id", "viewed"}, "jid=? AND type=? AND viewed=?", new String[]{jid, String.valueOf(type.ordinal()), viewed ? "1" : "0"}, null, null, null);
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        ArrayList<MessageSeenItem> messages = new ArrayList<>();
        do {
            var message_id = cursor.getString(cursor.getColumnIndexOrThrow("message_id"));
            var message = new MessageSeenItem(jid, message_id, viewed);
            messages.add(message);
        } while (cursor.moveToNext());
        cursor.close();
        return messages;
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("create table MessageHistory(_id INTEGER PRIMARY KEY AUTOINCREMENT, row_id INTEGER NOT NULL, text_data TEXT NOT NULL, editTimestamp BIGINT DEFAULT 0 );");
        sqLiteDatabase.execSQL("create table hide_seen_messages(_id INTEGER PRIMARY KEY AUTOINCREMENT, jid TEXT NOT NULL, message_id TEXT NOT NULL,type INT NOT NULL, viewed INT DEFAULT 0);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            sqLiteDatabase.execSQL("create table hide_seen_messages(_id INTEGER PRIMARY KEY AUTOINCREMENT, jid TEXT NOT NULL, message_id TEXT NOT NULL,type INT NOT NULL, viewed INT DEFAULT 0);");
        }
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

    public static class MessageSeenItem {
        public String jid;
        public String message;
        public boolean viewed;
        private FMessageWpp fMessageWpp;

        public MessageSeenItem(String jid, String message, boolean viewed) {
            this.jid = jid;
            this.message = message;
            this.viewed = viewed;
        }

        @Nullable
        public FMessageWpp getFMessage() {
            if (fMessageWpp == null) {
                try {
                    var key = XposedHelpers.newInstance(FMessageWpp.Key.TYPE, WppCore.createUserJid(jid), message, false);
                    var fmessageObj = WppCore.getFMessageFromKey(key);
                    fMessageWpp = new FMessageWpp(fmessageObj);
                } catch (Exception ignored) {

                }
            }
            return fMessageWpp;
        }


    }


}
