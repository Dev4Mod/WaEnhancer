package com.wmods.wppenhacer.xposed.utils

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
fun View.setTouchClickAndLongClickListener(
    longPressDelayMs: Long = ViewConfiguration.getLongPressTimeout().toLong(),
    onClick: (View) -> Unit,
    onLongClick: (View) -> Unit
) {
    val handler = Handler(Looper.getMainLooper())
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var downX = 0f
    var downY = 0f
    var isLongClick = false
    var isCanceled = false

    val longClickRunnable = Runnable {
        isLongClick = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongClick(this)
    }

    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isLongClick = false
                isCanceled = false

                view.isPressed = true
                handler.postDelayed(longClickRunnable, longPressDelayMs)

                true
            }

            MotionEvent.ACTION_MOVE -> {
                val movedTooMuch =
                    abs(event.x - downX) > touchSlop ||
                            abs(event.y - downY) > touchSlop

                if (movedTooMuch && !isCanceled) {
                    isCanceled = true
                    view.isPressed = false
                    handler.removeCallbacks(longClickRunnable)
                }

                true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longClickRunnable)
                view.isPressed = false

                if (!isCanceled && !isLongClick) {
                    onClick(view)
                }

                true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longClickRunnable)
                view.isPressed = false
                isCanceled = true

                true
            }

            else -> true
        }
    }
}