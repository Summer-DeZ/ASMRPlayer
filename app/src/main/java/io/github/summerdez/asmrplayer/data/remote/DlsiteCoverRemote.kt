package io.github.summerdez.asmrplayer.data.remote

import android.text.TextUtils
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class DlsiteCoverRemote(private val httpClient: DlsiteHttpClient?) {
    @Throws(IOException::class)
    fun downloadCover(work: DlsiteWork?, targetDir: File?): File {
        if (work == null) {
            throw IOException("没有找到封面地址")
        }
        val coverWork = work.withEnsuredCoverUrl()
        var firstFailure: IOException? = null
        if (!TextUtils.isEmpty(coverWork.coverUrl)) {
            try {
                return downloadCoverUrl(coverWork, coverWork.coverUrl, targetDir)
            } catch (exception: IOException) {
                firstFailure = exception
            }
        }

        val resolvedCoverUrl = resolveCoverUrl(coverWork)
        if (!TextUtils.isEmpty(resolvedCoverUrl) && resolvedCoverUrl != coverWork.coverUrl) {
            return downloadCoverUrl(coverWork.withCoverUrl(resolvedCoverUrl), resolvedCoverUrl, targetDir)
        }
        if (firstFailure != null) {
            throw firstFailure
        }
        throw IOException("没有找到封面地址")
    }

    @Throws(IOException::class)
    private fun downloadCoverUrl(work: DlsiteWork, coverUrl: String?, targetDir: File?): File {
        if (TextUtils.isEmpty(coverUrl)) {
            throw IOException("没有找到封面地址")
        }
        val outputDir = targetDir ?: throw NullPointerException("targetDir")
        if (!outputDir.exists() && !outputDir.mkdirs() && !outputDir.isDirectory) {
            throw IOException("无法创建封面目录")
        }

        val tempFile = File(outputDir, "cover.part")
        var contentType: String? = null
        val client = httpClient ?: throw NullPointerException("httpClient")
        client.execute(
            coverUrl,
            if (TextUtils.isEmpty(work.detailUrl)) {
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library"
            } else {
                work.detailUrl
            },
            "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*",
            "GET",
            null,
            DlsiteRemoteConstants.COVER_CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.COVER_READ_TIMEOUT_MS,
        ).use { response ->
            val code = response.code
            if (!response.isSuccessful) {
                val body = DlsiteHttpClient.bodyString(response)
                throw IOException(
                    if (body.isEmpty()) {
                        "封面下载失败: HTTP $code"
                    } else {
                        "封面下载失败: HTTP $code"
                    },
                )
            }

            contentType = response.header("Content-Type")
            val actualContentType = contentType
            if (actualContentType != null &&
                (actualContentType.contains("text/html") || actualContentType.contains("application/json"))
            ) {
                val body = DlsiteHttpClient.bodyString(response)
                throw IOException("DLsite 返回了网页或错误信息，未拿到封面: ${summarizeBody(body)}")
            }

            val responseBody = response.body ?: throw IOException("封面下载失败: 响应为空")
            BufferedInputStream(responseBody.byteStream()).use { input ->
                BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) {
                            break
                        }
                        throwIfInterrupted()
                        output.write(buffer, 0, count)
                    }
                }
            }
        }

        val targetFile = File(outputDir, "cover" + coverExtension(coverUrl, contentType))
        if (looksLikeHtml(tempFile) || looksLikeJson(tempFile)) {
            deleteQuietly(tempFile)
            throw IOException("DLsite 返回了网页或错误信息，未拿到封面")
        }
        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("无法替换旧封面")
        }
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("无法保存封面")
        }
        return targetFile
    }

    private fun resolveCoverUrl(work: DlsiteWork?): String {
        val sourceWork = work ?: return ""
        val workId = sourceWork.workId
        if (TextUtils.isEmpty(workId)) {
            return ""
        }

        try {
            val detailJson = get(
                "/api/v3/work/" + encodeQueryValue(workId),
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                "application/json, text/plain, */*",
            )
            val detail = DlsiteJsonParser.parseWorkDetail(detailJson)
            if (detail != null &&
                !TextUtils.isEmpty(detail.coverUrl) &&
                detail.coverUrl != sourceWork.coverUrl
            ) {
                return detail.coverUrl
            }
        } catch (ignored: IOException) {
        }

        for (publicUrl in publicWorkUrls(workId)) {
            try {
                val html = getCoverPage(publicUrl)
                val coverUrl = DlsiteHtmlParser.findCoverUrl(html, publicUrl, workId)
                if (!TextUtils.isEmpty(coverUrl) && coverUrl != sourceWork.coverUrl) {
                    return coverUrl
                }
            } catch (ignored: IOException) {
            }
        }
        return ""
    }

    private fun publicWorkUrls(workId: String?): List<String> {
        val urls = ArrayList<String>()
        if (TextUtils.isEmpty(workId)) {
            return urls
        }
        val id = workId ?: return urls
        val first = id[0].uppercaseChar()
        if (first == 'B') {
            urls.add("https://www.dlsite.com/books/work/=/product_id/$id.html")
            return urls
        }
        if (first == 'V') {
            urls.add("https://www.dlsite.com/pro/work/=/product_id/$id.html")
            return urls
        }
        urls.add("https://www.dlsite.com/maniax/work/=/product_id/$id.html")
        urls.add("https://www.dlsite.com/home/work/=/product_id/$id.html")
        return urls
    }

    @Throws(IOException::class)
    private fun get(pathOrUrl: String?, referer: String?, accept: String?): String {
        val client = httpClient ?: throw NullPointerException("httpClient")
        return client.text(
            pathOrUrl,
            referer,
            accept,
            "GET",
            null,
            DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.READ_TIMEOUT_MS,
        )
    }

    @Throws(IOException::class)
    private fun getCoverPage(url: String?): String {
        val client = httpClient ?: throw NullPointerException("httpClient")
        return client.text(
            url,
            DlsiteRemoteConstants.DL_SITE_COOKIE_URL,
            "text/html,application/xhtml+xml,*/*",
            "GET",
            null,
            DlsiteRemoteConstants.COVER_CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.COVER_READ_TIMEOUT_MS,
        )
    }

    private fun encodeQueryValue(value: String?): String {
        return try {
            URLEncoder.encode(value ?: "", StandardCharsets.UTF_8.name())
                .replace("+", "%20")
        } catch (exception: IOException) {
            ""
        }
    }

    private fun coverExtension(url: String?, contentType: String?): String {
        val typeExtension = coverExtensionFromContentType(contentType)
        if (typeExtension.isNotEmpty()) {
            return typeExtension
        }
        var path = url ?: ""
        val query = path.indexOf('?')
        if (query >= 0) {
            path = path.substring(0, query)
        }
        val fragment = path.indexOf('#')
        if (fragment >= 0) {
            path = path.substring(0, fragment)
        }
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.')
        if (dot > slash && dot < path.length - 1) {
            val extension = path.substring(dot + 1).lowercase(Locale.US)
            if (extension == "jpg" ||
                extension == "jpeg" ||
                extension == "png" ||
                extension == "webp" ||
                extension == "gif" ||
                extension == "avif"
            ) {
                return ".$extension"
            }
        }
        return ".jpg"
    }

    private fun coverExtensionFromContentType(contentType: String?): String {
        if (contentType == null) {
            return ""
        }
        val lower = contentType.lowercase(Locale.US)
        if (lower.contains("image/jpeg") || lower.contains("image/jpg")) {
            return ".jpg"
        }
        if (lower.contains("image/png")) {
            return ".png"
        }
        if (lower.contains("image/webp")) {
            return ".webp"
        }
        if (lower.contains("image/gif")) {
            return ".gif"
        }
        if (lower.contains("image/avif")) {
            return ".avif"
        }
        return ""
    }

    @Throws(IOException::class)
    private fun looksLikeHtml(file: File?): Boolean {
        if (file == null || !file.isFile) {
            return false
        }
        val header = ByteArray(minOf(128L, file.length()).toInt())
        FileInputStream(file).use { input ->
            val count = input.read(header)
            if (count <= 0) {
                return false
            }
            val text = String(header, 0, count, StandardCharsets.UTF_8)
                .trim { it <= ' ' }
                .lowercase(Locale.ROOT)
            return text.startsWith("<!doctype html") || text.startsWith("<html")
        }
    }

    @Throws(IOException::class)
    private fun looksLikeJson(file: File?): Boolean {
        if (file == null || !file.isFile) {
            return false
        }
        val header = ByteArray(minOf(128L, file.length()).toInt())
        FileInputStream(file).use { input ->
            val count = input.read(header)
            if (count <= 0) {
                return false
            }
            val text = String(header, 0, count, StandardCharsets.UTF_8).trim { it <= ' ' }
            return text.startsWith("{") || text.startsWith("[")
        }
    }

    private fun deleteQuietly(file: File?) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit()
        }
    }

    private fun summarizeBody(body: String?): String {
        if (body == null) {
            return ""
        }
        val text = body.replace(Regex("\\s+"), " ").trim { it <= ' ' }
        return if (text.length <= 160) text else text.substring(0, 160)
    }

    @Throws(InterruptedIOException::class)
    private fun throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("下载已取消")
        }
    }
}
