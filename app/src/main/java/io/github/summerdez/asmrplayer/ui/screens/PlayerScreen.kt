package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.dp
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
    var menuOpen by remember { mutableStateOf(false) }
    var subtitlesVisible by remember { mutableStateOf(true) }
    val rootInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(playbackState.playlistId, playbackState.playlistIndex, playbackState.subtitleTitle) {
        subtitlesVisible = true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = rootInteractionSource,
                indication = null,
                onClick = {},
            )
            .uiProbe("player.root", "全屏播放器", "PlayerScreen.kt")
            .background(tokens.playerBase)
            .background(
                Brush.radialGradient(
                    colors = listOf(tokens.accent.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(180f, 140f),
                    radius = 520f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(tokens.accent2.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(880f, 260f),
                    radius = 640f,
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
