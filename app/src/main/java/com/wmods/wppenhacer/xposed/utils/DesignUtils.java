package com.wmods.wppenhacer.xposed.utils;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.XResources;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
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

import java.util.HashMap;
import java.util.Map;

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
        var textColor = mPrefs.getInt("text_color", 0);
        if (textColor == 0 || !mPrefs.getBoolean("changecolor", false)) {
            return DesignUtils.isNightMode() ? 0xfffffffe : 0xff000001;
        }
        return textColor;
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

    public static Drawable generatePrimaryColorDrawable(Drawable drawable) {
        if (drawable == null) return null;
        var primaryColorInt = mPrefs.getInt("primary_color", 0);
        if (primaryColorInt != 0 && mPrefs.getBoolean("changecolor", false)) {
            var bitmap = drawableToBitmap(drawable);
            var color = getDominantColor(bitmap);
            bitmap = replaceColor(bitmap, color, primaryColorInt, 120);
            return new BitmapDrawable(Utils.getApplication().getResources(), bitmap);
        }
        return null;
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
        return WppCore.getDefaultTheme() <= 0 ? isNightModeBySystem() : WppCore.getDefaultTheme() == 2;
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

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static int getDominantColor(Bitmap bitmap) {
        Map<Integer, Integer> colorCountMap = new HashMap<>();

        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int color = bitmap.getPixel(x, y);
                if (Color.alpha(color) > 0) { // Ignore pixels que são totalmente transparentes
                    colorCountMap.put(color, colorCountMap.getOrDefault(color, 0) + 1);
                }
            }
        }

        return colorCountMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Color.BLACK); // Retorna preto se não encontrar nenhuma cor
    }

    public static double colorDistance(int color1, int color2) {
        int r1 = Color.red(color1);
        int g1 = Color.green(color1);
        int b1 = Color.blue(color1);

        int r2 = Color.red(color2);
        int g2 = Color.green(color2);
        int b2 = Color.blue(color2);

        return Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2));
    }

    public static Bitmap replaceColor(Bitmap bitmap, int oldColor, int newColor, double threshold) {
        Bitmap newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        for (int y = 0; y < newBitmap.getHeight(); y++) {
            for (int x = 0; x < newBitmap.getWidth(); x++) {
                int currentColor = newBitmap.getPixel(x, y);
                if (colorDistance(currentColor, oldColor) < threshold) {
                    newBitmap.setPixel(x, y, newColor);
                }
            }
        }

        return newBitmap;
    }

    public static Drawable resizeDrawable(Drawable icon, int width, int height) {
        // resize icon
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        icon.draw(canvas);
        return new BitmapDrawable(Utils.getApplication().getResources(), bitmap);
    }
}
