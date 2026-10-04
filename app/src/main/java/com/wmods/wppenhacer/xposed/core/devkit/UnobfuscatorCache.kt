package com.wmods.wppenhacer.xposed.core.devkit

import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.widget.Toast
import com.google.devrel.gmscore.tools.apk.arsc.ArscUtils
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.datastore.UnobfuscatorCacheDataStore
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class UnobfuscatorCache private constructor(private val mApplication: Application) {

    private val cacheStore = UnobfuscatorCacheDataStore.getInstance(mApplication)
    private val reverseResourceMap = ConcurrentHashMap<String, String>()

    // One lock per key, so concurrent callers resolve a given key only once.
    private val keyLocks = ConcurrentHashMap<String, Any>()

    init {
        try {
            checkVersionAndResetIfNeeded()
            initCacheStrings()
        } catch (e: Exception) {
            throw RuntimeException("Can't initialize UnobfuscatorCache: ${e.message}", e)
        }
    }

    // ==================== Codecs ====================

    private interface CacheCodec<T> {
        fun serialize(value: T): String
        fun deserialize(raw: String, loader: ClassLoader): T
    }

    private val METHOD_CODEC = object : CacheCodec<Method> {
        override fun serialize(value: Method): String = methodToJson(value).toString()
        override fun deserialize(raw: String, loader: ClassLoader): Method =
            methodFromJson(loader, JSONObject(raw))
    }

    private val FIELD_CODEC = object : CacheCodec<Field> {
        override fun serialize(value: Field) = fieldToJson(value).toString()
        override fun deserialize(raw: String, loader: ClassLoader): Field =
            fieldFromJson(loader, JSONObject(raw))
    }

    private val CLASS_CODEC = object : CacheCodec<Class<*>> {
        override fun serialize(value: Class<*>) = JSONObject()
            .put("schema", CACHE_SCHEMA_VERSION)
            .put("kind", "class")
            .put("name", value.name)
            .toString()

        override fun deserialize(raw: String, loader: ClassLoader): Class<*> =
            ReflectionUtils.findClass(JSONObject(raw).getString("name"), loader)
    }

    private val CONSTRUCTOR_CODEC = object : CacheCodec<Constructor<*>> {
        override fun serialize(value: Constructor<*>): String = constructorToJson(value).toString()

        override fun deserialize(raw: String, loader: ClassLoader): Constructor<*> {
            val json = JSONObject(raw)
            val cls = ReflectionUtils.findClass(json.getString("declaringClass"), loader)
            val constructor = cls.getDeclaredConstructor(*readParameterTypes(loader, json))
            constructor.isAccessible = true
            return constructor
        }
    }

    private val NUMBER_CODEC = object : CacheCodec<Number> {
        override fun serialize(value: Number) = "${value.javaClass.name}:$value"
        override fun deserialize(raw: String, loader: ClassLoader): Number {
            val parts = raw.split(":", limit = 2)
            val className = if (parts.size == 2) parts[0] else "java.lang.Integer"
            val numberValue = if (parts.size == 2) parts[1] else raw
            return when (className) {
                "java.lang.Integer" -> numberValue.toInt()
                "java.lang.Long" -> numberValue.toLong()
                "java.lang.Float" -> numberValue.toFloat()
                "java.lang.Double" -> numberValue.toDouble()
                "java.lang.Short" -> numberValue.toShort()
                "java.lang.Byte" -> numberValue.toByte()
                else -> if (numberValue.contains(".")) numberValue.toDouble() else numberValue.toLong()
            }
        }
    }

    private val MAP_FIELD_CODEC = object : CacheCodec<HashMap<String, Field>> {
        override fun serialize(value: HashMap<String, Field>): String {
            val json = JSONObject()
                .put("schema", CACHE_SCHEMA_VERSION)
                .put("kind", "field-map")
            val items = JSONObject()
            for ((k, f) in value) {
                try {
                    items.put(k, fieldToJson(f))
                } catch (e: JSONException) {
                    YukiLog.log(e)
                }
            }
            json.put("items", items)
            return json.toString()
        }

        override fun deserialize(raw: String, loader: ClassLoader): HashMap<String, Field> {
            val map = HashMap<String, Field>()
            try {
                val json = JSONObject(raw).getJSONObject("items")
                val keys = json.keys()
                while (keys.hasNext()) {
                    val mapKey = keys.next()
                    try {
                        map[mapKey] = fieldFromJson(loader, json.getJSONObject(mapKey))
                    } catch (e: Exception) {
                        YukiLog.log(e)
                    }
                }
            } catch (e: JSONException) {
                YukiLog.log(e)
            }
            return map
        }
    }

    private inline fun <reified T> arrayCodec(elementCodec: CacheCodec<T>): CacheCodec<Array<T>> {
        return object : CacheCodec<Array<T>> {
            override fun serialize(value: Array<T>): String {
                val items = JSONArray()
                value.forEach { items.put(elementCodec.serialize(it)) }
                return JSONObject()
                    .put("schema", CACHE_SCHEMA_VERSION)
                    .put("kind", "array")
                    .put("items", items)
                    .toString()
            }

            override fun deserialize(raw: String, loader: ClassLoader): Array<T> {
                val items = JSONObject(raw).getJSONArray("items")
                return Array(items.length()) { elementCodec.deserialize(items.getString(it), loader) }
            }
        }
    }

    private fun methodToJson(method: Method): JSONObject = JSONObject()
        .put("schema", CACHE_SCHEMA_VERSION)
        .put("kind", "method")
        .put("declaringClass", method.declaringClass.name)
        .put("name", method.name)
        .put("parameterTypes", classNamesToJson(method.parameterTypes))
        .put("returnType", method.returnType.name)

    private fun methodFromJson(loader: ClassLoader, json: JSONObject): Method {
        val cls = ReflectionUtils.findClass(json.getString("declaringClass"), loader)
        val method =
            cls.getDeclaredMethod(json.getString("name"), *readParameterTypes(loader, json))
        val expected = json.optString("returnType")
        if (expected.isNotEmpty() && method.returnType.name != expected) {
            throw NoSuchMethodException("Return type mismatch: ${method.returnType.name} != $expected")
        }
        method.isAccessible = true
        return method
    }

    private fun fieldToJson(field: Field): JSONObject = JSONObject()
        .put("schema", CACHE_SCHEMA_VERSION)
        .put("kind", "field")
        .put("declaringClass", field.declaringClass.name)
        .put("name", field.name)
        .put("type", field.type.name)

    private fun fieldFromJson(loader: ClassLoader, json: JSONObject): Field {
        val cls = ReflectionUtils.findClass(json.getString("declaringClass"), loader)
        val field = cls.getDeclaredField(json.getString("name"))
        val expected = json.optString("type")
        if (expected.isNotEmpty() && field.type.name != expected) {
            throw NoSuchFieldException("Field type mismatch: ${field.type.name} != $expected")
        }
        field.isAccessible = true
        return field
    }

    private fun constructorToJson(constructor: Constructor<*>): JSONObject = JSONObject()
        .put("schema", CACHE_SCHEMA_VERSION)
        .put("kind", "constructor")
        .put("declaringClass", constructor.declaringClass.name)
        .put("parameterTypes", classNamesToJson(constructor.parameterTypes))

    private fun classNamesToJson(classes: Array<Class<*>>): JSONArray {
        val json = JSONArray()
        classes.forEach { json.put(it.name) }
        return json
    }

    private fun readParameterTypes(loader: ClassLoader, json: JSONObject): Array<Class<*>> {
        val params = json.getJSONArray("parameterTypes")
        return Array(params.length()) { ReflectionUtils.findClass(params.getString(it), loader) }
    }

    private val METHODS_CODEC = arrayCodec(METHOD_CODEC)
    private val FIELDS_CODEC = arrayCodec(FIELD_CODEC)
    private val CLASSES_CODEC = arrayCodec(CLASS_CODEC)

    // ==================== Initialization & Checks ====================

    private fun checkVersionAndResetIfNeeded() {
        val version = cacheStore.getLong(NAMESPACE_HOOKS, "version", 0)
        val currentVersion =
            mApplication.packageManager.getPackageInfo(mApplication.packageName, 0).longVersionCode
        val savedUpdateTime = cacheStore.getLong(NAMESPACE_HOOKS, "updateTime", 0)
        val savedVersionName = cacheStore.getString(NAMESPACE_HOOKS, "wae_version_name", "")
        val versionName = BuildConfig.VERSION_NAME

        var lastUpdateTime = savedUpdateTime
        try {
            lastUpdateTime = mApplication.packageManager.getPackageInfo(
                BuildConfig.APPLICATION_ID,
                0
            ).lastUpdateTime
        } catch (_: Exception) {
        }

        val whatsappUpdated = version != currentVersion
        val moduleUpdated = savedUpdateTime != lastUpdateTime && BuildConfig.RESET_ON_INSTALL
        val moduleVersionChanged = versionName != savedVersionName
        val schemaChanged =
            cacheStore.getInt(NAMESPACE_HOOKS, "cache_schema", 0) != CACHE_SCHEMA_VERSION

        if (whatsappUpdated || moduleUpdated || moduleVersionChanged || schemaChanged) {
            Utils.showToast(mApplication.getString(R.string.starting_cache), Toast.LENGTH_LONG)
            clearCache()
            cacheStore.putLong(NAMESPACE_HOOKS, "version", currentVersion)
            cacheStore.putLong(NAMESPACE_HOOKS, "updateTime", lastUpdateTime)
            cacheStore.putString(NAMESPACE_HOOKS, "wae_version_name", versionName)
            cacheStore.putInt(NAMESPACE_HOOKS, "cache_schema", CACHE_SCHEMA_VERSION)
            if (whatsappUpdated) {
                cacheStore.clearNamespace(NAMESPACE_STRINGS)
            }
        }
    }

    fun clearCache() {
        cacheStore.clearNamespace(NAMESPACE_HOOKS)
        cacheStore.clearNamespace(NAMESPACE_REFLECTION)
        cacheStore.flushBlocking()
    }

    // ==================== Core Cache Resolution ====================

    private fun <T : Any> resolve(
        key: String,
        loader: ClassLoader,
        functionCall: FunctionCall<T>,
        codec: CacheCodec<T>,
        onNull: () -> Exception
    ): T {
        readCached(key, loader, codec)?.let { return it }

        // Re-check inside the lock: another thread may have resolved the key while we waited.
        synchronized(keyLocks.computeIfAbsent(key) { Any() }) {
            readCached(key, loader, codec)?.let { return it }
            try {
                val result = functionCall.call() ?: throw onNull()
                cacheStore.putString(NAMESPACE_HOOKS, key, codec.serialize(result))
                return result
            } catch (e: Exception) {
                throw Exception("Error resolving $key: ${e.message}", e)
            }
        }
    }

    private fun <T : Any> readCached(key: String, loader: ClassLoader, codec: CacheCodec<T>): T? {
        val cached = cacheStore.getString(NAMESPACE_HOOKS, key, null) ?: return null
        return try {
            codec.deserialize(cached, loader)
        } catch (e: Throwable) {
            YukiLog.log("Invalid cache for $key, resolving again")
            YukiLog.log(e)
            cacheStore.remove(NAMESPACE_HOOKS, key)
            null
        }
    }

    // ==================== Public Getters ====================

    fun getMethod(loader: ClassLoader, functionCall: FunctionCall<Method>): Method =
        resolve(getKeyName(), loader, functionCall, METHOD_CODEC) {
            NoSuchMethodException("Method is null")
        }

    fun getMethods(loader: ClassLoader, functionCall: FunctionCall<Array<Method>>): Array<Method> =
        resolve(getKeyName(), loader, functionCall, METHODS_CODEC) {
            NoSuchMethodException("Methods is null")
        }

    fun getField(loader: ClassLoader, functionCall: FunctionCall<Field>): Field =
        resolve(getKeyName(), loader, functionCall, FIELD_CODEC) {
            NoSuchFieldException("Field is null")
        }

    @Suppress("unused")
    fun getFields(loader: ClassLoader, functionCall: FunctionCall<Array<Field>>): Array<Field> =
        resolve(getKeyName(), loader, functionCall, FIELDS_CODEC) {
            NoSuchFieldException("Fields is null")
        }

    fun getClass(loader: ClassLoader, functionCall: FunctionCall<Class<*>>): Class<*> =
        getClass(loader, getKeyName(), functionCall)

    fun getClass(
        loader: ClassLoader,
        key: String,
        functionCall: FunctionCall<Class<*>>
    ): Class<*> = resolve(key, loader, functionCall, CLASS_CODEC) {
        ClassNotFoundException("Class is null")
    }

    fun getClasses(
        loader: ClassLoader,
        functionCall: FunctionCall<Array<Class<*>>>
    ): Array<Class<*>> = resolve(getKeyName(), loader, functionCall, CLASSES_CODEC) {
        ClassNotFoundException("Classes is null")
    }

    fun getConstructor(
        loader: ClassLoader,
        functionCall: FunctionCall<Constructor<*>>
    ): Constructor<*> = resolve(getKeyName(), loader, functionCall, CONSTRUCTOR_CODEC) {
        Exception("Constructor is null")
    }

    fun getNumber(loader: ClassLoader, functionCall: FunctionCall<Number>): Number =
        resolve(getKeyName(), loader, functionCall, NUMBER_CODEC) { Exception("Number is null") }

    fun getMapField(
        loader: ClassLoader,
        functionCall: FunctionCall<HashMap<String, Field>>
    ): HashMap<String, Field> = getMapField(loader, getKeyName(), functionCall)

    fun getMapField(
        loader: ClassLoader,
        key: String,
        functionCall: FunctionCall<HashMap<String, Field>>
    ): HashMap<String, Field> =
        resolve(key, loader, functionCall, MAP_FIELD_CODEC) { Exception("HashMap is null") }

    // The key is the name of the Unobfuscator method that requested the value.
    private fun getKeyName(): String =
        Thread.currentThread().stackTrace
            .firstOrNull { it.className == Unobfuscator::class.java.name }
            ?.methodName ?: ""

    // ==================== Simples ====================

    fun getHookInt(key: String, defaultValue: Int): Int =
        cacheStore.getInt(NAMESPACE_HOOKS, key, defaultValue)

    fun putHookInt(key: String, value: Int) = cacheStore.putInt(NAMESPACE_HOOKS, key, value)

    // ==================== Strings ====================

    private fun initCacheStrings() {
        loadReverseResourceMapFromCache()
        listOf(
            "mystatus",
            "online",
            "groups",
            "messagedeleted",
            "selectcalltype",
            "lastseensun%s",
            "updates"
        ).forEach { getOfuscateIDString(it) }
    }

    private fun loadReverseResourceMapFromCache(): Boolean {
        val raw = cacheStore.getString(NAMESPACE_STRINGS, REVERSE_MAP_KEY, null) ?: return false
        return try {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                reverseResourceMap[k] = json.getString(k)
            }
            reverseResourceMap.isNotEmpty()
        } catch (_: Exception) {
            reverseResourceMap.clear()
            false
        }
    }

    private fun saveReverseResourceMapToCache() {
        if (reverseResourceMap.isEmpty()) return
        val json = JSONObject()
        for ((k, v) in reverseResourceMap) {
            try {
                json.put(k, v)
            } catch (_: JSONException) {
            }
        }
        cacheStore.putString(NAMESPACE_STRINGS, REVERSE_MAP_KEY, json.toString())
    }

    private fun initializeReverseResourceMap() {
        try {
            val app = Utils.application
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
            YukiLog.log(e)
            reverseResourceMap.clear()
        }
        if (reverseResourceMap.isEmpty()) {
            initializeReverseResourceMapBruteForce()
        }
        saveReverseResourceMapToCache()
    }

    private fun initializeReverseResourceMapBruteForce() {
        val currentTime = System.currentTimeMillis()
        val numThreads = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(numThreads)
        try {
            val configuration = Configuration(mApplication.resources.configuration)
            configuration.setLocale(Locale.ENGLISH)
            val context = Utils.application.createConfigurationContext(configuration)
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
            YukiLog.log(
                "String cache saved in ${System.currentTimeMillis() - currentTime}ms"
            )
        } catch (e: Exception) {
            YukiLog.log(e)
        } finally {
            executor.shutdown()
        }
    }

    private fun getMapIdString(search: String): String? {
        if (reverseResourceMap.isEmpty()) {
            initializeReverseResourceMap()
        }
        return reverseResourceMap[search.lowercase(Locale.ROOT).replace("\\s".toRegex(), "")]
    }

    fun getOfuscateIDString(search: String): Int {
        val s = search.lowercase(Locale.ROOT).replace("\\s".toRegex(), "")
        var id = cacheStore.getString(NAMESPACE_STRINGS, s, null)
        if (id == null) {
            id = getMapIdString(s)
            if (id != null) {
                cacheStore.putString(NAMESPACE_STRINGS, s, id)
            } else if (reverseResourceMap.isNotEmpty()) {
                // Negative cache: avoids searching the whole table again for a missing string.
                cacheStore.putString(NAMESPACE_STRINGS, s, NULL_SENTINEL)
            }
        }
        return id?.toIntOrNull() ?: -1
    }

    fun getString(search: String): String {
        val id = getOfuscateIDString(search)
        return if (id < 1) "" else mApplication.resources.getString(id)
    }

    fun interface FunctionCall<T> {
        fun call(): T?
    }

    companion object {
        private const val NAMESPACE_HOOKS = UnobfuscatorCacheDataStore.NAMESPACE_HOOKS
        private const val NAMESPACE_STRINGS = UnobfuscatorCacheDataStore.NAMESPACE_STRINGS
        private const val NAMESPACE_REFLECTION = UnobfuscatorCacheDataStore.NAMESPACE_REFLECTION
        private const val CACHE_SCHEMA_VERSION = 3
        private const val NULL_SENTINEL = "__NULL__"
        private const val REVERSE_MAP_KEY = "__reverse_map__"

        @Volatile
        private var mInstance: UnobfuscatorCache? = null

        @JvmStatic
        fun init(mApp: Application) {
            if (mInstance == null) {
                synchronized(this) {
                    if (mInstance == null) {
                        mInstance = UnobfuscatorCache(mApp)
                    }
                }
            }
        }

        @JvmStatic
        fun getInstance(): UnobfuscatorCache {
            return mInstance ?: throw IllegalStateException("UnobfuscatorCache is not initialized")
        }
    }
}
