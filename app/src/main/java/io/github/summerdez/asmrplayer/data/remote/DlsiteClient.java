package io.github.summerdez.asmrplayer.data.remote;

import android.text.TextUtils;
import android.webkit.CookieManager;

import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption;
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class DlsiteClient {
    public static final String LOGIN_URL = DlsiteRemoteConstants.PLAY_BASE_URL + "/library";

    private final DlsiteHttpClient httpClient = new DlsiteHttpClient();
    private final DlsiteWorkRemote workRemote = new DlsiteWorkRemote(httpClient);
    private final DlsiteCoverRemote coverRemote = new DlsiteCoverRemote(httpClient);
    private final DlsiteContentRemote contentRemote = new DlsiteContentRemote(httpClient);

    public boolean hasLoginCookie() {
        String cookie = CookieManager.getInstance().getCookie(DlsiteRemoteConstants.COOKIE_URL);
        if (TextUtils.isEmpty(cookie)) {
            cookie = CookieManager.getInstance().getCookie(DlsiteRemoteConstants.DL_SITE_COOKIE_URL);
        }
        return !TextUtils.isEmpty(cookie)
                && (cookie.contains("play_session")
                || cookie.contains("loginchecked")
                || cookie.contains("uid_jp")
                || cookie.contains("eridjp"));
    }

    public List<DlsiteWork> fetchPurchasedWorks() throws IOException {
        return workRemote.fetchPurchasedWorks();
    }

    public void downloadTo(DlsiteWork work, File targetFile) throws IOException {
        contentRemote.downloadTo(work, targetFile);
    }

    public File downloadCover(DlsiteWork work, File targetDir) throws IOException {
        return coverRemote.downloadCover(work, targetDir);
    }

    public List<DlsiteDownloadOption> fetchDownloadOptions(DlsiteWork work) throws IOException {
        return contentRemote.fetchDownloadOptions(work);
    }

    public List<File> downloadWorkFiles(DlsiteWork work, File workDir) throws IOException {
        return contentRemote.downloadWorkFiles(work, workDir, "");
    }

    public List<File> downloadWorkFiles(DlsiteWork work, File workDir, String downloadOptionId) throws IOException {
        return contentRemote.downloadWorkFiles(work, workDir, downloadOptionId);
    }
}
