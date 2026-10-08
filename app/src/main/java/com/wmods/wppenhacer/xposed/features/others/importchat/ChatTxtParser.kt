package com.wmods.wppenhacer.xposed.features.others.importchat

import java.io.BufferedReader
import java.io.Reader
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId

/** Orden de los campos de fecha en el TXT exportado. */
enum class DateOrder { AUTO, DAY_MONTH, MONTH_DAY, YEAR_MONTH_DAY }

/**
 * Un mensaje de la exportación.
 *
 * @param timestamp milisegundos desde epoch. El TXT solo trae minutos (segundos en iOS), así que
 * se suma 1 ms por mensaje dentro del mismo instante para conservar el orden del archivo.
 * @param sender nombre tal como aparece en el TXT.
 * @param text texto del mensaje; en un adjunto, el pie de foto (puede estar vacío).
 * @param attachmentName nombre del archivo adjunto, si el mensaje lo tiene.
 */
data class ParsedMessage(
    val timestamp: Long,
    val sender: String,
    val text: String,
    val attachmentName: String? = null
)

class ParsedChat(
    val messages: List<ParsedMessage>,
    /** Remitentes distintos, en orden de aparición. */
    val speakers: List<String>,
    val dateOrder: DateOrder,
    /** Mensajes del sistema (cifrado, cambios de grupo...) que no se importan. */
    val systemMessages: Int,
    /** Marcadores de multimedia que WhatsApp no incluyó en la exportación; no se importan. */
    val omittedMedia: Int,
    /** True si el orden día/mes no se pudo deducir y se asumió día/mes. */
    val dateOrderAmbiguous: Boolean
) {
    val attachmentCount: Int get() = messages.count { it.attachmentName != null }
}

/**
 * Lee el TXT que genera "Exportar chat" de WhatsApp (formatos Android e iOS, cualquier idioma).
 * No depende de Android para poder probarse en la JVM.
 */
object ChatTxtParser {

    private const val DATE = """(\d{1,4})[./\-](\d{1,2})[./\-](\d{1,4})"""
    private const val TIME = """(\d{1,2})[:.](\d{2})(?:[:.](\d{2}))?"""
    private const val AMPM = """(?:\s?([AaPp])\.?\s?[Mm]\.?)?"""
    private const val REST_GROUP = 8

    // iOS: [12/05/24, 10:32:05] Nombre: texto
    private val HEADER_BRACKET = Regex("""^\[$DATE,?\s+$TIME$AMPM]\s?(.*)$""")

    // Android: 12/05/2024, 10:32 - Nombre: texto
    private val HEADER_DASH = Regex("""^$DATE,?\s+$TIME$AMPM\s[-–—]\s(.*)$""")

    // iOS: <attached: 00000012-PHOTO-2024-05-12-10-32-05.jpg> (la palabra depende del idioma)
    private val ATTACH_IOS = Regex("""^<[^:<>]{1,40}:\s*(.+?)>$""")

    // Android: IMG-20240512-WA0001.jpg (archivo adjunto)
    private val ATTACH_ANDROID = Regex("""^(.+?\.[A-Za-z0-9]{1,5})\s+\([^()]{3,60}\)$""")

    // <Media omitted>, <Multimedia omitido>...
    private val OMITTED = Regex("""^<[^<>:]{2,40}>$""")

    private const val INVISIBLE_CLASS = "‎‏‪-‮⁦-⁩﻿"
    private val INVISIBLE = Regex("[$INVISIBLE_CLASS]")
    private val LEADING_INVISIBLE = Regex("^[$INVISIBLE_CLASS]+")

    private class Entry(
        val a: Int, val b: Int, val c: Int,
        val hour: Int, val minute: Int, val second: Int,
        val ampm: Char?,
        var body: String,
        val aLen: Int, val cLen: Int
    )

    private sealed class Body {
        object System : Body()
        object Omitted : Body()
        class Content(val attachment: String?, val text: String) : Body()
    }

    /**
     * @param fileExists indica si un nombre de archivo existe en el ZIP; solo así una línea con
     * pinta de adjunto se trata como adjunto.
     */
    @JvmStatic
    @JvmOverloads
    fun parse(
        reader: Reader,
        order: DateOrder = DateOrder.AUTO,
        zone: ZoneId = ZoneId.systemDefault(),
        fileExists: (String) -> Boolean = { false }
    ): ParsedChat {
        val entries = readEntries(reader)
        val (resolved, ambiguous) = resolveOrder(entries, order, zone)

        val messages = ArrayList<ParsedMessage>(entries.size)
        val speakers = LinkedHashSet<String>()
        var system = 0
        var omitted = 0
        var prevRaw = Long.MIN_VALUE
        var prevAssigned = Long.MIN_VALUE

        for (e in entries) {
            val raw = toEpoch(e, resolved, zone) ?: continue

            val (sender, body) = splitSender(e.body)
            if (sender == null) {
                system++
                continue
            }
            when (val kind = classifyBody(body, fileExists)) {
                Body.System -> system++
                Body.Omitted -> omitted++
                is Body.Content -> {
                    // Solo los mensajes que se importan reparten el desplazamiento de 1 ms.
                    val assigned =
                        if (raw <= prevAssigned && raw == prevRaw) prevAssigned + 1 else raw
                    prevRaw = raw
                    prevAssigned = assigned
                    speakers += sender
                    messages += ParsedMessage(assigned, sender, kind.text, kind.attachment)
                }
            }
        }
        return ParsedChat(messages, speakers.toList(), resolved, system, omitted, ambiguous)
    }

    @JvmStatic
    @JvmOverloads
    fun parse(
        text: String,
        order: DateOrder = DateOrder.AUTO,
        zone: ZoneId = ZoneId.systemDefault(),
        fileExists: (String) -> Boolean = { false }
    ): ParsedChat = parse(text.reader(), order, zone, fileExists)

    /**
     * Convierte dígitos de cualquier escritura a ASCII y los espacios especiales a espacio normal.
     * Mantiene la longitud, de modo que las posiciones valen también para el texto original.
     */
    private fun normalize(line: String): String {
        val sb = StringBuilder(line.length)
        for (ch in line) {
            when {
                ch == ' ' || ch == ' ' || ch == ' ' -> sb.append(' ')
                ch in '0'..'9' -> sb.append(ch)
                Character.isDigit(ch) -> sb.append(('0' + Character.digit(ch, 10)))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun readEntries(reader: Reader): List<Entry> {
        val out = ArrayList<Entry>()
        var current: Entry? = null
        val br = if (reader is BufferedReader) reader else BufferedReader(reader)
        var first = true
        while (true) {
            var line = br.readLine() ?: break
            if (first) {
                line = line.removePrefix("﻿")
                first = false
            }
            val trimmed = line.replaceFirst(LEADING_INVISIBLE, "")
            val head = normalize(trimmed)
            val m = HEADER_BRACKET.matchEntire(head) ?: HEADER_DASH.matchEntire(head)
            if (m != null) {
                val g = m.groupValues
                // El cuerpo se toma del texto original: normalize() no cambia las longitudes.
                val rest = trimmed.substring(m.groups[REST_GROUP]!!.range.first)
                current = Entry(
                    g[1].toInt(), g[2].toInt(), g[3].toInt(),
                    g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt(),
                    g[7].firstOrNull()?.lowercaseChar(),
                    rest, g[1].length, g[3].length
                )
                out += current
            } else if (current != null) {
                current.body += "\n" + line
            }
        }
        return out
    }

    private fun resolveOrder(
        entries: List<Entry>, requested: DateOrder, zone: ZoneId
    ): Pair<DateOrder, Boolean> {
        if (requested != DateOrder.AUTO) return requested to false
        if (entries.isEmpty()) return DateOrder.DAY_MONTH to false
        if (entries.any { it.aLen >= 4 }) return DateOrder.YEAR_MONTH_DAY to false

        var dmyOk = true
        var mdyOk = true
        for (e in entries) {
            if (e.a > 31 || e.b > 12) dmyOk = false
            if (e.a > 12 || e.b > 31) mdyOk = false
        }
        if (dmyOk && !mdyOk) return DateOrder.DAY_MONTH to false
        if (mdyOk && !dmyOk) return DateOrder.MONTH_DAY to false
        if (!dmyOk) return DateOrder.DAY_MONTH to true

        // Ambos valen: gana el que haga la secuencia más cronológica.
        fun backwards(o: DateOrder): Int {
            var prev = Long.MIN_VALUE
            var n = 0
            for (e in entries) {
                val t = toEpoch(e, o, zone) ?: continue
                if (t < prev) n++
                prev = t
            }
            return n
        }

        val dm = backwards(DateOrder.DAY_MONTH)
        val md = backwards(DateOrder.MONTH_DAY)
        return when {
            dm < md -> DateOrder.DAY_MONTH to false
            md < dm -> DateOrder.MONTH_DAY to false
            else -> DateOrder.DAY_MONTH to true
        }
    }

    private fun toEpoch(e: Entry, order: DateOrder, zone: ZoneId): Long? {
        val year: Int
        val month: Int
        val day: Int
        when (order) {
            DateOrder.YEAR_MONTH_DAY -> {
                year = e.a; month = e.b; day = e.c
            }
            DateOrder.MONTH_DAY -> {
                month = e.a; day = e.b; year = fullYear(e.c, e.cLen)
            }
            else -> {
                day = e.a; month = e.b; year = fullYear(e.c, e.cLen)
            }
        }
        var hour = e.hour
        if (e.ampm != null) {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (e.ampm == 'p') 12 else 0
        }
        return try {
            LocalDateTime.of(year, month, day, hour, e.minute, e.second)
                .atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun fullYear(y: Int, len: Int): Int = when {
        len >= 4 -> y
        y < 70 -> 2000 + y
        else -> 1900 + y
    }

    /** Separa "Nombre: texto". Devuelve sender=null si parece un mensaje del sistema. */
    private fun splitSender(body: String): Pair<String?, String> {
        val idx = body.indexOf(": ")
        if (idx <= 0 || idx > 60) return null to body
        val name = body.substring(0, idx)
        // Los avisos del sistema con comillas ("cambió el asunto a “X: Y”") no son remitentes.
        if (name.any { it == '\n' || it == '"' || it == '“' || it == '”' || it == '«' || it == '»' }) {
            return null to body
        }
        val cleaned = name.replace(INVISIBLE, "").trimStart('~', ' ').trim()
        if (cleaned.isEmpty()) return null to body
        return cleaned to body.substring(idx + 2)
    }

    private fun classifyBody(body: String, fileExists: (String) -> Boolean): Body {
        // En iOS, los avisos del sistema y los marcadores de multimedia empiezan con una marca
        // invisible tras el nombre.
        val marked = body.isNotEmpty() && (body[0] == '‎' || body[0] == '‏')
        val clean = body.replace(INVISIBLE, "")
        val lines = clean.split('\n')
        val firstLine = lines.first().trim()

        val candidate = ATTACH_IOS.matchEntire(firstLine)?.groupValues?.get(1)?.trim()
            ?: ATTACH_ANDROID.matchEntire(firstLine)?.groupValues?.get(1)?.trim()
        if (candidate != null && fileExists(candidate)) {
            return Body.Content(candidate, lines.drop(1).joinToString("\n").trim())
        }
        if (OMITTED.matches(clean.trim())) return Body.Omitted
        if (marked) return if (firstLine.endsWith("omitted", true)) Body.Omitted else Body.System
        return Body.Content(null, clean.trimEnd())
    }

    /** Dígitos del remitente si parece un número de teléfono (7 a 15 dígitos); si no, null. */
    @JvmStatic
    fun phoneDigits(speaker: String): String? {
        val s = speaker.replace(Regex("""[\s+\-().]"""), "")
        return if (s.length in 7..15 && s.all { it in '0'..'9' }) s else null
    }
}
