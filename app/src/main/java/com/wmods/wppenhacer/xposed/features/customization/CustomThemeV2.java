package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.utils.ColorReplacement.replaceColors;
import static com.wmods.wppenhacer.utils.DrawableColors.replaceColor;
import static com.wmods.wppenhacer.utils.IColors.alphacolors;
import static com.wmods.wppenhacer.utils.IColors.backgroundColors;
import static com.wmods.wppenhacer.utils.IColors.primaryColors;
import static com.wmods.wppenhacer.utils.IColors.textColors;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.utils.IColors;
import com.wmods.wppenhacer.views.WallpaperView;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.HashMap;
import java.util.Objects;
import java.util.Properties;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CustomThemeV2 extends Feature {

    private HashMap<String, String> wallAlpha;
    private HashMap<String, String> navAlpha;
    private HashMap<String, String> toolbarAlpha;
    private Properties properties;
//    private ViewGroup mContent;

    public CustomThemeV2(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    private static void processColors(String color, HashMap<String, String> mapColors) {
        String inputColorFull;
        if (color.length() == 7) {
            inputColorFull = "#ff" + color.substring(1);
        } else if (color.length() == 9) {
            inputColorFull = "#ff" + color.substring(3);
        } else {
            return;
        }

        int inputR, inputG, inputB;
        try {
            inputR = Integer.parseInt(inputColorFull.substring(3, 5), 16);
            inputG = Integer.parseInt(inputColorFull.substring(5, 7), 16);
            inputB = Integer.parseInt(inputColorFull.substring(7, 9), 16);
        } catch (NumberFormatException e) {
            return;
        }


        for (var c : mapColors.keySet()) {
            String value = mapColors.get(c);

            if (c.length() == 9) {
                String finalColorStr = inputColorFull;

                if (value != null && value.length() == 9 && !value.startsWith("#ff")) {
                    try {
                        int existingAlphaInt = Integer.parseInt(value.substring(1, 3), 16);
                        float alphaFactor = existingAlphaInt / 255.0f;

                        int newR = (int) (inputR * alphaFactor + 255 * (1 - alphaFactor));
                        int newG = (int) (inputG * alphaFactor + 255 * (1 - alphaFactor));
                        int newB = (int) (inputB * alphaFactor + 255 * (1 - alphaFactor));

                        newR = Math.max(0, Math.min(255, newR));
                        newG = Math.max(0, Math.min(255, newG));
                        newB = Math.max(0, Math.min(255, newB));

                        finalColorStr = String.format("#ff%02x%02x%02x", newR, newG, newB);

                    } catch (NumberFormatException e) {
                        finalColorStr = inputColorFull;
                    }
                }
                mapColors.put(c, finalColorStr);

            } else if (c.length() == 7) {
                mapColors.put(c, inputColorFull.substring(3));
            }
        }
    }

    @Override
    public void doHook() throws Throwable {
        properties = Utils.getProperties(prefs, "custom_css", "custom_filters");
        hookTheme();
        hookWallpaper();
        XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.ActivityThread", classLoader), "handleRelaunchActivity", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                loadAndApplyColors();
                loadAndApplyColorsWallpaper();
            }
        });
    }

    private void loadAndApplyColorsWallpaper() {
        if (prefs.getBoolean("lite_mode", false)) return;
        var customWallpaper = prefs.getBoolean("wallpaper", false);

        if (customWallpaper || properties.containsKey("wallpaper")) {

            wallAlpha = new HashMap<>(IColors.colors);
            var wallpaperAlpha = customWallpaper ? prefs.getInt("wallpaper_alpha", 30) : Utils.tryParseInt(properties.getProperty("wallpaper_alpha"), 30);
            replaceTransparency(wallAlpha, (100 - wallpaperAlpha) / 100.0f);

            navAlpha = new HashMap<>(IColors.colors);
            var wallpaperAlphaNav = customWallpaper ? prefs.getInt("wallpaper_alpha_navigation", 30) : Utils.tryParseInt(properties.getProperty("wallpaper_alpha_navigation"), 30);
            replaceTransparency(navAlpha, (100 - wallpaperAlphaNav) / 100.0f);

            toolbarAlpha = new HashMap<>(IColors.colors);

            var wallpaperToolbarAlpha = customWallpaper ? prefs.getInt("wallpaper_alpha_toolbar", 30) : Utils.tryParseInt(properties.getProperty("wallpaper_alpha_toolbar"), 30);
            replaceTransparency(toolbarAlpha, (100 - wallpaperToolbarAlpha) / 100.0f);
        }
    }

    private static HashMap<String, String> revertColors(HashMap<String, String> colors) {
        HashMap<String, String> newColors = new HashMap<>();
        for (var c : colors.keySet()) {
            var color = colors.get(c);
            newColors.put(color, c);
        }
        return newColors;
    }

    private void hookWallpaper() throws Exception {

        if (!prefs.getBoolean("wallpaper", false))
            return;

        loadAndApplyColorsWallpaper();

        var homeActivityClass = WppCore.getHomeActivityClass(classLoader);
        XposedHelpers.findAndHookMethod(homeActivityClass, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    injectWallpaper(activity.findViewById(Utils.getID("root_view", "id")));
                }
            }
        });

//        var revertWallAlpha = revertColors(wallAlpha);

//        WppCore.addListenerActivity((activity, type) -> {
//            var isHome = homeActivityClass.isInstance(activity);
//            if (WppCore.ActivityChangeState.ChangeType.RESUMED == type && isHome) {
//                mContent = activity.findViewById(android.R.id.content);
//                if (mContent != null) {
//                    replaceColors(mContent, wallAlpha);
//                }
//            } else if (WppCore.ActivityChangeState.ChangeType.CREATED == type && !isHome &&
//                    !activity.getClass().getSimpleName().equals("QuickContactActivity") && !DesignUtils.isNightMode()) {
//                if (mContent != null) {
//                    replaceColors(mContent, revertWallAlpha);
//                }
//            }
//        });

        var hookFragmentView = Unobfuscator.loadFragmentViewMethod(classLoader);

        XposedBridge.hookMethod(hookFragmentView,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (checkNotHomeActivity()) return;
                        var viewGroup = (ViewGroup) param.getResult();
                        replaceColors(viewGroup, wallAlpha);
                    }
                });

        var loadTabFrameClass = Unobfuscator.loadTabFrameClass(classLoader);
        XposedHelpers.findAndHookMethod(FrameLayout.class, "onMeasure", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!loadTabFrameClass.isInstance(param.thisObject)) return;
                var viewGroup = (ViewGroup) param.thisObject;
                if (checkNotHomeActivity()) return;
                var background = viewGroup.getBackground();
                replaceColor(background, navAlpha);
            }
        });


    }


    public void hookTheme() throws Throwable {
        loadAndApplyColors();

        XposedBridge.hookAllMethods(AssetManager.class, "getResourceValue", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var typedValue = (TypedValue) param.args[2];
                if (typedValue.type >= TypedValue.TYPE_FIRST_INT
                        && typedValue.type <= TypedValue.TYPE_LAST_INT) {
                    if (typedValue.data == 0) return;
                    if (checkNotApplyColor(typedValue.data)) return;
                    typedValue.data = IColors.getFromIntColor(typedValue.data, IColors.colors);
                }
            }
        });

        var resourceImpl = XposedHelpers.findClass("android.content.res.ResourcesImpl", classLoader);

        XposedBridge.hookAllMethods(resourceImpl, "loadDrawable", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var drawable = (Drawable) param.getResult();
                replaceColor(drawable, IColors.colors);
            }
        });

        XposedBridge.hookAllMethods(resourceImpl, "loadColorStateList", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var colorStateList = (ColorStateList) param.getResult();
                var mColors = (int[]) XposedHelpers.getObjectField(colorStateList, "mColors");
                for (var i = 0; i < mColors.length; i++) {
                    mColors[i] = IColors.getFromIntColor(mColors[i], IColors.colors);
                }
            }
        });
        var intBgHook = new IntBgColorHook();
        findAndHookMethod(Paint.class, "setColor", int.class, intBgHook);

        Class<?> filterItemClass = Unobfuscator.loadFilterItemClass(classLoader);

        XposedBridge.hookAllConstructors(filterItemClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.args[0];
                var textView = (TextView) view.findViewById(Utils.getID("text_view", "id"));
                textView.setTextColor(DesignUtils.getPrimaryTextColor());
            }
        });

//        Method activeButtonNav = Unobfuscator.loadActiveButtonNav(classLoader);
//
//        XposedBridge.hookMethod(activeButtonNav, new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                var drawable = (Drawable) param.args[0];
//                DrawableColors.replaceColor(drawable, alphacolors);
//            }
//        });
    }

    public void loadAndApplyColors() {

        IColors.initColors();

        var primaryColorInt = prefs.getInt("primary_color", 0);
        var textColorInt = prefs.getInt("text_color", 0);
        var backgroundColorInt = prefs.getInt("background_color", 0);

        var primaryColor = DesignUtils.checkSystemColor(properties.getProperty("primary_color", "0"));
        var textColor = DesignUtils.checkSystemColor(properties.getProperty("text_color", "0"));
        var backgroundColor = DesignUtils.checkSystemColor(properties.getProperty("background_color", "0"));

        if (prefs.getBoolean("changecolor", false)) {
            primaryColor = primaryColorInt == 0 ? "0" : IColors.toString(primaryColorInt);
            textColor = textColorInt == 0 ? "0" : IColors.toString(textColorInt);
            backgroundColor = backgroundColorInt == 0 ? "0" : IColors.toString(backgroundColorInt);
        }

        if (!DesignUtils.isNightMode()) {
            textColors.clear();
            textColors.putAll(backgroundColors);
            backgroundColors.clear();
        }

        if (prefs.getBoolean("changecolor", false) || Objects.equals(properties.getProperty("change_colors"), "true")) {

            if (!primaryColor.equals("0") && DesignUtils.isValidColor(primaryColor)) {
                processColors(primaryColor, primaryColors);
                processColors(primaryColor, alphacolors);
            }

            if (!textColor.equals("0") && DesignUtils.isValidColor(textColor)) {
                processColors(textColor, textColors);
            }

            if (!backgroundColor.equals("0") && DesignUtils.isValidColor(backgroundColor)) {
                processColors(backgroundColor, backgroundColors);
            }

            var entries = alphacolors.entrySet();
            var newAlphaColors = new HashMap<String, String>();
            for (var entry : entries) {
                var color = primaryColors.getOrDefault(entry.getKey(), null);
                if (color == null) {
                    newAlphaColors.put(entry.getKey(), entry.getValue());
                    continue;
                }
                var realColor = entry.getValue();
                newAlphaColors.put(color, realColor);
            }
            alphacolors = newAlphaColors;
        }

        IColors.colors.putAll(primaryColors);
        IColors.colors.putAll(textColors);
        IColors.colors.putAll(backgroundColors);
        primaryColors.clear();
        textColors.clear();

        if (!DesignUtils.isNightMode()) {
            backgroundColors.clear();
            backgroundColors.put("#ff1b8755", "#ffffffff");
            backgroundColors.put("#ffffffff", "#ffffffff");
            backgroundColors.put("ffffff", "ffffff");
        }

    }

    private void replaceTransparency(HashMap<String, String> wallpaperColors, float mAlpha) {
        var hexAlpha = Integer.toHexString((int) Math.ceil(mAlpha * 255));
        hexAlpha = hexAlpha.length() == 1 ? "0" + hexAlpha : hexAlpha;
        for (var c : backgroundColors.keySet()) {
            var oldColor = wallpaperColors.getOrDefault(c, backgroundColors.get(c));
            if (oldColor == null || oldColor.length() < 9) continue;
            var newColor = "#" + hexAlpha + oldColor.substring(3);
            wallpaperColors.put(c, newColor);
            wallpaperColors.put(oldColor, newColor);
        }
    }

    private void injectWallpaper(View view) {
        var content = (ViewGroup) view;
        var rootView = (ViewGroup) content.getChildAt(0);

        var header = content.findViewById(Utils.getID("header", "id"));
        replaceColors(header, toolbarAlpha);
        var frameLayout = new WallpaperView(rootView.getContext(), prefs, properties);
        rootView.addView(frameLayout, 0);
    }

    private boolean checkNotHomeActivity() {
        var homeClass = WppCore.getHomeActivityClass(classLoader);
        var currentActivity = WppCore.getCurrentActivity();
        return (currentActivity == null || !homeClass.isInstance(currentActivity));
    }

    private static int getOriginalColor(String sColor) {
        var colors = IColors.colors.keySet();
        var resultColor = -1;
        for (var c : colors) {
            var vColor = IColors.colors.getOrDefault(c, "");
            if (vColor.length() < 9) continue;
            if (sColor.equals(vColor)) {
                resultColor = IColors.parseColor(c);
                break;
            }
        }
        return resultColor;
    }

    private boolean checkNotApplyColor(int color) {
        var activity = WppCore.getCurrentActivity();
        if (activity != null && activity.getClass().getSimpleName().equals("Conversation") && ReflectionUtils.isCalledFromStrings("getValue") && !ReflectionUtils.isCalledFromStrings("android.view")) {
            return color != 0xff12181c;
        }
        return false;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Custom Theme V2";
    }


    public static class IntBgColorHook extends XC_MethodHook {


        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            var color = (int) param.args[0];

            if (param.thisObject instanceof TextView textView) {
                var id = Utils.getID("conversations_row_message_count", "id");
                if (textView.getId() == id) {
                    return;
                }
            } else if (param.thisObject instanceof Paint && ReflectionUtils.isCalledFromStrings("getValue")) {
                return;
            }
            param.args[0] = IColors.getFromIntColor(color, IColors.colors);
        }
    }


}
