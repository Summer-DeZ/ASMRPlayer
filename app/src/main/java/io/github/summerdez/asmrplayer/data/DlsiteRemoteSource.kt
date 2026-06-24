package io.github.summerdez.asmrplayer.data

import io.github.summerdez.asmrplayer.data.remote.DlsiteClient
import io.github.summerdez.asmrplayer.data.remote.DlsiteContentProgressListener
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.File
import java.io.IOException

interface DlsiteRemoteSource {
    fun hasLoginCookie(): Boolean

    @Throws(IOException::class)
    fun fetchPurchasedWorks(): List<DlsiteWork>

    @Throws(IOException::class)
    fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption>

    @Throws(IOException::class)
    fun downloadCover(work: DlsiteWork, outputDir: File): File

    @Throws(IOException::class)
    fun downloadWorkFiles(work: DlsiteWork, workDir: File, downloadOptionId: String): List<File>

    @Throws(IOException::class)
    fun downloadWorkFiles(
        work: DlsiteWork,
        workDir: File,
        downloadOptionId: String,
        progressListener: DlsiteContentProgressListener?,
    ): List<File> {
        return downloadWorkFiles(work, workDir, downloadOptionId)
    }
}

interface DlsiteApi : DlsiteRemoteSource

internal fun createDlsiteApi(): DlsiteApi {
    return DlsiteClientApi(DlsiteClient())
}

private class DlsiteClientApi(private val client: DlsiteClient) : DlsiteApi {
    override fun hasLoginCookie(): Boolean {
        return client.hasLoginCookie()
    }

    override fun fetchPurchasedWorks(): List<DlsiteWork> {
        return client.fetchPurchasedWorks()
    }

    override fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption> {
        return client.fetchDownloadOptions(work)
    }

    override fun downloadCover(work: DlsiteWork, outputDir: File): File {
        return client.downloadCover(work, outputDir)
    }

    override fun downloadWorkFiles(work: DlsiteWork, workDir: File, downloadOptionId: String): List<File> {
        return client.downloadWorkFiles(work, workDir, downloadOptionId)
    }

    override fun downloadWorkFiles(
        work: DlsiteWork,
        workDir: File,
        downloadOptionId: String,
        progressListener: DlsiteContentProgressListener?,
    ): List<File> {
        return client.downloadWorkFiles(work, workDir, downloadOptionId, progressListener)
    }
}
