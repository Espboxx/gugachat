package com.gugachat.app.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.util.ServerUrlUtils
import com.gugachat.app.data.api.SseRetryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "gugachat_settings")

data class AppSettings(
    val serverUrl: String = "",
    val themeStyle: ThemeStyle = ThemeStyle.GUGUGA,
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ZH,
    val memoryBudget: String = "4K",
    val sseAutoRetryCount: Int = SseRetryPolicy.DEFAULT_SSE_AUTO_RETRY_COUNT,
    val lastChatGroupId: Long = 0L,
    val lastChatModel: String = "",
    val lastImageKeyId: Long = 0L,
    val lastImageModel: String = "",
    val directModeActive: Boolean = false,
    val hasChosenEntryMode: Boolean = false,
    val preferredEntryMode: String = "",
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val MEMORY_BUDGET = stringPreferencesKey("memory_budget")
        val SSE_AUTO_RETRY_COUNT = intPreferencesKey("sse_auto_retry_count")
        val LAST_CHAT_GROUP_ID = longPreferencesKey("last_chat_group_id")
        val LAST_CHAT_MODEL = stringPreferencesKey("last_chat_model")
        val LAST_IMAGE_KEY_ID = longPreferencesKey("last_image_key_id")
        val LAST_IMAGE_MODEL = stringPreferencesKey("last_image_model")
        val DIRECT_MODE_ACTIVE = booleanPreferencesKey("direct_mode_active")
        val HAS_CHOSEN_ENTRY_MODE = booleanPreferencesKey("has_chosen_entry_mode")
        val PREFERRED_ENTRY_MODE = stringPreferencesKey("preferred_entry_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            serverUrl = prefs[Keys.SERVER_URL].orEmpty(),
            themeStyle = ThemeStyle.fromWire(prefs[Keys.THEME_STYLE]),
            colorMode = ColorMode.fromWire(prefs[Keys.COLOR_MODE]),
            language = AppLanguage.fromTag(prefs[Keys.LANGUAGE]),
            memoryBudget = prefs[Keys.MEMORY_BUDGET] ?: "4K",
            sseAutoRetryCount = SseRetryPolicy.clampRetryCount(
                prefs[Keys.SSE_AUTO_RETRY_COUNT] ?: SseRetryPolicy.DEFAULT_SSE_AUTO_RETRY_COUNT,
            ),
            lastChatGroupId = prefs[Keys.LAST_CHAT_GROUP_ID] ?: 0L,
            lastChatModel = prefs[Keys.LAST_CHAT_MODEL].orEmpty(),
            lastImageKeyId = prefs[Keys.LAST_IMAGE_KEY_ID] ?: 0L,
            lastImageModel = prefs[Keys.LAST_IMAGE_MODEL].orEmpty(),
            directModeActive = prefs[Keys.DIRECT_MODE_ACTIVE] ?: false,
            hasChosenEntryMode = prefs[Keys.HAS_CHOSEN_ENTRY_MODE] ?: false,
            preferredEntryMode = prefs[Keys.PREFERRED_ENTRY_MODE].orEmpty(),
        )
    }

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

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = language.tag }
    }

    suspend fun setMemoryBudget(budget: String) {
        context.settingsDataStore.edit { it[Keys.MEMORY_BUDGET] = budget }
    }

    suspend fun setSseAutoRetryCount(count: Int) {
        val clamped = SseRetryPolicy.clampRetryCount(count)
        context.settingsDataStore.edit { it[Keys.SSE_AUTO_RETRY_COUNT] = clamped }
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
}
