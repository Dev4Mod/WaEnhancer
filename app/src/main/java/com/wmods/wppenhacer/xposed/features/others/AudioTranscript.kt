package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import android.text.TextUtils
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class AudioTranscript(
    classLoader: ClassLoader,
    preferences:SharedPreferences
) : Feature(classLoader, preferences) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient()
    }

    @Throws(Throwable::class)
    override fun doHook() {
        if (!prefs.getBoolean(PREF_AUDIO_TRANSCRIPTION, false)) {
            return
        }

        val provider = prefs.getString(
            PREF_TRANSCRIPTION_PROVIDER,
            PROVIDER_ASSEMBLY_AI
        ) ?: PROVIDER_ASSEMBLY_AI

        val apiKey = getApiKey(provider)

        if (TextUtils.isEmpty(apiKey)) {
            return
        }

        val transcribeMethod = Unobfuscator.loadTranscribeMethod(classLoader)
        val transcriptionSegmentClass = Unobfuscator.loadTranscriptSegment(classLoader)

        XposedBridge.hookMethod(transcribeMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                handleTranscriptionHook(
                    param = param,
                    provider = provider,
                    transcriptionSegmentClass = transcriptionSegmentClass
                )
            }
        })

    }

    @Throws(Throwable::class)
    private fun handleTranscriptionHook(
        param: XC_MethodHook.MethodHookParam,
        provider: String,
        transcriptionSegmentClass: Class<*>
    ) {
        val pttTranscriptionRequest = param.args[0]
        val fieldFMessage = ReflectionUtils.getFieldByExtendType(
            pttTranscriptionRequest!!.javaClass,
            FMessageWpp.TYPE
        ) ?: return

        val fmessageObj = fieldFMessage.get(pttTranscriptionRequest)
        val fmessage = FMessageWpp(fmessageObj)
        val file = fmessage.mediaFile

        if (file == null) {
            Utils.showToast(Utils.getString(R.string.download_not_available), Toast.LENGTH_LONG)
            return
        }

        if (!file.exists()) {
            return
        }

        val callback = param.args[1]
        val onComplete = ReflectionUtils.findMethodUsingFilter(callback!!.javaClass) { method ->
            method.parameterCount == ON_COMPLETE_PARAMETER_COUNT
        }

        val responseJson = transcribeAudio(file, provider)
        val transcript = responseJson.getString(JSON_TEXT)
        val segments = buildTranscriptionSegments(
            responseJson = responseJson,
            transcript = transcript,
            provider = provider,
            transcriptionSegmentClass = transcriptionSegmentClass
        )

        ReflectionUtils.callMethod(
            onComplete,
            callback,
            fmessageObj,
            transcript,
            segments,
            TRANSCRIPTION_STATUS_SUCCESS
        )

        param.result = null
    }

    private fun getApiKey(provider: String): String {
        return when (provider) {
            PROVIDER_GROQ -> prefs.getString(PREF_GROQ_API_KEY, "").orEmpty()
            else -> prefs.getString(PREF_ASSEMBLY_AI_KEY, "").orEmpty()
        }
    }

    @Throws(Exception::class)
    private fun transcribeAudio(file: File, provider: String): JSONObject {
        return when (provider) {
            PROVIDER_GROQ -> transcriptionGroqAI(file)
            else -> transcriptionAssemblyAI(file)
        }
    }

    private fun buildTranscriptionSegments(
        responseJson: JSONObject,
        transcript: String,
        provider: String,
        transcriptionSegmentClass: Class<*>
    ): ArrayList<Any> {
        val segments = ArrayList<Any>()
        val wordsArray = responseJson.optJSONArray(JSON_WORDS) ?: return segments

        var currentPosition = 0

        for (index in 0 until wordsArray.length()) {
            val wordObject = wordsArray.getJSONObject(index)
            val wordText = wordObject.optString(JSON_TEXT, wordObject.optString(JSON_WORD))

            var startChar = transcript.indexOf(wordText, currentPosition)
            if (startChar == INDEX_NOT_FOUND) {
                startChar = currentPosition
            }

            val length = wordText.length
            val timing = getWordTiming(wordObject, provider)
            val duration = timing.endMs - timing.startMs
            val safeDuration = if (duration < 100) 100 else duration

            val segment = XposedHelpers.newInstance(
                transcriptionSegmentClass,
                startChar,
                length,
                DEFAULT_CONFIDENCE,
                timing.startMs,
                safeDuration
            )

            segments.add(segment)
            currentPosition = startChar + length
        }

        return segments
    }

    private fun getWordTiming(wordObject: JSONObject, provider: String): WordTiming {
        return if (provider == PROVIDER_GROQ) {
            WordTiming(
                startMs = (wordObject.getDouble(JSON_START) * MILLISECONDS_IN_SECOND).toInt(),
                endMs = (wordObject.getDouble(JSON_END) * MILLISECONDS_IN_SECOND).toInt()
            )
        } else {
            WordTiming(
                startMs = wordObject.getLong(JSON_START).toInt(),
                endMs = wordObject.getLong(JSON_END).toInt()
            )
        }
    }

    @Throws(Exception::class)
    private fun transcriptionAssemblyAI(fileOpus: File): JSONObject {
        val apiKey = prefs.getString(PREF_ASSEMBLY_AI_KEY, "").orEmpty()

        if (TextUtils.isEmpty(apiKey)) {
            throw Exception("API key not provided")
        }

        val uploadResult = uploadAudioToAssemblyAI(fileOpus, apiKey)
        val audioUrl = uploadResult.getString(JSON_UPLOAD_URL)

        val transcribeResult = startAssemblyAITranscription(audioUrl, apiKey)
        val transcriptId = transcribeResult.getString(JSON_ID)

        return waitForAssemblyAITranscription(transcriptId, apiKey)
    }

    @Throws(Exception::class)
    private fun uploadAudioToAssemblyAI(fileOpus: File, apiKey: String): JSONObject {
        val requestBody = fileOpus.asRequestBody(MEDIA_TYPE_OCTET_STREAM.toMediaType())

        val uploadRequest = Request.Builder()
            .url(ASSEMBLY_AI_UPLOAD_URL)
            .addHeader(HEADER_AUTHORIZATION, apiKey)
            .post(requestBody)
            .build()

        httpClient.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to upload audio: ${response.code}")
            }

            return JSONObject(response.body.string())
        }
    }

    @Throws(Exception::class)
    private fun startAssemblyAITranscription(audioUrl: String, apiKey: String): JSONObject {
        val transcriptionJson = JSONObject()
            .put(JSON_AUDIO_URL, audioUrl)
            .put(JSON_LANGUAGE_DETECTION, true)

        val requestBody =
            transcriptionJson.toString()
                .toRequestBody(MEDIA_TYPE_JSON.toMediaType())

        val transcribeRequest = Request.Builder()
            .url(ASSEMBLY_AI_TRANSCRIPT_URL)
            .addHeader(HEADER_AUTHORIZATION, apiKey)
            .addHeader(HEADER_CONTENT_TYPE, MEDIA_TYPE_JSON)
            .post(requestBody)
            .build()

        httpClient.newCall(transcribeRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to start transcription: ${response.code}")
            }

            return JSONObject(response.body.string())
        }
    }

    @Throws(Exception::class)
    private fun waitForAssemblyAITranscription(
        transcriptId: String,
        apiKey: String
    ): JSONObject {
        var status = STATUS_PROCESSING

        while (status == STATUS_PROCESSING || status == STATUS_QUEUED) {
            Thread.sleep(POLLING_DELAY_MS)

            val checkResult = checkAssemblyAITranscriptionStatus(transcriptId, apiKey)
            status = checkResult.getString(JSON_STATUS)

            when (status) {
                STATUS_COMPLETED -> return checkResult
                STATUS_ERROR -> {
                    val error = checkResult.optString(JSON_ERROR, "Unknown error")
                    throw Exception("Transcription error: $error")
                }
            }
        }

        throw Exception("Transcription failed")
    }

    @Throws(Exception::class)
    private fun checkAssemblyAITranscriptionStatus(
        transcriptId: String,
        apiKey: String
    ): JSONObject {
        val checkRequest = Request.Builder()
            .url("$ASSEMBLY_AI_TRANSCRIPT_URL/$transcriptId")
            .addHeader(HEADER_AUTHORIZATION, apiKey)
            .build()

        httpClient.newCall(checkRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to check transcription status: ${response.code}")
            }

            return JSONObject(response.body.string())
        }
    }

    @Throws(Exception::class)
    private fun transcriptionGroqAI(fileAudio: File): JSONObject {
        val apiKey = prefs.getString(PREF_GROQ_API_KEY, "").orEmpty()

        if (TextUtils.isEmpty(apiKey)) {
            throw Exception("Groq API key not provided")
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                FORM_FILE,
                fileAudio.name,
                fileAudio.asRequestBody(MEDIA_TYPE_AUDIO_OGG.toMediaType())
            )
            .addFormDataPart(FORM_MODEL, GROQ_MODEL)
            .addFormDataPart(FORM_RESPONSE_FORMAT, GROQ_RESPONSE_FORMAT_VERBOSE_JSON)
            .addFormDataPart(FORM_TIMESTAMP_GRANULARITIES, GROQ_TIMESTAMP_GRANULARITY_WORD)
            .addFormDataPart(FORM_TEMPERATURE, GROQ_TEMPERATURE)
            .build()

        val transcribeRequest = Request.Builder()
            .url(GROQ_TRANSCRIPTION_URL)
            .addHeader(HEADER_AUTHORIZATION, "Bearer $apiKey")
            .post(requestBody)
            .build()

        httpClient.newCall(transcribeRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception(
                    "Failed to transcribe audio: ${response.code} - ${response.message}"
                )
            }

            return JSONObject(response.body.string())
        }
    }

    override fun getPluginName(): String {
        return "Audio Transcript"
    }

    private data class WordTiming(
        val startMs: Int,
        val endMs: Int
    )

    private companion object {
        private const val PREF_AUDIO_TRANSCRIPTION = "audio_transcription"
        private const val PREF_TRANSCRIPTION_PROVIDER = "transcription_provider"
        private const val PREF_GROQ_API_KEY = "groq_api_key"
        private const val PREF_ASSEMBLY_AI_KEY = "assemblyai_key"

        private const val PROVIDER_GROQ = "groq"
        private const val PROVIDER_ASSEMBLY_AI = "assemblyai"

        private const val ASSEMBLY_AI_UPLOAD_URL = "https://api.assemblyai.com/v2/upload"
        private const val ASSEMBLY_AI_TRANSCRIPT_URL = "https://api.assemblyai.com/v2/transcript"
        private const val GROQ_TRANSCRIPTION_URL =
            "https://api.groq.com/openai/v1/audio/transcriptions"

        private const val MEDIA_TYPE_OCTET_STREAM = "application/octet-stream"
        private const val MEDIA_TYPE_JSON = "application/json"
        private const val MEDIA_TYPE_AUDIO_OGG = "audio/ogg"

        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_CONTENT_TYPE = "Content-Type"

        private const val FORM_FILE = "file"
        private const val FORM_MODEL = "model"
        private const val FORM_RESPONSE_FORMAT = "response_format"
        private const val FORM_TIMESTAMP_GRANULARITIES = "timestamp_granularities[]"
        private const val FORM_TEMPERATURE = "temperature"

        private const val GROQ_MODEL = "whisper-large-v3-turbo"
        private const val GROQ_RESPONSE_FORMAT_VERBOSE_JSON = "verbose_json"
        private const val GROQ_TIMESTAMP_GRANULARITY_WORD = "word"
        private const val GROQ_TEMPERATURE = "0"

        private const val JSON_TEXT = "text"
        private const val JSON_WORD = "word"
        private const val JSON_WORDS = "words"
        private const val JSON_START = "start"
        private const val JSON_END = "end"
        private const val JSON_UPLOAD_URL = "upload_url"
        private const val JSON_AUDIO_URL = "audio_url"
        private const val JSON_LANGUAGE_DETECTION = "language_detection"
        private const val JSON_ID = "id"
        private const val JSON_STATUS = "status"
        private const val JSON_ERROR = "error"

        private const val STATUS_PROCESSING = "processing"
        private const val STATUS_QUEUED = "queued"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_ERROR = "error"

        private const val TOAST_SHORT = 1
        private const val DEFAULT_CONFIDENCE = 100
        private const val INDEX_NOT_FOUND = -1
        private const val ON_COMPLETE_PARAMETER_COUNT = 4
        private const val TRANSCRIPTION_STATUS_SUCCESS = 1
        private const val MILLISECONDS_IN_SECOND = 1000
        private const val POLLING_DELAY_MS = 1000L
    }
}