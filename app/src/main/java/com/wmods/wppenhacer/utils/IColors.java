package com.wmods.wppenhacer.utils;

import android.graphics.Color;

import java.util.HashMap;

public class IColors {
    public static HashMap<String, String> colors = new HashMap<>();


    static {
        // Primary color
        colors.put("d9fdd3", "d9fdd3");
        colors.put("#ff1daa61", "#ff1daa61");
        colors.put("#ff103529", "#ff103529"); // darkest green color (background of new home buttons)
        colors.put("1da457", "1da457");
        colors.put("#ff1da457", "#ff1da457");
        colors.put("00a884", "00a884");
        colors.put("#ff00a884", "#ff00a884");
        colors.put("#21c063", "#21c063");
        colors.put("#ff21c063", "#ff21c063"); // bagde indicator, message indicator, date color, title color, image color
        colors.put("#ff000000", "#ff000000"); // background
        colors.put("#ff25d366", "#ff25d366"); // status indicator
        colors.put("#ffd9fdd3", "#ffd9fdd3"); // nav bar color in light theme
        colors.put("#ff15603e", "#ff15603e");  // nav bar color in light theme
        colors.put("15603e", "15603e");
//        colors.put("#0b141a", "#ff0b141a");

        // background
        colors.put("0a1014", "0a1014"); // background green color (above version 22)
        colors.put("#ff0a1014", "#ff0a1014"); // background green color (above version 22)
        colors.put("#ff10161a", "#ff10161a"); // background header green color (above version 22)
        colors.put("#ff12181c", "#ff12181c"); // background header green color (above version 22)


        colors.put("#ffffffff", "#ffffffff"); // background color in light theme
        colors.put("#ff1b8755", "#ff1b8755"); // background toolbar color in light theme

        // Secondary
        colors.put("#ff202c33", "#ff202c33");
        colors.put("#ff2a2f33", "#ff2a2f33");

        // New theme color
        colors.put("0b141a", "0b141a");
        colors.put("#ff0b141a", "#ff0b141a");

        // status bar
        colors.put("#ff111b21", "#ff111b21");

    }

    public static int parseColor(String str) {
        return Color.parseColor(str);
    }

    public static String toString(int i) {
        return "#" + Integer.toHexString(i);
    }


}
