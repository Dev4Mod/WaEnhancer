package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.utils.ColorReplacement.replaceColors;
import static com.wmods.wppenhacer.utils.DrawableColors.replaceColor;
import static com.wmods.wppenhacer.utils.IColors.colors;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.app.Activity;
import android.app.Notification;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.utils.IColors;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.Feature;

import org.xmlpull.v1.XmlPullParser;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class CustomTheme extends Feature {

    public static ClassLoader classLoader;

    public CustomTheme(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
        classLoader = loader;
    }

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("changecolor", false)) return;

        var customDrawable1 = Unobfuscator.loadExpandableWidgetClass(loader);
        logDebug("customDrawable1: " + customDrawable1.getName());
        var customDrawable2 = Unobfuscator.loadMaterialShapeDrawableClass(loader);
        logDebug("customDrawable2: " + customDrawable2.getName());
        var customDrawable3 = Unobfuscator.loadCustomDrawableClass(loader);
        logDebug("customDrawable3: " + customDrawable3.getName());


        var primaryColorInt = prefs.getInt("primary_color", 0);
        var secondaryColorInt = prefs.getInt("secondary_color", 0);
        var backgroundColorInt =  prefs.getInt("background_color", 0);

        var primaryColor = primaryColorInt == 0 ? "0" : String.format("#%08X",  primaryColorInt);
        var secondaryColor = secondaryColorInt == 0 ? "0" : String.format("#%08X",  secondaryColorInt);
        var backgroundColor = backgroundColorInt == 0 ? "0" : String.format("#%08X", backgroundColorInt);

        for (var c : colors.keySet()) {
            if (!primaryColor.equals("0")) {
                switch (c) {
                    case "00a884", "1da457", "21c063", "d9fdd3" ->
                            colors.put(c, primaryColor.substring(3));
                    case "#ff00a884", "#ff1da457", "#ff21c063", "#ff1daa61" ->
                            colors.put(c, primaryColor);
                    case "#ff103529" -> colors.put(c, "#66" + primaryColor.substring(3));
                }
            }

            if (!backgroundColor.equals("0")) {
                switch (c) {
                    case "0b141a" -> colors.put(c, backgroundColor.substring(3));
                    case "#ff0b141a", "#ff111b21" -> colors.put(c, backgroundColor);
                }
            }

            if (!secondaryColor.equals("0")) {
                if (c.equals("#ff202c33")) {
                    colors.put(c, secondaryColor);
                }
            }

        }

        findAndHookMethod(Activity.class.getName(), loader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                var activity = (Activity) param.thisObject;
                var view = activity.findViewById(android.R.id.content).getRootView();
                replaceColors(view);
            }
        });

        var intBgHook = new IntBgColorHook();
        findAndHookMethod(Paint.class.getName(), loader, "setColor", int.class, intBgHook);
        findAndHookMethod(View.class.getName(), loader, "setBackgroundColor", int.class, intBgHook);
        findAndHookMethod(GradientDrawable.class.getName(), loader, "setColor", int.class, intBgHook);
        findAndHookMethod(ColorDrawable.class.getName(), loader, "setColor", int.class, intBgHook);
        findAndHookMethod(Notification.Builder.class.getName(), loader, "setColor", int.class, intBgHook);
        findAndHookMethod(Drawable.class.getName(), loader, "setTint", int.class, intBgHook);
        findAndHookMethod("com.whatsapp.CircularProgressBar", loader, "setProgressBarColor", int.class, intBgHook);
        findAndHookMethod("com.whatsapp.CircularProgressBar", loader, "setProgressBarBackgroundColor", int.class, intBgHook);

        var colorStateListHook = new ColorStateListHook();
        findAndHookMethod(Drawable.class.getName(), loader, "setTintList", ColorStateList.class, colorStateListHook);
        findAndHookMethod(customDrawable1, "setBackgroundTintList", ColorStateList.class, colorStateListHook);
        findAndHookMethod(customDrawable1, "setRippleColor", ColorStateList.class, colorStateListHook);
        findAndHookMethod(customDrawable1, "setSupportImageTintList", ColorStateList.class, colorStateListHook);

        findAndHookMethod(customDrawable2, "setTintList", ColorStateList.class, colorStateListHook);
        findAndHookMethod(customDrawable2, "setTint", int.class, intBgHook);

        findAndHookMethod(customDrawable3, "setTintList", ColorStateList.class, colorStateListHook);

        var inflaterHook = (XC_MethodHook) new LayoutInflaterHook();
        findAndHookMethod(LayoutInflater.class.getName(), loader, "inflate", int.class, ViewGroup.class, inflaterHook);
        findAndHookMethod(LayoutInflater.class.getName(), loader, "inflate", XmlPullParser.class, ViewGroup.class, inflaterHook);
        findAndHookMethod(LayoutInflater.class.getName(), loader, "inflate", int.class, ViewGroup.class, boolean.class, inflaterHook);
        findAndHookMethod(LayoutInflater.class.getName(), loader, "inflate", XmlPullParser.class, ViewGroup.class, boolean.class, inflaterHook);

        findAndHookMethod(View.class.getName(), loader, "setBackground", Drawable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var drawable = (Drawable) param.args[0];
                replaceColor(drawable);
                super.beforeHookedMethod(param);
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Change Colors";
    }

    public static class LayoutInflaterHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            var view = (View) param.getResult();
            if (view == null) return;
            replaceColors(view);
        }
    }

    public static class ColorStateListHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            var colorStateList = param.args[0];
            if (colorStateList != null) {
                var mColors = (int[]) XposedHelpers.getObjectField(colorStateList, "mColors");

//            XposedBridge.log("mColors: " + Arrays.toString(mColors));
                for (int i = 0; i < mColors.length; i++) {
                    var sColor = IColors.toString(mColors[i]);
                    var newColor = colors.get(sColor);
                    if (newColor != null && newColor.length() == 9) {
                        mColors[i] = IColors.parseColor(newColor);
                    } else {
                        if (!sColor.equals("#0") && !sColor.startsWith("#ff")) {
                            var sColorSub = sColor.substring(0, 3);
                            newColor = colors.get(sColor.substring(3));
                            if (newColor != null) {
                                mColors[i] = IColors.parseColor(sColorSub + newColor);
                            }
                        }
                    }
                }
                XposedHelpers.setObjectField(colorStateList, "mColors", mColors);
                param.args[0] = colorStateList;
            }

            super.beforeHookedMethod(param);
        }
    }

    public static class IntBgColorHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            var color = (int) param.args[0];
            var sColor = IColors.toString(color);
            var newColor = colors.get(sColor);
//            XposedBridge.log("-> New Color: " + newColor);
            if (newColor != null && newColor.length() == 9) {
                param.args[0] = IColors.parseColor(newColor);
            } else {
                if (!sColor.equals("#0") && !sColor.startsWith("#ff")) {
                    var sColorSub = sColor.substring(0, 3);
                    newColor = colors.get(sColor.substring(3));
                    if (newColor != null) {
                        param.args[0] = IColors.parseColor(sColorSub + newColor);
                    }
                }
            }
            super.beforeHookedMethod(param);
        }
    }
}
