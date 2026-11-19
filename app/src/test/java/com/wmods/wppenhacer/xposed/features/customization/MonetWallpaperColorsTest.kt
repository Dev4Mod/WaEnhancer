package com.wmods.wppenhacer.xposed.features.customization

import org.junit.Assert.*
import org.junit.Test

class MonetWallpaperColorsTest {
    @Test fun darkDoodlesKeepTheAccentHueInsteadOfTurningGray() {
        val surface = 0xff1b1b1b.toInt()
        val color = MonetWallpaperColors.doodleColor(surface, 0xffff8080.toInt())
        assertEquals(0xff442d2d.toInt(), color)
        assertTrue(((color ushr 16) and 255) > ((color ushr 8) and 255))
        assertNotEquals(surface, color)
    }

    @Test fun lightDoodlesAreMutedAndDistinctFromTheSurface() {
        assertEquals(0xffdadaf0.toInt(),
            MonetWallpaperColors.doodleColor(0xfff8f8f8.toInt(), 0xff5050cc.toInt()))
    }

    @Test fun neutralPalettesStayNeutral() {
        val color = MonetWallpaperColors.doodleColor(0xff202020.toInt(), 0xffcccccc.toInt())
        assertEquals((color ushr 16) and 255, color and 255)
        assertEquals((color ushr 8) and 255, color and 255)
    }

    @Test fun tintIsOpaqueSoTheBitmapControlsDoodleTransparency() {
        assertEquals(255, MonetWallpaperColors.doodleColor(0xff202020.toInt(), 0xff80aacc.toInt()) ushr 24)
    }
}
