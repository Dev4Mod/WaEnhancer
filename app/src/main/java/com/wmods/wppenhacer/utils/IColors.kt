package com.wmods.wppenhacer.utils

import android.graphics.Color

object IColors {
    @JvmField
    var colors = HashMap<String, String>()

    @JvmField
    var alphacolors = HashMap<String, String>()

    @JvmField
    val backgroundColors = HashMap<String, String>()

    @JvmField
    val primaryColors = HashMap<String, String>()

    @JvmField
    val textColors = HashMap<String, String>()

    @JvmStatic
    fun parseColor(color: String): Int = Color.parseColor(color)

    @JvmStatic
    fun toString(color: Int): String {
        var hexColor = Integer.toHexString(color)
        if (hexColor.length == 7) {
            hexColor = "0$hexColor"
        } else if (hexColor.length == 1) {
            hexColor = "00000000"
        }
        return "#$hexColor"
    }

    @JvmStatic
    fun getFromIntColor(color: Int, colorMap: HashMap<String, String>): Int {
        val stringColor = toString(color)
        var newColor = colorMap[stringColor]
        if (newColor != null && newColor.length == 9) {
            return parseColor(newColor)
        }
        if (!stringColor.startsWith("#ff")) {
            val colorPrefix = stringColor.substring(0, 3)
            newColor = colorMap[stringColor.substring(3)]
            if (newColor != null) return parseColor(colorPrefix + newColor)
        }
        return color
    }

    @JvmStatic
    fun initColors() {
        primaryColors.clear()
        textColors.clear()
        backgroundColors.clear()
        colors.clear()

        primaryColors.put("00a884", "00a884")
        primaryColors.put("1da457", "1da457")
        primaryColors.put("21c063", "21c063")
        primaryColors.put("d9fdd3", "d9fdd3")
        primaryColors.put("#ff00a884", "#ff00a884")
        primaryColors.put("#ff1da457", "#ff1da457")
        primaryColors.put("#ff21c063", "#ff21c063")
        primaryColors.put("#ff1daa61", "#ff1daa61")
        primaryColors.put("#ff25d366", "#ff25d366")
        primaryColors.put("#ffd9fdd3", "#ffd9fdd3")
        primaryColors.put("#ff1b864b", "#ff1b864b")
        primaryColors.put("#ff144d37", "#ff144d37")
        primaryColors.put("#ff1b8755", "#ff1b8755")
        primaryColors.put("#ff15603e", "#ff15603e")
        primaryColors.put("#ff103529", "#c0103529")

        textColors.put("#ffeaedee", "#ffeaedee")
        textColors.put("#fff7f8fa", "#fff7f8fa")

        backgroundColors.put("0b141a", "0a1014")
        backgroundColors.put("#ff0b141a", "#ff111b21")
        backgroundColors.put("#ff111b21", "#ff111b21")
        backgroundColors.put("#ff000000", "#ff000000")
        backgroundColors.put("#ff0a1014", "#ff0a1014")
        backgroundColors.put("#ff10161a", "#ff10161a")
        backgroundColors.put("#ff12181c", "#ff12181c")
        backgroundColors.put("#ff20272b", "#ff20272b")

        alphacolors.put("#ff15603e", "#8015603e")
    }
}
