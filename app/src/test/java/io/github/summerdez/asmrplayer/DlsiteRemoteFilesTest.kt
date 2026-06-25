package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.remote.DlsiteRemoteFiles
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteRemoteFilesTest {
    @Test
    fun encodeAndSafeNameKeepEmptyStringSemantics() {
        assertEquals("", DlsiteRemoteFiles.encodePath(""))
        assertEquals("", DlsiteRemoteFiles.encodeQueryValue(""))
        assertEquals("download", DlsiteRemoteFiles.safeFileName(""))
    }

    @Test
    fun encodePathEncodesSegmentsWithoutEscapingSeparators() {
        assertEquals(
            "%E6%9C%AC%E7%B7%A8/01%20track%2Bbonus.mp3",
            DlsiteRemoteFiles.encodePath("本編/01 track+bonus.mp3"),
        )
        assertEquals("a%20b%2Bc%26d", DlsiteRemoteFiles.encodeQueryValue("a b+c&d"))
    }

    @Test
    fun safeFileNameReplacesUnsafeCharacters() {
        assertEquals("a_b_c_", DlsiteRemoteFiles.safeFileName("a/b:c?"))
        assertEquals("download", DlsiteRemoteFiles.safeFileName("."))
        assertEquals("download", DlsiteRemoteFiles.safeFileName(".."))
    }

    @Test
    fun sniffersReturnFalseForMissingFilesAndDirectories() {
        val directory = Files.createTempDirectory("dlsite-remote-files").toFile()
        try {
            val missing = File(directory, "missing.bin")

            assertFalse(DlsiteRemoteFiles.looksLikeHtml(missing))
            assertFalse(DlsiteRemoteFiles.looksLikeJson(missing))
            assertFalse(DlsiteRemoteFiles.looksLikeHtml(directory))
            assertFalse(DlsiteRemoteFiles.looksLikeJson(directory))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sniffersDetectHtmlAndJsonHeaders() {
        val directory = Files.createTempDirectory("dlsite-remote-files").toFile()
        try {
            val html = File(directory, "page.html")
            val json = File(directory, "body.json")
            html.writeText("  <!doctype html><html></html>")
            json.writeText("  {\"status\":\"ok\"}")

            assertTrue(DlsiteRemoteFiles.looksLikeHtml(html))
            assertTrue(DlsiteRemoteFiles.looksLikeJson(json))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun summarizeBodyCompactsWhitespaceAndTruncates() {
        assertEquals("", DlsiteRemoteFiles.summarizeBody(""))
        assertEquals("a b c", DlsiteRemoteFiles.summarizeBody(" a\n\tb   c "))
        assertEquals(160, DlsiteRemoteFiles.summarizeBody("x".repeat(200)).length)
    }
}
