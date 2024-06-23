package com.wmods.wppenhacer.xposed.core.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

public class MessageStore {


    private static SQLiteDatabase database;

    public static void initDatabase() {
        if (database != null) return;
        var dataDir = Utils.getApplication().getFilesDir().getParentFile();
        var dbFile = new File(dataDir, "/databases/msgstore.db");
        if (!dbFile.exists()) return;
        database = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
    }

    public static void closeDatabase() {
        if (database == null) return;
        database.close();
        database = null;
    }

    public static SQLiteDatabase getDatabase() {
        initDatabase();
        return database;
    }

    public static String getMessageById(long id) {
        initDatabase();
        String message = "";
        try {
            String[] columns = new String[]{"c0content"};
            String selection = "docid=?";
            String[] selectionArgs = new String[]{String.valueOf(id)};

            Cursor cursor = database.query("message_ftsv2_content", columns, selection, selectionArgs, null, null, null);
            if (cursor.moveToFirst()) {
                message = cursor.getString(cursor.getColumnIndexOrThrow("c0content"));
            }
            cursor.close();
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return message;
    }

    public static String getOriginalMessageKey(long id) {
        initDatabase();
        String message = "";
        try {
            Cursor cursor = database.rawQuery("SELECT  parent_message_row_id, key_id FROM message_add_on WHERE parent_message_row_id=\"" + id + "\"", null);
            cursor.moveToFirst();
            if (cursor.getCount() <= 0) {
                cursor.close();
                return message;
            }
            message = cursor.getString(1);
            cursor.close();
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return message;
    }

    public static String getMessageKeyByID(long id) {
        initDatabase();
        String message = "";
        try {
            Cursor cursor = database.rawQuery("SELECT _id, key_id FROM message WHERE _id=\"" + id + "\"", null);
            cursor.moveToFirst();
            if (cursor.getCount() <= 0) {
                cursor.close();
                return message;
            }
            message = cursor.getString(1);
            cursor.close();
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return message;
    }

    public static List<String> getAudioListByMessageList(List<String> messageList) {
        initDatabase();
        var list = new ArrayList<String>();
        var sql = "SELECT key_id, message_type FROM message WHERE key_id IN (";
        sql += String.join(",", messageList.stream().map(m -> "\"" + m + "\"").toArray(String[]::new));
        sql += ")";
        Cursor cursor = database.rawQuery(sql, null);
        if (cursor.getCount() <= 0) {
            cursor.close();
            return list;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return list;
        }
        do {
            var type = cursor.getInt(1);
            if (type == 2) {
                list.add(cursor.getString(0));
            }
        } while (cursor.moveToNext());
        cursor.close();
        return list;
    }

    public synchronized static void executeSQL(String sql) {
        initDatabase();
        try {
            database.execSQL(sql);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }


    public static void storeMessageRead(String messageId) {
        initDatabase();
        XposedBridge.log("storeMessageRead: " + messageId);
        database.execSQL("UPDATE message SET status = 1 WHERE key_id = \"" + messageId + "\"");
    }

    public static boolean isReadMessageStatus(String messageId) {
        initDatabase();
        boolean result = false;
        try {
            String[] columns = new String[]{"status"};
            String selection = "key_id=?";
            String[] selectionArgs = new String[]{messageId};

            Cursor cursor = database.query("message", columns, selection, selectionArgs, null, null, null);
            if (cursor.moveToFirst()) {
                result = cursor.getInt(cursor.getColumnIndexOrThrow("status")) == 1;
            }
            cursor.close();
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return result;
    }
}