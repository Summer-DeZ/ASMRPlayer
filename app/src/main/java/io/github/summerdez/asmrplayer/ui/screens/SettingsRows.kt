package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.ui.components.noRippleClickable
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.theme.amberSwitchColors
import io.github.summerdez.asmrplayer.ui.uiProbe

internal val SettingsRowTitleFontSize = 15.sp
internal val SettingsRowTitleFontWeight = FontWeight.SemiBold
internal val SettingsRowValueFontSize = 13.sp

@Composable
internal fun InlineSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = LocalAmberTokens.current.label3,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        color = LocalAmberTokens.current.separator,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
internal fun SettingsIcon(icon: ImageVector, alt: Boolean = false) {
    val tokens = LocalAmberTokens.current
    Box(
        modifier = Modifier.size(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (alt) tokens.accent2 else Color(0xFFF2759F),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
internal fun SettingsValueRow(icon: ImageVector?, title: String, value: String, valueAccent: Boolean = false) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .uiProbe(
                id = "settings.value-row:$title",
                label = "设置值行：$title",
                sourceHint = "SettingsRows.kt",
                metadata = mapOf("value" to value, "valueAccent" to valueAccent.toString()),
            )
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsIcon(icon)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            fontSize = SettingsRowTitleFontSize,
            fontWeight = SettingsRowTitleFontWeight,
            color = tokens.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = SettingsRowValueFontSize,
            color = if (valueAccent) tokens.accent else tokens.label3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal enum class RowTrailing {
    Chevron,
    External,
    None,
}

@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    valueAccent: Boolean = false,
    showDot: Boolean = false,
    trailing: RowTrailing = RowTrailing.Chevron,
    subtitle: String = "",
) {
    val tokens = LocalAmberTokens.current
    val rowHeight = if (subtitle.isBlank()) 66.dp else 76.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .uiProbe(
                id = "settings.action-row:$title",
                label = "设置操作行：$title",
                sourceHint = "SettingsRows.kt",
                metadata = mapOf(
                    "value" to value,
                    "subtitle" to subtitle,
                    "valueAccent" to valueAccent.toString(),
                    "showDot" to showDot.toString(),
                    "trailing" to trailing.name,
                ),
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = SettingsRowTitleFontSize,
                fontWeight = SettingsRowTitleFontWeight,
                color = tokens.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.5.sp,
                    color = tokens.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (showDot) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(tokens.accent, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (value.isNotBlank()) {
            Text(
                value,
                fontSize = SettingsRowValueFontSize,
                color = if (valueAccent) tokens.accent else tokens.label3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
internal fun SettingsSwitchIconRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    alt: Boolean = false,
    subtitle: String = "",
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .uiProbe(
                id = "settings.switch-row:$title",
                label = "设置开关行：$title",
                sourceHint = "SettingsRows.kt",
                metadata = mapOf(
                    "checked" to checked.toString(),
                    "subtitle" to subtitle,
                    "alt" to alt.toString(),
                ),
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon, alt)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            fontSize = SettingsRowTitleFontSize,
            fontWeight = SettingsRowTitleFontWeight,
            color = tokens.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onClick() },
            colors = amberSwitchColors(),
        )
    }
}

@Composable
internal fun SettingsSegmentPreferenceRow(
    icon: ImageVector,
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .uiProbe(
                id = "settings.segment-row:$title",
                label = "设置分段行：$title",
                sourceHint = "SettingsRows.kt",
                metadata = mapOf(
                    "selectedIndex" to selectedIndex.toString(),
                    "selectedLabel" to labels.getOrElse(selectedIndex) { "" },
                ),
            )
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            fontSize = SettingsRowTitleFontSize,
            fontWeight = SettingsRowTitleFontWeight,
            color = tokens.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        SettingsSegmentedControl(
            labels = labels,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun SettingsSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .uiProbe(
                id = "settings.segmented-control:${labels.joinToString("|")}",
                label = "设置分段控件：${labels.joinToString(" / ")}",
                sourceHint = "SettingsRows.kt",
                metadata = mapOf(
                    "selectedIndex" to selectedIndex.toString(),
                    "selectedLabel" to labels.getOrElse(selectedIndex) { "" },
                ),
            ),
        shape = RoundedCornerShape(24.dp),
        color = tokens.label.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, tokens.separator),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemCount = labels.size.coerceAtLeast(1)
            val selectedOptionIndex = selectedIndex.coerceIn(0, itemCount - 1)
            val contentPadding = 4.dp
            val itemSpacing = 5.dp
            val itemWidth = (maxWidth - contentPadding * 2 - itemSpacing * (itemCount - 1).toFloat()) / itemCount.toFloat()
            val selectedOffsetX = contentPadding + (itemWidth + itemSpacing) * selectedOptionIndex.toFloat()
            val animatedOffsetX = animateDpAsState(
                targetValue = selectedOffsetX,
                animationSpec = tween(durationMillis = 220),
                label = "settings-segment-outline",
            )

            Box(
                modifier = Modifier
                    .offset(x = animatedOffsetX.value, y = contentPadding)
                    .width(itemWidth)
                    .height(maxHeight - contentPadding * 2)
                    .background(Color.Transparent, RoundedCornerShape(20.dp))
                    .border(BorderStroke(1.dp, Color(0xFFF2759F)), RoundedCornerShape(20.dp)),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    val selected = selectedIndex == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .uiProbe(
                                id = "settings.segment-option:$label",
                                label = "设置分段选项：$label",
                                sourceHint = "SettingsRows.kt",
                                metadata = mapOf(
                                    "index" to index.toString(),
                                    "selected" to selected.toString(),
                                ),
                            )
                            .noRippleClickable { onSelected(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (selected) Color(0xFFF2759F) else tokens.label2,
                            fontSize = 13.sp,
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
internal fun SettingsSelectableRow(
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
            .height(66.dp)
            .uiProbe(
                id = "settings.selectable-row:$title",
                label = "设置选择行：$title",
                sourceHint = "SettingsRows.kt",
                metadata = mapOf("value" to value, "selected" to selected.toString()),
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            fontSize = SettingsRowTitleFontSize,
            fontWeight = SettingsRowTitleFontWeight,
            color = tokens.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(value, fontSize = SettingsRowValueFontSize, color = tokens.label3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        Text(if (selected) "✓" else "", fontSize = 17.sp, color = tokens.accent, fontWeight = FontWeight.Bold)
    }
}
