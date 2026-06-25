package io.github.summerdez.asmrplayer.data.download

internal class DlsiteDownloadRequest(
    val taskId: String,
    val workId: String,
    val title: String,
    optionIds: List<String>,
) {
    val optionIds: List<String> = ArrayList(optionIds)
}
