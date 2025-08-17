package com.wmods.wppenhacer.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class LinkSafetyActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent it = getIntent();
        final String original = it.getStringExtra("original");
        final String cleaned  = it.getStringExtra("cleaned");
        final String host     = it.getStringExtra("host");
        final boolean warnShort = it.getBooleanExtra("warnShort", false);
        final boolean blocked   = it.getBooleanExtra("blocked", false);

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Link Safety");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        layout.setPadding(pad, pad, pad, pad);

        TextView domain = new TextView(this);
        domain.setText("Domain: " + (host == null ? "" : host));
        layout.addView(domain);

        if (!TextUtils.equals(original, cleaned)) {
            TextView cleanedTv = new TextView(this);
            cleanedTv.setText("Cleaned URL: " + cleaned);
            layout.addView(cleanedTv);
        }

        if (warnShort) {
            TextView warn = new TextView(this);
            warn.setText("⚠ Short link (destination unknown)");
            layout.addView(warn);
        }
        if (blocked) {
            TextView warn = new TextView(this);
            warn.setText("⛔ Blocked domain (change in settings if needed).");
            layout.addView(warn);
        }

        final CheckBox trust = new CheckBox(this);
        trust.setText("Always trust this domain");
        layout.addView(trust);

        b.setView(layout);
        b.setCancelable(true);

        // Positive: Open
        b.setPositiveButton("Open", (d, w) -> {
            if (blocked) {
                Toast.makeText(this, "Blocked by Link Safety", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (trust.isChecked() && !TextUtils.isEmpty(host)) {
                android.content.SharedPreferences prefs = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
                String raw = prefs.getString("link_safety_allowlist", "");
                String norm = host.trim().toLowerCase(java.util.Locale.ROOT);
                boolean exists = false;
                if (!TextUtils.isEmpty(raw)) {
                    String[] toks = raw.split(",");
                    for (int i = 0; i < toks.length; i++) {
                        if (norm.equals(toks[i].trim().toLowerCase(java.util.Locale.ROOT))) { exists = true; break; }
                    }
                }
                if (!exists) {
                    String updated = TextUtils.isEmpty(raw) ? norm : raw + "," + norm;
                    prefs.edit().putString("link_safety_allowlist", updated).apply();
                }
                try {
                    Intent sync = new Intent("com.wmods.wppenhacer.action.ADD_TRUSTED_DOMAIN");
                    sync.setPackage(getPackageName());
                    sync.putExtra("host", host);
                    sendBroadcast(sync);
                } catch (Throwable ignored) {}
            }
            try {
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(cleaned));
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(view);
            } catch (Throwable t) {
                Toast.makeText(this, "Failed to open", Toast.LENGTH_SHORT).show();
            }
            finish();
        });

        // Neutral: Copy
        b.setNeutralButton("Copy", (d, w) -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", cleaned));
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {}
            finish();
        });

        // Negative: Block (new)
        b.setNegativeButton("Block", (d, w) -> {
            if (!TextUtils.isEmpty(host)) {
                android.content.SharedPreferences prefs = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
                String raw = prefs.getString("link_safety_blocklist", "");
                String norm = host.trim().toLowerCase(java.util.Locale.ROOT);
                boolean exists = false;
                if (!TextUtils.isEmpty(raw)) {
                    String[] toks = raw.split(",");
                    for (int i = 0; i < toks.length; i++) {
                        if (norm.equals(toks[i].trim().toLowerCase(java.util.Locale.ROOT))) { exists = true; break; }
                    }
                }
                if (!exists) {
                    String updated = TextUtils.isEmpty(raw) ? norm : raw + "," + norm;
                    prefs.edit().putString("link_safety_blocklist", updated).apply();
                }
                try {
                    Intent sync = new Intent("com.wmods.wppenhacer.action.ADD_BLOCKED_DOMAIN");
                    sync.setPackage(getPackageName());
                    sync.putExtra("host", host);
                    sendBroadcast(sync);
                } catch (Throwable ignored) {}
                Toast.makeText(this, "Blocked " + host, Toast.LENGTH_SHORT).show();
            }
            finish();
        });

        b.setOnDismissListener(d -> finish());
        b.show();
    }
}
