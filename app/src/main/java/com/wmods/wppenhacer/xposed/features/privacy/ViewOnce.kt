package com.wmods.wppenhacer.xposed.features.privacy

import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadViewOnceMethod


class ViewOnce(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        if (!xprefs.getBoolean("viewonce", false)) return

        val methods = loadViewOnceMethod(classLoader)

        methods.forEach { method ->
            logDebug(getMethodDescriptor(method))
            method.hook {
                before {
                    val returnValue = args[0] as Int
                    val fMessage = FMessageWpp(instance)
                    if (returnValue == 1 && !fMessage.key.isFromMe) {
                        args[0] = 0
                    }
                }
            }
        }

    }

    override fun getPluginName(): String {
        return "View Once"
    }
}