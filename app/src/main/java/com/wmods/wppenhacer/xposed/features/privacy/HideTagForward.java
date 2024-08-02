package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class HideTagForward extends Feature {
    public HideTagForward(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        Method method = Unobfuscator.loadForwardTagMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(method));
        Class<?> forwardClass = Unobfuscator.loadForwardClassMethod(classLoader);
        logDebug("ForwardClass: " + forwardClass.getName());

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("hidetag", false)) return;
                var arg = (int) param.args[0];
                if (arg == 1) {
                    if (ReflectionUtils.isCalledFromClass(forwardClass)) {
                        param.args[0] = 0;
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Tag Forward";
    }
}
