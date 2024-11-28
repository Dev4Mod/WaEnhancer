package com.wmods.wppenhacer.xposed.features.privacy;


import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ViewOnce extends Feature {

    public ViewOnce(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("viewonce", false)) return;

        var methods = Unobfuscator.loadViewOnceMethod(classLoader);

        for (var method : methods) {
            logDebug(Unobfuscator.getMethodDescriptor(method));
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int returnValue = (int) param.args[0];
                    var fMessage = new FMessageWpp(param.thisObject);
                    if (returnValue == 1 && !fMessage.getKey().isFromMe) {
                        param.args[0] = 0;
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