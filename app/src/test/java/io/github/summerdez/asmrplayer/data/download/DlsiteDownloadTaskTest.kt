package io.github.summerdez.asmrplayer.data.download

import io.github.summerdez.asmrplayer.data.remote.DlsiteJsonParser
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DlsiteDownloadTaskTest {
    @Test
    fun progressTrackerAggregatesMultipleFileProgress() {
        val first = contentFile("01.mp3", 100L)
        val second = contentFile("02.mp3", 300L)
        val option = DlsiteDownloadOption("main", "Main", listOf(first, second))
        val tracker = DlsiteDownloadTask.DownloadProgressTracker(listOf(option))

        tracker.onFileProgress(option, first, 100L, 100L)
        val progress = tracker.onFileProgress(option, second, 150L, 300L)

        assertEquals(250L, progress.bytesDownloaded)
        assertEquals(400L, progress.totalBytes)
    }

    @Test
    fun progressTrackerDoesNotPublishPercentWhenLengthIsUnknown() {
        val unknown = contentFile("unknown.mp3", 0L)
        val option = DlsiteDownloadOption("main", "Main", listOf(unknown))
        val tracker = DlsiteDownloadTask.DownloadProgressTracker(listOf(option))

        val progress = tracker.onFileProgress(option, unknown, 150L, 300L)

        assertEquals(150L, progress.bytesDownloaded)
        assertEquals(-1L, progress.totalBytes)
    }

    @Test
    fun contentResultsMapTrackIdsAfterSingleImport() {
        val root = File("/tmp/dlsite-import-test")
        val mainOne = File(root, "main/01.wav")
        val mainTwo = File(root, "main/02.wav")
        val bonusOne = File(root, "bonus/01.wav")
        val main = DlsiteDownloadTask.DownloadedContent(
            DlsiteDownloadOption("main", "Main"),
            File(root, "main"),
            listOf(mainOne, mainTwo),
        )
        val bonus = DlsiteDownloadTask.DownloadedContent(
            DlsiteDownloadOption("bonus", "Bonus"),
            File(root, "bonus"),
            listOf(bonusOne),
        )
        val trackIdsByPath = mapOf(
            mainOne.absolutePath to "track-main-1",
            mainTwo.absolutePath to "track-main-2",
            bonusOne.absolutePath to "track-bonus-1",
        )

        val results = DlsiteDownloadTask.contentResultsForImport(
            listOf(main, bonus),
            DlsiteDownloadTask.ImportResult(
                "playlist-1",
                listOf("track-main-1", "track-main-2", "track-bonus-1"),
                3,
                trackIdsByPath,
            ),
        )

        assertEquals(2, results.size)
        assertEquals("main", results[0].optionId)
        assertEquals(listOf("track-main-1", "track-main-2"), results[0].trackIds)
        assertEquals(2, results[0].trackCount)
        assertEquals("bonus", results[1].optionId)
        assertEquals(listOf("track-bonus-1"), results[1].trackIds)
        assertEquals(1, results[1].trackCount)
    }

    private fun contentFile(name: String, lengthBytes: Long): DlsiteJsonParser.ContentFile {
        return DlsiteJsonParser.ContentFile(
            displayPath = name,
            displayName = name,
            contentPath = "optimized/$name",
            subtitleContentPath = "",
            subtitleName = "$name.vtt",
            lengthBytes = lengthBytes,
        )
    }
}
