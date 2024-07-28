package com.wmods.wppenhacer.xposed.core.devkit;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class UnobfuscatorCache {

    private final Application mApp;
    private static UnobfuscatorCache mInstance;
    private final SharedPreferences mShared;

    private final Map<String, String> reverseResourceMap = new HashMap<>();

    public UnobfuscatorCache(Application app, XSharedPreferences shared) {
        mApp = app;
        try {
            mShared = mApp.getSharedPreferences("UnobfuscatorCache", Context.MODE_PRIVATE);
            long version = mShared.getLong("version", 0);
            long currentVersion = mApp.getPackageManager().getPackageInfo(mApp.getPackageName(), 0).getLongVersionCode();
            long savedUpdateTime = mShared.getLong("updateTime", 0);
            long lastUpdateTime = shared.getLong("lastUpdateTime", -1);
            if (version != currentVersion || savedUpdateTime != lastUpdateTime) {
                mShared.edit().clear().commit();
                mShared.edit().putLong("version", currentVersion).commit();
                mShared.edit().putLong("updateTime", lastUpdateTime).commit();
            }
            initCacheStrings();
        } catch (Exception e) {
            throw new RuntimeException("Can't initialize UnobfuscatorCache: " + e.getMessage(), e);
        }

    }

    private void initCacheStrings() {
        getOfuscateIDString("mystatus");
        getOfuscateIDString("online");
        getOfuscateIDString("groups");
        getOfuscateIDString("messagedeleted");
        getOfuscateIDString("selectcalltype");
    }

    public static UnobfuscatorCache getInstance() {
        return mInstance;
    }

    public static void init(Application mApp, XSharedPreferences pref) {
        mInstance = new UnobfuscatorCache(mApp, pref);
    }

    private void initializeReverseResourceMap() {
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads); // Create a thread pool with 4 threads

        try {
            var configuration = new Configuration(mApp.getResources().getConfiguration());
            configuration.setLocale(Locale.ENGLISH);
            var context = Utils.getApplication().createConfigurationContext(configuration);
            Resources resources = context.getResources();

            int startId = 0x7f120000;
            int endId = 0x7f12ffff;

            int chunkSize = (endId - startId + 1) / numThreads;
            CountDownLatch latch = new CountDownLatch(numThreads);

            for (int t = 0; t < numThreads; t++) {
                int threadStartId = startId + t * chunkSize;
                int threadEndId = t == numThreads - 1 ? endId : threadStartId + chunkSize - 1;

                executor.submit(() -> {
                    try {
                        for (int i = threadStartId; i <= threadEndId; i++) {
                            try {
                                String resourceString = resources.getString(i);
                                reverseResourceMap.put(resourceString.toLowerCase().replaceAll("\\s", ""), String.valueOf(i));
                            } catch (Resources.NotFoundException ignored) {
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(); // Wait for all threads to finish
        } catch (Exception e) {
            XposedBridge.log(e);
        } finally {
            executor.shutdown();
        }
    }

    private String getMapIdString(String search) {
        if (reverseResourceMap.isEmpty()) {
            initializeReverseResourceMap();
        }
        search = search.toLowerCase().replaceAll("\\s", "");
        return reverseResourceMap.get(search);
    }

    public int getOfuscateIDString(String search) {
        search = search.toLowerCase().replaceAll("\\s", "");
        var id = mShared.getString(search, null);
        if (id == null) {
            id = getMapIdString(search);
            if (id != null) {
                mShared.edit().putString(search, id).commit();
            }
        }
        return id == null ? -1 : Integer.parseInt(id);
    }

    public String getString(String search) {
        var id = getOfuscateIDString(search);
        return id == -1 ? "" : mApp.getResources().getString(id);
    }

    public Field getField(ClassLoader loader, FunctionCall functionCall) throws Exception {
        var methodName = getKeyName();
        String value = mShared.getString(methodName, null);
        if (value == null) {
            Field result = (Field) functionCall.call();
            if (result == null) throw new Exception("Field is null");
            saveField(methodName, result);
            return result;
        }
        String[] ClassAndName = value.split(":");
        Class<?> cls = ReflectionUtils.findClass(ClassAndName[0], loader);
        return XposedHelpers.findField(cls, ClassAndName[1]);
    }

    public Method getMethod(ClassLoader loader, FunctionCall functionCall) throws Exception {
        var methodName = getKeyName();
        String value = mShared.getString(methodName, null);
        if (value == null) {
            Method result = (Method) functionCall.call();
            if (result == null) throw new Exception("Method is null");
            saveMethod(methodName, result);
            return result;
        }
        String[] classAndName = value.split(":");
        Class<?> cls = XposedHelpers.findClass(classAndName[0], loader);
        if (classAndName.length == 3) {
            String[] params = classAndName[2].split(",");
            Class<?>[] paramTypes = Arrays.stream(params).map(param -> ReflectionUtils.findClass(param, loader)).toArray(Class<?>[]::new);
            return XposedHelpers.findMethodExact(cls, classAndName[1], paramTypes);
        }
        return XposedHelpers.findMethodExact(cls, classAndName[1]);
    }

    public Class<?> getClass(ClassLoader loader, FunctionCall functionCall) throws Exception {
        var methodName = getKeyName();
        String value = mShared.getString(methodName, null);
        if (value == null) {
            Class<?> result = (Class<?>) functionCall.call();
            if (result == null) throw new Exception("Class is null");
            saveClass(methodName, result);
            return result;
        }
        return XposedHelpers.findClass(value, loader);
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveField(String key, Field field) {
        String value = field.getDeclaringClass().getName() + ":" + field.getName();
        mShared.edit().putString(key, value).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveMethod(String key, Method method) {
        String value = method.getDeclaringClass().getName() + ":" + method.getName();
        if (method.getParameterTypes().length > 0) {
            value += ":" + Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(","));
        }
        mShared.edit().putString(key, value).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveClass(String message, Class<?> messageClass) {
        mShared.edit().putString(message, messageClass.getName()).commit();
    }

    private String getKeyName() {
        AtomicReference<String> keyName = new AtomicReference<>("");
        Arrays.stream(Thread.currentThread().getStackTrace()).filter(stackTraceElement -> stackTraceElement.getClassName().equals(Unobfuscator.class.getName())).findFirst().ifPresent(stackTraceElement -> keyName.set(stackTraceElement.getMethodName()));
        return keyName.get();
    }

    public Constructor getConstructor(ClassLoader loader, FunctionCall functionCall) throws Exception {
        var methodName = getKeyName();
        String value = mShared.getString(methodName, null);
        if (value == null) {
            var result = (Constructor) functionCall.call();
            if (result == null) throw new Exception("Class is null");
            saveConstructor(methodName, result);
            return result;
        }
        String[] classAndName = value.split(":");
        Class<?> cls = XposedHelpers.findClass(classAndName[0], loader);
        if (classAndName.length == 2) {
            String[] params = classAndName[1].split(",");
            Class<?>[] paramTypes = Arrays.stream(params).map(param -> ReflectionUtils.findClass(param, loader)).toArray(Class<?>[]::new);
            return XposedHelpers.findConstructorExact(cls, paramTypes);
        }
        return XposedHelpers.findConstructorExact(cls);
    }

    @SuppressWarnings("ApplySharedPref")
    private void saveConstructor(String key, Constructor constructor) {
        String value = constructor.getDeclaringClass().getName();
        if (constructor.getParameterTypes().length > 0) {
            value += ":" + Arrays.stream(constructor.getParameterTypes()).map(Class::getName).collect(Collectors.joining(","));
        }
        mShared.edit().putString(key, value).commit();
    }

    public interface FunctionCall {
        Object call() throws Exception;
    }

}
