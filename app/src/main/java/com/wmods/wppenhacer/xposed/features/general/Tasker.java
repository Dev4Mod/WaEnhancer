package com.wmods.wppenhacer.xposed.features.general;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.WppCore;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Tasker extends Feature {
    private static Object fMessage;

    public Tasker(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("tasker", false)) return;
        hookReceiveMessage();
        registerSenderMessage();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Tasker";
    }

    private void registerSenderMessage() {
        IntentFilter filter = new IntentFilter("com.wmods.wppenhacer.MESSAGE_SENT");
        ContextCompat.registerReceiver(Utils.getApplication(), new SenderMessageBroadcastReceiver(), filter, ContextCompat.RECEIVER_EXPORTED);
    }

    public void hookReceiveMessage() throws Throwable {
        var method = Unobfuscator.loadReceiptMethod(classLoader);
        var method2 = Unobfuscator.loadReceiptOutsideChat(classLoader);
        var newMessageMethod = Unobfuscator.loadNewMessageMethod(classLoader);
        var fieldMessageKey = Unobfuscator.loadMessageKeyField(classLoader);

        XposedBridge.hookMethod(method2, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                fMessage = param.args[0];
            }
        });

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[4] == "sender" || param.args[1] == null) return;
                var messageKey = fieldMessageKey.get(fMessage);
                var userJid = XposedHelpers.getObjectField(messageKey, "A00");
                var rawJid = WppCore.getRawString(userJid);
                var number = WppCore.stripJID(rawJid);
                var msg = (String) newMessageMethod.invoke(fMessage);
                new Handler(Utils.getApplication().getMainLooper()).post(() -> {
                    Intent intent = new Intent("com.wmods.wppenhacer.MESSAGE_RECEIVED");
                    intent.putExtra("number", number);
                    intent.putExtra("message", msg);
                    Utils.getApplication().sendBroadcast(intent);
                });
            }
        });

    }

    public static class SenderMessageBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            XposedBridge.log("Message sent");
            var number = intent.getStringExtra("number");
            var message = intent.getStringExtra("message");
            if (number == null || message == null) return;
            number = number.replaceAll("\\D", "");
            WppCore.sendMessage(number, message);
        }
    }


}
