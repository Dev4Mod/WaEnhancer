package com.wmods.wppenhacer.xposed.features.customization

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.utils.Utils
import android.content.SharedPreferences 
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val JID_CACHE_SIZE = 30
private const val REFRESH_DEBOUNCE_MS = 80L

private val CACHE_LOCK = Any()

private val jidCache = object : LruCache<String, JidSeenCache>(JID_CACHE_SIZE) {
    override fun entryRemoved(evicted: Boolean, key: String, oldValue: JidSeenCache?, newValue: JidSeenCache?) {
        loadedReadStatus.remove(key)
        loadedPlayedStatus.remove(key)
    }
}

private val mainHandler = Handler(Looper.getMainLooper())
private val refreshScheduled = AtomicBoolean(false)
private val cacheExecutor = Executors.newFixedThreadPool(2)
private val loadingReadStatus = ConcurrentHashMap<String, Boolean>()
private val loadingPlayedStatus = ConcurrentHashMap<String, Boolean>()
private val loadedReadStatus = ConcurrentHashMap<String, Boolean>()
private val loadedPlayedStatus = ConcurrentHashMap<String, Boolean>()

class HideSeenView(loader: ClassLoader, preferences:SharedPreferences) : Feature(loader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        if (!prefs.getBoolean("hide_seen_view", false)) return

        MessageHistoryStore.setHideSeenChangeListener(object : MessageHistoryStore.HideSeenChangeListener {
            override fun onHideSeenChanged(
                jid: String,
                messageId: String,
                type: MessageHistoryStore.ReceiptType,
                viewed: Boolean
            ) {
                handleHideSeenChanged(jid, messageId, type, viewed)
            }
        })

        ConversationItemListener.conversationListeners.add(object : ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(fMessage: FMessageWpp, view: ViewGroup, position: Int, convertView: View?) {
                if (fMessage.key.isFromMe) {
                    clearBubbleView(view)
                    return
                }
                updateBubbleView(fMessage, view)
            }
        })
    }

    override fun getPluginName(): String {
        return "Hide Seen View"
    }
}

private fun clearBubbleView(viewGroup: ViewGroup) {
    viewGroup.findViewById<ImageView>(Utils.getID("view_once_control_icon", "id"))
        ?.colorFilter = null
    viewGroup.findViewById<ViewGroup>(Utils.getID("date_wrapper", "id"))
        ?.findViewWithTag<TextView>("seen_view")
        ?.visibility = View.GONE
}

@SuppressLint("ResourceType")
private fun updateBubbleView(fmessage: FMessageWpp, viewGroup: ViewGroup) {
    val userJid = fmessage.key.remoteJid
    val messageId = fmessage.key.messageID
    if (userJid.isNull) return
    val jid = userJid.phoneRawString ?: return

    val view = viewGroup.findViewById<ImageView>(Utils.getID("view_once_control_icon", "id"))
    if (view != null) {
        val played = getCachedStatus(jid, messageId, MessageHistoryStore.ReceiptType.PLAYED)
        if (played == null) {
            ensureCacheLoaded(jid, MessageHistoryStore.ReceiptType.PLAYED)
            view.colorFilter = null
        } else {
            view.setColorFilter(if (played) Color.GREEN else Color.RED)
        }
    }

    val dateWrapper = viewGroup.findViewById<ViewGroup>(Utils.getID("date_wrapper", "id"))
    if (dateWrapper != null) {
        var status = dateWrapper.findViewWithTag("seen_view") as? TextView
        if (status == null) {
            status = TextView(viewGroup.context).apply {
                tag = "seen_view"
                textSize = 8f
                dateWrapper.addView(this)
            }
        }
        val viewedMessage = getCachedStatus(jid, messageId, MessageHistoryStore.ReceiptType.READ)
        if (viewedMessage == null) {
            ensureCacheLoaded(jid, MessageHistoryStore.ReceiptType.READ)
            status.visibility = View.GONE
        } else {
            status.visibility = View.VISIBLE
            status.text = if (viewedMessage) "\uD83D\uDFE2" else "\uD83D\uDD34"
        }
    }
}

private fun getCachedStatus(jid: String, messageId: String, type: MessageHistoryStore.ReceiptType): Boolean? {
    synchronized(CACHE_LOCK) {
        val cache = jidCache.get(jid) ?: return null
        val map = if (type == MessageHistoryStore.ReceiptType.READ) cache.readStatus else cache.playedStatus
        return map[messageId]
    }
}

private fun ensureCacheLoaded(jid: String, type: MessageHistoryStore.ReceiptType) {
    val loadingMap = if (type == MessageHistoryStore.ReceiptType.READ) loadingReadStatus else loadingPlayedStatus
    val loadedMap = if (type == MessageHistoryStore.ReceiptType.READ) loadedReadStatus else loadedPlayedStatus
    if (loadedMap.containsKey(jid)) return
    if (loadingMap.putIfAbsent(jid, true) != null) return
    cacheExecutor.execute {
        try {
            val map = loadStatusMap(jid, type)
            synchronized(CACHE_LOCK) {
                var cache = jidCache.get(jid)
                if (cache == null) {
                    cache = JidSeenCache()
                    jidCache.put(jid, cache)
                }
                if (type == MessageHistoryStore.ReceiptType.READ) {
                    cache.readStatus = map
                } else {
                    cache.playedStatus = map
                }
            }
            loadedMap[jid] = true
            requestRefresh()
        } finally {
            loadingMap.remove(jid)
        }
    }
}

private fun loadStatusMap(jid: String, type: MessageHistoryStore.ReceiptType): HashMap<String, Boolean> {
    val map = HashMap<String, Boolean>()
    val viewed = MessageHistoryStore.getInstance().getHideSeenMessages(jid, type, true)
    if (viewed != null) {
        for (item in viewed) {
            map[item.message] = true
        }
    }
    val notViewed = MessageHistoryStore.getInstance().getHideSeenMessages(jid, type, false)
    if (notViewed != null) {
        for (item in notViewed) {
            map[item.message] = false
        }
    }
    return map
}

private fun handleHideSeenChanged(jid: String, messageId: String, type: MessageHistoryStore.ReceiptType, viewed: Boolean) {
    synchronized(CACHE_LOCK) {
        var cache = jidCache.get(jid)
        if (cache == null) {
            cache = JidSeenCache()
            jidCache.put(jid, cache)
        }
        if (type == MessageHistoryStore.ReceiptType.READ) {
            cache.readStatus[messageId] = viewed
        } else {
            cache.playedStatus[messageId] = viewed
        }
    }
    requestRefresh()
}

private fun requestRefresh() {
    if (!refreshScheduled.compareAndSet(false, true)) return
    mainHandler.postDelayed({
        refreshScheduled.set(false)
        ConversationItemListener.notifyDataSetChanged()
    }, REFRESH_DEBOUNCE_MS)
}

private class JidSeenCache {
    var readStatus: HashMap<String, Boolean> = HashMap()
    var playedStatus: HashMap<String, Boolean> = HashMap()
}
