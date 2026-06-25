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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.domain.model.transcriptionDetailLabel
import io.github.summerdez.asmrplayer.presentation.LibraryUiState
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import io.github.summerdez.asmrplayer.ui.util.formatDuration
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.sin

@Composable
fun LibraryTab(
    state: LibraryUiState,
    playbackState: PlaybackUiState,
    onPlaylistClicked: (Playlist) -> Unit,
    onCoverClicked: (Playlist) -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onTrackClicked: (Playlist, Int) -> Unit,
    onTrackSubtitleClicked: (Playlist, TrackItem) -> Unit,
    onGenerateSubtitle: (Playlist, TrackItem) -> Unit,
    onOpenSubtitleGeneration: (String) -> Unit,
    onRenameTrack: (Playlist, TrackItem) -> Unit,
    onDeleteTrack: (Playlist, TrackItem, Int) -> Unit,
    onMoveTrack: (Playlist, TrackItem) -> Unit,
    subtitleTasks: Map<String, AiSubtitleTaskState> = emptyMap(),
) {
    var query by remember { mutableStateOf("") }
    val visiblePlaylists = remember(state.playlists, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            state.playlists
        } else {
            state.playlists.filter { playlist ->
                playlist.name.contains(q, ignoreCase = true) ||
                playlist.tracks.any { it.title.contains(q, ignoreCase = true) }
            }
        }
    }
    val initialExpandedId = state.selectedPlaylist?.id
        ?: playbackState.playlistId.takeIf { it.isNotEmpty() }
        ?: state.playlists.firstOrNull()?.id
    var expandedPlaylistId by remember { mutableStateOf(initialExpandedId) }
    LaunchedEffect(state.selectedPlaylist?.id) {
        state.selectedPlaylist?.id?.let { expandedPlaylistId = it }
    }
    LaunchedEffect(playbackState.playlistId) {
        if (playbackState.playlistId.isNotEmpty()) {
            expandedPlaylistId = playbackState.playlistId
        }
    }
    LaunchedEffect(visiblePlaylists.map { it.id }) {
        val firstVisiblePlaylistId = visiblePlaylists.firstOrNull()?.id
        if (expandedPlaylistId == null) {
            expandedPlaylistId = firstVisiblePlaylistId
        } else if (visiblePlaylists.none { it.id == expandedPlaylistId }) {
            expandedPlaylistId = firstVisiblePlaylistId
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiProbe("library.root", "资料库列表", "LibraryScreen.kt"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "library-search") {
            LibrarySearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            )
        }
        if (state.playlists.isEmpty()) {
            item(key = "library-dlsite-empty") {
                LibraryDlsiteEmptyState()
            }
        } else {
            items(visiblePlaylists, key = { it.id }) { playlist ->
                val selected = state.selectedPlaylist?.id == playlist.id
                val expanded = expandedPlaylistId == playlist.id
                PlaylistRow(
                    playlist = playlist,
                    selected = selected,
                    expanded = expanded,
                    playbackState = playbackState,
                    onPlaylistClicked = {
                        expandedPlaylistId = if (expandedPlaylistId == playlist.id) null else playlist.id
                        onPlaylistClicked(playlist)
                    },
                    onCoverClicked = { onCoverClicked(playlist) },
                    onRenamePlaylist = { onRenamePlaylist(playlist) },
                    onDeletePlaylist = { onDeletePlaylist(playlist) },
                    onTrackClicked = { index -> onTrackClicked(playlist, index) },
                    onTrackSubtitleClicked = { track -> onTrackSubtitleClicked(playlist, track) },
                    onGenerateSubtitle = { track -> onGenerateSubtitle(playlist, track) },
                    onOpenSubtitleGeneration = onOpenSubtitleGeneration,
                    onRenameTrack = { track -> onRenameTrack(playlist, track) },
                    onDeleteTrack = { track, index -> onDeleteTrack(playlist, track, index) },
                    onMoveTrack = { track -> onMoveTrack(playlist, track) },
                    subtitleTasks = subtitleTasks,
                )
            }
            if (visiblePlaylists.isEmpty()) {
                item(key = "library-empty-search") {
                    Text(
                        "没有找到匹配的作品",
                        color = LocalAmberTokens.current.label3,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp),
                    )
                }
            }
        }
    }
}
