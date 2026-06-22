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
import android.text.TextUtils;
import android.webkit.CookieManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class DlsiteHttpClient {
    private static final String PLAY_BASE_URL = "https://play.dlsite.com";
    private static final String PLAY_DOWNLOAD_BASE_URL = "https://play.dl.dlsite.com";
    private static final String DL_SITE_COOKIE_URL = "https://www.dlsite.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .cookieJar(new WebViewCookieJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    String text(
            String pathOrUrl,
            String referer,
            String accept,
            String method,
            byte[] body,
            int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        try (Response response = execute(pathOrUrl, referer, accept, method, body, connectTimeoutMs, readTimeoutMs)) {
            return successfulText(response, "请求失败");
        }
    }

    Response execute(
            String pathOrUrl,
            String referer,
            String accept,
            String method,
            byte[] body,
            int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        OkHttpClient requestClient = client.newBuilder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        Request.Builder builder = new Request.Builder()
                .url(pathOrUrl.startsWith("http") ? pathOrUrl : PLAY_BASE_URL + pathOrUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Language", "ja,en-US;q=0.8,en;q=0.6,zh-CN;q=0.5")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Origin", PLAY_BASE_URL);
        if (!TextUtils.isEmpty(referer)) {
            builder.header("Referer", referer);
        }
        if ("POST".equalsIgnoreCase(method)) {
            builder.post(RequestBody.create(body == null ? new byte[0] : body, JSON));
        } else {
            builder.get();
        }
        return requestClient.newCall(builder.build()).execute();
    }

    static String successfulText(Response response, String fallbackMessage) throws IOException {
        String body = bodyString(response);
        if (!response.isSuccessful()) {
            throw new IOException(body.isEmpty()
                    ? fallbackMessage + ": HTTP " + response.code()
                    : fallbackMessage + ": HTTP " + response.code() + " " + body);
        }
        return body;
    }

    static String bodyString(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private static final class WebViewCookieJar implements CookieJar {
        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            if (cookies.isEmpty()) {
                return;
            }
            CookieManager cookieManager = CookieManager.getInstance();
            for (Cookie cookie : cookies) {
                cookieManager.setCookie(url.toString(), cookie.toString());
            }
            cookieManager.flush();
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            LinkedHashSet<String> cookieHeaders = new LinkedHashSet<>();
            appendCookies(cookieHeaders, CookieManager.getInstance().getCookie(url.toString()));
            appendCookies(cookieHeaders, CookieManager.getInstance().getCookie(PLAY_BASE_URL));
            appendCookies(cookieHeaders, CookieManager.getInstance().getCookie(PLAY_DOWNLOAD_BASE_URL));
            appendCookies(cookieHeaders, CookieManager.getInstance().getCookie(DL_SITE_COOKIE_URL));
            if (cookieHeaders.isEmpty()) {
                return Collections.emptyList();
            }
            List<Cookie> cookies = new ArrayList<>();
            for (String cookieHeader : cookieHeaders) {
                Cookie cookie = Cookie.parse(url, cookieHeader);
                if (cookie != null) {
                    cookies.add(cookie);
                }
            }
            return cookies;
        }

        private void appendCookies(Set<String> cookies, String cookieHeader) {
            if (TextUtils.isEmpty(cookieHeader)) {
                return;
            }
            String[] parts = cookieHeader.split(";");
            for (String part : parts) {
                String cookie = part.trim();
                if (!cookie.isEmpty()) {
                    cookies.add(cookie);
                }
            }
        }
    }
}
