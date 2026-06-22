package io.github.summerdez.asmrplayer.domain

import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*
import java.util.Locale

object DlsiteDownloadPlanner {
    @JvmStatic
    fun optionsFor(ziptree: DlsiteJsonParser.DlsiteZiptree?): List<DlsiteDownloadOption> {
        val files = ziptree?.audioFiles.orEmpty()
        if (files.isEmpty()) {
            return emptyList()
        }

        val byTrackName = linkedMapOf<String, MutableList<DlsiteJsonParser.ContentFile>>()
        files.forEach { file ->
            byTrackName.getOrPut(logicalTrackName(file)) { mutableListOf() }.add(file)
        }

        val byOption = linkedMapOf<String, MutableList<DlsiteJsonParser.ContentFile>>()
        val sharedFiles = mutableListOf<DlsiteJsonParser.ContentFile>()
        byTrackName.values.forEach { trackGroup ->
            val buckets = variantBuckets(trackGroup)
            if (!isVersionedTrack(buckets)) {
                sharedFiles.addAll(trackGroup)
                return@forEach
            }
            buckets.values.forEach { bucket ->
                byOption.getOrPut(bucket.key) { mutableListOf() }.addAll(bucket.files)
            }
        }

        if (byOption.size <= 1) {
            return defaultOption(files)
        }

        return byOption.map { (key, optionFiles) ->
            DlsiteDownloadOption(key, displayTitle(key), optionFiles + sharedFiles)
        }
    }

    private fun defaultOption(files: List<DlsiteJsonParser.ContentFile>): List<DlsiteDownloadOption> {
        return listOf(DlsiteDownloadOption("", "默认版本", files))
    }

    private fun variantBuckets(
        files: List<DlsiteJsonParser.ContentFile>,
    ): LinkedHashMap<String, VariantBucket> {
        val shape = PathShape.from(files)
        val buckets = linkedMapOf<String, VariantBucket>()
        files.forEach { file ->
            val signature = VariantSignature.from(file, shape)
            val bucket = buckets.getOrPut(signature.key) { VariantBucket(signature) }
            bucket.files.add(file)
            bucket.merge(signature)
        }
        return buckets
    }

    private fun isVersionedTrack(buckets: LinkedHashMap<String, VariantBucket>): Boolean {
        if (buckets.size <= 1) {
            return false
        }
        val formats = mutableSetOf<String>()
        var hasPathFormatMarker = false
        var hasVersionToken = false
        buckets.values.forEach { bucket ->
            if (bucket.format.isNotEmpty()) {
                formats.add(bucket.format)
            }
            hasPathFormatMarker = hasPathFormatMarker || bucket.hasPathFormatMarker
            hasVersionToken = hasVersionToken || bucket.hasVersionToken
        }
        return formats.size > 1 || hasPathFormatMarker || hasVersionToken
    }

    private fun logicalTrackName(file: DlsiteJsonParser.ContentFile?): String {
        val name = file?.displayName.orEmpty()
        val dot = name.lastIndexOf('.')
        return normalized(if (dot > 0) name.substring(0, dot) else name)
    }

    private fun displayTitle(key: String): String {
        val title = key
            .split("/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" / ") { value ->
                if (isAudioFormat(value)) value.uppercase(Locale.US) else value
            }
        return title.ifEmpty { "默认版本" }
    }

    private fun pathSegments(path: String?): List<String> {
        if (path.isNullOrEmpty()) {
            return emptyList()
        }
        return path.split("/").filter { it.isNotEmpty() }
    }

    private fun parentSegments(file: DlsiteJsonParser.ContentFile?): List<String> {
        val segments = pathSegments(file?.displayPath)
        if (segments.size <= 1) {
            return emptyList()
        }
        return segments.subList(0, segments.size - 1)
    }

    private fun commonPrefixLength(paths: List<List<String>>): Int {
        var length = 0
        while (true) {
            var candidate: String? = null
            for (path in paths) {
                if (path.size <= length) {
                    return length
                }
                val value = normalized(path[length])
                if (candidate == null) {
                    candidate = value
                } else if (candidate != value) {
                    return length
                }
            }
            length++
        }
    }

    private fun commonSuffixLength(paths: List<List<String>>, prefixLength: Int): Int {
        var length = 0
        while (true) {
            var candidate: String? = null
            for (path in paths) {
                val index = path.size - length - 1
                if (index < prefixLength) {
                    return length
                }
                val value = normalized(path[index])
                if (candidate == null) {
                    candidate = value
                } else if (candidate != value) {
                    return length
                }
            }
            length++
        }
    }

    private fun commonSegmentNames(
        paths: List<List<String>>,
        prefixLength: Int,
        suffixLength: Int,
    ): Set<String> {
        var common: MutableSet<String>? = null
        paths.forEach { path ->
            val names = linkedSetOf<String>()
            val end = path.size - suffixLength
            for (index in prefixLength until end) {
                val name = normalized(path[index])
                if (name.isNotEmpty()) {
                    names.add(name)
                }
            }
            if (common == null) {
                common = names
            } else {
                common.retainAll(names)
            }
        }
        return common ?: emptySet()
    }

    private fun audioExtension(file: DlsiteJsonParser.ContentFile?): String {
        val name = file?.displayName.orEmpty()
        val dot = name.lastIndexOf('.')
        return if (dot >= 0 && dot < name.length - 1) {
            canonicalFormat(name.substring(dot + 1))
        } else {
            ""
        }
    }

    private fun pathAudioFormat(segments: List<String>): String {
        segments.forEach { segment ->
            if (isAudioFormat(segment)) {
                return canonicalFormat(segment)
            }
        }
        return ""
    }

    private fun isVersionToken(value: String): Boolean {
        val token = normalized(value)
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
        if (token.isEmpty()) {
            return false
        }
        return token.contains("効果音") ||
            token.contains("效果音") ||
            token.contains("環境音") ||
            token.contains("环境音") ||
            token.contains("bgm") ||
            token == "se" ||
            token == "nose" ||
            token.contains("seなし") ||
            token.contains("se無し") ||
            token.contains("無se") ||
            token.contains("无se") ||
            token == "なし" ||
            token == "無し" ||
            token == "あり" ||
            token.contains("通常") ||
            token.contains("差分") ||
            token.contains("高音質") ||
            token.contains("高音质") ||
            token.contains("バイノーラル") ||
            token.contains("binaural")
    }

    private fun isAudioFormat(value: String): Boolean {
        return when (normalized(value)) {
            "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma" -> true
            else -> false
        }
    }

    private fun canonicalFormat(value: String): String = normalized(value)

    private fun normalized(value: String?): String = value?.trim()?.lowercase(Locale.ROOT).orEmpty()

    private data class PathShape(
        val commonPrefixLength: Int,
        val commonSuffixLength: Int,
        val commonSegmentNames: Set<String>,
    ) {
        companion object {
            fun from(files: List<DlsiteJsonParser.ContentFile>): PathShape {
                val paths = files.map { parentSegments(it) }
                val prefix = commonPrefixLength(paths)
                val suffix = commonSuffixLength(paths, prefix)
                return PathShape(prefix, suffix, commonSegmentNames(paths, prefix, suffix))
            }
        }
    }

    private data class VariantSignature(
        val key: String,
        val format: String,
        val hasPathFormatMarker: Boolean,
        val hasVersionToken: Boolean,
    ) {
        companion object {
            fun from(file: DlsiteJsonParser.ContentFile, shape: PathShape): VariantSignature {
                val parents = parentSegments(file)
                val pathFormat = pathAudioFormat(parents)
                val format = pathFormat.ifEmpty { audioExtension(file) }
                var hasVersionToken = false
                val keySegments = mutableListOf<String>()
                if (format.isNotEmpty()) {
                    keySegments.add(format)
                }

                val end = maxOf(shape.commonPrefixLength, parents.size - shape.commonSuffixLength)
                for (index in shape.commonPrefixLength until end) {
                    val segment = parents[index]
                    val normalizedSegment = normalized(segment)
                    if (normalizedSegment.isEmpty() ||
                        isAudioFormat(normalizedSegment) ||
                        shape.commonSegmentNames.contains(normalizedSegment)
                    ) {
                        continue
                    }
                    if (isVersionToken(segment)) {
                        hasVersionToken = true
                    }
                    keySegments.add(segment.trim())
                }

                if (keySegments.isEmpty()) {
                    keySegments.add("default")
                }
                return VariantSignature(
                    key = joinKey(keySegments),
                    format = format,
                    hasPathFormatMarker = pathFormat.isNotEmpty(),
                    hasVersionToken = hasVersionToken,
                )
            }

            private fun joinKey(segments: List<String>): String {
                val key = segments
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("/")
                return key.ifEmpty { "default" }
            }
        }
    }

    private class VariantBucket(signature: VariantSignature) {
        val key: String = signature.key
        val files: MutableList<DlsiteJsonParser.ContentFile> = mutableListOf()
        var format: String = ""
        var hasPathFormatMarker: Boolean = false
        var hasVersionToken: Boolean = false

        init {
            merge(signature)
        }

        fun merge(signature: VariantSignature) {
            if (format.isEmpty()) {
                format = signature.format
            }
            hasPathFormatMarker = hasPathFormatMarker || signature.hasPathFormatMarker
            hasVersionToken = hasVersionToken || signature.hasVersionToken
        }
    }
}
