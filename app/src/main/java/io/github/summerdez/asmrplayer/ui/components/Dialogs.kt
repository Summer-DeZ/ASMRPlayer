package io.github.summerdez.asmrplayer.ui.components

import io.github.summerdez.asmrplayer.R
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
import java.text.DateFormat
import java.util.Date
import kotlin.math.max


@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmText: String,
    numeric: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val tokens = LocalAmberTokens.current
    IosDialog(onDismiss = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = tokens.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(if (numeric) "输入睡眠定时的分钟数" else "输入新的名称", color = tokens.label2, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { next ->
                    value = if (numeric) next.filter { it.isDigit() } else next
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
        DialogButtons(
            cancelText = "取消",
            confirmText = confirmText,
            confirmEnabled = value.trim().isNotEmpty(),
            destructive = false,
            onCancel = onDismiss,
            onConfirm = { onConfirm(value.trim()) },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val tokens = LocalAmberTokens.current
    IosDialog(onDismiss = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = tokens.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(message, color = tokens.label2, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        DialogButtons(
            cancelText = "取消",
            confirmText = "删除",
            destructive = true,
            onCancel = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

@Composable
fun MoveTrackDialog(
    sourcePlaylist: Playlist,
    track: TrackItem,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onChoose: (Playlist) -> Unit,
) {
    val targets = playlists.filter { it.id != sourcePlaylist.id }
    val tokens = LocalAmberTokens.current
    IosDialog(onDismiss = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("移动曲目", color = tokens.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(track.title, color = tokens.label2, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            if (targets.isEmpty()) {
                Text("没有其他播放列表", color = tokens.label2, fontSize = 15.sp, modifier = Modifier.padding(vertical = 10.dp))
            } else {
                targets.forEach { playlist ->
                    TextButton(onClick = { onChoose(playlist) }, modifier = Modifier.fillMaxWidth()) {
                        Text(playlist.name, color = tokens.accent, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
            }
        }
        DialogButtons(cancelText = "取消", confirmText = "", showConfirm = false, onCancel = onDismiss, onConfirm = {})
    }
}

@Composable
fun DownloadOptionsDialog(
    work: DlsiteWork,
    options: List<DlsiteDownloadOption>,
    onDismiss: () -> Unit,
    onChoose: (DlsiteDownloadOption) -> Unit,
) {
    val tokens = LocalAmberTokens.current
    IosDialog(onDismiss = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("选择下载版本", color = tokens.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(work.displayTitle(), color = tokens.label2, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            options.forEach { option ->
                TextButton(onClick = { onChoose(option) }, modifier = Modifier.fillMaxWidth()) {
                    Text(option.dialogLabel(), color = tokens.accent, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }
        DialogButtons(cancelText = "取消", confirmText = "", showConfirm = false, onCancel = onDismiss, onConfirm = {})
    }
}

@Composable
fun IosDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = LocalAmberTokens.current.sheet,
            modifier = Modifier.width(292.dp),
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun DialogButtons(
    cancelText: String,
    confirmText: String,
    showConfirm: Boolean = true,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    HorizontalDivider(color = tokens.separator)
    Row(Modifier.fillMaxWidth().height(46.dp)) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(cancelText, color = tokens.label2, fontSize = 17.sp)
        }
        if (showConfirm) {
            Box(
                Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .background(tokens.separator),
            )
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Text(
                    confirmText,
                    color = when {
                        !confirmEnabled -> tokens.label3
                        destructive -> tokens.accent2
                        else -> tokens.accent
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
