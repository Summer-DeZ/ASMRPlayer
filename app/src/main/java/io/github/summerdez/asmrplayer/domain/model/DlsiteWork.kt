package io.github.summerdez.asmrplayer.domain.model

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
import java.util.Locale
import org.json.JSONException
import org.json.JSONObject

data class DlsiteWork @JvmOverloads constructor(
    @JvmField val workId: String,
    @JvmField val title: String,
    @JvmField val detailUrl: String,
    @JvmField val downloadUrl: String,
    @JvmField val coverUrl: String = "",
    @JvmField val coverUri: String = "",
    @JvmField val status: String = STATUS_FOUND,
    @JvmField val playlistId: String = "",
    @JvmField val localPath: String = "",
    @JvmField val error: String = "",
    @JvmField val downloadOptionId: String = "",
    @JvmField val downloadOptionTitle: String = "",
    @JvmField val updatedAt: Long = System.currentTimeMillis(),
    @JvmField val trackCount: Int = 0,
) {
    fun isDownloaded(): Boolean = status == STATUS_DOWNLOADED

    fun isDownloading(): Boolean = status == STATUS_DOWNLOADING

    fun isPaused(): Boolean = status == STATUS_PAUSED

    fun isFailed(): Boolean = status == STATUS_FAILED

    fun displayTitle(): String = title.ifEmpty { workId }

    fun statusLabel(): String {
        return when (status) {
            STATUS_DOWNLOADING -> "下载中"
            STATUS_PAUSED -> "已暂停"
            STATUS_DOWNLOADED -> if (trackCount > 0) "已导入 $trackCount 首" else "已导入"
            STATUS_FAILED -> error.ifEmpty { "下载失败" }
            else -> "未下载"
        }
    }

    fun mergedWithDiscovery(discovered: DlsiteWork?): DlsiteWork {
        if (discovered == null || workId != discovered.workId) {
            return this
        }
        return copy(
            title = discovered.title.ifEmpty { title },
            detailUrl = discovered.detailUrl.ifEmpty { detailUrl },
            downloadUrl = discovered.downloadUrl.ifEmpty { downloadUrl },
            coverUrl = discovered.coverUrl.ifEmpty { coverUrl },
            coverUri = discovered.coverUri.ifEmpty { coverUri },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun withEnsuredCoverUrl(): DlsiteWork {
        return if (coverUrl.isEmpty()) copy(coverUrl = inferredCoverUrl(workId)) else this
    }

    fun withTitle(value: String?): DlsiteWork = copy(title = safe(value))

    fun withDetailUrl(value: String?): DlsiteWork = copy(detailUrl = safe(value))

    fun withDownloadUrl(value: String?): DlsiteWork = copy(downloadUrl = safe(value))

    fun withCoverUrl(value: String?): DlsiteWork = copy(coverUrl = safe(value))

    fun withCoverUri(value: String?): DlsiteWork = copy(coverUri = safe(value))

    fun withDownloadOption(optionId: String?, optionTitle: String?): DlsiteWork {
        return copy(downloadOptionId = safe(optionId), downloadOptionTitle = safe(optionTitle))
    }

    fun asDownloading(): DlsiteWork {
        return copy(status = STATUS_DOWNLOADING, error = "", updatedAt = System.currentTimeMillis())
    }

    fun asPaused(): DlsiteWork {
        return copy(status = STATUS_PAUSED, error = "", updatedAt = System.currentTimeMillis())
    }

    fun asDownloaded(playlistId: String?, localPath: String?, trackCount: Int): DlsiteWork {
        return copy(
            status = STATUS_DOWNLOADED,
            playlistId = safe(playlistId),
            localPath = safe(localPath),
            trackCount = trackCount,
            error = "",
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun asFailed(error: String?): DlsiteWork {
        return copy(
            status = STATUS_FAILED,
            error = safe(error).ifEmpty { "下载失败" },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun asCacheDeleted(): DlsiteWork {
        return copy(
            status = STATUS_FOUND,
            playlistId = "",
            localPath = "",
            trackCount = 0,
            error = "",
            downloadOptionId = "",
            downloadOptionTitle = "",
            updatedAt = System.currentTimeMillis(),
        )
    }

    @Throws(JSONException::class)
    fun toJson(): JSONObject {
        return JSONObject()
            .put("workId", workId)
            .put("title", title)
            .put("detailUrl", detailUrl)
            .put("downloadUrl", downloadUrl)
            .put("coverUrl", coverUrl)
            .put("coverUri", coverUri)
            .put("status", status)
            .put("playlistId", playlistId)
            .put("localPath", localPath)
            .put("error", error)
            .put("downloadOptionId", downloadOptionId)
            .put("downloadOptionTitle", downloadOptionTitle)
            .put("updatedAt", updatedAt)
            .put("trackCount", trackCount)
    }

    companion object {
        const val STATUS_FOUND = "found"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_PAUSED = "paused"
        const val STATUS_DOWNLOADED = "downloaded"
        const val STATUS_FAILED = "failed"

        @JvmStatic
        fun fromJson(obj: JSONObject): DlsiteWork {
            return DlsiteWork(
                workId = obj.optString("workId"),
                title = obj.optString("title"),
                detailUrl = obj.optString("detailUrl"),
                downloadUrl = obj.optString("downloadUrl"),
                coverUrl = obj.optString("coverUrl"),
                coverUri = obj.optString("coverUri"),
                status = obj.optString("status", STATUS_FOUND),
                playlistId = obj.optString("playlistId"),
                localPath = obj.optString("localPath"),
                error = obj.optString("error"),
                downloadOptionId = obj.optString("downloadOptionId"),
                downloadOptionTitle = obj.optString("downloadOptionTitle"),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                trackCount = obj.optInt("trackCount", 0),
            )
        }

        @JvmStatic
        fun inferredCoverUrl(workId: String?): String {
            val bucketName = imageBucketName(workId)
            if (bucketName.isEmpty()) {
                return ""
            }
            return "https://img.dlsite.jp/modpub/images2/work/" +
                imageSitePathForWorkId(workId) +
                "/" +
                bucketName +
                "/" +
                workId +
                "_img_main.jpg"
        }

        @JvmStatic
        fun safe(value: String?): String = value?.trim().orEmpty()

        private fun imageBucketName(workId: String?): String {
            if (workId == null || workId.length <= 2) {
                return ""
            }
            val prefix = workId.substring(0, 2).uppercase(Locale.US)
            val digits = workId.substring(2)
            val numericId = digits.toIntOrNull() ?: return ""
            val bucket = ((numericId + 999) / 1000) * 1000
            return prefix + String.format(Locale.US, "%0${digits.length}d", bucket)
        }

        private fun imageSitePathForWorkId(workId: String?): String {
            if (workId.isNullOrEmpty()) {
                return "doujin"
            }
            return when (workId.first().uppercaseChar()) {
                'B' -> "books"
                'V' -> "professional"
                else -> "doujin"
            }
        }
    }
}
