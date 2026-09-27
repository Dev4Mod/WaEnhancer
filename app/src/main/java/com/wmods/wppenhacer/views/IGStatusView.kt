package com.wmods.wppenhacer.views

import android.content.Context
import android.widget.FrameLayout
import com.wmods.wppenhacer.adapter.IGStatusAdapter

class IGStatusView(context: Context) : FrameLayout(context) {
    @JvmField
    var mStatusListView: HorizontalListView? = null

    @JvmField
    var mStatusAdapter: IGStatusAdapter? = null

    var fragmentId = 0

    var adapter: IGStatusAdapter?
        get() = mStatusAdapter
        set(value) {
            mStatusAdapter = value
            if (value != null) mStatusListView?.setAdapter(value)
        }

    init {
        val statusListView = HorizontalListView(context)
        statusListView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        mStatusListView = statusListView
        addView(statusListView)
    }

    override fun setTranslationY(translationY: Float) {
        if (height > 0) {
            val visibility = if (translationY > height.toFloat()) GONE else VISIBLE
            if (visibility == VISIBLE) super.setTranslationY(translationY)
            this.visibility = visibility
        }
    }

    fun updateList() {
        post {
            mStatusAdapter?.notifyDataSetChanged()
            invalidate()
        }
    }

}
