package com.wmods.wppenhacer.xposed.core.db;

import androidx.annotation.NonNull;

public class DeletedMessage {
    private long id;
    private String keyId;
    private String chatJid;
    private String senderJid;
    private long timestamp;
    private int mediaType;
    private String textContent;
    private String mediaPath;
    private String mediaCaption;

    public DeletedMessage(long id, String keyId, String chatJid, String senderJid, long timestamp, int mediaType, String textContent, String mediaPath, String mediaCaption) {
        this.id = id;
        this.keyId = keyId;
        this.chatJid = chatJid;
        this.senderJid = senderJid;
        this.timestamp = timestamp;
        this.mediaType = mediaType;
        this.textContent = textContent;
        this.mediaPath = mediaPath;
        this.mediaCaption = mediaCaption;
    }

    public DeletedMessage() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getChatJid() {
        return chatJid;
    }

    public void setChatJid(String chatJid) {
        this.chatJid = chatJid;
    }

    public String getSenderJid() {
        return senderJid;
    }

    public void setSenderJid(String senderJid) {
        this.senderJid = senderJid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getMediaType() {
        return mediaType;
    }

    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getMediaPath() {
        return mediaPath;
    }

    public void setMediaPath(String mediaPath) {
        this.mediaPath = mediaPath;
    }

    public String getMediaCaption() {
        return mediaCaption;
    }

    public void setMediaCaption(String mediaCaption) {
        this.mediaCaption = mediaCaption;
    }

    @NonNull
    @Override
    public String toString() {
        return "DeletedMessage{" +
                "id=" + id +
                ", keyId='" + keyId + '\'' +
                ", chatJid='" + chatJid + '\'' +
                ", senderJid='" + senderJid + '\'' +
                ", timestamp=" + timestamp +
                ", mediaType=" + mediaType +
                ", textContent='" + textContent + '\'' +
                ", mediaPath='" + mediaPath + '\'' +
                ", mediaCaption='" + mediaCaption + '\'' +
                '}';
    }
}
