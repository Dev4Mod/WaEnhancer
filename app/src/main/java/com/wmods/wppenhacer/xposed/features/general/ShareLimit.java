package com.wmods.wppenhacer.xposed.features.general;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Feature;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ShareLimit extends Feature {
    public ShareLimit(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        var shareLimitMethod = Unobfuscator.loadShareLimitMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(shareLimitMethod));
        var shareLimitField = Unobfuscator.loadShareLimitField(loader);
        logDebug(Unobfuscator.getFieldDescriptor(shareLimitField));

        XposedBridge.hookMethod(
                shareLimitMethod,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (prefs.getBoolean("removeforwardlimit", false)) {
                            XposedHelpers.setBooleanField(param.thisObject, shareLimitField.getName(), true);
                        }
                    }
                });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Share Limit";
    }
}
