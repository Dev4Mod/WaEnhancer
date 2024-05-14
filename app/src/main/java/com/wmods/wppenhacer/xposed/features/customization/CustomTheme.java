package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.utils.ColorReplacement.replaceColors;
import static com.wmods.wppenhacer.utils.DrawableColors.replaceColor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.utils.IColors;
import com.wmods.wppenhacer.views.WallpaperView;
import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.ShowEditMessage;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CustomTheme extends Feature {

    public static ClassLoader classLoader;
    private HashMap<String, String> newColors;
    private boolean isWallpaper;

    public CustomTheme(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
        classLoader = loader;
    }

    @Override
    public void doHook() throws Exception {
        hookWallpaper();
        hookColors();
    }

    private void hookWallpaper() {
        if (!prefs.getBoolean("wallpaper", false)) return;
        var clazz = XposedHelpers.findClass("com.whatsapp.HomeActivity", loader);
        XposedHelpers.findAndHookMethod(clazz.getSuperclass(), "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    injectWallpaper(activity.findViewById(android.R.id.content));
                }
            }
        });
    }

    private void hookColors() throws Exception {
        var customDrawable1 = Unobfuscator.loadExpandableWidgetClass(loader);
        logDebug("customDrawable1: " + customDrawable1.getName());
        var customDrawable2 = Unobfuscator.loadMaterialShapeDrawableClass(loader);
        logDebug("customDrawable2: " + customDrawable2.getName());
        var customDrawable3 = Unobfuscator.loadCustomDrawableClass(loader);
        logDebug("customDrawable3: " + customDrawable3.getName());


        var primaryColorInt = prefs.getInt("primary_color", 0);
        var secondaryColorInt = prefs.getInt("secondary_color", 0);
        var backgroundColorInt = prefs.getInt("background_color", 0);

        var primaryColor = primaryColorInt == 0 ? "0" : String.format("#%08x", primaryColorInt);
        var secondaryColor = secondaryColorInt == 0 ? "0" : String.format("#%08x", secondaryColorInt);
        var backgroundColor = backgroundColorInt == 0 ? "0" : String.format("#%08x", backgroundColorInt);

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
//                        case "0b141a" -> IColors.colors.put(c, backgroundColor.substring(3));
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

        var mAlpha = (100 - prefs.getInt("wallpaper_alpha", 30)) / 100.0f;
        var hexAlpha = Integer.toHexString((int) Math.floor(mAlpha * 255f)).toLowerCase();
        hexAlpha = hexAlpha.length() == 1 ? "0" + hexAlpha : hexAlpha;
        var bgColors = List.of("#ff0b141a", "#ff111b21", "#ff000000");
        newColors = new HashMap<>(IColors.colors);
        for (var color : bgColors) {
            var bgColor = IColors.colors.get(color);
            var alphaBgColor = "#" + hexAlpha + bgColor.substring(3);
            newColors.put(color, alphaBgColor);
            IColors.colors.put(alphaBgColor, bgColor);
            if (Objects.equals(hexAlpha, "00")) {
                IColors.colors.put(alphaBgColor.substring(3), bgColor);
            }
        }
        isWallpaper = prefs.getBoolean("wallpaper", false);

        findAndHookMethod(Activity.class.getName(), loader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                var colors = getAlpha();
                var activity = (Activity) param.thisObject;
                var view = activity.findViewById(android.R.id.content).getRootView();
                replaceColors(view, colors);
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

//        findAndHookMethod(View.class.getName(), loader, "setBackground", Drawable.class, new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                var colors = getAlpha();
//                var drawable = (Drawable) param.args[0];
//                replaceColor(drawable, colors);
//            }
//        });
    }

    private HashMap<String, String> getAlpha() {
        if (!isWallpaper) return IColors.colors;
//        if (!Unobfuscator.isCalledFromString("HomeActivity.onCreate")) return IColors.colors;
        if (Unobfuscator.isCalledFromClass(Window.class)) return IColors.colors;
        if (Unobfuscator.isCalledFromStrings(
                "setStatusBarColor", "WDSToolbar", "WDSFab", "onPreparePanel"
                , "onCreateOptionsMenu", "onLongClick", "PhoneWindow", "Image"))
            return IColors.colors;
        if (Unobfuscator.isCalledFromClass(ShowEditMessage.class)) return IColors.colors;
        if (Unobfuscator.isCalledFromClass(DesignUtils.class)) return IColors.colors;
        return newColors;
    }

    private void injectWallpaper(View view) {
        var content = (ViewGroup) view;
        var rootView = (ViewGroup) content.getChildAt(0);
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

    public class LayoutInflaterHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var colors = getAlpha();
            var view = (View) param.getResult();
            if (view == null) return;
            replaceColors(view, colors);
        }
    }

    public class ColorStateListHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            var colors = getAlpha();
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

    public class IntBgColorHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            var colors = getAlpha();
            var color = (int) param.args[0];
            var sColor = IColors.toString(color);
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
