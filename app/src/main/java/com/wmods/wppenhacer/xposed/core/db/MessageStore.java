package com.wmods.wppenhacer.xposed.core.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

public class MessageStore {


    public static SQLiteOpenHelper database;


    public static String getMessageById(long id) {
        String message = "";
        try {
            Cursor cursor = database.getReadableDatabase().rawQuery("SELECT docid, c0content FROM message_ftsv2_content WHERE docid=\"" + id + "\"", null);
            cursor.moveToFirst();
            XposedBridge.log("Count: " + cursor.getCount());
            if (cursor.getCount() <= 0) {
                cursor.close();
                return "";
            }
            message = cursor.getString(1);
            cursor.close();
        } catch (Exception ignored) {
            XposedBridge.log(ignored);
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
        database.getWritableDatabase().execSQL(sql);
    }


    public static void setDatabase(SQLiteOpenHelper database) {
        MessageStore.database = database;
    }
}