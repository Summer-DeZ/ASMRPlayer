package io.github.summerdez.asmrplayer.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.summerdez.asmrplayer.data.SettingsRepository
import kotlinx.coroutines.runBlocking

internal suspend fun SettingsRepository.canStartDlsiteDownload(context: Context): Boolean {
    return !appBehaviorSettings().wifiOnlyDownloads || context.isConnectedToWifi()
}

internal fun SettingsRepository.canStartDlsiteDownloadBlocking(context: Context): Boolean {
    return runBlocking { canStartDlsiteDownload(context) }
}

private fun Context.isConnectedToWifi(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
