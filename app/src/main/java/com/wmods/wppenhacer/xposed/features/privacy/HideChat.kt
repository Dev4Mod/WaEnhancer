package com.wmods.wppenhacer.xposed.features.privacy

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils

class HideChat(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {

    override fun doHook() {
        if (xprefs.getString("typearchive", "0") != "0") {

            val loadArchiveChatClass = Unobfuscator.loadArchiveChatClass(classLoader)

            val viewField =
                ReflectionUtils.getFieldByType(loadArchiveChatClass, View::class.java) ?: return

            loadArchiveChatClass.resolve().constructor { }.hookAll {
                after {
                    val currentActivity = WppCore.getCurrentActivity() ?: return@after
                    viewField.set(instance, HideView(currentActivity))
                }
            }
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