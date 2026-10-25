package com.wmods.wppenhacer.xposed.features.general

import android.content.SharedPreferences
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator

class ShareLimit(classLoader: ClassLoader, xprefs: SharedPreferences) :
    Feature(classLoader, xprefs) {

    override fun doHook() {
        if (!xprefs.getBoolean("removeforwardlimit", false)) return
        val multiSelectionLimitInfoClass =
            Unobfuscator.loadMultiSelectionLimitInfoClass(classLoader)
        multiSelectionLimitInfoClass.declaredConstructors.forEach { constructor ->
            val intIndexes = constructor.parameterTypes.indices.filter {
                constructor.parameterTypes[it] == Int::class.javaPrimitiveType
            }
            val booleanIndexes = constructor.parameterTypes.indices.filter {
                constructor.parameterTypes[it] == Boolean::class.javaPrimitiveType
            }
            if (intIndexes.isEmpty()) return@forEach
            constructor.hook {
                before {
                    val limitIndex = intIndexes.firstOrNull { (args[it] as Int) ushr 24 == 0 }
                    if (limitIndex != null) args[limitIndex] = Int.MAX_VALUE
                    booleanIndexes.forEach { args[it] = false }
                }
            }
        }
    }

    override fun getPluginName(): String = "Share Limit"
}
