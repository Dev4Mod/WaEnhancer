package com.wmods.wppenhacer.xposed.features.customization;

import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.utils.DrawableColors;
import com.wmods.wppenhacer.utils.IColors;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.Objects;
import java.util.Properties;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CustomThemeV2 extends Feature {

    public CustomThemeV2(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        Properties properties = Utils.extractProperties(prefs.getString("custom_css", ""));

        var primaryColorInt = prefs.getInt("primary_color", 0);
        var secondaryColorInt = prefs.getInt("secondary_color", 0);
        var backgroundColorInt = prefs.getInt("background_color", 0);

        var primaryColor = DesignUtils.checkSystemColor(properties.getProperty("primary_color", "0"));
        var secondaryColor = DesignUtils.checkSystemColor(properties.getProperty("secondary_color", "0"));
        var backgroundColor = DesignUtils.checkSystemColor(properties.getProperty("background_color", "0"));

        if (prefs.getBoolean("changecolor", false)) {
            primaryColor = primaryColorInt == 0 ? "0" : IColors.toString(primaryColorInt);
            secondaryColor = secondaryColorInt == 0 ? "0" : IColors.toString(secondaryColorInt);
            backgroundColor = backgroundColorInt == 0 ? "0" : IColors.toString(backgroundColorInt);
        }

        if (prefs.getBoolean("changecolor", false) || Objects.equals(properties.getProperty("change_colors"), "true")) {
            for (var c : IColors.colors.keySet()) {
                if (!primaryColor.equals("0") && DesignUtils.isValidColor(primaryColor)) {
                    primaryColor = primaryColor.length() == 9 ? primaryColor : "#ff" + primaryColor.substring(1);
                    switch (c) {
                        case "00a884", "1da457", "21c063", "d9fdd3" ->
                                IColors.colors.put(c, primaryColor.substring(3));
                        case "#ff00a884", "#ff1da457", "#ff21c063", "#ff1daa61", "#ff25d366",
                             "#ffd9fdd3" -> IColors.colors.put(c, primaryColor);
                        case "#ff103529" ->
                                IColors.colors.put(c, "#66" + primaryColor.substring(3));
                    }
                }

                if (!backgroundColor.equals("0") && DesignUtils.isValidColor(backgroundColor)) {
                    backgroundColor = backgroundColor.length() == 9 ? backgroundColor : "#ff" + backgroundColor.substring(1);
                    switch (c) {
                        case "0b141a", "0a1014" ->
                                IColors.colors.put(c, backgroundColor.substring(3));
                        case "#ff0b141a", "#ff111b21", "#ff000000", "#ff0a1014", "#ff10161a",
                             "#ff12181c", "#ff20272b" -> IColors.colors.put(c, backgroundColor);
                    }
                }

                if (!secondaryColor.equals("0") && DesignUtils.isValidColor(secondaryColor)) {
                    secondaryColor = secondaryColor.length() == 9 ? secondaryColor : "#ff" + secondaryColor.substring(1);
                    if (c.equals("#ff202c33") || c.equals("#ff2a2f33")) {
                        IColors.colors.put(c, secondaryColor);
                    }
                }
            }
        }

        XposedBridge.hookAllMethods(AssetManager.class, "getResourceValue", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var id = (int) param.args[0];
                var typedValue = (TypedValue) param.args[2];
                if (id >= 0x7f060000 && id <= 0x7f06ffff) {
                    if (typedValue.type >= TypedValue.TYPE_FIRST_INT
                            && typedValue.type <= TypedValue.TYPE_LAST_INT) {
                        if (typedValue.data == 0) return;
                        typedValue.data = IColors.getFromIntColor(typedValue.data, IColors.colors);
                    }
                }
            }
        });

        var resourceImpl = XposedHelpers.findClass("android.content.res.ResourcesImpl", classLoader);
        XposedBridge.hookAllMethods(resourceImpl, "loadDrawable", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var drawable = (Drawable) param.getResult();
                DrawableColors.replaceColor(drawable, IColors.colors);
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Custom Theme V2";
    }

}
