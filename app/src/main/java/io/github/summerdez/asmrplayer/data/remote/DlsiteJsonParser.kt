package io.github.summerdez.asmrplayer.data.remote

import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.IOException
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
        val user: Int = 0,
        val production: Int = 0,
        val pageLimit: Int = 50,
    )

    class IOExceptionLikeJsonException(
        message: String?,
        cause: Throwable?,
    ) : IOException(message, cause)

    class DlsiteZiptree(
        val workId: String = "",
        val revision: String = "",
        val audioFiles: List<ContentFile> = emptyList(),
    )

    class ContentFile(
        val displayPath: String = "",
        val displayName: String = "",
        val contentPath: String = "",
        val subtitleContentPath: String = "",
        val subtitleName: String = "",
        lengthBytes: Long = 0L,
    ) {
        val lengthBytes: Long = max(0L, lengthBytes)
    }
}
