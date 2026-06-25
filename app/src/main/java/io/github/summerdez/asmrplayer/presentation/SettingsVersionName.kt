package io.github.summerdez.asmrplayer.presentation

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal fun Context.installedVersionName(): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return packageInfo.versionName ?: "未知"
}
