package com.wmods.wppenhacer.xposed.features.customization

import org.junit.Assert.*
import org.junit.Test

class ChatInfoPaletteTest {
    private val palette = ChatInfoPalette(
        0xff300d1b.toInt(), 0xfff3b6c7.toInt(), 0xff300d1b.toInt(),
        0xffd697a9.toInt(), 0xff553341.toInt()
    )

    @Test fun offAndOnTogglesHaveDistinctThumbsAndTracks() {
        assertEquals(palette.onAccent, palette.thumb(true, true))
        assertEquals(palette.accent, palette.track(true, true))
        assertEquals(palette.inactiveThumb, palette.thumb(true, false))
        assertEquals(palette.inactiveTrack, palette.track(true, false))
        assertNotEquals(palette.thumb(true, true), palette.thumb(true, false))
        assertNotEquals(palette.track(true, true), palette.track(true, false))
    }

    @Test fun disabledTogglesStayMutedRegardlessOfTheirCheckedState() {
        assertEquals(palette.thumb(false, false), palette.thumb(false, true))
        assertEquals(palette.track(false, false), palette.track(false, true))
        assertTrue((palette.thumb(false, false) ushr 24) < 255)
        assertTrue((palette.track(false, false) ushr 24) < 255)
    }

    @Test fun lightAndDarkPalettesUseDifferentContainerTones() {
        val requests = mutableListOf<String>()
        val resolver: (String) -> Int = { requests.add(it); -1 }
        assertNotNull(ChatInfoPalette.resolve(true, resolver))
        assertEquals(listOf("color_system_neutral1_900") + listOf(300, 900, 400, 800).map { "color_system_accent1_$it" }, requests)
        requests.clear()
        assertNotNull(ChatInfoPalette.resolve(false, resolver))
        assertEquals(listOf("color_system_neutral1_10") + listOf(600, 0, 500, 100).map { "color_system_accent1_$it" }, requests)
    }

    @Test fun unavailableColorsDoNotProduceATransparentTheme() {
        assertNull(ChatInfoPalette.resolve(true) { 0 })
    }
}
