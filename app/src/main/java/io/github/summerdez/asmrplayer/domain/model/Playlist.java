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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Playlist {
    public final String id;
    public String name;
    public String coverUri;
    public final List<TrackItem> tracks;

    public Playlist(String id, String name) {
        this(id, name, "", new ArrayList<>());
    }

    public Playlist(String id, String name, List<TrackItem> tracks) {
        this(id, name, "", tracks);
    }

    public Playlist(String id, String name, String coverUri, List<TrackItem> tracks) {
        this.id = id;
        this.name = name;
        this.coverUri = coverUri == null ? "" : coverUri;
        this.tracks = tracks;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("coverUri", coverUri);
        JSONArray trackArray = new JSONArray();
        for (TrackItem track : tracks) {
            trackArray.put(track.toJson());
        }
        object.put("tracks", trackArray);
        return object;
    }

    public static Playlist fromJson(JSONObject object) throws JSONException {
        JSONArray trackArray = object.optJSONArray("tracks");
        List<TrackItem> tracks = new ArrayList<>();
        if (trackArray != null) {
            for (int i = 0; i < trackArray.length(); i++) {
                JSONObject trackObject = trackArray.optJSONObject(i);
                if (trackObject != null) {
                    tracks.add(TrackItem.fromJson(trackObject));
                }
            }
        }
        return new Playlist(
                object.optString("id"),
                object.optString("name"),
                object.optString("coverUri"),
                tracks);
    }
}
