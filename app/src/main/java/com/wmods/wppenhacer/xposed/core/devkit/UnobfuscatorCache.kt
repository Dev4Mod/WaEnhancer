package com.wmods.wppenhacer.xposed.core.devkit

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.widget.Toast
import com.google.devrel.gmscore.tools.apk.arsc.ArscUtils
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import androidx.core.content.edit

class UnobfuscatorCache private constructor(private val mApplication: Application) {

    val sPrefsCacheHooks: SharedPreferences
    private val sPrefsCacheStrings: SharedPreferences
    private val reverseResourceMap = HashMap<String, String>()

    init {
        try {
            sPrefsCacheHooks =
                mApplication.getSharedPreferences("UnobfuscatorCache", Context.MODE_PRIVATE)
            sPrefsCacheStrings =
                mApplication.getSharedPreferences("UnobfuscatorCacheStrings", Context.MODE_PRIVATE)
            val version = sPrefsCacheHooks.getLong("version", 0)
            val currentVersion = mApplication.packageManager
                .getPackageInfo(mApplication.packageName, 0).longVersionCode
            val savedUpdateTime = sPrefsCacheHooks.getLong("updateTime", 0)
            val savedCacheSchemaVersion = sPrefsCacheHooks.getInt("cache_schema_version", 0)
            val savedVersionName = sPrefsCacheHooks.getString("wae_version_name", "") ?: ""
            val versionName = BuildConfig.VERSION_NAME
            var lastUpdateTime = savedUpdateTime
            try {
                lastUpdateTime = mApplication.packageManager
                    .getPackageInfo(BuildConfig.APPLICATION_ID, 0).lastUpdateTime
            } catch (_: Exception) {
            }
            if (version != currentVersion || savedUpdateTime != lastUpdateTime
                || versionName != savedVersionName
                || savedCacheSchemaVersion != CACHE_SCHEMA_VERSION
            ) {
                Utils.showToast(mApplication.getString(R.string.starting_cache), Toast.LENGTH_LONG)
                sPrefsCacheHooks.edit(commit = true) { clear() }
                sPrefsCacheHooks.edit(commit = true) { putLong("version", currentVersion) }
                sPrefsCacheHooks.edit(commit = true) { putLong("updateTime", lastUpdateTime) }
                sPrefsCacheHooks.edit(commit = true) {
                    putInt(
                        "cache_schema_version",
                        CACHE_SCHEMA_VERSION
                    )
                }
                sPrefsCacheHooks.edit(commit = true) { putString("wae_version_name", versionName) }
                sPrefsCacheStrings.edit(commit = true) { clear() }
            }
            initCacheStrings()
        } catch (e: Exception) {
            throw RuntimeException("Can't initialize UnobfuscatorCache: ${e.message}", e)
        }
    }

    companion object {
        private const val CACHE_SCHEMA_VERSION = 2
        private var mInstance: UnobfuscatorCache? = null

        @JvmStatic
        fun init(mApp: Application) {
            if (mInstance == null) {
                mInstance = UnobfuscatorCache(mApp)
            }
        }

        @JvmStatic
        fun getInstance(): UnobfuscatorCache {
            return mInstance!!
        }
    }

    private fun initCacheStrings() {
        getOfuscateIDString("mystatus")
        getOfuscateIDString("online")
        getOfuscateIDString("groups")
        getOfuscateIDString("messagedeleted")
        getOfuscateIDString("selectcalltype")
        getOfuscateIDString("lastseensun%s")
        getOfuscateIDString("updates")
    }

    private fun initializeReverseResourceMap() {
        try {
            val app = Utils.getApplication()
            val source = app.applicationInfo.sourceDir
            val table = ArscUtils.getResourceTable(File(source))
            val pool = table.stringPool
            val pkg = table.getPackage(app.packageName) ?: return
            val typeChunks = pkg.getTypeChunks("string")
            val chunk = typeChunks.stream().filter { typeChunk ->
                typeChunk.configuration.isDefault
            }.findFirst().orElse(null) ?: return
            val entries = chunk.entries
            val baseValue = 0x7f12
            for ((keyHexValue, entry) in entries) {
                try {
                    val result = baseValue shl 16 or keyHexValue
                    val resourceString =
                        pool.getString(entry.value()!!.data()).lowercase(Locale.ROOT)
                            .replace("\\s".toRegex(), "")
                    if (reverseResourceMap.containsKey(resourceString)) continue
                    reverseResourceMap[resourceString] = result.toString()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
            reverseResourceMap.clear()
        }
        if (reverseResourceMap.isEmpty()) {
            initializeReverseResourceMapBruteForce()
        }
    }

    private fun initializeReverseResourceMapBruteForce() {
        val currentTime = System.currentTimeMillis()
        val numThreads = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(numThreads)
        try {
            val configuration = Configuration(mApplication.resources.configuration)
            configuration.setLocale(Locale.ENGLISH)
            val context = Utils.getApplication().createConfigurationContext(configuration)
            val resources = context.resources
            val startId = 0x7f120000
            val endId = 0x7f12ffff
            val chunkSize = (endId - startId + 1) / numThreads
            val latch = CountDownLatch(numThreads)
            for (t in 0 until numThreads) {
                val threadStartId = startId + t * chunkSize
                val threadEndId =
                    if (t == numThreads - 1) endId else threadStartId + chunkSize - 1
                executor.submit {
                    try {
                        for (i in threadStartId..threadEndId) {
                            try {
                                val resourceString = resources.getString(i)
                                val key = resourceString.lowercase(Locale.ROOT)
                                    .replace("\\s".toRegex(), "")
                                if (reverseResourceMap.containsKey(key)) continue
                                reverseResourceMap[key] = i.toString()
                            } catch (_: Resources.NotFoundException) {
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
            XposedBridge.log(
                "String cache saved in ${System.currentTimeMillis() - currentTime}ms"
            )
        } catch (e: Exception) {
            XposedBridge.log(e)
        } finally {
            executor.shutdown()
        }
    }

    private fun getMapIdString(search: String): String? {
        if (reverseResourceMap.isEmpty()) {
            initializeReverseResourceMap()
            System.gc()
        }
        val s = search.lowercase(Locale.ROOT).replace("\\s".toRegex(), "")
        XposedBridge.log("need search obsfucate: $s")
        return reverseResourceMap[s]
    }

    fun getOfuscateIDString(search: String): Int {
        val s = search.lowercase(Locale.ROOT).replace("\\s".toRegex(), "")
        var id = sPrefsCacheStrings.getString(s, null)
        if (id == null) {
            id = getMapIdString(s)
            if (id != null) {
                sPrefsCacheStrings.edit { putString(s, id) }
            }
        }
        return id?.toInt() ?: -1
    }

    fun getString(search: String): String {
        val id = getOfuscateIDString(search)
        return if (id < 1) "" else mApplication.resources.getString(id)
    }

    fun getField(loader: ClassLoader, functionCall: FunctionCall<Field>): Field {
        val methodName = getKeyName()
        val value = sPrefsCacheHooks.getString(methodName, null) ?: try {
            val result = functionCall.call() ?: throw NoSuchFieldException("Field is null")
            saveField(methodName, result)
            return result
        } catch (e: Exception) {
            throw Exception("Error getting field $methodName: ${e.message}", e)
        }
        return getFieldFromJson(loader, JSONObject(value))
    }

    @Suppress("unused")
    fun getFields(loader: ClassLoader, functionCall: FunctionCall<Array<Field>>): Array<Field> {
        val methodName = getKeyName()
        val value = sPrefsCacheHooks.getString(methodName, null) ?: try {
            val result = functionCall.call() ?: throw NoSuchFieldException("Fields is null")
            saveFields(methodName, result)
            return result
        } catch (e: Exception) {
            throw Exception("Error getting fields $methodName: ${e.message}", e)
        }
        val fields = ArrayList<Field>()
        val fieldsJson = JSONArray(value)
        for (i in 0 until fieldsJson.length()) {
            fields.add(getFieldFromJson(loader, fieldsJson.getJSONObject(i)))
        }
        return fields.toTypedArray()
    }

    fun getMethod(loader: ClassLoader, functionCall: FunctionCall<Method>): Method {
        val methodName = getKeyName()
        val value = sPrefsCacheHooks.getString(methodName, null) ?: try {
            val result = functionCall.call() ?: throw NoSuchMethodException("Method is null")
            saveMethod(methodName, result)
            return result
        } catch (e: Exception) {
            throw Exception("Error getting method $methodName: ${e.message}", e)
        }
        return getMethodFromJsonString(loader, value)
    }

    fun getMethods(loader: ClassLoader, functionCall: FunctionCall<Array<Method>>): Array<Method> {
        val methodName = getKeyName()
        val value = sPrefsCacheHooks.getString(methodName, null) ?: try {
            val result = functionCall.call() ?: throw NoSuchMethodException("Methods is null")
            if (result.isEmpty()) throw NoSuchMethodException("Methods is empty")
            saveMethods(methodName, result)
            return result
        } catch (e: Exception) {
            throw Exception("Error getting methods $methodName: ${e.message}", e)
        }
        val methods = ArrayList<Method>()
        val methodsJson = JSONArray(value)
        for (i in 0 until methodsJson.length()) {
            methods.add(getMethodFromJson(loader, methodsJson.getJSONObject(i)))
        }
        return methods.toTypedArray()
    }

    private fun getMethodFromJsonString(loader: ClassLoader, value: String): Method {
        return getMethodFromJson(loader, JSONObject(value))
    }

    fun getClass(loader: ClassLoader, functionCall: FunctionCall<Class<*>>): Class<*> {
        return getClass(loader, getKeyName(), functionCall)
    }

    fun getClass(
        loader: ClassLoader,
        key: String,
        functionCall: FunctionCall<Class<*>>
    ): Class<*> {
        val value = sPrefsCacheHooks.getString(key, null) ?: try {
            val result = functionCall.call() ?: throw ClassNotFoundException("Class is null")
            saveClass(key, result)
            return result
        } catch (e: Exception) {
            throw Exception("Error getting class $key: ${e.message}", e)
        }
        return getClassFromJson(loader, JSONObject(value))
    }

    fun getClasses(
        loader: ClassLoader,
        functionCall: FunctionCall<Array<Class<*>>>
    ): Array<Class<*>> {
        val methodName = getKeyName()
        val value = sPrefsCacheHooks.getString(methodName, null) ?: try {
            val result = functionCall.call() ?: throw ClassNotFoundException("Classes is null")
            saveClasses(methodName, result)
            return result
        } catch (e: Exception) {
            throw Exception("Error getting classes $methodName: ${e.message}", e)
        }
        val classes = ArrayList<Class<*>>()
        val classesJson = JSONArray(value)
        for (i in 0 until classesJson.length()) {
            classes.add(getClassFromJson(loader, classesJson.getJSONObject(i)))
        }
        return classes.toTypedArray()
    }

    fun getMapField(
        loader: ClassLoader,
        functionCall: FunctionCall<HashMap<String, Field>>
    ): HashMap<String, Field> {
        return getMapField(loader, getKeyName(), functionCall)
    }

    fun getMapField(
        loader: ClassLoader,
        key: String,
        functionCall: FunctionCall<HashMap<String, Field>>
    ): HashMap<String, Field> {
        sPrefsCacheHooks.getString(key, null) ?: try {
            val result = functionCall.call() ?: throw Exception("HashMap is null")
            saveHashMap(key, result)
            return result
        } catch (e: Exception) {
            throw Exception("Error getting HashMap $key: ${e.message}", e)
        }
        return loadHashMap(loader, key)
    }

    private fun saveHashMap(key: String, map: HashMap<String, Field>) {
        val jsonObject = JSONObject()
        for ((mapKey, field) in map) {
            try {
                jsonObject.put(mapKey, fieldToJson(field))
            } catch (e: JSONException) {
                XposedBridge.log(e)
            }
        }
        sPrefsCacheHooks.edit { putString(key, jsonObject.toString()) }
    }

    private fun loadHashMap(loader: ClassLoader, key: String): HashMap<String, Field> {
        val map = HashMap<String, Field>()
        val jsonString = sPrefsCacheHooks.getString(key, null) ?: return map
        try {
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val mapKey = keys.next()
                try {
                    map[mapKey] = getFieldFromJson(loader, jsonObject.getJSONObject(mapKey))
                } catch (e: Exception) {
                    XposedBridge.log(e)
                }
            }
        } catch (e: JSONException) {
            XposedBridge.log(e)
        }
        return map
    }

    fun saveField(key: String, field: Field) {
        sPrefsCacheHooks.edit { putString(key, fieldToJson(field).toString()) }
    }

    fun saveFields(key: String, fields: Array<Field>) {
        val values = JSONArray()
        for (field in fields) {
            values.put(fieldToJson(field))
        }
        sPrefsCacheHooks.edit { putString(key, values.toString()) }
    }

    fun saveMethod(key: String, method: Method) {
        sPrefsCacheHooks.edit { putString(key, methodToJson(method).toString()) }
    }

    fun saveMethods(key: String, methods: Array<Method>) {
        val values = JSONArray()
        for (method in methods) {
            values.put(methodToJson(method))
        }
        sPrefsCacheHooks.edit { putString(key, values.toString()) }
    }

    fun saveClass(key: String, messageClass: Class<*>) {
        sPrefsCacheHooks.edit { putString(key, classToJson(messageClass).toString()) }
    }

    fun saveClasses(key: String, messageClasses: Array<Class<*>>) {
        val values = JSONArray()
        for (aClass in messageClasses) {
            values.put(classToJson(aClass))
        }
        sPrefsCacheHooks.edit { putString(key, values.toString()) }
    }

    private fun fieldToJson(field: Field): JSONObject {
        val value = JSONObject()
        try {
            value.put("class", field.declaringClass.name)
            value.put("name", field.name)
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
        return value
    }

    private fun getFieldFromJson(loader: ClassLoader, value: JSONObject): Field {
        val cls = ReflectionUtils.findClass(value.getString("class"), loader)
        return XposedHelpers.findField(cls, value.getString("name"))
    }

    private fun methodToJson(method: Method): JSONObject {
        val value = JSONObject()
        try {
            value.put("class", method.declaringClass.name)
            value.put("name", method.name)
            value.put("params", classArrayToJson(method.parameterTypes))
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
        return value
    }

    private fun getMethodFromJson(loader: ClassLoader, value: JSONObject): Method {
        val cls = ReflectionUtils.findClass(value.getString("class"), loader)
        val paramTypes = classArrayFromJson(loader, value.getJSONArray("params"))
        return XposedHelpers.findMethodExact(cls, value.getString("name"), *paramTypes)
    }

    private fun classToJson(cls: Class<*>): JSONObject {
        val value = JSONObject()
        try {
            value.put("class", cls.name)
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
        return value
    }

    private fun getClassFromJson(loader: ClassLoader, value: JSONObject): Class<*> {
        return XposedHelpers.findClass(value.getString("class"), loader)
    }

    private fun classArrayToJson(classes: Array<Class<*>>): JSONArray {
        val values = JSONArray()
        for (cls in classes) {
            values.put(cls.name)
        }
        return values
    }

    private fun classArrayFromJson(loader: ClassLoader, values: JSONArray): Array<Class<*>> {
        return Array(values.length()) { i ->
            ReflectionUtils.findClass(values.getString(i), loader)
        }
    }

    private fun getKeyName(): String {
        val keyName = AtomicReference("")
        Thread.currentThread().stackTrace.firstOrNull { it.className == Unobfuscator::class.java.name }
            ?.let { keyName.set(it.methodName) }
        return keyName.get()
    }

    fun getConstructor(
        loader: ClassLoader,
        functionCall: FunctionCall<Constructor<*>>
    ): Constructor<*> {
        val methodName = getKeyName()
        val value = sPrefsCacheHooks.getString(methodName, null)
        if (value == null) {
            val result = functionCall.call() ?: throw Exception("Constructor is null")
            saveConstructor(methodName, result)
            return result
        }
        val constructorJson = JSONObject(value)
        val cls = XposedHelpers.findClass(constructorJson.getString("class"), loader)
        val paramTypes = classArrayFromJson(loader, constructorJson.getJSONArray("params"))
        return XposedHelpers.findConstructorExact(cls, *paramTypes)
    }

    private fun saveConstructor(key: String, constructor: Constructor<*>) {
        val value = JSONObject()
        try {
            value.put("class", constructor.declaringClass.name)
            value.put("params", classArrayToJson(constructor.parameterTypes))
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
        sPrefsCacheHooks.edit { putString(key, value.toString()) }
    }

    fun getNumber(ignored: ClassLoader, functionCall: FunctionCall<Number>): Number {
        val methodName = getKeyName()
        val value = sPrefsCacheHooks.getString(methodName, null) ?: try {
            val result = functionCall.call() ?: throw Exception("Number is null")
            saveNumber(methodName, result)
            return result
        } catch (e: Exception) {
            throw Exception("Error getting number $methodName: ${e.message}", e)
        }
        return loadNumber(JSONObject(value))
    }

    private fun saveNumber(key: String, number: Number) {
        val value = JSONObject()
        try {
            value.put("class", number.javaClass.name)
            value.put("value", number)
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
        sPrefsCacheHooks.edit { putString(key, value.toString()) }
    }

    private fun loadNumber(value: JSONObject): Number {
        val className = value.getString("class")
        return when (className) {
            "java.lang.Integer" -> value.getInt("value")
            "java.lang.Long" -> value.getLong("value")
            "java.lang.Float" -> value.getDouble("value").toFloat()
            "java.lang.Double" -> value.getDouble("value")
            "java.lang.Short" -> value.getInt("value").toShort()
            "java.lang.Byte" -> value.getInt("value").toByte()
            else -> value.getLong("value")
        }
    }

    fun interface FunctionCall<T> {
        fun call(): T?
    }
}
