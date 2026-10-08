package com.wmods.wppenhacer.xposed.features.others.importchat

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Archivo elegido por el usuario: el .txt suelto de "Exportar chat" o el .zip que WhatsApp genera
 * al exportar con archivos multimedia (un _chat.txt más los adjuntos).
 *
 * Todo se copia a [workDir] (una carpeta propia de esta importación) y se borra en [close].
 * Nunca se escribe en disco con nombres tomados del ZIP: solo se leen los flujos.
 */
class ChatImportSource private constructor(
    private val workDir: File,
    private val chatFile: File,
    private val zip: ZipFile?,
    private val media: Map<String, ZipEntry>
) : Closeable {

    private val mediaLower: Map<String, ZipEntry> =
        media.entries.associate { it.key.lowercase(Locale.ROOT) to it.value }

    val hasMedia: Boolean get() = media.isNotEmpty()
    val mediaCount: Int get() = media.size

    private fun entryFor(name: String): ZipEntry? =
        media[name] ?: mediaLower[name.lowercase(Locale.ROOT)]

    fun hasFile(name: String): Boolean = entryFor(name) != null

    fun parse(order: DateOrder = DateOrder.AUTO, zone: ZoneId = ZoneId.systemDefault()): ParsedChat =
        chatFile.bufferedReader(Charsets.UTF_8).use { reader ->
            ChatTxtParser.parse(reader, order, zone) { hasFile(it) }
        }

    /** Flujo del adjunto, o null si el ZIP no lo trae. Quien lo llama debe cerrarlo. */
    fun openMedia(name: String): InputStream? {
        val entry = entryFor(name) ?: return null
        return zip?.getInputStream(entry)
    }

    /** Tamaño sin comprimir del adjunto, o -1 si se desconoce. */
    fun mediaSize(name: String): Long = entryFor(name)?.size ?: -1L

    override fun close() {
        try {
            zip?.close()
        } catch (_: Exception) {
        }
        workDir.deleteRecursively()
    }

    class InvalidExportException(message: String) : Exception(message)

    companion object {
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

        /**
         * @param input contenido del archivo elegido.
         * @param workDir carpeta nueva y exclusiva para esta importación.
         */
        @JvmStatic
        fun open(input: InputStream, workDir: File): ChatImportSource {
            workDir.mkdirs()
            val raw = File(workDir, "source.bin")
            try {
                input.use { src -> raw.outputStream().use { dst -> src.copyTo(dst) } }
                if (!isZip(raw)) return ChatImportSource(workDir, raw, null, emptyMap())

                val zip = ZipFile(raw)
                try {
                    val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
                    val txt = pickChatEntry(entries)
                        ?: throw InvalidExportException("El ZIP no contiene ningún archivo .txt de chat")
                    val chatFile = File(workDir, "chat.txt")
                    zip.getInputStream(txt).use { src -> chatFile.outputStream().use { src.copyTo(it) } }

                    val media = LinkedHashMap<String, ZipEntry>()
                    for (e in entries) {
                        if (e === txt) continue
                        media.putIfAbsent(e.name.substringAfterLast('/'), e)
                    }
                    return ChatImportSource(workDir, chatFile, zip, media)
                } catch (e: Exception) {
                    zip.close()
                    throw e
                }
            } catch (e: Exception) {
                workDir.deleteRecursively()
                throw e
            }
        }

        private fun isZip(file: File): Boolean = file.inputStream().use { s ->
            val head = ByteArray(4)
            s.read(head) == 4 && head.contentEquals(ZIP_MAGIC)
        }

        /** _chat.txt (iOS) si existe; si no, el .txt más grande (Android: "Chat de WhatsApp con X.txt"). */
        private fun pickChatEntry(entries: List<ZipEntry>): ZipEntry? {
            val txts = entries.filter { it.name.endsWith(".txt", ignoreCase = true) }
            return txts.firstOrNull { it.name.substringAfterLast('/') == "_chat.txt" }
                ?: txts.maxByOrNull { it.size }
        }
    }
}
