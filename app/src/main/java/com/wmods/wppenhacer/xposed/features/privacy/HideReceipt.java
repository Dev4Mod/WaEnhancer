package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class HideReceipt extends Feature {
    public HideReceipt(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("hidereceipt", false) && !WppCore.getPrivBoolean("ghostmode", false))
            return;
        var method = Unobfuscator.loadReceiptMethod(classLoader);
        logDebug("hook method:" + Unobfuscator.getMethodDescriptor(method));
        var method2 = Unobfuscator.loadReceiptOutsideChat(classLoader);
        logDebug("Outside Chat: " + Unobfuscator.getMethodDescriptor(method2));
        var method3 = Unobfuscator.loadReceiptInChat(classLoader);
        logDebug("In Chat: " + Unobfuscator.getMethodDescriptor(method3));
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!ReflectionUtils.isCalledFromMethod(method2) && !ReflectionUtils.isCalledFromMethod(method3))
                    return;
                if (param.args[4] != "sender" && param.args[4] != "read") {
                    param.args[4] = "inactive";
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Receipt";
    }
}
