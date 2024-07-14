package com.wmods.wppenhacer.xposed.utils;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.XResources;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.WppXposed;
import com.wmods.wppenhacer.xposed.core.WppCore;

import de.robv.android.xposed.XposedBridge;

public class DesignUtils {

    private static SharedPreferences mPrefs;


    @SuppressLint("UseCompatLoadingForDrawables")
    public static Drawable getDrawable(int id) {
        return Utils.getApplication().getDrawable(id);
    }


    @Nullable
    public static Drawable getDrawableByName(String name) {
        var id = Utils.getID(name, "drawable");
        if (id == 0) return null;
        return DesignUtils.getDrawable(id);
    }

    @Nullable
    public static Drawable getIconByName(String name, boolean isTheme) {
        var id = Utils.getID(name, "drawable");
        if (id == 0) return null;
        var icon = DesignUtils.getDrawable(id);
        if (isTheme && icon != null) {
            return DesignUtils.coloredDrawable(icon, isNightMode() ? Color.WHITE : Color.BLACK);
        }
        return icon;
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

    @NonNull
    public static Drawable createDrawable(String type, int color) {
        switch (type) {
            case "rc_dialog_bg" -> {
                var border = Utils.dipToPixels(12.0f);
                var shapeDrawable = new ShapeDrawable(new RoundRectShape(new float[]{border, border, border, border, 0, 0, 0, 0}, null, null));
                shapeDrawable.getPaint().setColor(color);
                return shapeDrawable;
            }
            case "selector_bg" -> {
                var border = Utils.dipToPixels(18.0f);
                ShapeDrawable selectorBg = new ShapeDrawable(new RoundRectShape(new float[]{border, border, border, border, border, border, border, border}, null, null));
                selectorBg.getPaint().setColor(color);
                return selectorBg;
            }
            case "rc_dotline_dialog" -> {
                var border = Utils.dipToPixels(16.0f);
                ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(new float[]{border, border, border, border, border, border, border, border}, null, null));
                shapeDrawable.getPaint().setColor(color);
                return shapeDrawable;
            }
            case "stroke_border" -> {
                float radius = Utils.dipToPixels(18.0f);
                float[] outerRadii = new float[]{radius, radius, radius, radius, radius, radius, radius, radius};
                RoundRectShape roundRectShape = new RoundRectShape(outerRadii, null, null);
                ShapeDrawable shapeDrawable = new ShapeDrawable(roundRectShape);
                Paint paint = shapeDrawable.getPaint();
                paint.setColor(Color.TRANSPARENT);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Utils.dipToPixels(2));
                paint.setColor(color);
                int inset = Utils.dipToPixels(2);
                return new InsetDrawable(shapeDrawable, inset, inset, inset, inset);

            }
        }
        return new ColorDrawable(Color.BLACK);
    }

    // Colors
    public static int getPrimaryTextColor() {
        return DesignUtils.isNightMode() ? 0xfffffffe : 0xff000001;
    }


    public static int getUnSeenColor() {
        var primaryColor = mPrefs.getInt("primary_color", 0);
        if (primaryColor == 0 || !mPrefs.getBoolean("changecolor", false)) {
            return 0xFF25d366;
        }
        return primaryColor;
    }

    public static int getPrimarySurfaceColor() {
        var backgroundColor = mPrefs.getInt("background_color", 0);
        if (backgroundColor == 0 || !mPrefs.getBoolean("changecolor", false)) {
            return DesignUtils.isNightMode() ? 0xff121212 : 0xfffffffe;
        }
        return backgroundColor;
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

    public static boolean isNightMode() {
        return WppCore.getDefaultTheme() == -1 ? isNightModeBySystem() : WppCore.getDefaultTheme() == 2;
    }

    public static boolean isNightModeBySystem() {
        return (Utils.getApplication().getResources().getConfiguration().uiMode & 48) == 32;
    }


    public static void setPrefs(SharedPreferences mPrefs) {
        DesignUtils.mPrefs = mPrefs;
    }

    public static boolean isValidColor(String primaryColor) {
        try {
            Color.parseColor(primaryColor);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String checkSystemColor(String color) {
        if (DesignUtils.isValidColor(color)) {
            return color;
        }
        try {
            if (color.startsWith("color_")) {
                var idColor = color.replace("color_", "");
                var colorRes = android.R.color.class.getField(idColor).getInt(null);
                if (colorRes != -1) {
                    return "#" + Integer.toHexString(ContextCompat.getColor(Utils.getApplication(), colorRes));
                }
            }
        } catch (Exception e) {
            XposedBridge.log("Error: " + e);
        }
        return "0";
    }
}
