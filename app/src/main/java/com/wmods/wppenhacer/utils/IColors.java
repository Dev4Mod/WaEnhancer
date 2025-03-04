package com.wmods.wppenhacer.utils;

import android.graphics.Color;

import java.util.HashMap;

public class IColors {
    public static HashMap<String, String> colors = new HashMap<>();

    public static final HashMap<String, String> backgroundColors = new HashMap<>();
    public static final HashMap<String, String> primaryColors = new HashMap<>();
    public static final HashMap<String, String> secondaryColors = new HashMap<>();

    static {

        // primary colors
        primaryColors.put("00a884", "00a884");
        primaryColors.put("1da457", "1da457");
        primaryColors.put("21c063", "21c063");
        primaryColors.put("d9fdd3", "d9fdd3");
        primaryColors.put("#ff00a884", "#ff00a884");
        primaryColors.put("#ff1da457", "#ff1da457");
        primaryColors.put("#ff21c063", "#ff21c063");
        primaryColors.put("#ff1daa61", "#ff1daa61");
        primaryColors.put("#ff25d366", "#ff25d366");
        primaryColors.put("#ffd9fdd3", "#ffd9fdd3");
        primaryColors.put("#ff1b864b", "#ff1b864b");


        // secundary colors
        secondaryColors.put("#ff202c33", "#ff202c33");
        secondaryColors.put("#ff2a2f33", "#ff2a2f33");
        secondaryColors.put("#fff1f2f4", "#fff1f2f4");
        secondaryColors.put("#ff103529", "#ff103529");
        secondaryColors.put("#ff144d37", "#ff144d37");
        secondaryColors.put("#ff1b8755", "#ff1b8755");
        secondaryColors.put("#ff15603e", "#ff15603e");

        // background colors
        backgroundColors.put("0b141a", "0a1014");
        backgroundColors.put("#ff0b141a", "#ff111b21");
        backgroundColors.put("#ff111b21", "#ff111b21");
        backgroundColors.put("#ff000000", "#ff000000");
        backgroundColors.put("#ff0a1014", "#ff0a1014");
        backgroundColors.put("#ff10161a", "#ff10161a");
        backgroundColors.put("#ff12181c", "#ff12181c");
        backgroundColors.put("#ff20272b", "#ff20272b");
    }

    public static int parseColor(String str) {
        return Color.parseColor(str);
    }

    public static String toString(int i) {
        var color = Integer.toHexString(i);
        if (color.length() == 7) {
            color = "0" + color;
        }
        return "#" + color;
    }


    public static int getFromIntColor(int color, HashMap<String, String> colors) {
        var sColor = IColors.toString(color);
        var newColor = colors.get(sColor);
        if (newColor != null && newColor.length() == 9) {
            return IColors.parseColor(newColor);
        } else {
            if (!sColor.equals("#0") && !sColor.startsWith("#ff")) {
                var sColorSub = sColor.substring(0, 3);
                newColor = colors.get(sColor.substring(3));
                if (newColor != null) {
                    return IColors.parseColor(sColorSub + newColor);
                }
            }
        }
        return color;
    }
}
