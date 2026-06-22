package com.wmods.wppenhacer.xposed;

import android.content.pm.PackageInstaller;

import com.wmods.wppenhacer.xposed.core.FeatureLoader;

import java.io.IOException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AntiUpdater {

    public static void hookSession(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam.packageName.equals("android")) return;
        XposedBridge.hookAllMethods(PackageInstaller.class, "createSession", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var session = (PackageInstaller.SessionParams) param.args[0];
                var packageName = XposedHelpers.getObjectField(session, "mPackageName");
                if (packageName.equals(FeatureLoader.PACKAGE_WPP) || packageName.equals(FeatureLoader.PACKAGE_BUSINESS)) {
                    param.setThrowable(new IOException("UPDATE LOCKED BY WAENHANCER"));
                }
            }
        });

    }
}
