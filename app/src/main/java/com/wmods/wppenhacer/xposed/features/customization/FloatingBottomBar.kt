package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.ViewHookDispatcher
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.WeakHashMap

class FloatingBottomBar(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val CORNER_RADIUS_DP = 24f
        private const val SIDE_MARGIN_DP = 12f
        private const val BOTTOM_MARGIN_DP = 16f
        private const val ELEVATION_DP = 4f
        private const val BACKGROUND_ALPHA = 0.90f
        private const val STROKE_ALPHA = 0.40f
        private const val FAB_GAP_DP = 8f
        private val FAB_RESOURCE_NAMES = arrayOf("fab", "fab_second", "extended_mini_fab")
    }

    private val processedBars = WeakHashMap<ViewGroup, Boolean>()
    private val setupAttempts = WeakHashMap<ViewGroup, Int>()
    private val baseBottomPadding = WeakHashMap<ViewGroup, Int>()
    private val paddingGuarded = WeakHashMap<ViewGroup, Boolean>()

    override fun doHook() {
        if (!xprefs.getBoolean("floating_bottom_bar", false)) return

        val bottomNavId = Utils.getID("bottom_nav", "id")
        if (bottomNavId <= 0) return
        val fabIds = FAB_RESOURCE_NAMES.mapNotNull { name ->
            Utils.getID(name, "id").takeIf { id -> id > 0 }
        }.toSet()

        ViewHookDispatcher.onAttachedToWindow(this) { view ->
            if (view.id == bottomNavId) {
                val bar = view as? ViewGroup ?: return@onAttachedToWindow
                scheduleSetup(bar)
                return@onAttachedToWindow
            }
            if (view.id in fabIds) {
                view.post { positionFabAboveCurrentBar(view, bottomNavId) }
            }
        }

        ViewHookDispatcher.onDetachedFromWindow(this) { view ->
            if (view.id != bottomNavId) return@onDetachedFromWindow
            val bar = view as? ViewGroup ?: return@onDetachedFromWindow
            setupAttempts.remove(bar)
            processedBars.remove(bar)
        }
    }

    private fun scheduleSetup(bar: ViewGroup) {
        if (processedBars.containsKey(bar)) {
            ensureBarOverlay(bar)
            return
        }

        bar.post {
            if (setupFloatingBar(bar)) {
                processedBars[bar] = true
                setupAttempts.remove(bar)
                return@post
            }
            retrySetup(bar)
        }
    }

    private fun retrySetup(bar: ViewGroup) {
        val attempt = setupAttempts[bar] ?: 0
        if (attempt >= 3) return
        setupAttempts[bar] = attempt + 1
        bar.postDelayed({
            if (processedBars.containsKey(bar)) return@postDelayed
            if (setupFloatingBar(bar)) {
                processedBars[bar] = true
                setupAttempts.remove(bar)
            } else {
                retrySetup(bar)
            }
        }, 100L)
    }

    private fun ensureBarOverlay(bar: ViewGroup) {
        val container = bar.parent as? ViewGroup ?: return
        if (container.parent !is FrameLayout) {
            bar.post { setupFloatingBar(bar) }
        }
    }

    private fun setupFloatingBar(bar: ViewGroup): Boolean {
        try {
            val container = bar.parent as? ViewGroup ?: return false
            val rootView = findRootView(bar) ?: return false
            if (container.parent === rootView) {
                updateOverlayLayout(rootView, container, bar)
                applyTransparentShadowStyle(container, bar)
                positionFabsAboveBar(rootView, container)
                return true
            }

            (container.parent as? ViewGroup)?.removeView(container)

            val rootParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = navigationBarInset(rootView) + Utils.dipToPixels(BOTTOM_MARGIN_DP)
            }
            rootView.addView(container, rootParams)

            applyTransparentShadowStyle(container, bar)
            positionFabsAboveBar(rootView, container)

            logDebug("FloatingBottomBar applied as overlay")
            return true
        } catch (e: Throwable) {
            log(e)
            return false
        }
    }

    private fun updateOverlayLayout(rootView: FrameLayout, container: ViewGroup, bar: ViewGroup) {
        val params = container.layoutParams as? FrameLayout.LayoutParams ?: return
        val bottomMargin = navigationBarInset(rootView) + Utils.dipToPixels(BOTTOM_MARGIN_DP)
        if (params.gravity != Gravity.BOTTOM
            || params.width != ViewGroup.LayoutParams.MATCH_PARENT
            || params.height != ViewGroup.LayoutParams.WRAP_CONTENT
            || params.bottomMargin != bottomMargin
        ) {
            params.gravity = Gravity.BOTTOM
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.bottomMargin = bottomMargin
            container.layoutParams = params
        }
        val barParams = bar.layoutParams
        if (barParams.width != ViewGroup.LayoutParams.MATCH_PARENT
            || barParams.height != ViewGroup.LayoutParams.WRAP_CONTENT
        ) {
            barParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            barParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            bar.layoutParams = barParams
        }
    }

    private fun navigationBarInset(view: View): Int {
        return ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?.bottom ?: 0
    }

    private fun findRootView(startView: View): FrameLayout? {
        var current: View? = startView
        var lastFrameLayout: FrameLayout? = null
        while (current != null) {
            if (current is FrameLayout) {
                lastFrameLayout = current
            }
            current = current.parent as? View
        }
        return lastFrameLayout
    }


    private fun applyTransparentShadowStyle(container: ViewGroup, bar: ViewGroup) {
        container.setBackgroundColor(Color.TRANSPARENT)

        val parent = container.parent as? ViewGroup
        parent?.clipChildren = false
        parent?.clipToPadding = false
        container.clipChildren = false
        container.clipToPadding = false

        val dividerId = Utils.getID("bottom_nav_divider", "id")
        if (dividerId > 0) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.id == dividerId) {
                    child.visibility = View.GONE
                    break
                }
            }
        }

        val barColor = resolveBarColor(bar)
        val transparentColor = (barColor and 0x00FFFFFF) or
                ((BACKGROUND_ALPHA * 255).toInt() shl 24)
        val strokeColor = if (isLightColor(barColor)) {
            Color.argb((STROKE_ALPHA * 255).toInt(), 0, 0, 0)
        } else {
            Color.argb((STROKE_ALPHA * 255).toInt(), 255, 255, 255)
        }
        val radiusDp =
            xprefs.getInt("floating_bottom_bar_radius", CORNER_RADIUS_DP.toInt()).toFloat()
        val radius = Utils.dipToPixels(radiusDp).toFloat()

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                radius, radius, radius, radius,
                radius, radius, radius, radius
            )
            setColor(transparentColor)
            setStroke(Utils.dipToPixels(1.5f), strokeColor)
        }
        bar.background = background
        bar.outlineProvider = ViewOutlineProvider.BACKGROUND
        bar.elevation = Utils.dipToPixels(ELEVATION_DP).toFloat()

        val sideMargin = Utils.dipToPixels(SIDE_MARGIN_DP)
        val params = bar.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null && (params.width != ViewGroup.LayoutParams.MATCH_PARENT
                    || params.height != ViewGroup.LayoutParams.WRAP_CONTENT
                    || params.leftMargin != sideMargin || params.topMargin != 0
                    || params.rightMargin != sideMargin || params.bottomMargin != 0)
        ) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.setMargins(sideMargin, 0, sideMargin, 0)
            bar.layoutParams = params
        }

        stripBottomInset(bar)
        installPaddingGuard(bar)
    }

    private fun stripBottomInset(bar: ViewGroup) {
        val basePadding = baseBottomPadding.getOrPut(bar) {
            (bar.paddingBottom - navigationBarInset(bar)).coerceAtLeast(0)
        }
        if (bar.paddingBottom != basePadding) {
            bar.setPaddingRelative(bar.paddingStart, bar.paddingTop, bar.paddingEnd, basePadding)
        }
    }

    // WDSBottomBar (Material BottomNavigationView) re-applies the navigation bar inset as
    // bottom padding on every insets dispatch (e.g. when switching tabs), which made the
    // floating bar grow taller. The bar already sits above the navigation bar via its margin,
    // so the bottom inset is ignored and the base padding is enforced on every layout.
    private fun installPaddingGuard(bar: ViewGroup) {
        if (paddingGuarded.put(bar, true) != null) return
        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            stripBottomInset(view as ViewGroup)
            insets
        }
        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            stripBottomInset(bar)
            val params = bar.layoutParams
            if (params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                bar.layoutParams = params
            }
        }
    }

    private fun positionFabsAboveBar(rootView: ViewGroup, container: ViewGroup) {
        val barHeight = container.height
        if (barHeight <= 0) {
            container.postDelayed({ positionFabsAboveBar(rootView, container) }, 100L)
            return
        }

        val offset = -(barHeight + Utils.dipToPixels(FAB_GAP_DP)).toFloat()
        for (name in FAB_RESOURCE_NAMES) {
            val id = Utils.getID(name, "id")
            if (id <= 0) continue
            rootView.findViewById<View>(id)?.let { fab ->
                fab.translationY = offset
                bringToFrontIfNeeded(fab)
            }
        }
    }

    private fun positionFabAboveCurrentBar(fab: View, bottomNavId: Int) {
        val bottomNav = fab.rootView.findViewById<View>(bottomNavId) ?: return
        val container = bottomNav.parent as? ViewGroup ?: return
        if (container.parent !is FrameLayout) return
        positionFab(fab, container)
    }

    private fun positionFab(fab: View, container: ViewGroup) {
        val barHeight = container.height
        if (barHeight <= 0) {
            container.postDelayed({ positionFab(fab, container) }, 100L)
            return
        }

        fab.translationY = -(barHeight + Utils.dipToPixels(FAB_GAP_DP)).toFloat()
        bringToFrontIfNeeded(fab)
    }

    private fun bringToFrontIfNeeded(view: View) {
        val parent = view.parent as? ViewGroup ?: return
        if (parent.indexOfChild(view) != parent.childCount - 1) view.bringToFront()
    }

    private fun resolveBarColor(bar: ViewGroup): Int {
        val currentBg = bar.background
        if (currentBg is ColorDrawable) {
            return currentBg.color
        }
        return try {
            val typedValue = TypedValue()
            bar.context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            typedValue.data
        } catch (_: Throwable) {
            0xFF121212.toInt()
        }
    }

    private fun isLightColor(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return (red * 299 + green * 587 + blue * 114) / 1000 > 180
    }

    override fun getPluginName(): String {
        return "Floating Bottom Bar"
    }
}
