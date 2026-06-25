package io.github.summerdez.asmrplayer.presentation

import io.github.summerdez.asmrplayer.data.SettingsRepository
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.remoteTranscriptionBaseUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal object SettingsPendingAiSubtitleSelection {
    @Volatile
    var whisperModelId: String? = null
}

internal class SettingsAiSettingsHandler(
    private val settingsRepository: SettingsRepository,
    private val state: MutableStateFlow<SettingsUiState>,
    private val scope: CoroutineScope,
) {
    private var pendingRemoteTranscriptionAddress: String? = null
    private var pendingRemoteTranscriptionPort: String? = null

    fun setAiTranslationEngine(engine: AiTranslationEngine) {
        scope.launch {
            settingsRepository.setAiTranslationEngine(engine)
        }
    }

    fun setAiTranscriptionBackend(backend: AiTranscriptionBackend) {
        scope.launch {
            settingsRepository.setAiTranscriptionBackend(backend)
        }
        resetRemoteWhisperTestStatus()
    }

    fun setAiOllamaBaseUrl(value: String) {
        scope.launch {
            settingsRepository.setAiOllamaBaseUrl(value)
        }
    }

    fun setAiOllamaModel(value: String) {
        scope.launch {
            settingsRepository.setAiOllamaModel(value)
        }
    }

    fun setAiDeepSeekBaseUrl(value: String) {
        scope.launch {
            settingsRepository.setAiDeepSeekBaseUrl(value)
        }
    }

    fun setAiDeepSeekModel(value: String) {
        scope.launch {
            settingsRepository.setAiDeepSeekModel(value)
        }
    }

    fun setAiDeepSeekApiKey(value: String) {
        scope.launch {
            settingsRepository.setAiDeepSeekApiKey(value)
        }
    }

    fun setAiWhisperModelId(value: String) {
        SettingsPendingAiSubtitleSelection.whisperModelId = value
        scope.launch {
            settingsRepository.setAiWhisperModelId(value)
        }
    }

    fun setAiRemoteWhisperBaseUrl(value: String) {
        pendingRemoteTranscriptionAddress = null
        pendingRemoteTranscriptionPort = null
        scope.launch {
            settingsRepository.setAiRemoteWhisperBaseUrl(value)
        }
        resetRemoteWhisperTestStatus()
    }

    fun setAiRemoteTranscriptionAddress(value: String) {
        pendingRemoteTranscriptionAddress = value
        val port = pendingRemoteTranscriptionPort ?: state.value.aiSubtitleSettings.remoteTranscriptionPort
        scope.launch {
            settingsRepository.setAiRemoteWhisperBaseUrl(remoteTranscriptionBaseUrl(value, port))
        }
        resetRemoteWhisperTestStatus()
    }

    fun setAiRemoteTranscriptionPort(value: String) {
        pendingRemoteTranscriptionPort = value
        val address = pendingRemoteTranscriptionAddress ?: state.value.aiSubtitleSettings.remoteTranscriptionAddress
        scope.launch {
            settingsRepository.setAiRemoteWhisperBaseUrl(remoteTranscriptionBaseUrl(address, value))
        }
        resetRemoteWhisperTestStatus()
    }

    fun setAiRemoteWhisperModel(value: String) {
        scope.launch {
            settingsRepository.setAiRemoteWhisperModel(value)
        }
        resetRemoteWhisperTestStatus()
    }

    fun setAiRemoteWhisperToken(value: String) {
        scope.launch {
            settingsRepository.setAiRemoteWhisperToken(value)
        }
        resetRemoteWhisperTestStatus()
    }

    fun setAiAdultContentTranslationAllowed(value: Boolean) {
        scope.launch {
            settingsRepository.setAiAdultContentTranslationAllowed(value)
        }
    }

    private fun resetRemoteWhisperTestStatus() {
        state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Idle) }
    }
}
