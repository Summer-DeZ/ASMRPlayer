package io.github.summerdez.asmrplayer.data.remote;

import io.github.summerdez.asmrplayer.R;
import io.github.summerdez.asmrplayer.data.*;
import io.github.summerdez.asmrplayer.data.remote.*;
import io.github.summerdez.asmrplayer.data.download.*;
import io.github.summerdez.asmrplayer.data.files.*;
import io.github.summerdez.asmrplayer.domain.*;
import io.github.summerdez.asmrplayer.domain.model.*;
import io.github.summerdez.asmrplayer.playback.*;
import io.github.summerdez.asmrplayer.presentation.*;
import io.github.summerdez.asmrplayer.ui.*;
import io.github.summerdez.asmrplayer.ui.activity.*;
import io.github.summerdez.asmrplayer.ui.components.*;
import io.github.summerdez.asmrplayer.ui.screens.*;
import io.github.summerdez.asmrplayer.ui.theme.*;
import io.github.summerdez.asmrplayer.ui.util.*;
import io.github.summerdez.asmrplayer.di.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DlsiteLibraryJsonParser {
    private static final String PLAY_BASE_URL = "https://play.dlsite.com";

    private DlsiteLibraryJsonParser() {
    }

    static boolean isAuthorized(String json) {
        try {
            Object root = DlsiteJsonSupport.parse(json);
            if (root instanceof Map) {
                return !((Map<?, ?>) root).isEmpty();
            }
            if (root instanceof List) {
                return !((List<?>) root).isEmpty();
            }
            return root != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static DlsiteJsonParser.ContentCount parseContentCount(String json)
            throws DlsiteJsonParser.IOExceptionLikeJsonException {
        try {
            Map<String, Object> object = DlsiteJsonSupport.asObject(DlsiteJsonSupport.parse(json));
            return new DlsiteJsonParser.ContentCount(
                    DlsiteJsonSupport.asInt(object.get("user"), 0),
                    DlsiteJsonSupport.asInt(object.get("production"), 0),
                    DlsiteJsonSupport.asInt(object.get("page_limit"), 50));
        } catch (IllegalArgumentException exception) {
            throw new DlsiteJsonParser.IOExceptionLikeJsonException("购买数量解析失败", exception);
        }
    }

    static List<String> parseSalesWorkIds(String json)
            throws DlsiteJsonParser.IOExceptionLikeJsonException {
        try {
            return parseWorkIds(json, "sales");
        } catch (IllegalArgumentException exception) {
            throw new DlsiteJsonParser.IOExceptionLikeJsonException("购买记录解析失败", exception);
        }
    }

    static List<String> parseHistoryWorkIds(String json)
            throws DlsiteJsonParser.IOExceptionLikeJsonException {
        try {
            return parseWorkIds(json, "histories");
        } catch (IllegalArgumentException exception) {
            throw new DlsiteJsonParser.IOExceptionLikeJsonException("浏览记录解析失败", exception);
        }
    }

    static List<DlsiteWork> parseContentWorks(String json)
            throws DlsiteJsonParser.IOExceptionLikeJsonException {
        try {
            List<Object> array = DlsiteJsonSupport.arrayFromRoot(DlsiteJsonSupport.parse(json), "works");
            List<DlsiteWork> works = new ArrayList<>();
            for (Object value : array) {
                DlsiteWork work = toWork(DlsiteJsonSupport.asObjectOrNull(value));
                if (work != null) {
                    works.add(work);
                }
            }
            return works;
        } catch (IllegalArgumentException exception) {
            throw new DlsiteJsonParser.IOExceptionLikeJsonException("作品解析失败", exception);
        }
    }

    static DlsiteWork parseWorkDetail(String json)
            throws DlsiteJsonParser.IOExceptionLikeJsonException {
        try {
            Map<String, Object> root = DlsiteJsonSupport.asObject(DlsiteJsonSupport.parse(json));
            Map<String, Object> work = DlsiteJsonSupport.asObjectOrNull(root.get("work"));
            return toWork(work == null ? root : work);
        } catch (IllegalArgumentException exception) {
            throw new DlsiteJsonParser.IOExceptionLikeJsonException("作品详情解析失败", exception);
        }
    }

    private static List<String> parseWorkIds(String json, String key) {
        List<Object> array = DlsiteJsonSupport.arrayFromRoot(DlsiteJsonSupport.parse(json), key);
        List<String> ids = new ArrayList<>();
        for (Object value : array) {
            Map<String, Object> object = DlsiteJsonSupport.asObjectOrNull(value);
            if (object == null) {
                continue;
            }
            String workId = DlsiteJsonSupport.asString(object.get("workno")).trim();
            if (!workId.isEmpty() && !ids.contains(workId)) {
                ids.add(workId);
            }
        }
        return ids;
    }

    private static DlsiteWork toWork(Map<String, Object> object) {
        if (object == null) {
            return null;
        }
        String workId = DlsiteJsonSupport.asString(object.get("workno")).trim();
        if (workId.isEmpty() || !isDownloadableAudio(object)) {
            return null;
        }
        String title = localizedString(object.get("name"));
        if (title.isEmpty()) {
            title = localizedString(object.get("work_name"));
        }
        if (title.isEmpty()) {
            title = DlsiteJsonSupport.asString(object.get("title")).trim();
        }
        if (title.isEmpty()) {
            title = workId;
        }
        String coverUrl = coverUrlFrom(object);
        if (coverUrl.isEmpty()) {
            coverUrl = DlsiteWork.inferredCoverUrl(workId);
        }
        return new DlsiteWork(
                workId,
                title,
                PLAY_BASE_URL + "/work/" + workId + "/tree",
                PLAY_BASE_URL + "/api/v3/download?workno=" + workId,
                coverUrl);
    }

    private static boolean isDownloadableAudio(Map<String, Object> object) {
        Boolean downloadable = DlsiteJsonSupport.asBooleanOrNull(object.get("downloadable"));
        if (Boolean.FALSE.equals(downloadable)) {
            return false;
        }
        if (object.containsKey("content_count")
                && DlsiteJsonSupport.asInt(object.get("content_count"), 0) <= 0) {
            return false;
        }
        String appType = DlsiteJsonSupport.asString(object.get("app_type")).trim();
        if ("playbox".equalsIgnoreCase(appType)) {
            return false;
        }
        if ("sound".equalsIgnoreCase(appType)) {
            return true;
        }
        String workType = DlsiteJsonSupport.asString(object.get("work_type")).trim();
        String category = DlsiteJsonSupport.asString(object.get("work_category")).trim();
        return "SOU".equalsIgnoreCase(workType)
                || "MUS".equalsIgnoreCase(workType)
                || "music".equalsIgnoreCase(category)
                || "audio".equalsIgnoreCase(category);
    }

    private static String coverUrlFrom(Map<String, Object> object) {
        String[] directKeys = new String[]{
                "coverUrl", "cover_url", "cover", "jacket", "jacket_url",
                "image_main", "imageMain", "imageMainUrl", "image_main_url",
                "main_image", "mainImage", "mainImageUrl", "main_image_url",
                "work_image", "workImage", "workImageUrl", "work_image_url",
                "thumbnail", "thumbnail_url", "thumbnailUrl",
                "thumb", "thumb_url", "thumbUrl",
                "image", "image_url", "imageUrl",
                "images", "image_urls", "imageUrls",
                "sam", "sample_image", "sampleImage"
        };
        for (String key : directKeys) {
            String url = coverUrlCandidate(object.get(key));
            if (!url.isEmpty()) {
                return url;
            }
        }
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.US);
            if (!key.contains("cover")
                    && !key.contains("jacket")
                    && !key.contains("image")
                    && !key.contains("thumb")
                    && !key.equals("sam")) {
                continue;
            }
            String url = coverUrlCandidate(entry.getValue());
            if (!url.isEmpty()) {
                return url;
            }
        }
        return "";
    }

    private static String coverUrlCandidate(Object value) {
        if (value instanceof String) {
            return normalizeCoverUrl((String) value);
        }
        List<Object> list = DlsiteJsonSupport.asListOrNull(value);
        if (list != null) {
            for (Object item : list) {
                String url = coverUrlCandidate(item);
                if (!url.isEmpty()) {
                    return url;
                }
            }
            return "";
        }
        Map<String, Object> object = DlsiteJsonSupport.asObjectOrNull(value);
        if (object == null) {
            return "";
        }

        String[] preferredKeys = new String[]{
                "url", "src", "href", "main", "large", "large_url", "largeUrl",
                "medium", "medium_url", "mediumUrl", "original", "original_url", "originalUrl",
                "work", "image", "thumbnail", "thumb", "jacket", "cover",
                "path", "file", "filename", "file_name", "sam"
        };
        for (String key : preferredKeys) {
            String url = coverUrlCandidate(object.get(key));
            if (!url.isEmpty()) {
                return url;
            }
        }
        for (Object nestedValue : object.values()) {
            String url = coverUrlCandidate(nestedValue);
            if (!url.isEmpty()) {
                return url;
            }
        }
        return "";
    }

    private static String normalizeCoverUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.isEmpty()) {
            return "";
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://")
                && !lower.startsWith("https://")
                && !lower.startsWith("//")
                && !lower.startsWith("/")) {
            return "";
        }
        try {
            return URI.create(PLAY_BASE_URL).resolve(url).toString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String localizedString(Object value) {
        Map<String, Object> object = DlsiteJsonSupport.asObjectOrNull(value);
        if (object != null) {
            String[] preferred = new String[]{"zh_CN", "ja_JP", "en_US", "zh_TW", "ko_KR"};
            for (String key : preferred) {
                String text = DlsiteJsonSupport.asString(object.get(key)).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
            for (Object nestedValue : object.values()) {
                String text = DlsiteJsonSupport.asString(nestedValue).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
            return "";
        }
        return DlsiteJsonSupport.asString(value).trim();
    }
}
