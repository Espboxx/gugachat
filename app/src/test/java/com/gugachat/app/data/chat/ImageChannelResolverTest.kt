package com.gugachat.app.data.chat

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.data.api.GroupModelFreeQuotaStatusDto
import com.gugachat.app.data.api.OnlineImageCapabilitiesDto
import com.gugachat.app.data.api.OnlineImageKeyDto
import com.gugachat.app.data.api.OnlineImageModelDto
import com.gugachat.app.ui.providers.API_PROVIDER_QUICK_PRESETS
import com.gugachat.app.ui.providers.ApiProviderDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageChannelResolverTest {

    @Test
    fun mergeCapabilitiesAndCustomKeys_placesServerKeysBeforeCustomAndSortsFreeQuotaFirst() {
        val paidServer = OnlineImageKeyDto(
            id = 1L,
            name = "Alpha Paid Pool",
            has_free_quotas = false,
            models = listOf(OnlineImageModelDto(id = "paid-img", provider = "openai", transport = "images")),
        )
        val freeServer = OnlineImageKeyDto(
            id = 2L,
            name = "Zeta Free Pool",
            has_free_quotas = true,
            models = listOf(
                OnlineImageModelDto(
                    id = "free-img",
                    provider = "openai",
                    transport = "images",
                    free_quota = GroupModelFreeQuotaStatusDto(daily_limit = 10.0, remaining = 5.0),
                ),
            ),
        )
        val custom = OnlineImageKeyDto(
            id = -1001L,
            name = "Custom Endpoint",
            models = listOf(OnlineImageModelDto(id = "dall-e-3", provider = "openai", transport = "images")),
        )

        val resolved = ImageChannelResolver.mergeCapabilitiesAndCustomKeys(
            cap = OnlineImageCapabilitiesDto(enabled = true, keys = listOf(paidServer, freeServer)),
            customKeys = listOf(custom),
        )

        assertEquals(2, resolved.serverKeys.size)
        assertEquals(listOf(2L, 1L, -1001L), resolved.allKeys.map { it.id })
    }

    @Test
    fun sortModelsWithFallback_usesFallbackWhenFetchedEmptyAndRanksFreeModelsFirst() {
        val fallback = listOf(
            OnlineImageModelDto(id = "m_paid", provider = "openai", transport = "images", free_quota = null),
            OnlineImageModelDto(
                id = "m_free",
                provider = "openai",
                transport = "images",
                free_quota = GroupModelFreeQuotaStatusDto(daily_limit = 10.0, remaining = 5.0),
            ),
        )
        val sortedFromFallback = ImageChannelResolver.sortModelsWithFallback(emptyList(), fallback)
        assertEquals(listOf("m_free", "m_paid"), sortedFromFallback.map { it.id })
        assertTrue(sortedFromFallback.first().free_quota != null)
    }

    @Test
    fun apiProviderDraft_withProtocolAndWithPreset_updateBaseUrlAndFieldsPredictably() {
        val initial = ApiProviderDraft()
        val toAnthropic = initial.withProtocol(ApiProtocol.ANTHROPIC)
        assertEquals(ApiProtocol.ANTHROPIC, toAnthropic.protocol)
        assertEquals(ApiProtocol.ANTHROPIC.defaultBaseUrl, toAnthropic.baseUrl)

        val customBase = toAnthropic.copy(baseUrl = "https://my-proxy.example.com/v1")
        val keptCustom = customBase.withProtocol(ApiProtocol.GEMINI)
        assertEquals(ApiProtocol.GEMINI, keptCustom.protocol)
        assertEquals("https://my-proxy.example.com/v1", keptCustom.baseUrl)

        val preset = API_PROVIDER_QUICK_PRESETS.first { it.id == "deepseek" }
        val applied = keptCustom.withPreset(preset)
        assertEquals(preset.protocol, applied.protocol)
        assertEquals(preset.displayName, applied.name)
        assertEquals(preset.baseUrl, applied.baseUrl)
    }

    @Test
    fun filterSelectableCustomArtProviders_includesNonAnthropicEnabledProvidersWithoutPresetImageModels() {
        val openaiRelayNoImg = ApiProviderConfig(
            id = "p_relay",
            name = "Third-party Relay",
            protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = "https://relay.example.com/v1",
            apiKey = "sk-1",
            customModels = listOf("deepseek-v3"),
            enabled = true,
        )
        val gemini = ApiProviderConfig(
            id = "p_gemini",
            name = "Gemini Direct",
            protocol = ApiProtocol.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com",
            apiKey = "g-1",
            enabled = true,
        )
        val anthropic = ApiProviderConfig(
            id = "p_claude",
            name = "Claude Direct",
            protocol = ApiProtocol.ANTHROPIC,
            baseUrl = "https://api.anthropic.com",
            apiKey = "a-1",
            enabled = true,
        )
        val disabledOpenAi = openaiRelayNoImg.copy(id = "p_disabled", enabled = false)

        val selectable = ImageChannelResolver.filterSelectableCustomArtProviders(
            listOf(openaiRelayNoImg, gemini, anthropic, disabledOpenAi),
        )
        assertEquals(listOf("p_relay", "p_gemini"), selectable.map { it.id })
    }
}
