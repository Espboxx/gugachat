package com.gugachat.app.domain.images

import com.gugachat.app.data.api.OnlineImageKeyDto
import com.gugachat.app.data.api.OnlineImageModelDto
import com.gugachat.app.domain.chat.FreeQuotaUtils
import java.util.Locale
import kotlin.math.roundToLong

data class ImageCostEstimate(
    val amountUsd: Double?,
    val isFreeByQuota: Boolean,
    val isOpusFree: Boolean,
    val label: String,
)

object OnlineImageStudioUtils {
    val PRESET_ASPECT_RATIOS = listOf("auto", "1:1", "4:3", "3:4", "16:9", "9:16", "3:2", "2:3", "21:9")
    val PRESET_RESOLUTIONS = listOf("auto", "1K", "2K", "4K")

    fun isNovelAIModel(model: OnlineImageModelDto?): Boolean =
        model?.provider.equals("novelai", ignoreCase = true) ||
            model?.id.orEmpty().lowercase().contains("nai-diffusion")

    fun normalizeDimensionMultiple64(value: Int): Int =
        ((value.coerceIn(64, 4096) + 32) / 64) * 64

    fun computeCanvasDimensions(
        aspectRatio: String,
        resolution: String,
        model: OnlineImageModelDto? = null,
    ): Pair<Int, Int> {
        val exactMatch = model?.canvas_sizes?.firstOrNull {
            it.aspect_ratio.equals(aspectRatio, ignoreCase = true) &&
                it.resolution.equals(resolution, ignoreCase = true)
        }
        if (exactMatch != null && exactMatch.width > 0 && exactMatch.height > 0) {
            return exactMatch.width to exactMatch.height
        }

        val longEdge = when (resolution.uppercase()) {
            "4K" -> 2048
            "2K" -> 1536
            else -> 1024
        }

        val (wRatio, hRatio) = parseAspectRatio(aspectRatio)
        return if (wRatio >= hRatio) {
            val w = normalizeDimensionMultiple64(longEdge)
            val h = normalizeDimensionMultiple64(((longEdge * hRatio) / wRatio).toInt())
            w to h
        } else {
            val h = normalizeDimensionMultiple64(longEdge)
            val w = normalizeDimensionMultiple64(((longEdge * wRatio) / hRatio).toInt())
            w to h
        }
    }

    fun parseAspectRatio(aspect: String): Pair<Double, Double> {
        val clean = aspect.trim().lowercase()
        if (clean.isEmpty() || clean == "auto") return 1.0 to 1.0
        val parts = clean.split(':')
        if (parts.size == 2) {
            val w = parts[0].toDoubleOrNull()
            val h = parts[1].toDoubleOrNull()
            if (w != null && h != null && w > 0 && h > 0) return w to h
        }
        val xParts = clean.split('x')
        if (xParts.size == 2) {
            val w = xParts[0].toDoubleOrNull()
            val h = xParts[1].toDoubleOrNull()
            if (w != null && h != null && w > 0 && h > 0) return w to h
        }
        return 1.0 to 1.0
    }

    fun estimateGenerationCost(
        key: OnlineImageKeyDto?,
        model: OnlineImageModelDto?,
        aspectRatio: String,
        resolution: String,
        count: Int,
        novelAISteps: Int = 28,
        novelAIWidth: Int? = null,
        novelAIHeight: Int? = null,
        isZh: Boolean = true,
    ): ImageCostEstimate {
        if (model == null) {
            return ImageCostEstimate(null, isFreeByQuota = false, isOpusFree = false, label = "-")
        }
        if (FreeQuotaUtils.hasAvailableFreeQuota(model.free_quota)) {
            return ImageCostEstimate(
                amountUsd = 0.0,
                isFreeByQuota = true,
                isOpusFree = false,
                label = if (isZh) "限免额可用 (\$0.00)" else "Free Quota (\$0.00)",
            )
        }

        val safeCount = count.coerceAtLeast(1)
        val pricing = model.parameter_schema?.pricing
        val keyPricing = key?.pricing
        val rateMult = when {
            keyPricing?.image_rate_independent == true && (keyPricing.image_rate_multiplier ?: 0.0) > 0.0 ->
                keyPricing.image_rate_multiplier!!
            (keyPricing?.rate_multiplier ?: 0.0) > 0.0 -> keyPricing!!.rate_multiplier!!
            else -> 1.0
        }

        if (isNovelAIModel(model)) {
            val (w, h) = if (novelAIWidth != null && novelAIHeight != null) {
                novelAIWidth to novelAIHeight
            } else {
                computeCanvasDimensions(aspectRatio, resolution, model)
            }
            val pixels = w.toLong() * h.toLong()
            val opusFree = pricing?.opus_free_tier == true && pixels <= 1048576L && novelAISteps <= 28
            if (opusFree) {
                return ImageCostEstimate(
                    amountUsd = 0.0,
                    isFreeByQuota = false,
                    isOpusFree = true,
                    label = if (isZh) "Opus 免费档 (\$0.00)" else "Opus Free (\$0.00)",
                )
            }
            val anlasPerImg = NovelAICostCalculator.estimateAnlasPerImage(w, h, novelAISteps)
            val anlasUsd = pricing?.anlas_usd ?: 0.002
            val profitMult = pricing?.profit_multiplier ?: 1.0
            val estUsd = anlasPerImg * anlasUsd * safeCount * profitMult * rateMult
            return ImageCostEstimate(
                amountUsd = estUsd,
                isFreeByQuota = false,
                isOpusFree = false,
                label = formatUsdEstimate(estUsd),
            )
        }

        if (pricing?.fixed_price_usd != null && pricing.fixed_price_usd > 0.0) {
            val usd = pricing.fixed_price_usd * safeCount * rateMult
            return ImageCostEstimate(usd, false, false, formatUsdEstimate(usd))
        }

        val matchedHint = keyPricing?.model_prices?.firstOrNull { hint ->
            hint.models.any { pat -> pat.equals(model.id, ignoreCase = true) || model.id.contains(pat, ignoreCase = true) }
        }
        val resKey = resolution.uppercase()
        val unitFromTier = matchedHint?.tiers?.get(resKey)
            ?: matchedHint?.tiers?.get(resolution.lowercase())
            ?: matchedHint?.image_price
            ?: matchedHint?.per_request_price

        val unitFromRes = when (resKey) {
            "4K" -> keyPricing?.image_price_4k ?: keyPricing?.image_price_2k ?: keyPricing?.image_price_1k
            "2K" -> keyPricing?.image_price_2k ?: keyPricing?.image_price_1k
            else -> keyPricing?.image_price_1k
        }

        val rawUnit = unitFromTier ?: unitFromRes
        if (rawUnit != null && rawUnit > 0.0) {
            val isPerReq = matchedHint?.billing_mode == "per_request" || matchedHint?.per_request_price != null && matchedHint.image_price == null
            val usd = (if (isPerReq) rawUnit else rawUnit * safeCount) * rateMult
            return ImageCostEstimate(usd, false, false, formatUsdEstimate(usd))
        }

        return ImageCostEstimate(
            amountUsd = null,
            isFreeByQuota = false,
            isOpusFree = false,
            label = if (isZh) "按模型计费" else "Pay-as-you-go",
        )
    }

    private fun formatUsdEstimate(value: Double): String {
        val rounded = (value * 10000.0).roundToLong() / 10000.0
        return "~$" + String.format(Locale.US, "%.4f", rounded).trimEnd('0').trimEnd('.')
    }
}

private object NovelAICostCalculator {
    fun estimateAnlasPerImage(width: Int, height: Int, steps: Int): Double {
        val pixels = (width.coerceAtLeast(64) * height.coerceAtLeast(64)).toDouble()
        val factor = pixels / (1024.0 * 1024.0)
        return (17.0 + (steps.coerceIn(1, 50) - 28) * 0.6).coerceAtLeast(5.0) * factor.coerceAtLeast(0.5)
    }
}
