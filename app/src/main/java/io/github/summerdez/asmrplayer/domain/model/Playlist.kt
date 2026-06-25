package io.github.summerdez.asmrplayer.domain.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Playlist(
    val id: String = "",
    var name: String = "",
    var coverUri: String = "",
    val tracks: List<TrackItem> = emptyList(),
) {
    constructor(id: String, name: String, tracks: List<TrackItem>) : this(id, name, "", tracks)

    fun copy(
        id: String = this.id,
        name: String = this.name,
        coverUri: String = this.coverUri,
        tracks: List<TrackItem> = this.tracks,
    ): Playlist = Playlist(id, name, coverUri, tracks)

    @Throws(JSONException::class)
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("coverUri", coverUri)
        val trackArray = JSONArray()
        for (track in tracks) {
            trackArray.put(track.toJson())
        }
        obj.put("tracks", trackArray)
        return obj
    }

    companion object {
        @Throws(JSONException::class)
        fun fromJson(obj: JSONObject): Playlist {
            val trackArray = obj.optJSONArray("tracks")
            val tracks = ArrayList<TrackItem>()
            if (trackArray != null) {
                for (i in 0 until trackArray.length()) {
                    val trackObject = trackArray.optJSONObject(i)
                    if (trackObject != null) {
                        tracks.add(TrackItem.fromJson(trackObject))
                    }
                }
            }
            return Playlist(
                obj.optString("id"),
                obj.optString("name"),
                obj.optString("coverUri"),
                tracks,
            )
        }
    }
}
