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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DlsiteJsonParser {
    private DlsiteJsonParser() {
    }

    public static boolean isAuthorized(String json) {
        return DlsiteLibraryJsonParser.isAuthorized(json);
    }

    public static ContentCount parseContentCount(String json) throws IOExceptionLikeJsonException {
        return DlsiteLibraryJsonParser.parseContentCount(json);
    }

    public static List<String> parseSalesWorkIds(String json) throws IOExceptionLikeJsonException {
        return DlsiteLibraryJsonParser.parseSalesWorkIds(json);
    }

    public static List<String> parseHistoryWorkIds(String json) throws IOExceptionLikeJsonException {
        return DlsiteLibraryJsonParser.parseHistoryWorkIds(json);
    }

    public static List<DlsiteWork> parseContentWorks(String json) throws IOExceptionLikeJsonException {
        return DlsiteLibraryJsonParser.parseContentWorks(json);
    }

    public static DlsiteWork parseWorkDetail(String json) throws IOExceptionLikeJsonException {
        return DlsiteLibraryJsonParser.parseWorkDetail(json);
    }

    public static String toJsonArray(List<String> values) {
        return DlsiteDownloadJsonParser.toJsonArray(values);
    }

    public static DlsiteZiptree parseZiptree(String json) throws IOExceptionLikeJsonException {
        return DlsiteDownloadJsonParser.parseZiptree(json);
    }

    public static Map<String, String> parseSignUrlParams(String json) throws IOExceptionLikeJsonException {
        return DlsiteDownloadJsonParser.parseSignUrlParams(json);
    }

    public static String parseWebvttJson(String json) throws IOExceptionLikeJsonException {
        return DlsiteWebvttJsonParser.parseWebvttJson(json);
    }

    public static final class ContentCount {
        public final int user;
        public final int production;
        public final int pageLimit;

        public ContentCount(int user, int production, int pageLimit) {
            this.user = user;
            this.production = production;
            this.pageLimit = pageLimit;
        }
    }

    public static final class IOExceptionLikeJsonException extends java.io.IOException {
        public IOExceptionLikeJsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class DlsiteZiptree {
        public final String workId;
        public final String revision;
        public final List<ContentFile> audioFiles;

        public DlsiteZiptree(String workId, String revision, List<ContentFile> audioFiles) {
            this.workId = workId == null ? "" : workId;
            this.revision = revision == null ? "" : revision;
            this.audioFiles = audioFiles == null ? new ArrayList<>() : audioFiles;
        }
    }

    public static final class ContentFile {
        public final String displayPath;
        public final String displayName;
        public final String contentPath;
        public final String subtitleContentPath;
        public final String subtitleName;
        public final long lengthBytes;

        public ContentFile(
                String displayPath,
                String displayName,
                String contentPath,
                String subtitleContentPath,
                String subtitleName) {
            this(displayPath, displayName, contentPath, subtitleContentPath, subtitleName, 0L);
        }

        public ContentFile(
                String displayPath,
                String displayName,
                String contentPath,
                String subtitleContentPath,
                String subtitleName,
                long lengthBytes) {
            this.displayPath = displayPath == null ? "" : displayPath;
            this.displayName = displayName == null ? "" : displayName;
            this.contentPath = contentPath == null ? "" : contentPath;
            this.subtitleContentPath = subtitleContentPath == null ? "" : subtitleContentPath;
            this.subtitleName = subtitleName == null ? "" : subtitleName;
            this.lengthBytes = Math.max(0L, lengthBytes);
        }
    }
}
