package com.wmods.wppenhacer.preference

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.preference.PreferenceViewHolder
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import com.wmods.wppenhacer.R

class FeatureColorPreferenceCompat(context: Context, attrs: AttributeSet?) :
    ColorPreferenceCompat(context, attrs) {

    init {
        setLayoutResource(R.layout.preference_feature_color)
        setIconSpaceReserved(true)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val iconContainer = holder.findViewById(R.id.icon_container) as? FrameLayout
        val widgetFrame = holder.findViewById(android.R.id.widget_frame) as? FrameLayout
        val iconView = holder.findViewById(android.R.id.icon)
        val icon: Drawable? = getIcon()

        if (iconContainer != null && iconView != null) {
            iconContainer.visibility = if (icon == null) View.GONE else View.VISIBLE
            iconView.visibility = if (icon == null) View.GONE else View.VISIBLE
        }

        if (widgetFrame != null) {
            for (index in 0 until widgetFrame.childCount) {
                val child = widgetFrame.getChildAt(index)
                val layoutParams = child.layoutParams
                if (layoutParams is FrameLayout.LayoutParams) {
                    layoutParams.gravity = Gravity.CENTER
                    child.layoutParams = layoutParams
                }
            }
        }
    }
}
