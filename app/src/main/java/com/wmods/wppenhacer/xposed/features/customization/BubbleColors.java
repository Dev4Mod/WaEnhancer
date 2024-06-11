package com.wmods.wppenhacer.xposed.features.customization;


import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.NinePatchDrawable;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class BubbleColors extends Feature {
    public BubbleColors(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        if (!prefs.getBoolean("bubble_color", false)) return;

        var bubbleLeftColor = prefs.getInt("bubble_left", 0);

        var bubbleRightColor = prefs.getInt("bubble_right", 0);

        // Right

        if (bubbleRightColor!= 0) {

            replaceColor(Unobfuscator.BUBBLE_COLORS_BALLOON_OUTGOING_NORMAL, bubbleRightColor);

            replaceColor(Unobfuscator.BUBBLE_COLORS_BALLOON_OUTGOING_NORMAL_EXT, bubbleRightColor);

            replaceColor("balloon_outgoing_frame", bubbleRightColor);

            replaceColor("balloon_outgoing_pressed", bubbleRightColor);

            replaceColor("balloon_outgoing_pressed_ext", bubbleRightColor);

            replaceColor("balloon_outgoing_normal_stkr", bubbleRightColor);

            replaceColor("balloon_outgoing_normal_stkr_tinted", bubbleRightColor);
        }

        // Left

        if (bubbleLeftColor != 0) {

            replaceColor(Unobfuscator.BUBBLE_COLORS_BALLOON_INCOMING_NORMAL, bubbleLeftColor);

            replaceColor(Unobfuscator.BUBBLE_COLORS_BALLOON_INCOMING_NORMAL_EXT, bubbleLeftColor);

            replaceColor("balloon_incoming_frame", bubbleLeftColor);

            replaceColor("balloon_incoming_pressed", bubbleLeftColor);

            replaceColor("balloon_incoming_pressed_ext", bubbleLeftColor);

            replaceColor("balloon_incoming_normal_stkr", bubbleLeftColor);

            replaceColor("balloon_incoming_normal_stkr_tinted", bubbleLeftColor);

        }


        var methods = Unobfuscator.loadNineDrawableMethods(classLoader);
        for (var method : methods) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var draw = (NinePatchDrawable) param.getResult();
                    var right = (boolean) param.args[3];
                    if (right) {
                        if (bubbleRightColor == 0) return;
                        draw.setColorFilter(new PorterDuffColorFilter(bubbleRightColor, PorterDuff.Mode.SRC_IN));
                    } else {
                        if (bubbleLeftColor == 0) return;
                        draw.setColorFilter(new PorterDuffColorFilter(bubbleLeftColor, PorterDuff.Mode.SRC_IN));
                    }
                }
            });
        }
    }

    private static void replaceColor(String drawableName, int color) {
        try {
            var drawable = DesignUtils.getDrawableByName(drawableName);
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            DesignUtils.setReplacementDrawable(drawableName, drawable);
        } catch (Exception ignored) {
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Bubble Colors";
    }
}
