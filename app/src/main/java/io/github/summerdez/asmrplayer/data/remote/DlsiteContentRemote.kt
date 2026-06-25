package io.github.summerdez.asmrplayer.data.remote

import android.text.TextUtils
import io.github.summerdez.asmrplayer.domain.DlsiteDownloadPlanner
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class DlsiteContentRemote(private val httpClient: DlsiteHttpClient) {
    @Throws(IOException::class)
    fun downloadTo(work: DlsiteWork, targetFile: File) {
        val downloadUrl = resolveDownloadUrl(work)
        if (TextUtils.isEmpty(downloadUrl)) {
            throw IOException("没有找到下载入口")
        }

        val parent = targetFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("无法创建下载目录")
        }

        httpClient.execute(
            downloadUrl,
            work.detailUrl,
            "application/zip,application/octet-stream,*/*",
            "GET",
            null,
            DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.READ_TIMEOUT_MS,
        ).use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载失败: HTTP ${response.code}")
            }
            val responseBody = response.body ?: throw IOException("下载失败: 响应为空")
            BufferedInputStream(responseBody.byteStream()).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) {
                            break
                        }
                        DlsiteRemoteFiles.throwIfInterrupted()
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption> {
        val workId = work.workId
        if (TextUtils.isEmpty(workId)) {
            throw IOException("作品编号为空")
        }
        signDownloadCookies(workId)
        return DlsiteDownloadPlanner.optionsFor(fetchZiptree(workId))
    }

    @Throws(IOException::class)
    fun downloadWorkFiles(work: DlsiteWork, workDir: File, downloadOptionId: String): List<File> {
        return downloadWorkFiles(work, workDir, downloadOptionId, null)
    }

    @Throws(IOException::class)
    fun downloadWorkFiles(
        work: DlsiteWork,
        workDir: File,
        downloadOptionId: String,
        progressListener: DlsiteContentProgressListener?,
    ): List<File> {
        val workId = work.workId
        if (TextUtils.isEmpty(workId)) {
            throw IOException("作品编号为空")
        }
        if (!workDir.exists() && !workDir.mkdirs() && !workDir.isDirectory) {
            throw IOException("无法创建作品目录")
        }

        signDownloadCookies(workId)
        val ziptree = fetchZiptree(workId)
        if (ziptree.audioFiles.isEmpty()) {
            throw IOException("DLsite Play 没有返回可下载的音频文件")
        }
        val selectedContentFiles = selectedContentFiles(ziptree, downloadOptionId)
        if (selectedContentFiles.isEmpty()) {
            throw IOException("所选版本没有可下载的音频文件")
        }
        val signParams = fetchDownloadSignParams(workId)
        val revision = if (ziptree.revision.isEmpty()) {
            DlsiteRemoteConstants.DEFAULT_REVISION
        } else {
            ziptree.revision
        }

        val audioFiles = ArrayList<File>()
        val usedTargets = HashSet<String>()
        for (contentFile in selectedContentFiles) {
            DlsiteRemoteFiles.throwIfInterrupted()
            val audioFile = DlsiteRemoteFiles.uniqueTarget(
                DlsiteRemoteFiles.localFileFor(workDir, contentFile.displayPath),
                usedTargets,
            )
            downloadSignedContentFile(
                signedContentUrl(workId, contentFile.contentPath, revision, signParams),
                audioFile,
                "application/octet-stream,audio/*,*/*",
                contentFile,
                progressListener,
            )
            audioFiles.add(audioFile)

            if (!TextUtils.isEmpty(contentFile.subtitleContentPath)) {
                val subtitleFile = DlsiteRemoteFiles.uniqueTarget(
                    File(audioFile.parentFile, DlsiteRemoteFiles.safeFileName(contentFile.subtitleName)),
                    usedTargets,
                )
                try {
                    downloadSignedSubtitleFile(
                        signedContentUrl(workId, contentFile.subtitleContentPath, revision, signParams),
                        subtitleFile,
                    )
                } catch (ignored: IOException) {
                    DlsiteRemoteFiles.deleteQuietly(subtitleFile)
                    DlsiteRemoteFiles.deleteQuietly(File(subtitleFile.parentFile, subtitleFile.name + ".part"))
                }
            }
        }
        return audioFiles
    }

    @Throws(IOException::class)
    private fun selectedContentFiles(
        ziptree: DlsiteJsonParser.DlsiteZiptree,
        downloadOptionId: String,
    ): List<DlsiteJsonParser.ContentFile> {
        if (TextUtils.isEmpty(downloadOptionId)) {
            return ziptree.audioFiles
        }
        for (option in DlsiteDownloadPlanner.optionsFor(ziptree)) {
            if (downloadOptionId == option.id) {
                return option.audioFiles
            }
        }
        throw IOException("没有找到所选下载版本")
    }

    private fun resolveDownloadUrl(work: DlsiteWork): String {
        if (!TextUtils.isEmpty(work.workId)) {
            return DlsiteRemoteConstants.PLAY_BASE_URL + "/api/v3/download?workno=" + work.workId
        }
        return work.downloadUrl
    }

    @Throws(IOException::class)
    private fun signDownloadCookies(workId: String) {
        get(
            DlsiteRemoteConstants.PLAY_DOWNLOAD_BASE_URL +
                "/api/v3/download/sign/cookie?workno=" +
                DlsiteRemoteFiles.encodeQueryValue(workId),
            DlsiteRemoteConstants.PLAY_BASE_URL + "/work/" + workId + "/tree",
            "application/json, text/plain, */*",
        )
    }

    @Throws(IOException::class)
    private fun fetchZiptree(workId: String): DlsiteJsonParser.DlsiteZiptree {
        val seconds = System.currentTimeMillis() / 1000L
        val minuteBucket = seconds - seconds % 60L
        val json = get(
            contentUrl(workId, "ziptree.json") + "?v=" + minuteBucket,
            DlsiteRemoteConstants.PLAY_BASE_URL + "/work/" + workId + "/tree",
            "application/json, text/plain, */*",
        )
        return DlsiteJsonParser.parseZiptree(json)
    }

    @Throws(IOException::class)
    private fun fetchDownloadSignParams(workId: String): Map<String, String> {
        val json = get(
            "/api/v3/download/sign/url?workno=" + DlsiteRemoteFiles.encodeQueryValue(workId),
            DlsiteRemoteConstants.PLAY_BASE_URL + "/work/" + workId + "/tree",
            "application/json, text/plain, */*",
        )
        return DlsiteJsonParser.parseSignUrlParams(json)
    }

    private fun signedContentUrl(
        workId: String,
        contentPath: String,
        revision: String,
        signParams: Map<String, String>,
    ): String {
        val builder = StringBuilder(contentUrl(workId, contentPath))
        builder.append("?v=").append(DlsiteRemoteFiles.encodeQueryValue(revision))
        for (entry in signParams.entries) {
            builder.append('&')
                .append(DlsiteRemoteFiles.encodeQueryValue(entry.key))
                .append('=')
                .append(DlsiteRemoteFiles.encodeQueryValue(entry.value))
        }
        return builder.toString()
    }

    private fun contentUrl(workId: String, relativePath: String): String {
        return DlsiteRemoteConstants.PLAY_DOWNLOAD_BASE_URL +
            contentBasePath(workId) +
            "/" +
            DlsiteRemoteFiles.encodePath(relativePath)
    }

    private fun contentBasePath(workId: String): String {
        val site = sitePathForWorkId(workId)
        val prefix = if (workId.length >= 2) workId.substring(0, 2) else workId
        val digits = if (workId.length > 2) workId.substring(2) else "0"
        val numericId = try {
            digits.toInt()
        } catch (exception: NumberFormatException) {
            0
        }
        val bucket = ((numericId + 999) / 1000) * 1000
        val bucketName = prefix + String.format(Locale.US, "%0" + digits.length + "d", bucket)
        return "/content/work/$site/$bucketName/$workId"
    }

    private fun sitePathForWorkId(workId: String): String {
        if (TextUtils.isEmpty(workId)) {
            return "doujin"
        }
        val first = workId[0]
        if (first == 'B') {
            return "books"
        }
        if (first == 'V') {
            return "professional"
        }
        return "doujin"
    }

    @Throws(IOException::class)
    private fun downloadSignedContentFile(
        url: String,
        targetFile: File,
        accept: String,
        contentFile: DlsiteJsonParser.ContentFile,
        progressListener: DlsiteContentProgressListener?,
    ) {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("无法创建下载目录")
        }
        val tempFile = File(parent ?: targetFile.parentFile, targetFile.name + ".part")
        httpClient.execute(
            url,
            DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
            accept,
            "GET",
            null,
            DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.READ_TIMEOUT_MS,
        ).use { response ->
            val code = response.code
            if (!response.isSuccessful) {
                val body = DlsiteHttpClient.bodyString(response)
                throw IOException(
                    if (body.isEmpty()) {
                        "下载失败: HTTP $code"
                    } else {
                        "下载失败: HTTP $code $body"
                    },
                )
            }
            val contentType = response.header("Content-Type")
            if (contentType != null &&
                (contentType.contains("text/html") || contentType.contains("application/json"))
            ) {
                val body = DlsiteHttpClient.bodyString(response)
                throw IOException("DLsite 返回了网页或错误信息，未拿到媒体文件: ${DlsiteRemoteFiles.summarizeBody(body)}")
            }

            val responseBody = response.body ?: throw IOException("下载失败: 响应为空")
            val totalBytes = responseBody.contentLength()
            var bytesDownloaded = 0L
            BufferedInputStream(responseBody.byteStream()).use { input ->
                BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) {
                            break
                        }
                        DlsiteRemoteFiles.throwIfInterrupted()
                        output.write(buffer, 0, count)
                        bytesDownloaded += count.toLong()
                        if (progressListener != null) {
                            progressListener.onProgress(contentFile, bytesDownloaded, totalBytes)
                        }
                    }
                }
            }
        }

        if (DlsiteRemoteFiles.looksLikeHtml(tempFile)) {
            if (!tempFile.delete()) {
                tempFile.deleteOnExit()
            }
            throw IOException("DLsite 返回了网页，未拿到媒体文件")
        }
        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("无法替换旧文件")
        }
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("无法保存下载文件")
        }
    }

    @Throws(IOException::class)
    private fun downloadSignedSubtitleFile(url: String, targetFile: File) {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("无法创建下载目录")
        }
        val tempFile = File(parent ?: targetFile.parentFile, targetFile.name + ".part")
        httpClient.execute(
            url,
            DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
            "text/vtt,text/plain,application/json,*/*",
            "GET",
            null,
            DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.READ_TIMEOUT_MS,
        ).use { response ->
            val code = response.code
            if (!response.isSuccessful) {
                val body = DlsiteHttpClient.bodyString(response)
                throw IOException(
                    if (body.isEmpty()) {
                        "字幕下载失败: HTTP $code"
                    } else {
                        "字幕下载失败: HTTP $code $body"
                    },
                )
            }

            val contentType = response.header("Content-Type")
            if (contentType != null && contentType.contains("text/html")) {
                val body = DlsiteHttpClient.bodyString(response)
                throw IOException("DLsite 返回了网页，未拿到字幕文件: ${DlsiteRemoteFiles.summarizeBody(body)}")
            }

            val jsonSubtitle = contentType != null && contentType.contains("application/json")
            if (jsonSubtitle) {
                val body = DlsiteHttpClient.bodyString(response)
                DlsiteRemoteFiles.writeTextFile(tempFile, DlsiteJsonParser.parseWebvttJson(body))
            } else {
                val responseBody = response.body ?: throw IOException("字幕下载失败: 响应为空")
                BufferedInputStream(responseBody.byteStream()).use { input ->
                    BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) {
                                break
                            }
                            DlsiteRemoteFiles.throwIfInterrupted()
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (DlsiteRemoteFiles.looksLikeHtml(tempFile)) {
                    if (!tempFile.delete()) {
                        tempFile.deleteOnExit()
                    }
                    throw IOException("DLsite 返回了网页，未拿到字幕文件")
                }
                if (DlsiteRemoteFiles.looksLikeJson(tempFile)) {
                    val body = DlsiteRemoteFiles.readTextFile(tempFile)
                    DlsiteRemoteFiles.writeTextFile(tempFile, DlsiteJsonParser.parseWebvttJson(body))
                }
            }
        }

        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("无法替换旧字幕文件")
        }
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("无法保存字幕文件")
        }
    }

    @Throws(IOException::class)
    private fun get(pathOrUrl: String?, referer: String?, accept: String?): String {
        return httpClient.text(
            pathOrUrl,
            referer,
            accept,
            "GET",
            null,
            DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.READ_TIMEOUT_MS,
        )
    }
}
