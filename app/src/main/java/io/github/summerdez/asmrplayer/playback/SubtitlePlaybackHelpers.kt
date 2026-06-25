package io.github.summerdez.asmrplayer.playback

import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import java.io.IOException

internal class SubtitlePlaybackTicker(
    private val handler: Handler,
    private val positionMs: () -> Long,
    private val isPlaying: () -> Boolean,
    private val cues: () -> List<SubtitleCue>,
    private val updateText: (String?) -> Unit,
) {
    private var running = false
    private val ticker = object : Runnable {
        override fun run() {
            updateAndSchedule()
        }
    }

    fun start() {
        if (running) {
            updateAndSchedule()
            return
        }
        running = true
        updateAndSchedule()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(ticker)
    }

    fun updateAndSchedule() {
        val schedule = currentSchedule()
        updateText(schedule.frame.text)
        scheduleNext(schedule)
    }

    fun activeSubtitleIndex(): Int {
        return currentSchedule().frame.index
    }

    private fun currentSchedule(): SubtitleCueSchedule {
        return SubtitleCueScheduler.scheduleAt(cues(), positionMs())
    }

    private fun scheduleNext(schedule: SubtitleCueSchedule = currentSchedule()) {
        handler.removeCallbacks(ticker)
        if (!running || !isPlaying() || cues().isEmpty()) {
            return
        }
        val delayMs = schedule.nextWakeDelayMs
        if (delayMs == null) {
            return
        }
        handler.postDelayed(ticker, delayMs)
    }
}

internal data class SubtitleLoadResult(
    val cues: List<SubtitleCue>,
    val errorMessage: String?,
    val clearError: Boolean,
)

internal fun loadSubtitleCues(contentResolver: ContentResolver, subtitleUri: Uri?): SubtitleLoadResult {
    if (subtitleUri == null) {
        return SubtitleLoadResult(emptyList(), errorMessage = null, clearError = true)
    }
    return try {
        SubtitleLoadResult(
            cues = SubtitleParser.parse(contentResolver, subtitleUri),
            errorMessage = null,
            clearError = true,
        )
    } catch (error: IOException) {
        SubtitleLoadResult(emptyList(), errorMessage = "无法读取字幕文件", clearError = false)
    } catch (error: SecurityException) {
        SubtitleLoadResult(emptyList(), errorMessage = "无法读取字幕文件", clearError = false)
    }
}

internal data class SubtitleOverlayShowOutcome(
    val shown: Boolean,
    val errorMessage: String?,
)

internal fun showSubtitleOverlay(
    window: SubtitleOverlayWindow?,
    subtitleText: String,
    isPlaying: Boolean,
    locked: Boolean,
): SubtitleOverlayShowOutcome {
    if (window == null) {
        return SubtitleOverlayShowOutcome(shown = false, errorMessage = "悬浮字幕窗口创建失败")
    }
    return when (window.show(subtitleText, isPlaying, locked)) {
        SubtitleOverlayWindow.ShowResult.SHOWN -> SubtitleOverlayShowOutcome(shown = true, errorMessage = null)
        SubtitleOverlayWindow.ShowResult.MISSING_PERMISSION -> SubtitleOverlayShowOutcome(
            shown = false,
            errorMessage = "需要先允许悬浮窗权限",
        )
        SubtitleOverlayWindow.ShowResult.FAILED -> SubtitleOverlayShowOutcome(
            shown = false,
            errorMessage = "悬浮字幕窗口创建失败",
        )
    }
}
