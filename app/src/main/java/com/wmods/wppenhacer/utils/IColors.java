package com.wmods.wppenhacer.utils;

import android.graphics.Color;

import java.util.HashMap;
import java.util.HashSet;

public class IColors {
    public static HashMap<String, String> colors = new HashMap<>();

    public static HashSet<Integer> exceptionsIdAlpha = new HashSet<>();

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
        colors.put("#ff21c063", "#ff21c063");
        colors.put("#ff000000", "#ff000000");
        colors.put("#0b141a", "#ff000000");

        // Secondary
        colors.put("#ff202c33", "#ff202c33");

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
