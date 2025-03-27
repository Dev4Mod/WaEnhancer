package com.wmods.wppenhacer.xposed.features.media;

import android.graphics.Bitmap;
import android.graphics.RecordingCanvas;
import android.os.Build;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.Others;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MediaQuality extends Feature {
    public MediaQuality(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var videoQuality = prefs.getBoolean("videoquality", false);
        var imageQuality = prefs.getBoolean("imagequality", false);
        var maxSize = (int) prefs.getFloat("video_limit_size", 60);
        var realResolution = prefs.getBoolean("video_real_resolution", false);

        // Max video size
        Others.propsInteger.put(3185, maxSize);
        Others.propsInteger.put(3656, maxSize);
        Others.propsInteger.put(4155, maxSize);
        Others.propsInteger.put(3659, maxSize);
        Others.propsInteger.put(4685, maxSize);
        Others.propsInteger.put(596, maxSize);

        // Enable Media Quality selection for Stories
        var hookMediaQualitySelection = Unobfuscator.loadMediaQualitySelectionMethod(classLoader);
        XposedBridge.hookMethod(hookMediaQualitySelection, XC_MethodReplacement.returnConstant(true));

        if (videoQuality) {

            Others.propsBoolean.put(5549, true); // Use bitrate from json to force video high quality

            var jsonProperty = Unobfuscator.loadPropsJsonMethod(classLoader);

            AtomicReference<XC_MethodHook.Unhook> jsonPropertyHook = new AtomicReference<>();

            var unhooked = XposedBridge.hookMethod(jsonProperty, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var value = ReflectionUtils.getArg(param.args, Integer.class, 0);
                    if (value == 5550) {
                        JSONObject videoBitrateData = new JSONObject();
                        String[] resolutions = {"360", "480", "720", "1080"};
                        for (String resolution : resolutions) {
                            JSONObject resolutionData = new JSONObject();
                            resolutionData.put("min_bitrate", 3000);
                            resolutionData.put("max_bitrate", 96000);
                            resolutionData.put("null_bitrate", 96000);
                            resolutionData.put("min_bandwidth", 1);
                            resolutionData.put("max_bandwidth", 1);
                            videoBitrateData.put(resolution, resolutionData);
                        }
                        param.setResult(videoBitrateData);
                    } else if (value == 9705) {
                        param.setResult(new JSONObject());
                    }
                }
            });
            jsonPropertyHook.set(unhooked);

            var videoMethod = Unobfuscator.loadMediaQualityVideoMethod2(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(videoMethod));

            var mediaFields = Unobfuscator.loadMediaQualityOriginalVideoFields(classLoader);
            var mediaTranscodeParams = Unobfuscator.loadMediaQualityVideoFields(classLoader);

            XposedBridge.hookMethod(videoMethod, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var resizeVideo = param.getResult();
                    if ((int) param.args[1] == 3) {

                        if (realResolution) {
                            var width = mediaFields.get("widthPx").getInt(param.args[0]);
                            var height = mediaFields.get("heightPx").getInt(param.args[0]);
                            var rotationAngle = mediaFields.get("rotationAngle").getInt(param.args[0]);

                            var targetWidthField = mediaTranscodeParams.get("targetWidth");
                            var targetHeightField = mediaTranscodeParams.get("targetHeight");

                            var inverted = rotationAngle == 90 || rotationAngle == 270;

                            targetHeightField.setInt(resizeVideo, inverted ? width : height);
                            targetWidthField.setInt(resizeVideo, inverted ? height : width);

                        }

                    }
                    if (prefs.getBoolean("video_maxfps", false)) {
                        var frameRateField = mediaTranscodeParams.get("frameRate");
                        frameRateField.setInt(resizeVideo, 60);
                    }
                }
            });

            // HD video must be sent in maximum resolution (up to 4K)
            if (realResolution) {
                Others.propsInteger.put(594, 8000);
                Others.propsInteger.put(12852, 8000);
            } else {
                Others.propsInteger.put(594, 1920);
                Others.propsInteger.put(12852, 1920);
            }

            // Non-HD video must be sent in HD resolution
            Others.propsInteger.put(4686, 1280);
            Others.propsInteger.put(3654, 1280);
            Others.propsInteger.put(3183, 1280); // Stories
            Others.propsInteger.put(4685, 1280); // Stories

            // Max bitrate
            Others.propsInteger.put(3755, 96000);
            Others.propsInteger.put(3756, 96000);
            Others.propsInteger.put(3757, 96000);
            Others.propsInteger.put(3758, 96000);

        }

        if (imageQuality) {

            // Image Max Size
            int maxImageSize = 50 * 1024; // 50MB
            Others.propsInteger.put(1577, maxImageSize);
            Others.propsInteger.put(6030, maxImageSize);
            Others.propsInteger.put(2656, maxImageSize);

            // Image Quality
            int imageMaxQuality = 100;
            Others.propsInteger.put(1581, imageMaxQuality);
            Others.propsInteger.put(1575, imageMaxQuality);
            Others.propsInteger.put(1578, imageMaxQuality);
            Others.propsInteger.put(6029, imageMaxQuality);
            Others.propsInteger.put(2655, imageMaxQuality);

            // HD image must be sent in maximum 4K resolution
            Others.propsBoolean.put(6033, true);
            Others.propsInteger.put(2654, 6000); // Only HD images
            Others.propsInteger.put(6032, 6000); // Only HD images

            // Non-HD image must be sent in HD resolution
            Others.propsInteger.put(1580, 4160);
            Others.propsInteger.put(1574, 4160);
            Others.propsInteger.put(1576, 4160);
            Others.propsInteger.put(12902, 4160);

            // Prevent crashes in Media preview
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                XposedHelpers.findAndHookMethod(RecordingCanvas.class, "throwIfCannotDraw", Bitmap.class, XC_MethodReplacement.DO_NOTHING);
            }

        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Media Quality";
    }

}