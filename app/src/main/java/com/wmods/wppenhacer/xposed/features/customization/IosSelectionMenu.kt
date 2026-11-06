package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
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
 *  - the whole conversation behind the menu is blurred using a downscaled +
 *    box-blurred snapshot of the decor view (works from minSdk 28). We
 *    deliberately do NOT use View.setRenderEffect on the decor: that would also
 *    blur any child overlay, including the sharp lifted-bubble copy;
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
        private val ANCHOR_FIELD_HINTS = arrayOf(
            "anchor", "message", "bubble", "row", "selected", "source", "conversation"
        )

        /** iOS scales the lifted bubble up slightly when the menu opens. */
        private const val LIFT_SCALE = 1.07f
        private const val STACK_GAP_DP = 10
        private const val STACK_MARGIN_DP = 16

        /** A full-width saturated green fill is WA's selected-row highlight. */
        private fun isGreenTint(drawable: Drawable?): Boolean {
            val color = (drawable as? ColorDrawable)?.color ?: return false
            if (Color.alpha(color) < 40) return false
            return Color.green(color) > Color.red(color) + 12 &&
                    Color.green(color) > Color.blue(color) + 12
        }
    }

    private data class BackdropState(
        val decor: ViewGroup,
        val overlay: View,
        val sharpBubble: View?,
        val sharpBitmap: Bitmap?
    )

    private data class StackHosts(
        val tray: View?,
        val menu: View?,
        val root: View?,
        val extras: List<View>,
        val hidden: List<View>
    )

    private val stackHosts =
        Collections.synchronizedMap(WeakHashMap<PopupWindow, StackHosts>())

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

        // NEVER touch the PopupWindow background. WhatsApp may keep the lifted
        // (sharp) selected bubble in that drawable; replacing it at any point
        // makes the highlighted chat disappear entirely.
        val popupRef = WeakReference(popup)
        content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                val current = popupRef.get() ?: return
                // Do NOT touch the popup background here: WhatsApp paints the
                // highlighted message bubble into it after the constructor.
                val activity = findActivity(view.context)
                if (activity != null) showBackdrop(current, activity, view)
                view.post { styleCards(view, current) }
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
            styleCards(content, popup)
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

    private fun showBackdrop(popup: PopupWindow, activity: Activity, content: View?) {
        clearStaleBackdrops(popup)
        if (backdrops.containsKey(popup)) return
        val decor = activity.window?.decorView as? ViewGroup ?: return

        // Snapshot-blur ONLY. A RenderEffect attached to the decor would blur
        // every child of that view - including the sharp bubble copy we add
        // below - which is why the selected chat used to stay blurred.
        val drawable: Drawable = createSnapshotDrawable(decor)
            ?: ColorDrawable(dimColor())

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
            // Draw a crisp copy of the selected chat bubble ON TOP of the blur.
            // If WhatsApp also draws its own lifted bubble in the popup window,
            // that window sits above this one, so no visible duplicate appears.
            val sharp = attachSharpBubble(popup, decor, content)
            backdrops[popup] = BackdropState(
                decor, overlay, sharp?.first, sharp?.second
            )
            overlay.animate().alpha(1f).setDuration(180L).start()
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun hideBackdrop(popup: PopupWindow) {
        val state = backdrops.remove(popup) ?: return
        stackHosts.remove(popup)
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

    // Plain dark scrim, like iOS: keep the blurred conversation's REAL colours
    // (teal/green bubbles and all) and only darken it. No desaturation / colour
    // filter anywhere - that washed the whole chat out.
    private fun dimColor(): Int =
        Color.argb((dimPercent * 1.35f).toInt().coerceIn(0, 170), 0, 0, 0)


    // ------------------------------------------------------------------
    // Keep the selected (lifted) chat bubble sharp above the blur
    // ------------------------------------------------------------------

    private fun isBubbleLike(name: String): Boolean =
        BUBBLE_KEYWORDS.any { name.contains(it) }

    /**
     * Draw a CLEAN iOS bubble. We never copy WhatsApp's row drawable: on many
     * builds the selected row is highlighted with a solid green GradientDrawable
     * / StateListDrawable, and drawing that verbatim produced the ugly
     * full-width green strip. Instead only the leaf content views (message text,
     * timestamp, emoji, media) are painted on our own rounded bubble.
     */
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

            val origin = IntArray(2)
            bubble.getLocationOnScreen(origin)
            val leaves = ArrayList<Pair<View, Rect>>()
            collectBubbleLeaves(bubble, bubble, 0, origin, leaves)
            if (leaves.isEmpty()) return null

            val box = Rect(w, h, 0, 0)
            for ((_, rect) in leaves) box.union(rect)
            if (box.right <= box.left || box.bottom <= box.top) {
                box.set(0, 0, w, h)
            }
            val padX = Utils.dipToPixels(14)
            val padY = Utils.dipToPixels(9)
            box.left = (box.left - padX).coerceIn(0, w)
            box.top = (box.top - padY).coerceIn(0, h)
            box.right = (box.right + padX).coerceIn(0, w)
            box.bottom = (box.bottom + padY).coerceIn(0, h)
            val cw = box.width()
            val ch = box.height()
            if (cw <= 0 || ch <= 0) return null

            val bitmap = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = Utils.dipToPixels(18f).toFloat()
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isNight()) Color.argb(255, 62, 64, 68)
                else Color.argb(255, 240, 240, 242)
            }
            val shape = Path().apply {
                addRoundRect(
                    0f, 0f, cw.toFloat(), ch.toFloat(),
                    radius, radius, Path.Direction.CW
                )
            }
            canvas.drawPath(shape, fill)
            // Clip strictly to the bubble: no full-width highlight strip can
            // ever leak out of the rounded shape, whatever WA's row drawable is.
            canvas.save()
            canvas.clipPath(shape)
            // Draw ONLY text / media leaves on our own clean bubble. The WA row
            // itself (and any highlight it paints in onDraw) is never copied.
            for ((leaf, rect) in leaves) {
                val bg = leaf.background
                try {
                    leaf.background = null
                    canvas.save()
                    canvas.translate(
                        (rect.left - box.left).toFloat(),
                        (rect.top - box.top).toFloat()
                    )
                    leaf.draw(canvas)
                    canvas.restore()
                } finally {
                    leaf.background = bg
                }
            }
            canvas.restore()

            val loc = IntArray(2)
            val decorLoc = IntArray(2)
            bubble.getLocationInWindow(loc)
            decor.getLocationInWindow(decorLoc)

            val sharp = ImageView(decor.context).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_XY
                layoutParams = FrameLayout.LayoutParams(cw, ch).apply {
                    leftMargin = loc[0] - decorLoc[0] + box.left
                    topMargin = loc[1] - decorLoc[1] + box.top
                }
                isClickable = false
                isFocusable = false
                elevation = Utils.dipToPixels(24f).toFloat()
                // iOS lifts the selected bubble a touch. Start visible so a
                // skipped animation can never leave the chat invisible.
                scaleX = 1f
                scaleY = 1f
                alpha = 0.92f
                outlineProvider = ViewOutlineProvider.BOUNDS
                tag = SHARP_TAG
            }
            decor.addView(sharp)
            sharp.animate().alpha(1f).scaleX(LIFT_SCALE).scaleY(LIFT_SCALE)
                .setDuration(210L).setInterpolator(DecelerateInterpolator(1.5f)).start()
            sharp to bitmap
        } catch (t: Throwable) {
            logDebug(t)
            null
        }
    }

    /** Collect visible leaf views (text / emoji / media) in bubble coordinates. */
    private fun collectBubbleLeaves(
        root: View,
        view: View,
        depth: Int,
        origin: IntArray,
        out: MutableList<Pair<View, Rect>>
    ) {
        if (depth > 10 || view.visibility != View.VISIBLE) return
        val tag = view.getTag()
        if (tag == BACKDROP_TAG || tag == SHARP_TAG) return
        val group = view as? ViewGroup
        if (group != null && group.childCount > 0) {
            for (i in 0 until group.childCount) {
                collectBubbleLeaves(root, group.getChildAt(i), depth + 1, origin, out)
            }
            return
        }
        if (view === root || view.width < 2 || view.height < 2) return
        // Only real message content defines the bubble bounds. A plain View
        // with a solid fill is WA's selection highlight and must not widen it.
        // Reaction pills / menu rows must NEVER be copied into the bubble -
        // that is what produced the second, faded pill and menu in the popup.
        var ancestor: View? = view
        while (ancestor != null && ancestor !== root) {
            val current = ancestor
            val name = viewName(current)
            if (arrayOf("reaction", "tray", "menu", "actions", "divider")
                    .any { name.contains(it) }
            ) return
            ancestor = current.parent as? View
        }
        val isText = view is TextView && !view.text.isNullOrEmpty()
        val isMedia = view is ImageView && view.drawable != null
        if (!isText && !isMedia) return
        val pos = IntArray(2)
        view.getLocationOnScreen(pos)
        val left = pos[0] - origin[0]
        val top = pos[1] - origin[1]
        val rect = Rect(left, top, left + view.width, top + view.height)
        rect.intersect(0, 0, root.width, root.height)
        if (rect.width() > 0 && rect.height() > 0) out.add(view to rect)
    }

    private fun findSelectedBubble(
        popup: PopupWindow,
        decor: View,
        content: View?
    ): View? {
        // Relaxed bounds: a short one-line bubble is ~28dp tall. Being too
        // strict here leaves NO candidate, which makes the chat disappear.
        val minW = Utils.dipToPixels(72)
        val minH = Utils.dipToPixels(26)
        val maxH = Utils.dipToPixels(420)
        val candidates = LinkedHashSet<View>()
        val strong = LinkedHashSet<View>()

        collectViewFields(popup, decor, content, minW, minH, maxH, candidates, strong)
        if (content != null) {
            collectViewFields(
                content, decor, content, minW, minH, maxH, candidates, strong
            )
        }
        scanForBubbleCandidates(decor, decor, 0, minW, minH, maxH, candidates)
        // WhatsApp keeps a direct reference to the selected message row in its
        // popup fields. Those anchors are far more reliable than guessing from
        // the whole view tree, so prefer them whenever present.
        val pool = if (strong.isNotEmpty()) strong else candidates
        if (pool.isEmpty()) return null
        return pool.maxByOrNull { bubbleScore(it, decor) }
    }

    private fun collectViewFields(
        owner: Any,
        decor: View,
        content: View?,
        minW: Int,
        minH: Int,
        maxH: Int,
        out: MutableSet<View>,
        strong: MutableSet<View>
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
                        val fieldName = field.name.lowercase(Locale.ROOT)
                        if (ANCHOR_FIELD_HINTS.any { fieldName.contains(it) }) {
                            strong.add(view)
                        }
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

    private fun bubbleScore(view: View, decor: View): Long {
        val name = viewName(view)
        val lifted = view.isSelected || view.isActivated || view.isPressed ||
                view.elevation > 0f
        val ratio = view.width.toFloat() / max(1, decor.width)
        var score = 0L
        if (isBubbleLike(name)) score += 80_000_000L
        if (containsText(view, 0)) score += 25_000_000L
        if (lifted) score += 15_000_000L
        if (view.background is GradientDrawable) score += 12_000_000L
        // Only whole-screen containers are disfavoured. Chat bubbles are often
        // quite wide on WhatsApp, so never reject a candidate for its width.
        if (ratio > 0.97f) score -= 20_000_000L
        return score + (view.width.toLong() * view.height.toLong()) / 40L
    }

    private fun containsText(view: View, depth: Int): Boolean {
        if (depth > 6) return false
        if (view is TextView && !view.text.isNullOrEmpty()) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsText(view.getChildAt(i), depth + 1)) return true
            }
        }
        return false
    }

    private fun createSnapshotDrawable(decor: ViewGroup): Drawable? {
        val w = decor.width
        val h = decor.height
        if (w < 16 || h < 16) return null

        // Never capture one of our own backdrops (would recursively blur blurs).
        val hidden = ArrayList<View>()
        for (i in 0 until decor.childCount) {
            val child = decor.getChildAt(i)
            val tag = child.getTag()
            if ((tag == BACKDROP_TAG || tag == SHARP_TAG) &&
                child.visibility == View.VISIBLE
            ) {
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
            // Full colour, exactly as the conversation looks - no colour matrix.
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

    private fun styleCards(root: View, popup: PopupWindow) {
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
            // Our fixed stack may move cards past their parent's bounds.
            if (root is ViewGroup) {
                root.clipChildren = false
                root.clipToPadding = false
            }
            val hosts = LinkedHashSet<View>()
            val trayId = Utils.getID("reactions_tray_layout", "id")
            if (trayId > 0) {
                root.findViewById<View>(trayId)?.let { hosts.add(expandHost(it, root, screenW, screenH)) }
            }
            collectHosts(root, root, 0, false, screenW, screenH, hosts)

            val topmost = hosts.filter { host ->
                hosts.none { other -> other !== host && isAncestor(other, host) }
            }

            // Classify ALL hosts every time (not only unstyled ones), then keep
            // exactly ONE reaction pill and ONE action menu. WhatsApp's lifted
            // snapshot sometimes contains a second, faded pill/menu; hiding the
            // duplicates is what makes the stack look neat and deterministic.
            val capsules = ArrayList<View>()
            val menus = ArrayList<View>()
            for (host in topmost) {
                val name = viewName(host)
                val capsule = name.contains("reaction") || name.contains("tray") ||
                        (host.height in 1..Utils.dipToPixels(110) &&
                                ((host as? ViewGroup)?.childCount ?: 0) >= 4)
                if (capsule) capsules.add(host) else menus.add(host)
            }
            val tray = capsules.maxByOrNull {
                ((it as? ViewGroup)?.childCount ?: 0) * 1000 + it.width
            }
            // A real iOS action card is narrow. WA's wide purple/green
            // selection frame is taller, so height alone would pick the wrong
            // host and leave two menus on screen.
            val narrow = menus.filter { it.width < screenW * 0.80f }
            val menu = (if (narrow.isNotEmpty()) narrow else menus)
                .maxByOrNull { it.height }
            val extras = topmost.filter { it !== tray && it !== menu }

            // Hide WA's own "highlighted copy" of the selected row (the green /
            // purple full-width strip) and any duplicated pill/menu snapshots.
            val hidden = hideHighlightCopies(
                root, root, 0, screenW, screenH, tray, menu, ArrayList()
            )
            extras.forEach { it.visibility = View.INVISIBLE }

            for (host in topmost) {
                if (styledCards.containsKey(host)) continue
                styledCards[host] = true
                applyGlass(host, host === tray, night)
            }
            if (tray != null || menu != null) {
                stackHosts[popup] = StackHosts(tray, menu, root, extras, hidden)
                scheduleFixedStack(root, popup)
            }
        } catch (t: Throwable) {
            log(t)
        }
    }

    /**
     * WhatsApp draws a second, highlighted copy of the selected message row
     * behind the iOS menu (solid green strip, purple outlined box, or a bitmap
     * snapshot that even contains a faded duplicate of the pill and menu).
     * Hide those copies entirely: our own clean rounded bubble is the only one
     * that should be visible.
     */
    private fun hideHighlightCopies(
        view: View,
        root: View,
        depth: Int,
        screenW: Int,
        screenH: Int,
        tray: View?,
        menu: View?,
        out: MutableList<View>
    ): List<View> {
        if (depth > MAX_SCAN_DEPTH + 3) return out
        if (view !== root && view !== tray && view !== menu &&
            !isAncestorOf(view, tray) && !isAncestorOf(view, menu) &&
            !isAncestorOf(tray, view) && !isAncestorOf(menu, view)
        ) {
            val name = viewName(view)
            val isCard = arrayOf("reaction", "tray", "menu", "actions")
                .any { name.contains(it) }
            val bg = view.background
            val wide = view.width >= screenW * 0.80f
            // WA's purple/green selection frame can wrap a tall media bubble,
            // so use a screen-relative bound instead of a fixed "short row".
            val rowLike = view.height in 1..(screenH * 0.62f).toInt()
            val outline = bg is GradientDrawable && !isCard
            val flat = isGreenTint(bg) ||
                    (bg is ColorDrawable && Color.alpha(bg.color) >= 40)
            // NEVER hide a BitmapDrawable view: that is WA's lifted sharp
            // bubble, and hiding it made the selected chat disappear. We only
            // hide flat colour/outline highlight strips and duplicate cards.
            if (bg !is BitmapDrawable && !isCard && wide && rowLike &&
                (flat || outline)
            ) {
                view.visibility = View.INVISIBLE
                out.add(view)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                hideHighlightCopies(
                    view.getChildAt(i), root, depth + 1,
                    screenW, screenH, tray, menu, out
                )
            }
        }
        return out
    }

    private fun isAncestorOf(ancestor: View?, view: View?): Boolean {
        if (view == null || ancestor == null) return false
        var parent: View? = view.parent as? View
        while (parent != null) {
            if (parent === ancestor) return true
            parent = parent.parent as? View
        }
        return false
    }

    // ------------------------------------------------------------------
    // Deterministic iOS stack: reaction pill ABOVE the bubble, menu BELOW it.
    // WhatsApp anchors these by the message position, which wanders and
    // overlaps. We place both from the bubble rect with fixed gaps instead.
    // ------------------------------------------------------------------

    private fun scheduleFixedStack(root: View, popup: PopupWindow) {
        root.post { applyFixedStack(popup) }
        root.postDelayed({ applyFixedStack(popup) }, 260L)
        root.postDelayed({ applyFixedStack(popup) }, 620L)
    }

    private fun applyFixedStack(popup: PopupWindow) {
        try {
            val state = backdrops[popup] ?: return
            val sharp = state.sharpBubble ?: return
            val hosts = stackHosts[popup] ?: return
            val lp = sharp.layoutParams as? FrameLayout.LayoutParams ?: return
            val decor = state.decor
            // WA repaints its highlighted copy / duplicates after layout, so
            // re-assert the hidden state on every placement pass.
            hosts.hidden.forEach { it.visibility = View.INVISIBLE }
            hosts.extras.forEach { it.visibility = View.INVISIBLE }

            val origin = IntArray(2)
            decor.getLocationOnScreen(origin)
            val bubbleLeft = origin[0] + lp.leftMargin
            val bubbleTop = origin[1] + lp.topMargin

            val gap = Utils.dipToPixels(STACK_GAP_DP)
            val margin = Utils.dipToPixels(STACK_MARGIN_DP)
            val screenW = decor.resources.displayMetrics.widthPixels
            val screenH = decor.resources.displayMetrics.heightPixels
            val tray = hosts.tray
            val menu = hosts.menu
            val trayH = tray?.height ?: 0
            val menuH = menu?.height ?: 0
            val widest = maxOf(lp.width, tray?.width ?: 0, menu?.width ?: 0)
            // One fixed left column for pill, bubble and menu - like iOS.
            val columnX = margin.coerceAtMost((screenW - widest - margin).coerceAtLeast(margin))

            val totalH = trayH + gap + lp.height + gap + menuH
            var stackTop = bubbleTop - gap - trayH
            stackTop = stackTop.coerceIn(
                margin,
                (screenH - totalH - margin).coerceAtLeast(margin)
            )
            var cursor = stackTop
            val trayY = cursor
            cursor += trayH + gap
            val bubbleY = cursor
            cursor += lp.height + gap
            val menuY = cursor

            // Our bubble is positioned from its known layout margins, so this
            // is an absolute assignment (safe to re-apply any number of times).
            sharp.translationX = (columnX - bubbleLeft).toFloat()
            sharp.translationY = (bubbleY - bubbleTop).toFloat()
            tray?.let { placeAt(it, columnX, trayY) }
            menu?.let { placeAt(it, columnX, menuY) }
        } catch (t: Throwable) {
            logDebug(t)
        }
    }

    private fun placeAt(view: View, x: Int, y: Int) {
        if (view.width <= 0 || view.height <= 0) return
        // left/top are layout coordinates, unaffected by translation/scale.
        // Using parent + left/top gives a STABLE baseline, so repeated calls
        // never drift (getLocationOnScreen would include our own translation).
        val loc = IntArray(2)
        val parent = view.parent as? View
        if (parent != null) {
            parent.getLocationOnScreen(loc)
            loc[0] += view.left
            loc[1] += view.top
        } else {
            view.getLocationOnScreen(loc)
        }
        view.translationX = (x - loc[0]).toFloat()
        view.translationY = (y - loc[1]).toFloat()
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
            val alpha = if (night) 0xBE else 0xE0
            val base = if (night) {
                Color.argb(alpha, 48, 51, 56)
            } else {
                Color.argb(alpha, 250, 250, 252)
            }
            val stroke = if (night) {
                Color.argb(64, 255, 255, 255)
            } else {
                Color.argb(26, 0, 0, 0)
            }
            val radius = if (capsule) {
                max(view.height / 2f, Utils.dipToPixels(22f).toFloat())
            } else {
                Utils.dipToPixels(18f).toFloat()
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
            // Menu rows also get iOS hairline separators (the reference UI has
            // them; without them the panel reads as a flat, cheap block).
            if (view is ViewGroup) {
                val count = view.childCount
                for (i in 0 until count) {
                    val child = view.getChildAt(i)
                    val original = child.background
                    val clean = if (original is ColorDrawable &&
                        Color.alpha(original.color) >= 180
                    ) ColorDrawable(Color.TRANSPARENT) else original
                    if (!capsule && i < count - 1 && child.height > 0) {
                        child.background = withHairline(clean, night)
                    } else if (clean !== original) {
                        child.background = clean
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

    private fun withHairline(base: Drawable?, night: Boolean): Drawable {
        val lineColor = if (night) {
            Color.argb(40, 255, 255, 255)
        } else {
            Color.argb(26, 0, 0, 0)
        }
        val line = GradientDrawable().apply { setColor(lineColor) }
        val layers = LayerDrawable(
            arrayOf(base ?: ColorDrawable(Color.TRANSPARENT), line)
        )
        runCatching {
            layers.setLayerGravity(1, Gravity.BOTTOM)
            layers.setLayerHeight(1, max(1, Utils.dipToPixels(1f)))
            layers.setLayerInsetStart(1, Utils.dipToPixels(16f))
            layers.setLayerInsetEnd(1, Utils.dipToPixels(16f))
        }
        return layers
    }

    private fun isNight(): Boolean = runCatching { DesignUtils.isNightMode() }.getOrDefault(true)

    override fun getPluginName(): String = "IosSelectionMenu"
}
