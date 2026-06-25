package io.github.summerdez.asmrplayer.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MainTab(val title: String) {
    MEDIA("资料库"),
    SETTINGS("设置"),
    SLEEP("睡眠模式"),
    DLSITE("DLsite"),
}

class MainViewModel : ViewModel() {
    private val _selectedTab = MutableStateFlow(MainTab.MEDIA)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }
}
