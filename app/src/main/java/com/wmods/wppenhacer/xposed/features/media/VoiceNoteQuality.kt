package com.wmods.wppenhacer.xposed.features.media

import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Field
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class VoiceNoteQuality(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun getPluginName(): String = "Voice Note Quality"

    override fun doHook() {
        val overrides = configOverrides(
            xprefs.getBoolean("voice_note_high_quality", false),
            xprefs.getString("voice_note_noise_level", NOISE_DEFAULT) ?: NOISE_DEFAULT
        )
        if (overrides.isEmpty()) return

        val configClass = runCatching {
            classLoader.loadClass(CONFIG_CLASS)
        }.getOrElse {
            Unobfuscator.findFirstClassUsingName(
                classLoader,
                StringMatchType.EndsWith,
                ".OpusRecorderConfig"
            )
        }
        val fields = ConcurrentHashMap<String, Optional<Field>>()

        configClass.declaredConstructors.forEach { constructor ->
            constructor.hook {
                after {
                    val config = instance
                    logDebug("Voice note config from WhatsApp: $config")
                    overrides.forEach { (name, value) ->
                        val field = fields.computeIfAbsent(name) { findField(configClass, name) }
                            .orElse(null) ?: return@forEach
                        runCatching { set(field, config, value) }
                    }
                    logDebug("Voice note config sent to the recorder: $config")
                }
            }
        }
    }

    private fun findField(configClass: Class<*>, name: String): Optional<Field> =
        Optional.ofNullable(runCatching { configClass.getDeclaredField(name) }
            .onFailure { logDebug("OpusRecorderConfig has no field $name") }
            .getOrNull()?.apply { isAccessible = true })

    private fun configOverrides(highQuality: Boolean, noiseLevel: String): List<Pair<String, Any>> {
        val quality = if (!highQuality) emptyList() else listOf(
            "sampleRate" to SAMPLE_RATE,
            "opusTargetBitrateBps" to TARGET_BITRATE_BPS,
            "opusNonSpeechBitrateBps" to NON_SPEECH_BITRATE_BPS,
            "opusMaxBandwidth" to MAX_BANDWIDTH,
            "opusComplexity" to COMPLEXITY,
            "enableOpusDtx" to ENABLE_DTX
        )
        val noise = when (noiseLevel) {
            NOISE_DEFAULT -> emptyList()
            NOISE_OFF -> listOf("enableNoiseSuppression" to false)
            else -> noiseLevel.toFloatOrNull()?.let { intensity ->
                listOf(
                    "enableNoiseSuppression" to true,
                    "noiseSuppressionProcessingIntensity" to intensity.coerceIn(0f, 1f)
                )
            }.orEmpty()
        }
        return quality + noise
    }

    private fun set(field: Field, target: Any, value: Any) {
        when (value) {
            is Int -> field.setInt(target, value)
            is Boolean -> field.setBoolean(target, value)
            is Float -> field.setFloat(target, value)
        }
    }

    companion object {
        private const val CONFIG_CLASS =
            "com.whatsapp.infra.media.audioRecording.OpusRecorderConfig"
        private const val SAMPLE_RATE = 48000
        private const val TARGET_BITRATE_BPS = 64000
        private const val NON_SPEECH_BITRATE_BPS = 64000
        private const val MAX_BANDWIDTH = 1105
        private const val COMPLEXITY = 10
        private const val ENABLE_DTX = true
        private const val NOISE_DEFAULT = "default"
        private const val NOISE_OFF = "off"
    }
}
