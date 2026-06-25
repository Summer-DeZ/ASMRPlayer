package io.github.summerdez.asmrplayer.data.remote

fun interface DlsiteContentProgressListener {
    fun onProgress(
        contentFile: DlsiteJsonParser.ContentFile,
        fileBytesDownloaded: Long,
        fileTotalBytes: Long,
    )
}
