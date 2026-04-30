package com.wmods.wppenhacer.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.db.PresenceLogStore;

public class PresenceLogProvider extends ContentProvider {

    public static final String AUTHORITY = "com.wmods.wppenhacer.presence";
    public static final String PATH_PRESENCE_LOGS = "logs";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_PRESENCE_LOGS);

    private static final int PRESENCE_LOGS = 1;
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, PATH_PRESENCE_LOGS, PRESENCE_LOGS);
    }

    private PresenceLogStore dbHelper;

    @Override
    public boolean onCreate() {
        dbHelper = PresenceLogStore.getInstance();
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (uriMatcher.match(uri) == PRESENCE_LOGS) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            return db.query(PresenceLogStore.TABLE_PRESENCE_EVENTS, projection, selection, selectionArgs, null, null, sortOrder);
        }
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
        if (uriMatcher.match(uri) == PRESENCE_LOGS && values != null) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            long id = db.insertWithOnConflict(PresenceLogStore.TABLE_PRESENCE_EVENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            if (id > 0) {
                getContext().getContentResolver().notifyChange(CONTENT_URI, null);
                return Uri.withAppendedPath(CONTENT_URI, String.valueOf(id));
            }
        }
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        if (uriMatcher.match(uri) == PRESENCE_LOGS) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int count = db.delete(PresenceLogStore.TABLE_PRESENCE_EVENTS, selection, selectionArgs);
            if (count > 0) {
                getContext().getContentResolver().notifyChange(CONTENT_URI, null);
            }
            return count;
        }
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
