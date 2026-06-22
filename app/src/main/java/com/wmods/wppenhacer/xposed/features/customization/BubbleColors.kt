package com.wmods.wppenhacer.xposed.features.customization

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBallonBorderDrawable
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBallonDateDrawable
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBubbleDrawableMethod
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge


class BubbleColors(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        val properties = Utils.getProperties(prefs, "custom_css", "custom_filters")

        val bubbleColor = prefs.getBoolean("bubble_color", false)

        if (!bubbleColor && properties.getProperty("bubble_colors") != "true") return

        val bubbleLeftColor = if (bubbleColor) prefs.getInt(
            "bubble_left",
            0
        ) else Color.parseColor(
            DesignUtils.checkSystemColor(
                properties.getProperty(
                    "bubble_left",
                    "#00000000"
                )
            )
        )
        val bubbleRightColor = if (bubbleColor) prefs.getInt(
            "bubble_right",
            0
        ) else Color.parseColor(
            DesignUtils.checkSystemColor(
                properties.getProperty(
                    "bubble_right",
                    "#00000000"
                )
            )
        )

        val dateWrapper = loadBallonDateDrawable(classLoader)

        XposedBridge.hookMethod(dateWrapper, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val drawable = param.result as? Drawable? ?: return
                val position = param.args[0] as Int
                if (position == 3) {
                    if (bubbleRightColor == 0) return
                    drawable.colorFilter = PorterDuffColorFilter(
                        bubbleRightColor,
                        PorterDuff.Mode.SRC_IN
                    )
                } else {
                    if (bubbleLeftColor == 0) return
                    drawable.colorFilter = PorterDuffColorFilter(
                        bubbleLeftColor,
                        PorterDuff.Mode.SRC_IN
                    )
                }
            }
        })

        val babblon = loadBallonBorderDrawable(classLoader)
        XposedBridge.hookMethod(babblon, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val drawable = param.result as? Drawable? ?: return
                val position = param.args[1] as Int
                if (position == 3) {
                    if (bubbleRightColor == 0) return
                    drawable.colorFilter = PorterDuffColorFilter(
                        bubbleRightColor,
                        PorterDuff.Mode.SRC_IN
                    )
                } else {
                    if (bubbleLeftColor == 0) return
                    drawable.colorFilter = PorterDuffColorFilter(
                        bubbleLeftColor,
                        PorterDuff.Mode.SRC_IN
                    )
                }
            }
        })


        val bubbleDrawableMethod = loadBubbleDrawableMethod(classLoader)

        XposedBridge.hookMethod(bubbleDrawableMethod, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val position = param.args[0] as Int
                val draw = param.result as Drawable
                val right = position == 3
                if (right) {
                    if (bubbleRightColor == 0) return
                    draw.colorFilter = PorterDuffColorFilter(
                        bubbleRightColor,
                        PorterDuff.Mode.SRC_IN
                    )
                } else {
                    if (bubbleLeftColor == 0) return
                    draw.colorFilter = PorterDuffColorFilter(
                        bubbleLeftColor,
                        PorterDuff.Mode.SRC_IN
                    )
                }
            }
        })
    }

    override fun getPluginName(): String {
        return "Bubble Colors"
    }
}
