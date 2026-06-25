package io.github.summerdez.asmrplayer.data.remote

import io.github.summerdez.asmrplayer.domain.model.DlsiteZiptree
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.IOException

object DlsiteJsonParser {
    fun isAuthorized(json: String): Boolean = DlsiteLibraryJsonParser.isAuthorized(json)

    @Throws(IOExceptionLikeJsonException::class)
    fun parseContentCount(json: String): ContentCount =
        DlsiteLibraryJsonParser.parseContentCount(json)

    @Throws(IOExceptionLikeJsonException::class)
    fun parseSalesWorkIds(json: String): List<String> =
        DlsiteLibraryJsonParser.parseSalesWorkIds(json)

    @Throws(IOExceptionLikeJsonException::class)
    fun parseHistoryWorkIds(json: String): List<String> =
        DlsiteLibraryJsonParser.parseHistoryWorkIds(json)

    @Throws(IOExceptionLikeJsonException::class)
    fun parseContentWorks(json: String): List<DlsiteWork> =
        DlsiteLibraryJsonParser.parseContentWorks(json)

    @Throws(IOExceptionLikeJsonException::class)
    fun parseWorkDetail(json: String): DlsiteWork? =
        DlsiteLibraryJsonParser.parseWorkDetail(json)

    fun toJsonArray(values: List<String?>?): String =
        DlsiteDownloadJsonParser.toJsonArray(values)

    @Throws(IOExceptionLikeJsonException::class)
    fun parseZiptree(json: String): DlsiteZiptree =
        DlsiteDownloadJsonParser.parseZiptree(json)

    @Throws(IOExceptionLikeJsonException::class)
    fun parseSignUrlParams(json: String): Map<String, String> =
        DlsiteDownloadJsonParser.parseSignUrlParams(json)

    @Throws(IOExceptionLikeJsonException::class)
    fun parseWebvttJson(json: String): String =
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
}
