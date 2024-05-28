package com.wmods.wppenhacer;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.wmods.wppenhacer.xposed.core.Utils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private static App instance;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Handler MainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.edit().putLong("lastUpdateTime", System.currentTimeMillis()).commit();
        var mode = Integer.parseInt(sharedPreferences.getString("thememode", "0"));
        setThemeMode(mode);
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


}
