package io.github.summerdez.asmrplayer.ui.theme

import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*
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
    val separator: Color,
    val glass: Color,
    val bar: Color,
    val solidBar: Color,
    val sheet: Color,
    val playerBase: Color,
)

val LocalAmberTokens = staticCompositionLocalOf { amberDarkTokens() }

fun amberDarkTokens() = AmberTokens(
    accent = Color(0xFFE0A26B),
    accentDark = Color(0xFFC98A52),
    accent2 = Color(0xFFD98C7A),
    accentSoft = Color(0x26E0A26B),
    accent2Soft = Color(0x29D98C7A),
    glow = Color(0x42E0A26B),
    switchOn = Color(0xFFE0A26B),
    switchOff = Color(0xFF3A322A),
    bg = Color(0xFF17120E),
    grouped = Color(0xFF17120E),
    card = Color(0xFF221A14),
    cardTop = Color(0xFF221A13),
    cardBottom = Color(0xFF1D1610),
    coverTop = Color(0xFF4A3829),
    coverBottom = Color(0xFF2A1F16),
    gray5 = Color(0xFF2C231B),
    label = Color(0xFFF6F1EA),
    label2 = Color(0x8CF6F1EA),
    label3 = Color(0x4DF6F1EA),
    separator = Color(0x29BEA078),
    glass = Color(0x9E1E1710),
    bar = Color(0xDB1E1710),
    solidBar = Color(0xFF15100B),
    sheet = Color(0xF01D1610),
    playerBase = Color(0xFF140F0B),
)

fun amberLightTokens() = AmberTokens(
    accent = Color(0xFFC47A3E),
    accentDark = Color(0xFFA9632E),
    accent2 = Color(0xFFC06B58),
    accentSoft = Color(0x24C47A3E),
    accent2Soft = Color(0x21C06B58),
    glow = Color(0x33C47A3E),
    switchOn = Color(0xFFC47A3E),
    switchOff = Color(0xFFE2D8C8),
    bg = Color(0xFFFBF6EF),
    grouped = Color(0xFFF3EBE0),
    card = Color.White,
    cardTop = Color(0xFFFFFFFF),
    cardBottom = Color(0xFFFAF4EC),
    coverTop = Color(0xFFECE0CD),
    coverBottom = Color(0xFFDCC9AD),
    gray5 = Color(0xFFEFE6D8),
    label = Color(0xFF2A2018),
    label2 = Color(0x943C2D1E),
    label3 = Color(0x523C2D1E),
    separator = Color(0x29785A3C),
    glass = Color(0xB8FBF6EF),
    bar = Color(0xE6FBF6EF),
    solidBar = Color(0xFFFBF6EF),
    sheet = Color(0xF2FFFFFF),
    playerBase = Color(0xFFFBF1E6),
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
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
