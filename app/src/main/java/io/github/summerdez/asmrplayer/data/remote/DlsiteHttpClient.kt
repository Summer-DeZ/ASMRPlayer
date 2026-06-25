package io.github.summerdez.asmrplayer.data.remote

import android.webkit.CookieManager
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class DlsiteHttpClient {
    private val client = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Throws(IOException::class)
    fun text(
        pathOrUrl: String?,
        referer: String?,
        accept: String?,
        method: String?,
        body: ByteArray?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): String {
        execute(pathOrUrl, referer, accept, method, body, connectTimeoutMs, readTimeoutMs).use { response ->
            return successfulText(response, "请求失败")
        }
    }

    @Throws(IOException::class)
    fun execute(
        pathOrUrl: String?,
        referer: String?,
        accept: String?,
        method: String?,
        body: ByteArray?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): Response {
        val rawPathOrUrl = pathOrUrl ?: throw NullPointerException("pathOrUrl")
        val acceptHeader = accept ?: throw NullPointerException("accept")
        val requestClient = client.newBuilder()
            .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val builder = Request.Builder()
            .url(if (rawPathOrUrl.startsWith("http")) rawPathOrUrl else PLAY_BASE_URL + rawPathOrUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", acceptHeader)
            .header("Accept-Language", "ja,en-US;q=0.8,en;q=0.6,zh-CN;q=0.5")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Origin", PLAY_BASE_URL)
        if (!referer.isNullOrEmpty()) {
            builder.header("Referer", referer)
        }
        if (method.equals("POST", ignoreCase = true)) {
            builder.post((body ?: ByteArray(0)).toRequestBody(JSON))
        } else {
            builder.get()
        }
        return requestClient.newCall(builder.build()).execute()
    }

    companion object {
        private const val PLAY_BASE_URL = "https://play.dlsite.com"
        private const val PLAY_DOWNLOAD_BASE_URL = "https://play.dl.dlsite.com"
        private const val DL_SITE_COOKIE_URL = "https://www.dlsite.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        @JvmStatic
        @Throws(IOException::class)
        fun successfulText(response: Response?, fallbackMessage: String?): String {
            val actualResponse = response ?: throw NullPointerException("response")
            val body = bodyString(actualResponse)
            if (!actualResponse.isSuccessful) {
                throw IOException(
                    if (body.isEmpty()) {
                        "$fallbackMessage: HTTP ${actualResponse.code}"
                    } else {
                        "$fallbackMessage: HTTP ${actualResponse.code} $body"
                    },
                )
            }
            return body
        }

        @JvmStatic
        @Throws(IOException::class)
        fun bodyString(response: Response?): String {
            val actualResponse = response ?: throw NullPointerException("response")
            val body = actualResponse.body
            return body?.string() ?: ""
        }
    }

    private class WebViewCookieJar : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) {
                return
            }
            val cookieManager = CookieManager.getInstance()
            for (cookie in cookies) {
                cookieManager.setCookie(url.toString(), cookie.toString())
            }
            cookieManager.flush()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookieHeaders = LinkedHashSet<String>()
            appendCookies(cookieHeaders, CookieManager.getInstance().getCookie(url.toString()))
            appendCookies(cookieHeaders, CookieManager.getInstance().getCookie(PLAY_BASE_URL))
            appendCookies(cookieHeaders, CookieManager.getInstance().getCookie(PLAY_DOWNLOAD_BASE_URL))
            appendCookies(cookieHeaders, CookieManager.getInstance().getCookie(DL_SITE_COOKIE_URL))
            if (cookieHeaders.isEmpty()) {
                return emptyList()
            }
            val cookies = ArrayList<Cookie>()
            for (cookieHeader in cookieHeaders) {
                val cookie = Cookie.parse(url, cookieHeader)
                if (cookie != null) {
                    cookies.add(cookie)
                }
            }
            return cookies
        }

        private fun appendCookies(cookies: MutableSet<String>, cookieHeader: String?) {
            if (cookieHeader.isNullOrEmpty()) {
                return
            }
            val parts = cookieHeader.split(";")
            for (part in parts) {
                val cookie = part.trim()
                if (cookie.isNotEmpty()) {
                    cookies.add(cookie)
                }
            }
        }
    }
}
