package com.wmods.wppenhacer.xposed.features.customization

import org.junit.Assert.*
import org.junit.Test

class MonetWallpaperColorsTest {
    private fun transform(matrix: FloatArray, color: Int): FloatArray {
        val channels = floatArrayOf(
            ((color ushr 16) and 255).toFloat(), ((color ushr 8) and 255).toFloat(),
            (color and 255).toFloat(), ((color ushr 24) and 255).toFloat()
        )
        return FloatArray(4) { row ->
            (0..3).sumOf { (matrix[row * 5 + it] * channels[it]).toDouble() }.toFloat() + matrix[row * 5 + 4]
        }
    }

    @Test fun darkDefaultBackdropMatchesMonetExactly() {
        val source = 0xff0a1014.toInt()
        val result = transform(MonetWallpaperColors.colorMatrix(source, 0xff211a1b.toInt()), source)
        assertArrayEquals(floatArrayOf(33f, 26f, 27f, 255f), result, 0.001f)
    }

    @Test fun lightDefaultBackdropMatchesMonetExactly() {
        val source = 0xffefeae2.toInt()
        val result = transform(MonetWallpaperColors.colorMatrix(source, 0xfffff8f7.toInt()), source)
        assertArrayEquals(floatArrayOf(255f, 248f, 247f, 255f), result, 0.001f)
    }

    @Test fun doodleContrastSurvivesTheTint() {
        val matrix = MonetWallpaperColors.colorMatrix(0xff0a1014.toInt(), 0xff211a1b.toInt())
        val background = transform(matrix, 0xff0a1014.toInt())
        val doodle = transform(matrix, 0xff0f181d.toInt())
        for (channel in 0..2) assertTrue(doodle[channel] > background[channel])
        assertEquals(doodle[0] - background[0], doodle[1] - background[1], 0.001f)
    }

    @Test fun transparencyRemainsUnchanged() {
        val matrix = MonetWallpaperColors.colorMatrix(0xff0a1014.toInt(), 0xff211a1b.toInt())
        assertEquals(96f, transform(matrix, 0x600f181d)[3], 0.001f)
    }
}
