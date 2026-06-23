package io.github.summerdez.asmrplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.summerdez.asmrplayer.data.update.AppUpdateRelease
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens


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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = tokens.label,
                    unfocusedTextColor = tokens.label,
                    cursorColor = tokens.accent,
                    focusedBorderColor = tokens.accent,
                    unfocusedBorderColor = tokens.separator,
                    focusedContainerColor = tokens.cardTop,
                    unfocusedContainerColor = tokens.cardTop,
                ),
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
fun UpdateDetailsDialog(
    release: AppUpdateRelease,
    currentVersionName: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    IosDialog(onDismiss = onDismiss) {
        Column(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("发现新版本 ${release.versionName}", color = tokens.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(
                "当前 $currentVersionName · 更新包 ${formatUpdateSize(release.apkSizeBytes)}",
                color = tokens.label2,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                releaseNotesText(release.releaseNotes),
                color = tokens.label2,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 9,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
        DialogButtons(
            cancelText = "稍后",
            confirmText = "立即更新",
            destructive = false,
            onCancel = onDismiss,
            onConfirm = onDownload,
        )
    }
}

@Composable
fun InstallUpdateDialog(
    release: AppUpdateRelease,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    IosDialog(onDismiss = onDismiss) {
        Column(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("更新包已下载", color = tokens.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(
                "是否立即安装 ${release.versionName}？",
                color = tokens.label2,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        DialogButtons(
            cancelText = "稍后",
            confirmText = "立即安装",
            destructive = false,
            onCancel = onDismiss,
            onConfirm = onInstall,
        )
    }
}

@Composable
fun IosDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val tokens = LocalAmberTokens.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = tokens.sheet,
            border = BorderStroke(0.5.dp, tokens.separator),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.width(300.dp),
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
    Row(Modifier.fillMaxWidth().height(48.dp)) {
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

private fun releaseNotesText(notes: String): String {
    val lines = notes.lines()
        .map { it.trim().trimStart('-', '*', '·').trim() }
        .filter { it.isNotEmpty() }
        .take(8)
    if (lines.isEmpty()) {
        return "更新内容\n· 本次发布未填写更新日志。"
    }
    return buildString {
        append("更新内容")
        lines.forEach { line ->
            append('\n')
            append("· ")
            append(line)
        }
    }
}

private fun formatUpdateSize(bytes: Long): String {
    if (bytes <= 0L) {
        return "未知大小"
    }
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(value, units[unitIndex])
    }
}
