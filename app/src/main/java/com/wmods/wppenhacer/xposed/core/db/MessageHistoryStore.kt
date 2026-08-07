package com.wmods.wppenhacer.xposed.core.db

import android.content.Context
import android.os.Looper
import android.util.LruCache
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.db.entity.HideSeenEntity
import com.wmods.wppenhacer.xposed.core.db.entity.MessageEntity
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.ConcurrentHashMap

class MessageHistoryStore private constructor(context: Context) {

    enum class ReceiptType {
        READ,
        PLAYED
    }

    interface HideSeenChangeListener {
        fun onHideSeenChanged(jid: String, messageId: String, type: ReceiptType, viewed: Boolean)
    }

    data class MessageItem(
        @JvmField val id: Long,
        @JvmField val message: String,
        @JvmField val timestamp: Long
    )

    class MessageSeenItem(
        @JvmField val jid: String,
        @JvmField val message: String,
        @JvmField val viewed: Boolean
    ) {
        private var fMessageWpp: FMessageWpp? = null

        val fMessage: FMessageWpp?
            get() {
                if (fMessageWpp == null) {
                    try {
                        val userJid = FMessageWpp.UserJid(jid)
                        if (userJid.isNull) return null
                        fMessageWpp = FMessageWpp.Key(message, userJid, false).fMessage
                    } catch (_: Exception) {
                    }
                }
                return fMessageWpp
            }
    }

    private val messagesCache = LruCache<Long, ArrayList<MessageItem>>(MESSAGE_CACHE_SIZE)
    private val seenMessageCache = LruCache<String, MessageSeenItem>(SEEN_MESSAGE_CACHE_SIZE)
    private val seenMessagesListCache =
        LruCache<String, List<MessageSeenItem>>(SEEN_MESSAGES_LIST_CACHE_SIZE)
    private val loadingCacheKeys = ConcurrentHashMap.newKeySet<String>()

    private val db: MessageHistoryDatabase = Room.databaseBuilder(
        context.applicationContext,
        MessageHistoryDatabase::class.java,
        "MessageHistory.db"
    )
        .addMigrations(MIGRATION_5_6)
        .setQueryExecutor(Utils.databaseExecutor)
        .setTransactionExecutor(Utils.databaseExecutor)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration(true)
        .build()

    private val messageDao = db.messageDao()
    private val hideSeenDao = db.hideSeenDao()

    companion object {
        private const val MESSAGE_CACHE_SIZE = 100
        private const val SEEN_MESSAGE_CACHE_SIZE = 200
        private const val SEEN_MESSAGES_LIST_CACHE_SIZE = 50

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_message_history_row_id " +
                            "ON MessageHistory (row_id)"
                )
            }
        }

        private val EMPTY_SEEN_ITEM = MessageSeenItem("", "", false)
        private val EMPTY_MESSAGE_LIST = ArrayList<MessageItem>()

        @Volatile
        private var hideSeenChangeListener: HideSeenChangeListener? = null

        @Volatile
        private var mInstance: MessageHistoryStore? = null

        @JvmStatic
        fun getInstance(): MessageHistoryStore {
            return mInstance ?: synchronized(this) {
                mInstance ?: MessageHistoryStore(Utils.application).also { mInstance = it }
            }
        }

        @JvmStatic
        fun setHideSeenChangeListener(listener: HideSeenChangeListener?) {
            hideSeenChangeListener = listener
        }


    }

    fun insertMessage(id: Long, message: String, timestamp: Long) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            insertMessageAsync(id, message, timestamp)
            return
        }
        try {
            messageDao.insert(
                MessageEntity(
                    rowId = id,
                    textData = message,
                    editTimestamp = timestamp
                )
            )
            messagesCache.remove(id)
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun insertMessageAsync(id: Long, message: String, timestamp: Long) {
        Utils.databaseExecutor.execute {
            insertMessage(id, message, timestamp)
        }
    }

    fun recordEditMessageAsync(id: Long, message: String, timestamp: Long) {
        Utils.databaseExecutor.execute {
            try {
                val originalMessage = MessageStore.getInstance().getCurrentMessageByID(id)
                if (getMessages(id) == null) {
                    insertMessage(id, originalMessage, 0)
                }
                insertMessage(id, message, timestamp)
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }
    }

    fun getMessages(v: Long): ArrayList<MessageItem>? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            scheduleCacheLoad("messages:$v") { getMessages(v) }
            return null
        }
        try {
            val cachedMessages = messagesCache.get(v)
            if (cachedMessages != null) {
                return if (cachedMessages === EMPTY_MESSAGE_LIST) null else cachedMessages
            }

            val history = messageDao.getMessagesByRowId(v)
            if (history.isNotEmpty()) {
                val messages = ArrayList<MessageItem>()
                for (entity in history) {
                    messages.add(
                        MessageItem(
                            entity.rowId,
                            entity.textData,
                            entity.editTimestamp ?: 0L
                        )
                    )
                }
                messagesCache.put(v, messages)
                return messages
            } else {
                messagesCache.put(v, EMPTY_MESSAGE_LIST)
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
        return null
    }

    fun getMessagesAsync(v: Long, callback: (ArrayList<MessageItem>) -> Unit) {
        Utils.databaseExecutor.execute {
            val messages = getMessages(v) ?: ArrayList()
            android.os.Handler(Looper.getMainLooper()).post {
                callback(messages)
            }
        }
    }

    fun insertHideSeenMessage(
        jid: String?,
        messageId: String?,
        type: ReceiptType?,
        viewed: Boolean
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            insertHideSeenMessageAsync(jid, messageId, type, viewed)
            return
        }
        try {
            if (jid == null || messageId == null || type == null) return

            val isViewedInt = if (viewed) 1 else 0

            db.runInTransaction {
                if (type == ReceiptType.PLAYED) {
                    hideSeenDao.insertOrIgnore(
                        HideSeenEntity(
                            jid = jid,
                            messageId = messageId,
                            played = isViewedInt
                        )
                    )
                    hideSeenDao.updatePlayed(jid, messageId, isViewedInt)
                } else {
                    hideSeenDao.insertOrIgnore(
                        HideSeenEntity(
                            jid = jid,
                            messageId = messageId,
                            read = isViewedInt
                        )
                    )
                    hideSeenDao.updateRead(jid, messageId, isViewedInt)
                }
            }

            val cacheKey = createSeenMessageCacheKey(jid, messageId, type)
            seenMessageCache.remove(cacheKey)
            invalidateSeenMessagesListCache(jid, type)
            hideSeenChangeListener?.onHideSeenChanged(jid, messageId, type, viewed)
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun insertHideSeenMessageAsync(
        jid: String?,
        messageId: String?,
        type: ReceiptType?,
        viewed: Boolean
    ) {
        Utils.databaseExecutor.execute {
            insertHideSeenMessage(jid, messageId, type, viewed)
        }
    }

    fun insertHideSeenMessagesAsync(
        jid: String?,
        messageIds: Iterable<String?>,
        type: ReceiptType?,
        viewed: Boolean
    ) {
        Utils.databaseExecutor.execute {
            for (messageId in messageIds) {
                insertHideSeenMessage(jid, messageId, type, viewed)
            }
        }
    }

    fun updateViewedMessage(
        jid: String?,
        messageId: String?,
        type: ReceiptType?,
        viewed: Boolean
    ): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateViewedMessageAsync(jid, messageId, type, viewed)
            return false
        }
        try {
            if (jid == null || messageId == null || type == null) return false

            val isViewedInt = if (viewed) 1 else 0
            val updatedRows = if (type == ReceiptType.PLAYED) {
                hideSeenDao.updatePlayed(jid, messageId, isViewedInt)
            } else {
                hideSeenDao.updateRead(jid, messageId, isViewedInt)
            }

            if (updatedRows <= 0) return false

            val cacheKey = createSeenMessageCacheKey(jid, messageId, type)
            val cachedItem = seenMessageCache.get(cacheKey)

            if (cachedItem != null && (cachedItem === EMPTY_SEEN_ITEM || cachedItem.viewed != viewed)) {
                seenMessageCache.remove(cacheKey)
            }
            invalidateSeenMessagesListCache(jid, type)

            hideSeenChangeListener?.onHideSeenChanged(jid, messageId, type, viewed)
            return true
        } catch (t: Throwable) {
            XposedBridge.log(t)
            return false
        }
    }

    fun updateViewedMessageAsync(
        jid: String?,
        messageId: String?,
        type: ReceiptType?,
        viewed: Boolean
    ) {
        Utils.databaseExecutor.execute {
            updateViewedMessage(jid, messageId, type, viewed)
        }
    }

    fun getHideSeenMessage(jid: String?, messageId: String?, type: ReceiptType?): MessageSeenItem? {
        try {
            if (jid == null || messageId == null || type == null) return null

            val cacheKey = createSeenMessageCacheKey(jid, messageId, type)
            val cachedItem = seenMessageCache.get(cacheKey)
            if (cachedItem != null) {
                return if (cachedItem === EMPTY_SEEN_ITEM) null else cachedItem
            }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                scheduleCacheLoad(cacheKey) { getHideSeenMessage(jid, messageId, type) }
                return null
            }

            val state = if (type == ReceiptType.PLAYED) {
                hideSeenDao.getPlayedState(jid, messageId)
            } else {
                hideSeenDao.getReadState(jid, messageId)
            }

            if (state != null) {
                val viewed = state == 1
                val message = MessageSeenItem(jid, messageId, viewed)
                seenMessageCache.put(cacheKey, message)
                return message
            } else {
                seenMessageCache.put(cacheKey, EMPTY_SEEN_ITEM)
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
        return null
    }

    fun getHideSeenMessages(
        jid: String?,
        type: ReceiptType?,
        viewed: Boolean
    ): List<MessageSeenItem>? {
        try {
            if (jid == null || type == null) return null

            val cacheKey = createSeenMessagesListCacheKey(jid, type, viewed)
            val cachedList = seenMessagesListCache.get(cacheKey)
            if (cachedList != null) {
                return cachedList
            }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                scheduleCacheLoad(cacheKey) { getHideSeenMessages(jid, type, viewed) }
                return null
            }

            val isViewedInt = if (viewed) 1 else 0
            val entities = if (type == ReceiptType.PLAYED) {
                hideSeenDao.getMessagesByPlayedState(jid, isViewedInt)
            } else {
                hideSeenDao.getMessagesByReadState(jid, isViewedInt)
            }

            if (entities.isNotEmpty()) {
                val messages = ArrayList<MessageSeenItem>()
                for (entity in entities) {
                    val message = MessageSeenItem(jid, entity.messageId, viewed)
                    messages.add(message)

                    // Alimenta também o cache individual
                    val msgCacheKey = createSeenMessageCacheKey(jid, entity.messageId, type)
                    seenMessageCache.put(msgCacheKey, message)
                }
                seenMessagesListCache.put(cacheKey, messages)
                return messages
            } else {
                seenMessagesListCache.put(cacheKey, emptyList())
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
        return null
    }

    fun clearCaches() {
        messagesCache.evictAll()
        seenMessageCache.evictAll()
        seenMessagesListCache.evictAll()
    }

    private fun scheduleCacheLoad(key: String, load: () -> Unit) {
        if (!loadingCacheKeys.add(key)) return
        Utils.databaseExecutor.execute {
            try {
                load()
            } finally {
                loadingCacheKeys.remove(key)
            }
        }
    }

    private fun createSeenMessageCacheKey(
        jid: String,
        messageId: String,
        type: ReceiptType
    ): String {
        return "${jid}_${messageId}_${type.ordinal}"
    }

    private fun createSeenMessagesListCacheKey(
        jid: String,
        type: ReceiptType,
        viewed: Boolean
    ): String {
        return "${jid}_${type.ordinal}_${if (viewed) "1" else "0"}"
    }

    private fun invalidateSeenMessagesListCache(jid: String, type: ReceiptType) {
        seenMessagesListCache.remove(createSeenMessagesListCacheKey(jid, type, true))
        seenMessagesListCache.remove(createSeenMessagesListCacheKey(jid, type, false))
    }
}
