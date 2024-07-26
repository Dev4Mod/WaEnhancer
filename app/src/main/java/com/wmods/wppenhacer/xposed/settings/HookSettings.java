package com.wmods.wppenhacer.xposed.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookSettings {

    private void hookSettings(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        XposedBridge.log("AllTrans: Trying to hook settings ");
        // https://android.googlesource.com/platform/frameworks/base/+/master/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java
        @SuppressLint("PrivateApi")
        Class<?> clsSet = Class.forName("com.android.providers.settings.SettingsProvider", false, lpparam.classLoader);

        XposedBridge.log("AllTrans: Got method to hook settings ");
        // Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)

        Method mQuery = clsSet.getMethod("query", Uri.class, String[].class, String.class, String[].class, String.class);

        XposedBridge.hookMethod(mQuery, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("beforeHookedMethod mQuery Settings: ");
                try {
                    Uri uri = (Uri) param.args[0];

                    if (uri.toString().contains("alltransProxyProviderURI")) {

                        XposedBridge.log("AllTrans: got projection xlua ");
                        long ident = Binder.clearCallingIdentity();

                        try {
                            Method mGetContext = param.thisObject.getClass().getMethod("getContext");
                            Context context = (Context) mGetContext.invoke(param.thisObject);

                            XposedBridge.log("AllTrans: Trying to allow blocking ");
                            XposedHelpers.callStaticMethod(Binder.class, "allowBlockingForCurrentThread");

                            XposedBridge.log("AllTrans: Old URI " + uri.toString());
                            String new_uri_string = uri.toString().replace("content://settings/system/alltransProxyProviderURI/", "content://akhil.alltrans.");
                            Uri new_uri = Uri.parse(new_uri_string);
                            XposedBridge.log("AllTrans: New URI " + new_uri.toString());

                            Cursor cursor = context.getContentResolver().query(new_uri, null, null, null, null);
                            param.setResult(cursor);

                            XposedHelpers.callStaticMethod(Binder.class, "defaultBlockingForCurrentThread");
                            XposedBridge.log("AllTrans: setting query result");

                        } catch (Throwable ex) {
                            XposedBridge.log(Log.getStackTraceString(ex));
                            param.setResult(null);
                            Binder.restoreCallingIdentity(ident);
                        }
                    }
                } catch (Throwable ex) {
                    XposedBridge.log(Log.getStackTraceString(ex));
                }
            }
        });
    }

}
