package io.github.summerdez.asmrplayer.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.components.AsmrIcon
import io.github.summerdez.asmrplayer.ui.components.AsmrIconName
import io.github.summerdez.asmrplayer.ui.components.WaveBars
import io.github.summerdez.asmrplayer.ui.components.noRippleClickable
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import io.github.summerdez.asmrplayer.ui.util.formatDuration
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Composable
fun QueueContent(
    playlist: Playlist?,
    playbackState: PlaybackUiState,
    onTrackClicked: (Playlist, Int) -> Unit,
    onDismissRequest: (() -> Unit)? = null,
) {
    val tokens = LocalAmberTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "queue.sheet",
                label = "播放队列弹层",
                sourceHint = "QueueScreen.kt",
                metadata = mapOf(
                    "playlistId" to (playlist?.id ?: ""),
                    "playlistName" to (playlist?.name ?: ""),
                    "trackCount" to (playlist?.tracks?.size ?: 0).toString(),
                ),
            ),
        color = tokens.sheet,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        border = BorderStroke(1.dp, tokens.separator),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 14.dp, bottom = 14.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(tokens.switchOff)
                    .align(Alignment.CenterHorizontally),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist?.name ?: "队列",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (playlist == null) "0 段" else "${playlist.tracks.size} 段 · 播放队列",
                        color = tokens.label3,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (onDismissRequest != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .uiProbe("queue.close", "关闭播放队列按钮", "QueueScreen.kt")
                            .noRippleClickable(onClick = onDismissRequest),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsmrIcon(
                            name = AsmrIconName.Close,
                            tint = tokens.labelFaint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            val queuePlaylist = playlist
            if (queuePlaylist == null || queuePlaylist.tracks.isEmpty()) {
                Text(
                    "队列为空",
                    color = tokens.label2,
                    modifier = Modifier
                        .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp)
                        .uiProbe("queue.empty-state", "播放队列空状态", "QueueScreen.kt"),
                )
                return@Column
            }
            val currentIndex = if (playbackState.playlistId == queuePlaylist.id) {
                playbackState.playlistIndex.takeIf { it in queuePlaylist.tracks.indices } ?: -1
            } else {
                -1
            }
            val orderedIndices = if (currentIndex >= 0) {
                listOf(currentIndex) + (currentIndex + 1 until queuePlaylist.tracks.size).toList() + (0 until currentIndex).toList()
            } else {
                queuePlaylist.tracks.indices.toList()
            }
            orderedIndices.forEach { index ->
                QueueTrackRow(
                    index = index,
                    track = queuePlaylist.tracks[index],
                    active = index == currentIndex,
                    playing = playbackState.isPlaying,
                    onClick = { onTrackClicked(queuePlaylist, index) },
                )
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    index: Int,
    track: TrackItem,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) tokens.accentSoft.copy(alpha = 0.50f) else Color.Transparent)
            .uiProbe(
                id = "queue.track-row:${track.id}",
                label = "播放队列曲目：${track.title}",
                sourceHint = "QueueScreen.kt",
                metadata = mapOf(
                    "trackId" to track.id,
                    "index" to index.toString(),
                    "active" to active.toString(),
                    "playing" to playing.toString(),
                    "durationMs" to track.durationMs.toString(),
                ),
            )
            .noRippleClickable(onClick = onClick)
            .padding(start = 22.dp, end = 22.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                WaveBars(
                    modifier = Modifier.size(width = 22.dp, height = 16.dp),
                    playing = playing,
                )
            } else {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    color = tokens.labelFaint,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            track.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) tokens.accent else tokens.label2,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            formatDuration(track.durationMs),
            color = tokens.labelFaint,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
