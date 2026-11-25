package com.gugachat.app.domain.images

import com.gugachat.app.data.api.GroupModelFreeQuotaStatusDto
import com.gugachat.app.data.api.OnlineImageCanvasSizeDto
import com.gugachat.app.data.api.OnlineImageKeyDto
import com.gugachat.app.data.api.OnlineImageKeyPricingDto
import com.gugachat.app.data.api.OnlineImageModelDto
import com.gugachat.app.data.api.OnlineImageParameterPricingDto
import com.gugachat.app.data.api.OnlineImageParameterSchemaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineImageStudioTest {

    @Test
    fun estimateCost_respectsFreeQuotaOpusTierAndKeyPricing() {
        val freeModel = OnlineImageModelDto(
            id = "gpt-image-1",
            provider = "openai",
            transport = "images",
            free_quota = GroupModelFreeQuotaStatusDto(remaining = 3.0, daily_limit = 5.0),
        )
        val freeEst = OnlineImageStudioUtils.estimateGenerationCost(null, freeModel, "1:1", "1K", 1)
        assertTrue(freeEst.isFreeByQuota)
        assertEquals(0.0, freeEst.amountUsd!!, 0.0001)

        val naiModel = OnlineImageModelDto(
            id = "nai-diffusion-4-5-full",
            provider = "novelai",
            transport = "novelai",
            parameter_schema = OnlineImageParameterSchemaDto(
                pricing = OnlineImageParameterPricingDto(opus_free_tier = true, anlas_usd = 0.002),
            ),
        )
        val opusEst = OnlineImageStudioUtils.estimateGenerationCost(
            null,
            naiModel,
            "2:3",
            "1K",
            1,
            novelAISteps = 28,
            novelAIWidth = 832,
            novelAIHeight = 1216,
        )
        assertTrue(opusEst.isOpusFree)
        assertEquals(0.0, opusEst.amountUsd!!, 0.0001)

        val paidKey = OnlineImageKeyDto(
            id = -7L,
            name = "Image Group",
            pricing = OnlineImageKeyPricingDto(rate_multiplier = 1.5, image_price_2k = 0.04),
        )
        val paidModel = OnlineImageModelDto(id = "gemini-image", provider = "gemini", transport = "generateContent")
        val paidEst = OnlineImageStudioUtils.estimateGenerationCost(paidKey, paidModel, "16:9", "2K", 2)
        assertEquals(0.12, paidEst.amountUsd!!, 0.0001)
    }

    @Test
    fun computeCanvasDimensions_usesSchemaAndFallbackMultiplesOf64() {
        val model = OnlineImageModelDto(
            id = "m1",
            provider = "openai",
            transport = "images",
            canvas_sizes = listOf(OnlineImageCanvasSizeDto(1792, 1024, "16:9", "2K")),
        )
        assertEquals(1792 to 1024, OnlineImageStudioUtils.computeCanvasDimensions("16:9", "2K", model))
        val fallback = OnlineImageStudioUtils.computeCanvasDimensions("16:9", "1K", null)
        assertEquals(0, fallback.first % 64)
        assertEquals(0, fallback.second % 64)
        assertTrue(fallback.first > fallback.second)
    }
}
