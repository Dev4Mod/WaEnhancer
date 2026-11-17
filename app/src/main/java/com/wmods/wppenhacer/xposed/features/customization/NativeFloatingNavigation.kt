package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.ViewHookDispatcher
import com.wmods.wppenhacer.xposed.features.general.PropsHook
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.Method

class NativeFloatingNavigation(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private var indicatorColorSetter: Method? = null
    private var indicatorColorGetter: Method? = null

    override fun doHook() {
        if (!xprefs.getBoolean(PREF_KEY, false)) return
        if (xprefs.getBoolean(FLOATING_BOTTOM_BAR_KEY, false)) return
        PropsHook.booleans[FLOATING_NAVIGATION_CODE] = true

        val backgroundColor = xprefs.getInt(PREF_BACKGROUND_COLOR, 0)
        val selectionColor = xprefs.getInt(PREF_SELECTION_COLOR, 0)
        if (backgroundColor == 0 && selectionColor == 0) return

        val containerId = Utils.getID("nav_phoenix_rounded_background_container", "id")
        val navId = Utils.getID("bottom_nav", "id")
        ViewHookDispatcher.onAttachedToWindow(this) { view ->
            val id = view.id
            if (id <= 0) return@onAttachedToWindow
            if (id == containerId && backgroundColor != 0) {
                keepApplied(view) { applyBackground(it, backgroundColor) }
            } else if (id == navId && selectionColor != 0) {
                keepApplied(view) { applySelection(it, selectionColor) }
            }
        }
    }

    private fun keepApplied(view: View, apply: (View) -> Unit) {
        apply(view)
        if (ReflectionUtils.getAdditionalInstanceField(view, KEY_COLORED) != null) return
        ReflectionUtils.setAdditionalInstanceField(view, KEY_COLORED, true)
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> apply(v) }
    }

    private fun applyBackground(container: View, color: Int) {
        val background = container.background as? GradientDrawable ?: return
        if (background.color?.defaultColor != color) background.setColor(color)
    }

    private fun applySelection(nav: View, color: Int) {
        runCatching {
            val setter = indicatorColorSetter ?: nav.javaClass
                .getMethod("setItemActiveIndicatorColor", ColorStateList::class.java)
                .also { indicatorColorSetter = it }
            val getter = indicatorColorGetter ?: nav.javaClass
                .getMethod("getItemActiveIndicatorColor")
                .also { indicatorColorGetter = it }
            val current = getter.invoke(nav) as? ColorStateList
            if (current?.defaultColor != color) {
                setter.invoke(nav, ColorStateList.valueOf(color))
            }
        }.onFailure { logDebug("Unable to color the selected item", it) }
    }

    override fun getPluginName(): String = "Native Floating Navigation"

    companion object {
        const val PREF_KEY = "native_floating_navigation"
        const val PREF_BACKGROUND_COLOR = "native_floating_navigation_bg_color"
        const val PREF_SELECTION_COLOR = "native_floating_navigation_selection_color"
        private const val FLOATING_BOTTOM_BAR_KEY = "floating_bottom_bar"
        private const val FLOATING_NAVIGATION_CODE = 36795
        private const val KEY_COLORED = "wae_native_floating_colored"
    }
}
