package com.wmods.wppenhacer.xposed.bridge;

import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;

import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ScopeHook {

    private static Set<XC_MethodHook.Unhook> hook;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if ("android".equals(lpparam.packageName) && "android".equals(lpparam.processName) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hookService(lpparam);
            } else if ("com.android.providers.settings".equals(lpparam.packageName)) {
                hookSettings(lpparam);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private static void hookSettings(XC_LoadPackage.LoadPackageParam lpparam) throws Exception {
        Class<?> clsSet = XposedHelpers.findClass("com.android.providers.settings.SettingsProvider", lpparam.classLoader);

        // Bundle call(String method, String arg, Bundle extras)
        Method mCall = clsSet.getMethod("call", String.class, String.class, Bundle.class);
        XposedBridge.hookMethod(mCall, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String method = (String) param.args[0];
                    String arg = (String) param.args[1];
                    if ("WaEnhancer".equals(method)) {
                        if ("getHookBinder".equals(arg)) {
                            Method mGetContext = param.thisObject.getClass().getMethod("getContext");
                            Context context = (Context) mGetContext.invoke(param.thisObject);
                            XposedBridge.log("Wa Enhancer: Trying to allow blocking ");
                            try {
                                XposedHelpers.callStaticMethod(Binder.class, "allowBlockingForCurrentThread");
                            } catch (Throwable ignored) {
                            }
                            var result = Utils.binderLocalScope(() -> {
                                var uri = Uri.parse("content://com.wmods.waenhancer.hookprovider");
                                return context.getContentResolver().call(uri, "getHookBinder", null, null);
                            });
                            param.setResult(result);
                            try {
                                XposedHelpers.callStaticMethod(Binder.class, "defaultBlockingForCurrentThread");
                            } catch (Throwable ignored) {
                            }
                            XposedBridge.log("Wa Enhancer: Bypass Scope using Provider Settings");
                        }
                    }
                } catch (Throwable ex) {
                    XposedBridge.log(ex);
                }
            }
        });
    }

    private static void hookService(XC_LoadPackage.LoadPackageParam lpparam) {
        var serviceClass = XposedHelpers.findClass("android.os.ServiceManager", lpparam.classLoader);
        var addService = ReflectionUtils.findMethodUsingFilter(serviceClass, method -> method.getName().equals("addService"));
        var hookedService = new AtomicReference<XC_MethodHook.Unhook>();
        var hooked = XposedBridge.hookMethod(addService, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var service = (String) param.args[0];
                if (Objects.equals(service, "package")) {
                    if (hookedService.get() != null) {
                        hookedService.get().unhook();
                    }
                    new Thread(() -> hookScope(param.args[1], lpparam.classLoader)).start();
                }
            }
        });
        hookedService.set(hooked);
    }

    private static void hookScope(Object pms, ClassLoader loader) {
        XposedBridge.log("Hooked visibility Scope");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hook = XposedBridge.hookAllMethods(XposedHelpers.findClass("com.android.server.pm.AppsFilterBase", loader), "shouldFilterApplication", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        var snapshot = param.args[0];
                        var callingUid = (int) param.args[1];
                        if (callingUid == 1000) return;
                        var callingApps = Utils.binderLocalScope(() -> {
                            var computerClass = XposedHelpers.findClass("com.android.server.pm.Computer", loader);
                            var getPackagesForUidMethod = ReflectionUtils.findMethodUsingFilter(computerClass, method -> method.getName().equals("getPackagesForUid"));
                            return (String[]) ReflectionUtils.callMethod(getPackagesForUidMethod, snapshot, callingUid);
                        });
                        if (callingApps == null) return;
                        var targetApp = getPackageNameFromPackageSettings(param.args[3]);
                        for (var caller : callingApps) {
                            if ((caller.equals(FeatureLoader.PACKAGE_WPP) || caller.equals(FeatureLoader.PACKAGE_BUSINESS)) && targetApp.equals(BuildConfig.APPLICATION_ID)) {
                                param.setResult(Boolean.FALSE);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        XposedBridge.log("Error while hooking Android System");
                        XposedBridge.log(e);
                        unhook();
                    }
                }
            });
        } else {
            hook = XposedBridge.hookAllMethods(XposedHelpers.findClass("com.android.server.pm.AppsFilter", loader), "shouldFilterApplication", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        var callingUid = (int) param.args[0];
                        if (callingUid == 1000) return;
                        var callingApps = Utils.binderLocalScope(() -> {
                            var getPackagesForUidMethod = ReflectionUtils.findMethodUsingFilter(pms.getClass(), method -> method.getName().equals("getPackagesForUid"));
                            return (String[]) ReflectionUtils.callMethod(getPackagesForUidMethod, pms, callingUid);
                        });
                        if (callingApps == null) return;
                        var targetApp = getPackageNameFromPackageSettings(param.args[2]);
                        for (var caller : callingApps) {
                            if ((caller.equals(FeatureLoader.PACKAGE_WPP) || caller.equals(FeatureLoader.PACKAGE_BUSINESS)) && targetApp.equals(BuildConfig.APPLICATION_ID)) {
                                param.setResult(Boolean.FALSE);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        XposedBridge.log("Error while hooking Android System");
                        XposedBridge.log(e);
                        unhook();
                    }
                }
            });
        }

    }

    private static void unhook() {
        if (hook != null) {
            for (var unhook : hook) {
                unhook.unhook();
            }
        }
    }

    private static String getPackageNameFromPackageSettings(Object packageSettings) {
        String packageSettingsString = packageSettings.toString();
        int startIndex = packageSettingsString.lastIndexOf(' ') + 1;
        int endIndex = packageSettingsString.lastIndexOf('/');
        return packageSettingsString.substring(startIndex, endIndex);
    }

}
