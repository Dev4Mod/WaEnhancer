package com.wmods.wppenhacer.views.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils

class TabDialogContent(context: Context) : LinearLayout(context) {
    private lateinit var titleView: TextView
    private lateinit var contentLinear: LinearLayout

    init {
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        layoutParams = params
        orientation = VERTICAL
        setBackground(
            DesignUtils.createDrawable(
                "rc_dialog_bg",
                DesignUtils.getPrimarySurfaceColor()
            )
        )
        setPadding(
            Utils.dipToPixels(16),
            Utils.dipToPixels(12),
            Utils.dipToPixels(16),
            Utils.dipToPixels(16)
        )

        val lineLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams =
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setMargins(0, Utils.dipToPixels(5), 0, Utils.dipToPixels(5))
                }
        }
        val lineImage = ImageView(context).apply {
            layoutParams = LayoutParams(Utils.dipToPixels(70), LayoutParams.MATCH_PARENT)
            setImageDrawable(
                DesignUtils.createDrawable(
                    "rc_dotline_dialog",
                    DesignUtils.getPrimaryTextColor()
                )
            )
        }
        lineLayout.addView(lineImage)
        addView(lineLayout)

        titleView = TextView(context).apply {
            layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                    setMargins(Utils.dipToPixels(6), 0, 0, 0)
                }
            gravity = Gravity.CENTER
            setTextColor(DesignUtils.getPrimaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(null, Typeface.BOLD)
        }
        addView(titleView)

        addView(View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, Utils.dipToPixels(0.9f)).apply {
                setMargins(0, Utils.dipToPixels(10), 0, 0)
            }
            setBackgroundColor(Color.GRAY)
        })

        contentLinear = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams =
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, Utils.dipToPixels(5), 0, Utils.dipToPixels(5))
                }
        }
        addView(contentLinear)
    }

    fun setTitle(title: String) {
        titleView.text = title
    }

    fun addTab(title: String, image: Drawable?, listener: OnClickListener?) {
        val tab = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(Utils.dipToPixels(1.5f), 0, Utils.dipToPixels(1.5f), 0)
            }
            setPadding(
                Utils.dipToPixels(10),
                Utils.dipToPixels(20),
                Utils.dipToPixels(10),
                Utils.dipToPixels(10)
            )
            setBackground(
                DesignUtils.createDrawable(
                    "stroke_border",
                    DesignUtils.getPrimaryTextColor()
                )
            )
            setOnClickListener(listener)
        }
        tab.addView(ImageView(context).apply {
            layoutParams = LayoutParams(Utils.dipToPixels(40), Utils.dipToPixels(40))
            setImageDrawable(image)
        })
        tab.addView(TextView(context).apply {
            layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, Utils.dipToPixels(5), 0, 0)
                }
            text = title
            setTextColor(DesignUtils.getPrimaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        })
        contentLinear.addView(tab)
    }
}
