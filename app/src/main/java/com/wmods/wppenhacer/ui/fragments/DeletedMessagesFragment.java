package com.wmods.wppenhacer.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.adapter.DeletedMessagesAdapter;
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore;
import com.wmods.wppenhacer.xposed.core.db.DeletedMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeletedMessagesFragment extends Fragment implements DeletedMessagesAdapter.OnItemClickListener {

    private RecyclerView recyclerView;
    private View emptyView;
    private DeletedMessagesAdapter adapter;
    private DelMessageStore delMessageStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_deleted_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView = view.findViewById(R.id.empty_view);

        delMessageStore = DelMessageStore.getInstance(requireContext());
        adapter = new DeletedMessagesAdapter(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        loadMessages();
    }

    private void loadMessages() {
        new Thread(() -> {
            List<DeletedMessage> allMessages = delMessageStore.getAllDeletedMessages();
            Map<String, DeletedMessage> latestMessagesMap = new HashMap<>();

            for (DeletedMessage msg : allMessages) {
                if (!latestMessagesMap.containsKey(msg.getChatJid())) {
                    latestMessagesMap.put(msg.getChatJid(), msg);
                } else {
                    if (msg.getTimestamp() > latestMessagesMap.get(msg.getChatJid()).getTimestamp()) {
                         latestMessagesMap.put(msg.getChatJid(), msg);
                    }
                }
            }

            List<DeletedMessage> uniqueChats = new ArrayList<>(latestMessagesMap.values());
            uniqueChats.sort((m1, m2) -> Long.compare(m2.getTimestamp(), m1.getTimestamp()));

            requireActivity().runOnUiThread(() -> {
                if (uniqueChats.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setMessages(uniqueChats);
                }
            });
        }).start();
    }

    @Override
    public void onItemClick(DeletedMessage message) {
        android.content.Intent intent = new android.content.Intent(requireContext(), com.wmods.wppenhacer.activities.MessageListActivity.class);
        intent.putExtra("chat_jid", message.getChatJid());
        startActivity(intent);
    }
}
