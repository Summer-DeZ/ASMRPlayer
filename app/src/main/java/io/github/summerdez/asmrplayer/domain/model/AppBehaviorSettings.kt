package io.github.summerdez.asmrplayer.domain.model

data class AppBehaviorSettings(
    val binauralEnhanced: Boolean = true,
    val crossfadeEnabled: Boolean = true,
    val wifiOnlyDownloads: Boolean = true,
    val sleepFadeBeforeEndEnabled: Boolean = true,
)
