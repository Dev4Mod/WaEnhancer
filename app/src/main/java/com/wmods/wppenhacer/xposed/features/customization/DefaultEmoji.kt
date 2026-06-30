package com.wmods.wppenhacer.xposed.features.customization

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.max

class DefaultEmoji(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    private val minScale = 1.00f
    private val maxScale = 2.00f
    private val widthPaddingRatio = 0.40f
    private val heightPaddingRatio = 0.35f

    private val spanWidths = WeakHashMap<Any, Int>()

    override fun doHook() {
        if (prefs.getBoolean("force_disable_emojis", false)) {
            val assetsClass = Utils.application.resources.assets.javaClass
            XposedBridge.hookAllMethods(assetsClass, "openFd", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val name = param.args[0] as String
                    if (name.contains("emojis.oba"))
                        param.result = null
                }
            })
            return
        }
        if (!prefs.getBoolean("disable_defemojis", false)) return
        Unobfuscator.loadGetSizeSpanMethods(classLoader).forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    overrideGetSize(param)
                }
            })
        }

        Unobfuscator.loadDrawSpanMethods(classLoader).forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    drawSystemEmoji(param)
                }
            })
        }
    }

    private fun overrideGetSize(param: MethodHookParam) {
        val span = param.thisObject ?: return
        val paint = param.args.getOrNull(0) as? Paint ?: return
        val text = param.args.getOrNull(1) as? CharSequence ?: return
        val start = param.args.getOrNull(2) as? Int ?: return
        val end = param.args.getOrNull(3) as? Int ?: return
        val fontMetrics = param.args.getOrNull(4) as? Paint.FontMetricsInt

        if (!isValidRange(text, start, end)) return

        val emojiText = text.subSequence(start, end).toString()
        val drawable = getEmojiDrawable(span)
        val drawableBounds = drawable?.bounds ?: Rect()

        val emojiPaint = createEmojiPaint(
            paint = paint,
            emojiText = emojiText,
            drawableBounds = drawableBounds
        )

        val baseWidth = max(
            drawableBounds.width().toFloat(),
            emojiPaint.measureText(emojiText)
        )

        val paddingSource = max(drawableBounds.width(), paint.textSize.toInt())
        val widthPadding = ceil(paddingSource * widthPaddingRatio).toInt()
        val finalWidth = ceil(baseWidth).toInt() + widthPadding

        updateExpandedFontMetrics(
            paint = emojiPaint,
            drawableBounds = drawableBounds,
            target = fontMetrics
        )

        synchronized(spanWidths) {
            spanWidths[span] = finalWidth
        }

        param.result = finalWidth
    }

    private fun drawSystemEmoji(param: MethodHookParam) {
        val canvas = param.args.getOrNull(0) as? Canvas ?: return
        val text = param.args.getOrNull(1) as? CharSequence ?: return
        val start = param.args.getOrNull(2) as? Int ?: return
        val end = param.args.getOrNull(3) as? Int ?: return
        val x = param.args.getOrNull(4) as? Float ?: return
        val y = param.args.getOrNull(6) as? Int ?: return
        val paint = param.args.getOrNull(8) as? Paint ?: return

        if (!isValidRange(text, start, end)) return

        val emojiText = text.subSequence(start, end).toString()
        val drawable = getEmojiDrawable(param.thisObject)
        val drawableBounds = drawable?.bounds ?: Rect()

        val emojiPaint = createEmojiPaint(
            paint = paint,
            emojiText = emojiText,
            drawableBounds = drawableBounds
        )

        val drawY = calculateCenteredBaseline(
            originalPaint = paint,
            emojiPaint = emojiPaint,
            baselineY = y
        )

        canvas.drawText(
            emojiText,
            x,
            drawY,
            emojiPaint
        )

        param.result = null
    }

    private fun createEmojiPaint(
        paint: Paint,
        emojiText: String,
        drawableBounds: Rect
    ): Paint {
        val heightScale = calculateHeightScale(
            paint = paint,
            drawableBounds = drawableBounds
        )

        val widthScale = calculateWidthScale(
            paint = paint,
            emojiText = emojiText,
            drawableBounds = drawableBounds
        )

        val scale = max(heightScale, widthScale)
            .coerceIn(minScale, maxScale)

        return Paint(paint).apply {
            textSize = paint.textSize * scale
        }
    }

    private fun calculateHeightScale(
        paint: Paint,
        drawableBounds: Rect
    ): Float {
        val targetHeight = drawableBounds.height().toFloat()
        if (targetHeight <= 0f) return minScale

        val currentHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
        if (currentHeight <= 0f) return minScale

        return targetHeight / currentHeight
    }

    private fun calculateWidthScale(
        paint: Paint,
        emojiText: String,
        drawableBounds: Rect
    ): Float {
        val targetWidth = drawableBounds.width().toFloat()
        if (targetWidth <= 0f) return minScale

        val currentWidth = paint.measureText(emojiText)
        if (currentWidth <= 0f) return minScale

        return targetWidth / currentWidth
    }

    private fun calculateCenteredBaseline(
        originalPaint: Paint,
        emojiPaint: Paint,
        baselineY: Int
    ): Float {
        val originalMetrics = originalPaint.fontMetrics
        val emojiMetrics = emojiPaint.fontMetrics

        val originalCenter = baselineY + (originalMetrics.ascent + originalMetrics.descent) / 2f
        return originalCenter - (emojiMetrics.ascent + emojiMetrics.descent) / 2f
    }

    private fun updateExpandedFontMetrics(
        paint: Paint,
        drawableBounds: Rect,
        target: Paint.FontMetricsInt?
    ) {
        if (target == null) return

        val metrics = paint.fontMetricsInt
        val paddingSource = max(drawableBounds.height(), paint.textSize.toInt())
        val padding = ceil(paddingSource * heightPaddingRatio).toInt()

        target.ascent = metrics.ascent - padding
        target.descent = metrics.descent + padding
        target.top = metrics.top - padding
        target.bottom = metrics.bottom + padding
    }

    private fun getEmojiDrawable(span: Any?): Drawable? {
        if (span == null) return null

        return runCatching {
            val field = span.javaClass.declaredMethods.firstOrNull {
                it.name.length == 3 && it.returnType == Drawable::class.java
            }
            field?.isAccessible = true
            field?.invoke(span) as? Drawable
        }.getOrNull() ?: runCatching {
            XposedHelpers.callMethod(span, "getDrawable") as? Drawable
        }.getOrNull()
    }

    private fun isValidRange(
        text: CharSequence,
        start: Int,
        end: Int
    ): Boolean {
        return start >= 0 && end <= text.length && start < end
    }

    override fun getPluginName(): String = "Default Emoji"
}