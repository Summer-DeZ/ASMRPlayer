package io.github.summerdez.asmrplayer.playback

import android.content.Intent
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.summerdez.asmrplayer.di.AppGraph
import io.github.summerdez.asmrplayer.domain.SleepTimerState
import io.github.summerdez.asmrplayer.domain.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sleepTimer = SleepTimerState()
    private val sleepTimerRunnable = Runnable {
        sleepTimer.cancel()
        handler.removeCallbacks(sleepFadeRunnable)
        pausePlayback()
        restoreSleepFadeVolume()
    }
    private val sleepFadeRunnable = Runnable {
        updateSleepFade()
    }
    private val transitionFadeRunnable = Runnable {
        updateTransitionFadeIn()
    }
    private val playlistLoadGate = PlaybackPlaylistLoadGate()
    private val playerListener = PlaybackPlayerListener(
        playWhenReady = { player.playWhenReady },
        overlayRequested = { overlayRequested },
        startSubtitleTicker = ::startSubtitleTicker,
        stopSubtitleTicker = ::stopSubtitleTicker,
        ensureOverlay = ::ensureOverlay,
        updateOverlayPlaybackState = ::updateOverlayPlaybackState,
        consumeSleepAtEndOfTrack = ::consumeSleepAtEndOfTrack,
        handleAudioSessionIdChanged = ::onAudioSessionIdChanged,
        startTransitionFadeIn = ::startTransitionFadeIn,
        syncCurrentTrack = ::syncCurrentTrack,
        updateSubtitleForCurrentPositionAndSchedule = ::updateSubtitleForCurrentPositionAndSchedule,
        markPlayerError = ::markPlayerError,
        publishState = ::publishState,
    )

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var playbackPlaylistResolver: PlaybackPlaylistResolver
    private lateinit var subtitleTicker: SubtitlePlaybackTicker
    private lateinit var playlistNavigator: PlaybackPlaylistNavigator
    private lateinit var trackSynchronizer: PlaybackTrackSynchronizer
    private var subtitleOverlayWindow: SubtitleOverlayWindow? = null
    private var lastSessionExtrasSnapshot = PlaybackServiceSnapshot()

    private var currentAudioUri: Uri? = null
    private var currentPlaylistId: String = ""
    private var currentPlaylistIndex: Int = -1
    private var trackTitle: String = DEFAULT_TRACK_TITLE
    private var lastSubtitleText: String = ""
    private var lastError: String = ""
    private var subtitleCues: List<SubtitleCue> = emptyList()
    private var overlayRequested: Boolean = false
    private var overlayLocked: Boolean = false
    private var sleepFadeBeforeEndEnabled: Boolean = true
    private var sleepFadeActive: Boolean = false
    private var sleepFadeStartVolume: Float = 1f
    private var binauralEnhancedEnabled: Boolean = true
    private var crossfadeEnabled: Boolean = true
    private var transitionFadeActive: Boolean = false
    private var transitionFadeStartElapsedMs: Long = 0L
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var virtualizer: Virtualizer? = null
    private var virtualizerAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    override fun onCreate() {
        super.onCreate()
        val serviceDependencies = AppGraph.container(this).playbackServiceDependencies
        playbackPlaylistResolver = serviceDependencies.playbackPlaylistResolver
        subtitleTicker = SubtitlePlaybackTicker(
            handler = handler,
            positionMs = { playerPositionMs().toLong() },
            isPlaying = { player.isPlaying },
            cues = { subtitleCues },
            updateText = ::updateSubtitleText,
        )
        subtitleOverlayWindow = SubtitleOverlayWindow(this, object : SubtitleOverlayWindow.Listener {
            override fun onPrevious() {
                playRelativeInPlaylist(-1)
            }

            override fun onPlayPause() {
                togglePlayback()
            }

            override fun onNext() {
                playRelativeInPlaylist(1)
            }

            override fun onLock() {
                setOverlayLocked(true)
            }
        })
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            addListener(playerListener)
        }
        playlistNavigator = PlaybackPlaylistNavigator(
            player = player,
            scope = serviceScope,
            loadGate = playlistLoadGate,
            loadPlaylist = ::loadPlaylistForPlayback,
            playMedia = ::playMedia,
        )
        trackSynchronizer = PlaybackTrackSynchronizer(
            player = player,
            scope = serviceScope,
            loadPlaylist = ::loadPlaylistForPlayback,
            applyPlaylistTrack = ::applyPlaylistTrackSnapshot,
            applyFallback = ::applyTrackMetadataFallback,
            publishState = ::publishState,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(playbackSessionActivity(this))
            .setCallback(PlaybackSessionCallback(::handleCommand))
            .setSessionExtras(PlaybackServiceSnapshot().toSessionExtras())
            .build()
        serviceScope.launch {
            serviceDependencies.settingsRepository.appBehaviorSettingsFlow.collect { settings ->
                binauralEnhancedEnabled = settings.binauralEnhanced
                applyBinauralEnhancement()
                crossfadeEnabled = settings.crossfadeEnabled
                if (!crossfadeEnabled) {
                    finishTransitionFadeIn()
                }
                sleepFadeBeforeEndEnabled = settings.sleepFadeBeforeEndEnabled
                if (sleepFadeBeforeEndEnabled) {
                    scheduleSleepFade()
                } else {
                    handler.removeCallbacks(sleepFadeRunnable)
                    restoreSleepFadeVolume()
                }
            }
        }
        publishState()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        handleIntent(intent)
        return result
    }

    override fun onDestroy() {
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        restoreSleepFadeVolume()
        releaseVirtualizer()
        removeOverlay()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        handleCommand(action, intent.extras ?: Bundle.EMPTY)
    }

    private fun handleCommand(action: String, args: Bundle) {
        when (val command = parsePlaybackServiceCommand(action, args) ?: return) {
            is PlaybackServiceCommand.PlayMedia -> playMedia(
                audioUri = command.audioUri,
                subtitleUri = command.subtitleUri,
                title = command.title,
                playlistId = command.playlistId,
                playlistIndex = command.playlistIndex,
            )
            PlaybackServiceCommand.TogglePlayback -> togglePlayback()
            PlaybackServiceCommand.StopPlayback -> stopPlaybackAndService()
            is PlaybackServiceCommand.SeekTo -> seekTo(command.positionMs)
            is PlaybackServiceCommand.SetSubtitle -> setSubtitle(command.subtitleUri)
            PlaybackServiceCommand.ShowOverlay -> setOverlayVisible(true)
            PlaybackServiceCommand.HideOverlay -> setOverlayVisible(false)
            PlaybackServiceCommand.UnlockOverlay -> setOverlayLocked(false)
            is PlaybackServiceCommand.SetSleepMinutes -> setSleepTimer(command.minutes)
            PlaybackServiceCommand.SetSleepAtEnd -> setSleepTimerAtEndOfTrack()
            PlaybackServiceCommand.CancelSleep -> cancelSleepTimer()
        }
    }

    private fun playMedia(
        audioUri: Uri?,
        subtitleUri: Uri?,
        title: String?,
        playlistId: String?,
        playlistIndex: Int,
    ) {
        if (audioUri == null) {
            lastError = "请选择音频文件"
            publishState()
            return
        }

        lastError = ""
        currentAudioUri = audioUri
        currentPlaylistId = playlistId.orEmpty()
        currentPlaylistIndex = playlistIndex
        trackTitle = title?.takeIf { it.isNotBlank() } ?: DEFAULT_TRACK_TITLE
        loadSubtitle(subtitleUri)

        val requestVersion = playlistLoadGate.nextRequest()
        val requestedAudioUri = audioUri.toString()
        val requestedPlaylistId = currentPlaylistId
        val requestedPlaylistIndex = currentPlaylistIndex
        val requestedTitle = trackTitle
        playlistLoadGate.setJob(
            serviceScope.launch {
                val playlist = loadPlaylistForPlayback(requestedPlaylistId)
                if (!playlistLoadGate.isLatest(requestVersion)
                    || currentAudioUri?.toString() != requestedAudioUri
                    || currentPlaylistId != requestedPlaylistId
                    || currentPlaylistIndex != requestedPlaylistIndex
                ) {
                    return@launch
                }
                prepareAndPlay(requestedAudioUri, requestedTitle, requestedPlaylistId, requestedPlaylistIndex, playlist)
            },
        )
        publishState()
    }

    private fun prepareAndPlay(
        audioUriValue: String,
        title: String,
        playlistId: String,
        playlistIndex: Int,
        playlist: Playlist?,
    ) {
        val audioUri = parsePlaybackUri(audioUriValue) ?: return
        val queue = playlist?.let(PlaybackMediaQueue::buildPlaylistQueue).orEmpty()
        val queueIndex = queue.indexOfFirst {
            it.mediaId == PlaybackMediaQueue.mediaId(playlistId, playlistIndex, audioUriValue)
        }
        if (queueIndex >= 0) {
            player.setMediaItems(queue, queueIndex, 0L)
        } else {
            player.setMediaItem(
                PlaybackMediaQueue.buildMediaItem(audioUri, title, playlistId, playlistIndex, playlist?.coverUri),
            )
        }
        player.prepare()
        startTransitionFadeIn()
        player.play()
        startSubtitleTicker()
        if (overlayRequested) {
            ensureOverlay()
        }
        publishState()
    }

    private fun setSubtitle(subtitleUri: Uri?) {
        loadSubtitle(subtitleUri)
        updateSubtitleForCurrentPositionAndSchedule()
        publishState()
    }

    private fun togglePlayback() {
        if (player.mediaItemCount == 0) {
            return
        }
        if (player.isPlaying) {
            pausePlayback()
        } else {
            player.play()
            startSubtitleTicker()
            publishState()
        }
    }

    private fun pausePlayback() {
        finishTransitionFadeIn()
        player.pause()
        stopSubtitleTicker()
        updateOverlayPlaybackState()
        publishState()
    }

    private fun seekTo(positionMs: Long) {
        if (player.mediaItemCount == 0) {
            return
        }
        player.seekTo(max(0L, positionMs))
        updateSubtitleForCurrentPositionAndSchedule()
        publishState()
    }

    private fun setOverlayVisible(visible: Boolean) {
        overlayRequested = visible
        if (visible) {
            ensureOverlay()
            updateSubtitleForCurrentPositionAndSchedule()
        } else {
            removeOverlay()
        }
        publishState()
    }

    private fun setOverlayLocked(locked: Boolean) {
        overlayLocked = locked
        subtitleOverlayWindow?.setLocked(locked)
        publishState()
    }

    private fun setSleepTimer(minutes: Int) {
        restoreSleepFadeVolume()
        val durationMs = sleepTimer.setMinutes(minutes, SystemClock.elapsedRealtime())
        handler.removeCallbacks(sleepTimerRunnable)
        handler.removeCallbacks(sleepFadeRunnable)
        handler.postDelayed(sleepTimerRunnable, durationMs)
        scheduleSleepFade()
        publishState()
    }

    private fun setSleepTimerAtEndOfTrack() {
        restoreSleepFadeVolume()
        sleepTimer.setAtEndOfTrack()
        handler.removeCallbacks(sleepTimerRunnable)
        handler.removeCallbacks(sleepFadeRunnable)
        publishState()
    }

    private fun cancelSleepTimer() {
        sleepTimer.cancel()
        handler.removeCallbacks(sleepTimerRunnable)
        handler.removeCallbacks(sleepFadeRunnable)
        restoreSleepFadeVolume()
        publishState()
    }

    private fun startTransitionFadeIn() {
        handler.removeCallbacks(transitionFadeRunnable)
        if (!crossfadeEnabled || sleepFadeActive || !::player.isInitialized || player.mediaItemCount == 0) {
            return
        }
        transitionFadeActive = true
        transitionFadeStartElapsedMs = SystemClock.elapsedRealtime()
        player.volume = 0f
        handler.post(transitionFadeRunnable)
    }

    private fun updateTransitionFadeIn() {
        if (!transitionFadeActive) {
            return
        }
        if (!crossfadeEnabled || sleepFadeActive || !::player.isInitialized) {
            finishTransitionFadeIn()
            return
        }
        val elapsedMs = SystemClock.elapsedRealtime() - transitionFadeStartElapsedMs
        val progress = (elapsedMs.toFloat() / TRANSITION_FADE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        player.volume = progress
        if (progress >= 1f) {
            transitionFadeActive = false
            player.volume = 1f
        } else {
            handler.postDelayed(transitionFadeRunnable, TRANSITION_FADE_TICK_MS)
        }
    }

    private fun finishTransitionFadeIn() {
        handler.removeCallbacks(transitionFadeRunnable)
        if (::player.isInitialized && transitionFadeActive && !sleepFadeActive) {
            player.volume = 1f
        }
        transitionFadeActive = false
    }

    private fun onAudioSessionIdChanged(audioSessionId: Int) {
        currentAudioSessionId = audioSessionId
        applyBinauralEnhancement()
    }

    private fun applyBinauralEnhancement() {
        val sessionId = currentAudioSessionId
        if (!binauralEnhancedEnabled || sessionId == C.AUDIO_SESSION_ID_UNSET) {
            releaseVirtualizer()
            return
        }
        if (virtualizerAudioSessionId == sessionId && virtualizer != null) {
            virtualizer?.enabled = true
            return
        }
        releaseVirtualizer()
        virtualizer = runCatching {
            Virtualizer(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(BINAURAL_VIRTUALIZER_STRENGTH)
                }
                enabled = true
            }
        }.onFailure { exception ->
            Log.w(TAG, "Failed to enable binaural virtualizer for audio session=$sessionId", exception)
        }.getOrNull()
        virtualizerAudioSessionId = if (virtualizer != null) sessionId else C.AUDIO_SESSION_ID_UNSET
    }

    private fun releaseVirtualizer() {
        virtualizer?.let { effect ->
            runCatching { effect.enabled = false }
            runCatching { effect.release() }
        }
        virtualizer = null
        virtualizerAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun scheduleSleepFade() {
        handler.removeCallbacks(sleepFadeRunnable)
        if (!sleepFadeBeforeEndEnabled || sleepTimer.isAtEndOfTrack()) {
            return
        }
        val remainingMs = getSleepTimerRemainingMs()
        if (remainingMs <= 0L) {
            return
        }
        val delayMs = (remainingMs - SLEEP_FADE_DURATION_MS).coerceAtLeast(0L)
        handler.postDelayed(sleepFadeRunnable, delayMs)
    }

    private fun updateSleepFade() {
        if (!sleepFadeBeforeEndEnabled || sleepTimer.isAtEndOfTrack()) {
            restoreSleepFadeVolume()
            return
        }
        val remainingMs = getSleepTimerRemainingMs()
        if (remainingMs <= 0L) {
            return
        }
        if (!sleepFadeActive) {
            sleepFadeActive = true
            sleepFadeStartVolume = player.volume.coerceIn(0f, 1f)
        }
        val progress = (remainingMs.toFloat() / SLEEP_FADE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        player.volume = (sleepFadeStartVolume * progress).coerceIn(0f, 1f)
        handler.postDelayed(sleepFadeRunnable, SLEEP_FADE_TICK_MS)
    }

    private fun restoreSleepFadeVolume() {
        if (!::player.isInitialized || !sleepFadeActive) {
            return
        }
        player.volume = sleepFadeStartVolume.coerceIn(0f, 1f)
        sleepFadeActive = false
    }

    private fun playRelativeInPlaylist(delta: Int) {
        playlistNavigator.playRelative(delta, currentPlaylistId, currentPlaylistIndex)
    }

    private fun loadSubtitle(subtitleUri: Uri?) {
        lastSubtitleText = ""
        val result = loadSubtitleCues(contentResolver, subtitleUri)
        subtitleCues = result.cues
        if (result.clearError) {
            lastError = ""
        }
        result.errorMessage?.let { lastError = it }
    }

    private fun updateSubtitleForCurrentPositionAndSchedule() {
        subtitleTicker.updateAndSchedule()
    }

    private fun updateSubtitleText(text: String?) {
        lastSubtitleText = text.orEmpty()
        subtitleOverlayWindow?.updateText(lastSubtitleText)
        publishState()
    }

    private fun startSubtitleTicker() {
        subtitleTicker.start()
    }

    private fun stopSubtitleTicker() {
        subtitleTicker.stop()
    }

    private fun ensureOverlay(): Boolean {
        val outcome = showSubtitleOverlay(subtitleOverlayWindow, lastSubtitleText, player.isPlaying, overlayLocked)
        outcome.errorMessage?.let { lastError = it }
        return outcome.shown
    }

    private fun removeOverlay() {
        subtitleOverlayWindow?.remove()
    }

    private fun updateOverlayPlaybackState() {
        subtitleOverlayWindow?.setPlaying(player.isPlaying)
    }

    private fun stopPlaybackAndService() {
        stopSubtitleTicker()
        finishTransitionFadeIn()
        restoreSleepFadeVolume()
        removeOverlay()
        player.stop()
        player.clearMediaItems()
        stopSelf()
    }

    private fun consumeSleepAtEndOfTrack(): Boolean {
        if (!sleepTimer.consumeAtEndOfTrack()) {
            return false
        }
        handler.removeCallbacks(sleepTimerRunnable)
        player.pause()
        stopSubtitleTicker()
        publishState()
        return true
    }

    private fun markPlayerError() {
        lastError = "播放失败，文件格式可能不受当前设备支持"
    }

    private fun syncCurrentTrack(mediaItem: MediaItem?) {
        trackSynchronizer.sync(mediaItem)
    }

    private fun applyPlaylistTrackSnapshot(snapshot: PlaybackTrackSnapshot) {
        currentPlaylistId = snapshot.playlistId
        currentPlaylistIndex = snapshot.playlistIndex
        currentAudioUri = snapshot.audioUri
        trackTitle = snapshot.title
        loadSubtitle(snapshot.subtitleUri)
        updateSubtitleForCurrentPositionAndSchedule()
    }

    private fun applyTrackMetadataFallback(snapshot: PlaybackTrackMetadataSnapshot) {
        snapshot.audioUri?.let { currentAudioUri = it }
        snapshot.title?.let { trackTitle = it }
    }

    private suspend fun loadPlaylistForPlayback(playlistId: String): Playlist? {
        return when (val result = playbackPlaylistResolver.resolve(playlistId)) {
            PlaybackPlaylistResolveResult.None -> null
            is PlaybackPlaylistResolveResult.Loaded -> result.playlist
            is PlaybackPlaylistResolveResult.Failed -> {
                lastError = "无法读取播放列表"
                publishState()
                null
            }
        }
    }

    private fun playerPositionMs(): Int {
        val position = player.currentPosition
        if (position <= 0L) {
            return 0
        }
        return position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun getSleepTimerRemainingMs(): Long {
        return sleepTimer.remainingMs(SystemClock.elapsedRealtime())
    }

    private fun publishState() {
        val sleepRemainingMs = getSleepTimerRemainingMs()
        val sleepAtEndOfTrack = sleepTimer.isAtEndOfTrack()
        lastSessionExtrasSnapshot = publishPlaybackServiceState(
            mediaSession = mediaSession,
            lastSessionExtrasSnapshot = lastSessionExtrasSnapshot,
            snapshot = buildPlaybackServiceSnapshot(
                playlistId = currentPlaylistId,
                playlistIndex = currentPlaylistIndex,
                audioUri = currentAudioUri?.toString().orEmpty(),
                subtitleLines = subtitleCues.map { it.text },
                subtitleStartsMs = subtitleCues.map { it.startMs.toInt() },
                subtitleIndex = subtitleTicker.activeSubtitleIndex(),
                subtitleCount = subtitleCues.size,
                overlayRequested = overlayRequested,
                overlayLocked = overlayLocked,
                lastError = lastError,
                sleepTimerActive = sleepAtEndOfTrack || sleepRemainingMs > 0L,
                sleepTimerAtEndOfTrack = sleepAtEndOfTrack,
                sleepTimerEndElapsedRealtimeMs = sleepTimer.endElapsedRealtimeMs(),
                sleepTimerMinutes = sleepTimer.minutes(),
            ),
        )
    }

    companion object {
        const val ACTION_PLAY_MEDIA = "io.github.summerdez.asmrplayer.action.PLAY_MEDIA"
        const val ACTION_TOGGLE_PLAYBACK = "io.github.summerdez.asmrplayer.action.TOGGLE_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "io.github.summerdez.asmrplayer.action.STOP_PLAYBACK"
        const val ACTION_SEEK_TO = "io.github.summerdez.asmrplayer.action.SEEK_TO"
        const val ACTION_SET_SUBTITLE = "io.github.summerdez.asmrplayer.action.SET_SUBTITLE"
        const val ACTION_SHOW_OVERLAY = "io.github.summerdez.asmrplayer.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "io.github.summerdez.asmrplayer.action.HIDE_OVERLAY"
        const val ACTION_UNLOCK_OVERLAY = "io.github.summerdez.asmrplayer.action.UNLOCK_OVERLAY"
        const val ACTION_SET_SLEEP_MINUTES = "io.github.summerdez.asmrplayer.action.SET_SLEEP_MINUTES"
        const val ACTION_SET_SLEEP_AT_END = "io.github.summerdez.asmrplayer.action.SET_SLEEP_AT_END"
        const val ACTION_CANCEL_SLEEP = "io.github.summerdez.asmrplayer.action.CANCEL_SLEEP"

        const val COMMAND_PLAY_MEDIA = ACTION_PLAY_MEDIA
        const val COMMAND_SET_SUBTITLE = ACTION_SET_SUBTITLE
        const val COMMAND_SHOW_OVERLAY = ACTION_SHOW_OVERLAY
        const val COMMAND_HIDE_OVERLAY = ACTION_HIDE_OVERLAY
        const val COMMAND_UNLOCK_OVERLAY = ACTION_UNLOCK_OVERLAY
        const val COMMAND_SET_SLEEP_MINUTES = ACTION_SET_SLEEP_MINUTES
        const val COMMAND_SET_SLEEP_AT_END = ACTION_SET_SLEEP_AT_END
        const val COMMAND_CANCEL_SLEEP = ACTION_CANCEL_SLEEP

        private const val SLEEP_FADE_DURATION_MS = 30_000L
        private const val SLEEP_FADE_TICK_MS = 500L
        private const val TRANSITION_FADE_DURATION_MS = 1_200L
        private const val TRANSITION_FADE_TICK_MS = 8L
        private const val BINAURAL_VIRTUALIZER_STRENGTH: Short = 700
        private const val TAG = "PlaybackService"

        const val EXTRA_AUDIO_URI = "extra_audio_uri"
        const val EXTRA_SUBTITLE_URI = "extra_subtitle_uri"
        const val EXTRA_TRACK_TITLE = "extra_track_title"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_PLAYLIST_INDEX = "extra_playlist_index"
        const val EXTRA_SLEEP_MINUTES = "extra_sleep_minutes"

        private const val DEFAULT_TRACK_TITLE = "ASMRPlayer"
    }
}
