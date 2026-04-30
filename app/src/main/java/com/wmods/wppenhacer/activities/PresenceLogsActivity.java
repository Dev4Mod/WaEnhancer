package com.wmods.wppenhacer.activities;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.adapter.PresenceLogAdapter;
import com.wmods.wppenhacer.model.PresenceEvent;
import com.wmods.wppenhacer.utils.ContactHelper;
import com.wmods.wppenhacer.xposed.features.general.PresenceStateTracker;

import java.util.ArrayList;
import java.util.List;

public class PresenceLogsActivity extends BaseActivity {

    private PresenceLogAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_presence_logs);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PresenceLogAdapter(this);
        recyclerView.setAdapter(adapter);

        loadLogs();
    }

    private void loadLogs() {
        List<PresenceEvent> events = new ArrayList<>();
        Uri uri = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".presence/logs");
        
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, "timestamp DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                    String contactId = cursor.getString(cursor.getColumnIndexOrThrow("contact_id"));
                    String statusStr = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                    
                    PresenceStateTracker.Status status = PresenceStateTracker.Status.valueOf(statusStr);
                    String contactName = ContactHelper.getContactName(this, contactId);
                    
                    events.add(new PresenceEvent(id, contactId, status, timestamp, contactName));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        adapter.setEvents(events);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
