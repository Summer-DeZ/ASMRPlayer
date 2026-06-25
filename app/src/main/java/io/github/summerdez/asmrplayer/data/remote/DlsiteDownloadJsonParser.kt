package io.github.summerdez.asmrplayer.data.remote

import java.util.LinkedHashMap

object DlsiteDownloadJsonParser {
    fun toJsonArray(values: List<String?>?): String {
        val builder = StringBuilder("[")
        if (values != null) {
            for (i in values.indices) {
                if (i > 0) {
                    builder.append(',')
                }
                builder.append('"')
                    .append(DlsiteJsonSupport.escapeJson(values[i]))
                    .append('"')
            }
        }
        return builder.append(']').toString()
    }

    @Throws(DlsiteJsonParser.IOExceptionLikeJsonException::class)
    fun parseZiptree(json: String?): DlsiteJsonParser.DlsiteZiptree {
        try {
            val root = DlsiteJsonSupport.asObject(DlsiteJsonSupport.parse(json))
            val playFiles = DlsiteJsonSupport.asObject(root["playfile"])
            val audioFiles = ArrayList<DlsiteJsonParser.ContentFile>()
            collectAudioFiles(DlsiteJsonSupport.arrayFromRoot(root, "tree"), playFiles, "", audioFiles)
            return DlsiteJsonParser.DlsiteZiptree(
                javaTrim(DlsiteJsonSupport.asString(root["workno"])),
                javaTrim(DlsiteJsonSupport.asString(root["revision"])),
                audioFiles,
            )
        } catch (exception: IllegalArgumentException) {
            throw DlsiteJsonParser.IOExceptionLikeJsonException("DLsite 文件树解析失败", exception)
        }
    }

    @Throws(DlsiteJsonParser.IOExceptionLikeJsonException::class)
    fun parseSignUrlParams(json: String?): Map<String, String> {
        try {
            val root = DlsiteJsonSupport.asObject(DlsiteJsonSupport.parse(json))
            var params = DlsiteJsonSupport.asObjectOrNull(root["params"])
            if (params == null) {
                params = DlsiteJsonSupport.asObjectOrNull(root["parameters"])
            }
            if (params == null) {
                params = root
            }
            val values = LinkedHashMap<String, String>()
            for ((key, rawValue) in params) {
                val value = DlsiteJsonSupport.asQueryValue(rawValue)
                if (value.isNotEmpty()) {
                    values[key] = value
                }
            }
            return values
        } catch (exception: IllegalArgumentException) {
            throw DlsiteJsonParser.IOExceptionLikeJsonException("DLsite 签名参数解析失败", exception)
        }
    }

    private fun collectAudioFiles(
        tree: List<Any?>,
        playFiles: Map<String, Any?>,
        parentPath: String,
        output: MutableList<DlsiteJsonParser.ContentFile>,
    ) {
        for (value in tree) {
            val item = DlsiteJsonSupport.asObjectOrNull(value) ?: continue
            val type = javaTrim(DlsiteJsonSupport.asString(item["type"]))
            val name = javaTrim(DlsiteJsonSupport.asString(item["name"]))
            if (type == "folder") {
                val nextPath = joinPath(parentPath, name)
                collectAudioFiles(DlsiteJsonSupport.asListOrEmpty(item["children"]), playFiles, nextPath, output)
                continue
            }
            if (type != "file") {
                continue
            }
            val hashName = javaTrim(DlsiteJsonSupport.asString(item["hashname"]))
            val playFile = DlsiteJsonSupport.asObjectOrNull(playFiles[hashName])
            if (playFile == null || javaTrim(DlsiteJsonSupport.asString(playFile["type"])) != "audio") {
                continue
            }
            val audio = DlsiteJsonSupport.asObjectOrNull(playFile["audio"])
            val optimized = if (audio == null) {
                null
            } else {
                DlsiteJsonSupport.asObjectOrNull(audio["optimized"])
            }
            val optimizedName = if (optimized == null) {
                ""
            } else {
                javaTrim(DlsiteJsonSupport.asString(optimized["name"]))
            }
            if (optimizedName.isEmpty()) {
                continue
            }
            val lengthBytes = if (optimized == null) {
                0L
            } else {
                DlsiteJsonSupport.asLong(optimized["length"], 0L)
            }
            val displayName = if (name.isEmpty()) optimizedName else name
            var subtitleContentPath = ""
            val subtitleHash = if (audio == null) {
                ""
            } else {
                javaTrim(DlsiteJsonSupport.asString(audio["vtt"]))
            }
            if (subtitleHash.isNotEmpty()) {
                subtitleContentPath = subtitleContentPath(playFiles, subtitleHash)
            }
            output.add(
                DlsiteJsonParser.ContentFile(
                    joinPath(parentPath, displayName),
                    displayName,
                    "optimized/$optimizedName",
                    subtitleContentPath,
                    "$displayName.vtt",
                    lengthBytes,
                ),
            )
        }
    }

    private fun subtitleContentPath(playFiles: Map<String, Any?>, subtitleHash: String): String {
        val playFile = DlsiteJsonSupport.asObjectOrNull(playFiles[subtitleHash]) ?: return ""
        val type = javaTrim(DlsiteJsonSupport.asString(playFile["type"]))
        val typed = DlsiteJsonSupport.asObjectOrNull(playFile[type])
        val optimized = if (typed == null) {
            null
        } else {
            DlsiteJsonSupport.asObjectOrNull(typed["optimized"])
        }
        val optimizedName = if (optimized == null) {
            ""
        } else {
            javaTrim(DlsiteJsonSupport.asString(optimized["name"]))
        }
        return if (optimizedName.isEmpty()) "" else "optimized/$optimizedName"
    }

    private fun joinPath(parent: String?, child: String?): String {
        val safeChild = javaTrim(child ?: "")
        if (parent == null || parent.isEmpty()) {
            return safeChild
        }
        if (safeChild.isEmpty()) {
            return parent
        }
        return "$parent/$safeChild"
    }

    private fun javaTrim(value: String): String = value.trim { it <= ' ' }
}
