package io.github.summerdez.asmrplayer.data.remote;

import android.text.TextUtils;

import io.github.summerdez.asmrplayer.domain.model.DlsiteWork;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DlsiteWorkRemote {
    private final DlsiteHttpClient httpClient;

    DlsiteWorkRemote(DlsiteHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    List<DlsiteWork> fetchPurchasedWorks() throws IOException {
        Map<String, DlsiteWork> byId = new LinkedHashMap<>();
        String authorization = get("/api/authorize", DlsiteRemoteConstants.PLAY_BASE_URL, "application/json, text/plain, */*");
        if (!DlsiteJsonParser.isAuthorized(authorization)) {
            throw new IOException("请先登录 DLsite");
        }

        String countJson = get(
                "/api/v3/content/count?last=0",
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                "application/json, text/plain, */*");
        DlsiteJsonParser.ContentCount count = DlsiteJsonParser.parseContentCount(countJson);

        for (DlsiteWork work : fetchUserPurchasedWorks()) {
            putDiscoveredWork(byId, work);
        }
        for (DlsiteWork work : fetchProductionWorks(count)) {
            putDiscoveredWork(byId, work);
        }

        if (byId.isEmpty()) {
            for (DlsiteWork work : fetchRecentlyViewedWorks()) {
                putDiscoveredWork(byId, work);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<DlsiteWork> fetchUserPurchasedWorks() throws IOException {
        String salesJson = get(
                "/api/v3/content/sales?last=0",
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                "application/json, text/plain, */*");
        List<String> workIds = DlsiteJsonParser.parseSalesWorkIds(salesJson);
        if (workIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<DlsiteWork> works = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < workIds.size(); index += DlsiteRemoteConstants.CONTENT_WORK_CHUNK_SIZE) {
            int end = Math.min(index + DlsiteRemoteConstants.CONTENT_WORK_CHUNK_SIZE, workIds.size());
            List<String> chunk = workIds.subList(index, end);
            String worksJson = postJsonArray(
                    "/api/v3/content/works",
                    DlsiteJsonParser.toJsonArray(chunk),
                    DlsiteRemoteConstants.PLAY_BASE_URL + "/library");
            for (DlsiteWork work : DlsiteJsonParser.parseContentWorks(worksJson)) {
                if (seen.add(work.workId)) {
                    works.add(work);
                }
            }
        }
        return works;
    }

    private List<DlsiteWork> fetchProductionWorks(DlsiteJsonParser.ContentCount count) throws IOException {
        if (count.production <= 0 || count.pageLimit <= 0) {
            return Collections.emptyList();
        }
        List<DlsiteWork> works = new ArrayList<>();
        int pages = (int) Math.ceil(count.production / (double) count.pageLimit);
        for (int page = 1; page <= pages; page++) {
            String json = get(
                    "/api/v3/content/products?page=" + page + "&last=0",
                    DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                    "application/json, text/plain, */*");
            works.addAll(DlsiteJsonParser.parseContentWorks(json));
        }
        return works;
    }

    private List<DlsiteWork> fetchRecentlyViewedWorks() throws IOException {
        String historiesJson = get(
                "/api/view_histories",
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                "application/json, text/plain, */*");
        List<String> workIds = DlsiteJsonParser.parseHistoryWorkIds(historiesJson);
        List<DlsiteWork> works = new ArrayList<>();
        for (String workId : workIds) {
            String detailJson = get(
                    "/api/v3/work/" + workId,
                    DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                    "application/json, text/plain, */*");
            DlsiteWork work = DlsiteJsonParser.parseWorkDetail(detailJson);
            if (work != null) {
                works.add(work);
            }
        }
        return works;
    }

    private void putDiscoveredWork(Map<String, DlsiteWork> byId, DlsiteWork work) {
        if (work == null || TextUtils.isEmpty(work.workId)) {
            return;
        }
        DlsiteWork existing = byId.get(work.workId);
        if (existing == null) {
            byId.put(work.workId, work);
        } else {
            byId.put(work.workId, existing.mergedWithDiscovery(work));
        }
    }

    private String get(String pathOrUrl, String referer, String accept) throws IOException {
        return httpClient.text(
                pathOrUrl,
                referer,
                accept,
                "GET",
                null,
                DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
                DlsiteRemoteConstants.READ_TIMEOUT_MS);
    }

    private String postJsonArray(String path, String jsonBody, String referer) throws IOException {
        return httpClient.text(
                path,
                referer,
                "application/json, text/plain, */*",
                "POST",
                jsonBody.getBytes(StandardCharsets.UTF_8),
                DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
                DlsiteRemoteConstants.READ_TIMEOUT_MS);
    }
}
