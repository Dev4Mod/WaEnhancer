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

    public MessageListAdapter(OnRestoreClickListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<DeletedMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deleted_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeletedMessage message = messages.get(position);

        holder.senderName.setText(message.getSenderJid()); 
        holder.timestamp.setText(Utils.getDateTimeFromMillis(message.getTimestamp()));

        if (message.getTextContent() != null && !message.getTextContent().isEmpty()) {
            holder.messageContent.setText(message.getTextContent());
            holder.messageContent.setVisibility(View.VISIBLE);
        } else {
            holder.messageContent.setVisibility(View.GONE);
        }

        if (message.getMediaPath() != null) {
            holder.mediaContainer.setVisibility(View.VISIBLE);
            File mediaFile = new File(message.getMediaPath());
            if (mediaFile.exists()) {
                 holder.mediaPreview.setImageURI(Uri.fromFile(mediaFile));
            } else {
                holder.mediaPreview.setImageResource(android.R.drawable.ic_menu_report_image);
            }

            if (message.getMediaCaption() != null && !message.getMediaCaption().isEmpty()) {
                holder.mediaCaption.setText(message.getMediaCaption());
                holder.mediaCaption.setVisibility(View.VISIBLE);
            } else {
                holder.mediaCaption.setVisibility(View.GONE);
            }
        } else {
            holder.mediaContainer.setVisibility(View.GONE);
        }

        holder.btnRestore.setOnClickListener(v -> listener.onRestoreClick(message));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView senderName;
        TextView timestamp;
        TextView messageContent;
        View mediaContainer;
        ImageView mediaPreview;
        TextView mediaCaption;
        MaterialButton btnRestore;

        ViewHolder(View itemView) {
            super(itemView);
            senderName = itemView.findViewById(R.id.sender_name);
            timestamp = itemView.findViewById(R.id.timestamp);
            messageContent = itemView.findViewById(R.id.message_content);
            mediaContainer = itemView.findViewById(R.id.media_container);
            mediaPreview = itemView.findViewById(R.id.media_preview);
            mediaCaption = itemView.findViewById(R.id.media_caption);
            btnRestore = itemView.findViewById(R.id.btn_restore);
        }
    }
}
