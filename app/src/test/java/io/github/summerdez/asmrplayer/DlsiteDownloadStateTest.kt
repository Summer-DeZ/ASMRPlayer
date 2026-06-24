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
        val store = DlsiteDownloadStateStore()

        store.publishProgress(
            workId = "RJ000001",
            title = "Title",
            status = "下载中",
            bytesDownloaded = 25L,
            totalBytes = 100L,
        )

        val progress = store.state.value
        assertTrue(progress.active)
        assertEquals("RJ000001", progress.workId)
        assertEquals(25, progress.progressPercent)
        assertEquals("下载中 25%", progress.statusText)

        store.publish("RJ000001", "Title", "暂停中")
        val indeterminate = store.state.value
        assertNull(indeterminate.progressPercent)
        assertEquals("暂停中", indeterminate.statusText)

        store.clear()
        assertFalse(store.state.value.active)
    }

    @Test
    fun multiTaskStateSummarizesKnownByteProgressAndQueue() {
        val store = DlsiteDownloadStateStore()

        store.publishProgress(
            workId = "RJ000001",
            title = "One",
            status = "下载中",
            bytesDownloaded = 50L,
            totalBytes = 100L,
        )
        store.publishProgress(
            workId = "RJ000002",
            title = "Two",
            status = "下载中",
            bytesDownloaded = 25L,
            totalBytes = 100L,
        )
        store.publishQueued("RJ000003", "Three", 1)

        val state = store.state.value
        assertTrue(state.active)
        assertEquals(3, state.tasks.size)
        assertEquals(2, state.summary.runningCount)
        assertEquals(3, state.summary.totalCount)
        assertEquals(75L, state.summary.bytesDownloaded)
        assertEquals(200L, state.summary.totalBytes)
        assertEquals(37, state.summary.progressPercent)
        assertEquals("排队中 · 第 1 位", state.tasks["RJ000003"]?.statusText)
    }
}
