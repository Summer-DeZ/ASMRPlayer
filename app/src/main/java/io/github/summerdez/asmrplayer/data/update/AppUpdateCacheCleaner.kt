package io.github.summerdez.asmrplayer.data.update

import java.io.File

internal object AppUpdateCacheCleaner {
    private const val UPDATES_DIR = "updates"
    private val apkNamePattern = Regex("""^ASMRPlayer-(.+)\.apk$""")

    fun prepareForDownload(cacheDir: File, targetVersionName: String): File {
        val updatesDir = updatesDir(cacheDir).apply { mkdirs() }
        deleteNamedFiles(
            updatesDir,
            filesToDeleteBeforeDownload(
                fileNames = updatesDir.listFiles()?.map { it.name }.orEmpty(),
                targetVersionName = targetVersionName,
            ),
        )
        return updatesDir
    }

    fun cleanInstalledVersion(cacheDir: File, installedVersionName: String) {
        val updatesDir = updatesDir(cacheDir)
        if (!updatesDir.isDirectory) {
            return
        }
        deleteNamedFiles(
            updatesDir,
            filesToDeleteForInstalledVersion(
                fileNames = updatesDir.listFiles()?.map { it.name }.orEmpty(),
                installedVersionName = installedVersionName,
            ),
        )
    }

    fun apkFile(updatesDir: File, versionName: String): File {
        return File(updatesDir, "ASMRPlayer-${AppVersionComparator.normalized(versionName)}.apk")
    }

    internal fun filesToDeleteBeforeDownload(
        fileNames: Iterable<String>,
        targetVersionName: String,
    ): Set<String> {
        val targetVersion = AppVersionComparator.normalized(targetVersionName)
        return fileNames.filterTo(mutableSetOf()) { fileName ->
            fileName.endsWith(".part") ||
                apkVersion(fileName)?.let { AppVersionComparator.normalized(it) != targetVersion } == true
        }
    }

    internal fun filesToDeleteForInstalledVersion(
        fileNames: Iterable<String>,
        installedVersionName: String,
    ): Set<String> {
        val installedVersion = AppVersionComparator.normalized(installedVersionName)
        if (!hasNumericVersion(installedVersion)) {
            return fileNames.filterTo(mutableSetOf()) { it.endsWith(".part") }
        }
        return fileNames.filterTo(mutableSetOf()) { fileName ->
            if (fileName.endsWith(".part")) {
                true
            } else {
                val version = apkVersion(fileName)
                version != null &&
                    hasNumericVersion(version) &&
                    AppVersionComparator.compare(version, installedVersion) <= 0
            }
        }
    }

    private fun updatesDir(cacheDir: File): File = File(cacheDir, UPDATES_DIR)

    private fun deleteNamedFiles(directory: File, fileNames: Set<String>) {
        if (fileNames.isEmpty()) {
            return
        }
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name in fileNames) {
                file.delete()
            }
        }
    }

    private fun apkVersion(fileName: String): String? {
        return apkNamePattern.matchEntire(fileName)?.groupValues?.getOrNull(1)
    }

    private fun hasNumericVersion(versionName: String): Boolean {
        return AppVersionComparator.normalized(versionName).any { it.isDigit() }
    }
}
