package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteDownloadPlannerTest {
    @Test
    fun detectsNestedFormatOptionsAndKeepsSharedBonus() {
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

        assertEquals(listOf("wav", "mp3", "wav/无效果音", "mp3/无效果音"), options.map { it.id })
        assertEquals(listOf("WAV", "MP3", "WAV / 无效果音", "MP3 / 无效果音"), options.map { it.title })
        assertTrue(options.all { option -> option.audioFiles.contains(bonus) })
        assertTrue(options.all { option -> option.audioFiles.size == 2 })
    }

    @Test
    fun detectsFormatMarkerBelowSharedFolder() {
        val options = DlsiteDownloadPlanner.optionsFor(
            ziptree(
                contentFile("作品/本編/mp3/01.mp3"),
                contentFile("作品/本編/WAV/01.wav"),
            )
        )

        assertEquals(listOf("mp3", "wav"), options.map { it.id })
    }

    @Test
    fun doesNotTreatRegularNestedFoldersAsVersions() {
        val options = DlsiteDownloadPlanner.optionsFor(
            ziptree(
                contentFile("作品/本編/01.mp3"),
                contentFile("作品/特典/01.mp3"),
            )
        )

        assertEquals(1, options.size)
        assertEquals("", options[0].id)
        assertEquals(2, options[0].audioFiles.size)
    }

    private fun ziptree(vararg files: DlsiteJsonParser.ContentFile): DlsiteJsonParser.DlsiteZiptree {
        return DlsiteJsonParser.DlsiteZiptree("RJ00000001", "revision", files.toList())
    }

    private fun contentFile(path: String): DlsiteJsonParser.ContentFile {
        val name = path.substringAfterLast("/")
        return DlsiteJsonParser.ContentFile(
            path,
            name,
            "optimized/$name",
            "",
            "$name.vtt",
        )
    }
}
