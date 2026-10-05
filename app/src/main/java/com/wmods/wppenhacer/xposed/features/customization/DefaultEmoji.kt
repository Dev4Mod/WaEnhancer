package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.param.HookParam
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.max

class DefaultEmoji(
    classLoader: ClassLoader,
    xprefs: SharedPreferences
) : Feature(classLoader, xprefs) {

    private val minScale = 1.00f
    private val maxScale = 2.00f
    private val widthPaddingRatio = 0.40f
    private val heightPaddingRatio = 0.35f

    private val spanWidths = WeakHashMap<Any, Int>()
    private val drawableMethodCache = WeakHashMap<Class<*>, Method?>()

    override fun doHook() {
        if (xprefs.getBoolean("force_disable_emojis", false)) {
            val assetsClass = Utils.application.resources.assets.javaClass
            assetsClass.resolve().method {
                name = "openFd"
            }.hookAll {
                before {
                    val name = args[0] as String
                    if (name.contains("emojis.oba"))
                        result = null
                }
            }
            return
        }
        if (!xprefs.getBoolean("disable_defemojis", false)) return
        val sizeMethods = Unobfuscator.loadEmojiSpanGetSizeMethods(classLoader)
        val drawMethods = Unobfuscator.loadEmojiSpanDrawMethods(classLoader)
        sizeMethods.forEach { method ->
            method.hook {
                after {
                    overrideGetSize(this)
                }
            }
        }

        drawMethods.forEach { method ->
            method.hook {
                before {
                    drawSystemEmoji(this)
                }
            }
        }
    }

    private fun overrideGetSize(param: HookParam) {
        val span = param.instanceOrNull ?: return
        val paint = param.args.getOrNull(0) as? Paint ?: return
        val text = param.args.getOrNull(1) as? CharSequence ?: return
        val start = param.args.getOrNull(2) as? Int ?: return
        val end = param.args.getOrNull(3) as? Int ?: return
        val fontMetrics = param.args.getOrNull(4) as? Paint.FontMetricsInt

        if (!isValidRange(text, start, end)) return

        val emojiText = text.subSequence(start, end).toString()
        if (!isEmojiText(emojiText)) return
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

    private fun drawSystemEmoji(param: HookParam) {
        val canvas = param.args.getOrNull(0) as? Canvas ?: return
        val text = param.args.getOrNull(1) as? CharSequence ?: return
        val start = param.args.getOrNull(2) as? Int ?: return
        val end = param.args.getOrNull(3) as? Int ?: return
        val x = param.args.getOrNull(4) as? Float ?: return
        val y = param.args.getOrNull(6) as? Int ?: return
        val paint = param.args.getOrNull(8) as? Paint ?: return

        if (!isValidRange(text, start, end)) return

        val emojiText = text.subSequence(start, end).toString()
        if (!isEmojiText(emojiText)) return
        val drawable = getEmojiDrawable(param.instanceOrNull)
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
            val method = synchronized(drawableMethodCache) {
                if (drawableMethodCache.containsKey(span.javaClass)) {
                    drawableMethodCache[span.javaClass]
                } else {
                    val found = span.javaClass.declaredMethods.firstOrNull {
                        it.name.length == 3 && it.returnType == Drawable::class.java
                    }
                    found?.isAccessible = true
                    drawableMethodCache[span.javaClass] = found
                    found
                }
            }
            method?.invoke(span) as? Drawable
        }.getOrNull() ?: runCatching {
            ReflectionUtils.callMethod(span, "getDrawable") as? Drawable
        }.getOrNull()
    }

    private fun isEmojiText(text: String): Boolean {
        if (text.isEmpty()) return false
        val cp = text.codePointAt(0)
        if (cp >= 0x1F000) return true
        if (cp in 0x2190..0x2BFF || cp == 0xA9 || cp == 0xAE || cp == 0x203C || cp == 0x2049) return true
        if (cp in 0x3030..0x303D || cp == 0x3297 || cp == 0x3299) return true
        return (cp == '#'.code || cp == '*'.code || cp in '0'.code..'9'.code) &&
            text.length > 1 && (text.contains('\uFE0F') || text.contains('\u20E3'))
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
