package com.wmods.wppenhacer.xposed.features.customization

import kotlin.math.roundToInt

internal object MonetWallpaperColors {
    // Give the pattern an accent hue while keeping it quieter than chat bubbles.
    // WhatsApp's alpha-only bitmap supplies the texture's original transparency.
    fun doodleColor(surface: Int, accent: Int): Int {
        fun channel(shift: Int): Int {
            val background = (surface ushr shift) and 255
            val foreground = (accent ushr shift) and 255
            return (background + (foreground - background) * 0.18f).roundToInt()
        }
        return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
