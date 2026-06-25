package io.github.summerdez.asmrplayer.domain.model

import io.github.summerdez.asmrplayer.data.remote.DlsiteJsonParser

class DlsiteDownloadOption(
    val id: String = "",
    val title: String = "",
    audioFiles: List<DlsiteJsonParser.ContentFile> = emptyList(),
) {
    val audioFiles: List<DlsiteJsonParser.ContentFile> = ArrayList(audioFiles)

    fun dialogLabel(): String {
        return title + " · " + audioFiles.size + " 首"
    }
}
