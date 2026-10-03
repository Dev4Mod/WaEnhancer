package com.wmods.wppenhacer.xposed.features.media

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.RecordingCanvas
import android.media.MediaCodecInfo
import android.os.Build
import androidx.core.content.edit
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.features.general.Others
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import java.lang.reflect.Field

class MediaQuality(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        const val EDGE_WIDTH = 1920
        const val BITRATE = 10_000
    }

    override fun doHook() {
        val videoQuality = xprefs.getBoolean("videoquality", false)
        val imageQuality = xprefs.getBoolean("imagequality", false)
        val maxSize = kotlin.math.max(xprefs.getFloat("video_limit_size", 60f).toInt(), 90)

        // Disable manual calculation ProcessMediaQuality
        Others.propsBoolean[14447] = false

        // Enable Media Quality selection for Stories
        enableMediaQualityForStories()

        if (videoQuality) {
            Others.propsBoolean[5549] = true

            val processVideoQualityClass = Unobfuscator.loadProcessVideoQualityClass(classLoader)
            val fieldsVideoQuality = Unobfuscator.getAllMapFields(processVideoQualityClass)

            fieldsVideoQuality.keys.forEach {
                fieldsVideoQuality[it]?.isAccessible = true
            }

            processVideoQualityClass.resolve().constructor { }.hookAll {
                after {
                    val instance = instance
                    fieldsVideoQuality["videoLimitMb"]?.setInt(instance, maxSize)
                    fieldsVideoQuality["videoMaxEdge"]?.setInt(instance, EDGE_WIDTH)
                    fieldsVideoQuality["videoMaxBitrate"]?.setInt(instance, BITRATE * 1000)
                    fieldsVideoQuality["mainHighBitRate"]?.set(instance, null)
                    fieldsVideoQuality["videoBitrateMode"]?.set(
                        instance,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    )
                }
            }

            val mediaDataVideoConfiguration =
                Unobfuscator.loadMediaDataVideoConfigurationClass(classLoader)
            val fieldsMediaDataVideoConfiguration =
                Unobfuscator.getAllMapFields(mediaDataVideoConfiguration)

            val videoTranscoderStart = Unobfuscator.loadVideoTranscoderStartMethod(classLoader)
            videoTranscoderStart.hook {
                before {
                    val videoProcessor = args[0]
                    val booleanParams = ReflectionUtils.getFieldsByType(
                        videoProcessor!!.javaClass,
                        java.lang.Boolean.TYPE
                    )
                    if (booleanParams.size > 2) {
                        val field: Field = booleanParams[2]
                        field.setBoolean(videoProcessor, false)
                    }
                    val fieldMediaDataVideoConfiguration = ReflectionUtils.getFieldByType(
                        videoProcessor!!.javaClass,
                        mediaDataVideoConfiguration
                    )
                    val mediaDataVideoConfigObj =
                        fieldMediaDataVideoConfiguration!!.get(videoProcessor)
                    val fieldforceSingleTranscoding =
                        fieldsMediaDataVideoConfiguration["forceSingleTranscoding"]
                    fieldforceSingleTranscoding?.setBoolean(mediaDataVideoConfigObj, true)
                }
            }

            Others.propsBoolean[18888] = true
            Unobfuscator.loadMediaTranscoderStart(classLoader).hook {
                before {
                    val processSpec = args[0] ?: return@before
                    val booleanField = processSpec.javaClass.declaredFields.first {
                        it.type == Boolean::class.javaPrimitiveType
                    }
                    booleanField.isAccessible = true
                    booleanField.set(
                        processSpec,
                        true
                    )
                }
            }

            Others.propsBoolean[5549] = true
            listOf(594, 12852).forEach { Others.propsInteger[it] = EDGE_WIDTH }
            listOf(4686, 3654, 3183, 4685).forEach { Others.propsInteger[it] = EDGE_WIDTH }
            listOf(3755, 3756, 3757, 3758).forEach { Others.propsInteger[it] = BITRATE }
        }

        if (imageQuality) {
            val processImageQualityClass = Unobfuscator.loadProcessImageQualityClass(classLoader)
            val fieldsProcessImageQuality = Unobfuscator.getAllMapFields(processImageQualityClass)

            processImageQualityClass.resolve().constructor { }.hookAll {
                after {
                    val processImageQuality = instance
                    val fieldimageMaxSize = fieldsProcessImageQuality["maxKb"]
                    val fieldimageMaxQuality = fieldsProcessImageQuality["quality"]
                    val fieldimageMaxEdge = fieldsProcessImageQuality["maxEdge"]

                    fieldimageMaxSize?.setInt(processImageQuality, 50 * 1024)
                    fieldimageMaxQuality?.setInt(processImageQuality, 100)
                    fieldimageMaxEdge?.setInt(processImageQuality, 6000)
                }
            }

            val maxKb = 50 * 1024
            listOf(1577, 6030, 2656, 15752, 15746).forEach { Others.propsInteger[it] = maxKb }
            listOf(1581, 1575, 1578, 6029, 2655, 15749, 2655).forEach {
                Others.propsInteger[it] = 100
            }
            Others.propsBoolean[6033] = true
            Others.propsBoolean[9569] = false
            Others.propsBoolean[26289] = true
            Others.propsBoolean[22375] = true
            listOf(1576, 2654, 6032, 15748, 3068).forEach { Others.propsInteger[it] = 3840 }

            // Prevent crashes in Media preview
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RecordingCanvas::class.java.resolve().firstMethod {
                    name = "throwIfCannotDraw"
                    superclass()
                    parameters(Bitmap::class.java)
                }.hook {
                    replaceUnit { }
                }
            }
        }
    }

    private fun enableMediaQualityForStories() {
        val xprefs = UnobfuscatorCache.getInstance().sPrefsCacheHooks
        var legacyQualitySelection = xprefs.getInt("legacy_quality_selection", -1)

        if (legacyQualitySelection != 0) {
            try {
                val hookMediaQualitySelection =
                    Unobfuscator.loadMediaQualitySelectionMethod(classLoader)
                hookMediaQualitySelection.hook {
                    replaceAny { true }
                }
                legacyQualitySelection = 1
            } catch (_: Exception) {
                legacyQualitySelection = 0
            }
        }

        if (legacyQualitySelection != 1) {
            val bottomBarConfigClass = Unobfuscator.loadBottomBarConfigClass(classLoader)
            val fieldsBottomBarConfig = Unobfuscator.getAllMapFields(bottomBarConfigClass)
            bottomBarConfigClass.resolve().constructor { }.hookAll {
                after {
                    val supportsHdQuality = fieldsBottomBarConfig["supportsHdQuality"]
                    supportsHdQuality?.set(instance, true)
                }
            }
            legacyQualitySelection = 0
        }
        xprefs.edit(commit = true) {
            putInt("legacy_quality_selection", legacyQualitySelection)
        }
    }

    override fun getPluginName(): String {
        return "Media Quality"
    }
}
