package com.wmods.wppenhacer.xposed.core.datastore

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class UnobfuscatorCacheDataStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, CACHE_FILE_NAME)
    private val writeLock = Any()
    private val memoryCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var loaded = false

    private var flushScheduled = false
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "WAE-CacheWriter").apply { isDaemon = true }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(writeLock) {
            if (loaded) return
            readCacheFile()
            loaded = true
        }
        writer.execute { deleteLegacyDataStore() }
    }

    private fun readCacheFile() {
        if (!cacheFile.isFile) return
        try {
            DataInputStream(ByteArrayInputStream(cacheFile.readBytes())).use { input ->
                if (input.readInt() != FILE_MAGIC || input.readInt() != FILE_VERSION) {
                    YLog.warn("Unobfuscator cache file has an unknown format, starting empty")
                    return
                }
                repeat(input.readInt()) {
                    val key = input.readSizedString()
                    memoryCache[key] = input.readSizedString()
                }
            }
        } catch (e: Exception) {
            YLog.error("Unobfuscator cache file is corrupt, starting empty", e)
            memoryCache.clear()
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
            if (value == null) memoryCache.remove(namespacedKey) else memoryCache[namespacedKey] =
                value
            scheduleFlushLocked()
        }
    }

    private fun scheduleFlushLocked() {
        if (!flushScheduled) {
            flushScheduled = true
            writer.execute { flush() }
        }
    }

    fun flushBlocking() {
        try {
            writer.submit { flush() }.get()
        } catch (e: Exception) {
            YLog.error("flushBlocking failed", e)
        }
    }

    private fun flush() {
        val snapshot: Map<String, String>
        synchronized(writeLock) {
            if (!flushScheduled) return
            flushScheduled = false
            snapshot = HashMap(memoryCache)
        }
        val tmpFile = File(cacheFile.path + ".tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmpFile))).use { output ->
                output.writeInt(FILE_MAGIC)
                output.writeInt(FILE_VERSION)
                output.writeInt(snapshot.size)
                for ((key, value) in snapshot) {
                    output.writeSizedString(key)
                    output.writeSizedString(value)
                }
            }
            if (!tmpFile.renameTo(cacheFile)) {
                YLog.error("Failed to replace the unobfuscator cache file")
                tmpFile.delete()
            }
        } catch (e: Exception) {
            YLog.error("Failed to persist the unobfuscator cache", e)
            tmpFile.delete()
        }
    }

    private fun deleteLegacyDataStore() {
        val legacyFile = File(appContext.filesDir, LEGACY_DATASTORE_PATH)
        if (legacyFile.exists() && !legacyFile.delete()) {
            YLog.warn("Unable to delete the legacy unobfuscator DataStore file")
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
            memoryCache.keys.removeAll { it.startsWith(prefix) }
            scheduleFlushLocked()
        }
    }

    fun clearAll() {
        ensureLoaded()
        synchronized(writeLock) {
            memoryCache.clear()
            scheduleFlushLocked()
        }
    }

    private fun namespacedKey(namespace: String, key: String): String {
        return "$namespace::$key"
    }

    private fun DataOutputStream.writeSizedString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readSizedString(): String {
        val bytes = ByteArray(readInt())
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        private const val CACHE_FILE_NAME = "wae_unobfuscator_cache.bin"
        private const val LEGACY_DATASTORE_PATH = "datastore/unobfuscator_cache.preferences_pb"
        private const val FILE_MAGIC = 0x57414543
        private const val FILE_VERSION = 1
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
