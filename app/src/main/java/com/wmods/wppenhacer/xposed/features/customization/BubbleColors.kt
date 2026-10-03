package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.core.graphics.toColorInt
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBallonBorderDrawable
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBallonDateDrawable
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBubbleDrawableMethod
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils


class BubbleColors(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        val properties = Utils.getProperties(xprefs, "custom_css", "custom_filters")

        val bubbleColor = xprefs.getBoolean("bubble_color", false)

        if (!bubbleColor && properties.getProperty("bubble_colors") != "true") return

        val bubbleLeftColor = if (bubbleColor) xprefs.getInt(
            "bubble_left",
            0
        ) else DesignUtils.checkSystemColor(
            properties.getProperty(
                "bubble_left",
                "#00000000"
            )
        ).toColorInt()
        val bubbleRightColor = if (bubbleColor) xprefs.getInt(
            "bubble_right",
            0
        ) else DesignUtils.checkSystemColor(
            properties.getProperty(
                "bubble_right",
                "#00000000"
            )
        ).toColorInt()

        val dateWrapper = loadBallonDateDrawable(classLoader)

        dateWrapper.hook {
            after {
                val drawable = result as? Drawable? ?: return@after
                val position = args[0] as Int
                if (position == 3) {
                    if (bubbleRightColor == 0) return@after
                    drawable.colorFilter = PorterDuffColorFilter(
                        bubbleRightColor,
                        PorterDuff.Mode.SRC_IN
                    )
                } else {
                    if (bubbleLeftColor == 0) return@after
                    drawable.colorFilter = PorterDuffColorFilter(
                        bubbleLeftColor,
                        PorterDuff.Mode.SRC_IN
                    )
                }
            }
        }

        val babblon = loadBallonBorderDrawable(classLoader)
        babblon.hook {
            after {
                val drawable = result as? Drawable? ?: return@after
                val position = args[1] as Int
                if (position == 3) {
                    if (bubbleRightColor == 0) return@after
                    drawable.colorFilter = PorterDuffColorFilter(
                        bubbleRightColor,
                        PorterDuff.Mode.SRC_IN
                    )
                } else {
                    if (bubbleLeftColor == 0) return@after
                    drawable.colorFilter = PorterDuffColorFilter(
                        bubbleLeftColor,
                        PorterDuff.Mode.SRC_IN
                    )
                }
            }
        }


        val bubbleDrawableMethod = loadBubbleDrawableMethod(classLoader)

        bubbleDrawableMethod.hook {
            after {
                val position = args[0] as Int
                val draw = result as Drawable
                val right = position == 3
                if (right) {
                    if (bubbleRightColor == 0) return@after
                    draw.colorFilter = PorterDuffColorFilter(
                        bubbleRightColor,
                        PorterDuff.Mode.SRC_IN
                    )
                } else {
                    if (bubbleLeftColor == 0) return@after
                    draw.colorFilter = PorterDuffColorFilter(
                        bubbleLeftColor,
                        PorterDuff.Mode.SRC_IN
                    )
                }
            }
        }
    }

    override fun getPluginName(): String {
        return "Bubble Colors"
    }
}
