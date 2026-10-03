package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

class CustomTime(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    @Throws(Exception::class)
    override fun doHook() {
        val secondsToTime = xprefs.getBoolean("segundos", false)
        val ampm = xprefs.getBoolean("ampm", false)
        val secondsToTimeMethod = Unobfuscator.loadTimeToSecondsMethod(classLoader)
        val textInHour = xprefs.getString("text_in_hour", "[TIME]") ?: "[TIME]"
        val pattern = if (ampm) {
            if (secondsToTime) "hh:mm:ss a" else "hh:mm a"
        } else {
            if (secondsToTime) "HH:mm:ss" else "HH:mm"
        }
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
            .withZone(ZoneId.systemDefault())
        val defaultTimeTemplate = textInHour == "[TIME]"
        val hasTimeToken = textInHour.contains("[TIME]")

        logDebug(Unobfuscator.getMethodDescriptor(secondsToTimeMethod))
        secondsToTimeMethod.hook {
            after {
                val calendar = args[1] as Calendar
                val formattedHour = formatter.format(Instant.ofEpochMilli(calendar.timeInMillis))
                result = when {
                    defaultTimeTemplate -> formattedHour
                    hasTimeToken -> textInHour.replace("[TIME]", formattedHour)
                    textInHour.isNotEmpty() -> "$textInHour $formattedHour"
                    else -> formattedHour
                }

            }
        }
    }

    override fun getPluginName(): String {
        return "Seconds To Time"
    }
}
