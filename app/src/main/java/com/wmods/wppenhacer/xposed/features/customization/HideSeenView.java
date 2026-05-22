package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore;
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XSharedPreferences;


public class HideSeenView extends Feature {

    private static final int JID_CACHE_SIZE = 30;
    private static final long REFRESH_DEBOUNCE_MS = 80;
    private static final Object CACHE_LOCK = new Object();
    private static final android.util.LruCache<String, JidSeenCache> jidCache = new android.util.LruCache<>(JID_CACHE_SIZE) {
        @Override
        protected void entryRemoved(boolean evicted, String key, JidSeenCache oldValue, JidSeenCache newValue) {
            loadedReadStatus.remove(key);
            loadedPlayedStatus.remove(key);
        }
    };
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean refreshScheduled = new AtomicBoolean(false);
    private static final ExecutorService cacheExecutor = Executors.newFixedThreadPool(2);
    private static final ConcurrentHashMap<String, Boolean> loadingReadStatus = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> loadingPlayedStatus = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> loadedReadStatus = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> loadedPlayedStatus = new ConcurrentHashMap<>();

    public HideSeenView(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }


    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("hide_seen_view", false)) return;

        MessageHistoryStore.setHideSeenChangeListener(HideSeenView::handleHideSeenChanged);

        // Register listener
        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup, int position, View convertView) {
                if (fMessage.getKey().isFromMe) return;
                updateBubbleView(fMessage, viewGroup);
            }
        });
    }

    @SuppressLint("ResourceType")
    private static void updateBubbleView(FMessageWpp fmessage, View viewGroup) {
        var userJid = fmessage.getKey().remoteJid;
        var messageId = fmessage.getKey().messageID;
        if (userJid.isNull()) return;
        var jid = userJid.getPhoneRawString();
        ImageView view = viewGroup.findViewById(Utils.getID("view_once_control_icon", "id"));
        if (view != null) {
            var played = getCachedStatus(jid, messageId, MessageHistoryStore.ReceiptType.PLAYED);
            if (played == null) {
                ensureCacheLoaded(jid, MessageHistoryStore.ReceiptType.PLAYED);
                view.setColorFilter(null);
            } else {
                view.setColorFilter(played ? Color.GREEN : Color.RED);
            }
        }
        ViewGroup dateWrapper = viewGroup.findViewById(Utils.getID("date_wrapper", "id"));
        if (dateWrapper != null) {
            TextView status = dateWrapper.findViewWithTag("seen_view");
            if (status == null) {
                status = new TextView(viewGroup.getContext());
                status.setTag("seen_view");
                status.setTextSize(8);
                dateWrapper.addView(status);
            }
            var viewedMessage = getCachedStatus(jid, messageId, MessageHistoryStore.ReceiptType.READ);
            if (viewedMessage == null) {
                ensureCacheLoaded(jid, MessageHistoryStore.ReceiptType.READ);
                status.setVisibility(View.GONE);
            } else {
                status.setVisibility(View.VISIBLE);
                status.setText(viewedMessage ? "\uD83D\uDFE2" : "\uD83D\uDD34");
            }
        }
    }

    private static Boolean getCachedStatus(String jid, String messageId, MessageHistoryStore.ReceiptType type) {
        synchronized (CACHE_LOCK) {
            var cache = jidCache.get(jid);
            if (cache == null) return null;
            Map<String, Boolean> map = type == MessageHistoryStore.ReceiptType.READ ? cache.readStatus : cache.playedStatus;
            return map.get(messageId);
        }
    }

    private static void ensureCacheLoaded(String jid, MessageHistoryStore.ReceiptType type) {
        var loadingMap = type == MessageHistoryStore.ReceiptType.READ ? loadingReadStatus : loadingPlayedStatus;
        var loadedMap = type == MessageHistoryStore.ReceiptType.READ ? loadedReadStatus : loadedPlayedStatus;
        if (loadedMap.containsKey(jid)) return;
        if (loadingMap.putIfAbsent(jid, true) != null) return;
        cacheExecutor.execute(() -> {
            try {
                Map<String, Boolean> map = loadStatusMap(jid, type);
                synchronized (CACHE_LOCK) {
                    var cache = jidCache.get(jid);
                    if (cache == null) {
                        cache = new JidSeenCache();
                        jidCache.put(jid, cache);
                    }
                    if (type == MessageHistoryStore.ReceiptType.READ) {
                        cache.readStatus = map;
                    } else {
                        cache.playedStatus = map;
                    }
                }
                loadedMap.put(jid, true);
                requestRefresh();
            } finally {
                loadingMap.remove(jid);
            }
        });
    }

    private static Map<String, Boolean> loadStatusMap(String jid, MessageHistoryStore.ReceiptType type) {
        Map<String, Boolean> map = new HashMap<>();
        List<MessageHistoryStore.MessageSeenItem> viewed = MessageHistoryStore.getInstance().getHideSeenMessages(jid, type, true);
        if (viewed != null) {
            for (var item : viewed) {
                map.put(item.message, true);
            }
        }
        List<MessageHistoryStore.MessageSeenItem> notViewed = MessageHistoryStore.getInstance().getHideSeenMessages(jid, type, false);
        if (notViewed != null) {
            for (var item : notViewed) {
                map.put(item.message, false);
            }
        }
        return map;
    }

    private static void handleHideSeenChanged(String jid, String messageId, MessageHistoryStore.ReceiptType type, boolean viewed) {
        synchronized (CACHE_LOCK) {
            var cache = jidCache.get(jid);
            if (cache == null) {
                cache = new JidSeenCache();
                jidCache.put(jid, cache);
            }
            if (type == MessageHistoryStore.ReceiptType.READ) {
                cache.readStatus.put(messageId, viewed);
            } else {
                cache.playedStatus.put(messageId, viewed);
            }
        }
        requestRefresh();
    }

    private static void requestRefresh() {
        if (!refreshScheduled.compareAndSet(false, true)) return;
        mainHandler.postDelayed(() -> {
            refreshScheduled.set(false);
            ConversationItemListener.notifyDataSetChanged();
        }, REFRESH_DEBOUNCE_MS);
    }

    private static class JidSeenCache {
        Map<String, Boolean> readStatus = new HashMap<>();
        Map<String, Boolean> playedStatus = new HashMap<>();
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen View";
    }
}