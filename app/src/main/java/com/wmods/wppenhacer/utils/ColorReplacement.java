package com.wmods.wppenhacer.utils;

import static com.wmods.wppenhacer.utils.DrawableColors.replaceColor;

import android.graphics.PorterDuffColorFilter;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;

import com.wmods.wppenhacer.xposed.utils.DesignUtils;

import java.util.HashMap;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ColorReplacement {
    public static void replaceColors(View view, HashMap<String, String> colors) {
        if (view instanceof ImageView imageView) {
            Image.replace(imageView,colors);
        } else if (view instanceof TextView textView) {
            Text.replace(textView, colors);
        } else if (view instanceof ViewGroup viewGroup) {
            Group.replace(viewGroup,colors);
        } else if (view instanceof ViewStub viewStub) {
            replaceColor(viewStub.getBackground(), colors);
        }
    }

    public static class Image {
        static void replace(ImageView view, HashMap<String, String> colors) {
            replaceColor(view.getBackground(), colors);
            var colorFilter = view.getColorFilter();
            if (colorFilter == null) return;
            if (colorFilter instanceof PorterDuffColorFilter filter) {
                var color = (int) XposedHelpers.callMethod(filter, "getColor");
                var sColor = IColors.toString(color);
                var newColor = colors.get(sColor);
                if (newColor != null) {
                    view.setColorFilter(IColors.parseColor(newColor));
                } else {
                    if (!sColor.startsWith("#ff") && !sColor.startsWith("#0")) {
                        var sColorSub = sColor.substring(0, 3);
                        newColor = colors.get(sColor.substring(3));
                        if (newColor != null)
                            view.setColorFilter(IColors.parseColor(sColorSub + newColor));
                    }
                }
            }
        }
    }

    public static class Text {
        static void replace(TextView view, HashMap<String, String> colors) {
            var color = view.getCurrentTextColor();
            var sColor = IColors.toString(color);
            if (sColor.equals("#ffffffff") && !DesignUtils.isNightMode()) {
                return;
            }
            replaceColor(view.getBackground(), colors);
            var newColor = colors.get(sColor);
            if (newColor != null) {
                view.setTextColor(IColors.parseColor(newColor));
                XposedBridge.log("ColorReplacement.Text.replace: " + sColor + "->" + newColor);
            } else {
                if (!sColor.startsWith("#ff") && !sColor.startsWith("#0")) {
                    var sColorSub = sColor.substring(0, 3);
                    newColor = colors.get(sColor.substring(3));
                    if (newColor != null) {
                        view.setTextColor(IColors.parseColor(sColorSub + newColor));
                        XposedBridge.log("ColorReplacement.Text.replace: " + sColor + "->" + newColor);
                    }
                }
            }
        }
    }

    public static class Group {
        static void replace(ViewGroup view, HashMap<String, String> colors) {
            var bg = view.getBackground();
            var count = view.getChildCount();
            for (int i = 0; i < count; i++) {
                var child = view.getChildAt(i);
                replaceColors(child, colors);
            }
            replaceColor(bg, colors);
        }
    }
}
