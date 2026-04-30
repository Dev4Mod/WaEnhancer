package com.wmods.wppenhacer.xposed.core.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.features.general.PresenceStateTracker;
import com.wmods.wppenhacer.xposed.utils.Utils;

public class PresenceLogStore extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "presence_logs.db";
    private static final int DATABASE_VERSION = 1;
    public static final String TABLE_PRESENCE_EVENTS = "presence_events";
    private static PresenceLogStore mInstance;

    private PresenceLogStore(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static PresenceLogStore getInstance() {
        synchronized (PresenceLogStore.class) {
            if (mInstance == null || !mInstance.getWritableDatabase().isOpen()) {
                mInstance = new PresenceLogStore(Utils.getApplication());
            }
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PRESENCE_EVENTS + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "contact_id TEXT NOT NULL, " +
                "status TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_presence_events_contact_time ON " +
                TABLE_PRESENCE_EVENTS + "(contact_id, timestamp)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void insertEvent(@NonNull String contactId, @NonNull PresenceStateTracker.Status status, long timestamp) {
        try (SQLiteDatabase dbWrite = this.getWritableDatabase()) {
            ContentValues values = new ContentValues();
            values.put("contact_id", contactId);
            values.put("status", status.name());
            values.put("timestamp", timestamp);
            dbWrite.insert(TABLE_PRESENCE_EVENTS, null, values);
        }
    }
}
