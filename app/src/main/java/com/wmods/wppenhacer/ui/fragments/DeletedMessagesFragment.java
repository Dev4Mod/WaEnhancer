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

    private boolean isGroup;

    public static DeletedMessagesFragment newInstance(boolean isGroup) {
        DeletedMessagesFragment fragment = new DeletedMessagesFragment();
        Bundle args = new Bundle();
        args.putBoolean("is_group", isGroup);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isGroup = getArguments().getBoolean("is_group");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_deleted_messages, container, false);
    }

    private View permissionBanner;
    private android.widget.Button btnGrantPermission;

    private static final int PERMISSION_REQUEST_CONTACTS = 1001;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView = view.findViewById(R.id.empty_view);
        permissionBanner = view.findViewById(R.id.permission_banner);
        btnGrantPermission = view.findViewById(R.id.btn_grant_permission);

        delMessageStore = DelMessageStore.getInstance(requireContext());
        adapter = new DeletedMessagesAdapter(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        btnGrantPermission.setOnClickListener(v -> requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, PERMISSION_REQUEST_CONTACTS));

        checkPermissions();
        loadMessages();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermissions();
    }

    private void checkPermissions() {
        if (requireContext().checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionBanner.setVisibility(View.GONE);
            if (adapter != null) adapter.notifyDataSetChanged(); // Refresh names
        } else {
            permissionBanner.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                checkPermissions();
            }
        }
    }

    private void loadMessages() {
        new Thread(() -> {
            List<DeletedMessage> allMessages = delMessageStore.getDeletedMessages(isGroup);
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

    @Override
    public void onRestoreClick(DeletedMessage message) {
        // Not used in this fragment anymore (removed from adapter view)
    }
}
