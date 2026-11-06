package com.wmods.wppenhacer.xposed.features.customization

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.general.PropsHook
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.LinkedHashSet
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.max

/**
 * "Enable iOS Style" for the message selection (long press) UI.
 *
 * WhatsApp already ships an iOS-like message menu behind feature flag 2889 (the
 * "New context menu UI" switch). This feature turns that flag on on its own and,
 * on top of it, replaces the stock opaque panels with iOS-style frosted glass:
 *
 *  - the whole conversation behind the menu is blurred (live RenderEffect blur on
 *    Android 12+, or a downscaled + blurred snapshot of the decor view on older
 *    Android versions, so the module's minSdk 28 is still supported);
 *  - a dim scrim is added, like the iOS context menu backdrop;
 *  - the reaction tray becomes a translucent capsule and the drop-down menu
 *    becomes a translucent 14dp rounded card, both with a hairline border,
 *    elevation and a short spring-ish pop-in animation.
 *
 * Everything is applied to the real PopupWindow WhatsApp inflates, so no
 * replacement menu has to be built and all native actions keep working. All
 * lookups are defensive: if WhatsApp changes its layout, the feature simply
 * falls back to the closest matching card containers instead of crashing.
 */
class IosSelectionMenu(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        const val PREF_ENABLE = "enable_ios_style"
        const val PREF_BLUR = "ios_blur_intensity"
        const val PREF_DIM = "ios_backdrop_dim"

        /** WhatsApp's feature flag for the iOS-like message selection menu. */
        private const val NATIVE_IOS_MENU_PROP = 2889
        private const val BACKDROP_TAG = "wae_ios_selection_backdrop"
        private const val SHARP_TAG = "wae_ios_sharp_bubble"
        private const val MAX_SCAN_DEPTH = 7
        private val CARD_KEYWORDS = arrayOf(
            "reaction", "tray", "menu", "selection", "dropdown", "drop_down",
            "recyclerview", "context_menu", "actions"
        )
        private val BUBBLE_KEYWORDS = arrayOf(
            "bubble", "message_row", "conversation_row", "chat_row", "message_text"
        )
    }

    private data class BackdropState(
        val decor: ViewGroup,
        val overlay: View,
        val sharpBubble: View?,
        val sharpBitmap: Bitmap?,
        val usedRenderEffect: Boolean
    )

    private val backdrops =
        Collections.synchronizedMap(WeakHashMap<PopupWindow, BackdropState>())
    private val installedPopups =
        Collections.synchronizedMap(WeakHashMap<PopupWindow, Boolean>())
    private val styledCards =
        Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    private val blurPercent by lazy { xprefs.getInt(PREF_BLUR, 65).coerceIn(0, 100) }
    private val dimPercent by lazy { xprefs.getInt(PREF_DIM, 40).coerceIn(0, 100) }

    override fun doHook() {
        if (!xprefs.getBoolean(PREF_ENABLE, false)) return

        // The iOS toggle must always use WhatsApp's iOS-shaped menu, even when the
        // separate "New context menu UI" switch is off. Others.kt also ORs this
        // value, so the result does not depend on feature load order.
        PropsHook.booleans[NATIVE_IOS_MENU_PROP] = true

        val popupClass = runCatching {
            Unobfuscator.loadPopupWindowMessageClass(classLoader)
        }.onFailure { log(it) }.getOrNull()

        if (popupClass == null) {
            log("iOS Style: message selection PopupWindow class not found")
            return
        }

        popupClass.resolve().constructor { }.hookAll {
            after {
                val popup = instance as? PopupWindow ?: return@after
                installPopup(popup)
            }
        }
    }

    private fun installPopup(popup: PopupWindow) {
        if (installedPopups.containsKey(popup)) return
        val content = runCatching { popup.contentView }.getOrNull() ?: return
        installedPopups[popup] = true

        // Only remove a plain opaque COLOR scrim. WhatsApp draws the lifted
        // (sharp) selected message bubble into a bitmap background on some
        // builds - replacing that would make the highlighted chat disappear.
        runCatching {
            val popupBg = runCatching { popup.background }.getOrNull()
            val contentBg = content.background
            val hasSnapshot = popupBg is BitmapDrawable || contentBg is BitmapDrawable
            if (!hasSnapshot && isPlainColorMask(popupBg ?: contentBg)) {
                popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
        }.onFailure { logDebug(it) }

        val popupRef = WeakReference(popup)
        content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                val current = popupRef.get() ?: return
                // Do NOT touch the popup background here: WhatsApp paints the
                // highlighted message bubble into it after the constructor.
                val activity = findActivity(view.context)
                if (activity != null) showBackdrop(current, activity, view)
                view.post { styleCards(view) }
            }

            override fun onViewDetachedFromWindow(view: View) {
                popupRef.get()?.let { hideBackdrop(it) }
            }
        })

        // Fallback for builds where the content view is already attached when the
        // constructor hook runs, or where the attach callback is not delivered.
        content.post {
            val activity = findActivity(content.context)
            if (content.isAttachedToWindow && activity != null && !backdrops.containsKey(popup)) {
                showBackdrop(popup, activity, content)
            }
            styleCards(content)
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current != null) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return WppCore.getCurrentActivity()
    }

    // ------------------------------------------------------------------
    // Blurred backdrop behind the menu
    // ------------------------------------------------------------------

    /** All API 31+ calls live in these helpers, so no S-only type is resolved on older ROMs. */
    @SuppressLint("NewApi")
    private fun applyLiveBlur(decor: View) {
        val density = decor.resources.displayMetrics.density
        val radius = (blurPercent / 100f) * 24f * density
        decor.setRenderEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        )
    }

    @SuppressLint("NewApi")
    private fun restoreLiveBlur(decor: View) {
        decor.setRenderEffect(null)
    }

    private fun showBackdrop(popup: PopupWindow, activity: Activity, content: View?) {
        clearStaleBackdrops(popup)
        if (backdrops.containsKey(popup)) return
        val decor = activity.window?.decorView as? ViewGroup ?: return

        var drawable: Drawable = ColorDrawable(dimColor())
        var usedRenderEffect = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Live blur of the activity window. The menu lives in its own
            // PopupWindow, so it stays sharp while everything behind it blurs.
            try {
                applyLiveBlur(decor)
                usedRenderEffect = true
            } catch (t: Throwable) {
                logDebug(t)
            }
        }

        if (!usedRenderEffect) {
            // Pre-Android 12 fallback: draw the decor into a small bitmap, box
            // blur it and use it as the (upscaled, filtered) backdrop drawable.
            drawable = createSnapshotDrawable(decor) ?: ColorDrawable(dimColor())
        }

        val overlay = View(activity).apply {
            background = drawable
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            alpha = 0f
            tag = BACKDROP_TAG
        }

        try {
            decor.addView(overlay)
            // Keep the selected chat bubble SHARP above the blur. WhatsApp keeps
            // the lifted bubble in the activity window (or in a bitmap view we
            // must not repaint), so we snapshot it and draw the crisp copy last.
            val sharp = attachSharpBubble(popup, decor, content)
            backdrops[popup] = BackdropState(
                decor, overlay, sharp?.first, sharp?.second, usedRenderEffect
            )
            overlay.animate().alpha(1f).setDuration(180L).start()
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun hideBackdrop(popup: PopupWindow) {
        val state = backdrops.remove(popup) ?: return
        if (state.usedRenderEffect) {
            try {
                restoreLiveBlur(state.decor)
            } catch (t: Throwable) {
                logDebug(t)
            }
        }
        val overlay = state.overlay
        val sharp = state.sharpBubble
        state.sharpBitmap?.let { if (!it.isRecycled) it.recycle() }
        sharp?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay.animate().cancel()
        overlay.animate().alpha(0f).setDuration(150L).withEndAction {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            (overlay.background as? BackdropDrawable)?.recycle()
        }.start()
    }

    private fun clearStaleBackdrops(current: PopupWindow) {
        val stale = ArrayList<PopupWindow>()
        synchronized(backdrops) {
            for (key in backdrops.keys) {
                if (key !== current) stale.add(key)
            }
        }
        stale.forEach { hideBackdrop(it) }
    }

    private fun dimColor(): Int =
        Color.argb((dimPercent * 1.55f).toInt().coerceIn(0, 190), 0, 0, 0)

    // ------------------------------------------------------------------
    // Keep the selected (lifted) chat bubble sharp above the blur
    // ------------------------------------------------------------------

    private fun isBubbleLike(name: String): Boolean =
        BUBBLE_KEYWORDS.any { name.contains(it) }

    private fun attachSharpBubble(
        popup: PopupWindow,
        decor: ViewGroup,
        content: View?
    ): Pair<View, Bitmap>? {
        val bubble = findSelectedBubble(popup, decor, content) ?: return null
        return try {
            val w = bubble.width
            val h = bubble.height
            if (w <= 0 || h <= 0) return null
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bubble.draw(Canvas(bitmap))

            val loc = IntArray(2)
            val decorLoc = IntArray(2)
            bubble.getLocationInWindow(loc)
            decor.getLocationInWindow(decorLoc)

            val sharp = ImageView(decor.context).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_XY
                layoutParams = FrameLayout.LayoutParams(w, h).apply {
                    leftMargin = loc[0] - decorLoc[0]
                    topMargin = loc[1] - decorLoc[1]
                }
                isClickable = false
                isFocusable = false
                tag = SHARP_TAG
            }
            decor.addView(sharp)
            sharp to bitmap
        } catch (t: Throwable) {
            logDebug(t)
            null
        }
    }

    private fun findSelectedBubble(
        popup: PopupWindow,
        decor: View,
        content: View?
    ): View? {
        val minW = Utils.dipToPixels(90)
        val minH = Utils.dipToPixels(32)
        val maxH = Utils.dipToPixels(700)
        val candidates = LinkedHashSet<View>()

        collectViewFields(popup, decor, content, minW, minH, maxH, candidates)
        if (content != null) {
            collectViewFields(content, decor, content, minW, minH, maxH, candidates)
        }
        if (candidates.isEmpty()) {
            scanForBubbleCandidates(decor, decor, 0, minW, minH, maxH, candidates)
        }
        return candidates.maxByOrNull { bubbleScore(it) }
    }

    private fun collectViewFields(
        owner: Any,
        decor: View,
        content: View?,
        minW: Int,
        minH: Int,
        maxH: Int,
        out: MutableSet<View>
    ) {
        var cls: Class<*>? = owner.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                runCatching {
                    field.isAccessible = true
                    val raw = field.get(owner)
                    val view = when (raw) {
                        is View -> raw
                        is WeakReference<*> -> raw.get() as? View
                        else -> null
                    }
                    if (view != null && view !== content &&
                        isPlausibleBubble(view, decor, minW, minH, maxH)
                    ) {
                        out.add(view)
                    }
                }
            }
            cls = cls.superclass
        }
    }

    private fun scanForBubbleCandidates(
        view: View,
        decor: View,
        depth: Int,
        minW: Int,
        minH: Int,
        maxH: Int,
        out: MutableSet<View>
    ) {
        if (depth > 12) return
        if (isPlausibleBubble(view, decor, minW, minH, maxH)) out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                scanForBubbleCandidates(
                    view.getChildAt(i), decor, depth + 1, minW, minH, maxH, out
                )
            }
        }
    }

    private fun isPlausibleBubble(
        view: View,
        decor: View,
        minW: Int,
        minH: Int,
        maxH: Int
    ): Boolean {
        if (view === decor || view.visibility != View.VISIBLE) return false
        if (view.getTag() == BACKDROP_TAG || view.getTag() == SHARP_TAG) return false
        if (view.width < minW || view.height < minH || view.height > maxH) return false
        if (view.rootView !== decor && view.rootView !== decor.rootView) return false
        if (view.width >= decor.width * 0.95f && view.height >= decor.height * 0.80f) return false
        return true
    }

    private fun bubbleScore(view: View): Long {
        val name = viewName(view)
        val lifted = view.isSelected || view.isActivated || view.isPressed ||
                view.elevation > 0f
        return (if (lifted) 50_000_000L else 0L) +
                (if (isBubbleLike(name)) 20_000_000L else 0L) +
                view.width.toLong() * view.height.toLong()
    }

    private fun createSnapshotDrawable(decor: ViewGroup): Drawable? {
        val w = decor.width
        val h = decor.height
        if (w < 16 || h < 16) return null

        // Never capture one of our own backdrops (would recursively blur blurs).
        val hidden = ArrayList<View>()
        for (i in 0 until decor.childCount) {
            val child = decor.getChildAt(i)
            if (child.getTag() == BACKDROP_TAG && child.visibility == View.VISIBLE) {
                child.visibility = View.INVISIBLE
                hidden.add(child)
            }
        }

        return try {
            val scale = 6
            val bw = max(1, w / scale)
            val bh = max(1, h / scale)
            val snapshot = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(snapshot)
            canvas.scale(bw.toFloat() / w, bh.toFloat() / h)
            decor.draw(canvas)

            val radius = ((blurPercent / 100f) * 8f).toInt().coerceIn(1, 10)
            val blurred = blurBitmap(snapshot, radius, 3)
            val tint = if (isNight()) {
                Color.argb(18, 20, 30, 45)
            } else {
                Color.argb(12, 255, 255, 255)
            }
            BackdropDrawable(blurred, dimColor(), tint)
        } catch (t: Throwable) {
            logDebug(t)
            null
        } finally {
            hidden.forEach { it.visibility = View.VISIBLE }
        }
    }

    /** Separable box blur; three iterations approximate a Gaussian well enough at 1/6 scale. */
    private fun blurBitmap(source: Bitmap, radius: Int, iterations: Int): Bitmap {
        val w = source.width
        val h = source.height
        val current = IntArray(w * h)
        val temp = IntArray(w * h)
        source.getPixels(current, 0, w, 0, 0, w, h)
        repeat(iterations) {
            blurHorizontal(current, temp, w, h, radius)
            blurVertical(temp, current, w, h, radius)
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(current, 0, w, 0, 0, w, h)
        source.recycle()
        return result
    }

    private fun blurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val window = r * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var red = 0
            var green = 0
            var blue = 0
            for (k in -r..r) {
                val px = src[row + clamp(k, 0, w - 1)]
                red += (px shr 16) and 0xFF
                green += (px shr 8) and 0xFF
                blue += px and 0xFF
            }
            for (x in 0 until w) {
                val alpha = src[row + x] and 0xFF000000.toInt()
                dst[row + x] = alpha or
                        ((red / window) shl 16) or
                        ((green / window) shl 8) or
                        (blue / window)
                val old = src[row + clamp(x - r, 0, w - 1)]
                val next = src[row + clamp(x + r + 1, 0, w - 1)]
                red += ((next shr 16) and 0xFF) - ((old shr 16) and 0xFF)
                green += ((next shr 8) and 0xFF) - ((old shr 8) and 0xFF)
                blue += (next and 0xFF) - (old and 0xFF)
            }
        }
    }

    private fun blurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val window = r * 2 + 1
        for (x in 0 until w) {
            var red = 0
            var green = 0
            var blue = 0
            for (k in -r..r) {
                val px = src[clamp(k, 0, h - 1) * w + x]
                red += (px shr 16) and 0xFF
                green += (px shr 8) and 0xFF
                blue += px and 0xFF
            }
            for (y in 0 until h) {
                val index = y * w + x
                val alpha = src[index] and 0xFF000000.toInt()
                dst[index] = alpha or
                        ((red / window) shl 16) or
                        ((green / window) shl 8) or
                        (blue / window)
                val old = src[clamp(y - r, 0, h - 1) * w + x]
                val next = src[clamp(y + r + 1, 0, h - 1) * w + x]
                red += ((next shr 16) and 0xFF) - ((old shr 16) and 0xFF)
                green += ((next shr 8) and 0xFF) - ((old shr 8) and 0xFF)
                blue += (next and 0xFF) - (old and 0xFF)
            }
        }
    }

    private fun clamp(value: Int, min: Int, max: Int): Int =
        if (value < min) min else if (value > max) max else value

    private class BackdropDrawable(
        private val bitmap: Bitmap,
        private val dimColor: Int,
        private val tintColor: Int
    ) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty || bitmap.isRecycled) return
            canvas.drawBitmap(bitmap, null, RectF(b), paint)
            canvas.drawColor(dimColor)
            canvas.drawColor(tintColor)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        fun recycle() {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    // ------------------------------------------------------------------
    // iOS frosted glass cards inside the popup
    // ------------------------------------------------------------------

    private fun styleCards(root: View) {
        try {
            val metrics = root.resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels
            val night = isNight()

            // Only plain opaque COLOR scrims may be cleared. A BitmapDrawable
            // here is WhatsApp's own snapshot that contains the sharp selected
            // chat bubble over the blurred conversation - keep it intact so the
            // highlighted message stays visible (iOS 1:1).
            if (isPlainColorMask(root.background)) {
                root.background = ColorDrawable(Color.argb(24, 0, 0, 0))
            }
            clearFullScreenMasks(root, root, 0, screenW, screenH)

            val hosts = LinkedHashSet<View>()
            val trayId = Utils.getID("reactions_tray_layout", "id")
            if (trayId > 0) {
                root.findViewById<View>(trayId)?.let { hosts.add(expandHost(it, root, screenW, screenH)) }
            }
            collectHosts(root, root, 0, false, screenW, screenH, hosts)

            val topmost = hosts.filter { host ->
                hosts.none { other -> other !== host && isAncestor(other, host) }
            }

            for (host in topmost) {
                if (styledCards.containsKey(host)) continue
                styledCards[host] = true
                val name = viewName(host)
                val capsule = name.contains("reaction") || name.contains("tray") ||
                        (host.height in 1..Utils.dipToPixels(84) &&
                                ((host as? ViewGroup)?.childCount ?: 0) >= 4)
                applyGlass(host, capsule, night)
            }
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun clearFullScreenMasks(
        view: View,
        root: View,
        depth: Int,
        screenW: Int,
        screenH: Int
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        if (view !== root && isFullScreen(view, screenW, screenH) &&
            isPlainColorMask(view.background)
        ) {
            view.background = ColorDrawable(Color.argb(24, 0, 0, 0))
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                clearFullScreenMasks(view.getChildAt(i), root, depth + 1, screenW, screenH)
            }
        }
    }

    private fun collectHosts(
        view: View,
        root: View,
        depth: Int,
        insideStyled: Boolean,
        screenW: Int,
        screenH: Int,
        out: MutableSet<View>
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        var chosen = false
        if (!insideStyled && view !== root && view is ViewGroup &&
            !isFullScreen(view, screenW, screenH) && !isSnapshotBackground(view)
        ) {
            val name = viewName(view)
            val named = CARD_KEYWORDS.any { name.contains(it) }
            if (!isBubbleLike(name) &&
                ((named && view.childCount >= 1) ||
                        (hasSolidBackground(view) && view.childCount >= 2))
            ) {
                out.add(expandHost(view, root, screenW, screenH))
                chosen = true
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectHosts(
                    view.getChildAt(i), root, depth + 1,
                    insideStyled || chosen, screenW, screenH, out
                )
            }
        }
    }

    /** Walk up to the outermost ancestor that actually owns the panel background. */
    private fun expandHost(start: View, root: View, screenW: Int, screenH: Int): View {
        var best = start
        var parent = start.parent as? View
        while (parent != null && parent !== root && !isFullScreen(parent, screenW, screenH)) {
            if (isSnapshotBackground(parent) || isBubbleLike(viewName(parent))) break
            if (hasSolidBackground(parent)) best = parent
            parent = parent.parent as? View
        }
        return best
    }

    private fun isAncestor(ancestor: View, view: View): Boolean {
        var parent = view.parent as? View
        while (parent != null) {
            if (parent === ancestor) return true
            parent = parent.parent as? View
        }
        return false
    }

    private fun isFullScreen(view: View, screenW: Int, screenH: Int): Boolean =
        view.width >= screenW * 0.92f && view.height >= screenH * 0.80f

    private fun hasSolidBackground(view: View): Boolean {
        val background = view.background ?: return false
        if (background is ColorDrawable) return Color.alpha(background.color) >= 180
        return background is GradientDrawable
    }

    /** A plain opaque color scrim that is safe to replace. */
    private fun isPlainColorMask(background: Drawable?): Boolean =
        background is ColorDrawable && Color.alpha(background.color) >= 180

    /** WhatsApp's lifted-message snapshot - never repaint or restyle this view. */
    private fun isSnapshotBackground(view: View): Boolean =
        view.background is BitmapDrawable

    private fun viewName(view: View): String {
        val name = StringBuilder(view.javaClass.name)
        if (view.id != View.NO_ID) {
            runCatching {
                name.append(' ').append(view.resources.getResourceEntryName(view.id))
            }
        }
        return name.toString().lowercase(Locale.ROOT)
    }

    private fun applyGlass(view: View, capsule: Boolean, night: Boolean) {
        try {
            if (isSnapshotBackground(view) || isBubbleLike(viewName(view))) return
            val alpha = if (night) 0xB2 else 0xC6
            val base = if (night) {
                Color.argb(alpha, 44, 46, 50)
            } else {
                Color.argb(alpha, 250, 250, 252)
            }
            val stroke = if (night) {
                Color.argb(52, 255, 255, 255)
            } else {
                Color.argb(28, 0, 0, 0)
            }
            val radius = if (capsule) {
                max(view.height / 2f, Utils.dipToPixels(22f).toFloat())
            } else {
                Utils.dipToPixels(14f).toFloat()
            }

            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(base)
                setStroke(Utils.dipToPixels(1f), stroke)
            }
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
            view.clipToOutline = true
            view.elevation = Utils.dipToPixels(6f).toFloat()
            (view.parent as? ViewGroup)?.clipChildren = false

            // A few WhatsApp builds paint an opaque color on every menu row /
            // reaction slot. Those would hide the frosted glass, so drop only
            // plain solid fills and keep ripples/rounded state drawables intact.
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    val childBg = child.background
                    if (childBg is ColorDrawable && Color.alpha(childBg.color) >= 180) {
                        child.background = ColorDrawable(Color.TRANSPARENT)
                    }
                }
            }

            // Short, slightly springy pop-in, like UIMenuController on iOS.
            view.alpha = 0.72f
            view.scaleX = 0.94f
            view.scaleY = 0.94f
            view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(210L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun isNight(): Boolean = runCatching { DesignUtils.isNightMode() }.getOrDefault(true)

    override fun getPluginName(): String = "IosSelectionMenu"
}
