package com.wmods.wppenhacer.xposed.features.customization

internal data class ChatInfoPalette(
    val background: Int,
    val accent: Int,
    val onAccent: Int,
    val inactiveThumb: Int,
    val inactiveTrack: Int
) {
    fun thumb(enabled: Boolean, checked: Boolean): Int = when {
        !enabled -> withAlpha(inactiveThumb, 0.38f)
        checked -> onAccent
        else -> inactiveThumb
    }

    fun track(enabled: Boolean, checked: Boolean): Int = when {
        !enabled -> withAlpha(accent, 0.12f)
        checked -> accent
        else -> inactiveTrack
    }

    companion object {
        fun resolve(nightMode: Boolean, resolveColor: (String) -> Int): ChatInfoPalette? {
            val background = resolveColor(
                if (nightMode) "color_system_neutral1_900" else "color_system_neutral1_10"
            )
            val tones = if (nightMode) listOf(300, 900, 400, 800)
            else listOf(600, 0, 500, 100)
            val colors = listOf(background) + tones.map { resolveColor("color_system_accent1_$it") }
            if (colors.any { it == 0 }) return null
            return ChatInfoPalette(colors[0], colors[1], colors[2], colors[3], colors[4])
        }

        private fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00ffffff) or ((((color ushr 24) * alpha).toInt()) shl 24)
    }
}
