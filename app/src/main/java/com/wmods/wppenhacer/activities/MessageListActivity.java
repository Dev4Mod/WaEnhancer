package com.wmods.wppenhacer.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.adapter.MessageListAdapter;
import com.wmods.wppenhacer.databinding.ActivityMessageListBinding;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore;
import com.wmods.wppenhacer.xposed.core.db.DeletedMessage;

import java.util.List;

public class MessageListActivity extends BaseActivity implements MessageListAdapter.OnRestoreClickListener {

    private ActivityMessageListBinding binding;
    private MessageListAdapter adapter;
    private DelMessageStore delMessageStore;
    private String chatJid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMessageListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        chatJid = getIntent().getStringExtra("chat_jid");
        if (chatJid == null) {
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            String title = chatJid;
            if (title != null) {
                 title = title.replace("@s.whatsapp.net", "").replace("@g.us", "");
                 if (title.contains("@")) title = title.split("@")[0];
            }
            getSupportActionBar().setTitle(title);
        }

        delMessageStore = DelMessageStore.getInstance(this);
        adapter = new MessageListAdapter(this);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        loadMessages();
    }

    private void loadMessages() {
        new Thread(() -> {
            List<DeletedMessage> messages = delMessageStore.getDeletedMessagesByChat(chatJid);
            runOnUiThread(() -> {
                if (messages.isEmpty()) {
                    binding.emptyView.setVisibility(View.VISIBLE);
                    binding.recyclerView.setVisibility(View.GONE);
                } else {
                    binding.emptyView.setVisibility(View.GONE);
                    binding.recyclerView.setVisibility(View.VISIBLE);
                    adapter.setMessages(messages);
                }
            });
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRestoreClick(DeletedMessage message) {
        Toast.makeText(this, "Restore feature coming soon!", Toast.LENGTH_SHORT).show();
    }
}
