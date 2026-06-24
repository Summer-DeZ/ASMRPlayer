package io.github.summerdez.asmrplayer

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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun fromNameFallsBackToDarkForUnknownValues() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromName("light"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromName("dark"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromName("system"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromName("unknown"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromName(null))
    }

    @Test
    fun explicitModeUpdatesRuntimePalette() {
        val previousMode = AppUi.themeMode()
        try {
            AppUi.setThemeMode(null, AppThemeMode.DARK)

            assertEquals(AppThemeMode.DARK, AppUi.themeMode())
            assertFalse(AppUi.isLightTheme())
            assertEquals(0xFF0A0A0B.toInt(), AppUi.BG)
            assertEquals(0xFFF5F5F7.toInt(), AppUi.LABEL)

            AppUi.setThemeMode(null, AppThemeMode.LIGHT)

            assertEquals(AppThemeMode.LIGHT, AppUi.themeMode())
            assertTrue(AppUi.isLightTheme())
            assertEquals(0xFFF5F5F7.toInt(), AppUi.BG)
            assertEquals(0xFF0F0F11.toInt(), AppUi.LABEL)
        } finally {
            AppUi.setThemeMode(null, previousMode)
        }
    }

    @Test
    fun systemModeWithoutContextKeepsRequestedModeAndUsesDarkFallback() {
        val previousMode = AppUi.themeMode()
        try {
            AppUi.setThemeMode(null, AppThemeMode.SYSTEM)

            assertEquals(AppThemeMode.SYSTEM, AppUi.themeMode())
            assertFalse(AppUi.isLightTheme())
            assertEquals(0xFF0A0A0B.toInt(), AppUi.BG)
        } finally {
            AppUi.setThemeMode(null, previousMode)
        }
    }
}
