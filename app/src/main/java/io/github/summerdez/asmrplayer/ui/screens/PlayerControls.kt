package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.components.EqualizerIcon
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import io.github.summerdez.asmrplayer.ui.util.splitSubtitle
import kotlin.math.max

@Composable
internal fun PlayerTopBar(
    contextTitle: String,
    onClose: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe("player.top-bar", "播放器顶部栏", "PlayerControls.kt"),
    ) {
        Surface(
            modifier = Modifier
                .size(44.dp)
                .uiProbe("player.close", "播放器关闭按钮", "PlayerControls.kt")
                .clickable(onClick = onClose),
            shape = CircleShape,
            color = tokens.glass,
            border = BorderStroke(0.5.dp, tokens.separator),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "关闭", tint = tokens.label, modifier = Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "正在播放",
                color = tokens.label3,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                contextTitle,
                color = tokens.label2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Surface(
            modifier = Modifier
                .size(44.dp)
                .uiProbe("player.more", "播放器更多菜单按钮", "PlayerControls.kt")
                .clickable(onClick = onOpenMenu),
            shape = CircleShape,
            color = tokens.glass,
            border = BorderStroke(0.5.dp, tokens.separator),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = tokens.label, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun PlayerSubtitleDisplay(
    playbackState: PlaybackUiState,
    subtitlesVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    BoxWithConstraints(
        modifier
            .uiProbe("player.subtitle-area", "播放器字幕展示区", "PlayerControls.kt")
            .padding(top = 10.dp, bottom = 10.dp),
    ) {
        val lines = playbackState.subtitleLines
        val current = playbackState.subtitleIndex
        if (!subtitlesVisible) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SubtitlesOff,
                        contentDescription = null,
                        tint = tokens.labelFaint,
                        modifier = Modifier.size(34.dp),
                    )
                    Text(
                        "字幕已关闭",
                        color = tokens.label3,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        } else if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    playbackState.subtitleEmptyText,
                    color = tokens.label3,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            val listState = rememberLazyListState()
            val centerPad = maxHeight * 0.40f
            LaunchedEffect(current, lines.size) {
                if (current in lines.indices) {
                    listState.animateScrollToItem(current)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = centerPad),
                userScrollEnabled = false,
            ) {
                itemsIndexed(lines, key = { index, _ -> index }) { index, raw ->
                    val active = index == current
                    val parts = splitSubtitle(raw)
                    val color by animateColorAsState(
                        targetValue = if (active) tokens.label else tokens.label3,
                        animationSpec = tween(380),
                        label = "lyricColor",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (active) 1f else 0.86f,
                        animationSpec = tween(380),
                        label = "lyricScale",
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .uiProbe(
                                id = "player.subtitle-line:$index",
                                label = if (active) "当前字幕行" else "字幕行 $index",
                                sourceHint = "PlayerControls.kt",
                                metadata = mapOf(
                                    "index" to index.toString(),
                                    "active" to active.toString(),
                                ),
                            )
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (active) tokens.label.copy(alpha = 0.05f) else Color.Transparent)
                            .padding(horizontal = if (active) 16.dp else 0.dp)
                            .padding(vertical = if (active) 12.dp else 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            parts.first,
                            color = color,
                            fontSize = if (active) 22.sp else 16.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.Normal,
                            lineHeight = if (active) 32.sp else 25.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (active && parts.second.isNotEmpty()) {
                            Text(
                                parts.second,
                                color = tokens.accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Brush.verticalGradient(listOf(tokens.playerBase, Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, tokens.playerBase))),
            )
            EqualizerIcon(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .size(width = 22.dp, height = 16.dp),
            )
        }
    }
}

@Composable
internal fun PlayerTrackMeta(playbackState: PlaybackUiState) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe("player.track-meta", "播放器曲目信息", "PlayerControls.kt"),
        verticalAlignment = Alignment.Top,
    ) {
        CoverBox(playbackState.coverUri, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playbackState.audioTitle,
                color = tokens.label,
                fontSize = 25.sp,
                lineHeight = 29.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (playbackState.overlayRequested) {
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = tokens.accentSoft,
                border = BorderStroke(1.dp, tokens.accent.copy(alpha = 0.24f)),
            ) {
                Text(
                    "字幕",
                    color = tokens.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
    }
}
