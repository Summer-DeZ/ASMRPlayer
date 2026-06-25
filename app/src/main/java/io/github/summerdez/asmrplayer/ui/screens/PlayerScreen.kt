package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe

@Composable
fun SubtitlePlayerScreen(
    playbackState: PlaybackUiState,
    aiSubtitleTask: AiSubtitleTaskState?,
    currentTrackIsLocal: Boolean,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onQueue: () -> Unit,
    onGenerateAiSubtitle: () -> Unit,
    onOpenAiSubtitleProgress: () -> Unit,
    onSleepTimer: () -> Unit,
    onMixLayers: () -> Unit,
    onDownloadTrack: () -> Unit,
    onRemoveFromQueue: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val backgroundColors = rememberPlayerBackgroundColors(
        coverUri = playbackState.coverUri,
        defaultBase = tokens.playerBase,
        defaultPrimaryGlow = tokens.accent,
        defaultSecondaryGlow = tokens.accent2,
    )
    var menuOpen by remember { mutableStateOf(false) }
    var subtitlesVisible by remember { mutableStateOf(true) }
    val hasPlaybackContent = playbackState.playlistId.isNotBlank() && playbackState.playlistIndex >= 0
    LaunchedEffect(playbackState.playlistId, playbackState.playlistIndex, playbackState.subtitleTitle) {
        subtitlesVisible = true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .consumeUnclaimedPointerInput()
            .uiProbe(
                id = "player.root",
                label = "全屏播放器",
                sourceHint = "PlayerScreen.kt",
                metadata = mapOf(
                    "coverUri" to playbackState.coverUri,
                    "dynamicBackground" to backgroundColors.fromCover.toString(),
                ),
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        backgroundColors.base,
                        backgroundColors.base,
                        backgroundColors.baseBottom,
                    ),
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(backgroundColors.glowPrimary.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(180f, 140f),
                    radius = 520f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(backgroundColors.glowSecondary.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(880f, 260f),
                    radius = 640f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(backgroundColors.glowSecondary.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(540f, 1960f),
                    radius = 980f,
                ),
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 28.dp),
        ) {
            PlayerTopBar(
                contextTitle = playbackState.contextTitle,
                showContextTitle = hasPlaybackContent,
                onClose = onClose,
                onOpenMenu = { menuOpen = true },
            )
            PlayerSubtitleDisplay(
                playbackState = playbackState,
                subtitlesVisible = subtitlesVisible,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            PlayerTrackMeta(playbackState)
            Spacer(Modifier.height(18.dp))
            PlaybackProgressLine(playbackState, onSeek)
            PlayerTransportControls(
                playbackState = playbackState,
                onPrevious = onPrevious,
                onPlay = onPlay,
                onNext = onNext,
            )
            PlayerBottomActions(
                onSleepTimer = onSleepTimer,
                onMixLayers = onMixLayers,
                onQueue = onQueue,
            )
        }
        if (menuOpen) {
            PlayerMoreMenuSheet(
                playbackState = playbackState,
                aiSubtitleTask = aiSubtitleTask,
                subtitlesVisible = subtitlesVisible,
                currentTrackIsLocal = currentTrackIsLocal,
                onToggleSubtitles = { subtitlesVisible = !subtitlesVisible },
                onGenerateAiSubtitle = onGenerateAiSubtitle,
                onOpenAiSubtitleProgress = onOpenAiSubtitleProgress,
                onSleepTimer = {
                    menuOpen = false
                    onSleepTimer()
                },
                onMixLayers = {
                    menuOpen = false
                    onMixLayers()
                },
                onDownloadTrack = {
                    menuOpen = false
                    if (!currentTrackIsLocal) {
                        onDownloadTrack()
                    }
                },
                onRemoveFromQueue = {
                    menuOpen = false
                    onRemoveFromQueue()
                },
                onClose = { menuOpen = false },
            )
        }
    }
}

private fun Modifier.consumeUnclaimedPointerInput(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { change ->
                if (!change.isConsumed) {
                    change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
