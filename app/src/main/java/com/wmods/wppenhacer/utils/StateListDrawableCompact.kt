package com.wmods.wppenhacer.utils

import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object StateListDrawableCompact {
    private val drawableClass = StateListDrawable::class.java

    @JvmStatic
    fun getStateCount(stateListDrawable: StateListDrawable): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return stateListDrawable.stateCount
        return try {
            val method = XposedHelpers.findMethodBestMatch(
                drawableClass,
                "getStateCount",
                *emptyArray<Class<*>>()
            )
            val result = method?.invoke(stateListDrawable)
            result as? Int ?: 0
        } catch (exception: Exception) {
            XposedBridge.log(exception)
            0
        }
    }

    @JvmStatic
    fun getStateDrawable(stateListDrawable: StateListDrawable, index: Int): Drawable? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return stateListDrawable.getStateDrawable(index)
        return try {
            val method = XposedHelpers.findMethodBestMatch(
                drawableClass,
                "getStateDrawable",
                Int::class.javaPrimitiveType
            )
            method?.invoke(stateListDrawable, index) as? Drawable
        } catch (exception: Exception) {
            XposedBridge.log(exception)
            null
        }
    }
}
