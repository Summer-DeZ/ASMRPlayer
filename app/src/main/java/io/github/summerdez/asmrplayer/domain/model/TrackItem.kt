package io.github.summerdez.asmrplayer.domain.model

import android.net.Uri
import org.json.JSONException
import org.json.JSONObject

class TrackItem(
    val id: String = "",
    var title: String = "",
    val uri: String = "",
    var subtitleUri: String = "",
    var subtitleTitle: String = "",
    durationMs: Long = 0L,
) {
    val durationMs: Long = maxOf(0L, durationMs)

    fun hasAudioUri(): Boolean = uri.isNotEmpty()

    fun audioUri(): Uri = Uri.parse(uri)

    fun subtitleUriOrNull(): Uri? = if (subtitleUri.isEmpty()) null else Uri.parse(subtitleUri)

    fun subtitleTitleOr(fallback: String): String = if (subtitleTitle.isEmpty()) fallback else subtitleTitle

    @Throws(JSONException::class)
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("uri", uri)
        obj.put("subtitleUri", subtitleUri)
        obj.put("subtitleTitle", subtitleTitle)
        obj.put("durationMs", durationMs)
        return obj
    }

    companion object {
        @Throws(JSONException::class)
        fun fromJson(obj: JSONObject): TrackItem {
            return TrackItem(
                obj.optString("id"),
                obj.optString("title"),
                obj.optString("uri"),
                obj.optString("subtitleUri"),
                obj.optString("subtitleTitle"),
                obj.optLong("durationMs", 0L),
            )
        }
    }
}
