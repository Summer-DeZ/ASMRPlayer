package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.presentation.AppUpdateStatus
import io.github.summerdez.asmrplayer.presentation.SettingsUiState
import io.github.summerdez.asmrplayer.ui.components.GroupedCard
import io.github.summerdez.asmrplayer.ui.components.PageHeader
import io.github.summerdez.asmrplayer.domain.model.AppThemeMode
import io.github.summerdez.asmrplayer.ui.uiProbe

@Composable
fun SettingsTab(
    state: SettingsUiState,
    onToggleOverlay: () -> Unit,
    onUnlockOverlay: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onBinauralEnhancedChange: (Boolean) -> Unit,
    onCrossfadeEnabledChange: (Boolean) -> Unit,
    onWifiOnlyDownloadsChange: (Boolean) -> Unit,
    onRefreshStorageUsage: () -> Unit,
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
    onEditRemoteTranscriptionAddress: () -> Unit,
    onEditRemoteTranscriptionPort: () -> Unit,
    onEditRemoteWhisperModel: () -> Unit,
    onEditRemoteWhisperToken: () -> Unit,
    onTestRemoteWhisperConnection: () -> Unit,
    onDownloadWhisperModel: () -> Unit,
    onCancelWhisperModelDownload: () -> Unit,
    onDeleteWhisperModel: () -> Unit,
    aiSettingsOpen: Boolean,
    onAiSettingsOpenChange: (Boolean) -> Unit,
) {
    val settingsScrollState = rememberScrollState()
    val aiSettingsScrollState = rememberScrollState()
    val pageSlideEasing = CubicBezierEasing(0.20f, 0f, 0f, 1f)

    @Composable
    fun MainSettingsPage() {
        Column(
            Modifier
                .fillMaxSize()
                .uiProbe(
                    id = "settings.root",
                    label = "设置页根容器",
                    sourceHint = "SettingsScreen.kt",
                    metadata = mapOf("aiSettingsOpen" to aiSettingsOpen.toString()),
                ),
        ) {
            PageHeader(
                title = "设置",
                showMenu = false,
                menuExpanded = false,
                onMenuExpandedChange = {},
                onCreatePlaylist = {},
                onImportAudio = {},
                onImportFolder = {},
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(settingsScrollState)
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                GroupedCard {
                    SettingsSwitchIconRow(
                        icon = Icons.Default.Hearing,
                        title = "双耳增强",
                        checked = state.binauralEnhanced,
                        onClick = { onBinauralEnhancedChange(!state.binauralEnhanced) },
                        subtitle = "为双耳录音作品优化声场",
                    )
                    SettingsDivider()
                    SettingsSwitchIconRow(
                        icon = Icons.Default.MergeType,
                        title = "淡入淡出衔接",
                        checked = state.crossfadeEnabled,
                        onClick = { onCrossfadeEnabledChange(!state.crossfadeEnabled) },
                        subtitle = "作品之间平滑过渡",
                    )
                }

            Spacer(Modifier.height(22.dp))
            GroupedCard {
                SettingsSwitchIconRow(
                    icon = Icons.Default.Wifi,
                    title = "仅 Wi-Fi 下载",
                    checked = state.wifiOnlyDownloads,
                    onClick = { onWifiOnlyDownloadsChange(!state.wifiOnlyDownloads) },
                    subtitle = "避免使用蜂窝数据同步",
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = Icons.Default.Storage,
                    title = "已用存储",
                    value = state.storageUsedText,
                    trailing = RowTrailing.None,
                    subtitle = "离线作品缓存占用",
                    onClick = onRefreshStorageUsage,
                )
            }

            Spacer(Modifier.height(22.dp))
            GroupedCard {
                SettingsSegmentPreferenceRow(
                    icon = Icons.Default.DarkMode,
                    title = "主题",
                    labels = listOf("深色", "浅色", "系统"),
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
            }

            Spacer(Modifier.height(22.dp))
            GroupedCard {
                SettingsSwitchIconRow(Icons.Default.Subtitles, "悬浮字幕", state.overlayRequested, onToggleOverlay)
                SettingsDivider()
                SettingsSwitchIconRow(Icons.Default.LockOpen, "悬浮字幕锁定", state.overlayLocked, onUnlockOverlay)
            }

            Spacer(Modifier.height(22.dp))
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

            Spacer(Modifier.height(22.dp))
            GroupedCard {
                SettingsActionRow(
                    icon = Icons.Default.Subtitles,
                    title = "AI字幕生成设置",
                    value = "",
                    onClick = { onAiSettingsOpenChange(true) },
                )
            }

            Spacer(Modifier.height(22.dp))
            GroupedCard {
                SettingsValueRow(null, "版本", state.currentVersionName)
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
    }

    AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        targetState = aiSettingsOpen,
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            slideInHorizontally(
                animationSpec = tween(durationMillis = 320, easing = pageSlideEasing),
                initialOffsetX = { width -> direction * width },
            ) togetherWith slideOutHorizontally(
                animationSpec = tween(durationMillis = 320, easing = pageSlideEasing),
                targetOffsetX = { width -> -direction * width },
            ) using SizeTransform(clip = true)
        },
        label = "settings-ai-page-transition",
    ) { open ->
        if (open) {
            AiSettingsPage(
                state = state,
                scrollState = aiSettingsScrollState,
                onBack = { onAiSettingsOpenChange(false) },
                onAiTranscriptionBackendSelected = onAiTranscriptionBackendSelected,
                onAiEngineSelected = onAiEngineSelected,
                onEditAiOllamaBaseUrl = onEditAiOllamaBaseUrl,
                onEditAiOllamaModel = onEditAiOllamaModel,
                onEditAiDeepSeekBaseUrl = onEditAiDeepSeekBaseUrl,
                onEditAiDeepSeekModel = onEditAiDeepSeekModel,
                onEditAiDeepSeekApiKey = onEditAiDeepSeekApiKey,
                onAiAdultContentTranslationAllowedChange = onAiAdultContentTranslationAllowedChange,
                onAiWhisperModelSelected = onAiWhisperModelSelected,
                onEditRemoteTranscriptionAddress = onEditRemoteTranscriptionAddress,
                onEditRemoteTranscriptionPort = onEditRemoteTranscriptionPort,
                onEditRemoteWhisperModel = onEditRemoteWhisperModel,
                onEditRemoteWhisperToken = onEditRemoteWhisperToken,
                onTestRemoteWhisperConnection = onTestRemoteWhisperConnection,
                onDownloadWhisperModel = onDownloadWhisperModel,
                onCancelWhisperModelDownload = onCancelWhisperModelDownload,
                onDeleteWhisperModel = onDeleteWhisperModel,
            )
        } else {
            MainSettingsPage()
        }
    }
}
