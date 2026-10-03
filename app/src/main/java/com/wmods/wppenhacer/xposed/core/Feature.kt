package com.wmods.wppenhacer.xposed.core

import android.content.SharedPreferences
import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog

abstract class Feature(
    @JvmField val classLoader: ClassLoader,
    @JvmField val xprefs: SharedPreferences
) : YukiBaseHooker() {

    companion object {
        @JvmField
        var DEBUG = false
    }

    override fun onHook() {
    }

    @Throws(Throwable::class)
    abstract fun doHook()

    abstract fun getPluginName(): String

    private fun formatObject(obj: Any?): String {
        if (obj == null) return "null"

        if (obj.javaClass.isArray) {
            return when (obj) {
                is Array<*> -> obj.contentToString()
                is IntArray -> obj.contentToString()
                is ByteArray -> obj.contentToString()
                is ShortArray -> obj.contentToString()
                is LongArray -> obj.contentToString()
                is FloatArray -> obj.contentToString()
                is DoubleArray -> obj.contentToString()
                is BooleanArray -> obj.contentToString()
                is CharArray -> obj.contentToString()
                else -> obj.toString()
            }
        }
        return if (obj is Throwable) obj.stackTraceToString() else obj.toString()
    }

    fun logDebug(obj: Any?) {
        if (!DEBUG) return

        val formattedStr = formatObject(obj)
        log(formattedStr)

        if (obj is Throwable) {
            Log.i("Vector-lsposed", "${getPluginName()}-> ${obj.message}", obj)
        } else {
            Log.i("Vector-lsposed", "${getPluginName()}-> $formattedStr")
        }
    }

    fun logDebug(title: String, obj: Any?) {
        if (!DEBUG) return

        val formattedStr = formatObject(obj)
        log("$title: $formattedStr")

        if (obj is Throwable) {
            Log.i("WAE", "${getPluginName()}-> $title: ${obj.message}", obj)
        } else {
            Log.i("WAE", "${getPluginName()}-> $title: $formattedStr")
        }
    }

    fun log(obj: Any?) {
        if (obj is Throwable) {
            YLog.error(String.format("[%s] Error:", getPluginName()), obj)
        } else {
            YLog.debug(String.format("[%s] %s", getPluginName(), formatObject(obj)))
        }
    }
}
