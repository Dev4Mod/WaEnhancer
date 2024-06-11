package com.wmods.wppenhacer.xposed.core.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

public class MessageStore {


    public static SQLiteOpenHelper database;


    public static String getMessageById(long id) {
        String message = "";
        try {
            String[] columns = new String[]{"c0content"};
            String selection = "docid=?";
            String[] selectionArgs = new String[]{String.valueOf(id)};

            Cursor cursor = database.getReadableDatabase().query("message_ftsv2_content", columns, selection, selectionArgs, null, null, null);
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
        String message = "";
        try {
            Cursor cursor = database.getReadableDatabase().rawQuery("SELECT  parent_message_row_id, key_id FROM message_add_on WHERE parent_message_row_id=\"" + id + "\"", null);
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
        String message = "";
        try {
            Cursor cursor = database.getReadableDatabase().rawQuery("SELECT _id, key_id FROM message WHERE _id=\"" + id + "\"", null);
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
        var list = new ArrayList<String>();
        var sql = "SELECT key_id, message_type FROM message WHERE key_id IN (";
        sql += String.join(",", messageList.stream().map(m -> "\"" + m + "\"").toArray(String[]::new));
        sql += ")";
        Cursor cursor = database.getReadableDatabase().rawQuery(sql, null);
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
        try {
            database.getWritableDatabase().execSQL(sql);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }


    public static void setDatabase(SQLiteOpenHelper database) {
        MessageStore.database = database;
    }
}