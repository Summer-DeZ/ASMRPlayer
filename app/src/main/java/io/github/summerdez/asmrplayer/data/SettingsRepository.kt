package io.github.summerdez.asmrplayer.data

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
import android.content.Context

interface SettingsRepository {
    fun themeMode(): AppThemeMode
    fun setThemeMode(mode: AppThemeMode)
}

class AppSettingsRepository(context: Context) : SettingsRepository {
    private val appContext = context.applicationContext

    override fun themeMode(): AppThemeMode {
        return AppUi.themeMode()
    }

    override fun setThemeMode(mode: AppThemeMode) {
        AppUi.setThemeMode(appContext, mode)
    }
}
