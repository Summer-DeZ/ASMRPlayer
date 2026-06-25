package io.github.summerdez.asmrplayer.ui.theme

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.summerdez.asmrplayer.domain.model.AppThemeMode
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

data class AmberTokens(
    val accent: Color,
    val accentDark: Color,
    val accent2: Color,
    val accentSoft: Color,
    val accent2Soft: Color,
    val glow: Color,
    val switchOn: Color,
    val switchOff: Color,
    val bg: Color,
    val grouped: Color,
    val card: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val coverTop: Color,
    val coverBottom: Color,
    val gray5: Color,
    val label: Color,
    val label2: Color,
    val label3: Color,
    val labelFaint: Color,
    val separator: Color,
    val glass: Color,
    val bar: Color,
    val solidBar: Color,
    val sheet: Color,
    val playerBase: Color,
)

val LocalAmberTokens = staticCompositionLocalOf { amberDarkTokens() }

fun amberDarkTokens() = AmberTokens(
    accent = Color(0xFFF0936A),
    accentDark = Color(0xFFE27A52),
    accent2 = Color(0xFFB4A7F2),
    accentSoft = Color(0x33F0936A),
    accent2Soft = Color(0x299A8AEC),
    glow = Color(0x33F0936A),
    switchOn = Color(0xFFF0936A),
    switchOff = Color(0xFF313135),
    bg = Color(0xFF0A0A0B),
    grouped = Color(0xFF0A0A0B),
    card = Color(0xFF141416),
    cardTop = Color(0xFF1A1A1D),
    cardBottom = Color(0xFF0F0F11),
    coverTop = Color(0xFF524A6E),
    coverBottom = Color(0xFF242427),
    gray5 = Color(0xFF242427),
    label = Color(0xFFF5F5F7),
    label2 = Color(0xFFB6B6BD),
    label3 = Color(0xFF65656C),
    labelFaint = Color(0xFF45454B),
    separator = Color(0x0FFFFFFF),
    glass = Color(0xA8161619),
    bar = Color(0xBD0A0A0B),
    solidBar = Color(0xFF0A0A0B),
    sheet = Color(0xFF0F0F11),
    playerBase = Color(0xFF0A0A0B),
)

fun amberLightTokens() = AmberTokens(
    accent = Color(0xFFE27A52),
    accentDark = Color(0xFFC9623C),
    accent2 = Color(0xFF7E6FD6),
    accentSoft = Color(0x26E27A52),
    accent2Soft = Color(0x247E6FD6),
    glow = Color(0x24E27A52),
    switchOn = Color(0xFFE27A52),
    switchOff = Color(0xFFE2E2E6),
    bg = Color(0xFFF5F5F7),
    grouped = Color(0xFFEDEDF1),
    card = Color(0xFFFFFFFF),
    cardTop = Color(0xFFFFFFFF),
    cardBottom = Color(0xFFF5F5F7),
    coverTop = Color(0xFFE2E2E6),
    coverBottom = Color(0xFFCFC6F7),
    gray5 = Color(0xFFE2E2E6),
    label = Color(0xFF0F0F11),
    label2 = Color(0xFF45454B),
    label3 = Color(0xFF8A8A92),
    labelFaint = Color(0xFFB6B6BD),
    separator = Color(0x1F0A0A0B),
    glass = Color(0xD9F5F5F7),
    bar = Color(0xEEF5F5F7),
    solidBar = Color(0xFFF5F5F7),
    sheet = Color(0xF2FFFFFF),
    playerBase = Color(0xFFF5F5F7),
)

@Composable
fun ASMRPlayerTheme(themeMode: AppThemeMode, content: @Composable () -> Unit) {
    val dark = themeMode != AppThemeMode.LIGHT
    val tokens = if (dark) amberDarkTokens() else amberLightTokens()
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = tokens.accent,
            secondary = tokens.accent2,
            background = tokens.bg,
            surface = tokens.card,
            surfaceVariant = tokens.gray5,
            onBackground = tokens.label,
            onSurface = tokens.label,
            onSurfaceVariant = tokens.label2,
            outline = tokens.separator,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            secondary = tokens.accent2,
            background = tokens.bg,
            surface = tokens.card,
            surfaceVariant = tokens.gray5,
            onBackground = tokens.label,
            onSurface = tokens.label,
            onSurfaceVariant = tokens.label2,
            outline = tokens.separator,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAmberTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = androidx.compose.material3.Typography(
                displayLarge = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                headlineLarge = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                titleLarge = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                ),
                bodyLarge = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                labelLarge = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                ),
            ),
            content = content,
        )
    }
}
