package com.wmods.wppenhacer.utils;

import static com.wmods.wppenhacer.utils.IColors.parseColor;
import static com.wmods.wppenhacer.xposed.features.customization.CustomTheme.classLoader;

import android.content.res.ColorStateList;
import android.graphics.NinePatch;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;

import java.util.HashMap;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DrawableColors {

    public static void replaceColor(Drawable drawable, HashMap<String, String> colors) {
        if (drawable instanceof StateListDrawable stateListDrawable) {
            var count = StateListDrawableCompact.getStateCount(stateListDrawable);
            for (int i = 0; i < count; i++) {
                var stateDrawable = StateListDrawableCompact.getStateDrawable(stateListDrawable, i);
                if (stateDrawable != null)
                    replaceColor(stateDrawable, colors);
            }
        } else if (drawable instanceof DrawableContainer drawableContainer) {
            var containerState = drawableContainer.getConstantState();
            var drawables = (Drawable[]) XposedHelpers.getObjectField(containerState, "mDrawables");
            for (var drawable1 : drawables) {
                replaceColor(drawable1, colors);
            }
        } else if (drawable instanceof LayerDrawable layerDrawable) {
            var layerState = layerDrawable.getConstantState();
            var mChildren = (Object[]) XposedHelpers.getObjectField(layerState, "mChildren");
            for (var childDrawable : mChildren) {
                if (childDrawable != null) {
                    var drawable1 = (Drawable) XposedHelpers.getObjectField(childDrawable, "mDrawable");
                    replaceColor(drawable1, colors);
                }
            }
        } else if (drawable instanceof GradientDrawable gradientDrawable) {
            var gradientColors = gradientDrawable.getColors();
            if (gradientColors != null) {
                for (var i = 0; i < gradientColors.length; i++) {
                    var gradientColor = IColors.toString(gradientColors[i]);
                    var newColor = colors.get(gradientColor);
                    if (newColor != null && newColor.contains("121212")){
                        XposedBridge.log(new Throwable());
                    }

                    if (newColor != null) {
                        gradientColors[i] = IColors.parseColor(newColor);
                    } else {
                        if (!gradientColor.startsWith("#ff") && !gradientColor.startsWith("#0")) {
                            var sColorSub = gradientColor.substring(0, 3);
                            newColor = colors.get(gradientColor.substring(3));
                            if (newColor != null)
                                gradientColors[i] = IColors.parseColor(sColorSub + newColor);
                        }
                    }
                }
                gradientDrawable.setColors(gradientColors);
            }
        } else if (drawable instanceof InsetDrawable insetDrawable) {
            replaceColor(insetDrawable.getDrawable(), colors);
        } else if (drawable instanceof NinePatchDrawable ninePatchDrawable) {
            var color = getNinePatchDrawableColor(ninePatchDrawable);
            var sColor = IColors.toString(color);
            var newColor = colors.get(sColor);
            if (newColor != null && newColor.contains("121212")){
                XposedBridge.log(new Throwable());
            }
            if (newColor != null) {
                ninePatchDrawable.setTintList(ColorStateList.valueOf(parseColor(newColor)));
            }
        } else {
            if (drawable == null) return;
            var color = getColor(drawable);
            var sColor = IColors.toString(color);
            var newColor = colors.get(sColor);
            if (newColor != null && newColor.contains("121212")){
                XposedBridge.log(new Throwable());
            }
            if (newColor != null) {
                drawable.setColorFilter(new PorterDuffColorFilter(parseColor(newColor), PorterDuff.Mode.SRC_IN));
            } else {
                if (!sColor.startsWith("#ff") && !sColor.startsWith("#0")) {
                    var sColorSub = sColor.substring(0, 3);
                    newColor = colors.get(sColor.substring(3));
                    if (newColor != null) {
                        drawable.setColorFilter(new PorterDuffColorFilter(parseColor(sColorSub + newColor), PorterDuff.Mode.SRC_IN));
                    }
                }
            }
        }

    }

    public static int getColor(Drawable drawable) {
        if (drawable == null) return 0;

        int color = 0;

        if (drawable instanceof ColorDrawable colorDrawable) {
            color = getColorDrawableColor(colorDrawable);
        } else if (drawable instanceof ShapeDrawable shapeDrawable) {
            color = getShapeDrawableColor(shapeDrawable);
        } else if (drawable instanceof RippleDrawable rippleDrawable) {
            color = getRippleDrawableColor(rippleDrawable);
        } else if (drawable instanceof NinePatchDrawable ninePatchDrawable) {
            color = getNinePatchDrawableColor(ninePatchDrawable);
        } else if (drawable instanceof InsetDrawable insetDrawable) {
            color = getInsetDrawableColor(insetDrawable);
        }
//        if (colors.get(IColors.toString(color)) == null) {
//            XposedBridge.log("(getColor) Color: " + IColors.toString(color) + " / Class: " + drawable.getClass());
//        }
        return color;
    }

    private static int getInsetDrawableColor(InsetDrawable insetDrawable) {
        var mDrawable = (Drawable) XposedHelpers.getObjectField(insetDrawable, "mDrawable");
        return getColor(mDrawable);
    }

    public static int getNinePatchDrawableColor(NinePatchDrawable ninePatchDrawable) {
        var state = ninePatchDrawable.getConstantState();
        var ninePatch = (NinePatch) XposedHelpers.getObjectField(state, "mNinePatch");
        var bitmap = ninePatch.getBitmap();

        var width = bitmap.getWidth();
        var height = bitmap.getHeight();

        //        XposedBridge.log("--> Bitmap color: " + IColors.toString(color.toArgb()));
        return bitmap.getPixel(width / 2, height / 2);
    }

    private static int getRippleDrawableColor(RippleDrawable rippleDrawable) {
        var state = rippleDrawable.getConstantState();
        var rippleStateClass = XposedHelpers.findClass("android.graphics.drawable.RippleDrawable.RippleState", classLoader);
        try {
            return XposedHelpers.getIntField(state, "mColor");
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static int getColorDrawableColor(ColorDrawable colorDrawable) {
        return colorDrawable.getColor();
    }

    public static int getShapeDrawableColor(ShapeDrawable shapeDrawable) {
        return shapeDrawable.getPaint().getColor();
    }

}
