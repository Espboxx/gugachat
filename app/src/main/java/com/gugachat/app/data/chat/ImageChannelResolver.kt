package com.gugachat.app.data.chat

import com.gugachat.app.AuthSessionState
import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.ApiProviderStore
import com.gugachat.app.data.api.DirectApiClient
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.OnlineImageCapabilitiesDto
import com.gugachat.app.data.api.OnlineImageKeyDto
import com.gugachat.app.data.api.OnlineImageModelDto
import com.gugachat.app.data.api.OnlineImagesApi
import com.gugachat.app.domain.chat.FreeQuotaUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

data class ResolvedImageChannels(
    val capabilities: OnlineImageCapabilitiesDto? = null,
    val serverKeys: List<OnlineImageKeyDto> = emptyList(),
    val customKeys: List<OnlineImageKeyDto> = emptyList(),
    val allKeys: List<OnlineImageKeyDto> = emptyList(),
)

/**
 * Unifies server + custom direct image channel discovery, free-quota sorting, and model
 * resolution across [StudioImageGenerationUseCase], `ImageViewModel`, and `ProfileViewModel`.
 */
class ImageChannelResolver(
    private val authSession: StateFlow<AuthSessionState>,
    private val apiProviderStore: ApiProviderStore,
    private val directApiClient: DirectApiClient,
    private val onlineImagesApi: OnlineImagesApi,
) {
    fun customImageProviders(): List<ApiProviderConfig> =
        apiProviderStore.enabledProviders().filter { p ->
            DirectProtocolCodec.supportsImageGeneration(
                protocol = p.protocol,
                baseUrl = p.baseUrl,
                customModels = p.customModels,
            )
        }

    fun buildCustomImageKeys(): List<OnlineImageKeyDto> =
        customImageProviders().map { p ->
            OnlineImageKeyDto(
                id = p.syntheticGroupId,
                name = formatCustomChannelName(p),
                models = runCatching { directApiClient.listImageModels(p) }.getOrDefault(emptyList()),
            )
        }

    fun selectableCustomArtProviders(): List<ApiProviderConfig> =
        filterSelectableCustomArtProviders(apiProviderStore.enabledProviders())

    fun findEnabledCustomArtProvider(keyId: Long): ApiProviderConfig? =
        if (ApiProviderConfig.isCustomProviderId(keyId)) {
            selectableCustomArtProviders().firstOrNull { it.syntheticGroupId == keyId }
        } else {
            null
        }

    fun buildSelectableCustomArtKeys(): List<OnlineImageKeyDto> =
        selectableCustomArtProviders().map { p ->
            OnlineImageKeyDto(
                id = p.syntheticGroupId,
                name = formatCustomChannelName(p),
                models = runCatching { directApiClient.listImageModels(p) }.getOrDefault(emptyList()),
            )
        }

    suspend fun resolveSelectableArtChannels(
        throwOnAllEmptyServerError: Boolean = false,
    ): ResolvedImageChannels {
        val isDirectOnly = (authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
        val customKeys = buildSelectableCustomArtKeys()
        val cap = if (isDirectOnly) {
            null
        } else {
            try {
                onlineImagesApi.capabilities()
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                if (throwOnAllEmptyServerError && customKeys.isEmpty()) throw err else null
            }
        }
        return mergeCapabilitiesAndCustomKeys(cap, customKeys)
    }

    suspend fun resolveExplicitModelForKey(
        key: OnlineImageKeyDto,
        modelId: String,
    ): OnlineImageModelDto? {
        val sortedModels = resolveModelsForKey(key)
        return resolveExplicitModelFromCandidates(
            key = key,
            sortedModels = sortedModels,
            modelId = modelId,
            customProvider = findEnabledCustomArtProvider(key.id),
        )
    }

    suspend fun resolveAvailableChannels(
        throwOnAllEmptyServerError: Boolean = false,
    ): ResolvedImageChannels {
        val isDirectOnly = (authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
        val customKeys = buildCustomImageKeys()
        val cap = if (isDirectOnly) {
            null
        } else {
            try {
                onlineImagesApi.capabilities()
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                if (throwOnAllEmptyServerError && customKeys.isEmpty()) throw err else null
            }
        }
        return mergeCapabilitiesAndCustomKeys(cap, customKeys)
    }

    suspend fun resolveModelsForKey(key: OnlineImageKeyDto): List<OnlineImageModelDto> {
        val fetched = try {
            onlineImagesApi.models(key.id)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            key.models
        }
        return sortModelsWithFallback(fetched, key.models)
    }

    suspend fun countAvailableImageChannels(): Int? {
        val customCount = customImageProviders().size
        val isDirectOnly = (authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
        if (isDirectOnly) return customCount
        val serverCount = try {
            onlineImagesApi.capabilities().keys.size
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }
        return serverCount?.plus(customCount)
    }

    companion object {
        internal fun mergeCapabilitiesAndCustomKeys(
            cap: OnlineImageCapabilitiesDto?,
            customKeys: List<OnlineImageKeyDto>,
        ): ResolvedImageChannels {
            val serverKeys = cap?.keys?.let { FreeQuotaUtils.sortImageKeysByFreeQuota(it) }.orEmpty()
            return ResolvedImageChannels(
                capabilities = cap,
                serverKeys = serverKeys,
                customKeys = customKeys,
                allKeys = serverKeys + customKeys,
            )
        }

        internal fun sortModelsWithFallback(
            fetched: List<OnlineImageModelDto>,
            fallback: List<OnlineImageModelDto>,
        ): List<OnlineImageModelDto> =
            FreeQuotaUtils.sortImageModelsByFreeQuota(fetched.ifEmpty { fallback })

        internal fun filterSelectableCustomArtProviders(
            providers: List<ApiProviderConfig>,
        ): List<ApiProviderConfig> =
            providers.filter { it.enabled && it.protocol != ApiProtocol.ANTHROPIC }

        internal fun formatCustomChannelName(provider: ApiProviderConfig): String {
            val title = provider.displayTitle().trim()
            val proto = provider.protocol.displayName.trim()
            return if (title.isBlank()) {
                proto
            } else if (title.equals(proto, ignoreCase = true) || title.contains(proto, ignoreCase = true)) {
                title
            } else {
                "$title · $proto"
            }
        }

        internal fun resolveExplicitModelFromCandidates(
            key: OnlineImageKeyDto,
            sortedModels: List<OnlineImageModelDto>,
            modelId: String,
            customProvider: ApiProviderConfig?,
        ): OnlineImageModelDto? {
            val cleanModelId = modelId.trim()
            if (cleanModelId.isEmpty()) return null
            sortedModels.firstOrNull { it.id == cleanModelId }?.let { return it }
            sortedModels.firstOrNull { it.id.equals(cleanModelId, ignoreCase = true) }?.let { return it }
            if (customProvider != null &&
                ApiProviderConfig.isCustomProviderId(key.id) &&
                customProvider.protocol != ApiProtocol.ANTHROPIC
            ) {
                return DirectProtocolCodec.buildDirectImageModelDto(
                    protocol = customProvider.protocol,
                    modelId = cleanModelId,
                )
            }
            return null
        }
    }
}
