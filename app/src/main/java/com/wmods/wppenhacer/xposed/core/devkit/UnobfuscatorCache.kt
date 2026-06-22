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
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class UnobfuscatorCache {

    private static final int CACHE_SCHEMA_VERSION = 2;

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
            int savedCacheSchemaVersion = sPrefsCacheHooks.getInt("cache_schema_version", 0);
            String savedVersionName = sPrefsCacheHooks.getString("wae_version_name", "");
            String versionName = BuildConfig.VERSION_NAME;
            long lastUpdateTime = savedUpdateTime;
            try {
                lastUpdateTime = mApplication.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0).lastUpdateTime;
            } catch (Exception ignored) {
            }
            if (version != currentVersion || savedUpdateTime != lastUpdateTime || !versionName.equals(savedVersionName) || savedCacheSchemaVersion != CACHE_SCHEMA_VERSION) {
                Utils.showToast(application.getString(R.string.starting_cache), Toast.LENGTH_LONG);
                sPrefsCacheHooks.edit().clear().commit();
                sPrefsCacheHooks.edit().putLong("version", currentVersion).commit();
                sPrefsCacheHooks.edit().putLong("updateTime", lastUpdateTime).commit();
                sPrefsCacheHooks.edit().putInt("cache_schema_version", CACHE_SCHEMA_VERSION).commit();
                sPrefsCacheHooks.edit().putString("wae_version_name", versionName).commit();
                sPrefsCacheStrings.edit().clear().commit();
            }
            initCacheStrings();
        } catch (Exception e) {
            throw new RuntimeException("Can't initialize UnobfuscatorCache: " + e.getMessage(), e);
        }

    }

    public static void init(Application mApp) {
        if (mInstance == null)
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
                try {
                    int keyHexValue = entry.getKey();
                    int result = baseValue << 16 | keyHexValue;
                    String resourceString = pool.getString(entry.getValue().value().data()).toLowerCase().replaceAll("\\s", "");
                    if (reverseResourceMap.containsKey(resourceString)) continue;
                    reverseResourceMap.put(resourceString, String.valueOf(result));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            XposedBridge.log(e);
            reverseResourceMap.clear();
        }
        if (reverseResourceMap.isEmpty()) {
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
                                String key = resourceString.toLowerCase().replaceAll("\\s", "");
                                if (reverseResourceMap.containsKey(key)) continue;
                                reverseResourceMap.put(key, String.valueOf(i));
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
            try {
                Field result = functionCall.call();
                if (result == null) throw new NoSuchFieldException("Field is null");
                saveField(methodName, result);
                return result;
            } catch (Exception e) {
                throw new Exception("Error getting field " + methodName + ": " + e.getMessage(), e);
            }
        }
        return getFieldFromJson(loader, new JSONObject(value));
    }

    public Field[] getFields(ClassLoader loader, FunctionCall<Field[]> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            try {
                Field[] result = functionCall.call();
                if (result == null) throw new NoSuchFieldException("Fields is null");
                saveFields(methodName, result);
                return result;
            } catch (Exception e) {
                throw new Exception("Error getting fields " + methodName + ": " + e.getMessage(), e);
            }
        }
        ArrayList<Field> fields = new ArrayList<>();
        JSONArray fieldsJson = new JSONArray(value);
        for (int i = 0; i < fieldsJson.length(); i++) {
            fields.add(getFieldFromJson(loader, fieldsJson.getJSONObject(i)));
        }
        return fields.toArray(new Field[0]);
    }

    public Method getMethod(ClassLoader loader, FunctionCall<Method> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            try {
                Method result = functionCall.call();
                if (result == null) throw new NoSuchMethodException("Method is null");
                saveMethod(methodName, result);
                return result;
            } catch (Exception e) {
                throw new Exception("Error getting method " + methodName + ": " + e.getMessage(), e);
            }
        }
        return getMethodFromJsonString(loader, value);
    }

    public Method[] getMethods(ClassLoader loader, FunctionCall<Method[]> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            try {
                Method[] result = functionCall.call();
                if (result == null) throw new NoSuchMethodException("Methods is null");
                if (result.length == 0)throw new NoSuchMethodException("Methods is empty");
                saveMethods(methodName, result);
                return result;
            } catch (Exception e) {
                throw new Exception("Error getting methods " + methodName + ": " + e.getMessage(), e);
            }
        }
        ArrayList<Method> methods = new ArrayList<>();
        JSONArray methodsJson = new JSONArray(value);
        for (int i = 0; i < methodsJson.length(); i++) {
            methods.add(getMethodFromJson(loader, methodsJson.getJSONObject(i)));
        }
        return methods.toArray(new Method[0]);
    }

    @NonNull
    private Method getMethodFromJsonString(ClassLoader loader, String value) throws JSONException {
        return getMethodFromJson(loader, new JSONObject(value));
    }


    public Class<?> getClass(ClassLoader loader, FunctionCall<Class<?>> functionCall) throws Exception {
        return getClass(loader, getKeyName(), functionCall);
    }

    public Class<?> getClass(ClassLoader loader, String key, FunctionCall<Class<?>> functionCall) throws Exception {
        String value = sPrefsCacheHooks.getString(key, null);
        if (value == null) {
            try {
                Class<?> result = functionCall.call();
                if (result == null) throw new ClassNotFoundException("Class is null");
                saveClass(key, result);
                return result;
            } catch (Exception e) {
                throw new Exception("Error getting class " + key + ": " + e.getMessage(), e);
            }
        }
        return getClassFromJson(loader, new JSONObject(value));
    }

    public Class<?>[] getClasses(ClassLoader loader, FunctionCall<Class<?>[]> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            try {
                Class<?>[] result = functionCall.call();
                if (result == null) throw new ClassNotFoundException("Classes is null");
                saveClasses(methodName, result);
                return result;
            } catch (Exception e) {
                throw new Exception("Error getting classes " + methodName + ": " + e.getMessage(), e);
            }
        }
        ArrayList<Class<?>> classes = new ArrayList<>();
        JSONArray classesJson = new JSONArray(value);
        for (int i = 0; i < classesJson.length(); i++) {
            classes.add(getClassFromJson(loader, classesJson.getJSONObject(i)));
        }
        return classes.toArray(new Class<?>[0]);
    }

    public HashMap<String, Field> getMapField(ClassLoader loader, FunctionCall<HashMap<String, Field>> functionCall) throws Exception {
        return getMapField(loader, getKeyName(), functionCall);
    }

    public HashMap<String, Field> getMapField(ClassLoader loader, String key, FunctionCall<HashMap<String, Field>> functionCall) throws Exception {
        String value = sPrefsCacheHooks.getString(key, null);
        if (value == null) {
            try {
                var result = functionCall.call();
                if (result == null) throw new Exception("HashMap is null");
                saveHashMap(key, result);
                return result;
            } catch (Exception e) {
                throw new Exception("Error getting HashMap " + key + ": " + e.getMessage(), e);
            }
        }
        return loadHashMap(loader, key);
    }

    private void saveHashMap(String key, HashMap<String, Field> map) {
        JSONObject jsonObject = new JSONObject();
        for (Map.Entry<String, Field> entry : map.entrySet()) {
            try {
                jsonObject.put(entry.getKey(), fieldToJson(entry.getValue()));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        sPrefsCacheHooks.edit().putString(key, jsonObject.toString()).apply();
    }

    private HashMap<String, Field> loadHashMap(ClassLoader loader, String key) {
        HashMap<String, Field> map = new HashMap<>();
        String jsonString = sPrefsCacheHooks.getString(key, null);
        if (jsonString == null) return map;

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keys = jsonObject.keys();

            while (keys.hasNext()) {
                String mapKey = keys.next();
                try {
                    map.put(mapKey, getFieldFromJson(loader, jsonObject.getJSONObject(mapKey)));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return map;
    }


    @SuppressWarnings("ApplySharedPref")
    public void saveField(String key, Field field) {
        sPrefsCacheHooks.edit().putString(key, fieldToJson(field).toString()).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveFields(String key, Field[] fields) {
        JSONArray values = new JSONArray();
        for (Field field : fields) {
            values.put(fieldToJson(field));
        }
        sPrefsCacheHooks.edit().putString(key, values.toString()).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveMethod(String key, Method method) {
        sPrefsCacheHooks.edit().putString(key, methodToJson(method).toString()).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveMethods(String key, Method[] methods) {
        JSONArray values = new JSONArray();
        for (Method method : methods) {
            values.put(methodToJson(method));
        }
        sPrefsCacheHooks.edit().putString(key, values.toString()).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveClass(String message, Class<?> messageClass) {
        sPrefsCacheHooks.edit().putString(message, classToJson(messageClass).toString()).commit();
    }

    @SuppressWarnings("ApplySharedPref")
    public void saveClasses(String message, Class<?>[] messageClass) {
        JSONArray values = new JSONArray();
        for (Class<?> aClass : messageClass) {
            values.put(classToJson(aClass));
        }
        sPrefsCacheHooks.edit().putString(message, values.toString()).commit();
    }

    private JSONObject fieldToJson(Field field) {
        JSONObject value = new JSONObject();
        try {
            value.put("class", field.getDeclaringClass().getName());
            value.put("name", field.getName());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    private Field getFieldFromJson(ClassLoader loader, JSONObject value) throws JSONException {
        Class<?> cls = ReflectionUtils.findClass(value.getString("class"), loader);
        return XposedHelpers.findField(cls, value.getString("name"));
    }

    private JSONObject methodToJson(Method method) {
        JSONObject value = new JSONObject();
        try {
            value.put("class", method.getDeclaringClass().getName());
            value.put("name", method.getName());
            value.put("params", classArrayToJson(method.getParameterTypes()));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    private Method getMethodFromJson(ClassLoader loader, JSONObject value) throws JSONException {
        Class<?> cls = ReflectionUtils.findClass(value.getString("class"), loader);
        Class<?>[] paramTypes = classArrayFromJson(loader, value.getJSONArray("params"));
        return XposedHelpers.findMethodExact(cls, value.getString("name"), paramTypes);
    }

    private JSONObject classToJson(Class<?> cls) {
        JSONObject value = new JSONObject();
        try {
            value.put("class", cls.getName());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    private Class<?> getClassFromJson(ClassLoader loader, JSONObject value) throws JSONException {
        return XposedHelpers.findClass(value.getString("class"), loader);
    }

    private JSONArray classArrayToJson(Class<?>[] classes) {
        JSONArray values = new JSONArray();
        for (Class<?> cls : classes) {
            values.put(cls.getName());
        }
        return values;
    }

    private Class<?>[] classArrayFromJson(ClassLoader loader, JSONArray values) throws JSONException {
        Class<?>[] classes = new Class<?>[values.length()];
        for (int i = 0; i < values.length(); i++) {
            classes[i] = ReflectionUtils.findClass(values.getString(i), loader);
        }
        return classes;
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
        JSONObject constructorJson = new JSONObject(value);
        Class<?> cls = XposedHelpers.findClass(constructorJson.getString("class"), loader);
        Class<?>[] paramTypes = classArrayFromJson(loader, constructorJson.getJSONArray("params"));
        return XposedHelpers.findConstructorExact(cls, paramTypes);
    }

    @SuppressWarnings("ApplySharedPref")
    private void saveConstructor(String key, Constructor constructor) {
        JSONObject value = new JSONObject();
        try {
            value.put("class", constructor.getDeclaringClass().getName());
            value.put("params", classArrayToJson(constructor.getParameterTypes()));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        sPrefsCacheHooks.edit().putString(key, value.toString()).commit();
    }

    public Number getNumber(ClassLoader loader, FunctionCall<Number> functionCall) throws Exception {
        var methodName = getKeyName();
        String value = sPrefsCacheHooks.getString(methodName, null);
        if (value == null) {
            try {
                Number result = functionCall.call();
                if (result == null) throw new Exception("Number is null");
                saveNumber(methodName, result);
                return result;
            } catch (Exception e) {
                throw new Exception("Error getting number " + methodName + ": " + e.getMessage(), e);
            }
        }
        return loadNumber(new JSONObject(value));
    }

    @SuppressWarnings("ApplySharedPref")
    private void saveNumber(String key, Number number) {
        JSONObject value = new JSONObject();
        try {
            value.put("class", number.getClass().getName());
            value.put("value", number);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        sPrefsCacheHooks.edit().putString(key, value.toString()).commit();
    }

    private Number loadNumber(JSONObject value) throws JSONException {
        String className = value.getString("class");

        return switch (className) {
            case "java.lang.Integer" -> value.getInt("value");
            case "java.lang.Long" -> value.getLong("value");
            case "java.lang.Float" -> (float) value.getDouble("value");
            case "java.lang.Double" -> value.getDouble("value");
            case "java.lang.Short" -> (short) value.getInt("value");
            case "java.lang.Byte" -> (byte) value.getInt("value");
            default -> value.getLong("value");
        };
    }

    public interface FunctionCall<T> {
        T call() throws Exception;
    }

}
