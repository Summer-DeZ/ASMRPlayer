package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.DlsiteDownloadPlanner
import io.github.summerdez.asmrplayer.domain.model.DlsiteContentFile
import io.github.summerdez.asmrplayer.domain.model.DlsiteZiptree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteDownloadPlannerTest {
    @Test
    fun buildsOptionsFromDirectoryTreeLeaves() {
        val bonus = contentFile("作品/附赠内容/自由谈话.wav")
        val options = DlsiteDownloadPlanner.optionsFor(
            ziptree(
                contentFile("作品/WAV/1.开场.wav"),
                contentFile("作品/mp3/1.开场.mp3"),
                contentFile("作品/WAV/无效果音/1.开场.wav"),
                contentFile("作品/mp3/无效果音/1.开场.mp3"),
                bonus,
            )
        )

        assertEquals(listOf("WAV", "mp3", "WAV/无效果音", "mp3/无效果音", "附赠内容"), options.map { it.id })
        assertEquals(listOf("WAV", "mp3", "WAV / 无效果音", "mp3 / 无效果音", "附赠内容"), options.map { it.title })
        assertEquals(listOf(1, 1, 1, 1, 1), options.map { it.audioFiles.size })
        assertEquals(bonus, options.last().audioFiles.single())
    }

    @Test
    fun stripsCommonDirectoryPrefixBeforeGrouping() {
        val options = DlsiteDownloadPlanner.optionsFor(
            ziptree(
                contentFile("作品/本編/mp3/01.mp3"),
                contentFile("作品/本編/WAV/01.wav"),
            )
        )

        assertEquals(listOf("mp3", "WAV"), options.map { it.id })
        assertEquals(listOf("mp3", "WAV"), options.map { it.title })
    }

    @Test
    fun regularNestedFoldersBecomeUserSelectableContent() {
        val options = DlsiteDownloadPlanner.optionsFor(
            ziptree(
                contentFile("作品/本編/01.mp3"),
                contentFile("作品/特典/01.mp3"),
            )
        )

        assertEquals(listOf("本編", "特典"), options.map { it.id })
        assertEquals(listOf("本編", "特典"), options.map { it.title })
        assertEquals(listOf(1, 1), options.map { it.audioFiles.size })
    }

    @Test
    fun rootAudioAndFoldersCanBothBeSelected() {
        val options = DlsiteDownloadPlanner.optionsFor(
            ziptree(
                contentFile("01.mp3"),
                contentFile("特典/bonus.mp3"),
            )
        )

        assertEquals(listOf("根目录音频", "特典"), options.map { it.title })
        assertEquals(listOf("__root_audio__", "特典"), options.map { it.id })
    }

    @Test
    fun rootAudioBelowCommonPrefixAndFoldersCanBothBeSelected() {
        val options = DlsiteDownloadPlanner.optionsFor(
            ziptree(
                contentFile("作品/01.mp3"),
                contentFile("作品/特典/bonus.mp3"),
            )
        )

        assertEquals(listOf("根目录音频", "特典"), options.map { it.title })
        assertEquals(listOf("__root_audio__", "特典"), options.map { it.id })
    }

    @Test
    fun optionIdsEscapeDownloadServiceSeparator() {
        val options = DlsiteDownloadPlanner.optionsFor(
            ziptree(
                contentFile("作品/A|B/01.mp3"),
                contentFile("作品/A%7CB/02.mp3"),
            )
        )

        assertEquals(listOf("A%7CB", "A%257CB"), options.map { it.id })
        assertTrue(options.none { it.id.contains("|") })
    }

    @Test
    fun emptyAudioFilesReturnNoOptions() {
        val options = DlsiteDownloadPlanner.optionsFor(ziptree())

        assertTrue(options.isEmpty())
    }

    private fun ziptree(vararg files: DlsiteContentFile): DlsiteZiptree {
        return DlsiteZiptree("RJ00000001", "revision", files.toList())
    }

    private fun contentFile(path: String): DlsiteContentFile {
        val name = path.substringAfterLast("/")
        return DlsiteContentFile(
            path,
            name,
            "optimized/$name",
            "",
            "$name.vtt",
        )
    }
}
