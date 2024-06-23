package com.wmods.wppenhacer.xposed.core;

import android.database.sqlite.SQLiteOpenHelper;

import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class WppDatabase {


    public static void Initialize(ClassLoader loader, XSharedPreferences pref) throws Exception {
        var msgstoreClass = Unobfuscator.loadMessageStoreClass2(loader);
        XposedBridge.hookAllConstructors(msgstoreClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                MessageStore.setDatabase((SQLiteOpenHelper) param.thisObject);
            }
        });
    }
}
