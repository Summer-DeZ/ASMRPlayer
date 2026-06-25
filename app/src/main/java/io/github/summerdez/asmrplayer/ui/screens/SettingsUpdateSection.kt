package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.presentation.AppUpdateDownloadStatus
import io.github.summerdez.asmrplayer.presentation.AppUpdateStatus
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe

@Composable
internal fun UpdateCheckRow(status: AppUpdateStatus, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    val enabled = status !is AppUpdateStatus.Checking
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .uiProbe(
                id = "settings.update-check",
                label = "检查更新行",
                sourceHint = "SettingsUpdateSection.kt",
                metadata = mapOf(
                    "status" to status::class.simpleName.orEmpty(),
                    "enabled" to enabled.toString(),
                ),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "检查更新",
            fontSize = SettingsRowTitleFontSize,
            fontWeight = SettingsRowTitleFontWeight,
            color = tokens.label,
            modifier = Modifier.weight(1f),
        )
        when (status) {
            AppUpdateStatus.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = tokens.accent,
                    trackColor = tokens.label3,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("正在检查…", fontSize = SettingsRowValueFontSize, color = tokens.label3)
            }
            is AppUpdateStatus.Available -> {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(tokens.accent, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "有新版本 ${status.release.versionName}",
                    fontSize = SettingsRowValueFontSize,
                    color = tokens.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppUpdateStatus.UpToDate -> Text("已是最新", fontSize = SettingsRowValueFontSize, color = tokens.label3)
            is AppUpdateStatus.Failed -> {
                Text(
                    text = status.message.ifBlank { "检查失败" },
                    fontSize = SettingsRowValueFontSize,
                    color = tokens.accent2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppUpdateStatus.Idle -> Unit
        }
    }
}

@Composable
internal fun UpdateDownloadCard(
    status: AppUpdateDownloadStatus,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val visible = status is AppUpdateDownloadStatus.Downloading || status is AppUpdateDownloadStatus.Failed
    if (!visible) {
        return
    }
    val tokens = LocalAmberTokens.current
    val release = when (status) {
        is AppUpdateDownloadStatus.Downloading -> status.release
        is AppUpdateDownloadStatus.Failed -> status.release
        AppUpdateDownloadStatus.Idle, is AppUpdateDownloadStatus.Downloaded -> return
    }
    val bytesDownloaded = when (status) {
        is AppUpdateDownloadStatus.Downloading -> status.bytesDownloaded
        is AppUpdateDownloadStatus.Failed -> status.bytesDownloaded
        AppUpdateDownloadStatus.Idle, is AppUpdateDownloadStatus.Downloaded -> 0L
    }
    val totalBytes = when (status) {
        is AppUpdateDownloadStatus.Downloading -> status.totalBytes
        is AppUpdateDownloadStatus.Failed -> status.totalBytes
        AppUpdateDownloadStatus.Idle, is AppUpdateDownloadStatus.Downloaded -> 0L
    }
    val speedBytesPerSecond = when (status) {
        is AppUpdateDownloadStatus.Downloading -> status.speedBytesPerSecond
        is AppUpdateDownloadStatus.Failed -> status.speedBytesPerSecond
        AppUpdateDownloadStatus.Idle, is AppUpdateDownloadStatus.Downloaded -> 0L
    }
    val progress = if (totalBytes > 0L) {
        (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val failureMessage = (status as? AppUpdateDownloadStatus.Failed)?.message
    val failed = failureMessage != null
    Spacer(Modifier.height(14.dp))
    Surface(
        color = tokens.card,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, tokens.separator),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "settings.update-download-card",
                label = if (failed) "更新下载失败卡片" else "更新下载进度卡片",
                sourceHint = "SettingsUpdateSection.kt",
                metadata = mapOf(
                    "versionName" to release.versionName,
                    "progressPercent" to (progress * 100f).toInt().toString(),
                    "failed" to failed.toString(),
                ),
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(Icons.Default.Download, alt = failed)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = if (failed) "更新下载失败" else "正在下载 v${release.versionName}",
                    color = tokens.label,
                    fontSize = SettingsRowTitleFontSize,
                    fontWeight = SettingsRowTitleFontWeight,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (failed) {
                    TextButton(onClick = onRetry) {
                        Text("重试", color = tokens.accent2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(onClick = onCancel),
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.label.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, tokens.separator),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "取消", tint = tokens.accent2, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            ProgressLine(
                progress = progress,
                failed = failed,
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = failureMessage ?: formatUpdateBytes(speedBytesPerSecond) + "/s",
                    color = if (failed) tokens.accent2 else tokens.label2,
                    fontSize = SettingsRowValueFontSize,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatUpdateBytes(bytesDownloaded)} / ${formatUpdateBytes(totalBytes)}",
                    color = tokens.label2,
                    fontSize = SettingsRowValueFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ProgressLine(progress: Float, failed: Boolean, modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(tokens.label.copy(alpha = 0.07f), RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(if (failed) tokens.accent2 else tokens.accent, RoundedCornerShape(3.dp)),
        )
    }
}

internal fun formatUpdateBytes(bytes: Long): String {
    if (bytes <= 0L) {
        return "--"
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
