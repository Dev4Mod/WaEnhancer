package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.json.JSONObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class HideReceipt extends Feature {
    public HideReceipt(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var hideReceipt = prefs.getBoolean("hidereceipt", false);
        var ghostmode = WppCore.getPrivBoolean("ghostmode", false);
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
                var messageKey = new FMessageWpp.Key(param.args[3]);
                var userJid = messageKey.remoteJid;
                var rawJid = WppCore.getRawString(userJid);
                var number = WppCore.stripJID(rawJid);
                var privacy = WppCore.getPrivJSON(number + "_privacy", new JSONObject());
                var customHideReceipt = privacy.optBoolean("HideReceipt", hideReceipt);
                if (param.args[4] != "sender" && (customHideReceipt || ghostmode)) {
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
