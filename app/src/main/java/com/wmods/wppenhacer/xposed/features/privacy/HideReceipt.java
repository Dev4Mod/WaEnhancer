package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class HideReceipt extends Feature {
    public HideReceipt(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var method = Unobfuscator.loadReceiptMethod(classLoader);
        logDebug("hook method:" + Unobfuscator.getMethodDescriptor(method));
        var method2 = Unobfuscator.loadReceiptOutsideChat(classLoader);
        logDebug("Outside Chat: " + Unobfuscator.getMethodDescriptor(method2));
        var method3 = Unobfuscator.loadReceiptInChat(classLoader);
        logDebug("In Chat: " + Unobfuscator.getMethodDescriptor(method3));
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("hidereceipt", false) && !WppCore.getPrivBoolean("ghostmode", false))
                    return;
                if (!Unobfuscator.isCalledFromMethod(method2) && !Unobfuscator.isCalledFromMethod(method3))
                    return;
                var jid = WppCore.getRawString(param.args[0]);
                if ((jid == null || jid.contains("@lid")) && param.args[4] != "sender") {
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
