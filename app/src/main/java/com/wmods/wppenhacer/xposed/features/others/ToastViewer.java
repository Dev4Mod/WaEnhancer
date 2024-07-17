package com.wmods.wppenhacer.xposed.features.others;

import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.Tasker;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ToastViewer extends Feature {

    private static final long MIN_INTERVAL = 1000;
    private static final Map<String, Long> lastEventTimeMap = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ToastViewer(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
        startCleanupTask();
    }

    @Override
    public void doHook() throws Throwable {

        var toastViewedStatus = prefs.getBoolean("toast_viewed_status", false);
        var toastViewedMessage = prefs.getBoolean("toast_viewed_message", false);

        var onInsertReceipt = Unobfuscator.loadOnInsertReceipt(classLoader);
        XposedBridge.hookMethod(onInsertReceipt, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var type = (int) param.args[1];
                var id = (long) param.args[2];
                if (type != 13) return;
                var PhoneUserJid = param.args[0];
                CompletableFuture.runAsync(() -> {
                    var raw = WppCore.getRawString(PhoneUserJid);
                    var UserJid = WppCore.createUserJid(raw);
                    var contactName = WppCore.getContactName(UserJid);

                    if (TextUtils.isEmpty(contactName)) contactName = WppCore.stripJID(raw);

                    var sql = MessageStore.getInstance().getDatabase();

                    checkDataBase(sql, id, contactName, raw, toastViewedMessage, toastViewedStatus);

                });
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Toast Viewer";
    }

    private synchronized void checkDataBase(SQLiteDatabase sql, long id, String contactName, String raw, boolean toastViewedMessage, boolean toast_viewed_status) {
        try (var result2 = sql.query("message", null, "_id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (!result2.moveToNext()) return;

            var participantHash = result2.getString(result2.getColumnIndexOrThrow("participant_hash"));
            if (participantHash != null) {
                if (toast_viewed_status) {
                    Utils.showToast(Utils.getApplication().getString(ResId.string.viewed_your_status, contactName), Toast.LENGTH_LONG);
                }
                Tasker.sendTaskerEvent(contactName, WppCore.stripJID(raw), "viewed_status");
                return;
            }

            if (Objects.equals(WppCore.getCurrentRawJID(), raw)) return;

            var chat_id = result2.getLong(result2.getColumnIndexOrThrow("chat_row_id"));
            try (var result3 = sql.query("chat", null, "_id = ? AND subject IS NULL", new String[]{String.valueOf(chat_id)}, null, null, null)) {
                if (!result3.moveToNext()) return;

                var key = raw + "_" + "viewed_message";
                long currentTime = System.currentTimeMillis();
                Long lastEventTime = lastEventTimeMap.get(key);
                if (lastEventTime == null || (currentTime - lastEventTime) >= MIN_INTERVAL) {
                    lastEventTimeMap.put(key, currentTime);
                    if (toastViewedMessage) {
                        Utils.showToast(Utils.getApplication().getString(ResId.string.viewed_your_message, contactName), Toast.LENGTH_LONG);
                    }
                    Tasker.sendTaskerEvent(contactName, WppCore.stripJID(raw), "viewed_message");
                }
            }
        }
    }

    private void startCleanupTask() {
        scheduler.scheduleWithFixedDelay(() -> {
            long currentTime = System.currentTimeMillis();
            synchronized (lastEventTimeMap) {
                lastEventTimeMap.entrySet().removeIf(entry -> (currentTime - entry.getValue()) >= MIN_INTERVAL);
            }
        }, MIN_INTERVAL, MIN_INTERVAL, TimeUnit.MILLISECONDS);
    }
}
