package com.wmods.wppenhacer.xposed.features.customization;


import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.Objects;
import java.util.Properties;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class BubbleColors extends Feature {
    public BubbleColors(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        Properties properties = Utils.extractProperties(prefs.getString("custom_css", ""));

        if (!prefs.getBoolean("bubble_color", false) && !Objects.equals(properties.getProperty("bubble_colors"), "true"))
            return;

        boolean bubbleColor = prefs.getBoolean("bubble_color", false);

        int bubbleLeftColor = bubbleColor ? prefs.getInt("bubble_left", 0) : Color.parseColor(DesignUtils.checkSystemColor(properties.getProperty("bubble_left", "#00000000")));
        int bubbleRightColor = bubbleColor ? prefs.getInt("bubble_right", 0) : Color.parseColor(DesignUtils.checkSystemColor(properties.getProperty("bubble_right", "#00000000")));

        var bubbleDrawableMethod = Unobfuscator.loadBubbleDrawableMethod(classLoader);

        XposedBridge.hookMethod(bubbleDrawableMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var position = (int) param.args[0];
                var draw = (Drawable) param.getResult();
                var right = position == 3;
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

    @NonNull
    @Override
    public String getPluginName() {
        return "Bubble Colors";
    }
}
