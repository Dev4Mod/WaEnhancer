package com.wmods.wppenhacer.xposed.features.media;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.RecordingCanvas;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.Others;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.json.JSONObject;

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

        Others.propsBoolean.put(7950, false); // Força o uso do MediaComposer para processar os videos

        if (videoQuality) {

            Others.propsBoolean.put(5549, true); // Remove o limite de qualidade do video
            var jsonProperty = Unobfuscator.loadPropsJsonMethod(classLoader);
            XposedBridge.hookMethod(jsonProperty, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var index = ReflectionUtils.findIndexOfType(param.args, Integer.class);
                    if (index == -1) {
                        logDebug("PropsJson: index int not found");
                        return;
                    }
                    if ((int) param.args[index] == 5550) {
                        var json = (JSONObject) param.getResult();
                        for (int i = 0; i < json.length(); i++) {
                            var key = (String) json.names().opt(i);
                            var jSONObject = json.getJSONObject(key);
                            jSONObject.put("max_bitrate", 16000);
                            jSONObject.put("max_bandwidth", 90);
                        }
                    }
                }
            });

            var resolutionMethod = Unobfuscator.loadMediaQualityResolutionMethod(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(resolutionMethod));

            XposedBridge.hookMethod(resolutionMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    var pair = new Pair<>(param.args[0], param.args[1]);
                    param.setResult(pair);
                }
            });

            var bitrateMethod = Unobfuscator.loadMediaQualityBitrateMethod(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(bitrateMethod));

            XposedBridge.hookMethod(bitrateMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(96 * 1000 * 1000);
                }
            });

            var videoMethod = Unobfuscator.loadMediaQualityVideoMethod2(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(videoMethod));

            var fields = Unobfuscator.loadMediaQualityVideoFields(classLoader);

            XposedBridge.hookMethod(videoMethod, new XC_MethodHook() {
                @SuppressLint("DefaultLocale")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if ((int) param.args[1] == 3) {
                        var resizeVideo = param.getResult();
                        if (prefs.getBoolean("video_real_resolution", false)) {

                            var sourceWidthField = fields.get("sourceWidth");
                            var sourceHeightField = fields.get("sourceHeight");

                            var sourceWidth = sourceWidthField.getInt(resizeVideo);
                            var sourceHeight = sourceHeightField.getInt(resizeVideo);

                            var targetWidthField = fields.get("targetWidth");
                            var targetHeightField = fields.get("targetHeight");

                            targetHeightField.setInt(resizeVideo, sourceHeight);
                            targetWidthField.setInt(resizeVideo, sourceWidth);
                        }
                        if (prefs.getBoolean("video_maxfps", false)) {
                            XposedHelpers.setIntField(resizeVideo, "A01", 60);
                        }
                    }
                }
            });

            var videoLimitClass = Unobfuscator.loadMediaQualityVideoLimitClass(classLoader);
            logDebug(videoLimitClass);

            XposedHelpers.findAndHookConstructor(videoLimitClass, int.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if (prefs.getBoolean("video_size_limit", false)) {
                        param.args[0] = 90;
                    }
                    param.args[1] = 8000;  // 4K Resolution
                    param.args[2] = 96 * 1000 * 1000; // 96 Mbps
                }
            });

        }

        if (imageQuality) {
            int[] props = {1573, 1575, 1578, 1574, 1576, 1577, 2654, 2656, 6030, 6032};
            int max = 10000;
            int min = 1000;
            for (int index = 0; index < props.length; index++) {
                if (index <= 2) {
                    Others.propsInteger.put(props[index], min);
                } else {
                    Others.propsInteger.put(props[index], max);
                }
            }
            Others.propsInteger.put(2655, 100); // Image quality compression
            Others.propsInteger.put(6029, 100); // Image quality compression
            Others.propsInteger.put(3657, 100); // Image quality compression

            var mediaQualityProcessor = Unobfuscator.loadMediaQualityProcessor(classLoader);
            XposedBridge.hookAllConstructors(mediaQualityProcessor, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length < 4) return;
                    param.args[0] = 10000; // maxKb
                    param.args[1] = 100; // quality
                    param.args[2] = 10000; // maxEdge
                    param.args[3] = 10000; // nothing
                }
            });

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