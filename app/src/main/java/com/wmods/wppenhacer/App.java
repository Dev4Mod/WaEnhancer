package com.wmods.wppenhacer;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.material.app.LocaleDelegate;

public class App extends Application {

    private static App instance;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Handler MainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("ApplySharedPref")
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.edit().putLong("lastUpdateTime", System.currentTimeMillis()).commit();
        var mode = Integer.parseInt(sharedPreferences.getString("thememode", "0"));
        setThemeMode(mode);
        changeLanguage(this);
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


}
