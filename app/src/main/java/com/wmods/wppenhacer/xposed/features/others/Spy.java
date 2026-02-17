package com.wmods.wppenhacer.xposed.features.others;

import android.view.MenuItem;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import com.wmods.wppenhacer.xposed.core.WppCore;

public class Spy extends Feature {

    public Spy(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        // if (!prefs.getBoolean("enable_spy", false)) return;
        XposedBridge.log("WAE: Spy Forced Enabled");

        // ... (Keep existing generic hooks if needed, but focusing on dumps for now)

        dumpMessageStore(classLoader);
        dumpCoreMessageStore(classLoader);
    }

    // ... (keep logMenuClick and dumpConversationFields)

    private void dumpMessageStore(ClassLoader loader) {
        try {
            java.lang.reflect.Field f = WppCore.class.getDeclaredField("mCachedMessageStore");
            f.setAccessible(true);
            Object store = f.get(null);
            if (store != null) {
                XposedBridge.log("WAE: mCachedMessageStore class: " + store.getClass().getName());
                for (Method m : store.getClass().getDeclaredMethods()) {
                     XposedBridge.log("WAE: MessageStore method: " + m.getName() + " " + Arrays.toString(m.getParameterTypes()));
                }
            } else {
                 XposedBridge.log("WAE: mCachedMessageStore is null");
            }
        } catch (Exception e) {
            XposedBridge.log("WAE: MessageStore Dump failed: " + e);
        }
    }

    private void dumpCoreMessageStore(ClassLoader loader) {
        try {
            Class<?> cms = Unobfuscator.loadCoreMessageStore(loader);
            XposedBridge.log("WAE: CoreMessageStore class: " + cms.getName());
            for (Method m : cms.getDeclaredMethods()) {
                 // Dump ALL methods to find the right signature
                 XposedBridge.log("WAE: CMS method: " + m.getName() + " " + Arrays.toString(m.getParameterTypes()));
            }
        } catch (Exception e) {
            XposedBridge.log("WAE: CMS Dump failed: " + e);
        }
    }

    @Override
    public String getPluginName() {
        return "Spy Tool";
    }
}
