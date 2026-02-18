package com.wmods.wppenhacer.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.core.db.DeletedMessage;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MessageListAdapter extends RecyclerView.Adapter<MessageListAdapter.ViewHolder> {

    private List<DeletedMessage> messages = new ArrayList<>();
    private final OnRestoreClickListener listener;

    public interface OnRestoreClickListener {
        void onRestoreClick(DeletedMessage message);
    }

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    public MessageListAdapter(OnRestoreClickListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<DeletedMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        DeletedMessage message = messages.get(position);
        return message.isFromMe() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = (viewType == VIEW_TYPE_SENT) ? R.layout.item_message_sent : R.layout.item_message_received;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeletedMessage message = messages.get(position);

        // Sender Name (Only for received messages, and usually specific to groups)
        if (holder.senderName != null) {
            boolean showName = !message.isFromMe() && message.getChatJid().contains("@g.us");
            
            if (showName) {
               String contactName = message.getContactName(); // Try persisted name first
               if (contactName == null) {
                   contactName = com.wmods.wppenhacer.utils.ContactHelper.getContactName(holder.itemView.getContext(), message.getSenderJid());
               }
               
               if (contactName != null) {
                   holder.senderName.setText(contactName);
                   holder.senderName.setVisibility(View.VISIBLE);
               } else {
                   String senderJid = message.getSenderJid();
                   if (senderJid != null) {
                       senderJid = senderJid.replace("@s.whatsapp.net", "").replace("@g.us", "");
                       if (senderJid.contains("@")) senderJid = senderJid.split("@")[0];
                       holder.senderName.setText(senderJid);
                       holder.senderName.setVisibility(View.VISIBLE);
                   } else {
                       holder.senderName.setVisibility(View.GONE);
                   }
               }
            } else {
               holder.senderName.setVisibility(View.GONE);
            }
        }

        // Timestamp
        holder.timestamp.setText(Utils.getDateTimeFromMillis(message.getTimestamp()));

        // Message Content
        String text = message.getTextContent();
        if (text != null && !text.isEmpty()) {
            holder.messageContent.setText(text);
            holder.messageContent.setVisibility(View.VISIBLE);
        } else {
             // Placeholder for media
             String type = "Message";
             if (message.getMediaType() != -1) {
                 if (message.getMediaType() == 1) type = "📷 Photo";
                 else if (message.getMediaType() == 2) type = "🔊 Audio";
                 else if (message.getMediaType() == 3) type = "🎥 Video";
                 else type = "📁 Media (" + message.getMediaType() + ")";
             } 
             
             if (message.getMediaCaption() != null && !message.getMediaCaption().isEmpty()) {
                 type += "\n" + message.getMediaCaption();
             }
             holder.messageContent.setText(type);
             holder.messageContent.setVisibility(View.VISIBLE);
        }

        // Restore Button
        holder.btnRestore.setOnClickListener(v -> listener.onRestoreClick(message));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView senderName; // Nullable (only in received)
        TextView timestamp;
        TextView messageContent;
        View btnRestore;

        ViewHolder(View itemView) {
            super(itemView);
            senderName = itemView.findViewById(R.id.sender_name);
            timestamp = itemView.findViewById(R.id.timestamp);
            messageContent = itemView.findViewById(R.id.message_content);
            btnRestore = itemView.findViewById(R.id.btn_restore);
        }
    }
}
