package com.wmods.wppenhacer.xposed.features.media;

import android.graphics.Bitmap;
import android.graphics.RecordingCanvas;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.Others;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MediaQuality extends Feature {

    private static final String VIDEO_MIME_AVC = "video/avc";
    private static final String VIDEO_MIME_HEVC = "video/hevc";
    private static final int SAFE_MAX_HD_BITRATE_KBPS = 16000;
    private static final int SAFE_MAX_NON_HD_BITRATE_KBPS = 10000;
    private static final int MIN_HD_NON_HD_BITRATE_GAP_KBPS = 2000;
    private static final int SAFE_MIN_VIDEO_DIMENSION = 2;
    private static final int SAFE_MAX_VIDEO_EDGE = 3840;
    private static final int FALLBACK_LANDSCAPE_WIDTH = 1280;
    private static final int FALLBACK_LANDSCAPE_HEIGHT = 720;

    public MediaQuality(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var videoQuality = prefs.getBoolean("videoquality", false);
        var imageQuality = prefs.getBoolean("imagequality", false);
        var maxSize = Math.max((int) prefs.getFloat("video_limit_size", 60), 90);
        var realResolution = prefs.getBoolean("video_real_resolution", false);

        // Disable manual calculation ProcessMediaQuality
        Others.propsBoolean.put(14447, false);

        // Enable Media Quality selection for Stories
        try {
            var hookMediaQualitySelection = Unobfuscator.loadMediaQualitySelectionMethod(classLoader);
            XposedBridge.hookMethod(hookMediaQualitySelection, XC_MethodReplacement.returnConstant(true));
        } catch (Exception ignored) {
            var BottomBarConfigClass = Unobfuscator.loadBottomBarConfigClass(classLoader);
            var fieldsBottomBarConfig = Unobfuscator.getAllMapFields(BottomBarConfigClass);

            XposedBridge.hookAllConstructors(BottomBarConfigClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var supportsHdQuality = fieldsBottomBarConfig.get("supportsHdQuality");
                    if (supportsHdQuality != null) {
                        supportsHdQuality.set(param.thisObject, true);
                    }
                }
            });
        }

        if (videoQuality) {
            Others.propsBoolean.put(5549, true);

            var ProcessVideoQualityClass = Unobfuscator.loadProcessVideoQualityClass(classLoader);
            var processVideoQualityFields = Unobfuscator.getAllMapFields(ProcessVideoQualityClass);

            XposedBridge.hookAllConstructors(ProcessVideoQualityClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var processVideoQuality = param.thisObject;
                    var fieldvideoMaxBitrate = processVideoQualityFields.get("videoMaxBitrate");
                    var fieldvideoMaxEdge = processVideoQualityFields.get("videoMaxEdge");
                    var fieldvideoLimitMb = processVideoQualityFields.get("videoLimitMb");
                    if (fieldvideoLimitMb != null) {
                        fieldvideoLimitMb.setInt(processVideoQuality, maxSize);
                    }
                    if (fieldvideoMaxEdge != null) {
                        fieldvideoMaxEdge.setInt(processVideoQuality, 3840);
                    }
                    if (fieldvideoMaxBitrate != null) {
                        int bitrateBps = 24000 * 1000;
                        fieldvideoMaxBitrate.setInt(processVideoQuality, bitrateBps);
                    }
                }
            });

            var MediaDataVideoConfiguration = Unobfuscator.loadMediaDataVideoConfigurationClass(classLoader);
            var fieldsMediaDataVideoConfiguration = Unobfuscator.getAllMapFields(MediaDataVideoConfiguration);

            Method VideoTranscoderStart = Unobfuscator.loadVideoTranscoderStartMethod(classLoader);
            XposedBridge.hookMethod(VideoTranscoderStart, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    var videoProcessor = param.args[0];
                    var booleanParams = ReflectionUtils.getFieldsByType(videoProcessor.getClass(), Boolean.TYPE);
                    if (booleanParams.size() > 2) {
                        Field field = booleanParams.get(2);
                        field.setBoolean(videoProcessor, false);
                    }
                    var fieldMediaDataVideoConfiguration = ReflectionUtils.getFieldByType(videoProcessor.getClass(), MediaDataVideoConfiguration);
                    var mediaDataVideoConfiguration = fieldMediaDataVideoConfiguration.get(videoProcessor);
                    var fieldforceSingleTranscoding = fieldsMediaDataVideoConfiguration.get("forceSingleTranscoding");
                    fieldforceSingleTranscoding.setBoolean(mediaDataVideoConfiguration, true);
                }
            });


            var videoMethod = Unobfuscator.loadMediaQualityVideoMethod2(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(videoMethod));

            var mediaFields = Unobfuscator.loadMediaQualityOriginalVideoFields(classLoader);
            var mediaTranscodeParams = Unobfuscator.loadMediaQualityVideoFields(classLoader);

            EncoderVideoCapabilities encoderVideoCapabilities = getPreferredEncoderCapabilities(VIDEO_MIME_AVC);
            if (encoderVideoCapabilities == null) {
                encoderVideoCapabilities = getPreferredEncoderCapabilities(VIDEO_MIME_HEVC);
            }

            final EncoderVideoCapabilities finalEncoderVideoCapabilities = encoderVideoCapabilities;

            XposedBridge.hookMethod(videoMethod, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var resizeVideo = param.getResult();
                    boolean isHighResolution;
                    boolean isEnum = false;
                    var enumObj = ReflectionUtils.getArg(param.args, Enum.class, 0);
                    var intParams = ReflectionUtils.findInstancesOfType(param.args, Integer.class);
                    if (enumObj != null) {
                        isEnum = true;
                        var hightResolution = Enum.valueOf((Class<Enum>) enumObj.getClass(), "RESOLUTION_1080P");
                        isHighResolution = hightResolution == enumObj;
                    } else {
                        isHighResolution = (int) param.args[1] == 3;
                    }
                    if (isHighResolution) {

                        if (realResolution) {
                            int width;
                            int height;
                            int rotationAngle;

                            if (mediaFields.isEmpty()) {
                                if (isEnum) {
                                    width = intParams.get(intParams.size() - 3).second;
                                    height = intParams.get(intParams.size() - 2).second;
                                    rotationAngle = intParams.get(intParams.size() - 1).second;
                                } else {
                                    JSONObject mediaFields = (JSONObject) XposedHelpers.callMethod(param.args[0], "A00");
                                    width = mediaFields.getInt("widthPx");
                                    height = mediaFields.getInt("heightPx");
                                    rotationAngle = mediaFields.getInt("rotationAngle");
                                }
                            } else {
                                width = mediaFields.get("widthPx").getInt(param.args[0]);
                                height = mediaFields.get("heightPx").getInt(param.args[0]);
                                rotationAngle = mediaFields.get("rotationAngle").getInt(param.args[0]);
                            }
                            var targetWidthField = mediaTranscodeParams.get("targetWidth");
                            var targetHeightField = mediaTranscodeParams.get("targetHeight");

                            var inverted = rotationAngle == 90 || rotationAngle == 270;

                            int targetWidth = inverted ? height : width;
                            int targetHeight = inverted ? width : height;

                            var sanitizedTargetSize = sanitizeVideoSize(targetWidth, targetHeight, finalEncoderVideoCapabilities);
                            if (sanitizedTargetSize != null) {
                                if (targetWidthField != null) {
                                    targetWidthField.setInt(resizeVideo, sanitizedTargetSize.first);
                                }
                                if (targetHeightField != null) {
                                    targetHeightField.setInt(resizeVideo, sanitizedTargetSize.second);
                                }
                            }

                        }
                    }
                    if (prefs.getBoolean("video_maxfps", false)) {
                        var frameRateField = mediaTranscodeParams.get("frameRate");
                        frameRateField.setInt(resizeVideo, 60);
                    }
                }
            });
        }

        if (imageQuality) {

            var processImageQualityClass = Unobfuscator.loadProcessImageQualityClass(classLoader);
            var fieldsProcessImageQuality = Unobfuscator.getAllMapFields(processImageQualityClass);

            XposedBridge.hookAllConstructors(processImageQualityClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var processImageQuality = param.thisObject;
                    var fieldimageMaxSize = fieldsProcessImageQuality.get("maxKb");
                    var fieldimageMaxQuality = fieldsProcessImageQuality.get("quality");
                    var fieldimageMaxEdge = fieldsProcessImageQuality.get("maxEdge");
                    if (fieldimageMaxSize != null) {
                        fieldimageMaxSize.setInt(processImageQuality, 50 * 1024);
                    }
                    if (fieldimageMaxQuality != null) {
                        fieldimageMaxQuality.setInt(processImageQuality, 100);
                    }
                    if (fieldimageMaxEdge != null) {
                        fieldimageMaxEdge.setInt(processImageQuality, 6000);
                    }
                }
            });

            // Prevent crashes in Media preview
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                XposedHelpers.findAndHookMethod(RecordingCanvas.class, "throwIfCannotDraw", Bitmap.class, XC_MethodReplacement.DO_NOTHING);
            }

        }
    }

    @Nullable
    private EncoderVideoCapabilities getPreferredEncoderCapabilities(@NonNull String mimeType) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();

        EncoderVideoCapabilities fallback = null;
        for (MediaCodecInfo info : codecInfos) {
            if (!info.isEncoder()) {
                continue;
            }

            for (String type : info.getSupportedTypes()) {
                if (!type.equalsIgnoreCase(mimeType)) {
                    continue;
                }

                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
                    MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
                    if (videoCaps == null) {
                        continue;
                    }

                    int minWidth = Math.max(SAFE_MIN_VIDEO_DIMENSION, videoCaps.getSupportedWidths().getLower());
                    int maxWidth = Math.max(minWidth, videoCaps.getSupportedWidths().getUpper());
                    int minHeight = Math.max(SAFE_MIN_VIDEO_DIMENSION, videoCaps.getSupportedHeights().getLower());
                    int maxHeight = Math.max(minHeight, videoCaps.getSupportedHeights().getUpper());
                    int widthAlignment = Math.max(2, videoCaps.getWidthAlignment());
                    int heightAlignment = Math.max(2, videoCaps.getHeightAlignment());
                    int maxBitrateKbps = Math.max(2000, videoCaps.getBitrateRange().getUpper() / 1000);

                    EncoderVideoCapabilities candidate = new EncoderVideoCapabilities(
                            info.getName(),
                            minWidth,
                            maxWidth,
                            minHeight,
                            maxHeight,
                            widthAlignment,
                            heightAlignment,
                            maxBitrateKbps,
                            videoCaps
                    );

                    if (fallback == null) {
                        fallback = candidate;
                    }

                    if (!isSoftwareCodec(info.getName())) {
                        return candidate;
                    }
                } catch (Exception e) {
                    logDebug("Failed to read encoder capabilities", e);
                }
                break;
            }
        }

        return fallback;
    }

    private boolean isSoftwareCodec(@Nullable String codecName) {
        if (codecName == null || codecName.isEmpty()) {
            return true;
        }
        String lowerName = codecName.toLowerCase();
        return lowerName.startsWith("omx.google.")
                || lowerName.startsWith("c2.android.")
                || lowerName.contains("software")
                || lowerName.contains(".sw.");
    }

    private record EncoderVideoCapabilities(
            @NonNull String codecName,
            int minWidth,
            int maxWidth,
            int minHeight,
            int maxHeight,
            int widthAlignment,
            int heightAlignment,
            int maxBitrateKbps,
            @NonNull MediaCodecInfo.VideoCapabilities videoCapabilities
    ) {
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    @Nullable
    private Pair<Integer, Integer> sanitizeVideoSize(int width, int height, @Nullable EncoderVideoCapabilities encoderVideoCapabilities) {
        if (width <= 0 || height <= 0) {
            return null;
        }

        Pair<Integer, Integer> boundedSize = fitToMaxEdge(width, height, SAFE_MAX_VIDEO_EDGE);
        width = boundedSize.first;
        height = boundedSize.second;

        if (encoderVideoCapabilities == null) {
            int safeWidth = makeEven(width);
            int safeHeight = makeEven(height);
            return new Pair<>(Math.max(SAFE_MIN_VIDEO_DIMENSION, safeWidth), Math.max(SAFE_MIN_VIDEO_DIMENSION, safeHeight));
        }

        int safeWidth = clampInt(width, encoderVideoCapabilities.minWidth, encoderVideoCapabilities.maxWidth);
        int safeHeight = clampInt(height, encoderVideoCapabilities.minHeight, encoderVideoCapabilities.maxHeight);

        safeWidth = alignToNearest(safeWidth, encoderVideoCapabilities.widthAlignment, encoderVideoCapabilities.minWidth, encoderVideoCapabilities.maxWidth);
        safeHeight = alignToNearest(safeHeight, encoderVideoCapabilities.heightAlignment, encoderVideoCapabilities.minHeight, encoderVideoCapabilities.maxHeight);

        Pair<Integer, Integer> supportedSize = findSupportedSize(safeWidth, safeHeight, encoderVideoCapabilities);
        if (supportedSize != null) {
            return supportedSize;
        }

        int fallbackWidth = alignToNearest(
                clampInt(Math.min(width, 1280), encoderVideoCapabilities.minWidth, encoderVideoCapabilities.maxWidth),
                encoderVideoCapabilities.widthAlignment,
                encoderVideoCapabilities.minWidth,
                encoderVideoCapabilities.maxWidth
        );
        int fallbackHeight = alignToNearest(
                clampInt(Math.min(height, 720), encoderVideoCapabilities.minHeight, encoderVideoCapabilities.maxHeight),
                encoderVideoCapabilities.heightAlignment,
                encoderVideoCapabilities.minHeight,
                encoderVideoCapabilities.maxHeight
        );

        if (isSizeSupported(encoderVideoCapabilities, fallbackWidth, fallbackHeight)) {
            return new Pair<>(fallbackWidth, fallbackHeight);
        }

        boolean isLandscape = width >= height;
        int conservativeWidth = isLandscape ? FALLBACK_LANDSCAPE_WIDTH : FALLBACK_LANDSCAPE_HEIGHT;
        int conservativeHeight = isLandscape ? FALLBACK_LANDSCAPE_HEIGHT : FALLBACK_LANDSCAPE_WIDTH;

        conservativeWidth = alignToNearest(
                clampInt(conservativeWidth, encoderVideoCapabilities.minWidth, encoderVideoCapabilities.maxWidth),
                encoderVideoCapabilities.widthAlignment,
                encoderVideoCapabilities.minWidth,
                encoderVideoCapabilities.maxWidth
        );
        conservativeHeight = alignToNearest(
                clampInt(conservativeHeight, encoderVideoCapabilities.minHeight, encoderVideoCapabilities.maxHeight),
                encoderVideoCapabilities.heightAlignment,
                encoderVideoCapabilities.minHeight,
                encoderVideoCapabilities.maxHeight
        );

        return new Pair<>(conservativeWidth, conservativeHeight);
    }

    @Nullable
    private Pair<Integer, Integer> findSupportedSize(int width, int height, @NonNull EncoderVideoCapabilities encoderVideoCapabilities) {
        if (isSizeSupported(encoderVideoCapabilities, width, height)) {
            return new Pair<>(width, height);
        }

        float aspectRatio = (float) width / (float) height;
        int currentWidth = width;
        int currentHeight = height;

        for (int attempt = 0; attempt < 80; attempt++) {
            if (currentWidth >= currentHeight) {
                currentWidth = alignDown(currentWidth - encoderVideoCapabilities.widthAlignment, encoderVideoCapabilities.widthAlignment);
                if (currentWidth < encoderVideoCapabilities.minWidth) {
                    break;
                }
                int scaledHeight = Math.round(currentWidth / aspectRatio);
                currentHeight = alignToNearest(
                        clampInt(scaledHeight, encoderVideoCapabilities.minHeight, encoderVideoCapabilities.maxHeight),
                        encoderVideoCapabilities.heightAlignment,
                        encoderVideoCapabilities.minHeight,
                        encoderVideoCapabilities.maxHeight
                );
            } else {
                currentHeight = alignDown(currentHeight - encoderVideoCapabilities.heightAlignment, encoderVideoCapabilities.heightAlignment);
                if (currentHeight < encoderVideoCapabilities.minHeight) {
                    break;
                }
                int scaledWidth = Math.round(currentHeight * aspectRatio);
                currentWidth = alignToNearest(
                        clampInt(scaledWidth, encoderVideoCapabilities.minWidth, encoderVideoCapabilities.maxWidth),
                        encoderVideoCapabilities.widthAlignment,
                        encoderVideoCapabilities.minWidth,
                        encoderVideoCapabilities.maxWidth
                );
            }

            if (isSizeSupported(encoderVideoCapabilities, currentWidth, currentHeight)) {
                return new Pair<>(currentWidth, currentHeight);
            }
        }

        return null;
    }

    private boolean isSizeSupported(@NonNull EncoderVideoCapabilities encoderVideoCapabilities, int width, int height) {
        try {
            return encoderVideoCapabilities.videoCapabilities.isSizeSupported(width, height);
        } catch (Exception ignored) {
            return false;
        }
    }

    private int alignToNearest(int value, int alignment, int min, int max) {
        if (alignment <= 1) {
            return clampInt(value, min, max);
        }

        int minAligned = ((min + alignment - 1) / alignment) * alignment;
        int maxAligned = (max / alignment) * alignment;
        if (minAligned > maxAligned) {
            return clampInt(value, min, max);
        }

        int clamped = clampInt(value, minAligned, maxAligned);
        int down = alignDown(clamped, alignment);
        int up = Math.min(maxAligned, down + alignment);
        if (down == up) {
            return down;
        }
        return (clamped - down < up - clamped) ? down : up;
    }

    private int alignDown(int value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        if (value <= 0) {
            return 0;
        }
        return (value / alignment) * alignment;
    }

    private int makeEven(int value) {
        return (value & 1) == 0 ? value : value + 1;
    }

    @NonNull
    private Pair<Integer, Integer> fitToMaxEdge(int width, int height, int maxEdge) {
        int longestEdge = Math.max(width, height);
        if (longestEdge <= maxEdge || maxEdge <= 0) {
            return new Pair<>(width, height);
        }

        float scale = (float) maxEdge / (float) longestEdge;
        int scaledWidth = Math.max(SAFE_MIN_VIDEO_DIMENSION, Math.round(width * scale));
        int scaledHeight = Math.max(SAFE_MIN_VIDEO_DIMENSION, Math.round(height * scale));
        return new Pair<>(scaledWidth, scaledHeight);
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Media Quality";
    }

}