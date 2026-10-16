package com.wmods.wppenhacer.toolkit.export

import android.database.sqlite.SQLiteDatabase
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exporta un chat de msgstore.db a TXT (formato de WhatsApp), HTML o CSV, con adjuntos opcionales. */
class ChatExporter(private val dbFile: File, private val mediaRoot: File) {

    enum class Format(val extension: String) { TXT("txt"), HTML("html"), CSV("csv") }

    /** Textos que aparecen en el archivo exportado (el idioma lo decide quien llama). */
    class Labels(
        val me: String = "Me",
        val deleted: String = "This message was deleted",
        /** Con %1$s = texto recuperado del mensaje borrado. */
        val deletedWithText: String = "%1\$s (deleted message)",
        val mediaOmitted: String = "<Media omitted>",
        val fileAttached: String = "file attached"
    )

    class Message(
        val rowId: Long,
        val timestamp: Long,
        val fromMe: Boolean,
        val senderJid: String?,
        val text: String?,
        val mediaPath: String?,
        val mime: String?,
        val deleted: Boolean
    )

    class Stats(val messages: Int, val attachments: Int, val attachmentsMissing: Int)

    fun interface Progress {
        fun update(done: Int, total: Int)
    }

    companion object {
        /** TXT y HTML con adjuntos van en un ZIP; el resto, en un archivo suelto. */
        fun isZip(format: Format, includeMedia: Boolean) = includeMedia && format != Format.CSV

        fun suggestedName(title: String, format: Format, includeMedia: Boolean): String {
            val base = title.replace(Regex("""[\\/:*?"<>|\n\r]+"""), "_").trim().ifEmpty { "chat" }.take(80)
            return "$base.${if (isZip(format, includeMedia)) "zip" else format.extension}"
        }
    }

    // ------------------------------------------------------------------ lectura

    private fun open(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun columns(db: SQLiteDatabase, table: String): Set<String> = try {
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val out = HashSet<String>()
            while (c.moveToNext()) out += c.getString(1)
            out
        }
    } catch (_: Exception) {
        emptySet()
    }

    private fun query(db: SQLiteDatabase, chatRowId: Long): String {
        val msg = columns(db, "message")
        val media = columns(db, "message_media")
        val hasMedia = "message_row_id" in media && "file_path" in media
        val hasRevoked = columns(db, "message_revoked").isNotEmpty()
        val hasLog = columns(db, "waimport_log").isNotEmpty()
        val text = if ("text_data" in msg) "m.text_data" else "NULL"
        val caption = if (hasMedia && "media_caption" in media) "mm.media_caption" else "NULL"
        val mime = if (hasMedia && "mime_type" in media) "mm.mime_type" else "NULL"
        val path = if (hasMedia) "mm.file_path" else "NULL"
        val join = if (hasMedia) "LEFT JOIN message_media mm ON mm.message_row_id = m._id" else ""
        val revokedExpr = buildString {
            append("(m.message_type IN (15, 64)")
            if (hasRevoked) append(" OR EXISTS (SELECT 1 FROM message_revoked r WHERE r.message_row_id = m._id)")
            append(")")
        }
        val logText = if (hasLog) "(SELECT l.text FROM waimport_log l WHERE l.message_row_id = m._id)" else "NULL"
        return "SELECT m._id, m.timestamp, m.from_me, sj.raw_string, $text, $caption, $path, $mime, " +
                "$revokedExpr, $logText FROM message m $join " +
                "LEFT JOIN jid sj ON sj._id = m.sender_jid_row_id " +
                "WHERE m.chat_row_id = $chatRowId ORDER BY m.timestamp, m._id"
    }

    private fun forEachMessage(db: SQLiteDatabase, chatRowId: Long, block: (Message) -> Unit) {
        db.rawQuery(query(db, chatRowId), null).use { c ->
            while (c.moveToNext()) {
                val deleted = c.getInt(8) != 0
                val mediaPath = c.getString(6)?.takeIf { it.isNotBlank() }
                val body = (c.getString(5)?.takeIf { it.isNotEmpty() } ?: c.getString(4))
                val text = if (deleted) c.getString(9)?.takeIf { it.isNotEmpty() } else body
                // Mensajes de sistema u otros sin contenido: no se exportan.
                if (!deleted && mediaPath == null && text.isNullOrEmpty()) continue
                block(
                    Message(
                        c.getLong(0), c.getLong(1), c.getInt(2) != 0, c.getString(3),
                        text, if (deleted) null else mediaPath, c.getString(7), deleted
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------------ exportación

    /**
     * Escribe el chat en [out]. [senderName] devuelve el nombre de un participante a partir de su
     * JID; en chats individuales (sin remitente) se usa [chatJid]. Bloqueante.
     */
    fun export(
        chatRowId: Long,
        chatJid: String,
        title: String,
        format: Format,
        includeMedia: Boolean,
        out: OutputStream,
        labels: Labels,
        senderName: (String?) -> String,
        isCancelled: () -> Boolean = { false },
        progress: Progress = Progress { _, _ -> }
    ): Stats {
        val zip = isZip(format, includeMedia)
        val buffered = BufferedOutputStream(out, 1 shl 16)
        val zos = if (zip) ZipOutputStream(buffered) else null
        val main: OutputStream = if (zos != null) {
            zos.putNextEntry(ZipEntry(if (format == Format.HTML) "chat.html" else "chat.txt"))
            zos
        } else buffered

        val w = main.bufferedWriter(Charsets.UTF_8)
        val usedNames = HashSet<String>()
        var count = 0
        var media = 0
        var missing = 0
        val mediaEntries = ArrayList<Pair<String, File>>()

        open().use { db ->
            val total = db.rawQuery("SELECT COUNT(*) FROM message WHERE chat_row_id = $chatRowId", null)
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val writer: Writer = when (format) {
                Format.TXT -> TxtWriter(w, labels)
                Format.HTML -> HtmlWriter(w, labels, title)
                Format.CSV -> CsvWriter(w)
            }
            writer.begin()
            var seen = 0
            forEachMessage(db, chatRowId) { m ->
                if (isCancelled()) throw java.util.concurrent.CancellationException()
                val sender = if (m.fromMe) labels.me else senderName(m.senderJid ?: chatJid)
                var shown: String? = null
                var present = false
                if (m.mediaPath != null) {
                    val f = File(mediaRoot, m.mediaPath)
                    if (f.isFile) {
                        present = true
                        var name = f.name
                        if (!usedNames.add(name)) name = "${m.rowId}-$name".also { usedNames.add(it) }
                        shown = name
                        if (includeMedia && zip) mediaEntries += name to f
                        media++
                    } else {
                        missing++
                        shown = f.name
                    }
                }
                writer.write(m, sender, shown, present && includeMedia && zip)
                count++
                seen++
                if (seen % 200 == 0) progress.update(seen, total)
            }
            writer.end()
            progress.update(total, total)
        }
        w.flush()

        if (zos != null) {
            zos.closeEntry()
            val dir = if (format == Format.HTML) "media/" else ""
            mediaEntries.forEachIndexed { i, (name, file) ->
                if (isCancelled()) throw java.util.concurrent.CancellationException()
                zos.putNextEntry(ZipEntry(dir + name).apply { time = file.lastModified() })
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
                if (i % 20 == 0) progress.update(i, mediaEntries.size)
            }
            zos.finish()
        }
        buffered.flush()
        return Stats(count, media, missing)
    }

    // ------------------------------------------------------------------ escritores

    private interface Writer {
        fun begin()
        /** [file]: nombre del adjunto (null si no tiene); [inArchive]: si va dentro del ZIP. */
        fun write(m: Message, sender: String, file: String?, inArchive: Boolean)
        fun end()
    }

    private class TxtWriter(private val w: java.io.Writer, private val l: Labels) : Writer {
        private val df = SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale.ROOT)
        override fun begin() {}
        override fun write(m: Message, sender: String, file: String?, inArchive: Boolean) {
            val head = "${df.format(Date(m.timestamp))} - $sender: "
            val body = when {
                m.deleted -> if (m.text != null) l.deletedWithText.format(m.text) else l.deleted
                file != null ->
                    (if (inArchive) "$file (${l.fileAttached})" else l.mediaOmitted) +
                            (if (m.text.isNullOrEmpty()) "" else "\n${m.text}")
                else -> m.text ?: ""
            }
            w.write(head); w.write(body); w.write("\n")
        }
        override fun end() {}
    }

    private class CsvWriter(private val w: java.io.Writer) : Writer {
        private val d = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        private val t = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
        private fun q(s: String?): String {
            val v = s ?: ""
            return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        }
        override fun begin() {
            w.write("﻿date,time,sender,direction,text,attachment,deleted\r\n")
        }
        override fun write(m: Message, sender: String, file: String?, inArchive: Boolean) {
            val ts = Date(m.timestamp)
            w.write(
                listOf(
                    d.format(ts), t.format(ts), q(sender), if (m.fromMe) "out" else "in",
                    q(m.text), q(file), if (m.deleted) "yes" else "no"
                ).joinToString(",") + "\r\n"
            )
        }
        override fun end() {}
    }

    private class HtmlWriter(private val w: java.io.Writer, private val l: Labels, private val title: String) : Writer {
        private val day = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        private val hour = SimpleDateFormat("HH:mm", Locale.ROOT)
        private var lastDay = ""

        private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("\n", "<br>")

        override fun begin() {
            w.write(
                """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title><style>
body{font-family:system-ui,sans-serif;background:#ece5dd;margin:0;color:#111}
header{background:#075e54;color:#fff;padding:14px 18px;font-size:18px;position:sticky;top:0}
main{max-width:760px;margin:0 auto;padding:12px}
.day{text-align:center;margin:14px 0 6px}.day span{background:#e1f3fb;border-radius:8px;padding:4px 10px;font-size:12px}
.m{max-width:80%;margin:4px 0;padding:6px 9px;border-radius:9px;background:#fff;clear:both;word-wrap:break-word}
.m.out{background:#dcf8c6;margin-left:auto}.m .n{font-size:12px;font-weight:600;color:#06795e}
.m .t{font-size:11px;color:#667;text-align:right;margin-top:2px}.m.del{opacity:.75;font-style:italic}
img,video{max-width:100%;border-radius:6px;display:block;margin:4px 0}audio{width:100%}
</style></head><body><header>${esc(title)}</header><main>
"""
            )
        }

        override fun write(m: Message, sender: String, file: String?, inArchive: Boolean) {
            val ts = Date(m.timestamp)
            val d = day.format(ts)
            if (d != lastDay) {
                lastDay = d
                w.write("<div class=\"day\"><span>${esc(d)}</span></div>\n")
            }
            w.write("<div class=\"m${if (m.fromMe) " out" else ""}${if (m.deleted) " del" else ""}\">")
            if (!m.fromMe) w.write("<div class=\"n\">${esc(sender)}</div>")
            if (file != null) {
                val src = "media/" + java.net.URLEncoder.encode(file, "UTF-8").replace("+", "%20")
                val mime = m.mime ?: ""
                when {
                    !inArchive -> w.write("<div>📎 ${esc(file)}</div>")
                    mime.startsWith("image/") -> w.write("<img src=\"$src\" loading=\"lazy\">")
                    mime.startsWith("video/") -> w.write("<video src=\"$src\" controls preload=\"none\"></video>")
                    mime.startsWith("audio/") -> w.write("<audio src=\"$src\" controls preload=\"none\"></audio>")
                    else -> w.write("<a href=\"$src\">📎 ${esc(file)}</a>")
                }
            }
            val text = if (m.deleted) {
                if (m.text != null) l.deletedWithText.format(m.text) else l.deleted
            } else m.text
            if (!text.isNullOrEmpty()) w.write("<div>${esc(text)}</div>")
            w.write("<div class=\"t\">${hour.format(ts)}</div></div>\n")
        }

        override fun end() {
            w.write("</main></body></html>\n")
        }
    }
}
