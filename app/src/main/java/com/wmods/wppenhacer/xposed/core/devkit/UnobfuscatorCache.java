package com.wmods.wppenhacer.xposed.core.devkit;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.devrel.gmscore.tools.apk.arsc.ArscUtils;
import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class UnobfuscatorCache {

    private final Application mApplication;
    private static UnobfuscatorCache mInstance;
    public final SharedPreferences sPrefsCacheHooks;

    private final Map<String, String> reverseResourceMap = new HashMap<>();
    private final SharedPreferences sPrefsCacheStrings;

    @SuppressLint("ApplySharedPref")
    public UnobfuscatorCache(Application application) {
        mApplication = application;
        try {
            sPrefsCacheHooks = mApplication.getSharedPreferences("UnobfuscatorCache", Context.MODE_PRIVATE);
            sPrefsCacheStrings = mApplication.getSharedPreferences("UnobfuscatorCacheStrings", Context.MODE_PRIVATE);
            long version = sPrefsCacheHooks.getLong("version", 0);
            long currentVersion = mApplication.getPackageManager().getPackageInfo(mApplication.getPackageName(), 0).getLongVersionCode();
            long savedUpdateTime = sPrefsCacheHooks.getLong("updateTime", 0);
            long lastUpdateTime = savedUpdateTime;
            try {
                lastUpdateTime = mApplication.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0).lastUpdateTime;
            } catch (Exception ignored) {
            }
            if (version != currentVersion || savedUpdateTime != lastUpdateTime) {
                Utils.showToast(application.getString(ResId.string.starting_cache), Toast.LENGTH_LONG);
                sPrefsCacheHooks.edit().clear().commit();
                sPrefsCacheHooks.edit().putLong("version", currentVersion).commit();
                sPrefsCacheHooks.edit().putLong("updateTime", lastUpdateTime).commit();
                if (version != currentVersion) {
                    sPrefsCacheStrings.edit().clear().commit();
                }
            }
            initCacheStrings();
        } catch (Exception e) {
            throw new RuntimeException("Can't initialize UnobfuscatorCache: " + e.getMessage(), e);
        }

    }

    public static void init(Application mApp) {
        mInstance = new UnobfuscatorCache(mApp);
    }

    public static UnobfuscatorCache getInstance() {
        return mInstance;
    }

    private void initCacheStrings() {
        getOfuscateIDString("mystatus");
        getOfuscateIDString("online");
        getOfuscateIDString("groups");
        getOfuscateIDString("messagedeleted");
        getOfuscateIDString("selectcalltype");
        getOfuscateIDString("lastseensun%s");
        getOfuscateIDString("updates");
    }

    private void initializeReverseResourceMap() {
        try {
            var app = Utils.getApplication();
            var source = app.getApplicationInfo().sourceDir;
            var table = ArscUtils.getResourceTable(new File(source));
            var pool = table.getStringPool();
            var pkg = table.getPackage(app.getPackageName());
            var typeChunks = pkg.getTypeChunks("string");
            var chunk = typeChunks.stream().filter(typeChunk -> typeChunk.getConfiguration().isDefault()).findFirst().orElse(null);
            var entries = chunk.getEntries();
            int baseValue = 0x7f12;
            for (var entry : entries.entrySet()) {
                int keyHexValue = entry.getKey();
                int result = baseValue << 16 | keyHexValue;
                String resourceString = pool.getString(entry.getValue().value().data()).toLowerCase().replaceAll("\\s", "");
                reverseResourceMap.put(resourceString, String.valueOf(result));
            }
        } catch (Exception e) {
            XposedBridge.log(e);
            reverseResourceMap.clear();
            initializeReverseResourceMapBruteForce();
        }
    }

    private void initializeReverseResourceMapBruteForce() {
        var currentTime = System.currentTimeMillis();
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads); // Create a thread pool with 4 threads

        try {
            var configuration = new Configuration(mApplication.getResources().getConfiguration());
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
            XposedBridge.log("String cache saved in " + (System.currentTimeMillis() - currentTime) + "ms");
        } catch (Exception e) {
            XposedBridge.log(e);
        } finally {
            executor.shutdown();
        }
    }

    private String getMapIdString(String search) {
        if (reverseResourceMap.isEmpty()) {
            initializeReverseResourceMap();
            System.gc();
        }
        search = search.toLowerCase().replaceAll("\\s", "");
        XposedBridge.log("need search obsfucate: " + search);
        return reverseResourceMap.get(search);
    }

    @SuppressLint("ApplySharedPref")
    public int getOfuscateIDString(String search) {
        search = search.toLowerCase().replaceAll("\\s", "");
        var id = sPrefsCacheStrings.getString(search, null);
        if (id == null) {
            id = getMapIdString(search);
            if (id != null) {
                sPrefsCacheStrings.edit().putString(search, id).commit();
            }
        }
        return id == null ? -1 : Integer.parseInt(id);
    }

    public String getString(String search) {
        var id = getOfuscateIDString(search);
        return id < 1 ? "" : mApplication.getResources().getString(id);
    }

    public Field getField(ClassLoader loader, FunctionCall<Field> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            Field result = functionCall.call();
            if (result == null) throw new Exception("Field is null:" + methodName);
            saveField(methodName, result);
            return result;
        }
        String[] ClassAndName = value.split(":");
        Class<?> cls = ReflectionUtils.findClass(ClassAndName[0], loader);
        return XposedHelpers.findField(cls, ClassAndName[1]);
    }

    public Field[] getFields(ClassLoader loader, FunctionCall<Field[]> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            Field[] result = functionCall.call();
            if (result == null) throw new Exception("Fields is null: " + methodName);
            saveFields(methodName, result);
            return result;
        }
        ArrayList<Field> fields = new ArrayList<>();
        String[] fieldsString = value.split("&");
        for (String field : fieldsString) {
            String[] ClassAndName = field.split(":");
            Class<?> cls = ReflectionUtils.findClass(ClassAndName[0], loader);
            fields.add(XposedHelpers.findField(cls, ClassAndName[1]));
        }
        return fields.toArray(new Field[0]);
    }

    public Method getMethod(ClassLoader loader, FunctionCall<Method> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            Method result = functionCall.call();
            if (result == null) throw new Exception("Method is null:" + methodName);
            saveMethod(methodName, result);
            return result;
        }
        return getMethodFromString(loader, value);
    }

    public Method[] getMethods(ClassLoader loader, FunctionCall<Method[]> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            Method[] result = functionCall.call();
            if (result == null) throw new Exception("Methods is null:" + methodName);
            saveMethods(methodName, result);
            return result;
        }
        var methodStrings = value.split("&");
        ArrayList<Method> methods = new ArrayList<>();
        for (String methodString : methodStrings) {
            var method = getMethodFromString(loader, methodString);
            methods.add(method);
        }
        return methods.toArray(new Method[0]);
    }

    @NonNull
    private Method getMethodFromString(ClassLoader loader, String value) {
        String[] classAndName = value.split(":");
        Class<?> cls = XposedHelpers.findClass(classAndName[0], loader);
        if (classAndName.length == 3) {
            String[] params = classAndName[2].split(",");
            Class<?>[] paramTypes = Arrays.stream(params).map(param -> ReflectionUtils.findClass(param, loader)).toArray(Class<?>[]::new);
            return XposedHelpers.findMethodExact(cls, classAndName[1], paramTypes);
        }
        return XposedHelpers.findMethodExact(cls, classAndName[1]);
    }


    public Class<?> getClass(ClassLoader loader, FunctionCall<Class<?>> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            Class<?> result = functionCall.call();
            if (result == null) throw new Exception("Class is null: " + methodName);
            saveClass(methodName, result);
            return result;
        }
        return XposedHelpers.findClass(value, loader);
    }

    public Class<?>[] getClasses(ClassLoader loader, FunctionCall<Class<?>[]> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            Class<?>[] result = functionCall.call();
            if (result == null) throw new Exception("Class is null: " + methodName);
            saveClasses(methodName, result);
            return result;
        }
        String[] classStrings = value.split("&");
        ArrayList<Class<?>> classes = new ArrayList<>();
        for (String classString : classStrings) {
            classes.add(XposedHelpers.findClass(classString, loader));
        }
        return classes.toArray(new Class<?>[0]);
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveField(String key, Field field) {
        String value = field.getDeclaringClass().getName() + ":" + field.getName();
        sPrefsCacheHooks.edit().putString(key, value).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveFields(String key, Field[] fields) {
        ArrayList<String> values = new ArrayList<>();
        for (Field field : fields) {
            values.add(field.getDeclaringClass().getName() + ":" + field.getName());
        }
        sPrefsCacheHooks.edit().putString(key, String.join("&", values)).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveMethod(String key, Method method) {
        String value = method.getDeclaringClass().getName() + ":" + method.getName();
        if (method.getParameterTypes().length > 0) {
            value += ":" + Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(","));
        }
        sPrefsCacheHooks.edit().putString(key, value).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveMethods(String key, Method[] methods) {
        ArrayList<String> values = new ArrayList<>();
        for (Method method : methods) {
            String value = method.getDeclaringClass().getName() + ":" + method.getName();
            if (method.getParameterTypes().length > 0) {
                value += ":" + Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(","));
            }
            values.add(value);
        }
        sPrefsCacheHooks.edit().putString(key, String.join("&", values)).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveClass(String message, Class<?> messageClass) {
        sPrefsCacheHooks.edit().putString(message, messageClass.getName()).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveClasses(String message, Class<?>[] messageClass) {
        ArrayList<String> values = new ArrayList<>();
        for (Class<?> aClass : messageClass) {
            values.add(aClass.getName());
        }
        sPrefsCacheHooks.edit().putString(message, String.join("&", values)).commit();
    }

    private String getKeyName() {
        AtomicReference<String> keyName = new AtomicReference<>("");
        Arrays.stream(Thread.currentThread().getStackTrace()).filter(stackTraceElement -> stackTraceElement.getClassName().equals(Unobfuscator.class.getName())).findFirst().ifPresent(stackTraceElement -> keyName.set(stackTraceElement.getMethodName()));
        return keyName.get();
    }

    public Constructor getConstructor(ClassLoader loader, FunctionCall functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
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
        sPrefsCacheHooks.edit().putString(key, value).commit();
    }

    public interface FunctionCall<T> {
        T call() throws Exception;
    }

}
