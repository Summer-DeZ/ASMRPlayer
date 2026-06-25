package io.github.summerdez.asmrplayer.domain.model

class DlsiteDownloadOption(
    val id: String = "",
    val title: String = "",
    audioFiles: List<DlsiteContentFile> = emptyList(),
) {
    val audioFiles: List<DlsiteContentFile> = ArrayList(audioFiles)

    fun dialogLabel(): String {
        return title + " · " + audioFiles.size + " 首"
    }
}
