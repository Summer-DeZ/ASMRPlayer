package io.github.summerdez.asmrplayer.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt

private const val UI_PROBE_LOG_TAG = "ASRM_UI_PROBE"

data class UiProbeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x <= right && y >= top && y <= bottom && width > 0f && height > 0f
    }
}

data class UiProbeTarget(
    val key: Int,
    val registeredOrder: Long,
    val id: String,
    val label: String,
    val sourceHint: String,
    val metadata: Map<String, String>,
    val bounds: UiProbeBounds,
)

data class UiProbeHit(
    val selected: UiProbeTarget?,
    val candidates: List<UiProbeTarget>,
)

data class UiProbeSelection(
    val schemaVersion: Int = 1,
    val capturedAtEpochMs: Long,
    val screen: String,
    val tapX: Float,
    val tapY: Float,
    val selected: UiProbeTarget,
    val candidates: List<UiProbeTarget>,
) {
    fun toJson(): String {
        return buildString {
            append("{\n")
            append("  \"schemaVersion\": ").append(schemaVersion).append(",\n")
            append("  \"capturedAtEpochMs\": ").append(capturedAtEpochMs).append(",\n")
            append("  \"screen\": ").appendJson(screen).append(",\n")
            append("  \"tap\": { \"x\": ").appendFloat(tapX).append(", \"y\": ").appendFloat(tapY).append(" },\n")
            append("  \"selected\": ")
            appendTarget(selected, indent = "  ")
            append(",\n")
            append("  \"candidates\": [")
            candidates.forEachIndexed { index, target ->
                if (index > 0) append(",")
                append("\n    ")
                appendTarget(target, indent = "    ")
            }
            if (candidates.isNotEmpty()) append("\n  ")
            append("]\n")
            append("}\n")
        }
    }
}

class UiProbeRegistry {
    private var nextKey = 1
    private var nextOrder = 1L
    private val targets = mutableStateMapOf<Int, UiProbeTarget>()

    fun allocateKey(): Int = nextKey++

    fun allocateOrder(): Long = nextOrder++

    fun update(target: UiProbeTarget) {
        targets[target.key] = target
    }

    fun unregister(key: Int) {
        targets.remove(key)
    }

    fun hit(offset: Offset): UiProbeHit {
        return UiProbeHitTester.hit(targets.values, offset.x, offset.y)
    }
}

object UiProbeHitTester {
    fun hit(targets: Collection<UiProbeTarget>, x: Float, y: Float): UiProbeHit {
        val candidates = targets
            .asSequence()
            .filter { it.bounds.contains(x, y) }
            .sortedWith(
                compareBy<UiProbeTarget> { it.bounds.area }
                    .thenByDescending { it.registeredOrder },
            )
            .toList()
        val visibleLayerCandidates = if (candidates.any { it.id == "player.root" }) {
            candidates.filter { it.id.startsWith("player.") }
        } else {
            candidates
        }
        return UiProbeHit(selected = visibleLayerCandidates.firstOrNull(), candidates = visibleLayerCandidates)
    }
}

private val LocalUiProbeRegistry = staticCompositionLocalOf<UiProbeRegistry?> { null }

fun Modifier.uiProbe(
    id: String,
    label: String,
    sourceHint: String,
    metadata: Map<String, String> = emptyMap(),
): Modifier = composed {
    val registry = LocalUiProbeRegistry.current
    if (registry == null) {
        this
    } else {
        val key = remember(registry) { registry.allocateKey() }
        val order = remember(registry) { registry.allocateOrder() }
        DisposableEffect(registry, key) {
            onDispose { registry.unregister(key) }
        }
        this.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInRoot()
            registry.update(
                UiProbeTarget(
                    key = key,
                    registeredOrder = order,
                    id = id,
                    label = label,
                    sourceHint = sourceHint,
                    metadata = metadata,
                    bounds = UiProbeBounds(
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                    ),
                ),
            )
        }
    }
}

@Composable
fun UiProbeHost(
    enabled: Boolean,
    screen: String,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val context = LocalContext.current
    val registry = remember { UiProbeRegistry() }
    var armed by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<UiProbeSelection?>(null) }

    CompositionLocalProvider(LocalUiProbeRegistry provides registry) {
        Box(Modifier.fillMaxSize()) {
            content()
            selection?.selected?.let { UiProbeHighlight(it) }
            if (armed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x33000000))
                        .pointerInput(registry, screen) {
                            detectTapGestures { offset ->
                                val hit = registry.hit(offset)
                                val selected = hit.selected
                                if (selected != null) {
                                    val nextSelection = UiProbeSelection(
                                        capturedAtEpochMs = System.currentTimeMillis(),
                                        screen = screen,
                                        tapX = offset.x,
                                        tapY = offset.y,
                                        selected = selected,
                                        candidates = hit.candidates.take(12),
                                    )
                                    selection = nextSelection
                                    UiProbeOutput.write(context, nextSelection)
                                } else {
                                    Log.i(UI_PROBE_LOG_TAG, "No target at x=${offset.x}, y=${offset.y}, screen=$screen")
                                }
                                armed = false
                            }
                        },
                )
            }
            UiProbeControl(
                armed = armed,
                selected = selection?.selected,
                onToggle = { armed = !armed },
                onClear = { selection = null },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 34.dp, end = 12.dp),
            )
            selection?.let { current ->
                UiProbeInfoSheet(
                    selection = current,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun UiProbeHighlight(target: UiProbeTarget) {
    val density = LocalDensity.current
    val bounds = target.bounds
    Box(
        modifier = Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(
                width = with(density) { bounds.width.toDp() },
                height = with(density) { bounds.height.toDp() },
            )
            .border(BorderStroke(2.dp, Color(0xFF8E8E93)), RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun UiProbeControl(
    armed: Boolean,
    selected: UiProbeTarget?,
    onToggle: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (armed) Color(0xFF8E8E93) else Color(0xCC1C1C1F),
        border = BorderStroke(1.dp, Color(0x66FFFFFF)),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = onToggle) {
                Text(
                    text = if (armed) "点选中" else "UI 探针",
                    color = if (armed) Color(0xFF111112) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (selected != null) {
                TextButton(onClick = onClear) {
                    Text("清除", color = if (armed) Color(0xFF111112) else Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun UiProbeInfoSheet(selection: UiProbeSelection, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 212.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xF21D1D20),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        shadowElevation = 18.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("选中区域", color = Color(0xFF8E8E93), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                selection.selected.label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            UiProbeInfoLine("ID", selection.selected.id)
            UiProbeInfoLine("来源", selection.selected.sourceHint)
            UiProbeInfoLine(
                "坐标",
                with(selection.selected.bounds) {
                    "x=${left.roundToInt()} y=${top.roundToInt()} w=${width.roundToInt()} h=${height.roundToInt()}"
                },
            )
            UiProbeInfoLine("屏幕", selection.screen)
        }
    }
}

@Composable
private fun UiProbeInfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = Color(0x99FFFFFF), fontSize = 12.sp, modifier = Modifier.width(42.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            color = Color(0xDDFFFFFF),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 0.dp),
        )
    }
}

private object UiProbeOutput {
    fun write(context: Context, selection: UiProbeSelection) {
        val json = selection.toJson()
        Log.i(UI_PROBE_LOG_TAG, json)
        runCatching {
            val dir = File(context.filesDir, "ui-probe")
            dir.mkdirs()
            File(dir, "latest-selection.json").writeText(json)
        }.onFailure { error ->
            Log.w(UI_PROBE_LOG_TAG, "Failed to write latest-selection.json", error)
        }
    }
}

private fun StringBuilder.appendTarget(target: UiProbeTarget, indent: String) {
    append("{\n")
    append(indent).append("  \"id\": ").appendJson(target.id).append(",\n")
    append(indent).append("  \"label\": ").appendJson(target.label).append(",\n")
    append(indent).append("  \"sourceHint\": ").appendJson(target.sourceHint).append(",\n")
    append(indent).append("  \"boundsPx\": ")
    appendBounds(target.bounds)
    append(",\n")
    append(indent).append("  \"metadata\": ")
    appendStringMap(target.metadata, indent)
    append("\n")
    append(indent).append("}")
}

private fun StringBuilder.appendBounds(bounds: UiProbeBounds) {
    append("{ \"left\": ").appendFloat(bounds.left)
    append(", \"top\": ").appendFloat(bounds.top)
    append(", \"right\": ").appendFloat(bounds.right)
    append(", \"bottom\": ").appendFloat(bounds.bottom)
    append(", \"width\": ").appendFloat(bounds.width)
    append(", \"height\": ").appendFloat(bounds.height)
    append(" }")
}

private fun StringBuilder.appendStringMap(values: Map<String, String>, indent: String) {
    if (values.isEmpty()) {
        append("{}")
        return
    }
    append("{")
    values.entries.forEachIndexed { index, entry ->
        if (index > 0) append(",")
        append("\n").append(indent).append("    ")
        appendJson(entry.key).append(": ").appendJson(entry.value)
    }
    append("\n").append(indent).append("  }")
}

private fun StringBuilder.appendJson(value: String): StringBuilder {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
    return this
}

private fun StringBuilder.appendFloat(value: Float): StringBuilder {
    append(((value * 10f).roundToInt() / 10f).toString())
    return this
}
