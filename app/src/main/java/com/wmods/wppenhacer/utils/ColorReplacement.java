package com.wmods.wppenhacer.utils;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static com.wmods.wppenhacer.utils.DrawableColors.replaceColor;
import static com.wmods.wppenhacer.utils.IColors.colors;
import static com.wmods.wppenhacer.utils.IColors.parseColor;
import static com.wmods.wppenhacer.xposed.features.customization.CustomTheme.classLoader;
import android.graphics.PorterDuffColorFilter;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;

import de.robv.android.xposed.XposedHelpers;

public class ColorReplacement {
    public static void replaceColors(View view) {
        if (view instanceof ImageView imageView) {
            Image.replace(imageView);
        } else if (view instanceof TextView textView) {
            Text.replace(textView);
        } else if (view instanceof ViewGroup viewGroup) {
            Group.replace(viewGroup);
        } else if (view instanceof ViewStub viewStub) {
            replaceColor(viewStub.getBackground());
        } else if (view.getClass().equals(findClass("com.whatsapp.CircularProgressBar", classLoader))) {
            CircularProgressBar.replace(view);
        }
    }

    public static class Image {
        static void replace(ImageView view) {
            replaceColor(view.getBackground());
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
            } else {
//                XposedBridge.log("Image replacement: " + colorFilter.getClass().getName());
            }
        }
    }

    public static class CircularProgressBar {
        static void replace(Object view) {
            var progressColor = (int) callMethod(view, "getProgressBarColor");
            var progressBackgroundColor = (int) callMethod(view, "getProgressBarBackgroundColor");

            var pcSColor = IColors.toString(progressColor);
            var pcbSColor = IColors.toString(progressBackgroundColor);

            var newPColor = colors.get(pcSColor);
            var newPBColor = colors.get(pcbSColor);

            if (newPColor != null) {
                callMethod(view, "setProgressBarColor", parseColor(newPColor));
            }

            if (newPBColor != null) {
                callMethod(view, "setProgressBarBackgroundColor", parseColor(newPBColor));
            }

        }
    }

    public static class Text {
        static void replace(TextView view) {
            replaceColor(view.getBackground());
            var color = view.getCurrentTextColor();
            var sColor = IColors.toString(color);
            var newColor = colors.get(sColor);
            if (newColor != null) {
//                XposedBridge.log(sColor + "/" + newColor + ": " + view.getText());
                view.setTextColor(IColors.parseColor(newColor));
            } else {
                if (!sColor.startsWith("#ff") && !sColor.startsWith("#0")) {
                    var sColorSub = sColor.substring(0, 3);
                    newColor = colors.get(sColor.substring(3));
                    if (newColor != null)
                        view.setTextColor(IColors.parseColor(sColorSub + newColor));
                }
            }
        }
    }

    public static class Group {
        static void replace(ViewGroup view) {
            var bg = view.getBackground();
            var count = view.getChildCount();
            for (int i = 0; i < count; i++) {
                var child = view.getChildAt(i);
                replaceColors(child);
            }
            replaceColor(bg);
        }
    }

    public static class Stub {
        static void replace(ViewStub view) {
            replaceColor(view.getBackground());
        }
    }
}
