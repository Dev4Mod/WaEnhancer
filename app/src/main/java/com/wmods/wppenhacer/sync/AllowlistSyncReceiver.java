package com.wmods.wppenhacer.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.content.SharedPreferences;

import java.util.Locale;

public class AllowlistSyncReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!"com.wmods.wppenhacer.action.ADD_TRUSTED_DOMAIN".equals(intent.getAction())) return;
        String host = intent.getStringExtra("host");
        if (TextUtils.isEmpty(host)) return;
        String normalized = host.trim().toLowerCase(Locale.ROOT);

        SharedPreferences prefs = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        String raw = prefs.getString("link_safety_allowlist", "");
        boolean exists = false;
        if (!TextUtils.isEmpty(raw)) {
            String[] toks = raw.split(",");
            for (int i = 0; i < toks.length; i++) {
                if (normalized.equals(toks[i].trim().toLowerCase(Locale.ROOT))) { exists = true; break; }
            }
        }
        if (!exists) {
            String updated = TextUtils.isEmpty(raw) ? normalized : raw + "," + normalized;
            prefs.edit().putString("link_safety_allowlist", updated).apply();
        }
    }
}
