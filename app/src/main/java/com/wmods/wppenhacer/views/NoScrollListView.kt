package com.wmods.wppenhacer.views

import android.content.Context
import android.widget.ListView

class NoScrollListView(context: Context) : ListView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(0x1FFFFFFF, MeasureSpec.AT_MOST)
        )
        layoutParams.height = measuredHeight
    }
}
