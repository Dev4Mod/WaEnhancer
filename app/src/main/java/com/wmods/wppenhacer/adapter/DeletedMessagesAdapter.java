package com.wmods.wppenhacer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.db.DeletedMessage;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class DeletedMessagesAdapter extends RecyclerView.Adapter<DeletedMessagesAdapter.ViewHolder> {

    private List<DeletedMessage> messages = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(DeletedMessage message);
    }

    public DeletedMessagesAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<DeletedMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deleted_message_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeletedMessage message = messages.get(position);

        // Contact Name
        String contactName = WppCore.getContactName(new com.wmods.wppenhacer.xposed.core.components.FMessageWpp.UserJid(message.getChatJid()));
        holder.contactName.setText(contactName != null ? contactName : message.getChatJid());

        // Timestamp
        holder.timestamp.setText(Utils.getDateTimeFromMillis(message.getTimestamp()));

        // Last Message Content
        String content = message.getTextContent();
        if (content == null || content.isEmpty()) {
            if (message.getMediaType() != -1) {
                content = "Media (" + message.getMediaType() + ")";
                if (message.getMediaCaption() != null && !message.getMediaCaption().isEmpty()) {
                     content += ": " + message.getMediaCaption();
                }
            } else {
                content = "Deleted Message";
            }
        }
        holder.lastMessage.setText(content);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(message));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView contactName;
        TextView timestamp;
        TextView lastMessage;
        ImageView icon;

        ViewHolder(View itemView) {
            super(itemView);
            contactName = itemView.findViewById(R.id.contact_name);
            timestamp = itemView.findViewById(R.id.timestamp);
            lastMessage = itemView.findViewById(R.id.last_message);
            icon = itemView.findViewById(R.id.icon);
        }
    }
}
