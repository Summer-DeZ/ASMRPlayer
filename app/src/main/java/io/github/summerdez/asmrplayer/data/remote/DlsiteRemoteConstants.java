package io.github.summerdez.asmrplayer.data.remote;

final class DlsiteRemoteConstants {
    static final String PLAY_BASE_URL = "https://play.dlsite.com";
    static final String PLAY_DOWNLOAD_BASE_URL = "https://play.dl.dlsite.com";
    static final String DL_SITE_COOKIE_URL = "https://www.dlsite.com";
    static final String COOKIE_URL = PLAY_BASE_URL;
    static final String DEFAULT_REVISION = "00000000-0000-7000-8000-000000000000";
    static final int CONNECT_TIMEOUT_MS = 20_000;
    static final int READ_TIMEOUT_MS = 45_000;
    static final int COVER_CONNECT_TIMEOUT_MS = 8_000;
    static final int COVER_READ_TIMEOUT_MS = 12_000;
    static final int CONTENT_WORK_CHUNK_SIZE = 100;

    private DlsiteRemoteConstants() {
    }
}
