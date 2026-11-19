package com.wmods.wppenhacer.xposed.features.media

import android.webkit.MimeTypeMap
import com.highcapable.yukihookapi.hook.log.YLog
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.utils.HKDF
import com.wmods.wppenhacer.xposed.utils.Utils
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object StatusMediaDownloader {

    private const val MEDIA_KEY_SIZE = 32
    private const val MAC_SIZE = 10
    private const val DERIVED_KEY_SIZE = 112
    private const val BUFFER_SIZE = 64 * 1024
    private const val CDN_HOST = "https://mmg.whatsapp.net"
    private const val USER_AGENT = "Chrome/117.0.5938.150"

    private val MEDIA_KEY_INFO = linkedMapOf(
        "image" to "WhatsApp Image Keys",
        "video" to "WhatsApp Video Keys",
        "audio" to "WhatsApp Audio Keys",
        "document" to "WhatsApp Document Keys"
    )
    private val DEFAULT_EXTENSIONS = mapOf("image" to "jpg", "video" to "mp4", "audio" to "ogg")
    private val MIME_REGEX = Regex("""^(image|video|audio)/[\w.+-]+.*""")

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().addHeader("User-Agent", USER_AGENT).build()
                )
            }
            .build()
    }

    class Transfer {
        @Volatile
        var isCancelled = false
            private set

        @Volatile
        internal var call: Call? = null

        fun cancel() {
            isCancelled = true
            call?.cancel()
        }

        internal fun throwIfCancelled() {
            if (isCancelled) throw CancellationException()
        }
    }

    interface Listener {
        fun onDownloadProgress(read: Long, total: Long)
        fun onDecrypting()
    }

    private class Source {
        val urls = linkedSetOf<String>()
        val directPaths = linkedSetOf<String>()
        val keys = mutableListOf<ByteArray>()
        var mimeType: String? = null

        val isComplete: Boolean
            get() = keys.isNotEmpty() && (urls.isNotEmpty() || directPaths.isNotEmpty())
    }

    private fun tempDir() = File(Utils.application.cacheDir, "status_downloads")

    fun isTemporary(file: File): Boolean = file.parentFile == tempDir()

    fun download(statusItem: StatusItemWpp, transfer: Transfer, listener: Listener): File? {
        val source = findSource(statusItem) ?: return null
        val dir = tempDir().apply { mkdirs() }
        val encrypted = File(dir, "${System.nanoTime()}.enc")
        try {
            for (url in source.urls + source.directPaths.map { CDN_HOST + it }) {
                transfer.throwIfCancelled()
                if (!fetch(url, encrypted, transfer, listener)) continue
                transfer.throwIfCancelled()
                listener.onDecrypting()
                decrypt(encrypted, source, dir)?.let { return it }
            }
            return null
        } finally {
            encrypted.delete()
        }
    }

    private fun findSource(statusItem: StatusItemWpp): Source? {
        val source = Source()
        statusItem.fStatus?.fstatus?.let { scan(it, source, nested = true) }
        if (!source.isComplete) {
            statusItem.fMessage?.getObject()?.let { scan(it, source, nested = true) }
        }
        return source.takeIf { it.isComplete }
    }

    private fun scan(target: Any, source: Source, nested: Boolean) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && !clazz.name.startsWith("java.") && !clazz.name.startsWith("android.")) {
            for (field in clazz.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                val value = runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull() ?: continue
                when {
                    value is String -> classify(value, source)
                    value is ByteArray -> {
                        val isNew = source.keys.none { it.contentEquals(value) }
                        if (value.size == MEDIA_KEY_SIZE && isNew) source.keys += value
                    }

                    nested && holdsMediaFile(value.javaClass) -> scan(value, source, nested = false)
                }
            }
            clazz = clazz.superclass
        }
    }

    private fun holdsMediaFile(clazz: Class<*>) =
        runCatching { clazz.declaredFields.any { it.type == File::class.java } }.getOrDefault(false)

    private fun classify(value: String, source: Source) {
        when {
            value.startsWith("https://") && value.contains(".whatsapp.net") -> source.urls += value
            value.startsWith("/v/") || value.startsWith("/o1/") -> source.directPaths += value
            source.mimeType == null && MIME_REGEX.matches(value) -> source.mimeType = value
        }
    }

    private fun fetch(
        url: String,
        target: File,
        transfer: Transfer,
        listener: Listener
    ): Boolean {
        return try {
            fetchUrl(url, target, transfer, listener)
        } catch (e: IOException) {
            transfer.throwIfCancelled()
            YLog.error("Status media download failed", e)
            false
        }
    }

    private fun fetchUrl(
        url: String,
        target: File,
        transfer: Transfer,
        listener: Listener
    ): Boolean {
        val call = client.newCall(Request.Builder().url(url).build())
        transfer.call = call
        transfer.throwIfCancelled()
        call.execute().use { response ->
            if (!response.isSuccessful) return false
            val total = response.body.contentLength()
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        listener.onDownloadProgress(read, total)
                    }
                }
            }
        }
        return target.length() > MAC_SIZE
    }

    private fun decrypt(encrypted: File, source: Source, dir: File): File? {
        val mimeType = source.mimeType
        val primaryType = mimeType?.substringBefore('/')
        val types = listOfNotNull(primaryType?.takeIf { it in MEDIA_KEY_INFO }) +
                MEDIA_KEY_INFO.keys.filter { it != primaryType }
        for (type in types) {
            val info = MEDIA_KEY_INFO.getValue(type).toByteArray()
            for (key in source.keys) {
                val derived = HKDF.createFor(3).deriveSecrets(key, info, DERIVED_KEY_SIZE) ?: continue
                if (!macMatches(encrypted, derived)) continue
                val output = File(dir, "${System.nanoTime()}.${extensionFor(mimeType, type)}")
                decryptTo(encrypted, output, derived)
                return output
            }
        }
        return null
    }

    private fun macMatches(encrypted: File, derived: ByteArray): Boolean {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(derived.copyOfRange(48, 80), "HmacSHA256"))
        mac.update(derived, 0, 16)
        val expected = ByteArray(MAC_SIZE)
        encrypted.inputStream().use { input ->
            forEachPayloadChunk(input, encrypted.length()) { buffer, count ->
                mac.update(buffer, 0, count)
            }
            if (input.read(expected) != MAC_SIZE) return false
        }
        return MessageDigest.isEqual(mac.doFinal().copyOf(MAC_SIZE), expected)
    }

    private fun decryptTo(encrypted: File, output: File, derived: ByteArray) {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(derived.copyOfRange(16, 48), "AES"),
            IvParameterSpec(derived.copyOfRange(0, 16))
        )
        encrypted.inputStream().use { input ->
            output.outputStream().use { out ->
                forEachPayloadChunk(input, encrypted.length()) { buffer, count ->
                    cipher.update(buffer, 0, count)?.let { out.write(it) }
                }
                out.write(cipher.doFinal())
            }
        }
    }

    private inline fun forEachPayloadChunk(
        input: InputStream,
        fileSize: Long,
        block: (ByteArray, Int) -> Unit
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = fileSize - MAC_SIZE
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (count <= 0) break
            block(buffer, count)
            remaining -= count
        }
    }

    private fun extensionFor(mimeType: String?, type: String): String {
        val cleanMime = mimeType?.substringBefore(';')?.trim()
        return cleanMime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: DEFAULT_EXTENSIONS[type]
            ?: "bin"
    }
}
