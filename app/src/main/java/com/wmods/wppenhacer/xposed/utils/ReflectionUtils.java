package com.wmods.wppenhacer.xposed.utils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ReflectionUtils {

    public static Method findMethodUsingFilter(Class<?> clazz, Predicate<Method> predicate) {
        do {
            var results = Arrays.stream(clazz.getDeclaredMethods()).filter(predicate).findFirst();
            if (results.isPresent()) return results.get();
        } while ((clazz = clazz.getSuperclass()) != null);
        throw new RuntimeException("Method not found");
    }

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


}
