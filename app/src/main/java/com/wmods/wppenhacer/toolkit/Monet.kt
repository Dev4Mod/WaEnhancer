package com.wmods.wppenhacer.toolkit

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Colores Material You (Monet): usa la paleta dinámica del sistema (Android 12+) y, si no está
 * disponible, una paleta verde fija. Sigue el modo claro/oscuro con el que WhatsApp pinta su pantalla.
 */
object Monet {
    class Palette(
        val surface: Int, val onSurface: Int, val onSurfaceVariant: Int,
        val primary: Int, val onPrimary: Int,
        val container: Int, val onContainer: Int, val card: Int, val outline: Int
    )

    fun isNight(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun palette(context: Context): Palette {
        val night = isNight(context)
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                fun c(id: Int) = context.getColor(id)
                return if (night) Palette(
                    surface = c(android.R.color.system_neutral1_900),
                    onSurface = c(android.R.color.system_neutral1_100),
                    onSurfaceVariant = c(android.R.color.system_neutral2_200),
                    primary = c(android.R.color.system_accent1_200),
                    onPrimary = c(android.R.color.system_accent1_800),
                    container = c(android.R.color.system_accent1_700),
                    onContainer = c(android.R.color.system_accent1_100),
                    card = c(android.R.color.system_neutral2_800),
                    outline = c(android.R.color.system_neutral2_500)
                ) else Palette(
                    surface = c(android.R.color.system_neutral1_10),
                    onSurface = c(android.R.color.system_neutral1_900),
                    onSurfaceVariant = c(android.R.color.system_neutral2_700),
                    primary = c(android.R.color.system_accent1_600),
                    onPrimary = c(android.R.color.system_accent1_0),
                    container = c(android.R.color.system_accent1_100),
                    onContainer = c(android.R.color.system_accent1_900),
                    card = c(android.R.color.system_neutral2_100),
                    outline = c(android.R.color.system_neutral2_500)
                )
            } catch (_: Throwable) {
            }
        }
        return if (night) Palette(
            0xFF191C1A.toInt(), 0xFFE1E3DF.toInt(), 0xFFC0C9C1.toInt(), 0xFF7FD9B0.toInt(),
            0xFF003826.toInt(), 0xFF005138.toInt(), 0xFF9BF6CB.toInt(), 0xFF2B312D.toInt(), 0xFF8A938C.toInt()
        ) else Palette(
            0xFFFBFDF9.toInt(), 0xFF191C1A.toInt(), 0xFF404944.toInt(), 0xFF006C4C.toInt(),
            0xFFFFFFFF.toInt(), 0xFF9BF6CB.toInt(), 0xFF002114.toInt(), 0xFFE6EFE9.toInt(), 0xFF707973.toInt()
        )
    }

    fun rounded(color: Int, radiusPx: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
    }

    private fun withAlpha(color: Int, a: Float) = Color.argb((255 * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    /** Lista simple de textos con los colores del tema. */
    fun adapter(context: Context, items: List<String>): ArrayAdapter<String> {
        val p = palette(context)
        return object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items.toMutableList()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.setTextColor(p.onSurface)
                return v
            }
        }
    }

    /** Aplica el tema a un diálogo ya mostrado: fondo redondeado, botones y todo su contenido. */
    fun styleDialog(dialog: AlertDialog) {
        try {
            val ctx = dialog.context
            val p = palette(ctx)
            val dm = ctx.resources.displayMetrics
            dialog.window?.setBackgroundDrawable(rounded(p.surface, 28 * dm.density))
            for (id in listOf("alertTitle", "message")) {
                val rid = ctx.resources.getIdentifier(id, "id", "android")
                (dialog.findViewById<View>(rid) as? TextView)?.setTextColor(p.onSurface)
            }
            for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
                dialog.getButton(which)?.apply {
                    setTextColor(p.primary)
                    background = null
                }
            }
            dialog.window?.decorView?.let { themeTree(it, p, skip = setOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).mapNotNull { w -> dialog.getButton(w) }) }
        } catch (_: Throwable) {
        }
    }

    private fun themeTree(v: View, p: Palette, skip: List<View>) {
        if (v in skip) return
        when (v) {
            is EditText -> {
                v.setTextColor(p.onSurface)
                v.setHintTextColor(withAlpha(p.onSurface, 0.55f))
                v.backgroundTintList = ColorStateList.valueOf(p.primary)
            }
            is CompoundButton -> {
                v.setTextColor(p.onSurface)
                v.buttonTintList = ColorStateList.valueOf(p.primary)
            }
            is Button -> {
                val r = 20 * v.resources.displayMetrics.density
                v.background = rounded(p.primary, r)
                v.setTextColor(p.onPrimary)
                v.isAllCaps = false
            }
            is ProgressBar -> {
                val tint = ColorStateList.valueOf(p.primary)
                v.indeterminateTintList = tint
                v.progressTintList = tint
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) themeTree(v.getChildAt(i), p, skip)
    }
}
