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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlsiteHtmlParser {
    private static final Pattern ANCHOR = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a>");
    private static final Pattern HREF = Pattern.compile("(?is)href\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern IMAGE_ATTR = Pattern.compile("(?is)(?:src|data-src|content)\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern IMAGE_URL = Pattern.compile(
            "(?is)(?:https?:)?//[^\"'<>\\s]+(?:_img_main|_img_sam)[^\"'<>\\s]*\\.(?:jpg|jpeg|png|webp)");
    private static final Pattern WORK_ID = Pattern.compile("\\b(?:RJ|RE|BJ|VJ)\\d{5,10}\\b", Pattern.CASE_INSENSITIVE);

    private DlsiteHtmlParser() {
    }

    public static List<DlsiteWork> parsePurchasedWorks(String html, String pageUrl) {
        Map<String, DlsiteWork> works = new LinkedHashMap<>();
        if (html == null || html.isEmpty()) {
            return new ArrayList<>();
        }

        Matcher anchorMatcher = ANCHOR.matcher(html);
        while (anchorMatcher.find()) {
            String attrs = anchorMatcher.group(1);
            String body = anchorMatcher.group(2);
            String href = hrefFrom(attrs);
            String text = cleanText(body);
            String workId = workIdFrom(href + " " + text);
            if (workId.isEmpty()) {
                continue;
            }

            String absoluteHref = absoluteUrl(pageUrl, href);
            DlsiteWork work = works.get(workId);
            if (work == null) {
                work = new DlsiteWork(workId, titleFrom(text, workId), "", "");
                works.put(workId, work);
            } else if (work.title.isEmpty() || work.title.equals(workId)) {
                work = work.withTitle(titleFrom(text, workId));
                works.put(workId, work);
            }

            if (looksLikeDownloadUrl(absoluteHref)) {
                works.put(workId, work.withDownloadUrl(absoluteHref));
            } else if (looksLikeDetailUrl(absoluteHref)) {
                works.put(workId, work.withDetailUrl(absoluteHref));
            }
        }
        return new ArrayList<>(works.values());
    }

    public static String findDownloadUrlForWork(String html, String pageUrl, String workId) {
        if (html == null || workId == null) {
            return "";
        }
        Matcher anchorMatcher = ANCHOR.matcher(html);
        while (anchorMatcher.find()) {
            String attrs = anchorMatcher.group(1);
            String body = anchorMatcher.group(2);
            String href = hrefFrom(attrs);
            String haystack = href + " " + cleanText(body);
            if (workId.equalsIgnoreCase(workIdFrom(haystack)) && looksLikeDownloadUrl(href)) {
                return absoluteUrl(pageUrl, href);
            }
        }
        return "";
    }

    public static String findFirstDownloadUrl(String html, String pageUrl) {
        if (html == null) {
            return "";
        }
        Matcher anchorMatcher = ANCHOR.matcher(html);
        while (anchorMatcher.find()) {
            String href = hrefFrom(anchorMatcher.group(1));
            if (looksLikeDownloadUrl(href)) {
                return absoluteUrl(pageUrl, href);
            }
        }
        return "";
    }

    public static String findCoverUrl(String html, String pageUrl, String workId) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        List<String> candidates = new ArrayList<>();
        Matcher attrMatcher = IMAGE_ATTR.matcher(html);
        while (attrMatcher.find()) {
            String url = decodeHtml(attrMatcher.group(2)).trim();
            addCoverCandidate(candidates, pageUrl, url);
        }

        Matcher urlMatcher = IMAGE_URL.matcher(html);
        while (urlMatcher.find()) {
            String url = decodeHtml(urlMatcher.group()).trim();
            addCoverCandidate(candidates, pageUrl, url);
        }

        String lowerWorkId = workId == null ? "" : workId.toLowerCase(Locale.ROOT);
        if (!lowerWorkId.isEmpty()) {
            for (String candidate : candidates) {
                if (candidate.toLowerCase(Locale.ROOT).contains(lowerWorkId)) {
                    return candidate;
                }
            }
        }
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    private static void addCoverCandidate(List<String> candidates, String pageUrl, String url) {
        if (!looksLikeCoverUrl(url)) {
            return;
        }
        String absolute = absoluteUrl(pageUrl, url);
        if (!absolute.isEmpty() && !candidates.contains(absolute)) {
            candidates.add(absolute);
        }
    }

    private static String hrefFrom(String attrs) {
        Matcher matcher = HREF.matcher(attrs == null ? "" : attrs);
        return matcher.find() ? decodeHtml(matcher.group(2)).trim() : "";
    }

    private static String workIdFrom(String value) {
        Matcher matcher = WORK_ID.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : "";
    }

    private static String titleFrom(String text, String workId) {
        String title = text == null ? "" : text.replace(workId, "").trim();
        title = title.replaceAll("\\s+", " ");
        return title.isEmpty() ? workId : title;
    }

    private static String cleanText(String html) {
        String withoutTags = (html == null ? "" : html)
                .replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", " ");
        return decodeHtml(withoutTags).replaceAll("\\s+", " ").trim();
    }

    private static boolean looksLikeDownloadUrl(String url) {
        String lower = (url == null ? "" : url).toLowerCase(Locale.ROOT);
        return lower.contains("download")
                || lower.contains("dl_count")
                || lower.contains("product_file")
                || lower.contains("file_type");
    }

    private static boolean looksLikeDetailUrl(String url) {
        String lower = (url == null ? "" : url).toLowerCase(Locale.ROOT);
        return lower.contains("product_id") || lower.contains("/work/") || lower.contains("dlsite.com");
    }

    private static boolean looksLikeCoverUrl(String url) {
        String lower = (url == null ? "" : url).toLowerCase(Locale.ROOT);
        if (!lower.contains("img.dlsite.jp") || !lower.contains("/work/")) {
            return false;
        }
        if (!lower.contains("_img_main") && !lower.contains("_img_sam")) {
            return false;
        }
        return true;
    }

    private static String absoluteUrl(String pageUrl, String href) {
        if (href == null || href.isEmpty()) {
            return "";
        }
        try {
            URI base = URI.create(pageUrl);
            return base.resolve(href).toString();
        } catch (IllegalArgumentException ignored) {
            return href;
        }
    }

    private static String decodeHtml(String value) {
        return (value == null ? "" : value)
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
    }
}
