package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.WppCore;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HideSeen extends Feature {

    public HideSeen(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        Method SendReadReceiptJobMethod = Unobfuscator.loadHideViewSendReadJob(loader);
        log(Unobfuscator.getMethodDescriptor(SendReadReceiptJobMethod));
        XposedBridge.hookMethod(SendReadReceiptJobMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var sendJob = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendReadReceiptJob", loader);
                var srj =  sendJob.cast(param.thisObject);
                var messageIds = XposedHelpers.getObjectField(srj, "messageIds");
                var firstmessage = (String) Array.get(messageIds, 0);
                if (firstmessage != null && WppCore.getPrivBoolean(firstmessage + "_rpass", false)) {
                    WppCore.removePrivKey(firstmessage + "_rpass");
                    return;
                }
                var jid = (String) XposedHelpers.getObjectField(srj, "jid");
                if (jid.contains("@g.us") && prefs.getBoolean("hideread_group", false)) {
                    param.setResult(null);
                } else if (prefs.getBoolean("hideread", false)) {
                    param.setResult(null);
                }

            }
        });

        Method hideViewInChatMethod = Unobfuscator.loadHideViewInChatMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(hideViewInChatMethod));

        Method hideViewMethod = Unobfuscator.loadHideViewMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(hideViewMethod));

        XposedBridge.hookMethod(hideViewMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefs.getBoolean("hideread", false)) {
                    if (Unobfuscator.isCalledFromMethod(hideViewInChatMethod)) {
                        if (param.args[4] != null && param.args[4].equals("read")) {
                            param.args[4] = null;
                        }
                    }
                }
            }
        });

        var methodPlayerViewJid = Unobfuscator.loadHideViewAudioMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(methodPlayerViewJid));
        XposedBridge.hookMethod(methodPlayerViewJid, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefs.getBoolean("hideonceseen", false))
                    param.setResult(true);
            }
        });

        var methodHideViewJid = Unobfuscator.loadHideViewJidMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(methodHideViewJid));
        XposedBridge.hookMethod(methodHideViewJid, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefs.getBoolean("hidestatusview", false))
                    param.setResult(null);
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen";
    }

}
