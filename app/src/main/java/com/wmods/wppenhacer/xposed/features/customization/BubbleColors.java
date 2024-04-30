package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.xposed.core.Utils.parseNegativeColor;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.NinePatchDrawable;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Feature;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class BubbleColors extends Feature {
    public BubbleColors(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {


        var bubbleLeftColor = prefs.getInt("bubble_left", 0);

        var bubbleRightColor = prefs.getInt("bubble_right", 0);

        if (bubbleLeftColor != 0) {

            var balloon = DesignUtils.getDrawableByName(Unobfuscator.BUBBLE_COLORS_BALLOON_OUTGOING_NORMAL);
            balloon.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleRightColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable(Unobfuscator.BUBBLE_COLORS_BALLOON_OUTGOING_NORMAL, balloon);

            var balloonExt = DesignUtils.getDrawableByName(Unobfuscator.BUBBLE_COLORS_BALLOON_OUTGOING_NORMAL_EXT);
            balloonExt.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleRightColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable(Unobfuscator.BUBBLE_COLORS_BALLOON_OUTGOING_NORMAL_EXT, balloonExt);

            var balloonFrame = DesignUtils.getDrawableByName("balloon_outgoing_frame");
            balloonFrame.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleRightColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable("balloon_outgoing_frame", balloonFrame);

            var balloonPressed = DesignUtils.getDrawableByName("balloon_outgoing_pressed");
            balloonPressed.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleRightColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable("balloon_outgoing_pressed", balloonPressed);

            var balloonSticker = DesignUtils.getDrawableByName("balloon_outgoing_normal_stkr");
            balloonSticker.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleRightColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable("balloon_outgoing_normal_stkr", balloonSticker);
        }

        if (bubbleRightColor != 0) {

            var balloon = DesignUtils.getDrawableByName(Unobfuscator.BUBBLE_COLORS_BALLOON_INCOMING_NORMAL);
            balloon.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleLeftColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable(Unobfuscator.BUBBLE_COLORS_BALLOON_INCOMING_NORMAL, balloon);

            var balloonExt = DesignUtils.getDrawableByName(Unobfuscator.BUBBLE_COLORS_BALLOON_INCOMING_NORMAL_EXT);
            balloonExt.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleLeftColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable(Unobfuscator.BUBBLE_COLORS_BALLOON_INCOMING_NORMAL_EXT, balloonExt);

            var balloonFrame = DesignUtils.getDrawableByName("balloon_incoming_frame");
            balloonFrame.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleLeftColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable("balloon_incoming_frame", balloonFrame);

            var balloonPressed = DesignUtils.getDrawableByName("balloon_incoming_pressed");
            balloonPressed.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleLeftColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable("balloon_incoming_pressed", balloonPressed);

            var balloonSticker = DesignUtils.getDrawableByName("balloon_incoming_normal_stkr");
            balloonSticker.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleLeftColor), PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable("balloon_incoming_normal_stkr", balloonSticker);

        }

        var methods = Unobfuscator.loadNineDrawableMethods(loader);
        for (var method : methods) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var draw = (NinePatchDrawable) param.getResult();
                    var right = (boolean) param.args[3];
                    if (right) {
                        draw.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleRightColor), PorterDuff.Mode.SRC_IN));
                    } else {
                        draw.setColorFilter(new PorterDuffColorFilter(parseNegativeColor(bubbleLeftColor), PorterDuff.Mode.SRC_IN));
                    }
                }
            });
        }

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Bubble Colors";
    }
}
