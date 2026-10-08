package com.wmods.wppenhacer.xposed.features.others.importchat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.io.File

/** Datos que WhatsApp guarda de un adjunto (dimensiones, duración en segundos y miniatura JPEG). */
class MediaInfo(
    val width: Int = 0,
    val height: Int = 0,
    val durationSec: Int = 0,
    val thumbnail: ByteArray? = null
)

fun interface MediaProbe {
    fun probe(file: File, kind: MediaKind): MediaInfo
}

/** Implementación con las APIs de Android. Todo es opcional: si algo falla se devuelve lo que haya. */
object AndroidMediaProbe : MediaProbe {

    private const val THUMB_SIDE = 100

    override fun probe(file: File, kind: MediaKind): MediaInfo = when (kind) {
        MediaKind.IMAGE, MediaKind.STICKER -> image(file, kind == MediaKind.IMAGE)
        MediaKind.VIDEO -> video(file)
        MediaKind.AUDIO -> audio(file)
        MediaKind.DOCUMENT -> MediaInfo()
    }

    private fun image(file: File, withThumb: Boolean): MediaInfo {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val w = bounds.outWidth.coerceAtLeast(0)
        val h = bounds.outHeight.coerceAtLeast(0)
        if (!withThumb || w <= 0 || h <= 0) return MediaInfo(w, h)

        var sample = 1
        while (w / (sample * 2) >= THUMB_SIDE && h / (sample * 2) >= THUMB_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return MediaInfo(w, h)
        return MediaInfo(w, h, 0, toJpeg(bmp))
    }

    private fun video(file: File): MediaInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = try {
                r.getFrameAtTime(0)
            } catch (_: Exception) {
                null
            }
            return MediaInfo(w, h, (ms / 1000).toInt(), frame?.let { toJpeg(it) })
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun audio(file: File): MediaInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            return MediaInfo(0, 0, (ms / 1000).toInt())
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun toJpeg(src: Bitmap): ByteArray? {
        var scaled: Bitmap? = null
        try {
            val longest = maxOf(src.width, src.height)
            val scale = THUMB_SIDE.toFloat() / longest
            scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    src, (src.width * scale).toInt().coerceAtLeast(1),
                    (src.height * scale).toInt().coerceAtLeast(1), true
                )
            } else src
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
            return out.toByteArray()
        } catch (_: Exception) {
            return null
        } finally {
            if (scaled != null && scaled !== src) scaled.recycle()
            src.recycle()
        }
    }
}
