package com.wmods.wppenhacer.utils;

import static com.wmods.wppenhacer.xposed.features.customization.CustomTheme.loader1;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
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

import com.wmods.wppenhacer.xposed.utils.DesignUtils;

import java.util.HashMap;

import de.robv.android.xposed.XposedHelpers;

public class DrawableColors {

    private static final HashMap<Bitmap, Integer> ninePatchs = new HashMap<>();

    public static void replaceColor(Drawable drawable, HashMap<String, String> colors) {
        if (drawable == null) return;

        if (DesignUtils.isNightMode()) {
            colors.remove("#ffffffff");
        }

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
                    var color = gradientColors[i];
                    var newColor = IColors.getFromIntColor(color, colors);
                    if (color == newColor) continue;
                    gradientColors[i] = newColor;
                }
                gradientDrawable.setColors(gradientColors);
            }
        } else if (drawable instanceof InsetDrawable insetDrawable) {
            replaceColor(insetDrawable.getDrawable(), colors);
        } else if (drawable instanceof NinePatchDrawable ninePatchDrawable) {
            var color = getNinePatchDrawableColor(ninePatchDrawable);
            var newColor = IColors.getFromIntColor(color, colors);
            if (color == newColor) return;
            ninePatchDrawable.setTintList(ColorStateList.valueOf(newColor));
        } else if (drawable instanceof ColorDrawable colorDrawable) {
            var color = getColorDrawableColor(colorDrawable);
            colorDrawable.setColor(IColors.getFromIntColor(color, colors));
        } else {
            var color = getColor(drawable);
            var newColor = IColors.getFromIntColor(color, colors);
            if (color == newColor) return;
            drawable.setColorFilter(new PorterDuffColorFilter(newColor, PorterDuff.Mode.SRC_IN));
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
        var corSalva = ninePatchs.get(bitmap);
        if (corSalva != null) return corSalva;

        HashMap<Integer, Integer> contagemCores = new HashMap<>();
        int corMaisFrequente = 0;
        int contagemMaxima = 0;

        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                int cor = bitmap.getPixel(x, y);
                // Atualiza a contagem da cor no mapa
                int contagemAtual = contagemCores.getOrDefault(cor, 0) + 1;
                contagemCores.put(cor, contagemAtual);

                // Verifica se essa cor se tornou a mais frequente até agora
                if (contagemAtual > contagemMaxima) {
                    corMaisFrequente = cor;
                    contagemMaxima = contagemAtual;
                }
            }
        }
        ninePatchs.put(bitmap, corMaisFrequente);
        return corMaisFrequente;
    }

    private static int getRippleDrawableColor(RippleDrawable rippleDrawable) {
        var state = rippleDrawable.getConstantState();
        var rippleStateClass = XposedHelpers.findClass("android.graphics.drawable.RippleDrawable.RippleState", loader1);
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
