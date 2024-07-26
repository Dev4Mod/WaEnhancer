package com.wmods.wppenhacer.xposed.provider;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XSettings {

    private static final String TAG = "XSettings";

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.android.settings")) {
            hookSettings(lpparam);
        }
    }


    private static void hookSettings(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // https://android.googlesource.com/platform/frameworks/base/+/master/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java
        @SuppressLint("PrivateApi")
        Class<?> clsSet = Class.forName("com.android.providers.settings.SettingsProvider", false, lpparam.classLoader);

        // Bundle call(String method, String arg, Bundle extras)
        Method mCall = clsSet.getMethod("call", String.class, String.class, Bundle.class);
        XposedBridge.hookMethod(mCall, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String method = (String) param.args[0];
                    String arg = (String) param.args[1];
                    Bundle extras = (Bundle) param.args[2];

                    if ("WaEnhancer".equals(method)) {
                        Method mGetContext = param.thisObject.getClass().getMethod("getContext");
                        Context context = (Context) mGetContext.invoke(param.thisObject);
                        if ("getBinder".equals(arg)) {
                            var filePath = extras.getString("filePath");
                            if (filePath != null) {
                                param.setResult(XSettings.getFile(context, filePath));
                            }
                        }
                    }
                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                    XposedBridge.log(ex);
                }
            }
        });
    }

    private static Bundle getFile(Context context, String filePath) {
        Bundle bundle = new Bundle();
        bundle.putBinder();
        return bundle;
    }
}
