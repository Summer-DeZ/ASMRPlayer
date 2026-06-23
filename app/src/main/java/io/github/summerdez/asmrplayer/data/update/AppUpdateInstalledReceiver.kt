package io.github.summerdez.asmrplayer.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class AppUpdateInstalledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        context.cleanInstalledUpdateCache()
    }
}

internal fun Context.cleanInstalledUpdateCache() {
    val versionName = runCatching { installedVersionNameForUpdateCache() }.getOrDefault("")
    if (versionName.isBlank()) {
        return
    }
    AppUpdateCacheCleaner.cleanInstalledVersion(cacheDir, versionName)
}

private fun Context.installedVersionNameForUpdateCache(): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return packageInfo.versionName.orEmpty()
}
