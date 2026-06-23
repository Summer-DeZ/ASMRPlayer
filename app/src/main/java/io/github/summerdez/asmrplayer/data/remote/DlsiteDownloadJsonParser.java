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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DlsiteDownloadJsonParser {
    private DlsiteDownloadJsonParser() {
    }

    static String toJsonArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append('"').append(DlsiteJsonSupport.escapeJson(values.get(i))).append('"');
            }
        }
        return builder.append(']').toString();
    }

    static DlsiteJsonParser.DlsiteZiptree parseZiptree(String json)
            throws DlsiteJsonParser.IOExceptionLikeJsonException {
        try {
            Map<String, Object> root = DlsiteJsonSupport.asObject(DlsiteJsonSupport.parse(json));
            Map<String, Object> playFiles = DlsiteJsonSupport.asObject(root.get("playfile"));
            List<DlsiteJsonParser.ContentFile> audioFiles = new ArrayList<>();
            collectAudioFiles(DlsiteJsonSupport.arrayFromRoot(root, "tree"), playFiles, "", audioFiles);
            return new DlsiteJsonParser.DlsiteZiptree(
                    DlsiteJsonSupport.asString(root.get("workno")).trim(),
                    DlsiteJsonSupport.asString(root.get("revision")).trim(),
                    audioFiles);
        } catch (IllegalArgumentException exception) {
            throw new DlsiteJsonParser.IOExceptionLikeJsonException("DLsite 文件树解析失败", exception);
        }
    }

    static Map<String, String> parseSignUrlParams(String json)
            throws DlsiteJsonParser.IOExceptionLikeJsonException {
        try {
            Map<String, Object> root = DlsiteJsonSupport.asObject(DlsiteJsonSupport.parse(json));
            Map<String, Object> params = DlsiteJsonSupport.asObjectOrNull(root.get("params"));
            if (params == null) {
                params = DlsiteJsonSupport.asObjectOrNull(root.get("parameters"));
            }
            if (params == null) {
                params = root;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String value = DlsiteJsonSupport.asQueryValue(entry.getValue());
                if (!value.isEmpty()) {
                    values.put(entry.getKey(), value);
                }
            }
            return values;
        } catch (IllegalArgumentException exception) {
            throw new DlsiteJsonParser.IOExceptionLikeJsonException("DLsite 签名参数解析失败", exception);
        }
    }

    private static void collectAudioFiles(
            List<Object> tree,
            Map<String, Object> playFiles,
            String parentPath,
            List<DlsiteJsonParser.ContentFile> output) {
        for (Object value : tree) {
            Map<String, Object> item = DlsiteJsonSupport.asObjectOrNull(value);
            if (item == null) {
                continue;
            }
            String type = DlsiteJsonSupport.asString(item.get("type")).trim();
            String name = DlsiteJsonSupport.asString(item.get("name")).trim();
            if ("folder".equals(type)) {
                String nextPath = joinPath(parentPath, name);
                collectAudioFiles(DlsiteJsonSupport.asListOrEmpty(item.get("children")), playFiles, nextPath, output);
                continue;
            }
            if (!"file".equals(type)) {
                continue;
            }
            String hashName = DlsiteJsonSupport.asString(item.get("hashname")).trim();
            Map<String, Object> playFile = DlsiteJsonSupport.asObjectOrNull(playFiles.get(hashName));
            if (playFile == null || !"audio".equals(DlsiteJsonSupport.asString(playFile.get("type")).trim())) {
                continue;
            }
            Map<String, Object> audio = DlsiteJsonSupport.asObjectOrNull(playFile.get("audio"));
            Map<String, Object> optimized = audio == null
                    ? null
                    : DlsiteJsonSupport.asObjectOrNull(audio.get("optimized"));
            String optimizedName = optimized == null
                    ? ""
                    : DlsiteJsonSupport.asString(optimized.get("name")).trim();
            if (optimizedName.isEmpty()) {
                continue;
            }
            long lengthBytes = optimized == null ? 0L : DlsiteJsonSupport.asLong(optimized.get("length"), 0L);
            String displayName = name.isEmpty() ? optimizedName : name;
            String subtitleContentPath = "";
            String subtitleHash = audio == null ? "" : DlsiteJsonSupport.asString(audio.get("vtt")).trim();
            if (!subtitleHash.isEmpty()) {
                subtitleContentPath = subtitleContentPath(playFiles, subtitleHash);
            }
            output.add(new DlsiteJsonParser.ContentFile(
                    joinPath(parentPath, displayName),
                    displayName,
                    "optimized/" + optimizedName,
                    subtitleContentPath,
                    displayName + ".vtt",
                    lengthBytes));
        }
    }

    private static String subtitleContentPath(Map<String, Object> playFiles, String subtitleHash) {
        Map<String, Object> playFile = DlsiteJsonSupport.asObjectOrNull(playFiles.get(subtitleHash));
        if (playFile == null) {
            return "";
        }
        String type = DlsiteJsonSupport.asString(playFile.get("type")).trim();
        Map<String, Object> typed = DlsiteJsonSupport.asObjectOrNull(playFile.get(type));
        Map<String, Object> optimized = typed == null
                ? null
                : DlsiteJsonSupport.asObjectOrNull(typed.get("optimized"));
        String optimizedName = optimized == null ? "" : DlsiteJsonSupport.asString(optimized.get("name")).trim();
        return optimizedName.isEmpty() ? "" : "optimized/" + optimizedName;
    }

    private static String joinPath(String parent, String child) {
        String safeChild = child == null ? "" : child.trim();
        if (parent == null || parent.isEmpty()) {
            return safeChild;
        }
        if (safeChild.isEmpty()) {
            return parent;
        }
        return parent + "/" + safeChild;
    }
}
