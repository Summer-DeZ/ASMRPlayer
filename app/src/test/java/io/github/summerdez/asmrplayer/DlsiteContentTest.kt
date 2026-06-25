package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteContentTest {
    @Test
    fun asDownloadedKeepsNonEmptyLocalPathAndTrackIds() {
        val downloaded = DlsiteContent("RJ432317", "default", "Default")
            .asDownloaded("/work/default", listOf("1", "2"), 2)

        assertTrue(downloaded.isDownloaded())
        assertEquals("/work/default", downloaded.localPath)
        assertEquals(listOf("1", "2"), downloaded.trackIdList())
        assertEquals(2, downloaded.trackCount)
    }
}
