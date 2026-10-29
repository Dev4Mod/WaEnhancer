package com.wmods.wppenhacer.xposed.features.customization

import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

class BubbleColorSettingsTest {
    private val requestedColors = mutableListOf<String>()
    private val resolver: (String) -> Int = {
        requestedColors.add(it)
        requestedColors.size
    }

    @Test fun monetEnablesBubblesWithoutManualOrCssConfiguration() {
        assertEquals(BubbleColorSettings.Colors(1, 2),
            BubbleColorSettings.resolve(null, Properties(), true, true, resolver))
        assertEquals(listOf("color_system_neutral1_800", "color_system_accent1_700"), requestedColors)
    }

    @Test fun lightModeUsesLightContainersForDarkMessageText() {
        BubbleColorSettings.resolve(null, Properties(), true, false, resolver)
        assertEquals(listOf("color_system_neutral1_10", "color_system_accent1_100"), requestedColors)
    }

    @Test fun disabledMonetLeavesBubblesAlone() {
        assertNull(BubbleColorSettings.resolve(null, Properties(), false, true, resolver))
        assertTrue(requestedColors.isEmpty())
    }

    @Test fun manualColorsIncludingZeroOverrideCssAndMonet() {
        val manual = BubbleColorSettings.Colors(0, 123)
        val css = Properties().apply { setProperty("bubble_colors", "true") }
        assertEquals(manual, BubbleColorSettings.resolve(manual, css, true, true, resolver))
        assertTrue(requestedColors.isEmpty())
    }

    @Test fun explicitCssWinsOverMonetAndKeepsUnspecifiedSideUntouched() {
        val css = Properties().apply {
            setProperty("bubble_colors", "true")
            setProperty("bubble_right", "color_system_accent2_700")
        }
        BubbleColorSettings.resolve(null, css, true, true, resolver)
        assertEquals(listOf("#00000000", "color_system_accent2_700"), requestedColors)
    }

    @Test fun cssColorsStillWorkWithoutMonet() {
        val css = Properties().apply { setProperty("bubble_colors", "true") }
        assertNotNull(BubbleColorSettings.resolve(null, css, false, false, resolver))
    }
}
