package com.wmods.wppenhacer.xposed.utils

import org.junit.Assert.*
import org.junit.Test

class ThemePropertiesTest {
    @Test fun editorGlobalsOverrideThemeAndPreserveOtherSettings() {
        val properties = ThemeProperties.parse(
            "/* bubble_colors = true\nbubble_left = #112233\nbubble_right = #445566 */",
            "/* bubble_right = color_system_accent1_700 */"
        )
        assertEquals("true", properties.getProperty("bubble_colors"))
        assertEquals("#112233", properties.getProperty("bubble_left"))
        assertEquals("color_system_accent1_700", properties.getProperty("bubble_right"))
    }

    @Test fun malformedAndBlankLinesDoNotDiscardValidGlobals() {
        val properties = ThemeProperties.parse("""
            /*
            Theme description

            missing_value =
            = missing_key
            author = "A Theme"
            wallpaper_file = "wall=paper.png"
            bubble_colors = true
            */
            #view { color: red; }
        """.trimIndent())
        assertEquals(3, properties.size)
        assertEquals("A Theme", properties.getProperty("author"))
        assertEquals("wall=paper.png", properties.getProperty("wallpaper_file"))
        assertEquals("true", properties.getProperty("bubble_colors"))
    }

    @Test fun ordinaryCssAndLaterCommentsAreNotGlobals() {
        assertTrue(ThemeProperties.parse("#view {}\n/* bubble_colors = true */").isEmpty())
        assertTrue(ThemeProperties.parse("", "/* unterminated").isEmpty())
    }
}
