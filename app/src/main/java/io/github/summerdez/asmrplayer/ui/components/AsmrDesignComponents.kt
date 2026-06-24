package io.github.summerdez.asmrplayer.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class AsmrIconName {
    Library,
    Moon,
    CloudDownload,
    Settings,
    Play,
    Pause,
    Close,
    ChevronDown,
    Ellipsis,
    ListMusic,
}

@Composable
fun WaveBars(
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    color: Color = LocalAmberTokens.current.accent,
) {
    val transition = rememberInfiniteTransition(label = "wave-bars")
    val easeCalm = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
    val bar0 by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = easeCalm),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0),
        ),
        label = "wave-bar-0",
    )
    val bar1 by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 940, easing = easeCalm),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(180),
        ),
        label = "wave-bar-1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1180, easing = easeCalm),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(420),
        ),
        label = "wave-bar-2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = easeCalm),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(100),
        ),
        label = "wave-bar-3",
    )
    val scales = if (playing) {
        floatArrayOf(bar0, bar1, bar2, bar3)
    } else {
        floatArrayOf(0.28f, 0.28f, 0.28f, 0.28f)
    }

    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val bars = scales.size
        val barWidth = size.width / (bars + (bars - 1) * 1.33f)
        val gap = barWidth * 1.33f
        val totalWidth = barWidth * bars + gap * (bars - 1)
        var x = (size.width - totalWidth) / 2f
        scales.forEach { scale ->
            val barHeight = size.height * scale.coerceIn(0.28f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
            x += barWidth + gap
        }
    }
}

@Composable
fun AsmrIcon(
    name: AsmrIconName,
    modifier: Modifier = Modifier,
    tint: Color = LocalAmberTokens.current.label,
    strokeWidth: Float = 1.75f,
) {
    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val scale = min(size.width, size.height) / 24f
        val offsetX = (size.width - 24f * scale) / 2f
        val offsetY = (size.height - 24f * scale) / 2f
        fun x(value: Float) = offsetX + value * scale
        fun y(value: Float) = offsetY + value * scale
        fun p(px: Float, py: Float) = Offset(x(px), y(py))
        fun sz(width: Float, height: Float) = Size(width * scale, height * scale)
        val stroke = Stroke(
            width = strokeWidth * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        when (name) {
            AsmrIconName.Library -> {
                drawLine(tint, p(4f, 4.5f), p(4f, 19.5f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(8f, 4.5f), p(8f, 19.5f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(12f, 4.5f), p(12f, 19.5f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(4f, 5f), p(12f, 5f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(4f, 19f), p(20f, 19f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(16f, 6f), p(20f, 18.5f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(13.8f, 6.8f), p(17.7f, 19f), strokeWidth * scale, StrokeCap.Round)
            }

            AsmrIconName.Moon -> {
                val moon = Path().apply {
                    moveTo(x(12.1f), y(3f))
                    cubicTo(x(9.9f), y(4.3f), x(8.5f), y(6.7f), x(8.5f), y(9.4f))
                    cubicTo(x(8.5f), y(14.3f), x(12.5f), y(18.3f), x(17.4f), y(18.3f))
                    cubicTo(x(18.8f), y(18.3f), x(20.1f), y(18f), x(21.2f), y(17.4f))
                    cubicTo(x(19.7f), y(20.1f), x(16.8f), y(21.5f), x(13.6f), y(21.1f))
                    cubicTo(x(7.7f), y(20.4f), x(3.2f), y(15.4f), x(3.8f), y(9.5f))
                    cubicTo(x(4.2f), y(5.7f), x(7.4f), y(2.8f), x(11.2f), y(2.8f))
                    cubicTo(x(11.5f), y(2.8f), x(11.8f), y(2.9f), x(12.1f), y(3f))
                }
                drawPath(moon, tint, style = stroke)
            }

            AsmrIconName.CloudDownload -> {
                val cloud = Path().apply {
                    moveTo(x(17.6f), y(18f))
                    lineTo(x(7f), y(18f))
                    cubicTo(x(4.8f), y(18f), x(3f), y(16.3f), x(3f), y(14.1f))
                    cubicTo(x(3f), y(12.2f), x(4.4f), y(10.6f), x(6.3f), y(10.2f))
                    cubicTo(x(7f), y(7.7f), x(9.2f), y(6f), x(11.8f), y(6f))
                    cubicTo(x(14.6f), y(6f), x(16.8f), y(8f), x(17.2f), y(10.7f))
                    cubicTo(x(19.4f), y(10.8f), x(21f), y(12.4f), x(21f), y(14.4f))
                    cubicTo(x(21f), y(16.5f), x(19.6f), y(18f), x(17.6f), y(18f))
                }
                drawPath(cloud, tint, style = stroke)
                drawLine(tint, p(12f, 12f), p(12f, 21f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(8.8f, 17.6f), p(12f, 21f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(15.2f, 17.6f), p(12f, 21f), strokeWidth * scale, StrokeCap.Round)
            }

            AsmrIconName.Settings -> {
                val center = p(12f, 12f)
                drawCircle(tint, radius = 3.3f * scale, center = center, style = stroke)
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45).toDouble())
                    val start = Offset(
                        center.x + cos(angle).toFloat() * 6.8f * scale,
                        center.y + sin(angle).toFloat() * 6.8f * scale,
                    )
                    val end = Offset(
                        center.x + cos(angle).toFloat() * 9.5f * scale,
                        center.y + sin(angle).toFloat() * 9.5f * scale,
                    )
                    drawLine(tint, start, end, strokeWidth * scale, StrokeCap.Round)
                }
                drawCircle(tint, radius = 8.2f * scale, center = center, style = Stroke(width = 1.1f * scale))
            }

            AsmrIconName.Play -> {
                val play = Path().apply {
                    moveTo(x(8f), y(5f))
                    lineTo(x(19f), y(12f))
                    lineTo(x(8f), y(19f))
                    close()
                }
                drawPath(play, tint, style = stroke)
            }

            AsmrIconName.Pause -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(6.5f, 5f),
                    size = sz(4f, 14f),
                    cornerRadius = CornerRadius(1.4f * scale, 1.4f * scale),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = p(13.5f, 5f),
                    size = sz(4f, 14f),
                    cornerRadius = CornerRadius(1.4f * scale, 1.4f * scale),
                    style = stroke,
                )
            }

            AsmrIconName.Close -> {
                drawLine(tint, p(6f, 6f), p(18f, 18f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(18f, 6f), p(6f, 18f), strokeWidth * scale, StrokeCap.Round)
            }

            AsmrIconName.ChevronDown -> {
                drawLine(tint, p(6f, 9f), p(12f, 15f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(18f, 9f), p(12f, 15f), strokeWidth * scale, StrokeCap.Round)
            }

            AsmrIconName.Ellipsis -> {
                drawCircle(tint, radius = 1.15f * scale, center = p(5f, 12f))
                drawCircle(tint, radius = 1.15f * scale, center = p(12f, 12f))
                drawCircle(tint, radius = 1.15f * scale, center = p(19f, 12f))
            }

            AsmrIconName.ListMusic -> {
                drawLine(tint, p(8f, 6f), p(21f, 6f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(8f, 12f), p(21f, 12f), strokeWidth * scale, StrokeCap.Round)
                drawLine(tint, p(8f, 18f), p(17f, 18f), strokeWidth * scale, StrokeCap.Round)
                drawCircle(tint, radius = 1.15f * scale, center = p(4f, 6f))
                drawCircle(tint, radius = 1.15f * scale, center = p(4f, 12f))
                drawPath(
                    Path().apply {
                        moveTo(x(17f), y(16f))
                        lineTo(x(17f), y(21f))
                        cubicTo(x(17f), y(22.2f), x(15.8f), y(22.8f), x(14.6f), y(22.2f))
                    },
                    tint,
                    style = stroke,
                )
            }
        }
    }
}
