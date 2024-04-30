package com.wmods.wppenhacer;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.wmods.wppenhacer.xposed.core.Utils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XposedHelpers;

public class App extends Application {

    private static App instance;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Handler MainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.edit().putBoolean("refresh", true).commit();
        try {
            Field f = sharedPreferences.getClass().getDeclaredField("mFile");
            f.setAccessible(true);
            File file = (File) f.get(sharedPreferences);
            executorService.execute(() -> {
                while (file != null && file.exists()) {
                    Utils.setWritePermissions(file);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
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
