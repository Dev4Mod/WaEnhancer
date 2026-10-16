package com.wmods.wppenhacer.toolkit

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Ventana principal del módulo: cuadrícula de herramientas, cada una con su icono y su nombre. */
object Hub {
    class Tool(val icon: String, val title: String, val description: String, val action: () -> Unit)

    private const val COLUMNS = 2

    fun show(activity: Activity, version: String, tools: List<Tool>) {
        val p = Monet.palette(activity)
        val dlg = Dlg(activity)
        fun dp(v: Int) = Host.dp(activity, v)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(6))
        }

        // Cabecera: nombre y versión
        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), dp(16))
            addView(TextView(activity).apply {
                text = S.get(S.hub_title)
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(p.onSurface)
            })
            addView(TextView(activity).apply {
                text = "v$version"
                textSize = 12f
                setTextColor(p.onContainer)
                background = Monet.rounded(p.container, dp(10).toFloat())
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(10) })
        })

        // Cuadrícula de herramientas
        for (rowTools in tools.chunked(COLUMNS)) {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until COLUMNS) {
                val tool = rowTools.getOrNull(i)
                val cell = if (tool == null) android.view.View(activity) else tile(activity, p, tool) { dlg.dismiss(); tool.action() }
                row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            root.addView(row)
        }

        dlg.setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton(S.get(S.deleted_close), null)
            .show()
    }

    private fun tile(activity: Activity, p: Monet.Palette, tool: Tool, onClick: () -> Unit): LinearLayout {
        fun dp(v: Int) = Host.dp(activity, v)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Monet.rounded(p.card, dp(24).toFloat())
            setPadding(dp(8), dp(16), dp(8), dp(14))
            isClickable = true
            setOnClickListener { onClick() }
            addView(TextView(activity).apply {
                text = tool.icon
                textSize = 26f
                gravity = Gravity.CENTER
                background = Monet.rounded(p.container, dp(28).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            })
            addView(TextView(activity).apply {
                text = tool.title
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(p.onSurface)
                setPadding(0, dp(10), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}
