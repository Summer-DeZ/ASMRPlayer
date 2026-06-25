package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import org.json.JSONArray
import org.json.JSONObject

interface TranslationRequestExecutor {
    suspend fun execute(settings: AiSubtitleSettings, request: TranslationRequest): TranslationResponse
}

data class TranslationMessage(
    val role: String,
    val content: String,
)

data class TranslationRequest(
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val messages: List<TranslationMessage>,
    val responseFormatJsonObject: Boolean,
    val disableThinking: Boolean,
) {
    fun toJsonObject(): JSONObject {
        val root = JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
            .put(
                "messages",
                JSONArray().also { array ->
                    messages.forEach { message ->
                        array.put(JSONObject().put("role", message.role).put("content", message.content))
                    }
                },
            )
        if (responseFormatJsonObject) {
            root.put("response_format", JSONObject().put("type", "json_object"))
        }
        if (disableThinking) {
            root.put("thinking", JSONObject().put("type", "disabled"))
        }
        return root
    }
}

data class TranslationResponse(
    val content: String,
    val finishReason: String,
)
