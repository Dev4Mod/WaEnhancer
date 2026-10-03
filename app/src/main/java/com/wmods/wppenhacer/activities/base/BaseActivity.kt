package com.wmods.wppenhacer.activities.base

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.wmods.wppenhacer.R

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        theme.applyStyle(
            rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference,
            true
        )
        theme.applyStyle(R.style.ThemeOverlay, true)
        applyThemeOverlay()
        super.onCreate(savedInstanceState)
    }

    private fun applyThemeOverlay() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val colorMode = prefs.getString("wae_color_mode", "preset")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorMode == "monet") return

        val colorPreset = prefs.getString("wae_color_preset", "green")
        theme.applyStyle(resolveColorOverlay(colorPreset), true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            theme.applyStyle(R.style.ThemeOverlay_LegacyTextColors, true)
        }
    }

    private fun resolveColorOverlay(colorPreset: String?): Int = when (colorPreset) {
        "blue" -> R.style.ThemeOverlay_MaterialBlue
        "cyan" -> R.style.ThemeOverlay_MaterialCyan
        "purple" -> R.style.ThemeOverlay_MaterialPurple
        "orange" -> R.style.ThemeOverlay_MaterialOrange
        "red" -> R.style.ThemeOverlay_MaterialRed
        "pink" -> R.style.ThemeOverlay_MaterialPink
        else -> R.style.ThemeOverlay_MaterialGreen
    }
}
