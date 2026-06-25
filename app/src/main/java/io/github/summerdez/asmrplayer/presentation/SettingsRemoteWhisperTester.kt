package io.github.summerdez.asmrplayer.presentation

import io.github.summerdez.asmrplayer.data.ai.RemoteWhisperTranscriber
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SettingsRemoteWhisperTester(
    private val state: MutableStateFlow<SettingsUiState>,
    private val events: MutableSharedFlow<SettingsEvent>,
    private val scope: CoroutineScope,
    private val remoteWhisperTranscriber: RemoteWhisperTranscriber = RemoteWhisperTranscriber(),
) {
    fun testConnection() {
        if (state.value.remoteWhisperTestStatus is RemoteWhisperTestStatus.Checking) {
            return
        }
        scope.launch {
            val settings = state.value.aiSubtitleSettings
            state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Checking) }
            try {
                val health = remoteWhisperTranscriber.checkHealth(settings)
                if (!health.modelsReady) {
                    val message = "服务可达，模型未就绪：${health.displaySummary}"
                    state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Failed(message)) }
                    events.emit(SettingsEvent.Message(message))
                    return@launch
                }
                val message = "OK"
                state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Success(message)) }
                events.emit(SettingsEvent.Message(message))
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Idle) }
                    throw error
                }
                val message = error.message ?: "远程转写服务连接失败"
                state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Failed(message)) }
                events.emit(SettingsEvent.Message(message))
            }
        }
    }
}
