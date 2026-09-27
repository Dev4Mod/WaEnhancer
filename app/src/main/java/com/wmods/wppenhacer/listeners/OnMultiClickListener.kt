package com.wmods.wppenhacer.listeners

import android.view.View

abstract class OnMultiClickListener(
    private val targetClicks: Int,
    private val delay: Long
) : View.OnClickListener {
    private var lastClick = 0L
    private var clicks = 0

    init {
        require(targetClicks >= 2) { "targetClicks must be greater than 1" }
    }

    override fun onClick(v: View) {
        if (lastClick == 0L || System.currentTimeMillis() - lastClick < delay) {
            lastClick = System.currentTimeMillis()
            clicks++
        } else {
            lastClick = 0L
            clicks = 0
        }

        if (clicks >= targetClicks) {
            clicks = 0
            lastClick = 0L
            onMultiClick(v)
        }
    }

    abstract fun onMultiClick(v: View)
}
