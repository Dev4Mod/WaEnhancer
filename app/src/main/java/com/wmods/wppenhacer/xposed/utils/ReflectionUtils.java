package com.wmods.wppenhacer.xposed.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.robv.android.xposed.XposedHelpers;

@SuppressWarnings("unused")
public class ReflectionUtils {

    public static Method findMethodUsingFilter(Class<?> clazz, Predicate<Method> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredMethods()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Method not found");
    }

    /**
     * @noinspection SimplifyStreamApiCallChains
     */
    public static Method[] findAllMethodUsingFilter(Class<?> clazz, Predicate<Method> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredMethods()).filter(predicate).collect(Collectors.toList());
            if (!results.isEmpty()) return results.toArray(new Method[0]);
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Method not found");
    }

    public static Method findMethodUsingFilterIfExists(Class<?> clazz, Predicate<Method> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredMethods()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        return null;
    }

    public static boolean isOverridden(Method method) {
        try {
            Class<?> superclass = method.getDeclaringClass().getSuperclass();
            if (superclass == null) return false;
            Method parentMethod = superclass.getMethod(method.getName(), method.getParameterTypes());
            return !parentMethod.equals(method);

        } catch (NoSuchMethodException e) {
            return false;
        }
    }


    public static List<Field> getFieldsByExtendType(Class<?> cls, Class<?> type) {
        return Arrays.stream(cls.getFields()).filter(f -> type.isAssignableFrom(f.getType())).collect(Collectors.toList());
    }

    public static List<Field> getFieldsByType(Class<?> cls, Class<?> type) {
        return Arrays.stream(cls.getFields()).filter(f -> type == f.getType()).collect(Collectors.toList());
    }

    public static Field getFieldByExtendType(Class<?> cls, Class<?> type) {
        return Arrays.stream(cls.getFields()).filter(f -> type.isAssignableFrom(f.getType())).findFirst().orElse(null);
    }

    public static Field getFieldByType(Class<?> cls, Class<?> type) {
        return Arrays.stream(cls.getFields()).filter(f -> type == f.getType()).findFirst().orElse(null);
    }

    public static Object callMethod(Method getUserJid, Object fMessage, Object... args) {
        try {
            return getUserJid.invoke(fMessage, args);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object getField(Field loadProfileInfoField, Object thisObject) {
        try {
            return loadProfileInfoField.get(thisObject);
        } catch (Exception e) {
            return null;
        }
    }

    public static int findIndexOfType(Object[] args, Class<?> type) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) continue;
            if (args[i] instanceof Class) {
                if (type.isAssignableFrom((Class) args[i])) return i;
                continue;
            }
            if (type.isAssignableFrom(args[i].getClass())) return i;
        }
        return -1;
    }

    public static boolean isCalledFromString(String contains) {
        var trace = Thread.currentThread().getStackTrace();
        var text = Arrays.toString(trace);
        return text.contains(contains);
    }

    public static boolean isCalledFromStrings(String... contains) {
        var trace = Thread.currentThread().getStackTrace();
        var text = Arrays.toString(trace);
        for (String s : contains) {
            if (text.contains(s)) return true;
        }
        return false;
    }

    public static boolean isClassSimpleNameString(Class<?> aClass, String s) {
        try {
            Class<?> search = XposedHelpers.findClassIfExists("android.view." + s, aClass.getClassLoader());
            if (search != null)
                search = XposedHelpers.findClassIfExists("android.widget." + s, aClass.getClassLoader());
            Class<?> cls = aClass;
            do {
                if (search != null) {
                    if (cls.getName().equals(search.getName())) return true;
                    if (cls.getName().startsWith("android.widget.") || cls.getName().startsWith("android.view."))
                        return false;
                } else {
                    if (cls.getSimpleName().contains(s)) return true;
                }
            } while ((cls = cls.getSuperclass()) != null);
        } catch (Exception ignored) {
        }
        return false;
    }
}
