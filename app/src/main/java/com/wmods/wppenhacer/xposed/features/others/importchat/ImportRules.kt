package com.wmods.wppenhacer.xposed.features.others.importchat

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.IsoFields
import java.util.Locale

/** Tipos de adjunto y su carpeta dentro de la carpeta de medios de WhatsApp. */
enum class MediaKind(val messageType: Int, val folder: String) {
    IMAGE(1, "WhatsApp Images"),
    AUDIO(2, "WhatsApp Audio"),
    VIDEO(3, "WhatsApp Video"),
    DOCUMENT(9, "WhatsApp Documents"),
    STICKER(20, "WhatsApp Stickers");

    /** True para los tipos de los que se genera miniatura. */
    val hasThumbnail: Boolean get() = this == IMAGE || this == VIDEO
}

object ImportRules {

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
    private val VIDEO_EXT = setOf("mp4", "3gp", "mov", "mkv", "avi", "webm", "gif")
    private val AUDIO_EXT = setOf("opus", "ogg", "oga", "m4a", "mp3", "aac", "amr", "wav", "flac")

    private val MIME = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "webp" to "image/webp", "heic" to "image/heic", "heif" to "image/heif",
        "bmp" to "image/bmp", "gif" to "image/gif",
        "mp4" to "video/mp4", "3gp" to "video/3gpp", "mov" to "video/quicktime",
        "mkv" to "video/x-matroska", "avi" to "video/x-msvideo", "webm" to "video/webm",
        "opus" to "audio/ogg; codecs=opus", "ogg" to "audio/ogg", "oga" to "audio/ogg",
        "m4a" to "audio/mp4", "mp3" to "audio/mpeg", "aac" to "audio/aac",
        "amr" to "audio/amr", "wav" to "audio/wav", "flac" to "audio/flac",
        "pdf" to "application/pdf", "txt" to "text/plain", "vcf" to "text/x-vcard",
        "zip" to "application/zip", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "apk" to "application/vnd.android.package-archive"
    )

    private fun extension(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    @JvmStatic
    fun kindFor(fileName: String): MediaKind {
        val ext = extension(fileName)
        val upper = fileName.uppercase(Locale.ROOT)
        return when {
            ext == "webp" && upper.startsWith("STK-") -> MediaKind.STICKER
            ext in IMAGE_EXT -> MediaKind.IMAGE
            ext in VIDEO_EXT -> MediaKind.VIDEO
            ext in AUDIO_EXT -> MediaKind.AUDIO
            else -> MediaKind.DOCUMENT
        }
    }

    @JvmStatic
    fun mimeFor(fileName: String): String =
        MIME[extension(fileName)] ?: "application/octet-stream"

    /**
     * Ruta relativa a la carpeta raíz de WhatsApp (la que va en message_media.file_path).
     * WhatsApp guarda lo enviado por el usuario en una subcarpeta "Sent".
     */
    @JvmStatic
    fun relativeMediaPath(kind: MediaKind, fileName: String, fromMe: Boolean, timestamp: Long): String {
        val safe = safeFileName(fileName)
        val sb = StringBuilder("Media/").append(kind.folder).append('/')
        if (kind == MediaKind.AUDIO && safe.uppercase(Locale.ROOT).startsWith("PTT-")) {
            // Las notas de voz van en carpetas por semana: "WhatsApp Voice Notes/202419/".
            sb.setLength(0)
            sb.append("Media/WhatsApp Voice Notes/").append(weekFolder(timestamp)).append('/')
        }
        if (fromMe) sb.append("Sent/")
        return sb.append(safe).toString()
    }

    private fun weekFolder(timestamp: Long): String {
        val d = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC)
        return "%04d%02d".format(Locale.ROOT, d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
    }

    /** Solo el nombre, sin rutas ni caracteres problemáticos (evita escribir fuera de la carpeta). */
    @JvmStatic
    fun safeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("""[\u0000-\u001f<>:"|?*]"""), "_")
            .trim()
        return if (base.isEmpty() || base == "." || base == "..") "archivo" else base
    }

    /**
     * Identificador estable del mensaje: importar dos veces el mismo archivo produce las mismas
     * claves, así que no se duplica nada.
     */
    @JvmStatic
    fun messageKey(chatRowId: Long, timestamp: Long, fromMe: Boolean, sender: String,
                   text: String, attachment: String?): String {
        val md = MessageDigest.getInstance("SHA-1")
        val raw = "$chatRowId|$timestamp|$fromMe|$sender|$text|${attachment ?: ""}"
        val hex = md.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(Locale.ROOT, it) }
        return "WAEI" + hex.take(28)
    }

    /**
     * Valores de sort_id para [count] mensajes, en orden cronológico.
     *
     * - Chat vacío: a continuación del mayor sort_id existente.
     * - Chat con mensajes (solo se importan los anteriores al primero): por debajo del menor
     *   sort_id existente, para que queden antes sin tocar los demás mensajes.
     */
    @JvmStatic
    fun planSortIds(count: Int, chatHasMessages: Boolean, globalMin: Long?, globalMax: Long?): LongArray {
        val ids = LongArray(count)
        if (count == 0) return ids
        if (!chatHasMessages) {
            val start = (globalMax ?: 0L) + 1
            for (i in 0 until count) ids[i] = start + i
        } else {
            // El último valor queda justo por debajo del menor existente, saltándose el 0:
            // WhatsApp lo usa como "ninguno" en varias columnas.
            val lowest = minOf(globalMin ?: 1L, 1L)
            val top = if (lowest >= 1L) -1L else lowest - 1
            val start = top - count + 1
            for (i in 0 until count) ids[i] = start + i
        }
        return ids
    }
}
