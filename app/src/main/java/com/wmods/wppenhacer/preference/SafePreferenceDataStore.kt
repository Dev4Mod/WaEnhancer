package com.wmods.wppenhacer.preference

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

/**
 * A resilient PreferenceDataStore that prevents ClassCastException crashes
 * when SharedPreferences contain mismatched types (e.g., from older app versions,
 * mods, or imported backups).
 */
class SafePreferenceDataStore(
    private val prefs: SharedPreferences
) : PreferenceDataStore() {

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, defValue)
        } catch (_: ClassCastException) {
            val str = try { prefs.getString(key, null) } catch (_: Exception) { null }
            val resolved = when (str?.trim()?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> defValue
            }
            try {
                prefs.edit().remove(key).putBoolean(key, resolved).apply()
            } catch (_: Exception) {}
            resolved
        } catch (_: Exception) {
            defValue
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String, defValue: String?): String? {
        return try {
            prefs.getString(key, defValue)
        } catch (_: ClassCastException) {
            val all = try { prefs.all } catch (_: Exception) { emptyMap() }
            all[key]?.toString() ?: defValue
        } catch (_: Exception) {
            defValue
        }
    }

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, defValue: Int): Int {
        return try {
            prefs.getInt(key, defValue)
        } catch (_: ClassCastException) {
            val str = try { prefs.getString(key, null) } catch (_: Exception) { null }
            str?.toIntOrNull() ?: defValue
        } catch (_: Exception) {
            defValue
        }
    }

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return try {
            prefs.getFloat(key, defValue)
        } catch (_: ClassCastException) {
            val str = try { prefs.getString(key, null) } catch (_: Exception) { null }
            str?.toFloatOrNull() ?: defValue
        } catch (_: Exception) {
            defValue
        }
    }

    override fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    override fun getLong(key: String, defValue: Long): Long {
        return try {
            prefs.getLong(key, defValue)
        } catch (_: ClassCastException) {
            val str = try { prefs.getString(key, null) } catch (_: Exception) { null }
            str?.toLongOrNull() ?: defValue
        } catch (_: Exception) {
            defValue
        }
    }

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return try {
            prefs.getStringSet(key, defValues)
        } catch (_: Exception) {
            defValues
        }
    }

    override fun putStringSet(key: String, values: Set<String>?) {
        prefs.edit().putStringSet(key, values).apply()
    }
}
