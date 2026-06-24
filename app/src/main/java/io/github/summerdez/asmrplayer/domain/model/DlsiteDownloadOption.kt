package io.github.summerdez.asmrplayer.domain.model

import io.github.summerdez.asmrplayer.data.remote.DlsiteJsonParser

class DlsiteDownloadOption(
    id: String?,
    title: String?,
    audioFiles: List<DlsiteJsonParser.ContentFile>?,
) {
    @JvmField
    val id: String = id.orEmpty()

    @JvmField
    val title: String = title.orEmpty()

    @JvmField
    val audioFiles: List<DlsiteJsonParser.ContentFile> = ArrayList(audioFiles ?: emptyList())

    fun dialogLabel(): String {
        return title + " · " + audioFiles.size + " 首"
    }
}
