package com.wmods.wppenhacer.xposed.features.customization

import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

class CustomTime(loader: ClassLoader, preferences: XSharedPreferences) : Feature(loader, preferences) {

    @Throws(Exception::class)
    override fun doHook() {
        val secondsToTime = prefs.getBoolean("segundos", false)
        val ampm = prefs.getBoolean("ampm", false)
        val secondsToTimeMethod = Unobfuscator.loadTimeToSecondsMethod(classLoader)
        val textInHour = prefs.getString("text_in_hour", "[TIME]") ?: "[TIME]"
        
        logDebug(Unobfuscator.getMethodDescriptor(secondsToTimeMethod))
        XposedBridge.hookMethod(secondsToTimeMethod, object : XC_MethodHook(){

            override fun afterHookedMethod(param: MethodHookParam) {
                val calendar = param.args[1] as Calendar
                val timestamp = calendar.timeInMillis
                val zonedDateTime = Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())

                val pattern = if (ampm) {
                    if (secondsToTime) "hh:mm:ss a" else "hh:mm a"
                } else {
                    if (secondsToTime) "HH:mm:ss" else "HH:mm"
                }

                var formattedHour = zonedDateTime.format(DateTimeFormatter.ofPattern(pattern, Locale.US))

                if (textInHour.contains("[TIME]")) {
                    formattedHour = textInHour.replace("[TIME]", formattedHour)
                } else if (textInHour.isNotEmpty()) {
                    formattedHour = "$textInHour $formattedHour"
                }

                param.result = formattedHour

            }

        })
    }

    override fun getPluginName(): String {
        return "Seconds To Time"
    }
}