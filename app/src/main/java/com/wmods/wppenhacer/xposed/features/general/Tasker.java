package com.wmods.wppenhacer.xposed.features.general;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class Tasker extends Feature {
    private static FMessageWpp fMessage;
    private static boolean taskerEnabled;


    public Tasker(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        taskerEnabled = prefs.getBoolean("tasker", false);
        if (!taskerEnabled) return;
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

    public synchronized static void sendTaskerEvent(String name, String number, String event) {
        if (!taskerEnabled) return;

        Intent intent = new Intent("com.wmods.wppenhacer.EVENT");
        intent.putExtra("name", name);
        intent.putExtra("number", number);
        intent.putExtra("event", event);
        Utils.getApplication().sendBroadcast(intent);

    }

    public void hookReceiveMessage() throws Throwable {
        var method = Unobfuscator.loadReceiptMethod(classLoader);

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[4] == "sender" || param.args[1] == null || param.args[3] == null)
                    return;
                var fMessage = new FMessageWpp(WppCore.getFMessageFromKey(param.args[3]));
                var userJid = fMessage.getKey().remoteJid;
                var rawJid = WppCore.getRawString(userJid);
                var name = WppCore.getContactName(userJid);
                var number = WppCore.stripJID(rawJid);
                var msg = fMessage.getMessageStr();
                if (TextUtils.isEmpty(msg) || TextUtils.isEmpty(number) || rawJid.startsWith("status"))
                    return;
                new Handler(Utils.getApplication().getMainLooper()).post(() -> {
                    Intent intent = new Intent("com.wmods.wppenhacer.MESSAGE_RECEIVED");
                    intent.putExtra("number", number);
                    intent.putExtra("name", name);
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
            if (number == null) {
                number = String.valueOf(intent.getLongExtra("number", 0));
                number = Objects.equals(number, "0") ? null : number;
            }
            var message = intent.getStringExtra("message");
            if (number == null || message == null) return;
            number = number.replaceAll("\\D", "");
            WppCore.sendMessage(number, message);
        }
    }

}
