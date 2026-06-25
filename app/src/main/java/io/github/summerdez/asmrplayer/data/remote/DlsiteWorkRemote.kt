package io.github.summerdez.asmrplayer.data.remote

import android.text.TextUtils
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.math.ceil
import kotlin.math.min

class DlsiteWorkRemote(private val httpClient: DlsiteHttpClient) {
    @Throws(IOException::class)
    fun fetchPurchasedWorks(): List<DlsiteWork> {
        val byId = LinkedHashMap<String, DlsiteWork>()
        val authorization = get(
            "/api/authorize",
            DlsiteRemoteConstants.PLAY_BASE_URL,
            "application/json, text/plain, */*",
        )
        if (!DlsiteJsonParser.isAuthorized(authorization)) {
            throw IOException("请先登录 DLsite")
        }

        val countJson = get(
            "/api/v3/content/count?last=0",
            DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
            "application/json, text/plain, */*",
        )
        val count = DlsiteJsonParser.parseContentCount(countJson)

        for (work in fetchUserPurchasedWorks()) {
            putDiscoveredWork(byId, work)
        }
        for (work in fetchProductionWorks(count)) {
            putDiscoveredWork(byId, work)
        }

        if (byId.isEmpty()) {
            for (work in fetchRecentlyViewedWorks()) {
                putDiscoveredWork(byId, work)
            }
        }
        return ArrayList(byId.values)
    }

    @Throws(IOException::class)
    private fun fetchUserPurchasedWorks(): List<DlsiteWork> {
        val salesJson = get(
            "/api/v3/content/sales?last=0",
            DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
            "application/json, text/plain, */*",
        )
        val workIds = DlsiteJsonParser.parseSalesWorkIds(salesJson)
        if (workIds.isEmpty()) {
            return emptyList()
        }

        val works = ArrayList<DlsiteWork>()
        val seen = HashSet<String>()
        var index = 0
        while (index < workIds.size) {
            val end = min(index + DlsiteRemoteConstants.CONTENT_WORK_CHUNK_SIZE, workIds.size)
            val chunk = workIds.subList(index, end)
            val worksJson = postJsonArray(
                "/api/v3/content/works",
                DlsiteJsonParser.toJsonArray(chunk),
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
            )
            for (work in DlsiteJsonParser.parseContentWorks(worksJson)) {
                if (seen.add(work.workId)) {
                    works.add(work)
                }
            }
            index += DlsiteRemoteConstants.CONTENT_WORK_CHUNK_SIZE
        }
        return works
    }

    @Throws(IOException::class)
    private fun fetchProductionWorks(count: DlsiteJsonParser.ContentCount): List<DlsiteWork> {
        if (count.production <= 0 || count.pageLimit <= 0) {
            return emptyList()
        }
        val works = ArrayList<DlsiteWork>()
        val pages = ceil(count.production / count.pageLimit.toDouble()).toInt()
        for (page in 1..pages) {
            val json = get(
                "/api/v3/content/products?page=$page&last=0",
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                "application/json, text/plain, */*",
            )
            works.addAll(DlsiteJsonParser.parseContentWorks(json))
        }
        return works
    }

    @Throws(IOException::class)
    private fun fetchRecentlyViewedWorks(): List<DlsiteWork> {
        val historiesJson = get(
            "/api/view_histories",
            DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
            "application/json, text/plain, */*",
        )
        val workIds = DlsiteJsonParser.parseHistoryWorkIds(historiesJson)
        val works = ArrayList<DlsiteWork>()
        for (workId in workIds) {
            val detailJson = get(
                "/api/v3/work/$workId",
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                "application/json, text/plain, */*",
            )
            val work = DlsiteJsonParser.parseWorkDetail(detailJson)
            if (work != null) {
                works.add(work)
            }
        }
        return works
    }

    private fun putDiscoveredWork(byId: MutableMap<String, DlsiteWork>, work: DlsiteWork?) {
        val workId = work?.workId?.takeIf { !TextUtils.isEmpty(it) } ?: return
        val existing = byId[workId]
        if (existing == null) {
            byId[workId] = work
        } else {
            byId[workId] = existing.mergedWithDiscovery(work)
        }
    }

    @Throws(IOException::class)
    private fun get(pathOrUrl: String?, referer: String?, accept: String?): String {
        return httpClient.text(
            pathOrUrl,
            referer,
            accept,
            "GET",
            null,
            DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.READ_TIMEOUT_MS,
        )
    }

    @Throws(IOException::class)
    private fun postJsonArray(path: String?, jsonBody: String, referer: String?): String {
        return httpClient.text(
            path,
            referer,
            "application/json, text/plain, */*",
            "POST",
            jsonBody.toByteArray(StandardCharsets.UTF_8),
            DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
            DlsiteRemoteConstants.READ_TIMEOUT_MS,
        )
    }
}
