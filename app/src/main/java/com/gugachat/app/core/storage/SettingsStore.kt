package com.gugachat.app.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.UiScalePreset
import com.gugachat.app.core.util.CoreAppJson
import com.gugachat.app.core.util.ServerUrlUtils
import com.gugachat.app.data.api.SseRetryPolicy
import com.gugachat.app.data.api.UpdateChannel
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.OutputLengthPolicy
import com.gugachat.app.domain.chat.RetrievalSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "gugachat_settings")

@Serializable
data class WebDavSyncConfig(
    val url: String = "",
    val username: String = "",
    val syncDirectImages: Boolean = false,
    val autoSyncOnBackground: Boolean = false,
    val lastSyncAt: Long = 0L,
)

fun RetrievalSettings.sanitizedForPlaintextStorage(): RetrievalSettings =
    SettingsStore.sanitizeRetrievalSettingsForPlaintextStorage(this)

data class AppSettings(
    val serverUrl: String = "",
    val themeStyle: ThemeStyle = ThemeStyle.GUGUGA,
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val uiScale: UiScalePreset = UiScalePreset.DEFAULT,
    val language: AppLanguage = AppLanguage.ZH,
    val memoryBudget: String = "4K",
    val sseAutoRetryCount: Int = SseRetryPolicy.DEFAULT_SSE_AUTO_RETRY_COUNT,
    val updateChannel: UpdateChannel = UpdateChannel.AUTO,
    val lastChatGroupId: Long = 0L,
    val lastChatModel: String = "",
    val lastImageKeyId: Long = 0L,
    val lastImageModel: String = "",
    val directModeActive: Boolean = false,
    val hasChosenEntryMode: Boolean = false,
    val preferredEntryMode: String = "",
    val readerPaper: String = "cream",
    val readerFontSizeSp: Int = 17,
    val readerLineHeight: Float = 1.75f,
    val readerSerif: Boolean = true,
    val readerIndent: Boolean = true,
    val defaultContextWindowOverride: Int? = null,
    val defaultMaxHistoryMessages: Int? = null,
    val defaultMaxOutputTokens: Int? = null,
    val defaultMinOutputChat: Int? = null,
    val defaultMinOutputRp: Int? = null,
    val defaultMinOutputNovel: Int? = null,
    val defaultAutoContinueShort: Boolean = false,
    val retrievalSettings: RetrievalSettings = RetrievalSettings(),
    val tokenCalibrationMap: Map<String, Double> = emptyMap(),
    val lastStudioSubtab: Int? = null,
    val webDavConfig: WebDavSyncConfig = WebDavSyncConfig(),
) {
    fun defaultMinOutputFor(mode: ConversationMode): Int? = when (mode) {
        ConversationMode.chat -> defaultMinOutputChat
        ConversationMode.rp -> defaultMinOutputRp
        ConversationMode.novel -> defaultMinOutputNovel
    }
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val UI_SCALE = stringPreferencesKey("ui_scale")
        val LANGUAGE = stringPreferencesKey("language")
        val MEMORY_BUDGET = stringPreferencesKey("memory_budget")
        val SSE_AUTO_RETRY_COUNT = intPreferencesKey("sse_auto_retry_count")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val LAST_CHAT_GROUP_ID = longPreferencesKey("last_chat_group_id")
        val LAST_CHAT_MODEL = stringPreferencesKey("last_chat_model")
        val LAST_IMAGE_KEY_ID = longPreferencesKey("last_image_key_id")
        val LAST_IMAGE_MODEL = stringPreferencesKey("last_image_model")
        val DIRECT_MODE_ACTIVE = booleanPreferencesKey("direct_mode_active")
        val HAS_CHOSEN_ENTRY_MODE = booleanPreferencesKey("has_chosen_entry_mode")
        val PREFERRED_ENTRY_MODE = stringPreferencesKey("preferred_entry_mode")
        val READER_PAPER = stringPreferencesKey("reader_paper")
        val READER_FONT_SIZE_SP = intPreferencesKey("reader_font_size_sp")
        val READER_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val READER_SERIF = booleanPreferencesKey("reader_serif")
        val READER_INDENT = booleanPreferencesKey("reader_indent")
        val DEFAULT_CONTEXT_WINDOW_OVERRIDE = intPreferencesKey("default_context_window_override")
        val DEFAULT_MAX_HISTORY_MESSAGES = intPreferencesKey("default_max_history_messages")
        val DEFAULT_MAX_OUTPUT_TOKENS = intPreferencesKey("default_max_output_tokens")
        val DEFAULT_MIN_OUTPUT_CHAT = intPreferencesKey("default_min_output_chat")
        val DEFAULT_MIN_OUTPUT_RP = intPreferencesKey("default_min_output_rp")
        val DEFAULT_MIN_OUTPUT_NOVEL = intPreferencesKey("default_min_output_novel")
        val DEFAULT_AUTO_CONTINUE_SHORT = booleanPreferencesKey("default_auto_continue_short")
        val RETRIEVAL_SETTINGS_JSON = stringPreferencesKey("retrieval_settings_json")
        val TOKEN_CALIBRATION_MAP_JSON = stringPreferencesKey("token_calibration_map_json")
        val LAST_STUDIO_SUBTAB = intPreferencesKey("last_studio_subtab")
        val WEBDAV_CONFIG_JSON = stringPreferencesKey("webdav_config_json")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            serverUrl = prefs[Keys.SERVER_URL].orEmpty(),
            themeStyle = ThemeStyle.fromWire(prefs[Keys.THEME_STYLE]),
            colorMode = ColorMode.fromWire(prefs[Keys.COLOR_MODE]),
            uiScale = UiScalePreset.fromWire(prefs[Keys.UI_SCALE]),
            language = AppLanguage.fromTag(prefs[Keys.LANGUAGE]),
            memoryBudget = normalizeMemoryBudget(prefs[Keys.MEMORY_BUDGET]),
            sseAutoRetryCount = SseRetryPolicy.clampRetryCount(
                prefs[Keys.SSE_AUTO_RETRY_COUNT] ?: SseRetryPolicy.DEFAULT_SSE_AUTO_RETRY_COUNT,
            ),
            updateChannel = UpdateChannel.fromWire(prefs[Keys.UPDATE_CHANNEL]),
            lastChatGroupId = prefs[Keys.LAST_CHAT_GROUP_ID] ?: 0L,
            lastChatModel = prefs[Keys.LAST_CHAT_MODEL].orEmpty(),
            lastImageKeyId = prefs[Keys.LAST_IMAGE_KEY_ID] ?: 0L,
            lastImageModel = prefs[Keys.LAST_IMAGE_MODEL].orEmpty(),
            directModeActive = prefs[Keys.DIRECT_MODE_ACTIVE] ?: false,
            hasChosenEntryMode = prefs[Keys.HAS_CHOSEN_ENTRY_MODE] ?: false,
            preferredEntryMode = prefs[Keys.PREFERRED_ENTRY_MODE].orEmpty(),
            readerPaper = prefs[Keys.READER_PAPER]?.takeIf { it in setOf("cream", "parchment", "sage", "night") } ?: "cream",
            readerFontSizeSp = (prefs[Keys.READER_FONT_SIZE_SP] ?: 17).coerceIn(13, 24),
            readerLineHeight = (prefs[Keys.READER_LINE_HEIGHT] ?: 1.75f).coerceIn(1.4f, 2.2f),
            readerSerif = prefs[Keys.READER_SERIF] ?: true,
            readerIndent = prefs[Keys.READER_INDENT] ?: true,
            defaultContextWindowOverride = prefs[Keys.DEFAULT_CONTEXT_WINDOW_OVERRIDE]?.takeIf { it >= 4096 },
            defaultMaxHistoryMessages = prefs[Keys.DEFAULT_MAX_HISTORY_MESSAGES]?.takeIf { it > 0 },
            defaultMaxOutputTokens = prefs[Keys.DEFAULT_MAX_OUTPUT_TOKENS]?.takeIf { it >= 256 },
            defaultMinOutputChat = OutputLengthPolicy.normalizeMinOutputChars(prefs[Keys.DEFAULT_MIN_OUTPUT_CHAT]),
            defaultMinOutputRp = OutputLengthPolicy.normalizeMinOutputChars(prefs[Keys.DEFAULT_MIN_OUTPUT_RP]),
            defaultMinOutputNovel = OutputLengthPolicy.normalizeMinOutputChars(prefs[Keys.DEFAULT_MIN_OUTPUT_NOVEL]),
            defaultAutoContinueShort = prefs[Keys.DEFAULT_AUTO_CONTINUE_SHORT] ?: false,
            retrievalSettings = decodeRetrievalSettings(prefs[Keys.RETRIEVAL_SETTINGS_JSON]),
            tokenCalibrationMap = decodeTokenCalibrationMap(prefs[Keys.TOKEN_CALIBRATION_MAP_JSON]),
            lastStudioSubtab = prefs[Keys.LAST_STUDIO_SUBTAB]?.takeIf { it >= 0 },
            webDavConfig = decodeWebDavConfig(prefs[Keys.WEBDAV_CONFIG_JSON]),
        )
    }.distinctUntilChanged()

    suspend fun currentSettings(): AppSettings = settingsFlow.first()

    suspend fun setServerUrl(rawUrl: String): String {
        val normalized = ServerUrlUtils.normalizeServerUrl(rawUrl)
        context.settingsDataStore.edit { it[Keys.SERVER_URL] = normalized }
        return normalized
    }

    suspend fun setThemeStyle(style: ThemeStyle) {
        context.settingsDataStore.edit { it[Keys.THEME_STYLE] = style.wireValue }
    }

    suspend fun setColorMode(mode: ColorMode) {
        context.settingsDataStore.edit { it[Keys.COLOR_MODE] = mode.wireValue }
    }

    suspend fun setUiScale(preset: UiScalePreset) {
        context.settingsDataStore.edit { it[Keys.UI_SCALE] = preset.wireValue }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = language.tag }
    }

    suspend fun setMemoryBudget(budget: String) {
        context.settingsDataStore.edit { it[Keys.MEMORY_BUDGET] = normalizeMemoryBudget(budget) }
    }

    suspend fun setSseAutoRetryCount(count: Int) {
        val clamped = SseRetryPolicy.clampRetryCount(count)
        context.settingsDataStore.edit { it[Keys.SSE_AUTO_RETRY_COUNT] = clamped }
    }

    suspend fun setUpdateChannel(channel: UpdateChannel) {
        context.settingsDataStore.edit { it[Keys.UPDATE_CHANNEL] = channel.wireValue }
    }

    suspend fun setLastChatSelection(groupId: Long, model: String) {
        context.settingsDataStore.edit {
            it[Keys.LAST_CHAT_GROUP_ID] = groupId
            it[Keys.LAST_CHAT_MODEL] = model
        }
    }

    suspend fun setLastImageSelection(keyId: Long, model: String) {
        context.settingsDataStore.edit {
            it[Keys.LAST_IMAGE_KEY_ID] = keyId
            it[Keys.LAST_IMAGE_MODEL] = model
        }
    }

    suspend fun setDirectModeActive(active: Boolean) {
        context.settingsDataStore.edit { it[Keys.DIRECT_MODE_ACTIVE] = active }
    }

    suspend fun setPreferredEntryMode(mode: String) {
        context.settingsDataStore.edit {
            it[Keys.HAS_CHOSEN_ENTRY_MODE] = true
            it[Keys.PREFERRED_ENTRY_MODE] = mode
        }
    }

    suspend fun setReaderPrefs(
        paper: String? = null,
        fontSizeSp: Int? = null,
        lineHeight: Float? = null,
        serif: Boolean? = null,
        indent: Boolean? = null,
    ) {
        context.settingsDataStore.edit { prefs ->
            paper?.takeIf { it in setOf("cream", "parchment", "sage", "night") }?.let { prefs[Keys.READER_PAPER] = it }
            fontSizeSp?.let { prefs[Keys.READER_FONT_SIZE_SP] = it.coerceIn(13, 24) }
            lineHeight?.let { prefs[Keys.READER_LINE_HEIGHT] = it.coerceIn(1.4f, 2.2f) }
            serif?.let { prefs[Keys.READER_SERIF] = it }
            indent?.let { prefs[Keys.READER_INDENT] = it }
        }
    }

    suspend fun setDefaultContextPrefs(
        contextWindowOverride: Int?,
        maxHistoryMessages: Int?,
        maxOutputTokens: Int?,
    ) {
        context.settingsDataStore.edit { prefs ->
            val cleanWin = contextWindowOverride?.coerceIn(4096, 2_000_000) ?: 0
            val cleanHist = maxHistoryMessages?.coerceIn(1, 500) ?: 0
            val cleanOut = maxOutputTokens?.coerceIn(256, 131_072) ?: 0
            prefs[Keys.DEFAULT_CONTEXT_WINDOW_OVERRIDE] = cleanWin
            prefs[Keys.DEFAULT_MAX_HISTORY_MESSAGES] = cleanHist
            prefs[Keys.DEFAULT_MAX_OUTPUT_TOKENS] = cleanOut
        }
    }

    suspend fun defaultMinOutputFor(mode: ConversationMode): Int? =
        currentSettings().defaultMinOutputFor(mode)

    suspend fun setDefaultMinOutputPrefs(
        mode: ConversationMode,
        minOutputChars: Int?,
        autoContinueShortReply: Boolean? = null,
    ) {
        context.settingsDataStore.edit { prefs ->
            val clean = OutputLengthPolicy.normalizeMinOutputChars(minOutputChars) ?: 0
            when (mode) {
                ConversationMode.chat -> prefs[Keys.DEFAULT_MIN_OUTPUT_CHAT] = clean
                ConversationMode.rp -> prefs[Keys.DEFAULT_MIN_OUTPUT_RP] = clean
                ConversationMode.novel -> prefs[Keys.DEFAULT_MIN_OUTPUT_NOVEL] = clean
            }
            if (autoContinueShortReply != null) {
                prefs[Keys.DEFAULT_AUTO_CONTINUE_SHORT] = autoContinueShortReply
            }
        }
    }

    suspend fun setAllDefaultMinOutputPrefs(
        chatMinOutput: Int?,
        rpMinOutput: Int?,
        novelMinOutput: Int?,
        autoContinueShort: Boolean,
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_MIN_OUTPUT_CHAT] = OutputLengthPolicy.normalizeMinOutputChars(chatMinOutput) ?: 0
            prefs[Keys.DEFAULT_MIN_OUTPUT_RP] = OutputLengthPolicy.normalizeMinOutputChars(rpMinOutput) ?: 0
            prefs[Keys.DEFAULT_MIN_OUTPUT_NOVEL] = OutputLengthPolicy.normalizeMinOutputChars(novelMinOutput) ?: 0
            prefs[Keys.DEFAULT_AUTO_CONTINUE_SHORT] = autoContinueShort
        }
    }

    suspend fun setRetrievalSettings(
        settings: RetrievalSettings,
        secretVaultStore: SecretVaultStore? = null,
    ) {
        val toPersist = if (secretVaultStore != null) {
            secretVaultStore.setRemoteEmbeddingApiKey(settings.remoteEmbedding.customApiKey)
            secretVaultStore.setRemoteRerankApiKey(settings.remoteRerank.customApiKey)
            settings.sanitizedForPlaintextStorage()
        } else {
            settings
        }
        val encoded = CoreAppJson.encodeToString(RetrievalSettings.serializer(), toPersist)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.RETRIEVAL_SETTINGS_JSON] = encoded
        }
    }

    suspend fun setLastStudioSubtab(index: Int) {
        context.settingsDataStore.edit { prefs ->
            if (index < 0) {
                prefs.remove(Keys.LAST_STUDIO_SUBTAB)
            } else {
                prefs[Keys.LAST_STUDIO_SUBTAB] = index
            }
        }
    }

    suspend fun setWebDavConfig(config: WebDavSyncConfig) {
        val encoded = CoreAppJson.encodeToString(WebDavSyncConfig.serializer(), config)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.WEBDAV_CONFIG_JSON] = encoded
        }
    }

    suspend fun migratePlaintextRetrievalKeysIfNeeded(secretVaultStore: SecretVaultStore) {
        context.settingsDataStore.edit { prefs ->
            val raw = prefs[Keys.RETRIEVAL_SETTINGS_JSON]?.trim().orEmpty()
            if (raw.isEmpty()) return@edit
            val decoded = decodeRetrievalSettings(raw)
            val embedKey = decoded.remoteEmbedding.customApiKey
            val rerankKey = decoded.remoteRerank.customApiKey
            if (embedKey.isEmpty() && rerankKey.isEmpty()) return@edit
            if (embedKey.isNotBlank() && secretVaultStore.getRemoteEmbeddingApiKey().isBlank()) {
                secretVaultStore.setRemoteEmbeddingApiKey(embedKey)
            }
            if (rerankKey.isNotBlank() && secretVaultStore.getRemoteRerankApiKey().isBlank()) {
                secretVaultStore.setRemoteRerankApiKey(rerankKey)
            }
            prefs[Keys.RETRIEVAL_SETTINGS_JSON] = CoreAppJson.encodeToString(
                RetrievalSettings.serializer(),
                decoded.sanitizedForPlaintextStorage(),
            )
        }
    }

    suspend fun updateTokenCalibration(key: String, factor: Double) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty() || !factor.isFinite()) return
        context.settingsDataStore.edit { prefs ->
            val existing = decodeTokenCalibrationMap(prefs[Keys.TOKEN_CALIBRATION_MAP_JSON])
            val updated = updateTokenCalibrationMap(existing, cleanKey, factor)
            prefs[Keys.TOKEN_CALIBRATION_MAP_JSON] = CoreAppJson.encodeToString(
                TOKEN_CAL_MAP_SERIALIZER,
                updated,
            )
        }
    }

    companion object {
        const val MIN_TOKEN_CALIBRATION = 0.60
        const val MAX_TOKEN_CALIBRATION = 1.60
        const val MAX_TOKEN_CALIBRATION_ENTRIES = 32

        private val TOKEN_CAL_MAP_SERIALIZER = MapSerializer(String.serializer(), Double.serializer())

        fun normalizeMemoryBudget(raw: String?): String = when (raw?.trim()?.lowercase()) {
            "low", "2k" -> "2K"
            "high", "8k" -> "8K"
            else -> "4K"
        }

        fun sanitizeRetrievalSettingsForPlaintextStorage(settings: RetrievalSettings): RetrievalSettings =
            settings.copy(
                remoteEmbedding = settings.remoteEmbedding.copy(customApiKey = ""),
                remoteRerank = settings.remoteRerank.copy(customApiKey = ""),
            )

        internal fun decodeRetrievalSettings(raw: String?): RetrievalSettings =
            com.gugachat.app.core.util.JsonUtils.decodeOrDefault(raw, RetrievalSettings())

        internal fun decodeWebDavConfig(raw: String?): WebDavSyncConfig =
            com.gugachat.app.core.util.JsonUtils.decodeOrDefault(raw, WebDavSyncConfig())

        internal fun decodeTokenCalibrationMap(raw: String?): Map<String, Double> {
            val clean = raw?.trim().orEmpty()
            if (clean.isEmpty()) return emptyMap()
            val parsed = runCatching {
                com.gugachat.app.core.util.CoreAppJson.decodeFromString(TOKEN_CAL_MAP_SERIALIZER, clean)
            }.getOrDefault(emptyMap())
            if (parsed.isEmpty()) return emptyMap()
            val out = LinkedHashMap<String, Double>(parsed.size)
            for ((k, v) in parsed) {
                val trimmedKey = k.trim()
                if (trimmedKey.isNotEmpty() && v.isFinite()) {
                    out[trimmedKey] = v.coerceIn(MIN_TOKEN_CALIBRATION, MAX_TOKEN_CALIBRATION)
                }
            }
            return out
        }

        internal fun updateTokenCalibrationMap(
            existing: Map<String, Double>,
            key: String,
            factor: Double,
            maxEntries: Int = MAX_TOKEN_CALIBRATION_ENTRIES,
        ): Map<String, Double> {
            val cleanKey = key.trim()
            if (cleanKey.isEmpty() || !factor.isFinite()) return existing
            val clamped = factor.coerceIn(MIN_TOKEN_CALIBRATION, MAX_TOKEN_CALIBRATION)
            val ordered = LinkedHashMap<String, Double>(existing.size + 1)
            for ((k, v) in existing) {
                if (k != cleanKey && k.isNotBlank() && v.isFinite()) {
                    ordered[k] = v.coerceIn(MIN_TOKEN_CALIBRATION, MAX_TOKEN_CALIBRATION)
                }
            }
            ordered[cleanKey] = clamped
            val limit = maxEntries.coerceAtLeast(1)
            while (ordered.size > limit) {
                val eldestKey = ordered.entries.firstOrNull()?.key ?: break
                ordered.remove(eldestKey)
            }
            return ordered
        }
    }
}
