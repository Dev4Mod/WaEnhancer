package com.wmods.wppenhacer.xposed.features.customization

internal object MonetWallpaperColors {
    // Keep the pattern's luminance differences, but anchor its hue and backdrop
    // to the Monet surface. The alpha-only doodle texture remains unchanged.
    fun colorMatrix(source: Int, surface: Int): FloatArray {
        val red = 0.2126f
        val green = 0.7152f
        val blue = 0.0722f
        val sourceLuminance = ((source ushr 16) and 255) * red +
                ((source ushr 8) and 255) * green + (source and 255) * blue
        return floatArrayOf(
            red, green, blue, 0f, ((surface ushr 16) and 255) - sourceLuminance,
            red, green, blue, 0f, ((surface ushr 8) and 255) - sourceLuminance,
            red, green, blue, 0f, (surface and 255) - sourceLuminance,
            0f, 0f, 0f, 1f, 0f
        )
    }
}
