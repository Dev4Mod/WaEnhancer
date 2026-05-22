package com.wmods.wppenhacer.model;

import android.annotation.SuppressLint;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;

/**
 * Model class representing a call recording with metadata.
 */
@Getter
public class Recording {

    private final File file;
    private String contactName;
    private long duration;
    private final long date;
    private final long size;

    private static final Pattern PHONE_PATTERN = Pattern.compile("Call_([+\\w\\s]+)_\\d{8}_\\d{6}.(wav|m4a)");

    public Recording(File file) {
        this.file = file;
        this.date = file.lastModified();
        this.size = file.length();
        extractContactName();
        parseDuration();
    }

    private void extractContactName() {
        String filename = file.getName();
        Matcher matcher = PHONE_PATTERN.matcher(filename);
        if (matcher.matches() && matcher.groupCount() >= 1) {
            String extracted = matcher.group(1);
            contactName = (extracted != null && !extracted.isEmpty()) ? extracted : "Unknown";
        } else {
            contactName = "Unknown";
        }
    }

    private void parseDuration() {
        if (!file.exists() || file.length() == 0) {
            duration = 0;
            return;
        }

        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            String timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (timeStr != null && !timeStr.isEmpty()) {
                duration = Long.parseLong(timeStr);
            } else {
                duration = 0;
            }
        } catch (Exception e) {
            duration = 0;
        }
    }

    @SuppressLint("DefaultLocale")
    public String getFormattedDuration() {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    @SuppressLint("DefaultLocale")
    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Recording recording = (Recording) o;
        return file.equals(recording.file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }
}