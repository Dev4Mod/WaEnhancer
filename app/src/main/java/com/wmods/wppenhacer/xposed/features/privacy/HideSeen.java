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

        Method SendReadReceiptJobMethod = Unobfuscator.loadHideViewSendReadJob(classLoader);
        var sendJob = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendReadReceiptJob", classLoader);
        log(Unobfuscator.getMethodDescriptor(SendReadReceiptJobMethod));

        var hideread = prefs.getBoolean("hideread", false);
        var hideread_group = prefs.getBoolean("hideread_group", false);
        var hidestatusview = prefs.getBoolean("hidestatusview", false);

        XposedBridge.hookMethod(SendReadReceiptJobMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!sendJob.isInstance(param.thisObject)) return;
                var srj = sendJob.cast(param.thisObject);
                var messageIds = XposedHelpers.getObjectField(srj, "messageIds");
                var firstmessage = (String) Array.get(messageIds, 0);
                if (firstmessage != null && WppCore.getPrivBoolean(firstmessage + "_rpass", false)) {
                    WppCore.removePrivKey(firstmessage + "_rpass");
                    return;
                }
                var jid = (String) XposedHelpers.getObjectField(srj, "jid");
                if (jid == null) return;

                if (WppCore.isGroup(jid)) {
                    if (hideread_group)
                        param.setResult(null);
                } else if (jid.startsWith("status")) {
                    if (hidestatusview)
                        param.setResult(null);
                } else if (hideread) {
                    param.setResult(null);
                }

            }
        });

        Method hideViewInChatMethod = Unobfuscator.loadHideViewInChatMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(hideViewInChatMethod));

        Method hideViewMethod = Unobfuscator.loadHideViewMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(hideViewMethod));

        XposedBridge.hookMethod(hideViewMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!Unobfuscator.isCalledFromMethod(hideViewInChatMethod)) return;
                if (param.args[4] == null || !param.args[4].equals("read")) return;
                var jid = WppCore.getCurrentRawJID();
                if (WppCore.isGroup(jid)) {
                    if (prefs.getBoolean("hideread_group", false))
                        param.args[4] = null;
                } else if (prefs.getBoolean("hideread", false)) {
                    param.args[4] = null;
                }
            }
        });

        var methodPlayerViewJid = Unobfuscator.loadHideViewAudioMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(methodPlayerViewJid));
        XposedBridge.hookMethod(methodPlayerViewJid, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefs.getBoolean("hideonceseen", false))
                    param.setResult(true);
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen";
    }

}
