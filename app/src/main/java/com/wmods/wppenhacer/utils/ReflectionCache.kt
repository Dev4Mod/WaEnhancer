package com.wmods.wppenhacer.utils

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.LruCache
import android.util.Log
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * ReflectionCache: LRU-bounded caches for Class / Method / Field lookups.
 * - Thread-safety: all public methods are synchronized.
 * - Keys are ClassLoader-aware to avoid mixing classes from different loaders.
 * - Eviction logging helps tuning cache sizes.
 */
object ReflectionCache {

    // Tune these based on observed behavior. Larger means fewer lookups but more memory.
    private const val CACHE_CLASS_SIZE = 256
    private const val CACHE_METHOD_SIZE = 1024
    private const val CACHE_FIELD_SIZE = 1024

    private val classCache = object : LruCache<String, Class<*>>(CACHE_CLASS_SIZE) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Class<*>?, newValue: Class<*>?) {
            // Eviction logging disabled for performance
            // if (evicted) XposedBridge.log("ReflectionCache: class evicted: $key")
        }
    }

    private val methodCache = object : LruCache<String, Method>(CACHE_METHOD_SIZE) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Method?, newValue: Method?) {
            // Eviction logging disabled for performance
            // if (evicted) XposedBridge.log("ReflectionCache: method evicted: $key")
        }
    }

    private val fieldCache = object : LruCache<String, Field>(CACHE_FIELD_SIZE) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Field?, newValue: Field?) {
            // Eviction logging disabled for performance
            // if (evicted) XposedBridge.log("ReflectionCache: field evicted: $key")
        }
    }

    /** Enable perf logging for slow lookups (keep false in production normally). */
    @Volatile
    var PERF_LOGGING = false

    fun clear() = synchronized(this) {
        classCache.evictAll()
        methodCache.evictAll()
        fieldCache.evictAll()
        XposedBridge.log("ReflectionCache: cleared all caches")
    }

    fun trimToSize(maxSize: Int) = synchronized(this) {
        // Trim each cache proportionally
        if (maxSize >= 0) {
            methodCache.trimToSize(maxSize)
            fieldCache.trimToSize(maxSize)
            classCache.trimToSize(maxSize / 4)
            XposedBridge.log("ReflectionCache: trimmed caches to $maxSize")
        }
    }

    /**
     * Registers a low-memory callback to automatically clear caches when the host app is under pressure.
     * Call this once during module initialization with the Application context.
     */
    fun registerMemoryCallbacks(context: Context) {
        try {
            context.registerComponentCallbacks(object : ComponentCallbacks2 {
                @Suppress("DEPRECATION")
                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                        XposedBridge.log("ReflectionCache: low memory trim (level=$level) -> clearing caches")
                        clear()
                    }
                }

                override fun onConfigurationChanged(newConfig: Configuration) {}

                override fun onLowMemory() {
                    XposedBridge.log("ReflectionCache: onLowMemory -> clearing caches")
                    clear()
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("ReflectionCache: failed to register memory callbacks: $t")
        }
    }

    private fun classKey(className: String, loader: ClassLoader?): String {
        val loaderId = loader?.let { System.identityHashCode(it) } ?: 0
        return "${loaderId}_$className"
    }

    private fun methodKey(clazz: Class<*>, name: String, signature: String): String =
        "${System.identityHashCode(clazz)}_${name}_$signature"

    private fun fieldKey(clazz: Class<*>, name: String): String =
        "${System.identityHashCode(clazz)}_${name}"

    /** Returns the Class object using the provided loader when available. */
    @Synchronized
    fun getClass(name: String, loader: ClassLoader? = null): Class<*>? {
        val key = classKey(name, loader)
        classCache.get(key)?.let { return it }

        val start = SystemClock.elapsedRealtime()
        val cls = try {
            if (loader != null) {
                Class.forName(name, false, loader)
            } else {
                Class.forName(name)
            }
        } catch (t: Throwable) {
            XposedBridge.log("ReflectionCache: class lookup failed for $name: $t")
            null
        }

        cls?.let { classCache.put(key, it) }

        if (PERF_LOGGING) {
            val dur = SystemClock.elapsedRealtime() - start
            if (dur > 20) Log.w("ReflectionCache", "getClass($name) took ${dur}ms")
        }

        return cls
    }

    /**
     * Get method by class object (preferred) or via classname loader if needed.
     * signature is built from parameter types for unique keys.
     */
    @Synchronized
    fun getMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        val sig = parameterTypes.joinToString(",") { it.name }
        val key = methodKey(clazz, name, sig)
        methodCache.get(key)?.let { return it }

        val start = SystemClock.elapsedRealtime()
        val method = try {
            try {
                val m = clazz.getMethod(name, *parameterTypes)
                m.isAccessible = true
                m
            } catch (nsme: NoSuchMethodException) {
                val m = clazz.getDeclaredMethod(name, *parameterTypes)
                m.isAccessible = true
                m
            }
        } catch (t: Throwable) {
            XposedBridge.log("ReflectionCache: method lookup failed for ${clazz.name}.$name($sig): $t")
            null
        }

        method?.let { methodCache.put(key, it) }

        if (PERF_LOGGING) {
            val dur = SystemClock.elapsedRealtime() - start
            if (dur > 10) Log.w("ReflectionCache", "getMethod(${clazz.name}.$name) took ${dur}ms")
        }

        return method
    }

    /** Convenience overload: lookup by class name + loader */
    @Synchronized
    fun getMethod(className: String, loader: ClassLoader?, name: String, vararg parameterTypes: Class<*>): Method? {
        val cls = getClass(className, loader) ?: return null
        return getMethod(cls, name, *parameterTypes)
    }

    @Synchronized
    fun getField(clazz: Class<*>, name: String): Field? {
        val key = fieldKey(clazz, name)
        fieldCache.get(key)?.let { return it }

        val start = SystemClock.elapsedRealtime()
        val field = try {
            try {
                val f = clazz.getField(name)
                f.isAccessible = true
                f
            } catch (nsfe: NoSuchFieldException) {
                val f = clazz.getDeclaredField(name)
                f.isAccessible = true
                f
            }
        } catch (t: Throwable) {
            XposedBridge.log("ReflectionCache: field lookup failed for ${clazz.name}.$name: $t")
            null
        }

        field?.let { fieldCache.put(key, it) }

        if (PERF_LOGGING) {
            val dur = SystemClock.elapsedRealtime() - start
            if (dur > 10) Log.w("ReflectionCache", "getField(${clazz.name}.$name) took ${dur}ms")
        }
        return field
    }

    /** Convenience overload: lookup by class name + loader */
    @Synchronized
    fun getField(className: String, loader: ClassLoader?, name: String): Field? {
        val cls = getClass(className, loader) ?: return null
        return getField(cls, name)
    }
}
