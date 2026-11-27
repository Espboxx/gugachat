package com.gugachat.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.gugachat.app.core.util.CoreAppJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

@Serializable
enum class ApiProtocol(
    val wireValue: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val supportsImages: Boolean,
) {
    OPENAI_CHAT("openai_chat", "OpenAI Chat", "https://api.openai.com/v1", true),
    OPENAI_RESPONSES("openai_responses", "OpenAI Responses", "https://api.openai.com/v1", true),
    ANTHROPIC("anthropic", "Anthropic Claude", "https://api.anthropic.com", false),
    GEMINI("gemini", "Google Gemini", "https://generativelanguage.googleapis.com", true);

    companion object {
        fun fromWire(value: String?): ApiProtocol =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: OPENAI_CHAT
    }
}

@Serializable
data class ApiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val numericId: Long = 0L,
    val name: String = "",
    val protocol: ApiProtocol = ApiProtocol.OPENAI_CHAT,
    val baseUrl: String = "",
    val apiKey: String = "",
    val customModels: List<String> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val syntheticGroupId: Long
        get() = CUSTOM_PROVIDER_ID_OFFSET - numericId

    fun displayTitle(): String = name.trim().ifBlank { protocol.displayName }

    fun maskedApiKey(): String {
        val raw = apiKey.trim()
        if (raw.isEmpty()) return ""
        return when {
            raw.length <= 6 -> "***"
            raw.length <= 10 -> "${raw.take(2)}***${raw.takeLast(2)}"
            else -> "${raw.take(4)}***${raw.takeLast(4)}"
        }
    }

    companion object {
        const val CUSTOM_PROVIDER_ID_OFFSET = -1_000_000_000L

        fun isCustomProviderId(id: Long): Boolean = id <= CUSTOM_PROVIDER_ID_OFFSET
    }
}

class ApiProviderStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedPrefsFactory.create(
        context = context.applicationContext,
        encryptedName = "gugachat_secure_providers",
        fallbackName = "gugachat_providers_fallback",
    )
    private val _providers = MutableStateFlow(readPersistedProviders())
    val providers: StateFlow<List<ApiProviderConfig>> = _providers.asStateFlow()

    @Synchronized
    fun currentProviders(): List<ApiProviderConfig> = _providers.value

    @Synchronized
    fun enabledProviders(): List<ApiProviderConfig> = _providers.value.filter { it.enabled }

    @Synchronized
    fun findById(id: String): ApiProviderConfig? = _providers.value.firstOrNull { it.id == id }

    @Synchronized
    fun findBySyntheticId(syntheticId: Long): ApiProviderConfig? =
        _providers.value.firstOrNull { it.syntheticGroupId == syntheticId }

    @Synchronized
    fun upsert(provider: ApiProviderConfig): ApiProviderConfig {
        val list = _providers.value.toMutableList()
        val existingIdx = list.indexOfFirst { it.id == provider.id }
        val now = System.currentTimeMillis()
        val allocatedNumericId = when {
            provider.numericId > 0L -> provider.numericId
            existingIdx >= 0 && list[existingIdx].numericId > 0L -> list[existingIdx].numericId
            else -> ((list.maxOfOrNull { it.numericId } ?: 0L) + 1L)
        }
        val cleanModels = provider.customModels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val normalized = provider.copy(
            numericId = allocatedNumericId,
            name = provider.name.trim(),
            baseUrl = provider.baseUrl.trim().ifBlank { provider.protocol.defaultBaseUrl },
            apiKey = provider.apiKey.trim(),
            customModels = cleanModels,
            createdAt = if (existingIdx >= 0) list[existingIdx].createdAt else now,
            updatedAt = now,
        )
        if (existingIdx >= 0) {
            list[existingIdx] = normalized
        } else {
            list.add(normalized)
        }
        persistList(list)
        return normalized
    }

    @Synchronized
    fun upsertAllDeduped(
        imported: List<ApiProviderConfig>,
        allowBlankKeyForLan: Boolean = false,
    ): Int {
        if (imported.isEmpty()) return 0
        val list = _providers.value.toMutableList()
        val existingKeys = list.map { ApiProviderImportCodec.dedupeKey(it) }.toHashSet()
        var nextNumericId = (list.maxOfOrNull { it.numericId } ?: 0L) + 1L
        val now = System.currentTimeMillis()
        var added = 0
        imported.forEach { raw ->
            val cleanKey = raw.apiKey.trim()
            val normalizedBaseUrl = raw.baseUrl.trim().ifBlank { raw.protocol.defaultBaseUrl }
            val keyAllowed = cleanKey.isNotEmpty() ||
                (allowBlankKeyForLan && com.gugachat.app.core.util.ServerUrlUtils.isPrivateLanOrLocalhost(normalizedBaseUrl))
            if (!keyAllowed) return@forEach
            val normalized = raw.copy(
                id = raw.id.ifBlank { UUID.randomUUID().toString() },
                numericId = nextNumericId,
                name = raw.name.trim(),
                baseUrl = normalizedBaseUrl,
                apiKey = cleanKey,
                customModels = raw.customModels.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                createdAt = now,
                updatedAt = now,
            )
            val key = ApiProviderImportCodec.dedupeKey(normalized)
            if (existingKeys.add(key)) {
                list.add(normalized)
                nextNumericId += 1L
                added += 1
            }
        }
        if (added > 0) {
            persistList(list)
        }
        return added
    }

    @Synchronized
    fun addCustomModel(syntheticId: Long, modelId: String) {
        val clean = modelId.trim()
        if (clean.isEmpty()) return
        val existing = findBySyntheticId(syntheticId) ?: return
        if (clean in existing.customModels) return
        upsert(existing.copy(customModels = existing.customModels + clean))
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        val existing = findById(id) ?: return
        upsert(existing.copy(enabled = enabled))
    }

    @Synchronized
    fun delete(id: String) {
        val next = _providers.value.filterNot { it.id == id }
        persistList(next)
    }

    private fun readPersistedProviders(): List<ApiProviderConfig> {
        val raw = prefs.getString(KEY_PROVIDERS_JSON, null)?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            CoreAppJson.decodeFromString(ListSerializer(ApiProviderConfig.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun persistList(list: List<ApiProviderConfig>) {
        val encoded = CoreAppJson.encodeToString(ListSerializer(ApiProviderConfig.serializer()), list)
        prefs.edit().putString(KEY_PROVIDERS_JSON, encoded).apply()
        _providers.value = list
    }

    private companion object {
        const val KEY_PROVIDERS_JSON = "custom_api_providers_json"
    }
}
