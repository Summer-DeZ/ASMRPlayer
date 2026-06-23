package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadQueueTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteDownloadQueueTaskTest {
    @Test
    fun pendingTasksKeepFifoQueueOrderAndOptionIds() {
        val later = DlsiteDownloadQueueTask.create(
            workId = "RJ00000002",
            optionIds = listOf("wav", "bonus"),
            queueOrder = 2L,
            now = 200L,
        )
        val earlier = DlsiteDownloadQueueTask.create(
            workId = "RJ00000001",
            optionIds = listOf("mp3"),
            queueOrder = 1L,
            now = 100L,
        )

        assertEquals(listOf(earlier, later), listOf(later, earlier).sortedBy { it.queueOrder })
        assertEquals(listOf("wav", "bonus"), later.optionIdList())
        assertTrue(earlier.isPending())
        assertTrue(earlier.isActive())
    }

    @Test
    fun runningTasksCanBeResetOrFinished() {
        val pending = DlsiteDownloadQueueTask.create(
            workId = "RJ00000001",
            optionIds = listOf("mp3"),
            queueOrder = 1L,
            now = 100L,
        )

        val running = pending.asRunning(now = 200L)
        val reset = running.asPending(now = 300L)
        val completed = running.asFinished(DlsiteDownloadQueueTask.STATUS_COMPLETED, now = 400L)

        assertTrue(running.isRunning())
        assertEquals(200L, running.startedAt)
        assertTrue(reset.isPending())
        assertEquals(null, reset.startedAt)
        assertFalse(completed.isActive())
        assertEquals(400L, completed.finishedAt)
    }
}
