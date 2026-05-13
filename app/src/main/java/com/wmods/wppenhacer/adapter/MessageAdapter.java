package com.wmods.wppenhacer.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.List;

public class MessageAdapter extends ArrayAdapter<MessageHistoryStore.MessageItem> {
    private final Context context;
    private final List<MessageHistoryStore.MessageItem> items;

    public MessageAdapter(Context context, List<MessageHistoryStore.MessageItem> items) {
        super(context, android.R.layout.simple_list_item_2, android.R.id.text1, items);
        this.context = context;
        this.items = items;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public MessageHistoryStore.MessageItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view1 = super.getView(position, convertView, parent);
        TextView textView0 = view1.findViewById(android.R.id.text1);
        textView0.setTextSize(14.0f);
        textView0.setTextColor(DesignUtils.getPrimaryTextColor());
        textView0.setText(this.items.get(position).message);
        TextView textView1 = view1.findViewById(android.R.id.text2);
        textView1.setTextSize(12.0f);
        textView1.setAlpha(0.75f);
        textView1.setTypeface(null, Typeface.ITALIC);
        textView1.setTextColor(DesignUtils.getPrimaryTextColor());
        var timestamp = this.items.get(position).timestamp;
        textView1.setText((timestamp == 0L ? context.getString(R.string.message_original) : "✏️ " + Utils.getDateTimeFromMillis(timestamp)));
        return view1;
    }

}