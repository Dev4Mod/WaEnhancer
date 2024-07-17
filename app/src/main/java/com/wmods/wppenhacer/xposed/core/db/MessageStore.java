package com.wmods.wppenhacer.xposed.core.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

public class MessageStore {


    private static MessageStore mInstance;

    private SQLiteDatabase sqLiteDatabase;

    private MessageStore() {
        var dataDir = Utils.getApplication().getFilesDir().getParentFile();
        var dbFile = new File(dataDir, "/databases/msgstore.db");
        if (!dbFile.exists()) return;
        sqLiteDatabase = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
    }

    public static MessageStore getInstance() {
        synchronized (MessageStore.class) {
            if (mInstance == null || mInstance.sqLiteDatabase == null || !mInstance.sqLiteDatabase.isOpen()) {
                mInstance = new MessageStore();
            }
        }
        return mInstance;
    }

    public String getMessageById(long id) {
        if (sqLiteDatabase == null) return "";
        String message = "";
        Cursor cursor = null;
        try {
            String[] columns = new String[]{"c0content"};
            String selection = "docid=?";
            String[] selectionArgs = new String[]{String.valueOf(id)};

            cursor = sqLiteDatabase.query("message_ftsv2_content", columns, selection, selectionArgs, null, null, null);
            if (cursor.moveToFirst()) {
                message = cursor.getString(cursor.getColumnIndexOrThrow("c0content"));
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return message;
    }

    public String getOriginalMessageKey(long id) {
        if (sqLiteDatabase == null) return "";
        String message = "";
        try (Cursor cursor = sqLiteDatabase.rawQuery("SELECT parent_message_row_id, key_id FROM message_add_on WHERE parent_message_row_id=\"" + id + "\"", null)) {
            if (cursor.moveToFirst()) {
                message = cursor.getString(1);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return message;
    }

//    public String getMessageKeyByID(long id) {
//        if (sqLiteDatabase == null) return "";
//        String message = "";
//        try (Cursor cursor = sqLiteDatabase.rawQuery("SELECT _id, key_id FROM message WHERE _id=\"" + id + "\"", null)) {
//            if (cursor.moveToFirst()) {
//                message = cursor.getString(1);
//            }
//        } catch (Exception e) {
//            XposedBridge.log(e);
//        } finally {
//            sqLiteDatabase.close();
//        }
//        return message;
//    }

    public List<String> getAudioListByMessageList(List<String> messageList) {
        if (sqLiteDatabase == null) return new ArrayList<>();
        List<String> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            String sql = "SELECT key_id, message_type FROM message WHERE key_id IN (";
            sql += String.join(",", messageList.stream().map(m -> "\"" + m + "\"").toArray(String[]::new));
            sql += ")";
            cursor = sqLiteDatabase.rawQuery(sql, null);
            if (cursor.moveToFirst()) {
                do {
                    int type = cursor.getInt(1);
                    if (type == 2) {
                        list.add(cursor.getString(0));
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    public synchronized void executeSQL(String sql) {
        try {
            if (sqLiteDatabase == null) return;
            sqLiteDatabase.execSQL(sql);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public void storeMessageRead(String messageId) {
        if (sqLiteDatabase == null) return;
        XposedBridge.log("storeMessageRead: " + messageId);
        sqLiteDatabase.execSQL("UPDATE message SET status = 1 WHERE key_id = \"" + messageId + "\"");
    }

    public boolean isReadMessageStatus(String messageId) {
        if (sqLiteDatabase == null) return false;
        boolean result = false;
        Cursor cursor = null;
        try {
            String[] columns = new String[]{"status"};
            String selection = "key_id=?";
            String[] selectionArgs = new String[]{messageId};

            cursor = sqLiteDatabase.query("message", columns, selection, selectionArgs, null, null, null);
            if (cursor.moveToFirst()) {
                result = cursor.getInt(cursor.getColumnIndexOrThrow("status")) == 1;
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    public SQLiteDatabase getDatabase() {
        return sqLiteDatabase;
    }
}
