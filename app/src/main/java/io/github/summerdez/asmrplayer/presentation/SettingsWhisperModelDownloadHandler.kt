package io.github.summerdez.asmrplayer.presentation

import io.github.summerdez.asmrplayer.data.ai.WhisperModelRepository
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SettingsWhisperModelDownloadHandler(
    private val whisperModelRepository: WhisperModelRepository,
    private val state: MutableStateFlow<SettingsUiState>,
    private val events: MutableSharedFlow<SettingsEvent>,
    private val scope: CoroutineScope,
) {
    private var whisperModelDownloadJob: Job? = null

    fun downloadWhisperModel() {
        val modelId = selectedWhisperModelId()
        val spec = WhisperModelSpec.byId(modelId)
        val current = state.value
        val modelState = whisperModelRepository.state(spec)
        if (modelState.downloaded) {
            state.update { it.copy(whisperModelState = modelState) }
            return
        }
        if (current.aiSubtitleSettings.whisperModelId == modelId && current.whisperModelState.downloading) {
            return
        }
        whisperModelDownloadJob?.cancel()
        whisperModelDownloadJob = scope.launch {
            try {
                state.update {
                    it.copy(whisperModelState = whisperModelRepository.state(spec).copy(downloading = true))
                }
                val modelState = whisperModelRepository.download(spec) { progress ->
                    state.update { it.copy(whisperModelState = progress) }
                }
                state.update { it.copy(whisperModelState = modelState) }
                events.emit(SettingsEvent.Message("${spec.label} 已下载"))
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    state.update { it.copy(whisperModelState = whisperModelRepository.state(spec)) }
                    return@launch
                }
                state.update {
                    it.copy(
                        whisperModelState = whisperModelRepository.state(spec).copy(
                            error = error.message ?: "模型下载失败",
                        ),
                    )
                }
            }
        }
    }

    fun cancelWhisperModelDownload() {
        whisperModelDownloadJob?.cancel()
        whisperModelDownloadJob = null
        val spec = selectedWhisperModelSpec()
        state.update { it.copy(whisperModelState = whisperModelRepository.state(spec)) }
    }

    fun deleteWhisperModel() {
        val spec = selectedWhisperModelSpec()
        whisperModelRepository.delete(spec)
        state.update { it.copy(whisperModelState = whisperModelRepository.state(spec)) }
    }

    private fun selectedWhisperModelSpec(): WhisperModelSpec {
        return WhisperModelSpec.byId(selectedWhisperModelId())
    }

    private fun selectedWhisperModelId(): String {
        return SettingsPendingAiSubtitleSelection.whisperModelId ?: state.value.aiSubtitleSettings.whisperModelId
    }
}
