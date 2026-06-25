package io.github.summerdez.asmrplayer.data.remote

import android.text.TextUtils
import android.util.Log
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Locale

private const val TAG = "DlsiteCoverRemote"

class DlsiteCoverRemote(private val httpClient: DlsiteHttpClient) {
    @Throws(IOException::class)
    fun downloadCover(work: DlsiteWork, targetDir: File): File {
        val coverWork = work.withEnsuredCoverUrl()
        var firstFailure: IOException? = null
        if (!TextUtils.isEmpty(coverWork.coverUrl)) {
            try {
                return downloadCoverUrl(coverWork, coverWork.coverUrl, targetDir)
            } catch (exception: InterruptedIOException) {
                throw exception
            } catch (exception: IOException) {
                Log.w(TAG, "Primary cover download failed for work=${coverWork.workId}; trying resolved cover fallback", exception)
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
    private fun downloadCoverUrl(work: DlsiteWork, coverUrl: String, targetDir: File): File {
        if (TextUtils.isEmpty(coverUrl)) {
            throw IOException("没有找到封面地址")
        }
        if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.isDirectory) {
            throw IOException("无法创建封面目录")
        }

        val tempFile = File(targetDir, "cover.part")
        var contentType: String? = null
        httpClient.execute(
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
                throw IOException("DLsite 返回了网页或错误信息，未拿到封面: ${DlsiteRemoteFiles.summarizeBody(body)}")
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
                        DlsiteRemoteFiles.throwIfInterrupted()
                        output.write(buffer, 0, count)
                    }
                }
            }
        }

        val targetFile = File(targetDir, "cover" + coverExtension(coverUrl, contentType))
        if (DlsiteRemoteFiles.looksLikeHtml(tempFile) || DlsiteRemoteFiles.looksLikeJson(tempFile)) {
            DlsiteRemoteFiles.deleteQuietly(tempFile)
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

    private fun resolveCoverUrl(work: DlsiteWork): String {
        val workId = work.workId
        if (workId.isEmpty()) {
            return ""
        }

        try {
            val detailJson = get(
                "/api/v3/work/" + DlsiteRemoteFiles.encodeQueryValue(workId),
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                "application/json, text/plain, */*",
            )
            val detail = DlsiteJsonParser.parseWorkDetail(detailJson)
            if (detail != null &&
                !TextUtils.isEmpty(detail.coverUrl) &&
                detail.coverUrl != work.coverUrl
            ) {
                return detail.coverUrl
            }
        } catch (exception: InterruptedIOException) {
            throw exception
        } catch (exception: IOException) {
            Log.d(TAG, "Failed to resolve cover from DLsite Play detail for work=$workId; trying public pages", exception)
        }

        for (publicUrl in publicWorkUrls(workId)) {
            try {
                val html = getCoverPage(publicUrl)
                val coverUrl = DlsiteHtmlParser.findCoverUrl(html, publicUrl, workId)
                if (!TextUtils.isEmpty(coverUrl) && coverUrl != work.coverUrl) {
                    return coverUrl
                }
            } catch (exception: InterruptedIOException) {
                throw exception
            } catch (exception: IOException) {
                Log.d(TAG, "Failed to resolve cover from public page for work=$workId url=$publicUrl; trying next fallback", exception)
            }
        }
        return ""
    }

    private fun publicWorkUrls(workId: String): List<String> {
        val urls = ArrayList<String>()
        if (workId.isEmpty()) {
            return urls
        }
        val first = workId[0].uppercaseChar()
        if (first == 'B') {
            urls.add("https://www.dlsite.com/books/work/=/product_id/$workId.html")
            return urls
        }
        if (first == 'V') {
            urls.add("https://www.dlsite.com/pro/work/=/product_id/$workId.html")
            return urls
        }
        urls.add("https://www.dlsite.com/maniax/work/=/product_id/$workId.html")
        urls.add("https://www.dlsite.com/home/work/=/product_id/$workId.html")
        return urls
    }

    @Throws(IOException::class)
    private fun get(pathOrUrl: String, referer: String, accept: String): String {
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

    @Throws(IOException::class)
    private fun getCoverPage(url: String): String {
        return httpClient.text(
            url,
            DlsiteRemoteConstants.DL_SITE_COOKIE_URL,
            "text/html,application/xhtml+xml,*/*",
            "GET",
            null,
            DlsiteRemoteConstants.COVER_CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.COVER_READ_TIMEOUT_MS,
        )
    }

    private fun coverExtension(url: String, contentType: String?): String {
        val typeExtension = coverExtensionFromContentType(contentType)
        if (typeExtension.isNotEmpty()) {
            return typeExtension
        }
        var path = url
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

}
