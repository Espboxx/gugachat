package com.gugachat.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gugachat.app.data.api.AppJson
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
    private val prefs: SharedPreferences = createEncryptedOrFallbackPrefs(context.applicationContext)
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
            AppJson.decodeFromString(ListSerializer(ApiProviderConfig.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun persistList(list: List<ApiProviderConfig>) {
        val encoded = AppJson.encodeToString(ListSerializer(ApiProviderConfig.serializer()), list)
        prefs.edit().putString(KEY_PROVIDERS_JSON, encoded).apply()
        _providers.value = list
    }

    private companion object {
        const val KEY_PROVIDERS_JSON = "custom_api_providers_json"

        fun createEncryptedOrFallbackPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "gugachat_secure_providers",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Throwable) {
                context.getSharedPreferences("gugachat_providers_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
