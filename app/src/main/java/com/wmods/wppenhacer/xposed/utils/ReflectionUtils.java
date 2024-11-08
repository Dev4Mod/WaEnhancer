package com.wmods.wppenhacer.xposed.utils;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.robv.android.xposed.XposedHelpers;

@SuppressWarnings("unused")
public class ReflectionUtils {

    public static Map<String, Class<?>> primitiveClasses = Map.of(
            "byte", Byte.TYPE,
            "short", Short.TYPE,
            "int", Integer.TYPE,
            "long", Long.TYPE,
            "float", Float.TYPE,
            "boolean", Boolean.TYPE
    );

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        var primitive = primitiveClasses.get(className);
        if (primitive != null) return primitive;
        return XposedHelpers.findClass(className, classLoader);
    }

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
    public static Method[] findAllMethodsUsingFilter(Class<?> clazz, Predicate<Method> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredMethods()).filter(predicate).collect(Collectors.toList());
            if (!results.isEmpty()) return results.toArray(new Method[0]);
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Method not found");
    }

    public static Field findFieldUsingFilter(Class<?> clazz, Predicate<Field> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredFields()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Field not found");
    }

    /**
     * @noinspection SimplifyStreamApiCallChains
     */
    public static Constructor[] findAllConstructorsUsingFilter(Class<?> clazz, Predicate<Constructor> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredConstructors()).filter(predicate).collect(Collectors.toList());
            if (!results.isEmpty()) return results.toArray(new Constructor[0]);
        } while ((clazz = clazz.getSuperclass()) != null);
        return new Constructor[0];
    }

    public static Constructor findConstructorUsingFilter(Class<?> clazz, Predicate<Constructor> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredConstructors()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Field not found");
    }

    /**
     * @noinspection SimplifyStreamApiCallChains
     */
    @NonNull
    public static Field[] findAllFieldsUsingFilter(Class<?> clazz, @NonNull Predicate<Field> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredFields()).filter(predicate).collect(Collectors.toList());
            if (!results.isEmpty()) return results.toArray(new Field[0]);
        } while ((clazz = clazz.getSuperclass()) != null);
        return new Field[0];
    }


    public static Method findMethodUsingFilterIfExists(Class<?> clazz, Predicate<Method> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredMethods()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        return null;
    }

    public static Field findFieldUsingFilterIfExists(Class<?> clazz, Predicate<Field> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredFields()).filter(predicate).findFirst();
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

    public static Object callMethod(Method method, Object instance, Object... args) {
        try {
            var count = method.getParameterCount();
            if (count != args.length) {
                var newargs = initArray(method.getParameterTypes());
                System.arraycopy(args, 0, newargs, 0, Math.min(args.length, count));
                args = newargs;
            }
            return method.invoke(instance, args);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object[] initArray(Class<?>[] parameterTypes) {
        var args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = getDefaultValue(parameterTypes[i]);
        }
        return args;
    }

    public static Object getDefaultValue(Class<?> paramType) {
        if (paramType == int.class || paramType == Integer.class) {
            return 0;
        } else if (paramType == long.class || paramType == Long.class) {
            return 0L;
        } else if (paramType == double.class || paramType == Double.class) {
            return 0.0;
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return false;
        }
        return null;
    }

    public static Object getObjectField(Field loadProfileInfoField, Object thisObject) {
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

    public static List<Pair<Integer, Object>> findArrayOfType(Object[] args, Class<?> type) {
        var result = new ArrayList<Pair<Integer, Object>>();
        for (int i = 0; i < args.length; i++) {
            var arg = args[i];
            if (arg == null) continue;
            if (arg instanceof Class) {
                if (type.isAssignableFrom((Class) arg)) {
                    result.add(new Pair<>(i, arg));
                }
                continue;
            }
            if (type.isAssignableFrom(arg.getClass()) || type.isInstance(arg)) {
                result.add(new Pair<>(i, arg));
            }
        }
        return result;
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

    public synchronized static boolean isCalledFromClass(Class<?> cls) {
        var trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : trace) {
            if (stackTraceElement.getClassName().equals(cls.getName()))
                return true;
        }
        return false;
    }

    public synchronized static boolean isCalledFromMethod(Method method) {
        var trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : trace) {
            if (stackTraceElement.getClassName().equals(method.getDeclaringClass().getName()) && stackTraceElement.getMethodName().equals(method.getName()))
                return true;
        }
        return false;
    }

}
