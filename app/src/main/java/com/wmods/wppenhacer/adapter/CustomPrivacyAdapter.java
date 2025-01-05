package com.wmods.wppenhacer.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.List;

public class CustomPrivacyAdapter extends ArrayAdapter {

    private final Class<?> contactClass;
    private final SharedPreferences prefs;
    private final Class<?> groupClass;
    private final List<Item> items;

    public CustomPrivacyAdapter(Context context, SharedPreferences preferences, List<Item> mlist, Class<?> contactClass, Class<?> groupClass) {
        super(context, 0);
        this.contactClass = contactClass;
        this.groupClass = groupClass;
        this.items = mlist;
        this.prefs = preferences;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        Item item = items.get(position);
        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = createLayout(holder);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.textView.setText(item.name);
        convertView.setOnClickListener(v -> {
            // Only Groups
            if (item.number.length() > 15) {
                Intent intent = new Intent(getContext(), groupClass);
                intent.putExtra("gid", item.number + "@g.us");
                getContext().startActivity(intent);
                return;
            }
            Intent intent = new Intent(getContext(), contactClass);
            intent.putExtra("jid", item.number + "@s.whatsapp.net");
            getContext().startActivity(intent);
        });
        holder.button.setOnClickListener(v -> {
            items.remove(position);
            this.prefs.edit().remove(item.key).apply();
            notifyDataSetChanged();
        });

        return convertView;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    private ViewGroup createLayout(ViewHolder holder) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(Utils.dipToPixels(25), 0, Utils.dipToPixels(25), 0);

        TextView textView = new TextView(getContext());
        var layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.weight = 1;
        textView.setLayoutParams(layoutParams);
        layout.addView(textView);
        holder.textView = textView;

        Button button = new Button(getContext());
        var buttonParams = new LinearLayout.LayoutParams(Utils.dipToPixels(40), Utils.dipToPixels(40));
        button.setLayoutParams(buttonParams);
        var drawable = DesignUtils.createDrawable("stroke_border", Color.BLACK);
        button.setBackground(DesignUtils.alphaDrawable(drawable, DesignUtils.getPrimaryTextColor(), 25));
        button.setText("X");
        layout.addView(button);
        holder.button = button;

        return layout;
    }

    static class ViewHolder {
        TextView textView;
        Button button;
    }

    public static class Item {
        public String name;
        public String number;
        public String key;
    }


}
