package com.wmods.wppenhacer.xposed.core;

import android.util.Log;

import androidx.annotation.NonNull;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public abstract class Feature {

    public final ClassLoader classLoader;
    public final XSharedPreferences prefs;
    public static boolean DEBUG = false;

    public Feature(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        this.classLoader = classLoader;
        this.prefs = preferences;
    }

    public abstract void doHook() throws Throwable;

    @NonNull
    public abstract String getPluginName();

    public void logDebug(Object object) {
        if (!DEBUG) return;
        log(object);
        if (object instanceof Throwable th) {
            Log.i("WAE", this.getPluginName() + "-> " + th.getMessage(), th);
        } else {
            Log.i("WAE", this.getPluginName() + "-> " + object);
        }
    }

    public void logDebug(String title, Object object) {
        if (!DEBUG) return;
        log(title + ": " + object);
        if (object instanceof Throwable th) {
            Log.i("WAE", this.getPluginName() + "-> " + title + ": " + th.getMessage(), th);
        } else {
            Log.i("WAE", this.getPluginName() + "-> " + title + ": " + object);
        }
    }


    public void log(Object object) {
        if (object instanceof Throwable) {
            XposedBridge.log(String.format("[%s] Error:", this.getPluginName()));
            XposedBridge.log((Throwable) object);
        } else {
            XposedBridge.log(String.format("[%s] %s", this.getPluginName(), object));
        }
    }
}
