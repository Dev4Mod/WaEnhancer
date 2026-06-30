package com.wmods.wppenhacer.xposed.features.privacy

import android.content.ContentResolver
import android.provider.Settings
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadCheckCustomRom
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadCheckEmulator
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadRootDetector
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import java.io.File

class AntiWa(classLoader: ClassLoader, preferences:SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        if (!prefs.getBoolean("bootloader_spoofer", false)) return
        val rootDetector  = loadRootDetector(classLoader)
        for (detector in rootDetector) {
            XposedBridge.hookMethod(detector, XC_MethodReplacement.returnConstant(false))
        }
        val settingsGetInt = Settings.Global::class.java.getDeclaredMethod(
            "getInt",
            ContentResolver::class.java,
            String::class.java,
            Int::class.javaPrimitiveType
        )
        XposedBridge.hookMethod(settingsGetInt, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args[1] as String
                if (key == "adb_enabled") {
                    param.setResult(0)
                }
            }
        })
        val checkEmulator = loadCheckEmulator(classLoader)
        XposedBridge.hookMethod(checkEmulator, XC_MethodReplacement.returnConstant(false))
        // File Check
        val FileConstructor = File::class.java.getConstructor(String::class.java)
        XposedBridge.hookMethod(FileConstructor, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val path = param.args[0] as String
                val fakePath = "/data/fakepath"
                if (path.contains("qemu") || path.contains("superuser")) {
                    param.args[0] = fakePath
                }
            }
        })

        val checkCustomRom = loadCheckCustomRom(classLoader)
        XposedBridge.hookMethod(checkCustomRom, XC_MethodReplacement.returnConstant(false))
    }

    override fun getPluginName(): String {
        return "AntiDetector"
    }
}
