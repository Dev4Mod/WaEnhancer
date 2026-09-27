package com.wmods.wppenhacer.utils

import android.graphics.PorterDuffColorFilter
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ImageView
import android.widget.TextView
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import de.robv.android.xposed.XposedHelpers
import java.util.HashMap

object ColorReplacement {
    @JvmStatic
    fun replaceColors(view: View?, colors: HashMap<String, String>?) {
        if (view == null) return
        val colorMap = colors!!
        when (view) {
            is ImageView -> replaceImage(view, colorMap)
            is TextView -> replaceText(view, colorMap)
            is ViewGroup -> replaceGroup(view, colorMap)
            is ViewStub -> DrawableColors.replaceColor(view.background, colorMap)
        }
    }

    private fun replaceImage(view: ImageView, colors: HashMap<String, String>) {
        DrawableColors.replaceColor(view.background, colors)
        val colorFilter = view.colorFilter ?: return
        if (colorFilter is PorterDuffColorFilter) {
            val color = XposedHelpers.callMethod(colorFilter, "getColor") as Int
            val stringColor = IColors.toString(color)
            var newColor = colors[stringColor]
            if (newColor != null) {
                view.setColorFilter(IColors.parseColor(newColor))
            } else if (!stringColor.startsWith("#ff") && !stringColor.startsWith("#0")) {
                val prefix = stringColor.substring(0, 3)
                newColor = colors[stringColor.substring(3)]
                if (newColor != null) view.setColorFilter(IColors.parseColor(prefix + newColor))
            }
        }
    }

    private fun replaceText(view: TextView, colors: HashMap<String, String>) {
        val stringColor = IColors.toString(view.currentTextColor)
        if (stringColor == "#ffffffff" && !DesignUtils.isNightMode()) return

        DrawableColors.replaceColor(view.background, colors)
        var newColor = colors[stringColor]
        if (newColor != null) {
            view.setTextColor(IColors.parseColor(newColor))
        } else if (!stringColor.startsWith("#ff") && !stringColor.startsWith("#0")) {
            val prefix = stringColor.substring(0, 3)
            newColor = colors[stringColor.substring(3)]
            if (newColor != null) view.setTextColor(IColors.parseColor(prefix + newColor))
        }
    }

    private fun replaceGroup(view: ViewGroup, colors: HashMap<String, String>) {
        val background = view.background
        for (index in 0 until view.childCount) {
            replaceColors(view.getChildAt(index), colors)
        }
        DrawableColors.replaceColor(background, colors)
    }
}
