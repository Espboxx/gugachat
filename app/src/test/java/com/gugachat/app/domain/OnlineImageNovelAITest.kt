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
                characters = listOf(
                    NovelAICharacterPrompt("girl smiling", "blurry", 0.25f, 0.5f),
                    NovelAICharacterPrompt("boy waving", "", 0.75f, 0.5f),
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
        val chars = v4Json["v4_prompt"]!!.jsonObject["caption"]!!.jsonObject["char_captions"]!!.jsonArray
        assertEquals(2, chars.size)
    }
}
