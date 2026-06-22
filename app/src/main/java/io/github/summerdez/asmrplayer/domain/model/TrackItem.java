package io.github.summerdez.asmrplayer.domain.model;

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
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

public final class TrackItem {
    public final String id;
    public String title;
    public final String uri;
    public String subtitleUri;
    public String subtitleTitle;
    public final long durationMs;

    public TrackItem(String id, String title, String uri) {
        this(id, title, uri, "", "");
    }

    public TrackItem(String id, String title, String uri, String subtitleUri, String subtitleTitle) {
        this(id, title, uri, subtitleUri, subtitleTitle, 0L);
    }

    public TrackItem(String id, String title, String uri, String subtitleUri, String subtitleTitle, long durationMs) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.uri = uri == null ? "" : uri;
        this.subtitleUri = subtitleUri == null ? "" : subtitleUri;
        this.subtitleTitle = subtitleTitle == null ? "" : subtitleTitle;
        this.durationMs = Math.max(0L, durationMs);
    }

    public boolean hasAudioUri() {
        return !uri.isEmpty();
    }

    public Uri audioUri() {
        return Uri.parse(uri);
    }

    public Uri subtitleUriOrNull() {
        return subtitleUri.isEmpty() ? null : Uri.parse(subtitleUri);
    }

    public String subtitleTitleOr(String fallback) {
        return subtitleTitle.isEmpty() ? fallback : subtitleTitle;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("title", title);
        object.put("uri", uri);
        object.put("subtitleUri", subtitleUri);
        object.put("subtitleTitle", subtitleTitle);
        object.put("durationMs", durationMs);
        return object;
    }

    public static TrackItem fromJson(JSONObject object) throws JSONException {
        return new TrackItem(
                object.optString("id"),
                object.optString("title"),
                object.optString("uri"),
                object.optString("subtitleUri"),
                object.optString("subtitleTitle"),
                object.optLong("durationMs", 0L));
    }
}
