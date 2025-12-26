package com.wmods.wppenhacer.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wmods.wppenhacer.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder> {

    private List<File> files = new ArrayList<>();
    private final OnRecordingActionListener listener;

    public interface OnRecordingActionListener {
        void onPlay(File file);
        void onShare(File file);
        void onDelete(File file);
    }

    public RecordingsAdapter(OnRecordingActionListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setFiles(List<File> files) {
        this.files = files;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recording, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = files.get(position);
        Context context = holder.itemView.getContext();

        holder.name.setText(file.getName());
        
        String size = Formatter.formatFileSize(context, file.length());
        String date = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date(file.lastModified()));
        // Duration would require MediaPlayer parsing, expensive for list. Using size/date for now.
        
        holder.details.setText(String.format("%s • %s", size, date));

        holder.btnPlay.setOnClickListener(v -> listener.onPlay(file));
        holder.btnShare.setOnClickListener(v -> listener.onShare(file));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(file));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, details;
        ImageButton btnPlay, btnShare, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            details = itemView.findViewById(R.id.details);
            btnPlay = itemView.findViewById(R.id.btn_play);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
