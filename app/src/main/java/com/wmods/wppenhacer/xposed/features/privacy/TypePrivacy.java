package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class TypePrivacy extends Feature {

    public TypePrivacy(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var ghostmode = WppCore.getPrivBoolean("ghostmode", false);
        var ghostmode_t = prefs.getBoolean("ghostmode_t", false) || ghostmode;
        var ghostmode_r = prefs.getBoolean("ghostmode_r", false) || ghostmode;
        Method method = Unobfuscator.loadGhostModeMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(method));
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                var p1 = (int) param.args[2];
                if ((p1 == 1 && ghostmode_r) || (p1 == 0 && ghostmode_t)) {
                    param.setResult(null);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Ghost Mode";
    }
}
