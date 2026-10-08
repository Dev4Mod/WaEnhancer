package com.wmods.wppenhacer.xposed.features.others.importchat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ImportRulesTest {

    @Test
    fun kinds() {
        assertEquals(MediaKind.IMAGE, ImportRules.kindFor("IMG-20240512-WA0001.jpg"))
        assertEquals(MediaKind.STICKER, ImportRules.kindFor("STK-20240512-WA0001.webp"))
        assertEquals(MediaKind.IMAGE, ImportRules.kindFor("foto.webp"))
        assertEquals(MediaKind.VIDEO, ImportRules.kindFor("VID-20240512-WA0001.MP4"))
        assertEquals(MediaKind.AUDIO, ImportRules.kindFor("PTT-20240512-WA0001.opus"))
        assertEquals(MediaKind.DOCUMENT, ImportRules.kindFor("informe.pdf"))
        assertEquals(MediaKind.DOCUMENT, ImportRules.kindFor("sinextension"))
    }

    @Test
    fun mime() {
        assertEquals("image/jpeg", ImportRules.mimeFor("a.JPG"))
        assertEquals("application/pdf", ImportRules.mimeFor("a.pdf"))
        assertEquals("application/octet-stream", ImportRules.mimeFor("a.xyz"))
    }

    @Test
    fun relativePaths() {
        val ts = LocalDateTime.of(2024, 5, 12, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(
            "Media/WhatsApp Images/IMG-1.jpg",
            ImportRules.relativeMediaPath(MediaKind.IMAGE, "IMG-1.jpg", false, ts)
        )
        assertEquals(
            "Media/WhatsApp Documents/Sent/a.pdf",
            ImportRules.relativeMediaPath(MediaKind.DOCUMENT, "a.pdf", true, ts)
        )
        // 12 de mayo de 2024 es la semana ISO 19.
        assertEquals(
            "Media/WhatsApp Voice Notes/202419/PTT-1.opus",
            ImportRules.relativeMediaPath(MediaKind.AUDIO, "PTT-1.opus", false, ts)
        )
    }

    @Test
    fun safeFileNameBlocksPathTricks() {
        assertEquals("passwd", ImportRules.safeFileName("../../etc/passwd"))
        assertEquals("a.jpg", ImportRules.safeFileName("C:\\x\\a.jpg"))
        assertEquals("archivo", ImportRules.safeFileName(".."))
        assertEquals("a_b.jpg", ImportRules.safeFileName("a:b.jpg"))
    }

    @Test
    fun messageKeyIsStableAndSensitiveToContent() {
        val a = ImportRules.messageKey(5, 1000, true, "Ana", "hola", null)
        assertEquals(a, ImportRules.messageKey(5, 1000, true, "Ana", "hola", null))
        assertNotEquals(a, ImportRules.messageKey(5, 1000, true, "Ana", "hola!", null))
        assertNotEquals(a, ImportRules.messageKey(6, 1000, true, "Ana", "hola", null))
        assertTrue(a.startsWith("WAEI") && a.length == 32)
    }

    @Test
    fun sortIds_emptyChatContinuesAfterGlobalMax() {
        assertArrayEquals(longArrayOf(101, 102, 103), ImportRules.planSortIds(3, false, 1, 100))
        assertArrayEquals(longArrayOf(1, 2), ImportRules.planSortIds(2, false, null, null))
    }

    @Test
    fun sortIds_olderMessagesGoBelowEverythingAndSkipZero() {
        assertArrayEquals(longArrayOf(-3, -2, -1), ImportRules.planSortIds(3, true, 7, 100))
        // Una segunda importación queda por debajo de la primera.
        assertArrayEquals(longArrayOf(-5, -4), ImportRules.planSortIds(2, true, -3, 100))
    }
}
