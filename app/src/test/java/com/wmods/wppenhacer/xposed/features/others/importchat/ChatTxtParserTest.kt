package com.wmods.wppenhacer.xposed.features.others.importchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ChatTxtParserTest {

    private val utc = ZoneOffset.UTC

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).toInstant(utc).toEpochMilli()

    @Test
    fun androidSpanish24h_multilineSystemAndOmitted() {
        val txt = """
            12/05/2024, 10:32 - Los mensajes y las llamadas están cifrados de extremo a extremo.
            12/05/2024, 10:32 - Ana: Hola
            12/05/2024, 10:33 - Luis: Qué tal
            segunda línea
            12/05/2024, 10:34 - Ana: <Multimedia omitido>
            13/05/2024, 09:00 - Luis cambió el asunto a “Foo: Bar”
        """.trimIndent()

        val chat = ChatTxtParser.parse(txt, zone = utc)

        assertEquals(DateOrder.DAY_MONTH, chat.dateOrder)
        assertEquals(listOf("Ana", "Luis"), chat.speakers)
        assertEquals(2, chat.messages.size)
        assertEquals("Qué tal\nsegunda línea", chat.messages[1].text)
        assertEquals(ms(2024, 5, 12, 10, 32), chat.messages[0].timestamp)
        assertEquals(2, chat.systemMessages)
        assertEquals(1, chat.omittedMedia)
    }

    @Test
    fun android12hWithNarrowNbspAndSpanishMeridiem() {
        val txt = "1/5/24, 3:07 PM - Ana: tarde\n" +
                "2/5/24, 12:05 a. m. - Ana: medianoche\n" +
                "2/5/24, 12:30 p. m. - Ana: mediodía"

        val chat = ChatTxtParser.parse(txt, zone = utc)

        assertEquals(ms(2024, 5, 1, 15, 7), chat.messages[0].timestamp)
        assertEquals(ms(2024, 5, 2, 0, 5), chat.messages[1].timestamp)
        assertEquals(ms(2024, 5, 2, 12, 30), chat.messages[2].timestamp)
    }

    @Test
    fun iosBracketFormatWithAttachmentsAndSystemLines() {
        val lrm = "‎"
        val txt = "[12/05/24, 10:32:05] Ana: ${lrm}Los mensajes están cifrados.\n" +
                "[12/05/24, 10:33:10] Ana: Mira\n" +
                "[12/05/24, 10:34:00] Luis: ${lrm}<adjunto: 00000012-PHOTO-2024-05-12-10-34-00.jpg>\n" +
                "[12/05/24, 10:35:00] Luis: ${lrm}imagen omitida"
        val files = setOf("00000012-PHOTO-2024-05-12-10-34-00.jpg")

        val chat = ChatTxtParser.parse(txt, zone = utc, fileExists = { it in files })

        assertEquals(2, chat.messages.size)
        assertEquals("00000012-PHOTO-2024-05-12-10-34-00.jpg", chat.messages[1].attachmentName)
        assertEquals(ms(2024, 5, 12, 10, 33, 10), chat.messages[0].timestamp)
        // El aviso de cifrado y el "imagen omitida" (sin archivo) no se importan.
        assertEquals(2, chat.systemMessages + chat.omittedMedia)
        assertEquals(1, chat.attachmentCount)
    }

    @Test
    fun androidAttachmentWithCaption_onlyWhenFileExists() {
        val txt = "12/05/2024, 10:32 - Ana: IMG-20240512-WA0001.jpg (archivo adjunto)\n" +
                "Mira esto\n" +
                "12/05/2024, 10:33 - Ana: nota.txt (ver arriba)"

        val chat = ChatTxtParser.parse(txt, zone = utc, fileExists = { it == "IMG-20240512-WA0001.jpg" })

        assertEquals("IMG-20240512-WA0001.jpg", chat.messages[0].attachmentName)
        assertEquals("Mira esto", chat.messages[0].text)
        assertNull(chat.messages[1].attachmentName)
        assertEquals("nota.txt (ver arriba)", chat.messages[1].text)
    }

    @Test
    fun dateOrder_dayAbove12MeansDayFirst() {
        val chat = ChatTxtParser.parse(
            "03/04/2024, 10:00 - A: x\n25/04/2024, 10:00 - A: y", zone = utc
        )
        assertEquals(DateOrder.DAY_MONTH, chat.dateOrder)
        assertFalse(chat.dateOrderAmbiguous)
        assertEquals(ms(2024, 4, 3, 10, 0), chat.messages[0].timestamp)
    }

    @Test
    fun dateOrder_monthFirstDetectedFromSecondField() {
        val chat = ChatTxtParser.parse(
            "4/3/2024, 10:00 AM - A: x\n4/25/2024, 10:00 AM - A: y", zone = utc
        )
        assertEquals(DateOrder.MONTH_DAY, chat.dateOrder)
        assertEquals(ms(2024, 4, 3, 10, 0), chat.messages[0].timestamp)
    }

    @Test
    fun dateOrder_ambiguousResolvedByChronology() {
        // Con día/mes iría 05/01 -> 02/02 -> 03/02 (ok); con mes/día iría 5 ene -> 2 feb... ambos
        // crecen, pero 01/06 -> 02/03 solo es cronológico como mes/día... aquí fuerza mes/día.
        val txt = "01/06/2024, 10:00 - A: a\n02/03/2024, 10:00 - A: b\n03/04/2024, 10:00 - A: c"
        val chat = ChatTxtParser.parse(txt, zone = utc)
        // día/mes retrocede (1 jun -> 2 mar); mes/día avanza (6 ene -> 3 feb -> 4 mar).
        assertEquals(DateOrder.MONTH_DAY, chat.dateOrder)
    }

    @Test
    fun dateOrder_trulyAmbiguousFallsBackToDayMonthAndFlags() {
        val chat = ChatTxtParser.parse("01/02/2024, 10:00 - A: a", zone = utc)
        assertEquals(DateOrder.DAY_MONTH, chat.dateOrder)
        assertTrue(chat.dateOrderAmbiguous)
    }

    @Test
    fun explicitOrderOverridesDetection() {
        val chat = ChatTxtParser.parse(
            "01/02/2024, 10:00 - A: a", order = DateOrder.MONTH_DAY, zone = utc
        )
        assertEquals(ms(2024, 1, 2, 10, 0), chat.messages[0].timestamp)
    }

    @Test
    fun yearFirstFormat() {
        val chat = ChatTxtParser.parse("2024-05-12 10:32 - A: hola", zone = utc)
        assertEquals(DateOrder.YEAR_MONTH_DAY, chat.dateOrder)
        assertEquals(ms(2024, 5, 12, 10, 32), chat.messages[0].timestamp)
    }

    @Test
    fun sameMinuteMessagesKeepFileOrder() {
        val txt = "12/05/2024, 10:32 - A: uno\n12/05/2024, 10:32 - B: dos\n12/05/2024, 10:32 - A: tres"
        val ts = ChatTxtParser.parse(txt, zone = utc).messages.map { it.timestamp }
        assertEquals(ms(2024, 5, 12, 10, 32), ts[0])
        assertTrue(ts[0] < ts[1] && ts[1] < ts[2])
    }

    @Test
    fun arabicIndicDigitsInHeaderAreUnderstoodButBodyIsKept() {
        val txt = "١٢/٠٥/٢٠٢٤, ١٠:٣٢ - A: ١٢٣"
        val chat = ChatTxtParser.parse(txt, zone = utc)
        assertEquals(ms(2024, 5, 12, 10, 32), chat.messages[0].timestamp)
        assertEquals("١٢٣", chat.messages[0].text)
    }

    @Test
    fun bomAndLeadingMarksAreIgnored() {
        val txt = "﻿‎12/05/2024, 10:32 - A: hola"
        assertEquals(1, ChatTxtParser.parse(txt, zone = utc).messages.size)
    }

    @Test
    fun unknownContactTildeIsStripped() {
        val chat = ChatTxtParser.parse("12/05/2024, 10:32 - ~ Pepe: hola", zone = utc)
        assertEquals("Pepe", chat.messages[0].sender)
    }

    @Test
    fun colonInsideMessageDoesNotSplitAgain() {
        val chat = ChatTxtParser.parse("12/05/2024, 10:32 - Ana: ojo: esto: sí", zone = utc)
        assertEquals("Ana", chat.messages[0].sender)
        assertEquals("ojo: esto: sí", chat.messages[0].text)
    }

    @Test
    fun phoneDigits() {
        assertEquals("34600111222", ChatTxtParser.phoneDigits("+34 600 11 12 22"))
        assertNull(ChatTxtParser.phoneDigits("Ana"))
        assertNull(ChatTxtParser.phoneDigits("123"))
    }

    @Test
    fun emptyInputGivesEmptyChat() {
        val chat = ChatTxtParser.parse("", zone = utc)
        assertTrue(chat.messages.isEmpty())
    }
}
