package com.wmods.wppenhacer.xposed.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.highcapable.yukihookapi.hook.log.YLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class UnobfuscatorCacheDataStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val writeLock = Any()
    private val memoryCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var loaded = false

    // Writes are batched and flushed in order on a single background thread.
    // Reads always go through memoryCache, which is updated synchronously.
    private val pendingWrites = LinkedHashMap<String, String?>()
    private var flushScheduled = false
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "WAE-CacheWriter").apply { isDaemon = true }
    }

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { appContext.preferencesDataStoreFile(DATASTORE_FILE_NAME) }
    )

    // Loads the whole store once, so later misses never hit the disk.
    private fun ensureLoaded() {
        if (loaded) return
        synchronized(writeLock) {
            if (loaded) return
            val snapshot = runBlocking { dataStore.data.first() }
            snapshot.asMap().forEach { (k, v) ->
                if (v is String) memoryCache.putIfAbsent(k.name, v)
            }
            loaded = true
        }
    }

    fun getString(namespace: String, key: String, defaultValue: String?): String? {
        ensureLoaded()
        return memoryCache[namespacedKey(namespace, key)] ?: defaultValue
    }

    fun getInt(namespace: String, key: String, defaultValue: Int): Int {
        return getString(namespace, key, null)?.toIntOrNull() ?: defaultValue
    }

    fun getLong(namespace: String, key: String, defaultValue: Long): Long {
        return getString(namespace, key, null)?.toLongOrNull() ?: defaultValue
    }

    fun putString(namespace: String, key: String, value: String?) {
        ensureLoaded()
        val namespacedKey = namespacedKey(namespace, key)
        synchronized(writeLock) {
            if (value == null) memoryCache.remove(namespacedKey) else memoryCache[namespacedKey] = value
            enqueue(namespacedKey, value)
        }
    }

    // Must be called holding writeLock.
    private fun enqueue(namespacedKey: String, value: String?) {
        pendingWrites.remove(namespacedKey)
        pendingWrites[namespacedKey] = value
        if (!flushScheduled) {
            flushScheduled = true
            writer.execute { flush() }
        }
    }

    // Blocks until everything queued so far is on disk (e.g. before killing the process).
    fun flushBlocking() {
        try {
            writer.submit { flush() }.get()
        } catch (e: Exception) {
            YLog.error("flushBlocking failed", e)
        }
    }

    private fun flush() {
        val batch: Map<String, String?>
        synchronized(writeLock) {
            batch = LinkedHashMap(pendingWrites)
            pendingWrites.clear()
            flushScheduled = false
        }
        if (batch.isEmpty()) return
        try {
            runBlocking {
                dataStore.edit { preferences ->
                    batch.forEach { (k, v) ->
                        val datastoreKey = stringPreferencesKey(k)
                        if (v == null) preferences.remove(datastoreKey) else preferences[datastoreKey] = v
                    }
                }
            }
        } catch (e: Exception) {
            YLog.error("Failed to persist cache batch", e)
        }
    }

    fun putInt(namespace: String, key: String, value: Int) {
        putString(namespace, key, value.toString())
    }

    fun putLong(namespace: String, key: String, value: Long) {
        putString(namespace, key, value.toString())
    }

    fun remove(namespace: String, key: String) {
        putString(namespace, key, null)
    }

    fun clearNamespace(namespace: String) {
        ensureLoaded()
        val prefix = "$namespace::"
        synchronized(writeLock) {
            memoryCache.keys.filter { it.startsWith(prefix) }.forEach { k ->
                memoryCache.remove(k)
                enqueue(k, null)
            }
        }
    }

    fun clearAll() {
        ensureLoaded()
        synchronized(writeLock) {
            memoryCache.keys.toList().forEach { k ->
                memoryCache.remove(k)
                enqueue(k, null)
            }
        }
    }

    private fun namespacedKey(namespace: String, key: String): String {
        return "$namespace::$key"
    }

    companion object {
        private const val DATASTORE_FILE_NAME = "unobfuscator_cache"
        const val NAMESPACE_HOOKS = "hooks"
        const val NAMESPACE_STRINGS = "strings"
        const val NAMESPACE_REFLECTION = "reflection"

        @Volatile
        private var instance: UnobfuscatorCacheDataStore? = null

        @JvmStatic
        fun getInstance(context: Context): UnobfuscatorCacheDataStore {
            return instance ?: synchronized(this) {
                instance ?: UnobfuscatorCacheDataStore(context).also { instance = it }
            }
        }
    }
}
