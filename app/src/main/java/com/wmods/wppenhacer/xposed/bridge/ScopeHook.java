package com.wmods.wppenhacer.xposed.bridge;

import android.os.Build;

import com.wmods.wppenhacer.BuildConfig;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ScopeHook {

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("android".equals(lpparam.packageName) && "android".equals(lpparam.processName)) {
            XposedBridge.log("Hooked Android System");
            String className = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? "com.android.server.pm.AppsFilterBase" : "com.android.server.pm.AppsFilter";
            XposedBridge.hookAllMethods(XposedHelpers.findClass(className, lpparam.classLoader), "shouldFilterApplication", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String arg = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? param.args[3].toString() : param.args[2].toString();
                    if (arg.contains(BuildConfig.APPLICATION_ID + "/")) {
                        param.setResult(Boolean.FALSE);
                    }
                }
            });
        }
    }

}
