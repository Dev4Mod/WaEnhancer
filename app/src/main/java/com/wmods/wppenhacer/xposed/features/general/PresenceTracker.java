package com.wmods.wppenhacer.xposed.features.general;

import android.os.BaseBundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.PresenceLogStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class PresenceTracker extends Feature {

    public static final long OFFLINE_TIMEOUT_MS = 90_000L;
    private static final String PREF_ENABLED = "presence_tracker";
    private static final String PREF_CONTACTS = "presence_tracker_contacts";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PresenceStateTracker tracker;
    private final Runnable flushOfflineRunnable = new Runnable() {
        @Override
        public void run() {
            tracker.flushOfflineTimeouts();
            handler.postDelayed(this, 1000L);
        }
    };

    public PresenceTracker(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
        tracker = new PresenceStateTracker(
                System::currentTimeMillis,
                (contactId, status, timestamp) -> PresenceLogStore.getInstance().insertEvent(contactId, status, timestamp),
                OFFLINE_TIMEOUT_MS);
    }

    @Override
    public void doHook() throws Throwable {
        reloadSettings();
        if (!prefs.getBoolean(PREF_ENABLED, false)) return;

        handler.postDelayed(flushOfflineRunnable, 1000L);
        var checkOnlineMethod = Unobfuscator.loadCheckOnlineMethod(classLoader);
        XposedBridge.hookMethod(checkOnlineMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadSettings();
                if (!prefs.getBoolean(PREF_ENABLED, false)) return;
                var message = (Message) param.args[0];
                if (message.arg1 != 5 || !(message.obj instanceof BaseBundle baseBundle)) return;
                var jid = baseBundle.getString("jid");
                if (TextUtils.isEmpty(jid)) return;
                var userJid = new FMessageWpp.UserJid(jid);
                if (userJid.isGroup()) return;
                tracker.onOnline(jid);
            }
        });
    }

    private void reloadSettings() {
        prefs.reload();
        tracker.setEnabled(prefs.getBoolean(PREF_ENABLED, false));
        tracker.updateTrackedContacts(prefs.getString(PREF_CONTACTS, ""));
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Presence Tracker";
    }
}
