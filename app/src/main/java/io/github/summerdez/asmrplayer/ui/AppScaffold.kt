package io.github.summerdez.asmrplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.summerdez.asmrplayer.BuildConfig
import io.github.summerdez.asmrplayer.presentation.MainTab
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.components.BottomPlaybackArea
import io.github.summerdez.asmrplayer.ui.components.PageHeader
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens

@Composable
fun AppScaffold(
    selectedTab: MainTab,
    playbackState: PlaybackUiState,
    showLibraryMenu: Boolean,
    playerOpen: Boolean,
    queueOpen: Boolean,
    downloadManagerOpen: Boolean,
    activeAiSubtitleTrackId: String?,
    updateDialogOpen: Boolean,
    installPromptOpen: Boolean,
    onLibraryMenuExpandedChange: (Boolean) -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportAudio: () -> Unit,
    onImportFolder: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayClicked: () -> Unit,
    onNextClicked: () -> Unit,
    onTabSelected: (MainTab) -> Unit,
    content: @Composable () -> Unit,
    dialogHost: @Composable () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val pageBackground = if (selectedTab == MainTab.SETTINGS || selectedTab == MainTab.SLEEP) {
        tokens.grouped
    } else {
        tokens.bg
    }
    val uiProbeScreen = buildList {
        add(selectedTab.title)
        if (playerOpen) add("全屏播放器")
        if (queueOpen) add("播放队列")
        if (downloadManagerOpen) add("下载管理")
        if (activeAiSubtitleTrackId != null) add("AI 字幕进度")
        if (updateDialogOpen) add("更新详情")
        if (installPromptOpen) add("安装更新")
    }.joinToString(" / ")

    UiProbeHost(enabled = BuildConfig.UI_PROBE_ENABLED, screen = uiProbeScreen) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pageBackground)
                .uiProbe("app.root", "应用根容器", "AppScaffold.kt"),
        ) {
            Scaffold(
                containerColor = pageBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    BottomPlaybackArea(
                        playbackState = playbackState,
                        selectedTab = selectedTab,
                        onOpenPlayer = onOpenPlayer,
                        onPlayClicked = onPlayClicked,
                        onNextClicked = onNextClicked,
                        onTabSelected = onTabSelected,
                    )
                },
            ) { innerPadding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(pageBackground),
                ) {
                    PageHeader(
                        title = selectedTab.title,
                        showMenu = selectedTab == MainTab.MEDIA,
                        menuExpanded = showLibraryMenu,
                        onMenuExpandedChange = onLibraryMenuExpandedChange,
                        onCreatePlaylist = onCreatePlaylist,
                        onImportAudio = onImportAudio,
                        onImportFolder = onImportFolder,
                    )
                    Box(Modifier.weight(1f)) {
                        content()
                    }
                }
            }

            dialogHost()
        }
    }
}
