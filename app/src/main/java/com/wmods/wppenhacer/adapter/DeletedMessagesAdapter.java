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
                if (displayText.contains("@"))
                    displayText = displayText.split("@")[0];
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

        // Sanitize text for media messages (Fix for weird strings/URLs)
        // If it's a media message, ignore text if it looks like a URL or Hash
        if (message.getMediaType() > 0 && text != null) {
            if (text.startsWith("http") || (text.length() > 20 && !text.contains(" "))) {
                text = null;
            }
        }

        if (text == null || text.isEmpty()) {
            int type = message.getMediaType();
            if (type != -1 && type != 0) {
                switch (type) {
                    case 1:
                        text = "📷 Photo";
                        break;
                    case 2:
                        text = "🔊 Audio";
                        break;
                    case 3:
                        text = "🎥 Video";
                        break;
                    case 4:
                        text = "👤 Contact";
                        break;
                    case 5:
                        text = "📍 Location";
                        break;
                    case 9:
                        text = "📄 Document";
                        break;
                    case 13:
                        text = "👾 GIF";
                        break;
                    case 20:
                        text = "💟 Sticker";
                        break;
                    case 42:
                        text = "🔄 Status Reply";
                        break; // Sometimes seen
                    default:
                        text = "📁 Media";
                        break;
                }
            } else {
                text = "🚫 Message deleted";
            }
        }
        holder.lastMessage.setText(senderPrefix + text);

        // Avatar (Placeholder)
        // Holder.avatar.setImageDrawable(...)

        // App Badge
        String pkg = message.getPackageName();
        if (pkg != null) {
            holder.appBadge.setVisibility(View.VISIBLE);
            if (pkg.equals("com.whatsapp.w4b")) {
                // WhatsApp Business - Teal
                holder.appBadge.setImageTintList(
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00BFA5")));
            } else {
                // WhatsApp - Green
                holder.appBadge.setImageTintList(
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#25D366")));
            }
        } else {
            holder.appBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(message));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        ImageView appBadge;
        TextView contactName;
        TextView timestamp; // Date
        TextView lastMessage;

        ViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatar);
            appBadge = itemView.findViewById(R.id.app_badge);
            contactName = itemView.findViewById(R.id.contact_name);
            timestamp = itemView.findViewById(R.id.timestamp);
            lastMessage = itemView.findViewById(R.id.last_message);
        }
    }
}
