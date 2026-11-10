package com.wmods.wppenhacer.xposed.features.customization

import org.junit.Assert.*
import org.junit.Test

class ConversationBackgroundColorsTest {
    private val surface = 0xff21171a.toInt()

    @Test fun darkConversationBackdropsUseTheMonetSurface() {
        val colors = ConversationBackgroundColors.palette(true, surface)
        assertEquals(surface, colors[0xff0b141a.toInt()])
        assertEquals(surface, colors[0xff111b21.toInt()])
        assertEquals(surface, colors[0xff12181c.toInt()])
    }

    @Test fun lightWallpaperColorsUseTheMonetSurface() {
        val colors = ConversationBackgroundColors.palette(false, surface)
        assertEquals(surface, colors[0xffefeae2.toInt()])
        assertEquals(surface, colors[0xffeae6df.toInt()])
        assertFalse(colors.containsKey(0xff111b21.toInt()))
    }

    @Test fun bubblesAndMessageForegroundsAreNotBackgrounds() {
        for (nightMode in listOf(false, true)) {
            val colors = ConversationBackgroundColors.palette(nightMode, surface)
            for (color in longArrayOf(0xff005c4b, 0xff202c33, 0xffd9fdd3,
                0xffe9edef, 0xff000000, 0xffffffff)) {
                assertFalse(colors.containsKey(color.toInt()))
            }
        }
    }

    @Test fun unavailablePaletteDoesNotEraseTheBackground() {
        assertTrue(ConversationBackgroundColors.palette(true, 0).isEmpty())
        assertTrue(ConversationBackgroundColors.palette(false, 0).isEmpty())
    }
}
