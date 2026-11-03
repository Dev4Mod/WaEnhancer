package com.wmods.wppenhacer.xposed.features.others.translation

/**
 * Result of a translation request, including which provider was actually used
 */
data class TranslationResult(
    val text: String?,
    val providerName: String,
    val providerBadge: String,
    val wasFallback: Boolean = false
) {
    /**
     * Get provider ID for cache storage
     * Fallback results always return "google" regardless of what was selected
     */
    fun getCacheProviderId(): String = if (wasFallback) "google" else when (providerBadge) {
        "G" -> "google"
        "AI" -> "openai"
        else -> "google"
    }
    
    companion object {
        fun google(text: String?) = TranslationResult(text, "Google Translate", "G", false)
        fun ai(text: String?, model: String?) = TranslationResult(
            text, 
            if (model != null) "AI ($model)" else "AI",
            "AI",
            false
        )
        fun fallback(text: String?) = TranslationResult(text, "Google (AI failed)", "G", true)
    }
}
