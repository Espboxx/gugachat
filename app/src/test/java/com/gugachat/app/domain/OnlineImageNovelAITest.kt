package com.gugachat.app.domain.images

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineImageNovelAITest {

    @Test
    fun buildPayload_switchesCleanlyBetweenV3AndV4WithCharacters() {
        val v3Defaults = OnlineImageNovelAI.defaultParamsForVersion(NovelAIVersion.V3)
        val v3Json = OnlineImageNovelAI.buildNovelAIPayloadJsonObject(
            prompt = "1girl, silver hair",
            params = v3Defaults.copy(sm = true, smDyn = true, width = 835, height = 1210),
            modelId = "nai-diffusion-3",
        )
        assertEquals(832, v3Json["width"]!!.jsonPrimitive.content.toInt())
        assertEquals(1216, v3Json["height"]!!.jsonPrimitive.content.toInt())
        assertEquals("true", v3Json["sm"]!!.jsonPrimitive.content)
        assertFalse(v3Json.containsKey("v4_prompt"))

        val v4Defaults = OnlineImageNovelAI.defaultParamsForVersion(NovelAIVersion.V4_5)
        val v4Json = OnlineImageNovelAI.buildNovelAIPayloadJsonObject(
            prompt = "two friends at cafe",
            params = v4Defaults.copy(
                varietyPlus = true,
                sampler = "ddim_v3", // incompatible with V4 -> auto-normalized
                useCoords = true,
                characters = listOf(
                    NovelAICharacterPrompt("girl smiling", "blurry", 0.25f, 0.5f),
                    NovelAICharacterPrompt("boy waving", "", 0.75f, 0.5f),
                    NovelAICharacterPrompt("", "", 0.5f, 0.5f), // blank entry filtered out
                ),
            ),
            modelId = "nai-diffusion-4-5-full",
            hasReferenceImages = true,
            isImg2Img = true,
        )
        assertEquals("k_euler_ancestral", v4Json["sampler"]!!.jsonPrimitive.content)
        assertTrue(v4Json.containsKey("v4_prompt"))
        assertTrue(v4Json.containsKey("strength"))
        assertTrue(v4Json.containsKey("reference_strength"))
        val v4PromptObj = v4Json["v4_prompt"]!!.jsonObject
        assertEquals("true", v4PromptObj["use_coords"]!!.jsonPrimitive.content)
        val chars = v4PromptObj["caption"]!!.jsonObject["char_captions"]!!.jsonArray
        assertEquals(2, chars.size)
        val center0 = chars[0].jsonObject["centers"]!!.jsonArray[0].jsonObject
        assertEquals(0.25, center0["x"]!!.jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(0.5, center0["y"]!!.jsonPrimitive.content.toDouble(), 0.001)
    }

    @Test
    fun buildAndParseNovelAIPayload_roundTripsCharactersCoordsAndUseCoordsFlag() {
        val original = OnlineImageNovelAI.defaultParamsForVersion(NovelAIVersion.V4_5).copy(
            negativePrompt = "bad hands, watermark",
            width = 1024,
            height = 1024,
            steps = 32,
            scale = 6.2,
            cfgRescale = 0.25,
            noiseSchedule = "exponential",
            seed = 123456789L,
            varietyPlus = true,
            useCoords = false,
            characters = listOf(
                NovelAICharacterPrompt("1girl, silver hair", "hat", 0.3f, 0.5f),
                NovelAICharacterPrompt("1boy, black coat", "glasses", 0.7f, 0.5f),
            ),
        )

        val wire = OnlineImageNovelAI.buildNovelAIPayloadJsonObject(
            prompt = "rooftop at sunset",
            params = original,
            modelId = "novelai-custom-alias",
            upstreamModel = "nai-diffusion-4-5-full",
        )
        assertEquals("false", wire["v4_prompt"]!!.jsonObject["use_coords"]!!.jsonPrimitive.content)
        assertEquals(2, OnlineImageNovelAI.countCharactersInPayload(wire))

        val restored = OnlineImageNovelAI.parseNovelAIParams(
            raw = wire,
            modelId = "novelai-custom-alias",
            upstreamModel = "nai-diffusion-4-5-full",
        )
        assertEquals("bad hands, watermark", restored.negativePrompt)
        assertEquals(1024, restored.width)
        assertEquals(1024, restored.height)
        assertEquals(32, restored.steps)
        assertEquals(6.2, restored.scale, 0.01)
        assertEquals(0.25, restored.cfgRescale, 0.01)
        assertEquals("exponential", restored.noiseSchedule)
        assertEquals(123456789L, restored.seed)
        assertTrue(restored.varietyPlus)
        assertFalse(restored.useCoords)
        assertEquals(2, restored.characters.size)
        assertEquals("1girl, silver hair", restored.characters[0].prompt)
        assertEquals("hat", restored.characters[0].negativePrompt)
        assertEquals(0.3f, restored.characters[0].centerX, 0.01f)
        assertEquals(0.5f, restored.characters[0].centerY, 0.01f)
        assertEquals("1boy, black coat", restored.characters[1].prompt)
        assertEquals("glasses", restored.characters[1].negativePrompt)
        assertEquals(0.7f, restored.characters[1].centerX, 0.01f)
    }

    @Test
    fun gridHelpers_assignDistinctSlotsAndFormatA1ToE5Labels() {
        val first = OnlineImageNovelAI.nextDefaultCharacterPosition(emptyList())
        assertEquals(0.5f to 0.5f, first)
        assertEquals("C3 (0.5, 0.5)", OnlineImageNovelAI.formatCoordinateLabel(first.first, first.second))

        val second = OnlineImageNovelAI.nextDefaultCharacterPosition(
            listOf(NovelAICharacterPrompt(centerX = first.first, centerY = first.second)),
        )
        assertEquals(0.3f to 0.5f, second)
        assertEquals("B3 (0.3, 0.5)", OnlineImageNovelAI.formatCoordinateLabel(second.first, second.second))
        assertEquals("A1 (0.1, 0.1)", OnlineImageNovelAI.formatCoordinateLabel(0.1f, 0.1f))
        assertEquals("E5 (0.9, 0.9)", OnlineImageNovelAI.formatCoordinateLabel(0.9f, 0.9f))
        assertEquals("(0.25, 0.62)", OnlineImageNovelAI.formatCoordinateLabel(0.25f, 0.62f))
    }
}
