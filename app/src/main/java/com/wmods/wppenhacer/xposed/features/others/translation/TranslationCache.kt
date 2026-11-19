package com.wmods.wppenhacer.xposed.features.others.translation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.LruCache
import com.wmods.wppenhacer.xposed.utils.YukiLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Two-tier translation cache:
 * - In-memory LruCache (~500 entries)
 * - Persistent SQLite database (capped at 2000 entries or 7 days old)
 */
class TranslationCache(context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // In-memory LRU cache (first tier)
    private val memoryCache = LruCache<String, String>(500)
    
    // SQLite database (second tier)
    private val dbHelper = TranslationDatabaseHelper(context)
    
    companion object {
        const val TAG = "[TranslationCache]"
        const val MAX_ENTRIES = 2000
        const val MAX_AGE_DAYS = 7
        
        fun hashPrompt(prompt: String?): String? {
            if (prompt.isNullOrBlank()) return null
            
            return try {
                val md = java.security.MessageDigest.getInstance("MD5")
                val digest = md.digest(prompt.toByteArray())
                digest.joinToString("") { "%02x".format(it) }.substring(0, 8)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Get cached translation if available
     * @param promptHash hash of the effective prompt (null for non-AI translations)
     * @return cached translation or null
     */
    fun get(messageId: String, targetLang: String, provider: String, promptHash: String? = null): String? {
        val providerKey = buildProviderKey(provider, promptHash)
        val key = buildKey(messageId, targetLang, providerKey)
        
        // Try memory cache first
        val memResult = memoryCache.get(key)
        if (memResult != null) {
            return memResult
        }
        
        // Try persistent cache (synchronous for simplicity, runs fast with index)
        return try {
            val dbResult = dbHelper.get(messageId, targetLang, providerKey)
            if (dbResult != null) {
                // Promote to memory cache
                memoryCache.put(key, dbResult)
            }
            dbResult
        } catch (e: Exception) {
            YukiLog.log("$TAG Error reading from DB: ${e.message}")
            null
        }
    }
    
    /**
     * Store translation in cache
     * @param promptHash hash of the effective prompt (null for non-AI translations)
     */
    fun put(messageId: String, targetLang: String, provider: String, translation: String, promptHash: String? = null) {
        val providerKey = buildProviderKey(provider, promptHash)
        val key = buildKey(messageId, targetLang, providerKey)
        
        // Store in memory cache
        memoryCache.put(key, translation)
        
        // Store in persistent cache (async)
        scope.launch {
            try {
                dbHelper.put(messageId, targetLang, providerKey, translation)
                
                // Prune old entries if needed (async, off main thread)
                pruneIfNeeded()
            } catch (e: Exception) {
                YukiLog.log("$TAG Error writing to DB: ${e.message}")
            }
        }
    }
    
    private fun buildProviderKey(provider: String, promptHash: String?): String {
        return if (promptHash.isNullOrBlank()) {
            provider
        } else {
            "$provider:$promptHash"
        }
    }
    
    private fun pruneIfNeeded() {
        try {
            // Count entries
            val count = dbHelper.count()
            if (count > MAX_ENTRIES) {
                // Delete oldest entries to get back to MAX_ENTRIES
                val toDelete = count - MAX_ENTRIES
                dbHelper.deleteOldest(toDelete)
                YukiLog.log("$TAG Pruned $toDelete old entries (count was $count)")
            }
            
            // Delete entries older than MAX_AGE_DAYS
            val deleted = dbHelper.deleteOlderThan(MAX_AGE_DAYS)
            if (deleted > 0) {
                YukiLog.log("$TAG Deleted $deleted entries older than $MAX_AGE_DAYS days")
            }
        } catch (e: Exception) {
            YukiLog.log("$TAG Error pruning DB: ${e.message}")
        }
    }
    
    private fun buildKey(messageId: String, targetLang: String, provider: String): String {
        return "$messageId|$targetLang|$provider"
    }
    
    fun close() {
        dbHelper.close()
    }
}

/**
 * SQLite helper for persistent translation cache
 */
private class TranslationDatabaseHelper(context: Context) : 
    SQLiteOpenHelper(context, "translation_cache.db", null, 2) {
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE translations (
                message_id TEXT NOT NULL,
                target_lang TEXT NOT NULL,
                provider TEXT NOT NULL,
                translation TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                PRIMARY KEY (message_id, target_lang, provider)
            )
        """.trimIndent())
        
        // Index for pruning by timestamp
        db.execSQL("CREATE INDEX idx_timestamp ON translations(timestamp)")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1->v2: Clear old 'openai' fallback results that were mislabeled
            db.execSQL("DELETE FROM translations WHERE provider = 'openai'")
        }
    }
    
    fun get(messageId: String, targetLang: String, provider: String): String? {
        val db = readableDatabase
        val cursor = db.query(
            "translations",
            arrayOf("translation"),
            "message_id = ? AND target_lang = ? AND provider = ?",
            arrayOf(messageId, targetLang, provider),
            null, null, null
        )
        
        return cursor.use {
            if (it.moveToFirst()) {
                it.getString(0)
            } else {
                null
            }
        }
    }
    
    fun put(messageId: String, targetLang: String, provider: String, translation: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("message_id", messageId)
            put("target_lang", targetLang)
            put("provider", provider)
            put("translation", translation)
            put("timestamp", System.currentTimeMillis())
        }
        
        db.insertWithOnConflict(
            "translations",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }
    
    fun count(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM translations", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }
    
    fun deleteOldest(count: Int) {
        val db = writableDatabase
        db.execSQL("""
            DELETE FROM translations
            WHERE rowid IN (
                SELECT rowid FROM translations
                ORDER BY timestamp ASC
                LIMIT ?
            )
        """.trimIndent(), arrayOf(count))
    }
    
    fun deleteOlderThan(days: Int): Int {
        val db = writableDatabase
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return db.delete(
            "translations",
            "timestamp < ?",
            arrayOf(cutoffTime.toString())
        )
    }
}
