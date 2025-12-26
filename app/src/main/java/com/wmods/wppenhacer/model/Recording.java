package com.wmods.wppenhacer.model;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model class representing a call recording with metadata.
 */
public class Recording {
    
    private final File file;
    private String phoneNumber;
    private String contactName;
    private long duration; // in milliseconds
    private final long date;
    private final long size;
    
    // Pattern to extract phone number from filename: Call_+1234567890_20261226_164651.wav
    private static final Pattern PHONE_PATTERN = Pattern.compile("Call_([+\\d]+)_\\d{8}_\\d{6}\\.wav");
    
    public Recording(File file, Context context) {
        this.file = file;
        this.date = file.lastModified();
        this.size = file.length();
        
        // Extract phone number from filename
        extractPhoneNumber();
        
        // Resolve contact name
        if (context != null && phoneNumber != null) {
            resolveContactName(context);
        }
        
        // Parse duration from WAV header
        parseDuration();
    }
    
    private void extractPhoneNumber() {
        String filename = file.getName();
        Matcher matcher = PHONE_PATTERN.matcher(filename);
        if (matcher.matches()) {
            phoneNumber = matcher.group(1);
        } else {
            // Fallback: try to find any phone number pattern
            Pattern fallbackPattern = Pattern.compile("([+]?\\d{10,15})");
            Matcher fallbackMatcher = fallbackPattern.matcher(filename);
            if (fallbackMatcher.find()) {
                phoneNumber = fallbackMatcher.group(1);
            }
        }
        
        // Default contact name to phone number
        contactName = phoneNumber != null ? phoneNumber : "Unknown";
    }
    
    private void resolveContactName(Context context) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return;
        
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            
            try (Cursor cursor = resolver.query(uri, 
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, 
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String name = cursor.getString(0);
                    if (name != null && !name.isEmpty()) {
                        contactName = name;
                    }
                }
            }
        } catch (Exception e) {
            // Keep phone number as name if lookup fails
        }
    }
    
    private void parseDuration() {
        if (!file.exists() || file.length() < 44) {
            duration = 0;
            return;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // Read WAV header
            byte[] header = new byte[44];
            raf.read(header);
            
            // Verify RIFF header
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') {
                duration = estimateDuration();
                return;
            }
            
            // Get sample rate (bytes 24-27, little endian)
            int sampleRate = (header[24] & 0xFF) | 
                            ((header[25] & 0xFF) << 8) |
                            ((header[26] & 0xFF) << 16) | 
                            ((header[27] & 0xFF) << 24);
            
            // Get byte rate (bytes 28-31, little endian)
            int byteRate = (header[28] & 0xFF) | 
                          ((header[29] & 0xFF) << 8) |
                          ((header[30] & 0xFF) << 16) | 
                          ((header[31] & 0xFF) << 24);
            
            // Get data size (bytes 40-43, little endian)
            long dataSize = (header[40] & 0xFF) | 
                           ((header[41] & 0xFF) << 8) |
                           ((header[42] & 0xFF) << 16) | 
                           ((long)(header[43] & 0xFF) << 24);
            
            if (byteRate > 0) {
                duration = (dataSize * 1000L) / byteRate;
            } else if (sampleRate > 0) {
                // Assume 16-bit mono
                duration = (dataSize * 1000L) / (sampleRate * 2);
            }
            
        } catch (Exception e) {
            duration = estimateDuration();
        }
    }
    
    private long estimateDuration() {
        // Estimate based on file size (assume 48kHz, 16-bit, mono = 96000 bytes/sec)
        return (file.length() - 44) * 1000L / 96000;
    }
    
    // Getters
    
    public File getFile() {
        return file;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public String getContactName() {
        return contactName;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public long getDate() {
        return date;
    }
    
    public long getSize() {
        return size;
    }
    
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
    
    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
    
    /**
     * Returns a grouping key for this recording (phone number or "Unknown")
     */
    public String getGroupKey() {
        return phoneNumber != null ? phoneNumber : "unknown";
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
