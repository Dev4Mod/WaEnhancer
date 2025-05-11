package com.wmods.wppenhacer.xposed.downgrade;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.os.Build;

import com.wmods.wppenhacer.xposed.core.FeatureLoader;

import java.lang.reflect.Field;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Patch {
    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) throws Throwable {
        if (!("android".equals(lpparam.packageName)) || !(lpparam.processName.equals("android")))
            return;
        XC_MethodHook hookDowngradeObject = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var pkg = (String) XposedHelpers.callMethod(param.args[0], "getPackageName");
                if (Objects.equals(pkg, FeatureLoader.PACKAGE_WPP) || Objects.equals(pkg, FeatureLoader.PACKAGE_BUSINESS))
                    param.setResult(null);
            }
        };

        XC_MethodHook hookDowngradeBoolean = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var pkg = (String) XposedHelpers.callMethod(param.args[0], "getPackageName");
                if (Objects.equals(pkg, FeatureLoader.PACKAGE_WPP) || Objects.equals(pkg, FeatureLoader.PACKAGE_BUSINESS))
                    param.setResult(true);
            }
        };


        switch (Build.VERSION.SDK_INT) {
            case Build.VERSION_CODES.VANILLA_ICE_CREAM:  // 35
            case Build.VERSION_CODES.UPSIDE_DOWN_CAKE: // 34
                findAndHookMethod("com.android.server.pm.PackageManagerServiceUtils", lpparam.classLoader,
                        "checkDowngrade",
                        "com.android.server.pm.pkg.AndroidPackage",
                        "android.content.pm.PackageInfoLite", hookDowngradeObject
                );
                break;
            case Build.VERSION_CODES.TIRAMISU: // 33
                var checkDowngrade = XposedHelpers.findMethodExactIfExists("com.android.server.pm.PackageManagerServiceUtils", lpparam.classLoader,
                        "checkDowngrade",
                        "com.android.server.pm.parsing.pkg.AndroidPackage",
                        "android.content.pm.PackageInfoLite");
                if (checkDowngrade != null) {
                    XposedBridge.hookMethod(checkDowngrade, hookDowngradeObject);
                }
                break;
            case Build.VERSION_CODES.S_V2: // 32
            case Build.VERSION_CODES.S: // 31
            case Build.VERSION_CODES.R: // 30
                var pmService = XposedHelpers.findClassIfExists("com.android.server.pm.PackageManagerService",
                        lpparam.classLoader);
                if (pmService != null) {
                    var checkDowngrade1 = XposedHelpers.findMethodExactIfExists(pmService, "checkDowngrade",
                            "com.android.server.pm.parsing.pkg.AndroidPackage",
                            "android.content.pm.PackageInfoLite");
                    if (checkDowngrade1 != null) {
                        // 允许降级
                        XposedBridge.hookMethod(checkDowngrade1, hookDowngradeObject);
                    }
                    // exists on flyme 9(Android 11) only
                    var flymeCheckDowngrade = XposedHelpers.findMethodExactIfExists(pmService, "checkDowngrade",
                            "android.content.pm.PackageInfoLite",
                            "android.content.pm.PackageInfoLite");
                    if (flymeCheckDowngrade != null)
                        XposedBridge.hookMethod(flymeCheckDowngrade, hookDowngradeBoolean);
                }
                break;
            case Build.VERSION_CODES.Q: // 29
            case Build.VERSION_CODES.P: // 28
                Class<?> packageClazz = XposedHelpers.findClass("android.content.pm.PackageParser.Package", lpparam.classLoader);
                hookAllMethods(XposedHelpers.findClass("com.android.server.pm.PackageManagerService", lpparam.classLoader), "checkDowngrade", new XC_MethodHook() {
                    public void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        Object packageInfoLite = methodHookParam.args[0];
                        var packageName = XposedHelpers.getObjectField(packageInfoLite, "packageName");
                        if (packageName == FeatureLoader.PACKAGE_WPP || packageName == FeatureLoader.PACKAGE_BUSINESS) {
                            Field field = packageClazz.getField("mVersionCode");
                            field.setAccessible(true);
                            field.set(packageInfoLite, 0);
                            field = packageClazz.getField("mVersionCodeMajor");
                            field.setAccessible(true);
                            field.set(packageInfoLite, 0);
                        }
                    }
                });
                break;
            default:
                XposedBridge.log("W/Patch Unsupported Version of Android " + Build.VERSION.SDK_INT);
                break;
        }
    }
}
