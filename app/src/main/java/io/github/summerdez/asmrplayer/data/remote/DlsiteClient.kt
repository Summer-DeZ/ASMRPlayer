package io.github.summerdez.asmrplayer.data.remote

import android.text.TextUtils
import android.webkit.CookieManager
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.File
import java.io.IOException

class DlsiteClient {
    private val httpClient = DlsiteHttpClient()
    private val workRemote = DlsiteWorkRemote(httpClient)
    private val coverRemote = DlsiteCoverRemote(httpClient)
    private val contentRemote = DlsiteContentRemote(httpClient)

    fun hasLoginCookie(): Boolean {
        var cookie: String? = CookieManager.getInstance().getCookie(DlsiteRemoteConstants.COOKIE_URL)
        if (TextUtils.isEmpty(cookie)) {
            cookie = CookieManager.getInstance().getCookie(DlsiteRemoteConstants.DL_SITE_COOKIE_URL)
        }
        return !TextUtils.isEmpty(cookie) &&
            (
                cookie?.contains("play_session") == true ||
                    cookie?.contains("loginchecked") == true ||
                    cookie?.contains("uid_jp") == true ||
                    cookie?.contains("eridjp") == true
                )
    }

    @Throws(IOException::class)
    fun fetchPurchasedWorks(): List<DlsiteWork> {
        return workRemote.fetchPurchasedWorks()
    }

    @Throws(IOException::class)
    fun downloadTo(work: DlsiteWork?, targetFile: File?) {
        contentRemote.downloadTo(work, targetFile)
    }

    @Throws(IOException::class)
    fun downloadCover(work: DlsiteWork?, targetDir: File?): File {
        return coverRemote.downloadCover(work, targetDir)
    }

    @Throws(IOException::class)
    fun fetchDownloadOptions(work: DlsiteWork?): List<DlsiteDownloadOption> {
        return contentRemote.fetchDownloadOptions(work)
    }

    @Throws(IOException::class)
    fun downloadWorkFiles(work: DlsiteWork?, workDir: File?): List<File> {
        return contentRemote.downloadWorkFiles(work, workDir, "")
    }

    @Throws(IOException::class)
    fun downloadWorkFiles(work: DlsiteWork?, workDir: File?, downloadOptionId: String?): List<File> {
        return contentRemote.downloadWorkFiles(work, workDir, downloadOptionId)
    }

    @Throws(IOException::class)
    fun downloadWorkFiles(
        work: DlsiteWork?,
        workDir: File?,
        downloadOptionId: String?,
        progressListener: DlsiteContentProgressListener?,
    ): List<File> {
        return contentRemote.downloadWorkFiles(work, workDir, downloadOptionId, progressListener)
    }

    companion object {
        const val LOGIN_URL: String = DlsiteRemoteConstants.PLAY_BASE_URL + "/library"
    }
}
