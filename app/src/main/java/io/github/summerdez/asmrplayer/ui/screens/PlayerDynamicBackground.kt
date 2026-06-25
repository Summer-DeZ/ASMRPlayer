package io.github.summerdez.asmrplayer.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class PlayerBackgroundColors(
    val base: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val fromCover: Boolean,
)

@Composable
internal fun rememberPlayerBackgroundColors(
    coverUri: String,
    defaultBase: Color,
    defaultPrimaryGlow: Color,
    defaultSecondaryGlow: Color,
): PlayerBackgroundColors {
    val context = LocalContext.current
    var extractedColors by remember(coverUri) { mutableStateOf<PlayerBackgroundColors?>(null) }
    val fallback = PlayerBackgroundColors(
        base = defaultBase,
        glowPrimary = defaultPrimaryGlow,
        glowSecondary = defaultSecondaryGlow,
        fromCover = false,
    )

    LaunchedEffect(context, coverUri, defaultBase, defaultPrimaryGlow, defaultSecondaryGlow) {
        extractedColors = null
        val normalizedCoverUri = coverUri.trim()
        if (normalizedCoverUri.isNotEmpty()) {
            extractedColors = extractPlayerBackgroundColors(
                context = context,
                coverUri = normalizedCoverUri,
                defaultBase = defaultBase,
                defaultPrimaryGlow = defaultPrimaryGlow,
                defaultSecondaryGlow = defaultSecondaryGlow,
            )
        }
    }

    return extractedColors ?: fallback
}

private suspend fun extractPlayerBackgroundColors(
    context: Context,
    coverUri: String,
    defaultBase: Color,
    defaultPrimaryGlow: Color,
    defaultSecondaryGlow: Color,
): PlayerBackgroundColors? = withContext(Dispatchers.IO) {
    val bitmap = decodeCoverBitmap(context, coverUri) ?: return@withContext null
    try {
        val palette = Palette.Builder(bitmap)
            .maximumColorCount(24)
            .resizeBitmapArea(16_384)
            .generate()
        val dominant = palette.dominantSwatch ?: palette.swatches.maxByOrNull { it.population }
        val primary = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: dominant
        val secondary = palette.mutedSwatch
            ?: palette.darkMutedSwatch
            ?: palette.lightMutedSwatch
            ?: dominant

        if (dominant == null) {
            null
        } else {
            PlayerBackgroundColors(
                base = dominant.toPlayerBase(defaultBase),
                glowPrimary = primary.toPlayerGlow(defaultPrimaryGlow),
                glowSecondary = secondary.toPlayerGlow(defaultSecondaryGlow),
                fromCover = true,
            )
        }
    } finally {
        bitmap.recycle()
    }
}

private fun decodeCoverBitmap(context: Context, coverUri: String): Bitmap? {
    val uri = runCatching { Uri.parse(coverUri) }.getOrNull() ?: return null
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeCoverBitmapWithImageDecoder(context, uri)
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
    }.getOrNull()
}

private fun decodeCoverBitmapWithImageDecoder(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val longestSide = maxOf(width, height)
        if (longestSide > 256 && width > 0 && height > 0) {
            val scale = 256f / longestSide.toFloat()
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}

private fun Palette.Swatch?.toPlayerBase(fallback: Color): Color {
    if (this == null) {
        return fallback
    }
    val hsl = hsl.copyOf()
    val darkSurface = fallback.luminance() < 0.5f
    hsl[1] = if (darkSurface) {
        (hsl[1] * 0.62f).coerceIn(0.16f, 0.54f)
    } else {
        (hsl[1] * 0.22f).coerceIn(0.04f, 0.18f)
    }
    hsl[2] = if (darkSurface) 0.11f else 0.94f
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Palette.Swatch?.toPlayerGlow(fallback: Color): Color {
    if (this == null) {
        return fallback
    }
    val hsl = hsl.copyOf()
    hsl[1] = (hsl[1] * 0.88f).coerceIn(0.22f, 0.72f)
    hsl[2] = hsl[2].coerceIn(0.34f, 0.68f)
    return Color(ColorUtils.HSLToColor(hsl))
}
