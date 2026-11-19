package com.wmods.wppenhacer.xposed.features.customization

internal object ConversationBackgroundColors {
    fun palette(nightMode: Boolean, backgroundColor: Int): Map<Int, Int> {
        if (backgroundColor == 0) return emptyMap()
        val stockColors = if (nightMode) {
            longArrayOf(0xff0b141a, 0xff0a1014, 0xff111b21, 0xff10161a, 0xff12181c, 0xff20272b)
        } else {
            longArrayOf(0xffefeae2, 0xffeae6df, 0xfff0f2f5)
        }
        return stockColors.associate { it.toInt() to backgroundColor }
    }
}
