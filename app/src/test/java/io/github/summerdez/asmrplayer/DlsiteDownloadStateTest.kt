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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteDownloadStateTest {
    @Test
    fun progressStatePublishesPercentAndClears() {
        try {
            DlsiteDownloadStateBus.publishProgress(
                workId = "RJ000001",
                title = "Title",
                status = "下载中",
                bytesDownloaded = 25L,
                totalBytes = 100L,
            )

            val progress = DlsiteDownloadStateBus.state.value
            assertTrue(progress.active)
            assertEquals("RJ000001", progress.workId)
            assertEquals(25, progress.progressPercent)
            assertEquals("下载中 25%", progress.statusText)

            DlsiteDownloadStateBus.publish("RJ000001", "Title", "暂停中")
            val indeterminate = DlsiteDownloadStateBus.state.value
            assertNull(indeterminate.progressPercent)
            assertEquals("暂停中", indeterminate.statusText)
        } finally {
            DlsiteDownloadStateBus.clear()
        }

        assertFalse(DlsiteDownloadStateBus.state.value.active)
    }
}
