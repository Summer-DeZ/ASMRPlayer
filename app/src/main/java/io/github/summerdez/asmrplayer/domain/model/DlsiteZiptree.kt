package io.github.summerdez.asmrplayer.domain.model

import kotlin.math.max

class DlsiteZiptree(
    val workId: String = "",
    val revision: String = "",
    audioFiles: List<DlsiteContentFile> = emptyList(),
) {
    val audioFiles: List<DlsiteContentFile> = ArrayList(audioFiles)
}

class DlsiteContentFile(
    val displayPath: String = "",
    val displayName: String = "",
    val contentPath: String = "",
    val subtitleContentPath: String = "",
    val subtitleName: String = "",
    lengthBytes: Long = 0L,
) {
    val lengthBytes: Long = max(0L, lengthBytes)
}
