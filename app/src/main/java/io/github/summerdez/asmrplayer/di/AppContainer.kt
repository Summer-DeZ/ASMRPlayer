package io.github.summerdez.asmrplayer.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.summerdez.asmrplayer.data.AppSettingsRepository
import io.github.summerdez.asmrplayer.data.AsrmDatabase
import io.github.summerdez.asmrplayer.data.DlsiteApi
import io.github.summerdez.asmrplayer.data.DlsiteRepository
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.data.RoomDlsiteRepository
import io.github.summerdez.asmrplayer.data.RoomLibraryRepository
import io.github.summerdez.asmrplayer.data.SettingsRepository
import io.github.summerdez.asmrplayer.data.createDlsiteApi
import io.github.summerdez.asmrplayer.data.update.AppUpdateRepository
import io.github.summerdez.asmrplayer.data.update.GitHubAppUpdateRepository
import io.github.summerdez.asmrplayer.playback.PlaybackCommandClient
import io.github.summerdez.asmrplayer.presentation.DlsiteViewModel
import io.github.summerdez.asmrplayer.presentation.LibraryViewModel
import io.github.summerdez.asmrplayer.presentation.MainViewModel
import io.github.summerdez.asmrplayer.presentation.PlaybackViewModel
import io.github.summerdez.asmrplayer.presentation.SettingsViewModel
import io.github.summerdez.asmrplayer.presentation.SleepTimerViewModel

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
        AppSettingsRepository(application, database.appSettingsDao())
    }

    val updateRepository: AppUpdateRepository by lazy {
        GitHubAppUpdateRepository(application.cacheDir)
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
                SettingsViewModel(
                    application,
                    container.settingsRepository,
                    container.playbackCommands,
                    container.updateRepository,
                ) as T
            modelClass.isAssignableFrom(SleepTimerViewModel::class.java) ->
                SleepTimerViewModel(container.playbackCommands) as T
            modelClass.isAssignableFrom(DlsiteViewModel::class.java) ->
                DlsiteViewModel(application, container.dlsiteRepository, container.libraryRepository) as T
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
