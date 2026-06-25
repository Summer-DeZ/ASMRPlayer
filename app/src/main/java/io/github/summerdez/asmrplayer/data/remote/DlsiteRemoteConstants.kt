package io.github.summerdez.asmrplayer.data.remote

object DlsiteRemoteConstants {
    const val PLAY_BASE_URL: String = "https://play.dlsite.com"
    const val PLAY_DOWNLOAD_BASE_URL: String = "https://play.dl.dlsite.com"
    const val DL_SITE_COOKIE_URL: String = "https://www.dlsite.com"
    const val COOKIE_URL: String = PLAY_BASE_URL
    const val USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
    const val ACCEPT_LANGUAGE: String = "ja,en-US;q=0.8,en;q=0.6,zh-CN;q=0.5"
    const val DEFAULT_REVISION: String = "00000000-0000-7000-8000-000000000000"
    const val CONNECT_TIMEOUT_MS: Int = 20_000
    const val READ_TIMEOUT_MS: Int = 45_000
    const val DEFAULT_CONNECT_TIMEOUT_MS: Int = CONNECT_TIMEOUT_MS
    const val DEFAULT_READ_TIMEOUT_MS: Int = READ_TIMEOUT_MS
    const val COVER_CONNECT_TIMEOUT_MS: Int = 8_000
    const val COVER_READ_TIMEOUT_MS: Int = 12_000
    const val CONTENT_WORK_CHUNK_SIZE: Int = 100
}
