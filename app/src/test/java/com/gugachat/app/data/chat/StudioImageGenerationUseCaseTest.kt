package com.gugachat.app.data.chat

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.data.api.OnlineImageKeyDto
import com.gugachat.app.data.api.OnlineImageModelDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudioImageGenerationUseCaseTest {

    @Test
    fun resolveConfiguredChannelAndModel_rejectsUnconfiguredSelectionWithoutFallback() = runBlocking {
        val available = listOf(
            OnlineImageKeyDto(
                id = 1L,
                name = "Paid Pool",
                models = listOf(OnlineImageModelDto(id = "gpt-image-1", provider = "openai", transport = "images")),
            ),
        )
        try {
            StudioImageGenerationUseCase.resolveConfiguredChannelAndModel(
                targetKeyId = 0L,
                targetModelId = "",
                availableKeys = available,
                resolveExplicitModel = { _, _ -> fail("Should not resolve models when unconfigured") as OnlineImageModelDto? },
                modelUnconfiguredMessage = "SELECT_FIRST",
            )
            fail("Expected StudioArtModelUnconfiguredException")
        } catch (e: StudioArtModelUnconfiguredException) {
            assertEquals("SELECT_FIRST", e.message)
        }
    }

    @Test
    fun resolveConfiguredChannelAndModel_rejectsMissingChannelInsteadOfSwitchingToAnother() = runBlocking {
        val otherChannel = OnlineImageKeyDto(
            id = 2L,
            name = "Other Paid Pool",
            models = listOf(OnlineImageModelDto(id = "gpt-image-1", provider = "openai", transport = "images")),
        )
        try {
            StudioImageGenerationUseCase.resolveConfiguredChannelAndModel(
                targetKeyId = 99L,
                targetModelId = "gpt-image-1",
                availableKeys = listOf(otherChannel),
                resolveExplicitModel = { _, _ -> otherChannel.models.first() },
                modelUnconfiguredMessage = "CHANNEL_GONE",
            )
            fail("Expected StudioArtModelUnconfiguredException when configured channel is missing")
        } catch (e: StudioArtModelUnconfiguredException) {
            assertEquals("CHANNEL_GONE", e.message)
        }
    }

    @Test
    fun resolveConfiguredChannelAndModel_matchesExplicitOnlineModelStrictly() = runBlocking {
        val key = OnlineImageKeyDto(
            id = 5L,
            name = "Online Free Pool",
            models = listOf(
                OnlineImageModelDto(id = "flux-schnell", provider = "openai", transport = "images"),
                OnlineImageModelDto(id = "seedream-3.0", provider = "openai", transport = "images"),
            ),
        )
        val (chosenKey, chosenModel) = StudioImageGenerationUseCase.resolveConfiguredChannelAndModel(
            targetKeyId = 5L,
            targetModelId = "seedream-3.0",
            availableKeys = listOf(key),
            resolveExplicitModel = { k, m ->
                ImageChannelResolver.resolveExplicitModelFromCandidates(k, k.models, m, customProvider = null)
            },
        )
        assertEquals(5L, chosenKey.id)
        assertEquals("seedream-3.0", chosenModel.id)

        // Unknown model on an online server channel must NOT silently fall back
        try {
            StudioImageGenerationUseCase.resolveConfiguredChannelAndModel(
                targetKeyId = 5L,
                targetModelId = "nonexistent-model",
                availableKeys = listOf(key),
                resolveExplicitModel = { k, m ->
                    ImageChannelResolver.resolveExplicitModelFromCandidates(k, k.models, m, customProvider = null)
                },
                modelUnconfiguredMessage = "MODEL_OFFLINE",
            )
            fail("Expected StudioArtModelUnconfiguredException for offline server model")
        } catch (e: StudioArtModelUnconfiguredException) {
            assertEquals("MODEL_OFFLINE", e.message)
        }
    }

    @Test
    fun resolveExplicitModelFromCandidates_synthesizesCustomDirectRelayModelId() = runBlocking {
        val relay = ApiProviderConfig(
            id = "relay_custom_art",
            name = "My Relay",
            protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = "https://relay.example.com/v1",
            apiKey = "sk-test",
            customModels = listOf("deepseek-chat"),
            enabled = true,
        )
        val customKey = OnlineImageKeyDto(
            id = relay.syntheticGroupId,
            name = "My Relay · OpenAI Chat",
            models = emptyList(),
        )

        val resolved = ImageChannelResolver.resolveExplicitModelFromCandidates(
            key = customKey,
            sortedModels = emptyList(),
            modelId = "  flux-kontext-pro  ",
            customProvider = relay,
        )
        assertNotNull(resolved)
        assertEquals("flux-kontext-pro", resolved!!.id)
        assertEquals("direct", resolved.transport)
        assertEquals(ApiProtocol.OPENAI_CHAT.wireValue, resolved.provider)
        assertTrue(resolved.max_reference_images > 0)

        // Anthropic custom provider must be rejected even if passed manually
        val anthropic = relay.copy(id = "anth_1", protocol = ApiProtocol.ANTHROPIC)
        val anthropicKey = customKey.copy(id = anthropic.syntheticGroupId)
        assertNull(
            ImageChannelResolver.resolveExplicitModelFromCandidates(
                key = anthropicKey,
                sortedModels = emptyList(),
                modelId = "claude-image",
                customProvider = anthropic,
            ),
        )
    }
}
