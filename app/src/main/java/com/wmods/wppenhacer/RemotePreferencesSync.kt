package com.wmods.wppenhacer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.factory.preferences
import com.highcapable.yukihookapi.hook.xposed.preference.YukiHookPreferences
import java.util.concurrent.Executors

/** Mirrors AndroidX settings into the LibXposed remote preference store. */
class RemotePreferencesSync(context: Context) {

    private val appContext = context.applicationContext
    private val localPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "WAE-RemotePreferences").apply { isDaemon = true }
    }

    @Volatile
    private var serviceAvailable = false

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (!serviceAvailable) return@OnSharedPreferenceChangeListener

            enqueue {
                if (key == null) syncAll() else syncKey(preferences, key)
            }
        }

    fun start() {
        localPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        YukiHook.Status.observeFrameworkService { available ->
            serviceAvailable = available
            if (available) enqueue(::syncAll)
        }
    }

    private fun syncAll() {
        val values = localPreferences.all
        val remotePreferences = appContext.preferences(PREFERENCES_NAME)
        val staleKeys = remotePreferences.all().keys - values.keys

        remotePreferences.edit {
            staleKeys.forEach(::remove)
            values.forEach { (key, value) -> putValue(key, value) }
        }
        Log.i(TAG, "Mirrored ${values.size} preferences to LibXposed")
    }

    private fun syncKey(preferences: SharedPreferences, key: String) {
        val remotePreferences = appContext.preferences(PREFERENCES_NAME)
        if (preferences.contains(key)) {
            val value = preferences.all[key]
            remotePreferences.edit { putValue(key, value) }
        } else {
            remotePreferences.edit().remove(key).apply()
        }
    }

    private fun enqueue(task: () -> Unit) {
        executor.execute {
            runCatching(task).onFailure { error ->
                Log.e(TAG, "Failed to mirror preferences to LibXposed", error)
            }
            // The hooked app caches a snapshot; refresh it once the remote store holds the new values.
            appContext.sendBroadcast(Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED"))
        }
    }

    private fun YukiHookPreferences.Editor.putValue(key: String, value: Any?) {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, HashSet(value.filterIsInstance<String>()))
            else -> {
                Log.w(TAG, "Skipping unsupported preference value for key '$key'")
                remove(key)
            }
        }
    }

    private companion object {
        const val TAG = "WAE-RemotePrefs"
        const val PREFERENCES_NAME = BuildConfig.APPLICATION_ID + "_preferences"
    }
}
