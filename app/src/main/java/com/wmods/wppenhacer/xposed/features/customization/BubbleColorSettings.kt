package com.wmods.wppenhacer.xposed.features.customization

import java.util.Properties

internal object BubbleColorSettings {
    data class Colors(val left: Int, val right: Int)

    fun resolve(
        manualColors: Colors?,
        properties: Properties,
        monetEnabled: Boolean,
        nightMode: Boolean,
        resolveColor: (String) -> Int
    ): Colors? {
        if (manualColors != null) return manualColors
        if (properties.getProperty("bubble_colors") == "true") {
            return Colors(
                resolveColor(properties.getProperty("bubble_left", "#00000000")),
                resolveColor(properties.getProperty("bubble_right", "#00000000"))
            )
        }
        if (!monetEnabled) return null

        // Bubble text keeps WhatsApp's light/dark foreground. Use container tones,
        // rather than the bright accent used for buttons in the dark theme.
        return Colors(
            resolveColor(if (nightMode) "color_system_neutral1_800" else "color_system_neutral1_10"),
            resolveColor(if (nightMode) "color_system_accent1_700" else "color_system_accent1_100")
        )
    }
}
