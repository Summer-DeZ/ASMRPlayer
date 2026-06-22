package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import io.github.summerdez.asmrplayer.presentation.SettingsUiState
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import io.github.summerdez.asmrplayer.ui.components.GroupFooter
import io.github.summerdez.asmrplayer.ui.components.GroupedCard
import io.github.summerdez.asmrplayer.ui.components.SettingsPermissionRow
import io.github.summerdez.asmrplayer.ui.components.SettingsSwitchRow
import io.github.summerdez.asmrplayer.ui.components.ThemeChip
import io.github.summerdez.asmrplayer.ui.theme.AppThemeMode
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens

@Composable
fun SettingsTab(
    state: SettingsUiState,
    onToggleOverlay: () -> Unit,
    onUnlockOverlay: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onCheckForUpdates: () -> Unit,
    onShowUpdateDetails: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onRetryUpdateDownload: () -> Unit,
    onAiEngineSelected: (AiTranslationEngine) -> Unit,
    onEditAiOllamaBaseUrl: () -> Unit,
    onEditAiOllamaModel: () -> Unit,
    onEditAiDeepSeekBaseUrl: () -> Unit,
    onEditAiDeepSeekModel: () -> Unit,
    onEditAiDeepSeekApiKey: () -> Unit,
    onAiAdultContentTranslationAllowedChange: (Boolean) -> Unit,
    onAiWhisperModelSelected: (String) -> Unit,
    onDownloadWhisperModel: () -> Unit,
    onCancelWhisperModelDownload: () -> Unit,
    onDeleteWhisperModel: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        GroupedCard {
            SettingsSwitchRow("悬浮字幕", state.overlayRequested, onToggleOverlay)
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
            SettingsSwitchRow("悬浮字幕锁定", state.overlayLocked, onUnlockOverlay)
        }
        GroupFooter("开启后字幕会以悬浮窗显示在其它应用之上，锁定可防止误触移动。")
        Spacer(Modifier.height(18.dp))
        GroupedCard {
            SettingsPermissionRow(
                title = "悬浮窗权限",
                value = if (state.overlayPermissionGranted) "已授权" else "去授权",
                valueAccent = !state.overlayPermissionGranted,
                onClick = onOpenOverlaySettings,
            )
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
            SettingsPermissionRow(
                title = "通知权限",
                value = if (state.notificationGranted) "已授权" else "去授权",
                valueAccent = !state.notificationGranted,
                onClick = onRequestNotificationPermission,
            )
        }
        GroupFooter("通知权限用于在后台显示播放控制。")
        Spacer(Modifier.height(18.dp))
        GroupedCard {
            Column(Modifier.padding(16.dp)) {
                Text("外观", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = tokens.label)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("深色", state.themeMode == AppThemeMode.DARK) { onThemeSelected(AppThemeMode.DARK) }
                    ThemeChip("浅色", state.themeMode == AppThemeMode.LIGHT) { onThemeSelected(AppThemeMode.LIGHT) }
                    ThemeChip("跟随系统", state.themeMode == AppThemeMode.SYSTEM) { onThemeSelected(AppThemeMode.SYSTEM) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("AI 字幕")
        GroupedCard {
            Column(Modifier.padding(16.dp)) {
                Text("翻译引擎", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = tokens.label)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("本地 Ollama", state.aiSubtitleSettings.translationEngine == AiTranslationEngine.OLLAMA) {
                        onAiEngineSelected(AiTranslationEngine.OLLAMA)
                    }
                    ThemeChip("云端 DeepSeek", state.aiSubtitleSettings.translationEngine == AiTranslationEngine.DEEPSEEK) {
                        onAiEngineSelected(AiTranslationEngine.DEEPSEEK)
                    }
                }
            }
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
            when (state.aiSubtitleSettings.translationEngine) {
                AiTranslationEngine.OLLAMA -> {
                    SettingsActionRow("接口地址", state.aiSubtitleSettings.ollamaBaseUrl, onEditAiOllamaBaseUrl)
                    HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
                    SettingsActionRow("模型", state.aiSubtitleSettings.ollamaModel, onEditAiOllamaModel)
                    HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
                    SettingsValueRow("API Key", "本地无需")
                }
                AiTranslationEngine.DEEPSEEK -> {
                    SettingsActionRow("接口地址", state.aiSubtitleSettings.deepSeekBaseUrl, onEditAiDeepSeekBaseUrl)
                    HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
                    SettingsActionRow("模型", state.aiSubtitleSettings.deepSeekModel, onEditAiDeepSeekModel)
                    HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
                    SettingsActionRow(
                        "API Key",
                        if (state.aiSubtitleSettings.deepSeekApiKey.isBlank()) "未设置" else "已设置",
                        onEditAiDeepSeekApiKey,
                    )
                }
            }
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
            SettingsSwitchRow(
                "成人内容直译",
                state.aiSubtitleSettings.allowAdultContentTranslation,
            ) {
                onAiAdultContentTranslationAllowedChange(!state.aiSubtitleSettings.allowAdultContentTranslation)
            }
        }
        GroupFooter("音频只在本机转写；翻译阶段只发送文本。开启成人内容直译后，AI 会按原文翻译成人向敏感词，不再用模糊说法规避。")
        Spacer(Modifier.height(14.dp))
        SectionLabel("转写模型")
        GroupedCard {
            WhisperModelSpec.ALL.forEachIndexed { index, spec ->
                SettingsSelectableRow(
                    title = spec.label,
                    value = formatUpdateBytes(spec.sizeBytes),
                    selected = state.aiSubtitleSettings.whisperModelId == spec.id,
                    onClick = { onAiWhisperModelSelected(spec.id) },
                )
                if (index != WhisperModelSpec.ALL.lastIndex) {
                    HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
                }
            }
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
            WhisperModelStatusRow(
                state = state,
                onDownload = onDownloadWhisperModel,
                onCancel = onCancelWhisperModelDownload,
                onDelete = onDeleteWhisperModel,
            )
        }
        GroupFooter("首次生成前下载 Whisper 模型；模型文件保存在本机应用私有目录，转写使用 CPU 并发处理语音切片。")
        Spacer(Modifier.height(18.dp))
        SectionLabel("关于")
        GroupedCard {
            SettingsValueRow("版本", state.currentVersionName)
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 16.dp))
            UpdateCheckRow(
                status = state.updateStatus,
                onClick = {
                    when (state.updateStatus) {
                        is AppUpdateStatus.Available -> onShowUpdateDetails()
                        AppUpdateStatus.Checking -> Unit
                        else -> onCheckForUpdates()
                    }
                },
            )
        }
        GroupFooter(updateFooterText(state.updateStatus))
        UpdateDownloadCard(
            status = state.updateDownloadStatus,
            onCancel = onCancelUpdateDownload,
            onRetry = onRetryUpdateDownload,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LocalAmberTokens.current.label2,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, bottom = 7.dp),
    )
}

@Composable
private fun SettingsValueRow(title: String, value: String) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 17.sp, color = tokens.label, modifier = Modifier.weight(1f))
        Text(value, fontSize = 17.sp, color = tokens.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsActionRow(title: String, value: String, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 17.sp, color = tokens.label, modifier = Modifier.weight(1f))
        Text(value, fontSize = 17.sp, color = tokens.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsSelectableRow(title: String, value: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 17.sp, color = tokens.label, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = tokens.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        Text(if (selected) "✓" else "", fontSize = 17.sp, color = tokens.accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WhisperModelStatusRow(
    state: SettingsUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val model = state.whisperModelState
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("模型状态", fontSize = 17.sp, color = tokens.label, modifier = Modifier.weight(1f))
            Text(
                when {
                    model.downloading -> "下载中 ${(model.progress * 100).toInt()}%"
                    model.downloaded -> "已下载"
                    model.error.isNotBlank() -> "下载失败"
                    else -> "未下载"
                },
                fontSize = 17.sp,
                color = if (model.error.isNotBlank()) tokens.accent2 else tokens.label2,
            )
        }
        if (model.downloading || model.error.isNotBlank()) {
            Box(
                modifier = Modifier
                    .padding(top = 11.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(tokens.label3.copy(alpha = 0.28f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(model.progress)
                        .background(if (model.error.isNotBlank()) tokens.accent2 else tokens.accent, RoundedCornerShape(3.dp)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = model.error.ifBlank {
                    if (model.downloaded) formatUpdateBytes(model.bytesDownloaded) else formatUpdateBytes(model.totalBytes)
                },
                color = tokens.label2,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = when {
                    model.downloading -> onCancel
                    model.downloaded -> onDelete
                    else -> onDownload
                },
            ) {
                Text(
                    when {
                        model.downloading -> "取消"
                        model.downloaded -> "删除"
                        else -> "下载"
                    },
                    color = if (model.downloaded || model.downloading) tokens.accent2 else tokens.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun UpdateCheckRow(status: AppUpdateStatus, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    val enabled = status !is AppUpdateStatus.Checking
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("检查更新", fontSize = 17.sp, color = tokens.label, modifier = Modifier.weight(1f))
        when (status) {
            AppUpdateStatus.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = tokens.accent,
                    trackColor = tokens.label3,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("正在检查…", fontSize = 17.sp, color = tokens.label2)
            }
            is AppUpdateStatus.Available -> {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(tokens.accent2, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "有新版本 ${status.release.versionName}",
                    fontSize = 17.sp,
                    color = tokens.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(18.dp))
            }
            AppUpdateStatus.UpToDate -> Text("已是最新", fontSize = 17.sp, color = tokens.label2)
            is AppUpdateStatus.Failed -> {
                Text("检查失败", fontSize = 17.sp, color = tokens.accent2)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(18.dp))
            }
            AppUpdateStatus.Idle -> {
                Text("点击检查", fontSize = 17.sp, color = tokens.label2)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun UpdateDownloadCard(
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
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (failed) "更新下载失败" else "正在下载更新 ${release.versionName}",
                    color = tokens.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (totalBytes > 0L) "${(progress * 100).toInt()}%" else "--",
                    color = if (failed) tokens.accent2 else tokens.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(tokens.label3.copy(alpha = 0.28f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(if (failed) tokens.accent2 else tokens.accent, RoundedCornerShape(3.dp)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = failureMessage ?: run {
                        "${formatUpdateBytes(bytesDownloaded)} / ${formatUpdateBytes(totalBytes)}"
                    },
                    color = tokens.label2,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = if (failed) onRetry else onCancel) {
                    Text(if (failed) "重试" else "取消", color = tokens.accent2, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun updateFooterText(status: AppUpdateStatus): String {
    return when (status) {
        is AppUpdateStatus.Failed -> status.message
        else -> "点击「检查更新」获取最新版本；发现新版本时显示更新点与版本号。"
    }
}

private fun formatUpdateBytes(bytes: Long): String {
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
