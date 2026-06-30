package com.wmods.wppenhacer.xposed.utils

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.XResources
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.wmods.wppenhacer.WppXposed
import com.wmods.wppenhacer.utils.IColors
import com.wmods.wppenhacer.xposed.core.WppCore
import de.robv.android.xposed.XposedBridge

object DesignUtils {

    private var mPrefs: SharedPreferences? = null

    @SuppressLint("UseCompatLoadingForDrawables")
    @JvmStatic
    fun getDrawable(id: Int): Drawable {
        return Utils.application.getDrawable(id)!!
    }

    @JvmStatic
    fun getDrawableByName(name: String): Drawable? {
        val id = Utils.getID(name, "drawable")
        if (id == 0) return null
        return getDrawable(id)
    }

    @JvmStatic
    fun getIconByName(name: String, isTheme: Boolean): Drawable? {
        val id = Utils.getID(name, "drawable")
        if (id == 0) return null
        val icon = getDrawable(id)
        if (isTheme && icon != null) {
            return coloredDrawable(icon, if (isNightMode()) Color.WHITE else Color.BLACK)
        }
        return icon
    }

    @JvmStatic
    fun coloredDrawable(drawable: Drawable, color: Int): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawable.colorFilter = BlendModeColorFilter(color, BlendMode.SRC_ATOP)
        } else {
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
        return drawable
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @JvmStatic
    fun alphaDrawable(drawable: Drawable, primaryTextColor: Int, i: Int): Drawable {
        val coloredDrawable = coloredDrawable(drawable, primaryTextColor)
        coloredDrawable.alpha = i
        return coloredDrawable
    }

    @JvmStatic
    fun createDrawable(type: String, color: Int): Drawable {
        return when (type) {
            "rc_dialog_bg" -> {
                val border = Utils.dipToPixels(12.0f).toFloat()
                val shapeDrawable = ShapeDrawable(
                    RoundRectShape(floatArrayOf(border, border, border, border, 0f, 0f, 0f, 0f), null, null)
                )
                shapeDrawable.paint.color = color
                shapeDrawable
            }
            "selector_bg" -> {
                val border = Utils.dipToPixels(18.0f).toFloat()
                val selectorBg = ShapeDrawable(
                    RoundRectShape(floatArrayOf(border, border, border, border, border, border, border, border), null, null)
                )
                selectorBg.paint.color = color
                selectorBg
            }
            "rc_dotline_dialog" -> {
                val border = Utils.dipToPixels(16.0f).toFloat()
                val shapeDrawable = ShapeDrawable(
                    RoundRectShape(floatArrayOf(border, border, border, border, border, border, border, border), null, null)
                )
                shapeDrawable.paint.color = color
                shapeDrawable
            }
            "stroke_border" -> {
                val radius = Utils.dipToPixels(18.0f).toFloat()
                val outerRadii = floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius)
                val roundRectShape = RoundRectShape(outerRadii, null, null)
                val shapeDrawable = ShapeDrawable(roundRectShape)
                val paint = shapeDrawable.paint
                paint.color = Color.TRANSPARENT
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = Utils.dipToPixels(2).toFloat()
                paint.color = color
                val inset = Utils.dipToPixels(2)
                InsetDrawable(shapeDrawable, inset, inset, inset, inset)
            }
            else -> ColorDrawable(Color.BLACK)
        }
    }

    @JvmStatic
    fun getPrimaryTextColor(): Int {
        var textColor = mPrefs?.getInt("text_color", 0) ?: 0
        if (shouldUseMonetColors()) {
            val monetTextColor = resolveMonetColor(if (isNightMode()) "system_neutral1_100" else "system_neutral1_900")
            if (monetTextColor != 0) {
                textColor = monetTextColor
            }
        }
        if (textColor == 0 || mPrefs?.getBoolean("changecolor", false) != true) {
            return if (isNightMode()) -2 else -16777215
        }
        return textColor
    }

    @JvmStatic
    fun getUnSeenColor(): Int {
        var primaryColor = mPrefs?.getInt("primary_color", 0) ?: 0
        if (shouldUseMonetColors()) {
            val monetPrimaryColor = resolveMonetColor(if (isNightMode()) "system_accent1_300" else "system_accent1_600")
            if (monetPrimaryColor != 0) {
                primaryColor = monetPrimaryColor
            }
        }
        if (primaryColor == 0 || mPrefs?.getBoolean("changecolor", false) != true) {
            return 0xFF25d366.toInt()
        }
        return primaryColor
    }

    @JvmStatic
    fun getPrimarySurfaceColor(): Int {
        var backgroundColor = mPrefs?.getInt("background_color", 0) ?: 0
        if (shouldUseMonetColors()) {
            val monetBackgroundColor = resolveMonetColor(if (isNightMode()) "system_neutral1_900" else "system_neutral1_10")
            if (monetBackgroundColor != 0) {
                backgroundColor = monetBackgroundColor
            }
        }
        if (backgroundColor == 0 || mPrefs?.getBoolean("changecolor", false) != true) {
            return if (isNightMode()) -15132398 else -2
        }
        return backgroundColor
    }

    @JvmStatic
    fun generatePrimaryColorDrawable(drawable: Drawable?): Drawable? {
        if (drawable == null) return null
        var primaryColorInt = mPrefs?.getInt("primary_color", 0) ?: 0
        if (shouldUseMonetColors()) {
            val monetPrimaryColor = resolveMonetColor(if (isNightMode()) "system_accent1_300" else "system_accent1_600")
            if (monetPrimaryColor != 0) {
                primaryColorInt = monetPrimaryColor
            }
        }
        if (primaryColorInt != 0 && mPrefs?.getBoolean("changecolor", false) == true) {
            val bitmap = drawableToBitmap(drawable)
            val color = getDominantColor(bitmap)
            val newBitmap = replaceColor(bitmap, color, primaryColorInt, 120.0)
            return BitmapDrawable(Utils.application.resources, newBitmap)
        }
        return null
    }

    @JvmStatic
    fun setReplacementDrawable(name: String, replacement: Drawable?) {
        if (WppXposed.ResParam == null) return
        WppXposed.ResParam!!.res.setReplacement(
            Utils.application.packageName, "drawable", name,
            object : XResources.DrawableLoader() {
                override fun newDrawable(res: XResources, id: Int): Drawable {
                    return replacement!!
                }
            }
        )
    }

    @JvmStatic
    fun isNightMode(): Boolean {
        return if (WppCore.getDefaultTheme() <= 0) isNightModeBySystem() else WppCore.getDefaultTheme() == 2
    }

    @JvmStatic
    fun isNightModeBySystem(): Boolean {
        return (Utils.application.resources.configuration.uiMode and 48) == 32
    }

    @JvmStatic
    fun setPrefs(prefs: SharedPreferences) {
        mPrefs = prefs
    }

    @JvmStatic
    fun isValidColor(primaryColor: String?): Boolean {
        return try {
            Color.parseColor(primaryColor)
            true
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun checkSystemColor(color: String?): String {
        if (isValidColor(color)) {
            return color!!
        }
        try {
            if (color != null && color.startsWith("color_")) {
                val idColor = color.replace("color_", "")
                val colorRes = android.R.color::class.java.getField(idColor).getInt(null)
                if (colorRes != -1) {
                    return "#" + Integer.toHexString(ContextCompat.getColor(Utils.application, colorRes))
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("Error: $e")
        }
        return "0"
    }

    private fun shouldUseMonetColors(): Boolean {
        if (mPrefs == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }
        if (mPrefs?.getBoolean("changecolor", false) != true) {
            return false
        }
        return "monet" == mPrefs?.getString("changecolor_mode", "manual")
    }

    private fun resolveMonetColor(resourceName: String): Int {
        val color = checkSystemColor("color_$resourceName")
        if (!isValidColor(color)) {
            return 0
        }
        return try {
            Color.parseColor(color)
        } catch (_: Exception) {
            0
        }
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth, drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @JvmStatic
    fun getDominantColor(bitmap: Bitmap): Int {
        val colorCountMap = HashMap<Int, Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 0) {
                    colorCountMap[color] = colorCountMap.getOrDefault(color, 0) + 1
                }
            }
        }
        return colorCountMap.entries.maxByOrNull { it.value }?.key ?: Color.BLACK
    }

    @JvmStatic
    fun colorDistance(color1: Int, color2: Int): Double {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        return Math.sqrt(
            (Math.pow((r1 - r2).toDouble(), 2.0)
                    + Math.pow((g1 - g2).toDouble(), 2.0)
                    + Math.pow((b1 - b2).toDouble(), 2.0))
        )
    }

    @JvmStatic
    fun replaceColor(bitmap: Bitmap, oldColor: Int, newColor: Int, threshold: Double): Bitmap {
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        for (y in 0 until newBitmap.height) {
            for (x in 0 until newBitmap.width) {
                val currentColor = newBitmap.getPixel(x, y)
                if (colorDistance(currentColor, oldColor) < threshold) {
                    newBitmap.setPixel(x, y, newColor)
                }
            }
        }
        return newBitmap
    }

    @JvmStatic
    fun resizeDrawable(icon: Drawable, width: Int, height: Int): Drawable {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        icon.setBounds(0, 0, canvas.width, canvas.height)
        icon.draw(canvas)
        return BitmapDrawable(Utils.application.resources, bitmap)
    }

    @JvmStatic
    fun getBackgroundColorFromMap(color: String): Int {
        val newcolor = IColors.backgroundColors.getOrDefault(color, color)
        return IColors.parseColor(newcolor)
    }
}
