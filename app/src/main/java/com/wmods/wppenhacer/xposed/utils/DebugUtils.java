package com.wmods.wppenhacer.xposed.utils;

import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DebugUtils {
    public static void debugFields(Class<?> cls, Object thisObject) {
        if (cls == null) return;
        XposedBridge.log("DEBUG FIELDS: Class " + cls.getName());
        for (var field : cls.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                var name = field.getName();
                var value = field.get(thisObject);
                if (value != null && value.getClass().isArray()) {
                    value = Arrays.toString((Object[]) value);
                }
                XposedBridge.log("FIELD: " + name + " -> VALUE: " + value);
            } catch (Exception ignored) {
            }
        }
    }


    public static void debugAllMethods(String className, String methodName, boolean printMethods, boolean printFields, boolean printArgs, boolean printTrace) {
        XposedBridge.hookAllMethods(XposedHelpers.findClass(className, Utils.getApplication().getClassLoader()), methodName, getDebugMethodHook(printMethods, printFields, printArgs, printTrace));
    }

    public static XC_MethodHook getDebugMethodHook(boolean printMethods, boolean printFields, boolean printArgs, boolean printTrace) {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("-----------------HOOKED DEBUG START-----------------------------");
                XposedBridge.log("DEBUG CLASS: " + param.method.getDeclaringClass().getName() + "->" + param.method.getName() + ": " + param.thisObject);

                if (printArgs) {
                    debugArgs(param.args);
                    XposedBridge.log("Return value: " + (param.getResult() == null ? null : param.getResult().getClass().getName()) + " -> VALUE: " + param.getResult());
                }

                if (printFields) {
                    debugFields(param.thisObject == null ? param.method.getDeclaringClass() : param.thisObject.getClass(), param.thisObject);
                }

                if (printMethods) {
                    debugMethods(param.thisObject == null ? param.method.getDeclaringClass() : param.thisObject.getClass(), param.thisObject);
                }

                if (printTrace) {
                    for (var trace : Thread.currentThread().getStackTrace()) {
                        XposedBridge.log("TRACE: " + trace.toString());
                    }
                }

                XposedBridge.log("-----------------HOOKED DEBUG END-----------------------------\n\n");
            }
        };
    }

    public static void debugArgs(Object[] args) {
        for (var i = 0; i < args.length; i++) {
            XposedBridge.log("ARG[" + i + "]: " + (args[i] == null ? null : args[i].getClass().getName()) + " -> VALUE: " + args[i]);
        }
    }


    public static void debugMethods(Class<?> cls, Object thisObject) {
        XposedBridge.log("DEBUG METHODS: Class " + cls.getName());
        for (var method : cls.getDeclaredMethods()) {
            if (method.getParameterCount() > 0) continue;
            try {
                method.setAccessible(true);
                XposedBridge.log("METHOD: " + method.getName() + " -> VALUE: " + method.invoke(thisObject));
            } catch (Exception ignored) {
            }
        }
    }

    public static void debugObject(Object srj) {
        if (srj == null) return;
        XposedBridge.log("DEBUG OBJECT: " + srj.getClass().getName());
        debugFields(srj.getClass(), srj);
        debugMethods(srj.getClass(), srj);
    }
}
