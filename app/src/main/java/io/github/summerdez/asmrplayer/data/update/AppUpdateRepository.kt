package io.github.summerdez.asmrplayer.data.update

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class AppUpdateRelease(
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
)

data class AppUpdateDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
)

data class GitHubReleasePayload(
    val tagName: String,
    val releaseNotes: String,
    val assets: List<GitHubReleaseAssetPayload>,
)

data class GitHubReleaseAssetPayload(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

sealed class AppUpdateCheckResult {
    data class Available(val release: AppUpdateRelease) : AppUpdateCheckResult()
    object UpToDate : AppUpdateCheckResult()
}

interface AppUpdateRepository {
    suspend fun checkLatestRelease(currentVersionName: String): AppUpdateCheckResult

    suspend fun downloadReleaseApk(
        release: AppUpdateRelease,
        onProgress: (AppUpdateDownloadProgress) -> Unit,
    ): File
}

class GitHubAppUpdateRepository(
    private val cacheDir: File,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) : AppUpdateRepository {
    override suspend fun checkLatestRelease(currentVersionName: String): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("检查更新失败：HTTP ${response.code}")
            }
            val release = GitHubReleaseParser.parseLatestRelease(body)
            if (AppVersionComparator.isNewer(release.versionName, currentVersionName)) {
                AppUpdateCheckResult.Available(release)
            } else {
                AppUpdateCheckResult.UpToDate
            }
        }
    }

    override suspend fun downloadReleaseApk(
        release: AppUpdateRelease,
        onProgress: (AppUpdateDownloadProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = AppUpdateCacheCleaner.prepareForDownload(cacheDir, release.versionName)
        val target = AppUpdateCacheCleaner.apkFile(updatesDir, release.versionName)
        val partial = File(updatesDir, "${target.name}.part")

        val request = Request.Builder()
            .url(release.apkDownloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        val call = client.newCall(request)
        val coroutineContext = currentCoroutineContext()
        val job = coroutineContext[Job]
        val cancellationHandle = job?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
            }
        }
        var completed = false
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("下载更新失败：HTTP ${response.code}")
                }
                val responseBody = response.body ?: throw IOException("下载更新失败：响应为空")
                val totalBytes = responseBody.contentLength()
                    .takeIf { it > 0L }
                    ?: release.apkSizeBytes.takeIf { it > 0L }
                    ?: 0L
                var bytesDownloaded = 0L
                val startedAtMs = System.currentTimeMillis()
                responseBody.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) {
                                break
                            }
                            output.write(buffer, 0, read)
                            bytesDownloaded += read
                            onProgress(
                                AppUpdateDownloadProgress(
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes,
                                    speedBytesPerSecond = averageSpeedBytesPerSecond(bytesDownloaded, startedAtMs),
                                ),
                            )
                        }
                    }
                }
                coroutineContext.ensureActive()
                if (target.exists()) {
                    target.delete()
                }
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                completed = true
                onProgress(
                    AppUpdateDownloadProgress(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        speedBytesPerSecond = averageSpeedBytesPerSecond(bytesDownloaded, startedAtMs),
                    ),
                )
                target
            }
        } catch (error: IOException) {
            if (job?.isCancelled == true || call.isCanceled()) {
                throw CancellationException("下载已取消").also { it.initCause(error) }
            }
            throw error
        } finally {
            cancellationHandle?.dispose()
            if (!completed) {
                partial.delete()
            }
        }
    }

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Summer-DeZ/ASMRPlayer/releases/latest"
        private const val USER_AGENT = "ASMRPlayer-Android"
    }
}

private fun averageSpeedBytesPerSecond(bytesDownloaded: Long, startedAtMs: Long): Long {
    val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
    return ((bytesDownloaded.coerceAtLeast(0L) * 1000L) / elapsedMs).coerceAtLeast(0L)
}

object GitHubReleaseParser {
    fun parseLatestRelease(json: String): AppUpdateRelease {
        val root = JSONObject(json)
        return releaseFromPayload(
            GitHubReleasePayload(
                tagName = root.optString("tag_name").trim(),
                releaseNotes = root.optString("body").trim(),
                assets = assetsFromJson(root.optJSONArray("assets") ?: JSONArray()),
            ),
        )
    }

    fun releaseFromPayload(payload: GitHubReleasePayload): AppUpdateRelease {
        val versionName = AppVersionComparator.normalized(payload.tagName)
        if (versionName.isEmpty()) {
            throw IOException("Release 缺少 tag_name")
        }
        val asset = findApkAsset(payload.assets)
            ?: throw IOException("Release assets 中没有 APK")
        return AppUpdateRelease(
            versionName = versionName,
            tagName = payload.tagName,
            releaseNotes = payload.releaseNotes,
            apkName = asset.name,
            apkDownloadUrl = asset.downloadUrl,
            apkSizeBytes = asset.sizeBytes,
        )
    }

    private fun assetsFromJson(assets: JSONArray): List<GitHubReleaseAssetPayload> {
        val payloads = mutableListOf<GitHubReleaseAssetPayload>()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            payloads += GitHubReleaseAssetPayload(
                name = asset.optString("name").trim(),
                downloadUrl = asset.optString("browser_download_url").trim(),
                sizeBytes = asset.optLong("size", 0L).coerceAtLeast(0L),
            )
        }
        return payloads
    }

    private fun findApkAsset(assets: List<GitHubReleaseAssetPayload>): GitHubReleaseAssetPayload? {
        return assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) && asset.downloadUrl.startsWith("http")
        }
    }
}

object AppVersionComparator {
    fun normalized(version: String): String {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("+")
            .trim()
    }

    fun isNewer(candidate: String, current: String): Boolean {
        return compare(candidate, current) > 0
    }

    fun compare(left: String, right: String): Int {
        val leftParts = numericParts(left)
        val rightParts = numericParts(right)
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxSize) {
            val l = leftParts.getOrElse(index) { 0 }
            val r = rightParts.getOrElse(index) { 0 }
            if (l != r) {
                return l.compareTo(r)
            }
        }
        return 0
    }

    private fun numericParts(version: String): List<Int> {
        return normalized(version)
            .split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() ?: 0 }
    }
}
