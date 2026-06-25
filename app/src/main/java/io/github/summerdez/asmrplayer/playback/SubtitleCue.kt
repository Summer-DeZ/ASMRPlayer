package io.github.summerdez.asmrplayer.playback

class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    text: String?,
) {
    val text: String = preserveJavaFieldValue(text)

    private companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T> preserveJavaFieldValue(value: T?): T = value as T
    }
}
