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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import io.github.summerdez.asmrplayer.presentation.AppUpdateDownloadStatus
import io.github.summerdez.asmrplayer.presentation.AppUpdateStatus
import io.github.summerdez.asmrplayer.presentation.RemoteWhisperTestStatus
import io.github.summerdez.asmrplayer.presentation.SettingsUiState
import io.github.summerdez.asmrplayer.ui.components.GroupedCard
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
    onAiTranscriptionBackendSelected: (AiTranscriptionBackend) -> Unit,
    onAiEngineSelected: (AiTranslationEngine) -> Unit,
    onEditAiOllamaBaseUrl: () -> Unit,
    onEditAiOllamaModel: () -> Unit,
    onEditAiDeepSeekBaseUrl: () -> Unit,
    onEditAiDeepSeekModel: () -> Unit,
    onEditAiDeepSeekApiKey: () -> Unit,
    onAiAdultContentTranslationAllowedChange: (Boolean) -> Unit,
    onAiWhisperModelSelected: (String) -> Unit,
    onEditRemoteWhisperBaseUrl: () -> Unit,
    onEditRemoteWhisperModel: () -> Unit,
    onEditRemoteWhisperToken: () -> Unit,
    onTestRemoteWhisperConnection: () -> Unit,
    onDownloadWhisperModel: () -> Unit,
    onCancelWhisperModelDownload: () -> Unit,
    onDeleteWhisperModel: () -> Unit,
) {
    var aiSettingsOpen by remember { mutableStateOf(false) }
    if (aiSettingsOpen) {
        AiSettingsPage(
            state = state,
            onBack = { aiSettingsOpen = false },
            onAiTranscriptionBackendSelected = onAiTranscriptionBackendSelected,
            onAiEngineSelected = onAiEngineSelected,
            onEditAiOllamaBaseUrl = onEditAiOllamaBaseUrl,
            onEditAiOllamaModel = onEditAiOllamaModel,
            onEditAiDeepSeekBaseUrl = onEditAiDeepSeekBaseUrl,
            onEditAiDeepSeekModel = onEditAiDeepSeekModel,
            onEditAiDeepSeekApiKey = onEditAiDeepSeekApiKey,
            onAiAdultContentTranslationAllowedChange = onAiAdultContentTranslationAllowedChange,
            onAiWhisperModelSelected = onAiWhisperModelSelected,
            onEditRemoteWhisperBaseUrl = onEditRemoteWhisperBaseUrl,
            onEditRemoteWhisperModel = onEditRemoteWhisperModel,
            onEditRemoteWhisperToken = onEditRemoteWhisperToken,
            onTestRemoteWhisperConnection = onTestRemoteWhisperConnection,
            onDownloadWhisperModel = onDownloadWhisperModel,
            onCancelWhisperModelDownload = onCancelWhisperModelDownload,
            onDeleteWhisperModel = onDeleteWhisperModel,
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SectionLabel("字幕")
        GroupedCard {
            SettingsSwitchIconRow(Icons.Default.Subtitles, "悬浮字幕", state.overlayRequested, onToggleOverlay)
            SettingsDivider()
            SettingsSwitchIconRow(Icons.Default.LockOpen, "悬浮字幕锁定", state.overlayLocked, onUnlockOverlay)
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("权限")
        GroupedCard {
            SettingsActionRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = "悬浮窗权限",
                value = if (state.overlayPermissionGranted) "已授权" else "去授权",
                valueAccent = true,
                trailing = RowTrailing.External,
                onClick = onOpenOverlaySettings,
            )
            SettingsDivider()
            SettingsActionRow(
                icon = Icons.Default.Notifications,
                title = "通知权限",
                value = if (state.notificationGranted) "已授权" else "去授权",
                valueAccent = true,
                trailing = RowTrailing.External,
                onClick = onRequestNotificationPermission,
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("外观")
        SettingsSegmentedControl(
            labels = listOf("深色", "浅色", "跟随系统"),
            selectedIndex = when (state.themeMode) {
                AppThemeMode.DARK -> 0
                AppThemeMode.LIGHT -> 1
                AppThemeMode.SYSTEM -> 2
            },
            onSelected = { index ->
                onThemeSelected(
                    when (index) {
                        0 -> AppThemeMode.DARK
                        1 -> AppThemeMode.LIGHT
                        else -> AppThemeMode.SYSTEM
                    },
                )
            },
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("高级设置")
        GroupedCard {
            SettingsActionRow(
                icon = Icons.Default.Subtitles,
                title = "AI 字幕与翻译",
                value = "翻译与转写",
                onClick = { aiSettingsOpen = true },
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("关于")
        GroupedCard {
            SettingsValueRow(Icons.Default.Settings, "版本", state.currentVersionName)
            SettingsDivider()
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
        UpdateDownloadCard(
            status = state.updateDownloadStatus,
            onCancel = onCancelUpdateDownload,
            onRetry = onRetryUpdateDownload,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AiSettingsPage(
    state: SettingsUiState,
    onBack: () -> Unit,
    onAiTranscriptionBackendSelected: (AiTranscriptionBackend) -> Unit,
    onAiEngineSelected: (AiTranslationEngine) -> Unit,
    onEditAiOllamaBaseUrl: () -> Unit,
    onEditAiOllamaModel: () -> Unit,
    onEditAiDeepSeekBaseUrl: () -> Unit,
    onEditAiDeepSeekModel: () -> Unit,
    onEditAiDeepSeekApiKey: () -> Unit,
    onAiAdultContentTranslationAllowedChange: (Boolean) -> Unit,
    onAiWhisperModelSelected: (String) -> Unit,
    onEditRemoteWhisperBaseUrl: () -> Unit,
    onEditRemoteWhisperModel: () -> Unit,
    onEditRemoteWhisperToken: () -> Unit,
    onTestRemoteWhisperConnection: () -> Unit,
    onDownloadWhisperModel: () -> Unit,
    onCancelWhisperModelDownload: () -> Unit,
    onDeleteWhisperModel: () -> Unit,
) {
    val settings = state.aiSubtitleSettings
    val tokens = LocalAmberTokens.current
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = tokens.label, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("AI 字幕与翻译", color = tokens.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        InlineSectionTitle("AI翻译接口")
        SettingsSegmentedControl(
            labels = listOf("本地 Ollama", "OpenAI 兼容"),
            selectedIndex = if (settings.translationEngine == AiTranslationEngine.OLLAMA) 0 else 1,
            onSelected = { index ->
                onAiEngineSelected(
                    if (index == 0) AiTranslationEngine.OLLAMA else AiTranslationEngine.DEEPSEEK,
                )
            },
        )
        Spacer(Modifier.height(9.dp))
        GroupedCard {
            when (settings.translationEngine) {
                AiTranslationEngine.OLLAMA -> {
                    SettingsActionRow(
                        icon = Icons.Default.Settings,
                        title = "接口地址",
                        value = compactEndpoint(settings.ollamaBaseUrl),
                        onClick = onEditAiOllamaBaseUrl,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Default.MusicNote,
                        title = "模型",
                        value = settings.ollamaModel,
                        onClick = onEditAiOllamaModel,
                    )
                    SettingsDivider()
                    SettingsValueRow(Icons.Default.LockOpen, "API Key", "本地无需")
                }
                AiTranslationEngine.DEEPSEEK -> {
                    SettingsActionRow(
                        icon = Icons.Default.Settings,
                        title = "接口地址",
                        value = compactEndpoint(settings.deepSeekBaseUrl),
                        onClick = onEditAiDeepSeekBaseUrl,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Default.MusicNote,
                        title = "模型",
                        value = settings.deepSeekModel,
                        onClick = onEditAiDeepSeekModel,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Default.LockOpen,
                        title = "API Key",
                        value = if (settings.deepSeekApiKey.isBlank()) "未设置" else "已设置",
                        onClick = onEditAiDeepSeekApiKey,
                    )
                }
            }
            SettingsDivider()
            SettingsSwitchIconRow(
                icon = Icons.Default.LockOpen,
                title = "成人内容直译",
                checked = settings.allowAdultContentTranslation,
                onClick = {
                    onAiAdultContentTranslationAllowedChange(!settings.allowAdultContentTranslation)
                },
                alt = true,
            )
        }
        Spacer(Modifier.height(16.dp))
        InlineSectionTitle("转写后端")
        SettingsSegmentedControl(
            labels = listOf("本机", "远程服务器"),
            selectedIndex = if (settings.transcriptionBackend == AiTranscriptionBackend.LOCAL) 0 else 1,
            onSelected = { index ->
                onAiTranscriptionBackendSelected(
                    if (index == 0) AiTranscriptionBackend.LOCAL else AiTranscriptionBackend.REMOTE,
                )
            },
        )
        Spacer(Modifier.height(9.dp))
        GroupedCard {
            when (settings.transcriptionBackend) {
                AiTranscriptionBackend.LOCAL -> {
                    WhisperModelSpec.ALL.forEachIndexed { index, spec ->
                        SettingsSelectableRow(
                            icon = Icons.Default.MusicNote,
                            title = spec.label,
                            value = formatUpdateBytes(spec.sizeBytes),
                            selected = settings.whisperModelId == spec.id,
                            onClick = { onAiWhisperModelSelected(spec.id) },
                        )
                        if (index != WhisperModelSpec.ALL.lastIndex) {
                            SettingsDivider()
                        }
                    }
                    SettingsDivider()
                    WhisperModelStatusRow(
                        state = state,
                        onDownload = onDownloadWhisperModel,
                        onCancel = onCancelWhisperModelDownload,
                        onDelete = onDeleteWhisperModel,
                    )
                }
                AiTranscriptionBackend.REMOTE -> {
                    SettingsActionRow(
                        icon = Icons.Default.UploadFile,
                        title = "服务器地址",
                        value = compactEndpoint(settings.remoteWhisperBaseUrl.ifBlank { "未设置" }),
                        onClick = onEditRemoteWhisperBaseUrl,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Default.MusicNote,
                        title = "模型",
                        value = settings.activeRemoteWhisperModel,
                        onClick = onEditRemoteWhisperModel,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Default.LockOpen,
                        title = "Bearer Token",
                        value = if (settings.remoteWhisperToken.isBlank()) "未设置" else "已设置",
                        onClick = onEditRemoteWhisperToken,
                    )
                    SettingsDivider()
                    RemoteWhisperTestRow(state.remoteWhisperTestStatus, onTestRemoteWhisperConnection)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LocalAmberTokens.current.label2,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun InlineSectionTitle(text: String) {
    Text(
        text = text,
        color = LocalAmberTokens.current.label2,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = LocalAmberTokens.current.separator,
        modifier = Modifier.padding(start = 64.dp),
    )
}

@Composable
private fun SettingsIcon(icon: ImageVector, alt: Boolean = false) {
    val tokens = LocalAmberTokens.current
    Surface(
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (alt) tokens.accent2Soft else tokens.accentSoft,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (alt) tokens.accent2 else tokens.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsValueRow(icon: ImageVector, title: String, value: String, valueAccent: Boolean = false) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 15.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(13.dp))
        Text(
            title,
            fontSize = 16.sp,
            color = tokens.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 16.sp,
            color = if (valueAccent) tokens.accent else tokens.label2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class RowTrailing {
    Chevron,
    External,
    None,
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    valueAccent: Boolean = false,
    showDot: Boolean = false,
    trailing: RowTrailing = RowTrailing.Chevron,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(start = 15.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(13.dp))
        Text(
            title,
            fontSize = 16.sp,
            color = tokens.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showDot) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(tokens.accent, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            value,
            fontSize = 16.sp,
            color = if (valueAccent) tokens.accent else tokens.label2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (trailing) {
            RowTrailing.Chevron -> {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = tokens.label3,
                    modifier = Modifier.size(19.dp),
                )
            }
            RowTrailing.External -> {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = tokens.label3,
                    modifier = Modifier.size(18.dp),
                )
            }
            RowTrailing.None -> Unit
        }
    }
}

@Composable
private fun SettingsSwitchIconRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    alt: Boolean = false,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(start = 15.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon, alt)
        Spacer(Modifier.width(13.dp))
        Text(
            title,
            fontSize = 16.sp,
            color = tokens.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onClick() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = tokens.switchOn,
                uncheckedTrackColor = tokens.switchOff,
                checkedThumbColor = Color.White,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SettingsSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(16.dp),
        color = tokens.cardBottom,
        border = BorderStroke(0.7.dp, tokens.separator),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val selected = selectedIndex == index
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelected(index) },
                    shape = RoundedCornerShape(13.dp),
                    color = if (selected) tokens.cardTop else Color.Transparent,
                    border = if (selected) BorderStroke(0.5.dp, tokens.separator) else null,
                    tonalElevation = 0.dp,
                    shadowElevation = if (selected) 2.dp else 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            color = if (selected) tokens.accent else tokens.label2,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSelectableRow(
    icon: ImageVector,
    title: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(start = 15.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(13.dp))
        Text(
            title,
            fontSize = 16.sp,
            color = tokens.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(value, fontSize = 14.sp, color = tokens.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val statusText = when {
        model.downloading -> "下载中 ${(model.progress * 100).toInt()}%"
        model.downloaded -> "已下载"
        model.error.isNotBlank() -> "下载失败"
        else -> "未下载"
    }
    val action = when {
        model.downloading -> "取消"
        model.downloaded -> "删除"
        else -> "下载"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(Icons.Default.Download, alt = model.error.isNotBlank())
            Spacer(Modifier.width(13.dp))
            Text(
                "模型状态",
                fontSize = 16.sp,
                color = tokens.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                statusText,
                fontSize = 15.sp,
                color = if (model.error.isNotBlank()) tokens.accent2 else tokens.label2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (model.downloading || model.error.isNotBlank()) {
            ProgressLine(
                progress = model.progress,
                failed = model.error.isNotBlank(),
                modifier = Modifier.padding(start = 49.dp, top = 13.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 49.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    action,
                    color = if (model.downloaded || model.downloading || model.error.isNotBlank()) tokens.accent2 else tokens.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RemoteWhisperTestRow(status: RemoteWhisperTestStatus, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    val checking = status is RemoteWhisperTestStatus.Checking
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(enabled = !checking, onClick = onClick)
            .padding(start = 15.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(Icons.Default.Settings)
        Spacer(Modifier.width(13.dp))
        Text("测试连接", fontSize = 16.sp, color = tokens.label, modifier = Modifier.weight(1f))
        when (status) {
            RemoteWhisperTestStatus.Idle -> {
                Text("未测试", fontSize = 16.sp, color = tokens.label2)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(19.dp))
            }
            RemoteWhisperTestStatus.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = tokens.accent,
                    trackColor = tokens.label3,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("测试中…", fontSize = 16.sp, color = tokens.label2)
            }
            is RemoteWhisperTestStatus.Success -> {
                Text(status.message, fontSize = 15.sp, color = tokens.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            is RemoteWhisperTestStatus.Failed -> {
                Text(status.message, fontSize = 15.sp, color = tokens.accent2, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            .height(64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 15.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(Icons.Default.Download)
        Spacer(Modifier.width(13.dp))
        Text("检查更新", fontSize = 16.sp, color = tokens.label, modifier = Modifier.weight(1f))
        when (status) {
            AppUpdateStatus.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = tokens.accent,
                    trackColor = tokens.label3,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("正在检查…", fontSize = 16.sp, color = tokens.label2)
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
                    fontSize = 16.sp,
                    color = tokens.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(19.dp))
            }
            AppUpdateStatus.UpToDate -> Text("已是最新", fontSize = 16.sp, color = tokens.label2)
            is AppUpdateStatus.Failed -> {
                Text(
                    text = status.message.ifBlank { "检查失败" },
                    fontSize = 16.sp,
                    color = tokens.accent2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(19.dp))
            }
            AppUpdateStatus.Idle -> {
                Text("未检查", fontSize = 16.sp, color = tokens.label2)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(19.dp))
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
        color = tokens.cardTop,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.5.dp, tokens.separator),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(Icons.Default.Download, alt = failed)
                Spacer(Modifier.width(13.dp))
                Text(
                    text = if (failed) "更新下载失败" else "正在下载 v${release.versionName}",
                    color = tokens.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
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
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.accent2Soft,
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
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatUpdateBytes(bytesDownloaded)} / ${formatUpdateBytes(totalBytes)}",
                    color = tokens.label2,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(progress: Float, failed: Boolean, modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(tokens.label3.copy(alpha = 0.24f), RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(if (failed) tokens.accent2 else tokens.accent, RoundedCornerShape(3.dp)),
        )
    }
}

private fun compactEndpoint(value: String): String {
    val cleaned = value
        .trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
    if (cleaned.isBlank()) {
        return "未设置"
    }
    return if (cleaned.length > 24) {
        "…" + cleaned.takeLast(23)
    } else {
        cleaned
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
