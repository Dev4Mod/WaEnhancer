package com.wmods.wppenhacer.xposed.features.privacy

import android.content.Context
import android.view.View
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge

class HideChat(loader: ClassLoader, preferences:SharedPreferences) : Feature(loader, preferences) {

    override fun doHook() {
        if (prefs.getString("typearchive", "0") != "0") {

            val loadArchiveChatClass = Unobfuscator.loadArchiveChatClass(classLoader)

            val viewField =
                ReflectionUtils.getFieldByType(loadArchiveChatClass, View::class.java) ?: return

            XposedBridge.hookAllConstructors(loadArchiveChatClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val currentActivity = WppCore.getCurrentActivity() ?: return
                    viewField.set(param.thisObject, HideView(currentActivity))
                }
            })
        }
    }

    override fun getPluginName(): String {
        return "Hide Chats"
    }

    class HideView(context: Context) : View(context) {

        init {
            visibility = GONE
        }

        override fun setVisibility(visibility: Int) {
        }

    }
}