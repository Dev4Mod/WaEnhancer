package com.wmods.wppenhacer.xposed.features.media

import android.content.SharedPreferences
import android.media.MediaExtractor
import android.media.MediaFormat
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import java.io.File

class UnsupportedAudioTranscode(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val AUDIO_AAC = "audio/mp4a-latm"
        private val SUPPORTED_AAC_OBJECT_TYPES = setOf(1, 2, 5, 29)
    }

    override fun doHook() {
        val paramsClass = Unobfuscator.loadVideoUploadParamsClass(classLoader)
        val fields = Unobfuscator.loadVideoUploadParamsFields(classLoader)
        val shouldTranscodeAudioField = fields["shouldTranscodeAudio"] ?: return
        val inputFileField = fields["inputFile"]?.takeIf { it.type == File::class.java } ?: return
        val skipAudioField = fields["isSkipAudioTrack"]
        shouldTranscodeAudioField.isAccessible = true
        inputFileField.isAccessible = true
        skipAudioField?.isAccessible = true

        paramsClass.resolve().constructor { }.hookAll().after {
            if (shouldTranscodeAudioField.getBoolean(instance)) return@after
            if (skipAudioField?.getBoolean(instance) == true) return@after
            val input = inputFileField.get(instance) as? File ?: return@after
            val objectType = readUnsupportedAacObjectType(input) ?: return@after
            logDebug("Forcing audio transcode for AAC object type $objectType: ${input.name}")
            shouldTranscodeAudioField.setBoolean(instance, true)
        }
    }

    private fun readUnsupportedAacObjectType(file: File): Int? {
        if (!file.exists() || file.length() == 0L) return null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                if (mime != AUDIO_AAC) return null
                val objectType = readAacObjectType(format) ?: return null
                return objectType.takeIf { it !in SUPPORTED_AAC_OBJECT_TYPES }
            }
            return null
        } catch (e: Exception) {
            logDebug("Failed to probe ${file.name}", e)
            return null
        } finally {
            extractor.release()
        }
    }

    private fun readAacObjectType(format: MediaFormat): Int? {
        val csd = format.getByteBuffer("csd-0")?.duplicate()
        if (csd != null && csd.remaining() >= 1) {
            val b0 = csd.get().toInt() and 0xFF
            val objectType = b0 ushr 3
            if (objectType != 31) return objectType
            if (csd.remaining() < 1) return null
            val b1 = csd.get().toInt() and 0xFF
            return 32 + (((b0 and 0x07) shl 3) or (b1 ushr 5))
        }
        if (!format.containsKey(MediaFormat.KEY_AAC_PROFILE)) return null
        return runCatching { format.getInteger(MediaFormat.KEY_AAC_PROFILE) }.getOrNull()
    }

    override fun getPluginName(): String = "Unsupported Audio Transcode"
}
