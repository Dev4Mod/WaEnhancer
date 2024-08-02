package com.wmods.wppenhacer.xposed.features.privacy;


import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

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

        XposedBridge.hookMethod(viewOnceStoreMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("viewonce", false)) return;
                isFromMe = false;
                var messageObject = param.args[0];
                if (messageObject == null) return;
                isFromMe = new FMessageWpp(messageObject).getKey().isFromMe;
            }
        });

        for (var method : methods) {
            logDebug(Unobfuscator.getMethodDescriptor(method));
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!prefs.getBoolean("viewonce", false)) return;
                    if ((int) param.getResult() != 2 && (ReflectionUtils.isCalledFromClass(classViewOnce))) {
                        param.setResult(0);
                    } else if ((int) param.getResult() != 2 && !isFromMe && (ReflectionUtils.isCalledFromClass(viewOnceStoreMethod.getDeclaringClass()))) {
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