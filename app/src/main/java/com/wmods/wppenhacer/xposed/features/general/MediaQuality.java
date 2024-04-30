package com.wmods.wppenhacer.xposed.features.general;

import android.graphics.Bitmap;
import android.graphics.RecordingCanvas;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Feature;

import java.util.ArrayList;

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

        if (videoQuality) {

            var resolutionMethod = Unobfuscator.loadMediaQualityResolutionMethod(loader);
            logDebug(Unobfuscator.getMethodDescriptor(resolutionMethod));

            XposedBridge.hookMethod(resolutionMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    var pair = new Pair<>(param.args[0], param.args[1]);
                    param.setResult(pair);
                }
            });

            var bitrateMethod = Unobfuscator.loadMediaQualityBitrateMethod(loader);
            logDebug(Unobfuscator.getMethodDescriptor(bitrateMethod));

            XposedBridge.hookMethod(bitrateMethod, XC_MethodReplacement.returnConstant((1600000)));

            var videoMethod = Unobfuscator.loadMediaQualityVideoMethod(loader);
            logDebug(Unobfuscator.getMethodDescriptor(videoMethod));

            XposedBridge.hookMethod(videoMethod, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return new Pair<>(true, new ArrayList<>());
                }
            });

        }

        if (imageQuality) {
            // 6Ex
            var mediaQualityImageMethod = Unobfuscator.loadMediaQualityImageMethod(loader);
            logDebug(Unobfuscator.getMethodDescriptor(mediaQualityImageMethod));
            XposedBridge.hookMethod(mediaQualityImageMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int p1 = (int) (param.args.length > 2 ? param.args[2] : param.args[1]);
                    int[] props = {1573, 1575, 1578, 1574, 1576, 1577};
                    int max = 10000;
                    int min = 1000;
                    for (int index = 0; index < props.length; index++) {
                        if (props[index] == p1) {
                            if (index <= 2) {
                                param.setResult(min);
                            } else {
                                param.setResult(max);
                            }
                        }
                    }
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