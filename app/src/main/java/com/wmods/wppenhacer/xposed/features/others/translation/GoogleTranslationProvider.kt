package com.wmods.wppenhacer.xposed.features.others.translation

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Google Translate provider using the free gtx API
 */
class GoogleTranslationProvider : TranslationProvider() {
    
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().apply { 
                maxRequests = 4
                maxRequestsPerHost = 4 
            })
            .build()
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
        
        val future = CompletableFuture<String?>()
        
        try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=" +
                    URLEncoder.encode(sourceLanguage, "UTF-8") + "&tl=" +
                    URLEncoder.encode(targetLanguage, "UTF-8") + "&q=" + 
                    URLEncoder.encode(text, "UTF-8")
            
            client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    future.completeExceptionally(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!it.isSuccessful) {
                                throw IOException("Translation HTTP ${it.code}")
                            }
                            
                            val parts = JSONArray(it.body.string()).getJSONArray(0)
                            val translated = buildString {
                                for (i in 0 until parts.length()) {
                                    val part = parts.optJSONArray(i) ?: continue
                                    if (!part.isNull(0)) {
                                        append(part.getString(0))
                                    }
                                }
                            }
                            
                            if (translated.isBlank()) {
                                throw IOException("Empty translation")
                            }
                            
                            future.complete(translated)
                        } catch (e: Exception) {
                            future.completeExceptionally(e)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
        
        return future
    }
    
    override fun getName(): String = "Google Translate"
    
    override fun getId(): String = "google"
    
    override fun getBadge(): String = "GT"
}
