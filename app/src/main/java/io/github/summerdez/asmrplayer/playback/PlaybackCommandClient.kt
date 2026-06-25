package io.github.summerdez.asmrplayer.playback

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaController.Listener
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PlaybackCommandClient"

data class PlaybackControllerSnapshot(
    val connected: Boolean = false,
    val mediaId: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Int = 0,
    val positionMs: Int = 0,
    val hasNextMediaItem: Boolean = false,
    val serviceSnapshot: PlaybackServiceSnapshot = PlaybackServiceSnapshot(),
)

internal fun PlaybackControllerSnapshot.activeServiceSnapshotOrNull(): PlaybackServiceSnapshot? {
    val activeServiceSnapshot = serviceSnapshot
    return if (connected && activeServiceSnapshot.connected) activeServiceSnapshot else null
}

class PlaybackCommandClient(private val application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _snapshots = MutableStateFlow(PlaybackControllerSnapshot())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var controllerJob: Job? = null
    private val controllerListener = object : Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            _snapshots.value = controller.snapshot()
        }

        override fun onDisconnected(controller: MediaController) {
            this@PlaybackCommandClient.controller = null
            controllerFuture = null
            controllerJob?.cancel()
            controllerJob = null
            _snapshots.value = PlaybackControllerSnapshot()
        }
    }

    val snapshots: StateFlow<PlaybackControllerSnapshot> = _snapshots.asStateFlow()

    fun connect() {
        if (controllerFuture != null) {
            return
        }
        val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        val future = try {
            MediaController.Builder(application, token)
                .setListener(controllerListener)
                .buildAsync()
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Failed to build playback controller; connection can retry", exception)
            controller = null
            controllerFuture = null
            return
        }
        controllerFuture = future
        future.addListener(
            {
                val builtController = try {
                    future.get()
                } catch (exception: Exception) {
                    Log.w(TAG, "Failed to obtain playback controller; connection can retry", exception)
                    controller = null
                    controllerFuture = null
                    return@addListener
                }
                controller = builtController
                observeController(builtController)
            },
            ContextCompat.getMainExecutor(application),
        )
    }

    fun playMedia(
        audioUri: Uri,
        title: String,
        subtitleUri: Uri?,
        playlistId: String,
        playlistIndex: Int,
    ) {
        val args = Bundle().apply {
            putString(PlaybackService.EXTRA_AUDIO_URI, audioUri.toString())
            putString(PlaybackService.EXTRA_TRACK_TITLE, title)
            putString(PlaybackService.EXTRA_SUBTITLE_URI, subtitleUri?.toString().orEmpty())
            putString(PlaybackService.EXTRA_PLAYLIST_ID, playlistId)
            putInt(PlaybackService.EXTRA_PLAYLIST_INDEX, playlistIndex)
        }
        if (sendCommand(PlaybackService.COMMAND_PLAY_MEDIA, args)) {
            return
        }
        application.startService(
            PlaybackIntents.playMedia(application, audioUri, title, subtitleUri, playlistId, playlistIndex),
        )
    }

    fun togglePlayback(isPlaying: Boolean) {
        val activeController = controller
        if (activeController != null) {
            if (isPlaying) {
                activeController.pause()
            } else {
                activeController.play()
            }
            return
        }
        application.startService(PlaybackIntents.simpleAction(application, PlaybackService.ACTION_TOGGLE_PLAYBACK))
    }

    fun seekTo(positionMs: Int) {
        val activeController = controller
        if (activeController != null) {
            activeController.seekTo(positionMs.toLong())
            return
        }
        application.startService(PlaybackIntents.seekTo(application, positionMs.toLong()))
    }

    fun setSubtitle(uri: Uri) {
        val args = Bundle().apply {
            putString(PlaybackService.EXTRA_SUBTITLE_URI, uri.toString())
        }
        if (!sendCommand(PlaybackService.COMMAND_SET_SUBTITLE, args)) {
            application.startService(PlaybackIntents.setSubtitle(application, uri))
        }
    }

    fun setOverlayVisible(visible: Boolean) {
        val action = if (visible) PlaybackService.COMMAND_SHOW_OVERLAY else PlaybackService.COMMAND_HIDE_OVERLAY
        if (!sendCommand(action, Bundle.EMPTY)) {
            val fallbackAction = if (visible) PlaybackService.ACTION_SHOW_OVERLAY else PlaybackService.ACTION_HIDE_OVERLAY
            application.startService(PlaybackIntents.simpleAction(application, fallbackAction))
        }
    }

    fun unlockOverlay() {
        if (!sendCommand(PlaybackService.COMMAND_UNLOCK_OVERLAY, Bundle.EMPTY)) {
            application.startService(PlaybackIntents.simpleAction(application, PlaybackService.ACTION_UNLOCK_OVERLAY))
        }
    }

    fun setSleepMinutes(minutes: Int): Boolean {
        val args = Bundle().apply {
            putInt(PlaybackService.EXTRA_SLEEP_MINUTES, minutes)
        }
        if (!sendCommand(PlaybackService.COMMAND_SET_SLEEP_MINUTES, args)) {
            application.startService(PlaybackIntents.setSleepMinutes(application, minutes))
        }
        return true
    }

    fun setSleepAtEndOfTrack(): Boolean {
        if (!sendCommand(PlaybackService.COMMAND_SET_SLEEP_AT_END, Bundle.EMPTY)) {
            application.startService(PlaybackIntents.simpleAction(application, PlaybackService.ACTION_SET_SLEEP_AT_END))
        }
        return true
    }

    fun cancelSleepTimer() {
        if (!sendCommand(PlaybackService.COMMAND_CANCEL_SLEEP, Bundle.EMPTY)) {
            application.startService(PlaybackIntents.simpleAction(application, PlaybackService.ACTION_CANCEL_SLEEP))
        }
    }

    fun hasNextMediaItem(): Boolean {
        return controller?.hasNextMediaItem() == true
    }

    fun seekToNextMediaItem() {
        controller?.seekToNextMediaItem()
    }

    fun seekToPreviousMediaItem() {
        controller?.seekToPreviousMediaItem()
    }

    private fun sendCommand(action: String, args: Bundle): Boolean {
        val activeController = controller ?: return false
        activeController.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
        return true
    }

    private fun observeController(activeController: MediaController) {
        controllerJob?.cancel()
        controllerJob = controllerSnapshotFlow(activeController)
            .onEach { _snapshots.value = it }
            .launchIn(scope)
    }

    private fun controllerSnapshotFlow(activeController: MediaController) = callbackFlow {
        fun emitSnapshot() {
            trySend(activeController.snapshot())
        }

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                emitSnapshot()
            }
        }
        activeController.addListener(listener)
        emitSnapshot()
        val positionTicker = launch {
            while (isActive) {
                delay(CONTROLLER_POSITION_REFRESH_MS)
                if (activeController.isPlaying) {
                    emitSnapshot()
                }
            }
        }
        val sleepTimerTicker = launch {
            while (isActive) {
                delay(SLEEP_TIMER_REFRESH_MS)
                val serviceSnapshot = playbackServiceSnapshotFromSessionExtras(activeController.sessionExtras)
                if (serviceSnapshot.hasActiveSleepTimerCountdown()) {
                    emitSnapshot()
                }
            }
        }
        awaitClose {
            positionTicker.cancel()
            sleepTimerTicker.cancel()
            activeController.removeListener(listener)
        }
    }

    private fun MediaController.snapshot(): PlaybackControllerSnapshot {
        val serviceSnapshot = playbackServiceSnapshotFromSessionExtras(sessionExtras)
            .withDerivedSleepTimer(SystemClock.elapsedRealtime())
        val durationMs = duration.toPlaybackIntMs()
        val positionMs = currentPosition.toPlaybackIntMs()
        return PlaybackControllerSnapshot(
            connected = true,
            mediaId = currentMediaItem?.mediaId.orEmpty(),
            isPlaying = isPlaying,
            durationMs = durationMs,
            positionMs = positionMs,
            hasNextMediaItem = hasNextMediaItem(),
            serviceSnapshot = serviceSnapshot,
        )
    }

    private fun Long.toPlaybackIntMs(): Int {
        if (this == C.TIME_UNSET || this <= 0L) {
            return 0
        }
        return coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun PlaybackServiceSnapshot.withDerivedSleepTimer(nowMs: Long): PlaybackServiceSnapshot {
        if (!connected || !sleepTimerActive || sleepTimerAtEndOfTrack) {
            return copy(sleepTimerRemainingMs = 0L)
        }
        val remainingMs = (sleepTimerEndElapsedRealtimeMs - nowMs).coerceAtLeast(0L)
        return copy(
            sleepTimerActive = remainingMs > 0L,
            sleepTimerRemainingMs = remainingMs,
        )
    }

    private fun PlaybackServiceSnapshot.hasActiveSleepTimerCountdown(): Boolean {
        return connected &&
            sleepTimerActive &&
            !sleepTimerAtEndOfTrack &&
            sleepTimerEndElapsedRealtimeMs > 0L
    }

    private companion object {
        const val CONTROLLER_POSITION_REFRESH_MS = 250L
        const val SLEEP_TIMER_REFRESH_MS = 1_000L
    }
}
