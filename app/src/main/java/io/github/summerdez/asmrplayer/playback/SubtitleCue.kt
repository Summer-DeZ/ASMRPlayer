package io.github.summerdez.asmrplayer.playback

class SubtitleCue(
    @JvmField val startMs: Long,
    @JvmField val endMs: Long,
    text: String?,
) {
    @JvmField
    val text: String = preserveJavaFieldValue(text)

    private companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T> preserveJavaFieldValue(value: T?): T = value as T
    }
}
