package com.wmods.wppenhacer.xposed.features.general;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PresenceStateTracker {

    public enum Status {
        ONLINE,
        OFFLINE
    }

    public interface Clock {
        long now();
    }

    public interface EventSink {
        void insert(String contactId, Status status, long timestamp);
    }

    private final Clock clock;
    private final EventSink eventSink;
    private final long offlineTimeoutMs;
    private final Set<String> trackedContacts = new HashSet<>();
    private final Map<String, Status> states = new HashMap<>();
    private final Map<String, Long> lastOnlineAt = new HashMap<>();
    private boolean enabled;

    public PresenceStateTracker(@NonNull Clock clock, @NonNull EventSink eventSink, long offlineTimeoutMs) {
        this.clock = clock;
        this.eventSink = eventSink;
        this.offlineTimeoutMs = offlineTimeoutMs;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            states.clear();
            lastOnlineAt.clear();
        }
    }

    public synchronized void updateTrackedContacts(@Nullable String contactsValue) {
        trackedContacts.clear();
        if (contactsValue == null || contactsValue.length() < 2) {
            states.clear();
            lastOnlineAt.clear();
            return;
        }
        String body = contactsValue.substring(1, contactsValue.length() - 1);
        if (body.trim().isEmpty()) {
            states.clear();
            lastOnlineAt.clear();
            return;
        }
        for (String item : body.split(", ")) {
            String normalized = normalizeContactId(item);
            if (normalized != null) {
                trackedContacts.add(normalized);
            }
        }
        states.keySet().removeIf(contactId -> !trackedContacts.contains(contactId));
        lastOnlineAt.keySet().removeIf(contactId -> !trackedContacts.contains(contactId));
    }

    public synchronized void onOnline(@Nullable String rawJid) {
        if (!enabled) return;
        String contactId = normalizeContactId(rawJid);
        if (contactId == null || !trackedContacts.contains(contactId)) return;

        long now = clock.now();
        lastOnlineAt.put(contactId, now);
        if (states.get(contactId) == Status.ONLINE) return;

        states.put(contactId, Status.ONLINE);
        eventSink.insert(contactId, Status.ONLINE, now);
    }

    public synchronized void flushOfflineTimeouts() {
        if (!enabled) return;
        long now = clock.now();
        for (String contactId : new HashSet<>(lastOnlineAt.keySet())) {
            Long onlineAt = lastOnlineAt.get(contactId);
            if (onlineAt == null || now - onlineAt < offlineTimeoutMs) continue;
            if (states.get(contactId) != Status.ONLINE) continue;

            states.put(contactId, Status.OFFLINE);
            lastOnlineAt.remove(contactId);
            eventSink.insert(contactId, Status.OFFLINE, now);
        }
    }

    @Nullable
    public static String normalizeContactId(@Nullable String rawJid) {
        if (rawJid == null) return null;
        String value = rawJid.trim();
        if (value.isEmpty()) return null;
        if (value.startsWith("[") && value.endsWith("]") && value.length() > 1) {
            value = value.substring(1, value.length() - 1).trim();
        }
        int atIndex = value.indexOf('@');
        if (atIndex > 0) {
            String domain = value.substring(atIndex + 1);
            if (domain.equals("g.us") || domain.equals("broadcast") || domain.equals("newsletter") || domain.equals("status")) {
                return null;
            }
            value = value.substring(0, atIndex);
        }
        int dotIndex = value.indexOf('.');
        if (dotIndex > 0) {
            value = value.substring(0, dotIndex);
        }
        return value.isEmpty() ? null : value;
    }
}
