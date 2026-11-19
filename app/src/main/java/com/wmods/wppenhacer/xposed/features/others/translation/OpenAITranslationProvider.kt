package com.wmods.wppenhacer.xposed.features.others.translation

import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.utils.YukiLog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible translation provider
 * Supports OpenAI API and compatible services (e.g. local LLMs, other providers)
 */
class OpenAITranslationProvider(
    private val prefs: SharedPreferences
) : TranslationProvider() {
    
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    
    private companion object {
        const val TAG = "[TranslateAI]"
        const val PREF_BASE_URL = "translation_ai_base_url"
        const val PREF_API_KEY = "translation_ai_api_key"
        const val PREF_MODEL = "translation_ai_model"
        const val PREF_TEMPERATURE = "translation_ai_temperature"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_TEMPERATURE = 0.3f
    }
    
    private fun getBaseUrl(): String {
        return normalizeBaseUrl(prefs.getString(PREF_BASE_URL, DEFAULT_BASE_URL)?.trim() ?: DEFAULT_BASE_URL)
    }
    
    /**
     * Normalize base URL: ensure it has /v1 or another version segment
     * Don't double-append if already present anywhere in the path
     */
    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trimEnd('/')
        // Check if already has a version segment like /v1, /v1beta, etc anywhere in path
        return if (trimmed.contains(Regex("/(v\\d+|v\\d+[a-z]+)(/|$)"))) {
            trimmed
        } else {
            "$trimmed/v1"
        }
    }
    
    private fun getApiKey(): String? {
        return prefs.getString(PREF_API_KEY, null)?.takeIf { it.isNotBlank() }
    }
    
    private fun getModel(): String {
        return prefs.getString(PREF_MODEL, DEFAULT_MODEL)?.trim()?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MODEL
    }
    
    private fun getTemperature(): Float {
        return try {
            prefs.getFloat(PREF_TEMPERATURE, DEFAULT_TEMPERATURE)
        } catch (e: Exception) {
            DEFAULT_TEMPERATURE
        }
    }
    
    override fun translateWithResult(
        text: String?,
        sourceLanguage: String,
        targetLanguage: String,
        bypassCache: Boolean,
        customPrompt: String?
    ): CompletableFuture<TranslationResult> {
        val startTime = System.currentTimeMillis()
        
        return translate(text, sourceLanguage, targetLanguage, bypassCache, customPrompt)
            .thenApply { translated ->
                val latency = System.currentTimeMillis() - startTime
                val model = getModel()
                TranslationResult.ai(translated, model)
            }
            .exceptionally { error ->
                val latency = System.currentTimeMillis() - startTime
                YukiLog.log("$TAG ERROR: Translation failed: ${error.message}, latency=${latency}ms")
                throw error
            }
    }
    
    override fun translate(
        text: String?,
        sourceLanguage: String,
        targetLanguage: String,
        bypassCache: Boolean,
        customPrompt: String?
    ): CompletableFuture<String?> {
        if (text.isNullOrBlank()) {
            return CompletableFuture.completedFuture(text)
        }
        
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            return CompletableFuture.failedFuture(
                IOException("AI translation: API key not configured")
            )
        }
        
        val future = CompletableFuture<String?>()
        
        try {
            val baseUrl = getBaseUrl()
            val model = getModel()
            val temperature = getTemperature()
            
            val url = "$baseUrl/chat/completions"
            val prompt = buildPrompt(text, targetLanguage)
            val systemPrompt = buildSystemPrompt(customPrompt)
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("temperature", temperature)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }.toString()
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    future.completeExceptionally(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val statusCode = it.code
                            val responseBody = it.body?.string() ?: ""
                            
                            if (!it.isSuccessful) {
                                val errorSnippet = responseBody.take(100)
                                throw IOException("HTTP $statusCode: $errorSnippet")
                            }
                            
                            val json = JSONObject(responseBody)
                            
                            val choices = json.optJSONArray("choices")
                            if (choices == null || choices.length() == 0) {
                                throw IOException("No choices in response")
                            }
                            
                            val message = choices.getJSONObject(0).optJSONObject("message")
                            val translated = message?.optString("content")?.trim()
                            
                            if (translated.isNullOrBlank()) {
                                throw IOException("Empty translation from AI")
                            }
                            
                            future.complete(translated)
                        } catch (e: Exception) {
                            YukiLog.log("$TAG ERROR parsing response: ${e.message}")
                            future.completeExceptionally(e)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            YukiLog.log("$TAG ERROR building request: ${e.message}")
            future.completeExceptionally(e)
        }
        
        return future
    }
    
    private fun buildPrompt(text: String, targetLanguage: String): String {
        // Map common language codes to full names for better AI understanding
        val langName = when (targetLanguage.lowercase()) {
            "en" -> "English"
            "id" -> "Indonesian"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "pt" -> "Portuguese"
            "ru" -> "Russian"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "zh", "zh-cn" -> "Chinese (Simplified)"
            "zh-tw" -> "Chinese (Traditional)"
            "ar" -> "Arabic"
            "hi" -> "Hindi"
            "it" -> "Italian"
            "nl" -> "Dutch"
            "pl" -> "Polish"
            "tr" -> "Turkish"
            "vi" -> "Vietnamese"
            "th" -> "Thai"
            else -> targetLanguage
        }
        
        return "Translate this to $langName:\n\n$text"
    }
    
    private fun buildSystemPrompt(customPrompt: String?): String {
        val base = "You are a translator. Translate the given text to the target language. " +
                   "Output ONLY the translation, nothing else. Preserve emoji and line breaks exactly."
        
        return if (customPrompt.isNullOrBlank()) {
            base
        } else {
            "$base\n\n$customPrompt"
        }
    }
    
    override fun getName(): String = "AI (OpenAI-compatible)"
    
    override fun getId(): String = "openai"
    
    override fun getBadge(): String = "AIT"
    
    override fun isConfigured(): Boolean {
        return !getApiKey().isNullOrBlank()
    }
    
    /**
     * Fetch available models from the API
     * Handles both OpenAI format {data:[{id}]} and plain arrays
     */
    fun fetchModels(callback: (Result<List<String>>, Int?, String?) -> Unit) {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            callback(Result.failure(IOException("API key not configured")), null, null)
            return
        }
        
        try {
            val baseUrl = getBaseUrl()
            val url = "$baseUrl/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(Result.failure(e), null, e.message)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val statusCode = it.code
                            val responseBody = it.body?.string() ?: ""
                            
                            if (!it.isSuccessful) {
                                val errorSnippet = responseBody.take(200)
                                YukiLog.log("$TAG ERROR fetchModels: $errorSnippet")
                                callback(
                                    Result.failure(IOException("HTTP $statusCode")),
                                    statusCode,
                                    errorSnippet
                                )
                                return
                            }
                            
                            if (responseBody.isBlank()) {
                                callback(
                                    Result.failure(IOException("Empty response")),
                                    statusCode,
                                    "Empty response body"
                                )
                                return
                            }
                            
                            val models = parseModelsResponse(responseBody)
                            
                            if (models.isEmpty()) {
                                callback(
                                    Result.failure(IOException("No models found")),
                                    statusCode,
                                    "Response contained no models"
                                )
                            } else {
                                callback(Result.success(models.sorted()), statusCode, null)
                            }
                        } catch (e: Exception) {
                            YukiLog.log("$TAG ERROR fetchModels parse: ${e.message}")
                            callback(Result.failure(e), it.code, e.message)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            YukiLog.log("$TAG ERROR fetchModels request: ${e.message}")
            callback(Result.failure(e), null, e.message)
        }
    }
    
    private fun parseModelsResponse(responseBody: String): List<String> {
        val json = JSONObject(responseBody)
        val models = mutableListOf<String>()
        
        // Try OpenAI format: {data: [{id: "model-name"}]}
        val data = json.optJSONArray("data")
        if (data != null) {
            for (i in 0 until data.length()) {
                val modelObj = data.optJSONObject(i)
                val id = modelObj?.optString("id")
                if (!id.isNullOrBlank()) {
                    models.add(id)
                }
            }
            return models
        }
        
        // Try plain array: {models: ["model-name"]}
        val modelsArray = json.optJSONArray("models")
        if (modelsArray != null) {
            for (i in 0 until modelsArray.length()) {
                val id = modelsArray.optString(i)
                if (id.isNotBlank()) {
                    models.add(id)
                }
            }
            return models
        }
        
        // Try top-level array
        try {
            val rootArray = JSONArray(responseBody)
            for (i in 0 until rootArray.length()) {
                val item = rootArray.opt(i)
                if (item is String && item.isNotBlank()) {
                    models.add(item)
                } else if (item is JSONObject) {
                    val id = item.optString("id")
                    if (id.isNotBlank()) {
                        models.add(id)
                    }
                }
            }
        } catch (e: Exception) {
            // Not a root array, that's fine
        }
        
        return models
    }
}
