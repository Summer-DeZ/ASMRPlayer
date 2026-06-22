package io.github.summerdez.asmrplayer.di

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
import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AppContainer(private val application: Application) {
    val database: AsrmDatabase by lazy {
        AsrmDatabase.get(application)
    }

    val dlsiteApi: DlsiteApi by lazy {
        createDlsiteApi()
    }

    val libraryRepository: LibraryRepository by lazy {
        RoomLibraryRepository(application, database)
    }

    val dlsiteRepository: DlsiteRepository by lazy {
        RoomDlsiteRepository(database, dlsiteApi)
    }

    val settingsRepository: SettingsRepository by lazy {
        AppSettingsRepository(application)
    }

    val playbackCommands: PlaybackCommandClient by lazy {
        PlaybackCommandClient(application)
    }

    val viewModelFactory: ViewModelProvider.Factory by lazy {
        ASMRViewModelFactory(application, this)
    }
}

object AppGraph {
    @JvmStatic
    fun container(context: Context): AppContainer {
        return (context.applicationContext as ASMRPlayerApplication).appContainer
    }
}

private class ASMRViewModelFactory(
    private val application: Application,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel() as T
            modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
                LibraryViewModel(application, container.libraryRepository) as T
            modelClass.isAssignableFrom(PlaybackViewModel::class.java) ->
                PlaybackViewModel(application, container.libraryRepository, container.playbackCommands) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(application, container.settingsRepository, container.playbackCommands) as T
            modelClass.isAssignableFrom(SleepTimerViewModel::class.java) ->
                SleepTimerViewModel(container.playbackCommands) as T
            modelClass.isAssignableFrom(DlsiteViewModel::class.java) ->
                DlsiteViewModel(application, container.dlsiteRepository, container.libraryRepository) as T
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
