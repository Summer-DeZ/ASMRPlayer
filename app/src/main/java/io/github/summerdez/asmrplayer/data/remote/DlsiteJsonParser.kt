package io.github.summerdez.asmrplayer.data.remote

import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.IOException
import java.util.ArrayList
import kotlin.math.max

object DlsiteJsonParser {
    @JvmStatic
    fun isAuthorized(json: String?): Boolean = DlsiteLibraryJsonParser.isAuthorized(json)

    @JvmStatic
    @Throws(IOExceptionLikeJsonException::class)
    fun parseContentCount(json: String?): ContentCount =
        DlsiteLibraryJsonParser.parseContentCount(json)

    @JvmStatic
    @Throws(IOExceptionLikeJsonException::class)
    fun parseSalesWorkIds(json: String?): List<String> =
        DlsiteLibraryJsonParser.parseSalesWorkIds(json)

    @JvmStatic
    @Throws(IOExceptionLikeJsonException::class)
    fun parseHistoryWorkIds(json: String?): List<String> =
        DlsiteLibraryJsonParser.parseHistoryWorkIds(json)

    @JvmStatic
    @Throws(IOExceptionLikeJsonException::class)
    fun parseContentWorks(json: String?): List<DlsiteWork> =
        DlsiteLibraryJsonParser.parseContentWorks(json)

    @JvmStatic
    @Throws(IOExceptionLikeJsonException::class)
    fun parseWorkDetail(json: String?): DlsiteWork? =
        DlsiteLibraryJsonParser.parseWorkDetail(json)

    @JvmStatic
    fun toJsonArray(values: List<String?>?): String =
        DlsiteDownloadJsonParser.toJsonArray(values)

    @JvmStatic
    @Throws(IOExceptionLikeJsonException::class)
    fun parseZiptree(json: String?): DlsiteZiptree =
        DlsiteDownloadJsonParser.parseZiptree(json)

    @JvmStatic
    @Throws(IOExceptionLikeJsonException::class)
    fun parseSignUrlParams(json: String?): Map<String, String> =
        DlsiteDownloadJsonParser.parseSignUrlParams(json)

    @JvmStatic
    @Throws(IOExceptionLikeJsonException::class)
    fun parseWebvttJson(json: String?): String =
        DlsiteWebvttJsonParser.parseWebvttJson(json)

    class ContentCount(
        @JvmField val user: Int,
        @JvmField val production: Int,
        @JvmField val pageLimit: Int,
    )

    class IOExceptionLikeJsonException(
        message: String?,
        cause: Throwable?,
    ) : IOException(message, cause)

    class DlsiteZiptree(
        workId: String?,
        revision: String?,
        audioFiles: List<ContentFile>?,
    ) {
        @JvmField
        val workId: String = workId ?: ""

        @JvmField
        val revision: String = revision ?: ""

        @JvmField
        val audioFiles: List<ContentFile> = audioFiles ?: ArrayList()
    }

    class ContentFile {
        @JvmField
        val displayPath: String

        @JvmField
        val displayName: String

        @JvmField
        val contentPath: String

        @JvmField
        val subtitleContentPath: String

        @JvmField
        val subtitleName: String

        @JvmField
        val lengthBytes: Long

        constructor(
            displayPath: String?,
            displayName: String?,
            contentPath: String?,
            subtitleContentPath: String?,
            subtitleName: String?,
        ) : this(displayPath, displayName, contentPath, subtitleContentPath, subtitleName, 0L)

        constructor(
            displayPath: String?,
            displayName: String?,
            contentPath: String?,
            subtitleContentPath: String?,
            subtitleName: String?,
            lengthBytes: Long,
        ) {
            this.displayPath = displayPath ?: ""
            this.displayName = displayName ?: ""
            this.contentPath = contentPath ?: ""
            this.subtitleContentPath = subtitleContentPath ?: ""
            this.subtitleName = subtitleName ?: ""
            this.lengthBytes = max(0L, lengthBytes)
        }
    }
}
