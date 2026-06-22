package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.security.MessageDigest

internal fun aiSubtitleSourceSignature(lines: List<SubtitleLine>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    lines.forEach { line ->
        digest.update(line.id.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(line.startMs.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(line.endMs.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(line.sourceText.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
