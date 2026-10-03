package com.wmods.wppenhacer.xposed.features.privacy

import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import java.lang.reflect.Method

class TypingPrivacy(
    loader: ClassLoader,
    preferences: SharedPreferences
) : Feature(loader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        val ghostmode = WppCore.getPrivBoolean("ghostmode", false)
        val ghostmodeT = xprefs.getBoolean("ghostmode_t", false)
        val ghostmodeR = xprefs.getBoolean("ghostmode_r", false)

        val method: Method = Unobfuscator.loadGhostModeMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(method))

        method.hook {
            before {
                val type = ReflectionUtils.getArg(args, Int::class.javaObjectType, 0)
                val jidObj = ReflectionUtils.getArg(args, FMessageWpp.UserJid.TYPE_JID, 0)

                if (jidObj == null) {
                    logDebug("UserJid not found in Typing Privacy")
                }

                val userJid = FMessageWpp.UserJid(jidObj)
                val privacy = CustomPrivacy.getJSON(userJid.phoneNumber)

                val customHideTyping = privacy.optBoolean("HideTyping", ghostmodeT) || ghostmode
                val customHideRecording =
                    privacy.optBoolean("HideRecording", ghostmodeR) || ghostmode

                if ((type == 1 && customHideRecording) ||
                    (type == 0 && customHideTyping)
                ) {
                    result = null
                }
            }
        }
    }

    override fun getPluginName(): String {
        return "Typing Privacy"
    }
}