package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Feature;

import java.lang.reflect.Method;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class HideSeen extends Feature {

    public HideSeen(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        Method hideViewOpenChatMethod = Unobfuscator.loadHideViewOpenChatMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(hideViewOpenChatMethod));

        XposedBridge.hookMethod(hideViewOpenChatMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefs.getBoolean("hideread", false))
                    param.setResult(new HashMap<>());
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
