package io.github.summerdez.asmrplayer.di

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.summerdez.asmrplayer.data.AppSettingsRepository
import io.github.summerdez.asmrplayer.data.AsrmDatabase
import io.github.summerdez.asmrplayer.data.DlsiteApi
import io.github.summerdez.asmrplayer.data.DlsiteDownloadStateStore
import io.github.summerdez.asmrplayer.data.DlsiteLocalStore
import io.github.summerdez.asmrplayer.data.DlsiteRepository
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.data.RoomDlsiteLocalStore
import io.github.summerdez.asmrplayer.data.RoomDlsiteRepository
import io.github.summerdez.asmrplayer.data.RoomLibraryRepository
import io.github.summerdez.asmrplayer.data.SettingsRepository
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleTaskStateStore
import io.github.summerdez.asmrplayer.data.createDlsiteApi
import io.github.summerdez.asmrplayer.data.download.DlsiteDownloadQueueRepository
import io.github.summerdez.asmrplayer.data.download.RoomDlsiteDownloadQueueRepository
import io.github.summerdez.asmrplayer.data.files.LibraryFileImportUseCase
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadStateStore
import io.github.summerdez.asmrplayer.data.update.AppUpdateRepository
import io.github.summerdez.asmrplayer.data.update.GitHubAppUpdateRepository
import io.github.summerdez.asmrplayer.playback.PlaybackCommandClient
import io.github.summerdez.asmrplayer.playback.PlaybackPlaylistResolver
import io.github.summerdez.asmrplayer.presentation.AiSubtitleTaskViewModel
import io.github.summerdez.asmrplayer.presentation.DlsiteViewModel
import io.github.summerdez.asmrplayer.presentation.LibraryViewModel
import io.github.summerdez.asmrplayer.presentation.MainViewModel
import io.github.summerdez.asmrplayer.presentation.PlaybackViewModel
import io.github.summerdez.asmrplayer.presentation.SettingsViewModel
import io.github.summerdez.asmrplayer.presentation.SleepTimerViewModel

data class AppUpdateDownloadServiceDependencies(
    val updateRepository: AppUpdateRepository,
    val appUpdateDownloadStateStore: AppUpdateDownloadStateStore,
)

data class AiSubtitleGenerationServiceDependencies(
    val settingsRepository: SettingsRepository,
    val libraryRepository: LibraryRepository,
    val aiSubtitleTaskStateStore: AiSubtitleTaskStateStore,
)

data class DlsiteDownloadServiceDependencies(
    val dlsiteRepository: DlsiteRepository,
    val libraryRepository: LibraryRepository,
    val dlsiteApi: DlsiteApi,
    val dlsiteDownloadStateStore: DlsiteDownloadStateStore,
)

data class PlaybackServiceDependencies(
    val playbackPlaylistResolver: PlaybackPlaylistResolver,
)

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

    val libraryFileImportUseCase: LibraryFileImportUseCase<Uri> by lazy {
        LibraryFileImportUseCase(libraryRepository)
    }

    val dlsiteDownloadQueueRepository: DlsiteDownloadQueueRepository by lazy {
        RoomDlsiteDownloadQueueRepository(database)
    }

    val dlsiteLocalStore: DlsiteLocalStore by lazy {
        RoomDlsiteLocalStore(database)
    }

    val dlsiteRepository: DlsiteRepository by lazy {
        RoomDlsiteRepository(dlsiteLocalStore, dlsiteApi, dlsiteDownloadQueueRepository, dlsiteDownloadStateStore)
    }

    val settingsRepository: SettingsRepository by lazy {
        AppSettingsRepository(application, database.appSettingsDao())
    }

    val updateRepository: AppUpdateRepository by lazy {
        GitHubAppUpdateRepository(application.cacheDir)
    }

    val appUpdateDownloadStateStore: AppUpdateDownloadStateStore by lazy {
        AppUpdateDownloadStateStore()
    }

    val aiSubtitleTaskStateStore: AiSubtitleTaskStateStore by lazy {
        AiSubtitleTaskStateStore()
    }

    val dlsiteDownloadStateStore: DlsiteDownloadStateStore by lazy {
        DlsiteDownloadStateStore()
    }

    val playbackCommands: PlaybackCommandClient by lazy {
        PlaybackCommandClient(application)
    }

    val playbackPlaylistResolver: PlaybackPlaylistResolver by lazy {
        PlaybackPlaylistResolver(libraryRepository)
    }

    val appUpdateDownloadServiceDependencies: AppUpdateDownloadServiceDependencies by lazy {
        AppUpdateDownloadServiceDependencies(updateRepository, appUpdateDownloadStateStore)
    }

    val aiSubtitleGenerationServiceDependencies: AiSubtitleGenerationServiceDependencies by lazy {
        AiSubtitleGenerationServiceDependencies(settingsRepository, libraryRepository, aiSubtitleTaskStateStore)
    }

    val dlsiteDownloadServiceDependencies: DlsiteDownloadServiceDependencies by lazy {
        DlsiteDownloadServiceDependencies(dlsiteRepository, libraryRepository, dlsiteApi, dlsiteDownloadStateStore)
    }

    val playbackServiceDependencies: PlaybackServiceDependencies by lazy {
        PlaybackServiceDependencies(playbackPlaylistResolver)
    }

    val viewModelFactory: ViewModelProvider.Factory by lazy {
        ASMRViewModelFactory(application, this)
    }
}

object AppGraph {
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
            modelClass.isAssignableFrom(AiSubtitleTaskViewModel::class.java) ->
                AiSubtitleTaskViewModel(container.aiSubtitleTaskStateStore) as T
            modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
                LibraryViewModel(application, container.libraryRepository, container.libraryFileImportUseCase) as T
            modelClass.isAssignableFrom(PlaybackViewModel::class.java) ->
                PlaybackViewModel(application, container.libraryRepository, container.playbackCommands) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    application,
                    container.settingsRepository,
                    container.playbackCommands,
                    container.updateRepository,
                    container.appUpdateDownloadStateStore,
                ) as T
            modelClass.isAssignableFrom(SleepTimerViewModel::class.java) ->
                SleepTimerViewModel(container.playbackCommands) as T
            modelClass.isAssignableFrom(DlsiteViewModel::class.java) ->
                DlsiteViewModel(application, container.dlsiteRepository, container.libraryRepository) as T
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
