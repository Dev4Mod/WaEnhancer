package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.graphics.toColorInt
import com.wmods.wppenhacer.utils.DrawableColors
import com.wmods.wppenhacer.utils.IColors
import com.wmods.wppenhacer.utils.StateListDrawableCompact
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils

internal object ChatInfoTheme {
    private val activityNames = setOf(
        "com.whatsapp.chatinfo.ContactInfoActivity",
        "com.whatsapp.chatinfo.group.GroupChatInfoActivity"
    )

    private data class Style(
        val backgrounds: HashMap<String, String>,
        val surface: ColorStateList,
        val thumb: ColorStateList,
        val track: ColorStateList
    )

    private val styles = HashMap<Boolean, Style>()

    fun isChatInfo(context: Context): Boolean {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current.javaClass.name in activityNames
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return false
    }

    fun applyTree(view: View) {
        // The activity has already been checked by the lifecycle listener.
        // DecorView uses a DecorContext rather than the Activity's context.
        applyStyle(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyTree(view.getChildAt(index))
        }
    }

    fun apply(view: View) {
        if (!isChatInfoView(view)) return
        applyStyle(view)
    }

    fun isChatInfoView(view: View): Boolean {
        if (isChatInfo(view.context)) return true
        val activity = WppCore.getCurrentActivity() ?: return false
        return isChatInfo(activity) && view.rootView === activity.window.decorView
    }

    fun cardContentColor(view: View): Int? {
        if (!isChatInfoView(view)) return null
        return style()?.surface?.defaultColor
    }

    fun replaceSurfaceColor(color: Int): Int {
        val style = style() ?: return color
        return IColors.getFromIntColor(color, style.backgrounds)
    }

    private fun style(): Style? {
        val nightMode = DesignUtils.isNightMode()
        return styles[nightMode] ?: run {
            val palette = ChatInfoPalette.resolve(nightMode) { name ->
                val color = DesignUtils.checkSystemColor(name)
                if (DesignUtils.isValidColor(color)) color.toColorInt() else 0
            } ?: return null
            val states = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            )
            Style(
                hashMapOf(IColors.toString(DesignUtils.getPrimarySurfaceColor()) to IColors.toString(palette.background)),
                ColorStateList.valueOf(palette.background),
                ColorStateList(states, intArrayOf(palette.thumb(false, false), palette.thumb(true, true), palette.thumb(true, false))),
                ColorStateList(states, intArrayOf(palette.track(false, false), palette.track(true, true), palette.track(true, false)))
            ).also { styles[nightMode] = it }
        }
    }

    private fun applyStyle(view: View) {
        val style = style() ?: return
        view.background?.let { background ->
            // Resource drawables can share constant state with other screens.
            val local = background.constantState?.newDrawable(view.resources)?.mutate() ?: background.mutate()
            DrawableColors.replaceColor(local, style.backgrounds)
            replaceSolidFills(local, style.backgrounds)
            view.background = local
        }
        view.backgroundTintList?.let {
            view.backgroundTintList = replaceSurfaceColors(it, style.backgrounds)
        }
        if (view is CompoundButton) {
            // Both platform Switch and WhatsApp's SwitchCompat subclasses expose
            // these setters. ColorStateList preserves checked and disabled states.
            for ((name, tint) in listOf(
                "setThumbTintList" to style.thumb,
                "setTrackTintList" to style.track,
                // WDSSwitch draws its outline as a separate track decoration.
                "setTrackDecorationTintList" to style.thumb
            )) {
                view.javaClass.methods.firstOrNull {
                    it.name == name && it.parameterTypes.contentEquals(arrayOf(ColorStateList::class.java))
                }?.invoke(view, tint)
            }
        }
    }

    private fun replaceSolidFills(drawable: Drawable, replacements: HashMap<String, String>) {
        when (drawable) {
            is GradientDrawable -> drawable.color?.let {
                drawable.setColor(replaceSurfaceColors(it, replacements))
            }
            is DrawableWrapper -> drawable.drawable?.let { replaceSolidFills(it, replacements) }
            is LayerDrawable -> for (index in 0 until drawable.numberOfLayers) {
                replaceSolidFills(drawable.getDrawable(index), replacements)
            }
            is StateListDrawable -> for (index in 0 until StateListDrawableCompact.getStateCount(drawable)) {
                StateListDrawableCompact.getStateDrawable(drawable, index)?.let {
                    replaceSolidFills(it, replacements)
                }
            }
        }
    }

    private fun replaceSurfaceColors(colors: ColorStateList, replacements: HashMap<String, String>): ColorStateList {
        val originals = ReflectionUtils.getObjectField(colors, "mColors") as IntArray
        val mapped = originals.map { IColors.getFromIntColor(it, replacements) }.toIntArray()
        if (originals.contentEquals(mapped)) return colors
        @Suppress("UNCHECKED_CAST")
        val states = ReflectionUtils.getObjectField(colors, "mStateSpecs") as Array<IntArray>
        return ColorStateList(states, mapped)
    }
}
