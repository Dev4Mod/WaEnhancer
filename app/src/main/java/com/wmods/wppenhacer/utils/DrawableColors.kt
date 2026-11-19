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
import com.wmods.wppenhacer.xposed.utils.YukiLog

object DrawableColors {
    private val ninePatchColors = HashMap<Bitmap, Int>()
    private var materialShapeDrawableClass: Class<*>? = null
    private var materialShapeDrawableLookupFailed = false
    private val materialColorStateListFields by lazy {
        val shapeClass = getMaterialShapeDrawable() ?: return@lazy emptyList()
        ReflectionUtils.findAllFieldsUsingFilter(shapeClass) { field ->
            field.type == ColorStateList::class.java
        }.toList()
    }
    private val materialPaintFields by lazy {
        val shapeClass = getMaterialShapeDrawable() ?: return@lazy emptyList()
        ReflectionUtils.getFieldsByType(shapeClass, Paint::class.java)
    }

    @JvmStatic
    @JvmOverloads
    fun replaceColor(drawable: Drawable?, colors: HashMap<String, String>, mutate: Boolean = false) {
        if (drawable == null) return
        if (mutate) drawable.mutate()
        when (drawable) {
            is StateListDrawable -> {
                val count = StateListDrawableCompact.getStateCount(drawable)
                for (index in 0 until count) {
                    StateListDrawableCompact.getStateDrawable(drawable, index)
                        ?.let { replaceColor(it, colors, mutate) }
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

            is DrawableWrapper -> replaceColor(drawable.drawable, colors, mutate)
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
                val count = ReflectionUtils.callMethod(drawable, "getNumberOfLevels") as Int
                for (index in 0 until count) {
                    val child =
                        ReflectionUtils.callMethod(drawable, "getDrawable", index) as? Drawable
                    if (child != null) replaceColor(child, colors, mutate)
                }
            }

            is TransitionDrawable -> {
                for (index in 0 until drawable.numberOfLayers) {
                    drawable.getDrawable(index)?.let { replaceColor(it, colors, mutate) }
                }
            }

            is LayerDrawable -> {
                val state = drawable.constantState!!
                val children = ReflectionUtils.getObjectField(state, "mChildren") as Array<*>
                children.forEach { childState ->
                    if (childState != null) {
                        val child =
                            ReflectionUtils.getObjectField(childState, "mDrawable") as? Drawable
                        replaceColor(child, colors, mutate)
                    }
                }
            }

            is DrawableContainer -> {
                val state = drawable.constantState!!
                val children =
                    ReflectionUtils.getObjectField(state, "mDrawables") as Array<Drawable?>
                children.forEach { replaceColor(it, colors, mutate) }
            }

            else -> replaceMaterialShapeDrawable(drawable, colors)
        }
    }

    private fun replaceMaterialShapeDrawable(drawable: Drawable, colors: HashMap<String, String>) {
        val shapeClass = getMaterialShapeDrawable() ?: return
        if (!shapeClass.isInstance(drawable)) return

        val state =
            ReflectionUtils.callMethod(drawable, "getConstantState") as Drawable.ConstantState
        materialColorStateListFields.forEach { field ->
            val stateList =
                ReflectionUtils.getObjectField(field, state) as? ColorStateList ?: return@forEach
            val color = stateList.defaultColor
            val newColor = IColors.getFromIntColor(color, colors)
            if (color != newColor) {
                ReflectionUtils.setObjectField(field, state, ColorStateList.valueOf(newColor))
            }
        }

        materialPaintFields.forEach { field ->
            val paint = ReflectionUtils.getObjectField(field, drawable) as? Paint ?: return@forEach
            val color = paint.color
            val newColor = IColors.getFromIntColor(color, colors)
            if (color != newColor) paint.color = newColor
        }
    }

    private fun getMaterialShapeDrawable(): Class<*>? {
        if (materialShapeDrawableClass == null && !materialShapeDrawableLookupFailed) {
            materialShapeDrawableClass = try {
                Unobfuscator.loadMaterialShapeDrawableClass(Utils.application.classLoader)
            } catch (_: Exception) {
                materialShapeDrawableLookupFailed = true
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
        val inner = ReflectionUtils.getObjectField(drawable, "mDrawable") as? Drawable
        return getColor(inner)
    }

    @JvmStatic
    fun getNinePatchDrawableColor(drawable: NinePatchDrawable): Int {
        val state = drawable.constantState!!
        val ninePatch = ReflectionUtils.getObjectField(state, "mNinePatch") as NinePatch
        val bitmap = ninePatch.bitmap
        ninePatchColors[bitmap]?.let { return it }

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var capacity = 16
        while (capacity < pixels.size * 2) capacity = capacity shl 1
        val mask = capacity - 1
        val keys = IntArray(capacity)
        val counts = IntArray(capacity)
        var mostFrequentColor = 0
        var maximumCount = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = pixels[y * width + x]
                var hash = color * -0x61c88647
                hash = hash xor (hash ushr 16)
                var index = hash and mask
                while (counts[index] != 0 && keys[index] != color) {
                    index = (index + 1) and mask
                }
                keys[index] = color
                val count = counts[index] + 1
                counts[index] = count
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
            val color = ReflectionUtils.getObjectField(state, "mColor") as ColorStateList
            color.defaultColor
        } catch (exception: IllegalArgumentException) {
            YukiLog.log(exception)
            0
        }
    }

    @JvmStatic
    fun getColorDrawableColor(drawable: ColorDrawable): Int = drawable.color

    @JvmStatic
    fun getShapeDrawableColor(drawable: ShapeDrawable): Int = drawable.paint.color
}
