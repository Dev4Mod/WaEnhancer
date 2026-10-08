package com.wmods.wppenhacer.xposed.features.others.importchat

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ChatImportSourceTest {

    private val dirs = ArrayList<File>()

    private fun workDir(): File = Files.createTempDirectory("waeimp").toFile().also {
        it.delete() // la clase la crea ella
        dirs += it
    }

    @After
    fun cleanup() = dirs.forEach { it.deleteRecursively() }

    private fun zipOf(vararg files: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, data) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun plainTxt_hasNoMedia() {
        val txt = "12/05/2024, 10:32 - Ana: hola".toByteArray()
        ChatImportSource.open(txt.inputStream(), workDir()).use { src ->
            assertFalse(src.hasMedia)
            assertEquals(1, src.parse(zone = ZoneOffset.UTC).messages.size)
        }
    }

    @Test
    fun zip_attachmentsAreRecognisedAndReadable_caseInsensitively() {
        val img = byteArrayOf(1, 2, 3, 4, 5)
        val txt = ("12/05/2024, 10:32 - Ana: IMG-20240512-WA0001.jpg (archivo adjunto)\n" +
                "Mira\n12/05/2024, 10:33 - Luis: nota.txt (ver)").toByteArray()
        val data = zipOf(
            "Chat de WhatsApp con Ana.txt" to txt,
            "IMG-20240512-WA0001.jpg" to img
        )
        ChatImportSource.open(data.inputStream(), workDir()).use { src ->
            assertTrue(src.hasMedia)
            assertEquals(1, src.mediaCount)
            val chat = src.parse(zone = ZoneOffset.UTC)
            assertEquals("IMG-20240512-WA0001.jpg", chat.messages[0].attachmentName)
            assertEquals("Mira", chat.messages[0].text)
            assertNull(chat.messages[1].attachmentName)
            assertArrayEquals(img, src.openMedia("img-20240512-wa0001.JPG")!!.use { it.readBytes() })
            assertNull(src.openMedia("no-existe.jpg"))
        }
    }

    @Test
    fun zip_prefersUnderscoreChatTxt_andKeepsOtherTxtAsMedia() {
        val data = zipOf(
            "_chat.txt" to "[12/05/24, 10:32:05] Ana: hola".toByteArray(),
            "notas.txt" to ByteArray(2000) { 'x'.code.toByte() }
        )
        ChatImportSource.open(data.inputStream(), workDir()).use { src ->
            assertEquals(1, src.parse(zone = ZoneOffset.UTC).messages.size)
            assertTrue(src.hasFile("notas.txt"))
        }
    }

    @Test
    fun zip_entriesInFoldersAreFoundByBaseName() {
        val data = zipOf(
            "export/_chat.txt" to "12/05/2024, 10:32 - A: foto.jpg (adjunto)".toByteArray(),
            "export/Media/foto.jpg" to byteArrayOf(9)
        )
        ChatImportSource.open(data.inputStream(), workDir()).use { src ->
            assertNotNull(src.parse(zone = ZoneOffset.UTC).messages[0].attachmentName)
        }
    }

    @Test
    fun zipWithoutTxt_isRejected_andWorkDirRemoved() {
        val dir = workDir()
        try {
            ChatImportSource.open(zipOf("a.jpg" to byteArrayOf(1)).inputStream(), dir)
            fail("debía fallar")
        } catch (_: ChatImportSource.InvalidExportException) {
        }
        assertFalse(dir.exists())
    }

    @Test
    fun close_removesWorkDir() {
        val dir = workDir()
        ChatImportSource.open("12/05/2024, 10:32 - A: x".toByteArray().inputStream(), dir).close()
        assertFalse(dir.exists())
    }
}
