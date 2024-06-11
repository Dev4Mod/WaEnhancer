package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.utils.ColorReplacement.replaceColors;
import static com.wmods.wppenhacer.utils.DrawableColors.replaceColor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.utils.IColors;
import com.wmods.wppenhacer.views.WallpaperView;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class CustomTheme extends Feature {

    public static ClassLoader loader1;
    private HashMap<String, String> wallAlpha;
    private HashMap<String, String> navAlpha;
    private HashMap<String, String> toolbarAlpha;

    public CustomTheme(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
        loader1 = loader;
    }

    @Override
    public void doHook() throws Exception {
        hookColors();
        hookWallpaper();
    }

    private void hookWallpaper() throws Exception {
        if (!prefs.getBoolean("wallpaper", false)) return;

        var clazz = XposedHelpers.findClass("com.whatsapp.HomeActivity", classLoader);
        XposedHelpers.findAndHookMethod(clazz.getSuperclass(), "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    injectWallpaper(activity.findViewById(android.R.id.content));
                }
            }
        });

        XposedHelpers.findAndHookMethod("androidx.viewpager.widget.ViewPager", loader1, "onMeasure", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var viewGroup = (ViewGroup) param.thisObject;
                replaceColors(viewGroup, wallAlpha);
            }
        });


        var loadTabFrameClass = Unobfuscator.loadTabFrameClass(classLoader);
        XposedHelpers.findAndHookMethod(FrameLayout.class, "onMeasure", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!loadTabFrameClass.isInstance(param.thisObject)) return;
                var viewGroup = (ViewGroup) param.thisObject;
                var background = viewGroup.getBackground();
                try {
                    var colorfilters = XposedHelpers.getObjectField(background, "A01");
                    var fields = ReflectionUtils.getFieldsByType(colorfilters.getClass(), ColorStateList.class);
                    var colorStateList = (ColorStateList) fields.get(0).get(colorfilters);
                    if (colorStateList == null) return;
                    var color = IColors.toString(colorStateList.getDefaultColor());
                    var newColor = navAlpha.get(color);
                    if (newColor != null) {
                        background.setTint(IColors.parseColor(newColor));
                    }
                } catch (Throwable ignored) {
                }
            }
        });

    }

    private void hookColors() throws Exception {
        var customDrawable1 = Unobfuscator.loadExpandableWidgetClass(classLoader);
        logDebug("customDrawable1: " + customDrawable1.getName());
        var customDrawable2 = Unobfuscator.loadMaterialShapeDrawableClass(classLoader);
        logDebug("customDrawable2: " + customDrawable2.getName());
        var customDrawable3 = Unobfuscator.loadCustomDrawableClass(classLoader);
        logDebug("customDrawable3: " + customDrawable3.getName());

        var primaryColorInt = prefs.getInt("primary_color", 0);
        var secondaryColorInt = prefs.getInt("secondary_color", 0);
        var backgroundColorInt = prefs.getInt("background_color", 0);

        var primaryColor = primaryColorInt == 0 ? "0" : IColors.toString(primaryColorInt);
        var secondaryColor = secondaryColorInt == 0 ? "0" : IColors.toString(secondaryColorInt);
        var backgroundColor = backgroundColorInt == 0 ? "0" : IColors.toString(backgroundColorInt);

        if (prefs.getBoolean("changecolor", false)) {
            for (var c : IColors.colors.keySet()) {
                if (!primaryColor.equals("0")) {
                    switch (c) {
                        case "00a884", "1da457", "21c063", "d9fdd3" ->
                                IColors.colors.put(c, primaryColor.substring(3));
                        case "#ff00a884", "#ff1da457", "#ff21c063", "#ff1daa61" ->
                                IColors.colors.put(c, primaryColor);
                        case "#ff103529" ->
                                IColors.colors.put(c, "#66" + primaryColor.substring(3));
                    }
                }

                if (!backgroundColor.equals("0")) {
                    switch (c) {
                        case "0b141a" -> IColors.colors.put(c, backgroundColor.substring(3));
                        case "#ff0b141a", "#ff111b21", "#ff000000" ->
                                IColors.colors.put(c, backgroundColor);
                    }
                }

                if (!secondaryColor.equals("0")) {
                    if (c.equals("#ff202c33")) {
                        IColors.colors.put(c, secondaryColor);
                    }
                }
            }
        }

        if (prefs.getBoolean("wallpaper", false)) {

            wallAlpha = new HashMap<>(IColors.colors);
            replaceTransparency(wallAlpha, (100 - prefs.getInt("wallpaper_alpha", 30)) / 100.0f);

            navAlpha = new HashMap<>(IColors.colors);
            replaceTransparency(navAlpha, (100 - prefs.getInt("wallpaper_alpha_navigation", 30)) / 100.0f);

            toolbarAlpha = new HashMap<>(IColors.colors);

            // Corrigir cor verde na barra de ferramentas
            var colorOrig = "#ff1b8755";
            var color = toolbarAlpha.get(colorOrig);
            if (Objects.equals(colorOrig, color)) toolbarAlpha.put(colorOrig, "#ffffffff");

            replaceTransparency(toolbarAlpha, (100 - prefs.getInt("wallpaper_alpha_toolbar", 30)) / 100.0f);
        }


        findAndHookMethod(Activity.class.getName(), classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                var colors = IColors.colors;
                var activity = (Activity) param.thisObject;
                var view = activity.findViewById(android.R.id.content).getRootView();
                replaceColors(view, colors);
            }
        });

        var intBgHook = new IntBgColorHook();
        findAndHookMethod(TextView.class.getName(), classLoader, "setTextColor", int.class, intBgHook);
        findAndHookMethod(Paint.class.getName(), classLoader, "setColor", int.class, intBgHook);
        findAndHookMethod(View.class.getName(), classLoader, "setBackgroundColor", int.class, intBgHook);
        findAndHookMethod(GradientDrawable.class.getName(), classLoader, "setColor", int.class, intBgHook);
        findAndHookMethod(ColorDrawable.class.getName(), classLoader, "setColor", int.class, intBgHook);
        findAndHookMethod(Notification.Builder.class.getName(), classLoader, "setColor", int.class, intBgHook);
        findAndHookMethod(Drawable.class.getName(), classLoader, "setTint", int.class, intBgHook);
        findAndHookMethod("com.whatsapp.CircularProgressBar", classLoader, "setProgressBarColor", int.class, intBgHook);
        findAndHookMethod("com.whatsapp.CircularProgressBar", classLoader, "setProgressBarBackgroundColor", int.class, intBgHook);

        var colorStateListHook = new ColorStateListHook();
        findAndHookMethod(Drawable.class.getName(), classLoader, "setTintList", ColorStateList.class, colorStateListHook);
        findAndHookMethod(customDrawable1, "setBackgroundTintList", ColorStateList.class, colorStateListHook);
        findAndHookMethod(customDrawable1, "setRippleColor", ColorStateList.class, colorStateListHook);
        findAndHookMethod(customDrawable1, "setSupportImageTintList", ColorStateList.class, colorStateListHook);

        findAndHookMethod(customDrawable2, "setTintList", ColorStateList.class, colorStateListHook);
        findAndHookMethod(customDrawable2, "setTint", int.class, intBgHook);

        findAndHookMethod(customDrawable3, "setTintList", ColorStateList.class, colorStateListHook);

        var inflaterHook = (XC_MethodHook) new LayoutInflaterHook();
        findAndHookMethod(LayoutInflater.class.getName(), classLoader, "inflate", int.class, ViewGroup.class, inflaterHook);
        findAndHookMethod(LayoutInflater.class.getName(), classLoader, "inflate", XmlPullParser.class, ViewGroup.class, inflaterHook);
        findAndHookMethod(LayoutInflater.class.getName(), classLoader, "inflate", int.class, ViewGroup.class, boolean.class, inflaterHook);
        findAndHookMethod(LayoutInflater.class.getName(), classLoader, "inflate", XmlPullParser.class, ViewGroup.class, boolean.class, inflaterHook);

        findAndHookMethod(View.class.getName(), classLoader, "setBackground", Drawable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var colors = IColors.colors;
                var drawable = (Drawable) param.args[0];
                replaceColor(drawable, colors);
            }
        });
    }

    private void replaceTransparency(HashMap<String, String> wallpaperColors, float mAlpha) {
        var hexAlpha = Integer.toHexString((int) Math.ceil(mAlpha * 255));
        hexAlpha = hexAlpha.length() == 1 ? "0" + hexAlpha : hexAlpha;
        for (var c : List.of("#ff0b141a", "#ff111b21", "#ff000000", "#ffffffff", "#ff1b8755")) {
            var oldColor = wallpaperColors.get(c);
            if (oldColor == null) continue;
            var newColor = "#" + hexAlpha + oldColor.substring(3);
            wallpaperColors.put(c, newColor);
            wallpaperColors.put(oldColor, newColor);
        }
    }

    private void injectWallpaper(View view) {
        var content = (ViewGroup) view;
        var rootView = (ViewGroup) content.getChildAt(0);
        var header = (ViewGroup) rootView.findViewById(Utils.getID("header", "id"));
        replaceColors(header, toolbarAlpha);

        var views = new ArrayList<View>();
        while (rootView.getChildCount() > 0) {
            views.add(rootView.getChildAt(0));
            rootView.removeView(rootView.getChildAt(0));
        }
        var frameLayout = new WallpaperView(rootView.getContext(), prefs);
        for (var v : views) {
            frameLayout.addView(v);
        }
        rootView.addView(frameLayout);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Change Colors";
    }

    public static class LayoutInflaterHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var colors = IColors.colors;
            var view = (View) param.getResult();
            if (view == null) return;
            replaceColors(view, colors);
        }
    }

    public static class ColorStateListHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            var colors = IColors.colors;
            var colorStateList = param.args[0];
            if (colorStateList != null) {
                var mColors = (int[]) XposedHelpers.getObjectField(colorStateList, "mColors");
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
        }
    }

    public static class IntBgColorHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            var colors = IColors.colors;
            var color = (int) param.args[0];
            var sColor = IColors.toString(color);
            if (param.thisObject instanceof TextView textView) {
                var id = Utils.getID("conversations_row_message_count", "id");
                if (textView.getId() == id) {
                    param.args[0] = IColors.parseColor("#ff" + sColor.substring(sColor.length() == 9 ? 3 : 1));
                    return;
                }
            }
            var newColor = colors.get(sColor);
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
        }
    }
}
