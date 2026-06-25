package io.github.summerdez.asmrplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import io.github.summerdez.asmrplayer.di.AppGraph
import io.github.summerdez.asmrplayer.domain.SleepTimerState
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.ui.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val subtitleTicker = object : Runnable {
        override fun run() {
            updateSubtitleForCurrentPositionAndSchedule()
        }
    }
    private val sleepTimer = SleepTimerState()
    private val sleepTimerRunnable = Runnable {
        sleepTimer.cancel()
        pausePlayback()
    }

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var playbackPlaylistResolver: PlaybackPlaylistResolver
    private var subtitleOverlayWindow: SubtitleOverlayWindow? = null
    private var playlistLoadJob: Job? = null
    private var trackSyncJob: Job? = null
    private var playlistLoadVersion: Long = 0L
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
    private var tickerRunning: Boolean = false

    override fun onCreate() {
        super.onCreate()
        val serviceDependencies = AppGraph.container(this).playbackServiceDependencies
        playbackPlaylistResolver = serviceDependencies.playbackPlaylistResolver
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
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity())
            .setCallback(SessionCallback())
            .setSessionExtras(PlaybackServiceSnapshot().toSessionExtras())
            .build()
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
        removeOverlay()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startSubtitleTicker()
                if (overlayRequested) {
                    ensureOverlay()
                }
            } else {
                stopSubtitleTicker()
            }
            updateOverlayPlaybackState()
            publishState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && player.playWhenReady) {
                startSubtitleTicker()
            }
            if (playbackState == Player.STATE_ENDED) {
                stopSubtitleTicker()
                consumeSleepAtEndOfTrack()
            }
            publishState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncCurrentTrack(mediaItem)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && consumeSleepAtEndOfTrack()) {
                return
            }
            updateSubtitleForCurrentPositionAndSchedule()
            publishState()
        }

        override fun onPlayerError(error: PlaybackException) {
            lastError = "播放失败，文件格式可能不受当前设备支持"
            stopSubtitleTicker()
            publishState()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            ) {
                updateSubtitleForCurrentPositionAndSchedule()
                publishState()
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connection = super.onConnect(session, controller)
            val commands = connection.availableSessionCommands.buildUpon()
            CUSTOM_COMMANDS.forEach { commands.add(SessionCommand(it, Bundle.EMPTY)) }
            return MediaSession.ConnectionResult.accept(commands.build(), connection.availablePlayerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            handleCommand(customCommand.customAction, args)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        handleCommand(action, intent.extras ?: Bundle.EMPTY)
    }

    private fun handleCommand(action: String, args: Bundle) {
        when (action) {
            ACTION_PLAY_MEDIA, COMMAND_PLAY_MEDIA -> {
                playMedia(
                    audioUri = parseUri(args.getString(EXTRA_AUDIO_URI)),
                    subtitleUri = parseUri(args.getString(EXTRA_SUBTITLE_URI)),
                    title = args.getString(EXTRA_TRACK_TITLE),
                    playlistId = args.getString(EXTRA_PLAYLIST_ID),
                    playlistIndex = args.getInt(EXTRA_PLAYLIST_INDEX, -1),
                )
            }
            ACTION_TOGGLE_PLAYBACK -> togglePlayback()
            ACTION_STOP_PLAYBACK -> stopPlaybackAndService()
            ACTION_SEEK_TO -> seekTo(args.getLong(EXTRA_POSITION_MS, 0L))
            ACTION_SET_SUBTITLE, COMMAND_SET_SUBTITLE -> setSubtitle(parseUri(args.getString(EXTRA_SUBTITLE_URI)))
            ACTION_SHOW_OVERLAY, COMMAND_SHOW_OVERLAY -> setOverlayVisible(true)
            ACTION_HIDE_OVERLAY, COMMAND_HIDE_OVERLAY -> setOverlayVisible(false)
            ACTION_UNLOCK_OVERLAY, COMMAND_UNLOCK_OVERLAY -> setOverlayLocked(false)
            ACTION_SET_SLEEP_MINUTES, COMMAND_SET_SLEEP_MINUTES ->
                setSleepTimer(args.getInt(EXTRA_SLEEP_MINUTES, 0))
            ACTION_SET_SLEEP_AT_END, COMMAND_SET_SLEEP_AT_END -> setSleepTimerAtEndOfTrack()
            ACTION_CANCEL_SLEEP, COMMAND_CANCEL_SLEEP -> cancelSleepTimer()
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

        val requestVersion = nextPlaylistLoadVersion()
        val requestedAudioUri = audioUri.toString()
        val requestedPlaylistId = currentPlaylistId
        val requestedPlaylistIndex = currentPlaylistIndex
        val requestedTitle = trackTitle
        playlistLoadJob = serviceScope.launch {
            val playlist = loadPlaylistForPlayback(requestedPlaylistId)
            if (!isLatestPlaylistRequest(requestVersion)
                || currentAudioUri?.toString() != requestedAudioUri
                || currentPlaylistId != requestedPlaylistId
                || currentPlaylistIndex != requestedPlaylistIndex
            ) {
                return@launch
            }
            prepareAndPlay(requestedAudioUri, requestedTitle, requestedPlaylistId, requestedPlaylistIndex, playlist)
        }
        publishState()
    }

    private fun prepareAndPlay(
        audioUriValue: String,
        title: String,
        playlistId: String,
        playlistIndex: Int,
        playlist: Playlist?,
    ) {
        val audioUri = parseUri(audioUriValue) ?: return
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
        val durationMs = sleepTimer.setMinutes(minutes, SystemClock.elapsedRealtime())
        handler.removeCallbacks(sleepTimerRunnable)
        handler.postDelayed(sleepTimerRunnable, durationMs)
        publishState()
    }

    private fun setSleepTimerAtEndOfTrack() {
        sleepTimer.setAtEndOfTrack()
        handler.removeCallbacks(sleepTimerRunnable)
        publishState()
    }

    private fun cancelSleepTimer() {
        sleepTimer.cancel()
        handler.removeCallbacks(sleepTimerRunnable)
        publishState()
    }

    private fun playRelativeInPlaylist(delta: Int) {
        if (delta > 0 && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
            return
        }
        if (delta < 0 && player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
            return
        }
        if (currentPlaylistId.isEmpty() || currentPlaylistIndex < 0) {
            return
        }

        val requestVersion = nextPlaylistLoadVersion()
        val requestedPlaylistId = currentPlaylistId
        val requestedPlaylistIndex = currentPlaylistIndex
        playlistLoadJob = serviceScope.launch {
            val playlist = loadPlaylistForPlayback(requestedPlaylistId) ?: return@launch
            if (!isLatestPlaylistRequest(requestVersion)
                || currentPlaylistId != requestedPlaylistId
                || currentPlaylistIndex != requestedPlaylistIndex
            ) {
                return@launch
            }
            val targetIndex = requestedPlaylistIndex + delta
            val track = playlist.tracks.getOrNull(targetIndex) ?: return@launch
            if (!track.hasAudioUri()) {
                return@launch
            }
            playMedia(track.audioUri(), track.subtitleUriOrNull(), track.title, playlist.id, targetIndex)
        }
    }

    private fun loadSubtitle(subtitleUri: Uri?) {
        lastSubtitleText = ""
        if (subtitleUri == null) {
            subtitleCues = emptyList()
            return
        }
        try {
            subtitleCues = SubtitleParser.parse(contentResolver, subtitleUri)
            lastError = ""
        } catch (error: IOException) {
            subtitleCues = emptyList()
            lastError = "无法读取字幕文件"
        } catch (error: SecurityException) {
            subtitleCues = emptyList()
            lastError = "无法读取字幕文件"
        }
    }

    private fun updateSubtitleForCurrentPositionAndSchedule() {
        val schedule = currentSubtitleSchedule()
        updateSubtitleText(schedule.frame.text)
        scheduleNextSubtitleUpdate(schedule)
    }

    private fun updateSubtitleText(text: String?) {
        lastSubtitleText = text.orEmpty()
        subtitleOverlayWindow?.updateText(lastSubtitleText)
        publishState()
    }

    private fun startSubtitleTicker() {
        if (tickerRunning) {
            updateSubtitleForCurrentPositionAndSchedule()
            return
        }
        tickerRunning = true
        updateSubtitleForCurrentPositionAndSchedule()
    }

    private fun stopSubtitleTicker() {
        tickerRunning = false
        handler.removeCallbacks(subtitleTicker)
    }

    private fun currentSubtitleSchedule(): SubtitleCueSchedule {
        return SubtitleCueScheduler.scheduleAt(subtitleCues, playerPositionMs().toLong())
    }

    private fun scheduleNextSubtitleUpdate(schedule: SubtitleCueSchedule = currentSubtitleSchedule()) {
        handler.removeCallbacks(subtitleTicker)
        if (!tickerRunning || !player.isPlaying || subtitleCues.isEmpty()) {
            return
        }
        val delayMs = schedule.nextWakeDelayMs
        if (delayMs == null) {
            return
        }
        handler.postDelayed(subtitleTicker, delayMs)
    }

    private fun ensureOverlay(): Boolean {
        val window = subtitleOverlayWindow
        if (window == null) {
            lastError = "悬浮字幕窗口创建失败"
            return false
        }
        return when (window.show(lastSubtitleText, player.isPlaying, overlayLocked)) {
            SubtitleOverlayWindow.ShowResult.SHOWN -> true
            SubtitleOverlayWindow.ShowResult.MISSING_PERMISSION -> {
                lastError = "需要先允许悬浮窗权限"
                false
            }
            SubtitleOverlayWindow.ShowResult.FAILED -> {
                lastError = "悬浮字幕窗口创建失败"
                false
            }
        }
    }

    private fun removeOverlay() {
        subtitleOverlayWindow?.remove()
    }

    private fun updateOverlayPlaybackState() {
        subtitleOverlayWindow?.setPlaying(player.isPlaying)
    }

    private fun stopPlaybackAndService() {
        stopSubtitleTicker()
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

    private fun syncCurrentTrack(mediaItem: MediaItem?) {
        val identity = PlaybackMediaQueue.parseMediaId(mediaItem?.mediaId)
        if (identity != null) {
            val mediaId = mediaItem?.mediaId.orEmpty()
            trackSyncJob?.cancel()
            trackSyncJob = serviceScope.launch {
                val playlist = loadPlaylistForPlayback(identity.playlistId)
                if (player.currentMediaItem?.mediaId != mediaId) {
                    return@launch
                }
                val track = playlist?.tracks?.getOrNull(identity.index)
                if (track != null) {
                    currentPlaylistId = identity.playlistId
                    currentPlaylistIndex = identity.index
                    currentAudioUri = track.audioUri()
                    trackTitle = track.title
                    loadSubtitle(track.subtitleUriOrNull())
                    updateSubtitleForCurrentPositionAndSchedule()
                    publishState()
                    return@launch
                }
                syncMediaMetadataFallback(mediaItem)
                publishState()
            }
            return
        }
        syncMediaMetadataFallback(mediaItem)
    }

    private fun syncMediaMetadataFallback(mediaItem: MediaItem?) {
        mediaItem?.localConfiguration?.uri?.let { currentAudioUri = it }
        mediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }?.let { trackTitle = it }
    }

    private fun nextPlaylistLoadVersion(): Long {
        playlistLoadJob?.cancel()
        playlistLoadVersion += 1L
        return playlistLoadVersion
    }

    private fun isLatestPlaylistRequest(requestVersion: Long): Boolean {
        return requestVersion == playlistLoadVersion
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

    private fun activeSubtitleIndex(): Int {
        return currentSubtitleSchedule().frame.index
    }

    private fun getSleepTimerRemainingMs(): Long {
        return sleepTimer.remainingMs(SystemClock.elapsedRealtime())
    }

    private fun publishState() {
        val snapshot = currentServiceSnapshot()
        val sessionExtrasSnapshot = snapshot.asSessionExtrasSnapshot()
        if (sessionExtrasSnapshot != lastSessionExtrasSnapshot) {
            lastSessionExtrasSnapshot = sessionExtrasSnapshot
            mediaSession?.setSessionExtras(sessionExtrasSnapshot.toSessionExtras())
        }
    }

    private fun currentServiceSnapshot(): PlaybackServiceSnapshot {
        val sleepRemainingMs = getSleepTimerRemainingMs()
        val sleepAtEndOfTrack = sleepTimer.isAtEndOfTrack()
        return PlaybackServiceSnapshot(
            connected = true,
            playlistId = currentPlaylistId,
            playlistIndex = currentPlaylistIndex,
            audioUri = currentAudioUri?.toString().orEmpty(),
            subtitleLines = subtitleCues.map { it.text },
            subtitleIndex = activeSubtitleIndex(),
            subtitleCount = subtitleCues.size,
            overlayRequested = overlayRequested,
            overlayLocked = overlayLocked,
            error = PlaybackError.fromMessage(lastError),
            sleepTimerActive = sleepAtEndOfTrack || sleepRemainingMs > 0L,
            sleepTimerAtEndOfTrack = sleepAtEndOfTrack,
            sleepTimerEndElapsedRealtimeMs = sleepTimer.endElapsedRealtimeMs(),
            sleepTimerRemainingMs = sleepRemainingMs,
            sleepTimerMinutes = sleepTimer.minutes(),
        )
    }

    private fun PlaybackServiceSnapshot.asSessionExtrasSnapshot(): PlaybackServiceSnapshot {
        // Session extras carry only low-frequency custom state; the client derives countdown ticks locally.
        return copy(
            sleepTimerRemainingMs = 0L,
        )
    }

    private fun sessionActivity(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
    }

    private fun parseUri(value: String?): Uri? {
        return value?.takeIf { it.isNotEmpty() }?.let(Uri::parse)
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

        const val EXTRA_AUDIO_URI = "extra_audio_uri"
        const val EXTRA_SUBTITLE_URI = "extra_subtitle_uri"
        const val EXTRA_TRACK_TITLE = "extra_track_title"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_PLAYLIST_INDEX = "extra_playlist_index"
        const val EXTRA_SLEEP_MINUTES = "extra_sleep_minutes"

        private const val DEFAULT_TRACK_TITLE = "ASMRPlayer"
        private val CUSTOM_COMMANDS = listOf(
            COMMAND_PLAY_MEDIA,
            COMMAND_SET_SUBTITLE,
            COMMAND_SHOW_OVERLAY,
            COMMAND_HIDE_OVERLAY,
            COMMAND_UNLOCK_OVERLAY,
            COMMAND_SET_SLEEP_MINUTES,
            COMMAND_SET_SLEEP_AT_END,
            COMMAND_CANCEL_SLEEP,
        )
    }
}
