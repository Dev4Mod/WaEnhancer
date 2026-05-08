package com.wmods.wppenhacer.xposed.features.media

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.RecordingCanvas
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Pair
import androidx.core.content.edit
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.features.general.Others
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import java.lang.reflect.Field

class MediaQuality(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val VIDEO_MIME_AVC = "video/avc"
        private const val VIDEO_MIME_HEVC = "video/hevc"
        private const val SAFE_MIN_VIDEO_DIMENSION = 2
        private const val SAFE_MAX_VIDEO_EDGE = 3840
        private const val FALLBACK_LANDSCAPE_WIDTH = 1280
        private const val FALLBACK_LANDSCAPE_HEIGHT = 720
    }

    private var cachedEncoderCapabilities: EncoderVideoCapabilities? = null

    override fun doHook() {
        val videoQuality = prefs.getBoolean("videoquality", false)
        val imageQuality = prefs.getBoolean("imagequality", false)
        val maxSize = kotlin.math.max(prefs.getFloat("video_limit_size", 60f).toInt(), 90)
        val realResolution = prefs.getBoolean("video_real_resolution", false)

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
                        fieldMediaDataVideoConfiguration.get(videoProcessor)
                    val fieldforceSingleTranscoding =
                        fieldsMediaDataVideoConfiguration["forceSingleTranscoding"]
                    fieldforceSingleTranscoding?.setBoolean(mediaDataVideoConfigObj, true)
                }
            })

            val videoMethod = Unobfuscator.loadMediaQualityVideoMethod2(classLoader)
            logDebug(Unobfuscator.getMethodDescriptor(videoMethod))

            val mediaFields = Unobfuscator.loadMediaQualityOriginalVideoFields(classLoader)
            val mediaTranscodeParams = Unobfuscator.loadMediaQualityVideoFields(classLoader)

            XposedBridge.hookMethod(videoMethod, object : XC_MethodHook() {
                @Suppress("UNCHECKED_CAST")
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (cachedEncoderCapabilities == null) {
                        cachedEncoderCapabilities = loadCapabilitiesFromCache()
                        if (cachedEncoderCapabilities == null) {
                            cachedEncoderCapabilities =
                                getPreferredEncoderCapabilities(VIDEO_MIME_AVC)
                                    ?: getPreferredEncoderCapabilities(VIDEO_MIME_HEVC)
                            saveCapabilitiesToCache(cachedEncoderCapabilities)
                        }
                    }
                    val finalEncoderVideoCapabilities = cachedEncoderCapabilities

                    val resizeVideo = param.result
                    val isHighResolution: Boolean
                    var isEnum = false
                    val enumObj = ReflectionUtils.getArg(param.args, Enum::class.java, 0)
                    val intParams = ReflectionUtils.findInstancesOfType(
                        param.args,
                        Integer::class.java
                    )

                    if (enumObj != null) {
                        isEnum = true
                        val enumClass = enumObj.javaClass
                        val hightResolution = java.lang.Enum.valueOf(
                            enumClass as Class<out Enum<*>?>,
                            "RESOLUTION_1080P"
                        )
                        isHighResolution = hightResolution == enumObj
                    } else {
                        isHighResolution = param.args[1] as Int == 3
                    }

                    if (isHighResolution && realResolution) {
                        val width: Int
                        val height: Int
                        val rotationAngle: Int

                        if (mediaFields.isEmpty()) {
                            if (isEnum) {
                                width = (intParams[intParams.size - 3] as Pair<*, Int>).second
                                height = (intParams[intParams.size - 2] as Pair<*, Int>).second
                                rotationAngle =
                                    (intParams[intParams.size - 1] as Pair<*, Int>).second
                            } else {
                                val mediaFieldsObj =
                                    XposedHelpers.callMethod(param.args[0], "A00") as JSONObject
                                width = mediaFieldsObj.getInt("widthPx")
                                height = mediaFieldsObj.getInt("heightPx")
                                rotationAngle = mediaFieldsObj.getInt("rotationAngle")
                            }
                        } else {
                            width = mediaFields["widthPx"]!!.getInt(param.args[0])
                            height = mediaFields["heightPx"]!!.getInt(param.args[0])
                            rotationAngle = mediaFields["rotationAngle"]!!.getInt(param.args[0])
                        }

                        val targetWidthField = mediaTranscodeParams["targetWidth"]
                        val targetHeightField = mediaTranscodeParams["targetHeight"]

                        val inverted = rotationAngle == 90 || rotationAngle == 270

                        val targetWidth = if (inverted) height else width
                        val targetHeight = if (inverted) width else height

                        val sanitizedTargetSize = sanitizeVideoSize(
                            targetWidth,
                            targetHeight,
                            finalEncoderVideoCapabilities
                        )
                        if (sanitizedTargetSize != null) {
                            targetWidthField?.setInt(resizeVideo, sanitizedTargetSize.first)
                            targetHeightField?.setInt(resizeVideo, sanitizedTargetSize.second)
                        }
                    }

                    if (prefs.getBoolean("video_maxfps", false)) {
                        val frameRateField = mediaTranscodeParams["frameRate"]
                        frameRateField?.setInt(resizeVideo, 60)
                    }
                }
            })
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
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val supportsHdQuality = fieldsBottomBarConfig?.get("supportsHdQuality")
                    supportsHdQuality?.set(param.thisObject, true)
                }
            })
            legacyQualitySelection = 0
        }
        prefs.edit(commit = true) {
            putInt("legacy_quality_selection", legacyQualitySelection)
        }
    }


    @SuppressLint("ApplySharedPref")
    private fun saveCapabilitiesToCache(caps: EncoderVideoCapabilities?) {
        if (caps == null) return
        WppCore.getPrivPrefs().edit(commit = true) {
            putString("codecName", caps.codecName)
                .putInt("minWidth", caps.minWidth)
                .putInt("maxWidth", caps.maxWidth)
                .putInt("minHeight", caps.minHeight)
                .putInt("maxHeight", caps.maxHeight)
                .putInt("widthAlignment", caps.widthAlignment)
                .putInt("heightAlignment", caps.heightAlignment)
                .putInt("maxBitrateKbps", caps.maxBitrateKbps)
        }
    }

    private fun loadCapabilitiesFromCache(): EncoderVideoCapabilities? {
        val prefs = WppCore.getPrivPrefs()
        val codecName = prefs.getString("codecName", null) ?: return null

        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (info.name == codecName && info.isEncoder) {
                val mimeType = if (info.supportedTypes.contains(VIDEO_MIME_AVC)) {
                    VIDEO_MIME_AVC
                } else if (info.supportedTypes.contains(VIDEO_MIME_HEVC)) {
                    VIDEO_MIME_HEVC
                } else {
                    null
                }

                if (mimeType != null) {
                    try {
                        val videoCaps = info.getCapabilitiesForType(mimeType).videoCapabilities
                        if (videoCaps != null) {
                            return EncoderVideoCapabilities(
                                codecName = codecName,
                                minWidth = prefs.getInt("minWidth", SAFE_MIN_VIDEO_DIMENSION),
                                maxWidth = prefs.getInt("maxWidth", SAFE_MAX_VIDEO_EDGE),
                                minHeight = prefs.getInt("minHeight", SAFE_MIN_VIDEO_DIMENSION),
                                maxHeight = prefs.getInt("maxHeight", SAFE_MAX_VIDEO_EDGE),
                                widthAlignment = prefs.getInt("widthAlignment", 2),
                                heightAlignment = prefs.getInt("heightAlignment", 2),
                                maxBitrateKbps = prefs.getInt("maxBitrateKbps", 2000),
                                videoCapabilities = videoCaps
                            )
                        }
                    } catch (e: Exception) {
                        logDebug("Failed to load video caps from cache for codec $codecName", e)
                    }
                }
                break
            }
        }
        return null
    }


    private fun getPreferredEncoderCapabilities(mimeType: String): EncoderVideoCapabilities? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codecInfos = codecList.codecInfos

        var fallback: EncoderVideoCapabilities? = null
        for (info in codecInfos) {
            if (!info.isEncoder) continue

            for (type in info.supportedTypes) {
                if (!type.equals(mimeType, ignoreCase = true)) continue

                try {
                    val caps = info.getCapabilitiesForType(type)
                    val videoCaps = caps.videoCapabilities ?: continue

                    val minWidth =
                        kotlin.math.max(SAFE_MIN_VIDEO_DIMENSION, videoCaps.supportedWidths.lower)
                    val maxWidth = kotlin.math.max(minWidth, videoCaps.supportedWidths.upper)
                    val minHeight =
                        kotlin.math.max(SAFE_MIN_VIDEO_DIMENSION, videoCaps.supportedHeights.lower)
                    val maxHeight = kotlin.math.max(minHeight, videoCaps.supportedHeights.upper)
                    val widthAlignment = kotlin.math.max(2, videoCaps.widthAlignment)
                    val heightAlignment = kotlin.math.max(2, videoCaps.heightAlignment)
                    val maxBitrateKbps = kotlin.math.max(2000, videoCaps.bitrateRange.upper / 1000)

                    val candidate = EncoderVideoCapabilities(
                        info.name,
                        minWidth,
                        maxWidth,
                        minHeight,
                        maxHeight,
                        widthAlignment,
                        heightAlignment,
                        maxBitrateKbps,
                        videoCaps
                    )

                    if (fallback == null) {
                        fallback = candidate
                    }

                    if (!isSoftwareCodec(info.name)) {
                        return candidate
                    }
                } catch (e: Exception) {
                    logDebug("Failed to read encoder capabilities", e)
                }
                break
            }
        }

        return fallback
    }

    private fun isSoftwareCodec(codecName: String?): Boolean {
        if (codecName.isNullOrEmpty()) return true
        val lowerName = codecName.lowercase()
        return lowerName.startsWith("omx.google.") ||
                lowerName.startsWith("c2.android.") ||
                lowerName.contains("software") ||
                lowerName.contains(".sw.")
    }

    private data class EncoderVideoCapabilities(
        val codecName: String,
        val minWidth: Int,
        val maxWidth: Int,
        val minHeight: Int,
        val maxHeight: Int,
        val widthAlignment: Int,
        val heightAlignment: Int,
        val maxBitrateKbps: Int,
        val videoCapabilities: MediaCodecInfo.VideoCapabilities
    )

    private fun clampInt(value: Int, min: Int, max: Int): Int {
        return kotlin.math.max(min, kotlin.math.min(value, max))
    }

    private fun sanitizeVideoSize(
        width: Int,
        height: Int,
        encoderVideoCapabilities: EncoderVideoCapabilities?
    ): Pair<Int, Int>? {
        if (width <= 0 || height <= 0) return null

        val boundedSize = fitToMaxEdge(width, height, SAFE_MAX_VIDEO_EDGE)
        var safeWidth = boundedSize.first
        var safeHeight = boundedSize.second

        if (encoderVideoCapabilities == null) {
            val fallbackSafeWidth = makeEven(safeWidth)
            val fallbackSafeHeight = makeEven(safeHeight)
            return Pair(
                kotlin.math.max(SAFE_MIN_VIDEO_DIMENSION, fallbackSafeWidth),
                kotlin.math.max(SAFE_MIN_VIDEO_DIMENSION, fallbackSafeHeight)
            )
        }

        safeWidth = clampInt(
            safeWidth,
            encoderVideoCapabilities.minWidth,
            encoderVideoCapabilities.maxWidth
        )
        safeHeight = clampInt(
            safeHeight,
            encoderVideoCapabilities.minHeight,
            encoderVideoCapabilities.maxHeight
        )

        safeWidth = alignToNearest(
            safeWidth,
            encoderVideoCapabilities.widthAlignment,
            encoderVideoCapabilities.minWidth,
            encoderVideoCapabilities.maxWidth
        )
        safeHeight = alignToNearest(
            safeHeight,
            encoderVideoCapabilities.heightAlignment,
            encoderVideoCapabilities.minHeight,
            encoderVideoCapabilities.maxHeight
        )

        val supportedSize = findSupportedSize(safeWidth, safeHeight, encoderVideoCapabilities)
        if (supportedSize != null) {
            return supportedSize
        }

        val fallbackWidth = alignToNearest(
            clampInt(
                kotlin.math.min(safeWidth, 1280),
                encoderVideoCapabilities.minWidth,
                encoderVideoCapabilities.maxWidth
            ),
            encoderVideoCapabilities.widthAlignment,
            encoderVideoCapabilities.minWidth,
            encoderVideoCapabilities.maxWidth
        )
        val fallbackHeight = alignToNearest(
            clampInt(
                kotlin.math.min(safeHeight, 720),
                encoderVideoCapabilities.minHeight,
                encoderVideoCapabilities.maxHeight
            ),
            encoderVideoCapabilities.heightAlignment,
            encoderVideoCapabilities.minHeight,
            encoderVideoCapabilities.maxHeight
        )

        if (isSizeSupported(encoderVideoCapabilities, fallbackWidth, fallbackHeight)) {
            return Pair(fallbackWidth, fallbackHeight)
        }

        val isLandscape = safeWidth >= safeHeight
        var conservativeWidth =
            if (isLandscape) FALLBACK_LANDSCAPE_WIDTH else FALLBACK_LANDSCAPE_HEIGHT
        var conservativeHeight =
            if (isLandscape) FALLBACK_LANDSCAPE_HEIGHT else FALLBACK_LANDSCAPE_WIDTH

        conservativeWidth = alignToNearest(
            clampInt(
                conservativeWidth,
                encoderVideoCapabilities.minWidth,
                encoderVideoCapabilities.maxWidth
            ),
            encoderVideoCapabilities.widthAlignment,
            encoderVideoCapabilities.minWidth,
            encoderVideoCapabilities.maxWidth
        )
        conservativeHeight = alignToNearest(
            clampInt(
                conservativeHeight,
                encoderVideoCapabilities.minHeight,
                encoderVideoCapabilities.maxHeight
            ),
            encoderVideoCapabilities.heightAlignment,
            encoderVideoCapabilities.minHeight,
            encoderVideoCapabilities.maxHeight
        )

        return Pair(conservativeWidth, conservativeHeight)
    }

    private fun findSupportedSize(
        width: Int,
        height: Int,
        encoderVideoCapabilities: EncoderVideoCapabilities
    ): Pair<Int, Int>? {
        if (isSizeSupported(encoderVideoCapabilities, width, height)) {
            return Pair(width, height)
        }

        val aspectRatio = width.toFloat() / height.toFloat()
        var currentWidth = width
        var currentHeight = height

        for (attempt in 0 until 80) {
            if (currentWidth >= currentHeight) {
                currentWidth = alignDown(
                    currentWidth - encoderVideoCapabilities.widthAlignment,
                    encoderVideoCapabilities.widthAlignment
                )
                if (currentWidth < encoderVideoCapabilities.minWidth) break

                val scaledHeight = kotlin.math.round(currentWidth / aspectRatio).toInt()
                currentHeight = alignToNearest(
                    clampInt(
                        scaledHeight,
                        encoderVideoCapabilities.minHeight,
                        encoderVideoCapabilities.maxHeight
                    ),
                    encoderVideoCapabilities.heightAlignment,
                    encoderVideoCapabilities.minHeight,
                    encoderVideoCapabilities.maxHeight
                )
            } else {
                currentHeight = alignDown(
                    currentHeight - encoderVideoCapabilities.heightAlignment,
                    encoderVideoCapabilities.heightAlignment
                )
                if (currentHeight < encoderVideoCapabilities.minHeight) break

                val scaledWidth = kotlin.math.round(currentHeight * aspectRatio).toInt()
                currentWidth = alignToNearest(
                    clampInt(
                        scaledWidth,
                        encoderVideoCapabilities.minWidth,
                        encoderVideoCapabilities.maxWidth
                    ),
                    encoderVideoCapabilities.widthAlignment,
                    encoderVideoCapabilities.minWidth,
                    encoderVideoCapabilities.maxWidth
                )
            }

            if (isSizeSupported(encoderVideoCapabilities, currentWidth, currentHeight)) {
                return Pair(currentWidth, currentHeight)
            }
        }

        return null
    }

    private fun isSizeSupported(
        encoderVideoCapabilities: EncoderVideoCapabilities,
        width: Int,
        height: Int
    ): Boolean {
        return try {
            encoderVideoCapabilities.videoCapabilities.isSizeSupported(width, height)
        } catch (ignored: Exception) {
            false
        }
    }

    private fun alignToNearest(value: Int, alignment: Int, min: Int, max: Int): Int {
        if (alignment <= 1) return clampInt(value, min, max)

        val minAligned = ((min + alignment - 1) / alignment) * alignment
        val maxAligned = (max / alignment) * alignment
        if (minAligned > maxAligned) return clampInt(value, min, max)

        val clamped = clampInt(value, minAligned, maxAligned)
        val down = alignDown(clamped, alignment)
        val up = kotlin.math.min(maxAligned, down + alignment)

        if (down == up) return down

        return if (clamped - down < up - clamped) down else up
    }

    private fun alignDown(value: Int, alignment: Int): Int {
        if (alignment <= 1) return value
        if (value <= 0) return 0
        return (value / alignment) * alignment
    }

    private fun makeEven(value: Int): Int {
        return if ((value and 1) == 0) value else value + 1
    }

    private fun fitToMaxEdge(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        val longestEdge = kotlin.math.max(width, height)
        if (maxEdge !in 1..<longestEdge) {
            return Pair(width, height)
        }

        val scale = maxEdge.toFloat() / longestEdge.toFloat()
        val scaledWidth =
            kotlin.math.max(SAFE_MIN_VIDEO_DIMENSION, kotlin.math.round(width * scale).toInt())
        val scaledHeight =
            kotlin.math.max(SAFE_MIN_VIDEO_DIMENSION, kotlin.math.round(height * scale).toInt())
        return Pair(scaledWidth, scaledHeight)
    }

    override fun getPluginName(): String {
        return "Media Quality"
    }
}