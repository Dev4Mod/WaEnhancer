package com.wmods.wppenhacer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Context;

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
        void onRestoreClick(DeletedMessage message);
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeletedMessage message = messages.get(position);

        // Contact Name
        Context context = holder.itemView.getContext();
        String displayJid = message.getChatJid();
        
        // Priority 1: Use Persisted Contact Name (from DB)
        String contactName = message.getContactName();
        
        // Priority 2: Runtime Lookup (if not in DB or changed)
        if (contactName == null && displayJid != null) {
             contactName = com.wmods.wppenhacer.utils.ContactHelper.getContactName(context, displayJid);
        }

        String displayText;
        if (contactName != null) {
            displayText = contactName;
        } else {
            // Fallback to formatted JID
            displayText = displayJid;
            if (displayText != null) {
                displayText = displayText.replace("@s.whatsapp.net", "").replace("@g.us", "");
                if (displayText.contains("@")) displayText = displayText.split("@")[0];
            } else {
                displayText = "Unknown";
            }
        }
        
        holder.contactName.setText(displayText);

        // Timestamp
        holder.timestamp.setText(Utils.getDateTimeFromMillis(message.getTimestamp()));

        // Message Preview
        String text = message.getTextContent();
        String senderPrefix = "";
        if (message.isFromMe()) {
            senderPrefix = "You: ";
        }
        
        if (text == null || text.isEmpty()) {
            if (message.getMediaType() != -1) {
                text = "📷 Photo"; 
                if (message.getMediaType() == 2) text = "🔊 Audio";
                if (message.getMediaType() == 3) text = "🎥 Video";
            } else {
                text = "Message deleted";
            }
        }
        holder.lastMessage.setText(senderPrefix + text);

        // Avatar (Placeholder)
        // Holder.avatar.setImageDrawable(...)
        
        holder.itemView.setOnClickListener(v -> listener.onItemClick(message));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView contactName;
        TextView timestamp; // Date
        TextView lastMessage;

        ViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatar);
            contactName = itemView.findViewById(R.id.contact_name);
            timestamp = itemView.findViewById(R.id.timestamp);
            lastMessage = itemView.findViewById(R.id.last_message);
        }
    }
}
