package com.wmods.wppenhacer.xposed.core.db

import android.content.Context
import android.util.LruCache
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.db.entity.HideSeenEntity
import com.wmods.wppenhacer.xposed.core.db.entity.MessageEntity
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge

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

    private val db: MessageHistoryDatabase = Room.databaseBuilder(
        context.applicationContext,
        MessageHistoryDatabase::class.java,
        "MessageHistory.db"
    )
        .allowMainThreadQueries()
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration(true)
        .build()

    private val messageDao = db.messageDao()
    private val hideSeenDao = db.hideSeenDao()

    companion object {
        private const val MESSAGE_CACHE_SIZE = 100
        private const val SEEN_MESSAGE_CACHE_SIZE = 200
        private const val SEEN_MESSAGES_LIST_CACHE_SIZE = 50

        private val EMPTY_SEEN_ITEM = MessageSeenItem("", "", false)
        private val EMPTY_MESSAGE_LIST = ArrayList<MessageItem>()

        @Volatile
        private var mInstance: MessageHistoryStore? = null

        @JvmStatic
        fun getInstance(): MessageHistoryStore {
            return mInstance ?: synchronized(this) {
                mInstance ?: MessageHistoryStore(Utils.getApplication()).also { mInstance = it }
            }
        }

    }

    fun insertMessage(id: Long, message: String, timestamp: Long) {
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

    fun getMessages(v: Long): ArrayList<MessageItem>? {
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

    fun insertHideSeenMessage(
        jid: String?,
        messageId: String?,
        type: ReceiptType?,
        viewed: Boolean
    ) {
        try {
            if (jid == null || messageId == null || type == null) return

            val isViewedInt = if (viewed) 1 else 0

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

            val cacheKey = createSeenMessageCacheKey(jid, messageId, type)
            seenMessageCache.remove(cacheKey)
            invalidateSeenMessagesListCache(jid, type)

        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun updateViewedMessage(
        jid: String?,
        messageId: String?,
        type: ReceiptType?,
        viewed: Boolean
    ): Boolean {
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

            return true
        } catch (t: Throwable) {
            XposedBridge.log(t)
            return false
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