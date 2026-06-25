package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.model.DlsiteWork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteWorkTest {
    @Test
    fun immutableUpdatesReturnNewWorkSnapshots() {
        val original = DlsiteWork("RJ432317", "Title", "", "")

        val withCover = original.withEnsuredCoverUrl()
        val downloaded = withCover.withCoverUri("file:///cover.jpg")
            .asDownloaded("playlist-1", "/work", 3)

        assertEquals("", original.coverUrl)
        assertFalse(original.isDownloaded())
        assertNotEquals(original, withCover)
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ433000/RJ432317_img_main.jpg",
            withCover.coverUrl,
        )
        assertTrue(downloaded.isDownloaded())
        assertEquals("file:///cover.jpg", downloaded.coverUri)
        assertEquals("playlist-1", downloaded.playlistId)
        assertEquals("/work", downloaded.localPath)
        assertEquals("已导入 3 首", downloaded.statusLabel())
    }

    @Test
    fun inferredCoverUrlKeepsEmptyWorkIdAndDlsiteBuckets() {
        assertEquals("", DlsiteWork.inferredCoverUrl(""))
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ433000/RJ432317_img_main.jpg",
            DlsiteWork.inferredCoverUrl("RJ432317"),
        )
    }

    @Test
    fun queuedAndDownloadingDoNotRefreshSortTimestamp() {
        val original = DlsiteWork(
            workId = "RJ432317",
            title = "Title",
            detailUrl = "",
            downloadUrl = "",
            updatedAt = 1234L,
        )

        assertEquals(1234L, original.asQueued().updatedAt)
        assertEquals(1234L, original.asDownloading().updatedAt)
    }
}
