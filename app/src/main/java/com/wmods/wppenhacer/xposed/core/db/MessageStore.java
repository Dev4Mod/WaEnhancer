package com.wmods.wppenhacer.xposed.core.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

public class MessageStore {

    private static SQLiteDatabase openDatabase() {
        var dataDir = Utils.getApplication().getFilesDir().getParentFile();
        var dbFile = new File(dataDir, "/databases/msgstore.db");
        if (!dbFile.exists()) return null;
        return SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
    }

    public static String getMessageById(long id) {
        SQLiteDatabase database = openDatabase();
        if (database == null) return "";
        String message = "";
        Cursor cursor = null;
        try {
            String[] columns = new String[]{"c0content"};
            String selection = "docid=?";
            String[] selectionArgs = new String[]{String.valueOf(id)};

            cursor = database.query("message_ftsv2_content", columns, selection, selectionArgs, null, null, null);
            if (cursor.moveToFirst()) {
                message = cursor.getString(cursor.getColumnIndexOrThrow("c0content"));
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        } finally {
            if (cursor != null) cursor.close();
            database.close();
        }
        return message;
    }

    public static String getOriginalMessageKey(long id) {
        SQLiteDatabase database = openDatabase();
        if (database == null) return "";
        String message = "";
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT parent_message_row_id, key_id FROM message_add_on WHERE parent_message_row_id=\"" + id + "\"", null);
            if (cursor.moveToFirst()) {
                message = cursor.getString(1);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        } finally {
            if (cursor != null) cursor.close();
            database.close();
        }
        return message;
    }

    public static String getMessageKeyByID(long id) {
        SQLiteDatabase database = openDatabase();
        if (database == null) return "";
        String message = "";
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT _id, key_id FROM message WHERE _id=\"" + id + "\"", null);
            if (cursor.moveToFirst()) {
                message = cursor.getString(1);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        } finally {
            if (cursor != null) cursor.close();
            database.close();
        }
        return message;
    }

    public static List<String> getAudioListByMessageList(List<String> messageList) {
        SQLiteDatabase database = openDatabase();
        if (database == null) return new ArrayList<>();
        List<String> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            String sql = "SELECT key_id, message_type FROM message WHERE key_id IN (";
            sql += String.join(",", messageList.stream().map(m -> "\"" + m + "\"").toArray(String[]::new));
            sql += ")";
            cursor = database.rawQuery(sql, null);
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
            database.close();
        }
        return list;
    }

    public synchronized static void executeSQL(String sql) {
        SQLiteDatabase database = openDatabase();
        try (database) {
            if (database == null) return;
            database.execSQL(sql);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public static void storeMessageRead(String messageId) {
        SQLiteDatabase database = openDatabase();
        try (database) {
            if (database == null) return;
            XposedBridge.log("storeMessageRead: " + messageId);
            database.execSQL("UPDATE message SET status = 1 WHERE key_id = \"" + messageId + "\"");
        }
    }

    public static boolean isReadMessageStatus(String messageId) {
        SQLiteDatabase database = openDatabase();
        if (database == null) return false;
        boolean result = false;
        Cursor cursor = null;
        try {
            String[] columns = new String[]{"status"};
            String selection = "key_id=?";
            String[] selectionArgs = new String[]{messageId};

            cursor = database.query("message", columns, selection, selectionArgs, null, null, null);
            if (cursor.moveToFirst()) {
                result = cursor.getInt(cursor.getColumnIndexOrThrow("status")) == 1;
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        } finally {
            if (cursor != null) cursor.close();
            database.close();
        }
        return result;
    }

    public static SQLiteDatabase getDatabase() {
        return openDatabase();
    }
}
