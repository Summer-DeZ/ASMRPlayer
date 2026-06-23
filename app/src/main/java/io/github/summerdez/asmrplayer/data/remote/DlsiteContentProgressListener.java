package io.github.summerdez.asmrplayer.data.remote;

public interface DlsiteContentProgressListener {
    void onProgress(DlsiteJsonParser.ContentFile contentFile, long fileBytesDownloaded, long fileTotalBytes);
}
