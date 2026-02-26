package com.wmods.wppenhacer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.model.Recording;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder> {

    private List<Recording> recordings = new ArrayList<>();
    private final OnRecordingActionListener listener;
    private boolean isSelectionMode = false;
    private final Set<Integer> selectedPositions = new HashSet<>();
    private OnSelectionChangeListener selectionChangeListener;

    public interface OnRecordingActionListener {
        void onPlay(Recording recording);
        void onShare(Recording recording);
        void onDelete(Recording recording);
        void onLongPress(Recording recording, int position);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int count);
    }

    public RecordingsAdapter(OnRecordingActionListener listener) {
        this.listener = listener;
    }

    public void setSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }

    public void setRecordings(List<Recording> recordings) {
        this.recordings = recordings;
        clearSelection();
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        if (this.isSelectionMode != selectionMode) {
            this.isSelectionMode = selectionMode;
            if (!selectionMode) {
                selectedPositions.clear();
            }
            notifyDataSetChanged();
        }
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
        }
    }

    public void selectAll() {
        selectedPositions.clear();
        for (int i = 0; i < recordings.size(); i++) {
            selectedPositions.add(i);
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
        }
    }

    public void clearSelection() {
        selectedPositions.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(0);
        }
    }

    public List<Recording> getSelectedRecordings() {
        List<Recording> selected = new ArrayList<>();
        for (int position : selectedPositions) {
            if (position < recordings.size()) {
                selected.add(recordings.get(position));
            }
        }
        return selected;
    }

    public int getSelectionCount() {
        return selectedPositions.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recording, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Recording recording = recordings.get(position);
        
        // Contact name
        holder.contactName.setText(recording.getContactName());
        
        // Phone number
        String phoneNumber = recording.getPhoneNumber();
        if (phoneNumber != null && !phoneNumber.equals(recording.getContactName())) {
            holder.phoneNumber.setVisibility(View.VISIBLE);
            holder.phoneNumber.setText(phoneNumber);
        } else {
            holder.phoneNumber.setVisibility(View.GONE);
        }
        
        // Duration
        holder.duration.setText(recording.getFormattedDuration());
        
        // Details: size and date
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        String details = recording.getFormattedSize() + " • " + dateFormat.format(new Date(recording.getDate()));
        holder.details.setText(details);
        
        // Selection mode UI
        if (isSelectionMode) {
            holder.checkbox.setVisibility(View.VISIBLE);
            holder.actionsContainer.setVisibility(View.GONE);
            holder.checkbox.setChecked(selectedPositions.contains(position));
            holder.card.setChecked(selectedPositions.contains(position));
        } else {
            holder.checkbox.setVisibility(View.GONE);
            holder.actionsContainer.setVisibility(View.VISIBLE);
            holder.card.setChecked(false);
        }
        
        // Click handling
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(position);
            } else {
                listener.onPlay(recording);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                listener.onLongPress(recording, position);
            }
            return true;
        });
        
        holder.checkbox.setOnClickListener(v -> toggleSelection(position));
        
        // Action buttons
        holder.btnPlay.setOnClickListener(v -> listener.onPlay(recording));
        holder.btnShare.setOnClickListener(v -> listener.onShare(recording));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(recording));
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        CheckBox checkbox;
        ImageView icon;
        TextView contactName;
        TextView phoneNumber;
        TextView duration;
        TextView details;
        LinearLayout actionsContainer;
        ImageButton btnPlay;
        ImageButton btnShare;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            checkbox = itemView.findViewById(R.id.checkbox);
            icon = itemView.findViewById(R.id.icon);
            contactName = itemView.findViewById(R.id.contact_name);
            phoneNumber = itemView.findViewById(R.id.phone_number);
            duration = itemView.findViewById(R.id.duration);
            details = itemView.findViewById(R.id.details);
            actionsContainer = itemView.findViewById(R.id.actions_container);
            btnPlay = itemView.findViewById(R.id.btn_play);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
