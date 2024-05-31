package com.wmods.wppenhacer.xposed.core;

import android.annotation.SuppressLint;
import android.app.Application;
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

import de.robv.android.xposed.XposedBridge;

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


    public static void setWritePermissions(File file) {
        try {
            file.setWritable(true, false);
            file.setReadable(true, false);
            file.setExecutable(true, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getDestination(SharedPreferences prefs, File file, String name) {
        var folderPath = prefs.getString("localdownload", Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download") + "/WhatsApp/Wa Enhancer/" + name + "/";
        var filePath = new File(folderPath);
        if (!filePath.exists()) filePath.mkdirs();
        return filePath.getAbsolutePath() + "/" + (file == null ? "" : file.getName());
    }

    public static boolean copyFile(File srcFile, File destFile) {
        if (srcFile == null || !srcFile.exists()) return false;

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

                    return true;
                }
                out.write(bArr, 0, read);
            }
        } catch (IOException e) {
            XposedBridge.log(e.getMessage());
            return false;
        }
    }

    public static void showToast(String s, int len) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(Utils.getApplication(), s, len).show());
    }


}
