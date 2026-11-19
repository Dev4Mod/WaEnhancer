package com.wmods.wppenhacer.xposed.features.others.translation

import android.content.SharedPreferences

/**
 * Settings manager for translation providers
 */
object TranslationSettings {
    private const val PREF_PROVIDER = "translation_provider"
    private const val PREF_FALLBACK_TO_GOOGLE = "translation_ai_fallback_to_google"
    
    const val PROVIDER_GOOGLE = "google"
    const val PROVIDER_OPENAI = "openai"
    
    fun getSelectedProvider(prefs: SharedPreferences): String {
        return prefs.getString(PREF_PROVIDER, PROVIDER_GOOGLE) ?: PROVIDER_GOOGLE
    }
    
    fun setSelectedProvider(prefs: SharedPreferences, provider: String) {
        prefs.edit().putString(PREF_PROVIDER, provider).apply()
    }
    
    fun shouldFallbackToGoogle(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_FALLBACK_TO_GOOGLE, true)
    }
    
    fun setFallbackToGoogle(prefs: SharedPreferences, fallback: Boolean) {
        prefs.edit().putBoolean(PREF_FALLBACK_TO_GOOGLE, fallback).apply()
    }
}
