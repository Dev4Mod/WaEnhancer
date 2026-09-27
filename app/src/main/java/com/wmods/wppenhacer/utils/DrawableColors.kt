package com.wmods.wppenhacer.utils

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.NinePatch
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.LevelListDrawable
import android.graphics.drawable.NinePatchDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.TransitionDrawable
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.HashMap

object DrawableColors {
    private val ninePatchColors = HashMap<Bitmap, Int>()
    private var materialShapeDrawableClass: Class<*>? = null

    @JvmStatic
    fun replaceColor(drawable: Drawable?, colors: HashMap<String, String>) {
        when (drawable) {
            null -> return
            is StateListDrawable -> {
                val count = StateListDrawableCompact.getStateCount(drawable)
                for (index in 0 until count) {
                    StateListDrawableCompact.getStateDrawable(drawable, index)?.let { replaceColor(it, colors) }
                }
            }
            is GradientDrawable -> {
                drawable.colors?.let { gradientColors ->
                    for (index in gradientColors.indices) {
                        val originalColor = gradientColors[index]
                        val newColor = IColors.getFromIntColor(originalColor, colors)
                        if (originalColor != newColor) gradientColors[index] = newColor
                    }
                    drawable.colors = gradientColors
                }
            }
            is DrawableWrapper -> replaceColor(drawable.drawable, colors)
            is NinePatchDrawable -> {
                val color = getNinePatchDrawableColor(drawable)
                val newColor = IColors.getFromIntColor(color, colors)
                if (color != newColor) drawable.setTintList(ColorStateList.valueOf(newColor))
            }
            is ColorDrawable -> {
                val color = getColorDrawableColor(drawable)
                val newColor = IColors.getFromIntColor(color, colors)
                if (newColor != color) drawable.color = newColor
            }
            is ShapeDrawable -> {
                val color = getShapeDrawableColor(drawable)
                val newColor = IColors.getFromIntColor(color, colors)
                if (color != newColor) drawable.paint.color = newColor
            }
            is LevelListDrawable -> {
                val count = XposedHelpers.callMethod(drawable, "getNumberOfLevels") as Int
                for (index in 0 until count) {
                    val child = XposedHelpers.callMethod(drawable, "getDrawable", index) as? Drawable
                    if (child != null) replaceColor(child, colors)
                }
            }
            is TransitionDrawable -> {
                for (index in 0 until drawable.numberOfLayers) {
                    drawable.getDrawable(index)?.let { replaceColor(it, colors) }
                }
            }
            is LayerDrawable -> {
                val state = drawable.constantState!!
                val children = XposedHelpers.getObjectField(state, "mChildren") as Array<*>
                children.forEach { childState ->
                    if (childState != null) {
                        val child = XposedHelpers.getObjectField(childState, "mDrawable") as? Drawable
                        replaceColor(child, colors)
                    }
                }
            }
            is DrawableContainer -> {
                val state = drawable.constantState!!
                val children = XposedHelpers.getObjectField(state, "mDrawables") as Array<Drawable?>
                children.forEach { replaceColor(it, colors) }
            }
            else -> replaceMaterialShapeDrawable(drawable, colors)
        }
    }

    private fun replaceMaterialShapeDrawable(drawable: Drawable, colors: HashMap<String, String>) {
        val shapeClass = getMaterialShapeDrawable() ?: return
        if (!shapeClass.isInstance(drawable)) return

        val state = XposedHelpers.callMethod(drawable, "getConstantState") as Drawable.ConstantState
        val colorFields = ReflectionUtils.findAllFieldsUsingFilter(shapeClass) { field ->
            field.type == ColorStateList::class.java
        }
        colorFields.forEach { field ->
            val stateList = ReflectionUtils.getObjectField(field, state) as? ColorStateList ?: return@forEach
            val color = stateList.defaultColor
            val newColor = IColors.getFromIntColor(color, colors)
            if (color != newColor) {
                ReflectionUtils.setObjectField(field, state, ColorStateList.valueOf(newColor))
            }
        }

        val paintFields = ReflectionUtils.getFieldsByType(shapeClass, Paint::class.java)
        paintFields.forEach { field ->
            val paint = ReflectionUtils.getObjectField(field, drawable) as? Paint ?: return@forEach
            val color = paint.color
            val newColor = IColors.getFromIntColor(color, colors)
            if (color != newColor) paint.color = newColor
        }
    }

    private fun getMaterialShapeDrawable(): Class<*>? {
        if (materialShapeDrawableClass == null) {
            materialShapeDrawableClass = try {
                Unobfuscator.loadMaterialShapeDrawableClass(Utils.application.classLoader)
            } catch (_: Exception) {
                return null
            }
        }
        return materialShapeDrawableClass
    }

    @JvmStatic
    fun getColor(drawable: Drawable?): Int = when (drawable) {
        null -> 0
        is ColorDrawable -> getColorDrawableColor(drawable)
        is ShapeDrawable -> getShapeDrawableColor(drawable)
        is RippleDrawable -> getRippleDrawableColor(drawable)
        is NinePatchDrawable -> getNinePatchDrawableColor(drawable)
        is InsetDrawable -> getInsetDrawableColor(drawable)
        else -> 0
    }

    private fun getInsetDrawableColor(drawable: InsetDrawable): Int {
        val inner = XposedHelpers.getObjectField(drawable, "mDrawable") as? Drawable
        return getColor(inner)
    }

    @JvmStatic
    fun getNinePatchDrawableColor(drawable: NinePatchDrawable): Int {
        val state = drawable.constantState!!
        val ninePatch = XposedHelpers.getObjectField(state, "mNinePatch") as NinePatch
        val bitmap = ninePatch.bitmap
        ninePatchColors[bitmap]?.let { return it }

        val colorCounts = HashMap<Int, Int>()
        var mostFrequentColor = 0
        var maximumCount = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val color = bitmap.getPixel(x, y)
                val count = (colorCounts[color] ?: 0) + 1
                colorCounts[color] = count
                if (count > maximumCount) {
                    mostFrequentColor = color
                    maximumCount = count
                }
            }
        }
        ninePatchColors[bitmap] = mostFrequentColor
        return mostFrequentColor
    }

    private fun getRippleDrawableColor(drawable: RippleDrawable): Int {
        val state = drawable.constantState
        return try {
            val color = XposedHelpers.getObjectField(state, "mColor") as ColorStateList
            color.defaultColor
        } catch (exception: IllegalArgumentException) {
            XposedBridge.log(exception)
            0
        }
    }

    @JvmStatic
    fun getColorDrawableColor(drawable: ColorDrawable): Int = drawable.color

    @JvmStatic
    fun getShapeDrawableColor(drawable: ShapeDrawable): Int = drawable.paint.color
}
