package com.wmods.wppenhacer.xposed.features.privacy;


import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ViewOnce extends Feature {
    private boolean isFromMe;

    public ViewOnce(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var methods = Unobfuscator.loadViewOnceMethod(classLoader);
        var classViewOnce = Unobfuscator.loadViewOnceClass(classLoader);
        logDebug(classViewOnce);
        var viewOnceStoreMethod = Unobfuscator.loadViewOnceStoreMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(viewOnceStoreMethod));
        var messageKeyField = Unobfuscator.loadMessageKeyField(classLoader);

        XposedBridge.hookMethod(viewOnceStoreMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("viewonce", false)) return;
                isFromMe = false;
                var messageObject = param.args[0];
                if (messageObject == null) return;
                var messageKey = messageKeyField.get(messageObject);
                isFromMe = XposedHelpers.getBooleanField(messageKey, "A02");
            }
        });

        for (var method : methods) {
            logDebug(Unobfuscator.getMethodDescriptor(method));
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!prefs.getBoolean("viewonce", false)) return;
                    if ((int) param.getResult() != 2 && (Unobfuscator.isCalledFromClass(classViewOnce))) {
                        param.setResult(0);
                    } else if ((int) param.getResult() != 2 && !isFromMe && (Unobfuscator.isCalledFromClass(viewOnceStoreMethod.getDeclaringClass()))) {
                        param.setResult(0);
                    }
                }
            });
        }

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "View Once";
    }


}