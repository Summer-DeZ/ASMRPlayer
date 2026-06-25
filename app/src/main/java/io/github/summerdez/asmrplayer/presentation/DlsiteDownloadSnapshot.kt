package io.github.summerdez.asmrplayer.presentation

import io.github.summerdez.asmrplayer.data.DlsiteDownloadState
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork

internal data class DownloadSnapshot(
    val works: List<DlsiteWork>,
    val contents: List<DlsiteContent>,
    val lastSyncMs: Long,
    val downloadState: DlsiteDownloadState,
)
