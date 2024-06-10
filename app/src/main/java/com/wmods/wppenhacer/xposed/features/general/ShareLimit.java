package com.wmods.wppenhacer.xposed.features.general;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ShareLimit extends Feature {
    public ShareLimit(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        var shareLimitMethod = Unobfuscator.loadShareLimitMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(shareLimitMethod));
        var shareLimitField = Unobfuscator.loadShareLimitField(classLoader);
        logDebug(Unobfuscator.getFieldDescriptor(shareLimitField));

        XposedBridge.hookMethod(
                shareLimitMethod,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (prefs.getBoolean("removeforwardlimit", false)) {
                            shareLimitField.set(param.thisObject, true);
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
