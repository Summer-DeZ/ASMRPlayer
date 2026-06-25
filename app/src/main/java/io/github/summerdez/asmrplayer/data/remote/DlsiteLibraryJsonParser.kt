package io.github.summerdez.asmrplayer.data.remote

import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.net.URI
import java.util.Locale

object DlsiteLibraryJsonParser {
    private const val PLAY_BASE_URL = "https://play.dlsite.com"

    fun isAuthorized(json: String): Boolean {
        return try {
            when (val root = DlsiteJsonSupport.parse(json)) {
                is Map<*, *> -> root.isNotEmpty()
                is List<*> -> root.isNotEmpty()
                else -> root != null
            }
        } catch (exception: IllegalArgumentException) {
            false
        }
    }

    @Throws(DlsiteJsonParser.IOExceptionLikeJsonException::class)
    fun parseContentCount(json: String): DlsiteJsonParser.ContentCount {
        return try {
            val data = DlsiteJsonSupport.asObject(DlsiteJsonSupport.parse(json))
            DlsiteJsonParser.ContentCount(
                DlsiteJsonSupport.asInt(data["user"], 0),
                DlsiteJsonSupport.asInt(data["production"], 0),
                DlsiteJsonSupport.asInt(data["page_limit"], 50),
            )
        } catch (exception: IllegalArgumentException) {
            throw DlsiteJsonParser.IOExceptionLikeJsonException("购买数量解析失败", exception)
        }
    }

    @Throws(DlsiteJsonParser.IOExceptionLikeJsonException::class)
    fun parseSalesWorkIds(json: String): List<String> {
        return try {
            parseWorkIds(json, "sales")
        } catch (exception: IllegalArgumentException) {
            throw DlsiteJsonParser.IOExceptionLikeJsonException("购买记录解析失败", exception)
        }
    }

    @Throws(DlsiteJsonParser.IOExceptionLikeJsonException::class)
    fun parseHistoryWorkIds(json: String): List<String> {
        return try {
            parseWorkIds(json, "histories")
        } catch (exception: IllegalArgumentException) {
            throw DlsiteJsonParser.IOExceptionLikeJsonException("浏览记录解析失败", exception)
        }
    }

    @Throws(DlsiteJsonParser.IOExceptionLikeJsonException::class)
    fun parseContentWorks(json: String): List<DlsiteWork> {
        return try {
            val array = DlsiteJsonSupport.arrayFromRoot(DlsiteJsonSupport.parse(json), "works")
            val works = ArrayList<DlsiteWork>()
            for (value in array) {
                val work = toWork(DlsiteJsonSupport.asObjectOrNull(value))
                if (work != null) {
                    works.add(work)
                }
            }
            works
        } catch (exception: IllegalArgumentException) {
            throw DlsiteJsonParser.IOExceptionLikeJsonException("作品解析失败", exception)
        }
    }

    @Throws(DlsiteJsonParser.IOExceptionLikeJsonException::class)
    fun parseWorkDetail(json: String): DlsiteWork? {
        return try {
            val root = DlsiteJsonSupport.asObject(DlsiteJsonSupport.parse(json))
            val work = DlsiteJsonSupport.asObjectOrNull(root["work"])
            toWork(work ?: root)
        } catch (exception: IllegalArgumentException) {
            throw DlsiteJsonParser.IOExceptionLikeJsonException("作品详情解析失败", exception)
        }
    }

    private fun parseWorkIds(json: String, key: String): List<String> {
        val array = DlsiteJsonSupport.arrayFromRoot(DlsiteJsonSupport.parse(json), key)
        val ids = ArrayList<String>()
        for (value in array) {
            val data = DlsiteJsonSupport.asObjectOrNull(value) ?: continue
            val workId = javaTrim(DlsiteJsonSupport.asString(data["workno"]))
            if (workId.isNotEmpty() && !ids.contains(workId)) {
                ids.add(workId)
            }
        }
        return ids
    }

    private fun toWork(data: Map<String, Any?>?): DlsiteWork? {
        if (data == null) {
            return null
        }
        val workId = javaTrim(DlsiteJsonSupport.asString(data["workno"]))
        if (workId.isEmpty() || !isDownloadableAudio(data)) {
            return null
        }
        var title = localizedString(data["name"])
        if (title.isEmpty()) {
            title = localizedString(data["work_name"])
        }
        if (title.isEmpty()) {
            title = javaTrim(DlsiteJsonSupport.asString(data["title"]))
        }
        if (title.isEmpty()) {
            title = workId
        }
        var coverUrl = coverUrlFrom(data)
        if (coverUrl.isEmpty()) {
            coverUrl = DlsiteWork.inferredCoverUrl(workId)
        }
        return DlsiteWork(
            workId,
            title,
            "$PLAY_BASE_URL/work/$workId/tree",
            "$PLAY_BASE_URL/api/v3/download?workno=$workId",
            coverUrl,
        )
    }

    private fun isDownloadableAudio(data: Map<String, Any?>): Boolean {
        val downloadable = DlsiteJsonSupport.asBooleanOrNull(data["downloadable"])
        if (downloadable == false) {
            return false
        }
        if (data.containsKey("content_count") && DlsiteJsonSupport.asInt(data["content_count"], 0) <= 0) {
            return false
        }
        val appType = javaTrim(DlsiteJsonSupport.asString(data["app_type"]))
        if ("playbox".equals(appType, ignoreCase = true)) {
            return false
        }
        if ("sound".equals(appType, ignoreCase = true)) {
            return true
        }
        val workType = javaTrim(DlsiteJsonSupport.asString(data["work_type"]))
        val category = javaTrim(DlsiteJsonSupport.asString(data["work_category"]))
        return "SOU".equals(workType, ignoreCase = true) ||
            "MUS".equals(workType, ignoreCase = true) ||
            "music".equals(category, ignoreCase = true) ||
            "audio".equals(category, ignoreCase = true)
    }

    private fun coverUrlFrom(data: Map<String, Any?>): String {
        val directKeys = arrayOf(
            "coverUrl", "cover_url", "cover", "jacket", "jacket_url",
            "image_main", "imageMain", "imageMainUrl", "image_main_url",
            "main_image", "mainImage", "mainImageUrl", "main_image_url",
            "work_image", "workImage", "workImageUrl", "work_image_url",
            "thumbnail", "thumbnail_url", "thumbnailUrl",
            "thumb", "thumb_url", "thumbUrl",
            "image", "image_url", "imageUrl",
            "images", "image_urls", "imageUrls",
            "sam", "sample_image", "sampleImage",
        )
        for (key in directKeys) {
            val url = coverUrlCandidate(data[key])
            if (url.isNotEmpty()) {
                return url
            }
        }
        for ((entryKey, entryValue) in data) {
            val key = entryKey.lowercase(Locale.US)
            if (!key.contains("cover") &&
                !key.contains("jacket") &&
                !key.contains("image") &&
                !key.contains("thumb") &&
                key != "sam"
            ) {
                continue
            }
            val url = coverUrlCandidate(entryValue)
            if (url.isNotEmpty()) {
                return url
            }
        }
        return ""
    }

    private fun coverUrlCandidate(value: Any?): String {
        if (value is String) {
            return normalizeCoverUrl(value)
        }
        val list = DlsiteJsonSupport.asListOrNull(value)
        if (list != null) {
            for (item in list) {
                val url = coverUrlCandidate(item)
                if (url.isNotEmpty()) {
                    return url
                }
            }
            return ""
        }
        val data = DlsiteJsonSupport.asObjectOrNull(value) ?: return ""

        val preferredKeys = arrayOf(
            "url", "src", "href", "main", "large", "large_url", "largeUrl",
            "medium", "medium_url", "mediumUrl", "original", "original_url", "originalUrl",
            "work", "image", "thumbnail", "thumb", "jacket", "cover",
            "path", "file", "filename", "file_name", "sam",
        )
        for (key in preferredKeys) {
            val url = coverUrlCandidate(data[key])
            if (url.isNotEmpty()) {
                return url
            }
        }
        for (nestedValue in data.values) {
            val url = coverUrlCandidate(nestedValue)
            if (url.isNotEmpty()) {
                return url
            }
        }
        return ""
    }

    private fun normalizeCoverUrl(value: String?): String {
        val url = javaTrim(value)
        if (url.isEmpty()) {
            return ""
        }
        val lower = url.lowercase(Locale.US)
        if (!lower.startsWith("http://") &&
            !lower.startsWith("https://") &&
            !lower.startsWith("//") &&
            !lower.startsWith("/")
        ) {
            return ""
        }
        return try {
            URI.create(PLAY_BASE_URL).resolve(url).toString()
        } catch (exception: IllegalArgumentException) {
            ""
        }
    }

    private fun localizedString(value: Any?): String {
        val data = DlsiteJsonSupport.asObjectOrNull(value)
        if (data != null) {
            val preferred = arrayOf("zh_CN", "ja_JP", "en_US", "zh_TW", "ko_KR")
            for (key in preferred) {
                val text = javaTrim(DlsiteJsonSupport.asString(data[key]))
                if (text.isNotEmpty()) {
                    return text
                }
            }
            for (nestedValue in data.values) {
                val text = javaTrim(DlsiteJsonSupport.asString(nestedValue))
                if (text.isNotEmpty()) {
                    return text
                }
            }
            return ""
        }
        return javaTrim(DlsiteJsonSupport.asString(value))
    }

    private fun javaTrim(value: String?): String = value?.trim { it <= ' ' }.orEmpty()
}
