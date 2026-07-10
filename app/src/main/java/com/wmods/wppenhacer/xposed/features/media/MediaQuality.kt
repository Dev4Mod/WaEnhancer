package com.wmods.wppenhacer.xposed.features.media

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.RecordingCanvas
import android.os.Build
import androidx.core.content.edit
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.features.general.Others
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field

class MediaQuality(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {


    override fun doHook() {
        val videoQuality = prefs.getBoolean("videoquality", false)
        val imageQuality = prefs.getBoolean("imagequality", false)
        val maxSize = kotlin.math.max(prefs.getFloat("video_limit_size", 60f).toInt(), 90)

        // Disable manual calculation ProcessMediaQuality
        Others.propsBoolean[14447] = false

        // Enable Media Quality selection for Stories
        enableMediaQualityForStories()

        if (videoQuality) {
            Others.propsBoolean[5549] = true

            val processVideoQualityClass = Unobfuscator.loadProcessVideoQualityClass(classLoader)
            val processVideoQualityFields = Unobfuscator.getAllMapFields(processVideoQualityClass)

            XposedBridge.hookAllConstructors(processVideoQualityClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val processVideoQuality = param.thisObject
                    val fieldvideoMaxBitrate = processVideoQualityFields["videoMaxBitrate"]
                    val fieldvideoMaxEdge = processVideoQualityFields["videoMaxEdge"]
                    val fieldvideoLimitMb = processVideoQualityFields["videoLimitMb"]

                    fieldvideoLimitMb?.setInt(processVideoQuality, maxSize)
                    fieldvideoMaxEdge?.setInt(processVideoQuality, 3840)
                    if (fieldvideoMaxBitrate != null) {
                        val bitrateBps = 24000 * 1000
                        fieldvideoMaxBitrate.setInt(processVideoQuality, bitrateBps)
                    }
                }
            })

            val mediaDataVideoConfiguration =
                Unobfuscator.loadMediaDataVideoConfigurationClass(classLoader)
            val fieldsMediaDataVideoConfiguration =
                Unobfuscator.getAllMapFields(mediaDataVideoConfiguration)

            val videoTranscoderStart = Unobfuscator.loadVideoTranscoderStartMethod(classLoader)
            XposedBridge.hookMethod(videoTranscoderStart, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val videoProcessor = param.args[0]
                    val booleanParams = ReflectionUtils.getFieldsByType(
                        videoProcessor.javaClass,
                        java.lang.Boolean.TYPE
                    )
                    if (booleanParams.size > 2) {
                        val field: Field = booleanParams[2]
                        field.setBoolean(videoProcessor, false)
                    }
                    val fieldMediaDataVideoConfiguration = ReflectionUtils.getFieldByType(
                        videoProcessor.javaClass,
                        mediaDataVideoConfiguration
                    )
                    val mediaDataVideoConfigObj =
                        fieldMediaDataVideoConfiguration!!.get(videoProcessor)
                    val fieldforceSingleTranscoding =
                        fieldsMediaDataVideoConfiguration["forceSingleTranscoding"]
                    fieldforceSingleTranscoding?.setBoolean(mediaDataVideoConfigObj, true)
                }
            })

            Others.propsBoolean[18888] = true
            XposedBridge.hookMethod(
                Unobfuscator.loadMediaTranscoderStart(classLoader),
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val processSpec = param.args[0] ?: return
                        val booleanField = processSpec.javaClass.declaredFields.first {
                            it.type == Boolean::class.javaPrimitiveType
                        }
                        booleanField.isAccessible = true
                        booleanField.set(
                            processSpec,
                            true
                        )
                    }
                })

            Others.propsBoolean[5549] = true
            listOf(594, 12852).forEach { Others.propsInteger[it] = 3840 }
            listOf(4686, 3654, 3183, 4685).forEach { Others.propsInteger[it] = 3840 }
            listOf(3755, 3756, 3757, 3758).forEach { Others.propsInteger[it] = 24000 * 1000 }

        }

        if (imageQuality) {
            val processImageQualityClass = Unobfuscator.loadProcessImageQualityClass(classLoader)
            val fieldsProcessImageQuality = Unobfuscator.getAllMapFields(processImageQualityClass)

            XposedBridge.hookAllConstructors(processImageQualityClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val processImageQuality = param.thisObject
                    val fieldimageMaxSize = fieldsProcessImageQuality["maxKb"]
                    val fieldimageMaxQuality = fieldsProcessImageQuality["quality"]
                    val fieldimageMaxEdge = fieldsProcessImageQuality["maxEdge"]

                    fieldimageMaxSize?.setInt(processImageQuality, 50 * 1024)
                    fieldimageMaxQuality?.setInt(processImageQuality, 100)
                    fieldimageMaxEdge?.setInt(processImageQuality, 6000)
                }
            })

            val maxKb = 50 * 1024
            listOf(1577, 6030, 2656, 15752, 15746).forEach { Others.propsInteger[it] = maxKb }
            listOf(1581, 1575, 1578, 6029, 2655, 15749, 2655).forEach {
                Others.propsInteger[it] = 100
            }
            Others.propsBoolean[6033] = true
            Others.propsBoolean[9569] = false
            Others.propsBoolean[26289] = true
            Others.propsBoolean[26291] = true
            Others.propsBoolean[22375] = true
            listOf(1576, 2654, 6032, 15748, 3068).forEach { Others.propsInteger[it] = 3840 }

            // Prevent crashes in Media preview
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                XposedHelpers.findAndHookMethod(
                    RecordingCanvas::class.java,
                    "throwIfCannotDraw",
                    Bitmap::class.java,
                    XC_MethodReplacement.DO_NOTHING
                )
            }
        }
    }

    private fun enableMediaQualityForStories() {
        val prefs = UnobfuscatorCache.getInstance().sPrefsCacheHooks
        var legacyQualitySelection = prefs.getInt("legacy_quality_selection", -1)

        if (legacyQualitySelection != 0) {
            try {
                val hookMediaQualitySelection =
                    Unobfuscator.loadMediaQualitySelectionMethod(classLoader)
                XposedBridge.hookMethod(
                    hookMediaQualitySelection,
                    XC_MethodReplacement.returnConstant(true)
                )
                legacyQualitySelection = 1
            } catch (_: Exception) {
                legacyQualitySelection = 0
            }
        }

        if (legacyQualitySelection != 1) {
            val bottomBarConfigClass = Unobfuscator.loadBottomBarConfigClass(classLoader)
            val fieldsBottomBarConfig = Unobfuscator.getAllMapFields(bottomBarConfigClass)
            XposedBridge.hookAllConstructors(bottomBarConfigClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val supportsHdQuality = fieldsBottomBarConfig["supportsHdQuality"]
                    supportsHdQuality?.set(param.thisObject, true)
                }
            })
            legacyQualitySelection = 0
        }
        prefs.edit(commit = true) {
            putInt("legacy_quality_selection", legacyQualitySelection)
        }
    }

    override fun getPluginName(): String {
        return "Media Quality"
    }
}