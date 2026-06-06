package com.wmods.wppenhacer.xposed.features.privacy

import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.getPrivBoolean
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadDndModeMethod
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge

class DndMode(loader: ClassLoader, preferences: XSharedPreferences) : Feature(loader, preferences) {

    override fun doHook() {
        if (!getPrivBoolean("dndmode", false)) return
        val dndMethod = loadDndModeMethod(classLoader)
        logDebug(getMethodDescriptor(dndMethod))
        XposedBridge.hookMethod(dndMethod, XC_MethodReplacement.DO_NOTHING)
    }

    override fun getPluginName(): String {
        return "Dnd Mode"
    }
}
