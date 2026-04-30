package com.wmods.wppenhacer.model;

import com.wmods.wppenhacer.xposed.features.general.PresenceStateTracker;

public class PresenceEvent {
    private final long id;
    private final String contactId;
    private final PresenceStateTracker.Status status;
    private final long timestamp;
    private final String contactName;

    public PresenceEvent(long id, String contactId, PresenceStateTracker.Status status, long timestamp, String contactName) {
        this.id = id;
        this.contactId = contactId;
        this.status = status;
        this.timestamp = timestamp;
        this.contactName = contactName;
    }

    public long getId() { return id; }
    public String getContactId() { return contactId; }
    public PresenceStateTracker.Status getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
    public String getContactName() { return contactName; }
}
