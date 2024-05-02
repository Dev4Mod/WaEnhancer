package com.wmods.wppenhacer.xposed.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.content.res.XResources;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.WppXposed;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DesignUtils {

    private static SharedPreferences mPrefs;


    @SuppressLint("UseCompatLoadingForDrawables")
    public static Drawable getDrawable(int id) {
        return Utils.getApplication().getDrawable(id);
    }


    public static Drawable getDrawableByName(String name) {
        var id = Utils.getID(name, "drawable");
        return DesignUtils.getDrawable(id);
    }

    @NonNull
    public static Drawable coloredDrawable(Drawable drawable, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawable.setColorFilter(new BlendModeColorFilter(color, BlendMode.SRC_ATOP));
        } else {
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        }
        return drawable;
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    public static Drawable alphaDrawable(Drawable drawable, int primaryTextColor, int i) {
        Drawable coloredDrawable = DesignUtils.coloredDrawable(drawable, primaryTextColor);
        coloredDrawable.setAlpha(i);
        return coloredDrawable;
    }

    public static Drawable createDrawable(String type) {
        switch (type) {
            case "rc_dialog_bg" -> {
                var shapeDrawable = new ShapeDrawable();
                shapeDrawable.getPaint().setColor(Color.BLACK);
                return shapeDrawable;
            }
            case "selector_bg" -> {
                var border = Utils.dipToPixels(18.0f);
                ShapeDrawable selectorBg = new ShapeDrawable(new RoundRectShape(new float[]{border, border, border, border, border, border, border, border}, null, null));
                selectorBg.getPaint().setColor(Color.BLACK);
                return selectorBg;
            }
            case "rc_dotline_dialog" -> {
                var border = Utils.dipToPixels(16.0f);
                ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(new float[]{border, border, border, border, border, border, border, border}, null, null));
                shapeDrawable.getPaint().setColor(0x28FFFFFF);
                return shapeDrawable;
            }
        }
        return null;
    }

    // Colors
    public static int getPrimaryTextColor(Context context) {
        try {
            var resourceId = (int) XposedHelpers.callMethod(context, "getThemeResId");
            @SuppressLint("ResourceType")
            TypedArray values = context.getTheme().obtainStyledAttributes(resourceId, new int[]{android.R.attr.textColorPrimary});
            return values.getColor(0, 0);
        } catch (Exception e) {
            XposedBridge.log("Error while getting colors: " + e);
        }
        return 0;
    }

    public static int getPrimaryColor(Context context) {
        try {
            var resourceId = (int) XposedHelpers.callMethod(context, "getThemeResId");
            @SuppressLint("ResourceType")
            TypedArray values = context.getTheme().obtainStyledAttributes(resourceId, new int[]{android.R.attr.colorPrimary});
            return values.getColor(0, 0);
        } catch (Exception e) {
            XposedBridge.log("Error while getting colors: " + e);
        }
        return 0;
    }

    public static int getUnSeenColor() {
        var primaryColor = mPrefs.getInt("primary_color", 0);
        if (primaryColor == 0 || !mPrefs.getBoolean("changecolor", false)) {
            return 0xFF25d366;
        }
        return primaryColor;
    }

    public static int getPrimarySurfaceColor(Context context) {
        try {
            var resourceId = (int) XposedHelpers.callMethod(context, "getThemeResId");
            @SuppressLint("ResourceType")
            TypedArray values = context.getTheme().obtainStyledAttributes(resourceId, new int[]{android.R.attr.windowBackground});
            return values.getColor(0, 0);
        } catch (Exception e) {
            XposedBridge.log("Error while getting colors: " + e);
        }
        return 0;
    }

    public static void setReplacementDrawable(String name, Drawable replacement) {
        if (WppXposed.ResParam == null) return;
        WppXposed.ResParam.res.setReplacement(Utils.getApplication().getPackageName(), "drawable", name, new XResources.DrawableLoader() {
            @Override
            public Drawable newDrawable(XResources res, int id) throws Throwable {
                return replacement;
            }
        });
    }

    public static void setPrefs(SharedPreferences mPrefs) {
        DesignUtils.mPrefs = mPrefs;
    }
}
