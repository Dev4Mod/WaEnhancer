package com.wmods.wppenhacer.xposed.features.others;

import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.Tasker;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
                processNewWA(param, toastViewedMessage, toastViewedStatus);
            }
        });
    }

    private void processNewWA(XC_MethodHook.MethodHookParam param, boolean toastViewedMessage, boolean toastViewedStatus) throws ClassNotFoundException, IllegalAccessException {
        Collection collection;
        log("Process New WA");
        if (!(param.args[0] instanceof Collection)) {
            collection = Collections.singleton(param.args[0]);
        } else {
            collection = (Collection) param.args[0];
        }
        var jidClass = classLoader.loadClass("com.whatsapp.jid.Jid");
        for (var messageStatusUpdateReceipt : collection) {
            var fieldByType = ReflectionUtils.getFieldByType(messageStatusUpdateReceipt.getClass(), int.class);
            var fieldId = ReflectionUtils.getFieldByType(messageStatusUpdateReceipt.getClass(), long.class);
            var fieldByUserJid = ReflectionUtils.getFieldByExtendType(messageStatusUpdateReceipt.getClass(), jidClass);
            var fieldMessage = ReflectionUtils.getFieldByExtendType(messageStatusUpdateReceipt.getClass(), FMessageWpp.TYPE);
            int type = fieldByType.getInt(messageStatusUpdateReceipt);
            long id = fieldId.getLong(messageStatusUpdateReceipt);
            if (type != 13) return;
            var userJid = new FMessageWpp.UserJid(fieldByUserJid.get(messageStatusUpdateReceipt));
            AtomicReference<Object> fmessage = new AtomicReference<>();
            try {
                fmessage.set(fieldMessage.get(messageStatusUpdateReceipt));
            } catch (Exception ignored) {
            }
            CompletableFuture.runAsync(() -> {
                var contactName = WppCore.getContactName(userJid);
                var rowId = id;

                if (TextUtils.isEmpty(contactName)) contactName = userJid.getStripJID();

                var sql = MessageStore.getInstance().getDatabase();

                if (fmessage.get() != null) {
                    rowId = new FMessageWpp(fmessage.get()).getRowId();
                }
                checkDataBase(sql, rowId, contactName, userJid.getRawString(), toastViewedMessage, toastViewedStatus);
            });
        }
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Toast Viewer";
    }

    private synchronized void checkDataBase(SQLiteDatabase sql, long id, String contactName, String rawJid, boolean toastViewedMessage, boolean toast_viewed_status) {
        try (var result2 = sql.query("message", null, "_id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (!result2.moveToNext()) return;

            var participantHash = result2.getString(result2.getColumnIndexOrThrow("participant_hash"));
            if (participantHash != null) {
                if (toast_viewed_status) {
                    Utils.showToast(Utils.getApplication().getString(ResId.string.viewed_your_status, contactName), Toast.LENGTH_LONG);
                }
                Tasker.sendTaskerEvent(contactName, WppCore.stripJID(rawJid), "viewed_status");
                return;
            }

            var userJid = WppCore.getCurrentUserJid();

            if (rawJid != null && userJid != null && Objects.equals(userJid.getRawString(), rawJid))
                return;

            var chat_id = result2.getLong(result2.getColumnIndexOrThrow("chat_row_id"));
            try (var result3 = sql.query("chat", null, "_id = ? AND subject IS NULL", new String[]{String.valueOf(chat_id)}, null, null, null)) {
                if (!result3.moveToNext()) return;

                var key = rawJid + "_" + "viewed_message";
                long currentTime = System.currentTimeMillis();
                Long lastEventTime = lastEventTimeMap.get(key);
                if (lastEventTime == null || (currentTime - lastEventTime) >= MIN_INTERVAL) {
                    lastEventTimeMap.put(key, currentTime);
                    Tasker.sendTaskerEvent(contactName, WppCore.stripJID(rawJid), "viewed_message");
                    if (toastViewedMessage) {
                        Utils.showToast(Utils.getApplication().getString(ResId.string.viewed_your_message, contactName), Toast.LENGTH_LONG);
                    }
                }
            } catch (Exception e) {
                XposedBridge.log(e);
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
