package io.github.summerdez.asmrplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import io.github.summerdez.asmrplayer.presentation.RemoteWhisperTestStatus
import io.github.summerdez.asmrplayer.presentation.SettingsUiState
import io.github.summerdez.asmrplayer.ui.components.GroupedCard
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe

@Composable
internal fun AiSettingsPage(
    state: SettingsUiState,
    scrollState: ScrollState,
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
    onEditRemoteTranscriptionAddress: () -> Unit,
    onEditRemoteTranscriptionPort: () -> Unit,
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
            .uiProbe(
                id = "settings.ai.root",
                label = "AI 设置页根容器",
                sourceHint = "SettingsAiPage.kt",
                metadata = mapOf(
                    "translationEngine" to settings.translationEngine.name,
                    "transcriptionBackend" to settings.transcriptionBackend.name,
                ),
            )
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .uiProbe("settings.ai.header", "AI 设置二级页顶部栏", "SettingsAiPage.kt"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(42.dp)
                    .uiProbe("settings.ai.back", "AI 设置返回按钮", "SettingsAiPage.kt")
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = tokens.label,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "设置",
                color = tokens.label,
                fontSize = 34.sp,
                lineHeight = 34.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(14.dp))
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
        Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(22.dp))
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
        Spacer(Modifier.height(10.dp))
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
                        value = compactEndpoint(settings.remoteTranscriptionAddress.ifBlank { "未设置" }),
                        onClick = onEditRemoteTranscriptionAddress,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Default.MusicNote,
                        title = "端口",
                        value = settings.remoteTranscriptionPort.ifBlank { "未设置" },
                        onClick = onEditRemoteTranscriptionPort,
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
            .uiProbe(
                id = "settings.whisper-model-status",
                label = "本机 Whisper 模型状态",
                sourceHint = "SettingsAiPage.kt",
                metadata = mapOf(
                    "status" to statusText,
                    "action" to action,
                    "downloading" to model.downloading.toString(),
                    "downloaded" to model.downloaded.toString(),
                    "error" to model.error,
                ),
            )
            .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(Icons.Default.Download, alt = model.error.isNotBlank())
            Spacer(Modifier.width(14.dp))
            Text(
                "模型状态",
                fontSize = SettingsRowTitleFontSize,
                fontWeight = SettingsRowTitleFontWeight,
                color = tokens.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                statusText,
                fontSize = SettingsRowValueFontSize,
                color = if (model.error.isNotBlank()) tokens.accent2 else tokens.label3,
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
                color = tokens.label3,
                fontSize = SettingsRowValueFontSize,
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
            .height(66.dp)
            .uiProbe(
                id = "settings.remote-whisper-test",
                label = "远程转写连接测试",
                sourceHint = "SettingsAiPage.kt",
                metadata = mapOf("status" to status::class.simpleName.orEmpty()),
            )
            .clickable(enabled = !checking, onClick = onClick)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(Icons.Default.Settings)
        Spacer(Modifier.width(14.dp))
        Text(
            "测试连接",
            fontSize = SettingsRowTitleFontSize,
            fontWeight = SettingsRowTitleFontWeight,
            color = tokens.label,
            modifier = Modifier.weight(1f),
        )
        when (status) {
            RemoteWhisperTestStatus.Idle -> {
                Text("未测试", fontSize = SettingsRowValueFontSize, color = tokens.label3)
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
                Text("测试中…", fontSize = SettingsRowValueFontSize, color = tokens.label3)
            }
            is RemoteWhisperTestStatus.Success -> {
                Text(status.message, fontSize = SettingsRowValueFontSize, color = tokens.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            is RemoteWhisperTestStatus.Failed -> {
                Text(status.message, fontSize = SettingsRowValueFontSize, color = tokens.accent2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
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
