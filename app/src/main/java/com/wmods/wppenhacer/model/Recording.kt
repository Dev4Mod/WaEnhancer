package com.wmods.wppenhacer.model

import android.annotation.SuppressLint
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.regex.Pattern

/**
 * Model class representing a call recording with metadata.
 */
data class Recording(
    val file: File
) {
    var contactName: String = "Unknown"
        private set
    var duration: Long = 0
        private set
    val date: Long = file.lastModified()
    val size: Long = file.length()

    init {
        extractContactName()
        parseDuration()
    }

    private fun extractContactName() {
        val filename = file.name
        val matcher = PHONE_PATTERN.matcher(filename)
        if (matcher.matches() && matcher.groupCount() >= 1) {
            val extracted = matcher.group(1)
            contactName = if (!extracted.isNullOrEmpty()) extracted else "Unknown"
        } else {
            contactName = "Unknown"
        }
    }

    private fun parseDuration() {
        if (!file.exists() || file.length() == 0L) {
            duration = 0
            return
        }

        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = if (!timeStr.isNullOrEmpty()) {
                    timeStr.toLongOrNull() ?: 0L
                } else {
                    0L
                }
            }
        } catch (_: Exception) {
            duration = 0
        }
    }

    @SuppressLint("DefaultLocale")
    fun getFormattedDuration(): String {
        var seconds = duration / 1000
        var minutes = seconds / 60
        seconds %= 60

        if (minutes >= 60) {
            val hours = minutes / 60
            minutes %= 60
            return String.format("%d:%02d:%02d", hours, minutes, seconds)
        }
        return String.format("%d:%02d", minutes, seconds)
    }

    @SuppressLint("DefaultLocale")
    fun getFormattedSize(): String {
        if (size < 1024) return "$size B"
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0)
        return String.format("%.1f MB", size / (1024.0 * 1024.0))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Recording) return false
        return file == other.file
    }

    override fun hashCode(): Int {
        return file.hashCode()
    }

    companion object {
        private val PHONE_PATTERN = Pattern.compile("Call_([+\\w\\s]+)_\\d{8}_\\d{6}.(wav|m4a)")
    }
}
