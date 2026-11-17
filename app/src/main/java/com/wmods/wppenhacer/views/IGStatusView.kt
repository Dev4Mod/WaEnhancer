package com.wmods.wppenhacer.views

import android.content.Context
import android.widget.FrameLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wmods.wppenhacer.adapter.IGStatusAdapter
import com.wmods.wppenhacer.xposed.core.FeatureLoader

class IGStatusView(context: Context) : FrameLayout(context) {
    @JvmField
    val mStatusListView: RecyclerView = RecyclerView(FeatureLoader.moduleContext).apply {
        layoutManager = LinearLayoutManager(
            FeatureLoader.moduleContext, LinearLayoutManager.HORIZONTAL, false
        )
        setHasFixedSize(true)
        itemAnimator = DefaultItemAnimator().apply {
            supportsChangeAnimations = false
            addDuration = 200
            removeDuration = 200
            moveDuration = 250
        }
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    @JvmField
    var mStatusAdapter: IGStatusAdapter? = null

    var fragmentId = 0

    var adapter: IGStatusAdapter?
        get() = mStatusAdapter
        set(value) {
            mStatusAdapter = value
            mStatusListView.adapter = value
        }

    init {
        addView(mStatusListView)
    }

    override fun setTranslationY(translationY: Float) {
        if (height > 0) {
            val visibility = if (translationY > height.toFloat()) GONE else VISIBLE
            if (visibility == VISIBLE) super.setTranslationY(translationY)
            this.visibility = visibility
        }
    }

    fun updateList(newItems: List<Any?>) {
        post { mStatusAdapter?.submitList(newItems) }
    }

}
