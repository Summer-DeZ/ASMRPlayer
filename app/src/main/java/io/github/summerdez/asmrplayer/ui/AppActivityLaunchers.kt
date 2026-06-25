package io.github.summerdez.asmrplayer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import io.github.summerdez.asmrplayer.data.files.DocumentFiles
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.presentation.DlsiteViewModel
import io.github.summerdez.asmrplayer.presentation.LibraryViewModel
import io.github.summerdez.asmrplayer.ui.activity.DlsiteLoginActivity

internal class AppActivityLaunchers(
    private val context: Context,
    private val audioLauncher: ActivityResultLauncher<Intent>,
    private val folderLauncher: ActivityResultLauncher<Intent>,
    private val coverLauncher: ActivityResultLauncher<Intent>,
    private val subtitleLauncher: ActivityResultLauncher<Intent>,
    private val loginLauncher: ActivityResultLauncher<Intent>,
    private val onStartCoverPicker: (Playlist) -> Unit,
    private val onStartSubtitlePicker: (Playlist, TrackItem) -> Unit,
) {
    fun importAudio() {
        audioLauncher.launch(DocumentFiles.audioPickerIntent(true))
    }

    fun importFolder() {
        folderLauncher.launch(DocumentFiles.folderPickerIntent())
    }

    fun pickCover(playlist: Playlist) {
        onStartCoverPicker(playlist)
        coverLauncher.launch(DocumentFiles.imagePickerIntent())
    }

    fun pickSubtitle(playlist: Playlist, track: TrackItem) {
        onStartSubtitlePicker(playlist, track)
        subtitleLauncher.launch(DocumentFiles.subtitlePickerIntent())
    }

    fun login() {
        loginLauncher.launch(Intent(context, DlsiteLoginActivity::class.java))
    }
}

@Composable
internal fun rememberAppActivityLaunchers(
    context: Context,
    libraryViewModel: LibraryViewModel,
    dlsiteViewModel: DlsiteViewModel,
    toast: (String) -> Unit,
): AppActivityLaunchers {
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            return@rememberLauncherForActivityResult
        }
        libraryViewModel.addAudioUris(context, DocumentFiles.urisFromResult(result.data))
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            toast("未选择文件夹")
            return@rememberLauncherForActivityResult
        }
        libraryViewModel.importFolder(context, data, uri)
    }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            libraryViewModel.handleCoverUri(context, uri)
            toast("封面已设置")
        } else {
            libraryViewModel.clearCoverPicker()
        }
    }
    val subtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            libraryViewModel.handleSubtitleUri(context, uri)
        } else {
            libraryViewModel.clearSubtitlePicker()
        }
    }
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        dlsiteViewModel.refresh()
        if (dlsiteViewModel.state.value.loggedIn) {
            dlsiteViewModel.syncWorks()
        }
    }

    return AppActivityLaunchers(
        context = context,
        audioLauncher = audioLauncher,
        folderLauncher = folderLauncher,
        coverLauncher = coverLauncher,
        subtitleLauncher = subtitleLauncher,
        loginLauncher = loginLauncher,
        onStartCoverPicker = libraryViewModel::startCoverPicker,
        onStartSubtitlePicker = libraryViewModel::startSubtitlePicker,
    )
}
