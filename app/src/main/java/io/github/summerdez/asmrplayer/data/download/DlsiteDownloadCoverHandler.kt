package io.github.summerdez.asmrplayer.data.download

import android.net.Uri
import android.text.TextUtils
import android.util.Log
import io.github.summerdez.asmrplayer.data.DlsiteApi
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.File
import java.io.IOException

private const val DLSITE_DOWNLOAD_TASK_TAG = "DlsiteDownloadTask"

internal object DlsiteDownloadCoverHandler {
    fun existingCoverFile(work: DlsiteWork): File? {
        if (TextUtils.isEmpty(work.coverUri)) {
            return null
        }
        return try {
            val uri = Uri.parse(work.coverUri)
            val path = uri.path
            if ("file" != uri.scheme || path.isNullOrEmpty()) {
                return null
            }
            val file = File(path)
            if (file.isFile) file else null
        } catch (exception: RuntimeException) {
            Log.d(
                DLSITE_DOWNLOAD_TASK_TAG,
                "Existing cover URI could not be reused for work=${work.workId}; ignoring cached cover",
                exception,
            )
            null
        }
    }

    fun downloadCover(dlsiteApi: DlsiteApi, work: DlsiteWork, workDir: File): File? {
        if (TextUtils.isEmpty(work.coverUrl)) {
            return null
        }
        return try {
            dlsiteApi.downloadCover(work, File(workDir, "cover"))
        } catch (exception: IOException) {
            Log.w(
                DLSITE_DOWNLOAD_TASK_TAG,
                "Cover download failed for work=${work.workId}; continuing import without local cover",
                exception,
            )
            null
        }
    }
}
