package com.wmods.wppenhacer.xposed.features.others.translation

import java.util.concurrent.CompletableFuture

/**
 * Abstract translation provider interface.
 * Implementations can use Google Translate, OpenAI, or other services.
 */
abstract class TranslationProvider {
    
    /**
     * Translate text from source language to target language.
     * @param text The text to translate
     * @param sourceLanguage The source language code (e.g. "auto", "en", "id")
     * @param targetLanguage The target language code (e.g. "en", "id")
     * @param bypassCache if true, skip cache read (but still write result)
     * @return CompletableFuture with the translated text, or null on failure
     */
    abstract fun translate(
        text: String?,
        sourceLanguage: String,
        targetLanguage: String,
        bypassCache: Boolean = false,
        customPrompt: String? = null
    ): CompletableFuture<String?>
    
    /**
     * Translate and return result with provider info
     * @param bypassCache if true, skip cache read (but still write result)
     * @param customPrompt additional custom prompt instructions (AI only)
     */
    open fun translateWithResult(
        text: String?,
        sourceLanguage: String,
        targetLanguage: String,
        bypassCache: Boolean = false,
        customPrompt: String? = null
    ): CompletableFuture<TranslationResult> {
        return translate(text, sourceLanguage, targetLanguage, bypassCache, customPrompt).thenApply { translated ->
            TranslationResult(translated, getName(), getBadge())
        }
    }
    
    /**
     * Get the provider name for display and logging
     */
    abstract fun getName(): String
    
    /**
     * Get a short identifier for this provider (e.g. "google", "openai")
     */
    abstract fun getId(): String
    
    /**
     * Get a short display badge (e.g. "G" for Google, "AI" for OpenAI)
     */
    abstract fun getBadge(): String
    
    /**
     * Check if the provider is properly configured
     */
    open fun isConfigured(): Boolean = true
}
