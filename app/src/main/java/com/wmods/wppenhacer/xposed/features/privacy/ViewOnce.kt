package com.wmods.wppenhacer.xposed.features.privacy

import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadViewOnceMethod
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge


class ViewOnce(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        if (!prefs.getBoolean("viewonce", false)) return

        val methods = loadViewOnceMethod(classLoader)

        methods.forEach { method ->
            logDebug(getMethodDescriptor(method))
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val returnValue = param.args[0] as Int
                    val fMessage = FMessageWpp(param.thisObject)
                    if (returnValue == 1 && !fMessage.key.isFromMe) {
                        param.args[0] = 0
                    }
                }
            })
        }

    }

    override fun getPluginName(): String {
        return "View Once"
    }
}