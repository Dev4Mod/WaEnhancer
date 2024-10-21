package com.wmods.wppenhacer.xposed.features.privacy;


import android.view.View;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
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
        if (!prefs.getBoolean("viewonce", false)) return;

        var methods = Unobfuscator.loadViewOnceMethod(classLoader);
        var classViewOnce = Unobfuscator.loadViewOnceClass(classLoader);
        logDebug(classViewOnce);
        var viewOnceStoreMethod = Unobfuscator.loadViewOnceStoreMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(viewOnceStoreMethod));
        var viewOnceChangeMethod = Unobfuscator.loadViewOnceChangeMethod(classLoader);

        XposedBridge.hookMethod(viewOnceStoreMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
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
                    int returnValue = (int) param.getResult();
                    if (returnValue != 2) {
                        if (ReflectionUtils.isCalledFromClass(classViewOnce)) {
                            param.setResult(0);
                        } else if (!isFromMe && (ReflectionUtils.isCalledFromClass(viewOnceStoreMethod.getDeclaringClass()))) {
                            param.setResult(0);
                        }
                    }
                }
            });
        }
        // This prevents view audio once from being set to previewed
        XposedBridge.hookMethod(viewOnceChangeMethod,
                new XC_MethodHook() {
                    private Unhook unhook;

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        unhook = XposedHelpers.findAndHookMethod(View.class, "postDelayed", Runnable.class, long.class, XC_MethodReplacement.returnConstant(false));
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        unhook.unhook();
                    }
                });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "View Once";
    }


}