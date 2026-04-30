package com.wmods.wppenhacer.adapter;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.model.PresenceEvent;
import com.wmods.wppenhacer.xposed.features.general.PresenceStateTracker;

import java.util.ArrayList;
import java.util.List;

public class PresenceLogAdapter extends RecyclerView.Adapter<PresenceLogAdapter.ViewHolder> {

    private final List<PresenceEvent> events = new ArrayList<>();
    private final Context context;

    public PresenceLogAdapter(Context context) {
        this.context = context;
    }

    public void setEvents(List<PresenceEvent> newEvents) {
        events.clear();
        events.addAll(newEvents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_presence_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PresenceEvent event = events.get(position);

        String name = event.getContactName();
        if (name == null || name.isEmpty()) {
            name = event.getContactId();
            if (name != null && name.contains("@")) {
                name = name.split("@")[0];
            }
        }
        holder.textName.setText(name);

        boolean isOnline = event.getStatus() == PresenceStateTracker.Status.ONLINE;
        holder.textStatus.setText(isOnline ? "🟢 Online" : "🔴 Offline");

        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(event.getTimestamp(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        holder.textTime.setText(timeAgo);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textName;
        TextView textStatus;
        TextView textTime;

        ViewHolder(View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.textName);
            textStatus = itemView.findViewById(R.id.textStatus);
            textTime = itemView.findViewById(R.id.textTime);
        }
    }
}
