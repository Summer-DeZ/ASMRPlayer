package io.github.summerdez.asmrplayer.data.remote

import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.net.URI
import java.util.Locale
import java.util.regex.Pattern

object DlsiteHtmlParser {
    private val ANCHOR: Pattern = Pattern.compile("""(?is)<a\b([^>]*)>(.*?)</a>""")
    private val HREF: Pattern = Pattern.compile("""(?is)href\s*=\s*(['"])(.*?)\1""")
    private val IMAGE_ATTR: Pattern = Pattern.compile("""(?is)(?:src|data-src|content)\s*=\s*(['"])(.*?)\1""")
    private val IMAGE_URL: Pattern = Pattern.compile(
        """(?is)(?:https?:)?//[^"'<>\s]+(?:_img_main|_img_sam)[^"'<>\s]*\.(?:jpg|jpeg|png|webp)""",
    )
    private val WORK_ID: Pattern = Pattern.compile("""\b(?:RJ|RE|BJ|VJ)\d{5,10}\b""", Pattern.CASE_INSENSITIVE)

    fun parsePurchasedWorks(html: String, pageUrl: String): List<DlsiteWork> {
        val works = LinkedHashMap<String, DlsiteWork>()
        if (html.isEmpty()) {
            return ArrayList()
        }

        val anchorMatcher = ANCHOR.matcher(html)
        while (anchorMatcher.find()) {
            val attrs = anchorMatcher.group(1).orEmpty()
            val body = anchorMatcher.group(2).orEmpty()
            val href = hrefFrom(attrs)
            val text = cleanText(body)
            val workId = workIdFrom("$href $text")
            if (workId.isEmpty()) {
                continue
            }

            val absoluteHref = absoluteUrl(pageUrl, href)
            var work = works[workId]
            if (work == null) {
                work = DlsiteWork(workId, titleFrom(text, workId), "", "")
                works[workId] = work
            } else if (work.title.isEmpty() || work.title == workId) {
                work = work.withTitle(titleFrom(text, workId))
                works[workId] = work
            }

            if (looksLikeDownloadUrl(absoluteHref)) {
                works[workId] = work.withDownloadUrl(absoluteHref)
            } else if (looksLikeDetailUrl(absoluteHref)) {
                works[workId] = work.withDetailUrl(absoluteHref)
            }
        }
        return ArrayList(works.values)
    }

    fun findDownloadUrlForWork(html: String, pageUrl: String, workId: String): String {
        val anchorMatcher = ANCHOR.matcher(html)
        while (anchorMatcher.find()) {
            val attrs = anchorMatcher.group(1).orEmpty()
            val body = anchorMatcher.group(2).orEmpty()
            val href = hrefFrom(attrs)
            val haystack = "$href ${cleanText(body)}"
            if (workId.equals(workIdFrom(haystack), ignoreCase = true) && looksLikeDownloadUrl(href)) {
                return absoluteUrl(pageUrl, href)
            }
        }
        return ""
    }

    fun findFirstDownloadUrl(html: String, pageUrl: String): String {
        val anchorMatcher = ANCHOR.matcher(html)
        while (anchorMatcher.find()) {
            val href = hrefFrom(anchorMatcher.group(1).orEmpty())
            if (looksLikeDownloadUrl(href)) {
                return absoluteUrl(pageUrl, href)
            }
        }
        return ""
    }

    fun findCoverUrl(html: String, pageUrl: String, workId: String): String {
        if (html.isEmpty()) {
            return ""
        }

        val candidates = ArrayList<String>()
        val attrMatcher = IMAGE_ATTR.matcher(html)
        while (attrMatcher.find()) {
            val url = decodeHtml(attrMatcher.group(2).orEmpty()).trim()
            addCoverCandidate(candidates, pageUrl, url)
        }

        val urlMatcher = IMAGE_URL.matcher(html)
        while (urlMatcher.find()) {
            val url = decodeHtml(urlMatcher.group().orEmpty()).trim()
            addCoverCandidate(candidates, pageUrl, url)
        }

        val lowerWorkId = workId.lowercase(Locale.ROOT)
        if (lowerWorkId.isNotEmpty()) {
            for (candidate in candidates) {
                if (candidate.lowercase(Locale.ROOT).contains(lowerWorkId)) {
                    return candidate
                }
            }
        }
        return if (candidates.isEmpty()) "" else candidates[0]
    }

    private fun addCoverCandidate(candidates: MutableList<String>, pageUrl: String, url: String) {
        if (!looksLikeCoverUrl(url)) {
            return
        }
        val absolute = absoluteUrl(pageUrl, url)
        if (absolute.isNotEmpty() && !candidates.contains(absolute)) {
            candidates.add(absolute)
        }
    }

    private fun hrefFrom(attrs: String): String {
        val matcher = HREF.matcher(attrs)
        return if (matcher.find()) decodeHtml(matcher.group(2).orEmpty()).trim() else ""
    }

    private fun workIdFrom(value: String): String {
        val matcher = WORK_ID.matcher(value)
        return if (matcher.find()) matcher.group().orEmpty().uppercase(Locale.ROOT) else ""
    }

    private fun titleFrom(text: String, workId: String): String {
        var title = text.replace(workId, "").trim()
        title = title.replace(Regex("\\s+"), " ")
        return title.ifEmpty { workId }
    }

    private fun cleanText(html: String): String {
        val withoutTags = html
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?is)<[^>]+>"), " ")
        return decodeHtml(withoutTags).replace(Regex("\\s+"), " ").trim()
    }

    private fun looksLikeDownloadUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("download") ||
            lower.contains("dl_count") ||
            lower.contains("product_file") ||
            lower.contains("file_type")
    }

    private fun looksLikeDetailUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("product_id") || lower.contains("/work/") || lower.contains("dlsite.com")
    }

    private fun looksLikeCoverUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (!lower.contains("img.dlsite.jp") || !lower.contains("/work/")) {
            return false
        }
        if (!lower.contains("_img_main") && !lower.contains("_img_sam")) {
            return false
        }
        return true
    }

    private fun absoluteUrl(pageUrl: String, href: String): String {
        if (href.isEmpty()) {
            return ""
        }
        try {
            val base = URI.create(pageUrl)
            return base.resolve(href).toString()
        } catch (ignored: IllegalArgumentException) {
            return href
        }
    }

    private fun decodeHtml(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
