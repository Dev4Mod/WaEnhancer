package com.wmods.wppenhacer.xposed.core;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.Toast;

import com.wmods.wppenhacer.xposed.utils.MimeTypeUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Utils {

    public static Application getApplication() {
        return MainFeatures.mApp;
    }

    public static boolean doRestart(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        if (intent == null)
            return false;
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        mainIntent.setPackage(context.getPackageName());
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
        return true;
    }

    @SuppressLint("DiscouragedApi")
    public static int getID(String name, String type) {
        try {
            return getApplication().getApplicationContext().getResources().getIdentifier(name, type, getApplication().getPackageName());
        } catch (Exception e) {
            XposedBridge.log("Error while getting ID: " + name + " " + type + " message:" + e);
            return -1;
        }
    }

    public static int dipToPixels(float dipValue) {
        DisplayMetrics metrics = MainFeatures.mApp.getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }

    public static String getMyNumber() {
        return MainFeatures.mApp.getSharedPreferences(MainFeatures.mApp.getPackageName() + "_preferences_light", Context.MODE_PRIVATE).getString("ph", "");
    }

    public static String getDateTimeFromMillis(long timestamp) {
        return new SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH).format(new Date(timestamp));
    }

    public static String[] StringToStringArray(String str) {
        try {
            return str.substring(1, str.length() - 1).replaceAll("\\s", "").split(",");
        } catch (Exception unused) {
            return null;
        }
    }


    public static void debugFields(Object thisObject) {
        XposedBridge.log(thisObject.getClass().getName());
        for (var field : thisObject.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                XposedBridge.log(field.getName() + " : " + field.get(thisObject));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void debugFields(Class<?> cls, Object thisObject) {
        XposedBridge.log("DEBUG FIELDS: Class " + cls.getName());
        for (var field : cls.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                XposedBridge.log("FIELD: " + field.getName() + " -> VALUE: " + field.get(thisObject));
            } catch (Exception ignored) {
            }
        }
    }

    public static XC_MethodHook getDebugMethodHook(boolean printMethods, boolean printFields, boolean printArgs, boolean printTrace) {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("\n\n-----------------HOOKED DEBUG START-----------------------------");
                XposedBridge.log("DEBUG CLASS: " + param.method.getDeclaringClass().getName() + ": " + param.thisObject);

                if (printArgs) {
                    for (var i = 0; i < param.args.length; i++) {
                        XposedBridge.log("ARG[" + i + "]: " + (param.args[i] == null ? null : param.args[i].getClass().getName()) + " -> VALUE: " + param.args[i]);
                    }
                }
                if (printFields) {
                    debugFields(param.thisObject);
                }

                if (printMethods) {
                    debugMethods(param.thisObject.getClass(), param.thisObject);
                }
                if (printTrace) {
                    for (var trace : Thread.currentThread().getStackTrace()) {
                        XposedBridge.log("TRACE: " + trace.toString());
                    }
                }
                XposedBridge.log("-----------------HOOKED DEBUG END-----------------------------\n\n");
            }
        };
    }


    public static void debugMethods(Class<?> cls, Object thisObject) {
        XposedBridge.log("DEBUG METHODS: Class " + cls.getName());
        for (var method : cls.getDeclaredMethods()) {
            if (method.getParameterCount() > 0) continue;
            try {
                method.setAccessible(true);
                XposedBridge.log("METHOD: " + method.getName() + " -> VALUE: " + method.invoke(thisObject));
            } catch (Exception ignored) {
            }
        }
    }

    public static String getDestination(SharedPreferences prefs, File file, String name) {
        var folderPath = prefs.getString("localdownload", Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download") + "/WhatsApp/Wa Enhancer/" + name + "/";
        var filePath = new File(folderPath);
        if (!filePath.exists()) filePath.mkdirs();
        return filePath.getAbsolutePath() + "/" + (file == null ? "" : file.getName());
    }

    public static String copyFile(File srcFile, File destFile) {
        if (srcFile == null || !srcFile.exists()) return "File not found or is null";

        try (FileInputStream in = new FileInputStream(srcFile);
             FileOutputStream out = new FileOutputStream(destFile)) {
            byte[] bArr = new byte[1024];
            while (true) {
                int read = in.read(bArr);
                if (read <= 0) {
                    in.close();
                    out.close();
                    MediaScannerConnection.scanFile(Utils.getApplication(),
                            new String[]{destFile.getAbsolutePath()},
                            new String[]{MimeTypeUtils.getMimeTypeFromExtension(srcFile.getAbsolutePath())},
                            (path, uri) -> {
                            });

                    return "";
                }
                out.write(bArr, 0, read);
            }
        } catch (IOException e) {
            XposedBridge.log(e.getMessage());
            return e.getMessage();
        }
    }

    public static void showToast(String s, int len) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(Utils.getApplication(), s, len).show());
    }


    public static void debugAllMethods(String className, String methodName, boolean printMethods, boolean printFields, boolean printArgs, boolean printTrace) {
        XposedBridge.hookAllMethods(XposedHelpers.findClass(className, Utils.getApplication().getClassLoader()), methodName, getDebugMethodHook(printMethods, printFields, printArgs, printTrace));
    }

    public static void setToClipboard(String string) {
        ClipboardManager clipboard = (ClipboardManager) Utils.getApplication().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", string);
        clipboard.setPrimaryClip(clip);
    }
}
