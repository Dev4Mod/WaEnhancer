package com.wmods.wppenhacer.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.db.DelMessageStore;

public class DeletedMessagesProvider extends ContentProvider {

    public static final String AUTHORITY = "com.wmods.wppenhacer.provider";
    public static final String PATH_DELETED_MESSAGES = "deleted_messages";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_DELETED_MESSAGES);

    private static final int DELETED_MESSAGES = 1;
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, PATH_DELETED_MESSAGES, DELETED_MESSAGES);
    }

    private DelMessageStore dbHelper;

    @Override
    public boolean onCreate() {
        dbHelper = DelMessageStore.getInstance(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        // Not needed for now, but good practice to implement basic query if UI needs it later
        // or just return null if we only use it for insertion from Xposed
        return null; 
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (uriMatcher.match(uri) == DELETED_MESSAGES) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            long id = db.insertWithOnConflict(DelMessageStore.TABLE_DELETED_FOR_ME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            if (id > 0) {
                return Uri.withAppendedPath(CONTENT_URI, String.valueOf(id));
            }
        }
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
