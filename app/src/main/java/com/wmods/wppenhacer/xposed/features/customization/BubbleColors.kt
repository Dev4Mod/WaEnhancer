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

        val monetEnabled = DesignUtils.shouldUseMonetColors(xprefs)
        if (!bubbleColor && properties.getProperty("bubble_colors") != "true" && !monetEnabled) return

        val manualColors = if (bubbleColor) BubbleColorSettings.Colors(
            xprefs.getInt("bubble_left", 0), xprefs.getInt("bubble_right", 0)
        ) else null
        val palettes = HashMap<Boolean, BubbleColorSettings.Colors>()
        fun currentColors(): BubbleColorSettings.Colors {
            val nightMode = DesignUtils.isNightMode()
            return palettes.getOrPut(nightMode) {
                BubbleColorSettings.resolve(
                    manualColors, properties, monetEnabled, nightMode
                ) { color ->
                    val resolved = DesignUtils.checkSystemColor(color)
                    if (DesignUtils.isValidColor(resolved)) resolved.toColorInt() else 0
                }!!
            }
        }

        val dateWrapper = loadBallonDateDrawable(classLoader)

        dateWrapper.hook {
            after {
                val drawable = result as? Drawable? ?: return@after
                val (bubbleLeftColor, bubbleRightColor) = currentColors()
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
                val (bubbleLeftColor, bubbleRightColor) = currentColors()
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
                val (bubbleLeftColor, bubbleRightColor) = currentColors()
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
