package com.wmods.wppenhacer.xposed.features.privacy;

import android.content.ContentResolver;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class AntiWa extends Feature {
    public AntiWa(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("bootloader_spoofer", false)) return;
        var rootDetector = Unobfuscator.loadRootDetector(classLoader);
        for (var detector : rootDetector) {
            logDebug("Root", detector);
            XposedBridge.hookMethod(detector, XC_MethodReplacement.returnConstant(false));
        }
        var settingsGetInt = Settings.Global.class.getDeclaredMethod("getInt", ContentResolver.class, String.class, int.class);
        logDebug("Adb", settingsGetInt);
        XposedBridge.hookMethod(settingsGetInt, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var key = (String) param.args[1];
                if (key.equals("adb_enabled")) {
                    param.setResult(0);
                }
            }
        });
        var checkEmulator = Unobfuscator.loadCheckEmulator(classLoader);
        logDebug("Emulator", checkEmulator);
        XposedBridge.hookMethod(checkEmulator, XC_MethodReplacement.returnConstant(false));
        // File Check
        var FileConstructor = File.class.getConstructor(String.class);
        XposedBridge.hookMethod(FileConstructor, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var path = (String) param.args[0];
                var fakePath = "/data/fakepath";
                if (path.contains("qemu") || path.contains("superuser")) {
                    param.args[0] = fakePath;
                }

            }
        });

        var checkCustomRom = Unobfuscator.loadCheckCustomRom(classLoader);
        logDebug("CustomRom", checkCustomRom);
        XposedBridge.hookMethod(checkCustomRom, XC_MethodReplacement.returnConstant(false));
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "AntiDetector";
    }
}
