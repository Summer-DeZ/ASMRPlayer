package io.github.summerdez.asmrplayer.data.remote

import io.github.summerdez.asmrplayer.domain.model.DlsiteContentFile

fun interface DlsiteContentProgressListener {
    fun onProgress(
        contentFile: DlsiteContentFile,
        fileBytesDownloaded: Long,
        fileTotalBytes: Long,
    )
}
