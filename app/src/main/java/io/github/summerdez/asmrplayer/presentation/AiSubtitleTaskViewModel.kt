package io.github.summerdez.asmrplayer.presentation

import androidx.lifecycle.ViewModel
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleTaskStateStore

class AiSubtitleTaskViewModel(
    store: AiSubtitleTaskStateStore,
) : ViewModel() {
    val tasks = store.tasks
}
