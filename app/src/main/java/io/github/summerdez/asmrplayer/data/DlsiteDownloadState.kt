package io.github.summerdez.asmrplayer.data

import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DlsiteDownloadState(
    val active: Boolean = false,
    val workId: String = "",
    val title: String = "",
    val status: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
) {
    val progressPercent: Int?
        get() = if (totalBytes > 0L) {
            ((bytesDownloaded.coerceAtLeast(0L) * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
        } else {
            null
        }

    val statusText: String
        get() {
            val percent = progressPercent
            return if (percent == null) status else "$status $percent%"
        }
}

object DlsiteDownloadStateBus {
    private val _state = MutableStateFlow(DlsiteDownloadState())
    val state: StateFlow<DlsiteDownloadState> = _state.asStateFlow()

    @JvmStatic
    fun publish(workId: String?, title: String?, status: String?) {
        publishProgress(workId, title, status, 0L, -1L)
    }

    @JvmStatic
    fun publishProgress(
        workId: String?,
        title: String?,
        status: String?,
        bytesDownloaded: Long,
        totalBytes: Long,
    ) {
        _state.value = DlsiteDownloadState(
            active = true,
            workId = workId.orEmpty(),
            title = title.orEmpty(),
            status = status.orEmpty(),
            bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
            totalBytes = totalBytes,
        )
    }

    @JvmStatic
    fun clear() {
        _state.value = DlsiteDownloadState()
    }
}
