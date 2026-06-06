package com.wmods.wppenhacer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wmods.wppenhacer.activities.CrashReportActivity;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.material.app.LocaleDelegate;

public class App extends Application {

    private static App instance;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Handler MainHandler = new Handler(Looper.getMainLooper());

    public static void showRequestStoragePermission(Activity activity) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(R.string.storage_permission);
        builder.setMessage(R.string.permission_storage);
        builder.setPositiveButton(R.string.allow, (dialog, which) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                activity.startActivity(intent);
            } else {
                ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            }
        });
        builder.setNegativeButton(R.string.deny, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        installCrashHandler();
        var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        var mode = Integer.parseInt(sharedPreferences.getString("thememode", "0"));
        setThemeMode(mode);
        changeLanguage(this);
    }

    private void installCrashHandler() {
        var previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                var intent = new Intent(this, CrashReportActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(CrashReportActivity.EXTRA_CRASH_INFO, buildCrashInfo());
                intent.putExtra(CrashReportActivity.EXTRA_CRASH_TRACE, Log.getStackTraceString(throwable));
                startActivity(intent);
            } catch (Throwable ignored) {
            } finally {
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable);
                } else {
                    Runtime.getRuntime().exit(2);
                }
            }
        });
    }

    private String buildCrashInfo() {
        var androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        var deviceModel = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        return "WAE version: " + BuildConfig.VERSION_NAME + "\n" +
                "WAE package: " + getPackageName() + "\n" +
                getString(R.string.crash_android_version) + ": " + androidVersion + "\n" +
                getString(R.string.device_model) + ": " + deviceModel;
    }

    public static void setThemeMode(int mode) {
        switch (mode) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }


    public static App getInstance() {
        return instance;
    }

    public static ExecutorService getExecutorService() {
        return executorService;
    }

    public static Handler getMainHandler() {
        return MainHandler;
    }


    public void restartApp(String packageWpp) {
        Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
        intent.putExtra("PKG", packageWpp);
        sendBroadcast(intent);
    }

    public static void changeLanguage(Context context) {
        var force = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("force_english", false);
        LocaleDelegate.setDefaultLocale(force ? Locale.ENGLISH : Locale.getDefault());
        var res = context.getResources();
        var config = res.getConfiguration();
        config.setLocale(LocaleDelegate.getDefaultLocale());
        //noinspection deprecation
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    public static File getWaEnhancerFolder() {
        var download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        var waEnhancerFolder = new File(download, "WaEnhancer");
        if (!waEnhancerFolder.exists()) waEnhancerFolder.mkdirs();
        return waEnhancerFolder;
    }

    public static boolean isOriginalPackage() {
        //noinspection ConstantValue
        return BuildConfig.APPLICATION_ID.equals("com.wmods.wppenhacer");
    }

}
