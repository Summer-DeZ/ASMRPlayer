package io.github.summerdez.asmrplayer.data

import android.content.Context
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import io.github.summerdez.asmrplayer.ui.theme.AppThemeMode
import io.github.summerdez.asmrplayer.ui.theme.AppUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

interface SettingsRepository {
    val aiSubtitleSettingsFlow: Flow<AiSubtitleSettings>

    fun themeMode(): AppThemeMode
    fun setThemeMode(mode: AppThemeMode)
    suspend fun aiSubtitleSettings(): AiSubtitleSettings
    suspend fun setAiTranscriptionBackend(backend: AiTranscriptionBackend)
    suspend fun setAiTranslationEngine(engine: AiTranslationEngine)
    suspend fun setAiOllamaBaseUrl(value: String)
    suspend fun setAiOllamaModel(value: String)
    suspend fun setAiDeepSeekBaseUrl(value: String)
    suspend fun setAiDeepSeekModel(value: String)
    suspend fun setAiDeepSeekApiKey(value: String)
    suspend fun setAiWhisperModelId(value: String)
    suspend fun setAiRemoteWhisperBaseUrl(value: String)
    suspend fun setAiRemoteWhisperModel(value: String)
    suspend fun setAiRemoteWhisperToken(value: String)
    suspend fun setAiAdultContentTranslationAllowed(value: Boolean)
}

class AppSettingsRepository(
    context: Context,
    private val settingsDao: AppSettingsDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsRepository {
    private val appContext = context.applicationContext
    override val aiSubtitleSettingsFlow: Flow<AiSubtitleSettings> = combine(
        listOf(
            settingsDao.valueFlow(KEY_AI_TRANSLATION_ENGINE),
            settingsDao.valueFlow(KEY_AI_OLLAMA_BASE_URL),
            settingsDao.valueFlow(KEY_AI_OLLAMA_MODEL),
            settingsDao.valueFlow(KEY_AI_DEEPSEEK_BASE_URL),
            settingsDao.valueFlow(KEY_AI_DEEPSEEK_MODEL),
            settingsDao.valueFlow(KEY_AI_DEEPSEEK_API_KEY),
            settingsDao.valueFlow(KEY_AI_WHISPER_MODEL_ID),
            settingsDao.valueFlow(KEY_AI_ADULT_CONTENT_TRANSLATION),
            settingsDao.valueFlow(KEY_AI_TRANSCRIPTION_BACKEND),
            settingsDao.valueFlow(KEY_AI_REMOTE_WHISPER_BASE_URL),
            settingsDao.valueFlow(KEY_AI_REMOTE_WHISPER_MODEL),
            settingsDao.valueFlow(KEY_AI_REMOTE_WHISPER_TOKEN),
        ),
    ) { values ->
        aiSettingsFromValues(values)
    }

    override fun themeMode(): AppThemeMode {
        return AppUi.themeMode()
    }

    override fun setThemeMode(mode: AppThemeMode) {
        AppUi.setThemeMode(appContext, mode)
    }

    override suspend fun aiSubtitleSettings(): AiSubtitleSettings {
        return withContext(ioDispatcher) {
            aiSettingsFromValues(
                arrayOf(
                    settingsDao.value(KEY_AI_TRANSLATION_ENGINE),
                    settingsDao.value(KEY_AI_OLLAMA_BASE_URL),
                    settingsDao.value(KEY_AI_OLLAMA_MODEL),
                    settingsDao.value(KEY_AI_DEEPSEEK_BASE_URL),
                    settingsDao.value(KEY_AI_DEEPSEEK_MODEL),
                    settingsDao.value(KEY_AI_DEEPSEEK_API_KEY),
                    settingsDao.value(KEY_AI_WHISPER_MODEL_ID),
                    settingsDao.value(KEY_AI_ADULT_CONTENT_TRANSLATION),
                    settingsDao.value(KEY_AI_TRANSCRIPTION_BACKEND),
                    settingsDao.value(KEY_AI_REMOTE_WHISPER_BASE_URL),
                    settingsDao.value(KEY_AI_REMOTE_WHISPER_MODEL),
                    settingsDao.value(KEY_AI_REMOTE_WHISPER_TOKEN),
                ),
            )
        }
    }

    override suspend fun setAiTranscriptionBackend(backend: AiTranscriptionBackend) {
        put(KEY_AI_TRANSCRIPTION_BACKEND, backend.name)
    }

    override suspend fun setAiTranslationEngine(engine: AiTranslationEngine) {
        put(KEY_AI_TRANSLATION_ENGINE, engine.name)
    }

    override suspend fun setAiOllamaBaseUrl(value: String) {
        put(KEY_AI_OLLAMA_BASE_URL, value.trim())
    }

    override suspend fun setAiOllamaModel(value: String) {
        put(KEY_AI_OLLAMA_MODEL, value.trim())
    }

    override suspend fun setAiDeepSeekBaseUrl(value: String) {
        put(KEY_AI_DEEPSEEK_BASE_URL, value.trim())
    }

    override suspend fun setAiDeepSeekModel(value: String) {
        put(KEY_AI_DEEPSEEK_MODEL, value.trim())
    }

    override suspend fun setAiDeepSeekApiKey(value: String) {
        put(KEY_AI_DEEPSEEK_API_KEY, value.trim())
    }

    override suspend fun setAiWhisperModelId(value: String) {
        put(KEY_AI_WHISPER_MODEL_ID, WhisperModelSpec.byId(value).id)
    }

    override suspend fun setAiRemoteWhisperBaseUrl(value: String) {
        put(KEY_AI_REMOTE_WHISPER_BASE_URL, value.trim())
    }

    override suspend fun setAiRemoteWhisperModel(value: String) {
        put(KEY_AI_REMOTE_WHISPER_MODEL, value.trim())
    }

    override suspend fun setAiRemoteWhisperToken(value: String) {
        put(KEY_AI_REMOTE_WHISPER_TOKEN, value.trim())
    }

    override suspend fun setAiAdultContentTranslationAllowed(value: Boolean) {
        put(KEY_AI_ADULT_CONTENT_TRANSLATION, value.toString())
    }

    private suspend fun put(key: String, value: String) {
        withContext(ioDispatcher) {
            settingsDao.put(AppSettingEntity(key, value))
        }
    }

    private fun aiSettingsFromValues(values: Array<String?>): AiSubtitleSettings {
        val engine = values.getOrNull(0).orEmpty()
            .let { raw -> AiTranslationEngine.entries.firstOrNull { it.name == raw } }
            ?: AiTranslationEngine.OLLAMA
        val transcriptionBackend = values.getOrNull(8).orEmpty()
            .let { raw -> AiTranscriptionBackend.entries.firstOrNull { it.name == raw } }
            ?: AiTranscriptionBackend.LOCAL
        return AiSubtitleSettings(
            transcriptionBackend = transcriptionBackend,
            translationEngine = engine,
            ollamaBaseUrl = values.getOrNull(1).orEmpty().ifBlank { AiTranslationEngine.OLLAMA.defaultBaseUrl },
            ollamaModel = values.getOrNull(2).orEmpty().ifBlank { AiTranslationEngine.OLLAMA.defaultModel },
            deepSeekBaseUrl = values.getOrNull(3).orEmpty().ifBlank { AiTranslationEngine.DEEPSEEK.defaultBaseUrl },
            deepSeekModel = normalizedOpenAiCompatibleModel(values.getOrNull(4)),
            deepSeekApiKey = values.getOrNull(5).orEmpty(),
            whisperModelId = WhisperModelSpec.byId(values.getOrNull(6)).id,
            allowAdultContentTranslation = values.getOrNull(7)?.toBooleanStrictOrNull() ?: false,
            remoteWhisperBaseUrl = values.getOrNull(9).orEmpty(),
            remoteWhisperModel = values.getOrNull(10).orEmpty(),
            remoteWhisperToken = values.getOrNull(11).orEmpty(),
        )
    }

    private companion object {
        const val KEY_AI_TRANSLATION_ENGINE = "ai_translation_engine"
        const val KEY_AI_OLLAMA_BASE_URL = "ai_ollama_base_url"
        const val KEY_AI_OLLAMA_MODEL = "ai_ollama_model"
        const val KEY_AI_DEEPSEEK_BASE_URL = "ai_deepseek_base_url"
        const val KEY_AI_DEEPSEEK_MODEL = "ai_deepseek_model"
        const val KEY_AI_DEEPSEEK_API_KEY = "ai_deepseek_api_key"
        const val KEY_AI_WHISPER_MODEL_ID = "ai_whisper_model_id"
        const val KEY_AI_ADULT_CONTENT_TRANSLATION = "ai_adult_content_translation"
        const val KEY_AI_TRANSCRIPTION_BACKEND = "ai_transcription_backend"
        const val KEY_AI_REMOTE_WHISPER_BASE_URL = "ai_remote_whisper_base_url"
        const val KEY_AI_REMOTE_WHISPER_MODEL = "ai_remote_whisper_model"
        const val KEY_AI_REMOTE_WHISPER_TOKEN = "ai_remote_whisper_token"
    }
}

internal fun normalizedOpenAiCompatibleModel(value: String?): String {
    return value.orEmpty().trim().ifBlank { AiTranslationEngine.DEEPSEEK.defaultModel }
}
