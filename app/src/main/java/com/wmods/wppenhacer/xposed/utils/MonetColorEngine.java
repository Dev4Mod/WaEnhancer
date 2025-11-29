package com.wmods.wppenhacer.xposed.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.RequiresApi;

public class MonetColorEngine {

    @ColorInt
    public static int getSystemAccentColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Try getting the system accent color directly from resources (Monet palette)
            try {
                return context.getColor(android.R.color.system_accent1_500);
            } catch (Exception e) {
                // Fallback to attribute if resource lookup fails
                return getColorFromAttr(context, android.R.attr.colorAccent);
            }
        }
        return -1;
    }

    @ColorInt
    public static int getSystemPrimaryColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // System accent is usually a good proxy for Primary in Monet
                // Using 600 for slightly darker/richer primary
                return context.getColor(android.R.color.system_accent1_600); 
            } catch (Exception e) {
                return getColorFromAttr(context, android.R.attr.colorPrimary);
            }
        }
        return -1;
    }
    
    @ColorInt
    public static int getSystemSecondaryColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.getColor(android.R.color.system_accent2_500);
            } catch (Exception e) {
                return getColorFromAttr(context, android.R.attr.colorSecondary);
            }
        }
        return -1;
    }

    @ColorInt
    public static int getSystemBackgroundColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (isNightMode(context)) {
                    // Dark Mode: Force Pure Black (AMOLED)
                    return Color.BLACK; 
                } else {
                    // Light Mode: Use light neutral background
                    return context.getColor(android.R.color.system_neutral1_50);
                }
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    private static boolean isNightMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    @ColorInt
    private static int getColorFromAttr(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        try {
            // Attempt to use the activity context if possible, or the app context
            // But here we receive a context. Ensure it has a theme.
            if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
                // Check if it's a color resource or raw color
                if (typedValue.resourceId != 0) {
                    return context.getColor(typedValue.resourceId);
                }
                return typedValue.data;
            }
        } catch (Exception e) {
            Log.e("MonetEngine", "Failed to resolve attr: " + e.getMessage());
        }
        return -1;
    }
}
