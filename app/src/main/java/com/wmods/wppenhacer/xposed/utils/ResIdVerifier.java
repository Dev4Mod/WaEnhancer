package com.wmods.wppenhacer.xposed.utils;

import com.wmods.wppenhacer.xposed.utils.ResId;
import de.robv.android.xposed.XposedBridge;

public class ResIdVerifier {
    public static void verify() {
        try {
            // Verify strings
            for (var field : ResId.string.class.getFields()) {
                int v = field.getInt(null);
                if (v == 0) {
                    XposedBridge.log("[ResIdVerifier] Missing string id: " + field.getName());
                }
            }
            // Verify arrays
            for (var field : ResId.array.class.getFields()) {
                int v = field.getInt(null);
                if (v == 0) {
                    XposedBridge.log("[ResIdVerifier] Missing array id: " + field.getName());
                }
            }
            // Verify drawables
            for (var field : ResId.drawable.class.getFields()) {
                int v = field.getInt(null);
                if (v == 0) {
                    XposedBridge.log("[ResIdVerifier] Missing drawable id: " + field.getName());
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
