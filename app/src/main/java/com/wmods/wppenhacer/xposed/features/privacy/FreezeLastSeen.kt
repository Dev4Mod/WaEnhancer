package com.wmods.wppenhacer.xposed.features.privacy

import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.getPrivBoolean
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadFreezeSeenMethod

class FreezeLastSeen(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        val freezeLastSeen = xprefs.getBoolean("freezelastseen", false)
        val freezeLastSeenOption = getPrivBoolean("freezelastseen", false)
        val ghostmode = getPrivBoolean("ghostmode", false) && xprefs.getBoolean("ghostmode", false)

        if (freezeLastSeen || freezeLastSeenOption || ghostmode) {
            val method = loadFreezeSeenMethod(classLoader)
            logDebug(getMethodDescriptor(method))
            method.hook {
                replaceUnit { }
            }
        }
    }

    override fun getPluginName(): String {
        return "Freeze Last Seen"
    }
}
