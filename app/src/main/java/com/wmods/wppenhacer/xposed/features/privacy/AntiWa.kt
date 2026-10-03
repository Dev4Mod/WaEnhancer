package com.wmods.wppenhacer.xposed.features.privacy

import android.content.ContentResolver
import android.content.SharedPreferences
import android.provider.Settings
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadCheckCustomRom
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadCheckEmulator
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadRootDetector
import java.io.File

class AntiWa(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        if (!xprefs.getBoolean("bootloader_spoofer", false)) return
        val rootDetector = loadRootDetector(classLoader)
        for (detector in rootDetector) {
            detector.hook {
                replaceAny { false }
            }
        }
        val settingsGetInt = Settings.Global::class.java.getDeclaredMethod(
            "getInt",
            ContentResolver::class.java,
            String::class.java,
            Int::class.javaPrimitiveType
        )
        settingsGetInt.hook {
            before {
                val key = args[1] as String
                if (key == "adb_enabled") {
                    result = (0)
                }
            }
        }
        val checkEmulator = loadCheckEmulator(classLoader)
        checkEmulator.hook {
            replaceAny { false }
        }
        // File Check
        val FileConstructor = File::class.java.getConstructor(String::class.java)
        FileConstructor.hook {
            before {
                val path = args[0] as String
                val fakePath = "/data/fakepath"
                if (path.contains("qemu") || path.contains("superuser")) {
                    args[0] = fakePath
                }
            }
        }

        val checkCustomRom = loadCheckCustomRom(classLoader)
        checkCustomRom.hook {
            replaceAny { false }
        }
    }

    override fun getPluginName(): String {
        return "AntiDetector"
    }
}
