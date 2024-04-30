package com.wmods.wppenhacer.utils;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class StateListDrawableCompact {
    private static final Class<?> mClass = StateListDrawable.class;

    private StateListDrawableCompact() {
    }

    public static int getStateCount(StateListDrawable stateListDrawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return stateListDrawable.getStateCount();
        } else {
            try {
                Method method = XposedHelpers.findMethodBestMatch(mClass, "getStateCount");
                if (method != null) {
                    Object invoke = method.invoke(stateListDrawable);
                    if (invoke instanceof Integer) {
                        return (Integer) invoke;
                    }
                }
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }
        return 0;
    }

    @Nullable
    public static Drawable getStateDrawable(StateListDrawable stateListDrawable, int i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return stateListDrawable.getStateDrawable(i);
        } else {
            try {
                Method method = XposedHelpers.findMethodBestMatch(mClass, "getStateDrawable", Integer.TYPE);
                if (method != null) {
                    Object invoke = method.invoke(stateListDrawable, i);
                    if (invoke instanceof Drawable) {
                        return (Drawable) invoke;
                    }
                }
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }
        return null;
    }
}