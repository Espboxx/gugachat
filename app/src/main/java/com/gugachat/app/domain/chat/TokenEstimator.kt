package com.gugachat.app.domain.chat

import kotlin.math.ceil
import kotlin.math.max

data class CharBreakdown(
    val hanzi: Int,
    val cjkPunct: Int,
    val other: Int,
    val whitespace: Int,
) {
    val totalChars: Int get() = hanzi + cjkPunct + other + whitespace
}

data class ModelTokenPrior(
    val familyKey: String,
    val hanRatio: Double,
    val cjkPunctRatio: Double = 0.90,
    val otherRatio: Double = 0.34,
    val whitespaceRatio: Double = 0.18,
)

/**
 * Unified model-family-aware token estimator replacing duplicated 1.45x / 0.5x heuristics.
 *
 * Splits text into (hanzi, cjkPunct, other, whitespace) and applies empirically calibrated
 * cold-start tokenizer priors per model family, plus cross-chat EMA calibration helpers.
 */
object TokenEstimator {

    private val OPENAI_REASONING_RE = Regex("(^|[/\\s:_\\-.])o[134]($|[/\\s:_\\-.])")

    private val PRIOR_DEEPSEEK = ModelTokenPrior("deepseek", hanRatio = 0.62, cjkPunctRatio = 0.88, otherRatio = 0.30, whitespaceRatio = 0.16)
    private val PRIOR_QWEN = ModelTokenPrior("qwen", hanRatio = 0.65, cjkPunctRatio = 0.88, otherRatio = 0.32, whitespaceRatio = 0.16)
    private val PRIOR_GLM = ModelTokenPrior("glm", hanRatio = 0.65, cjkPunctRatio = 0.88, otherRatio = 0.32, whitespaceRatio = 0.16)
    private val PRIOR_KIMI = ModelTokenPrior("kimi", hanRatio = 0.65, cjkPunctRatio = 0.88, otherRatio = 0.32, whitespaceRatio = 0.16)
    private val PRIOR_GEMINI = ModelTokenPrior("gemini", hanRatio = 0.72, cjkPunctRatio = 0.90, otherRatio = 0.32, whitespaceRatio = 0.16)
    private val PRIOR_MINIMAX = ModelTokenPrior("minimax", hanRatio = 0.85, cjkPunctRatio = 0.92, otherRatio = 0.34, whitespaceRatio = 0.18)
    private val PRIOR_DOUBAO = ModelTokenPrior("doubao", hanRatio = 0.85, cjkPunctRatio = 0.92, otherRatio = 0.34, whitespaceRatio = 0.18)
    private val PRIOR_OPENAI_O200K = ModelTokenPrior("openai_o200k", hanRatio = 0.90, cjkPunctRatio = 0.95, otherRatio = 0.32, whitespaceRatio = 0.18)
    private val PRIOR_LLAMA3 = ModelTokenPrior("llama3", hanRatio = 1.15, cjkPunctRatio = 0.95, otherRatio = 0.34, whitespaceRatio = 0.18)
    private val PRIOR_CLAUDE = ModelTokenPrior("claude", hanRatio = 1.30, cjkPunctRatio = 1.00, otherRatio = 0.34, whitespaceRatio = 0.18)
    private val PRIOR_OPENAI_CL100K = ModelTokenPrior("openai_cl100k", hanRatio = 1.40, cjkPunctRatio = 1.00, otherRatio = 0.35, whitespaceRatio = 0.20)
    private val PRIOR_LLAMA2 = ModelTokenPrior("llama2", hanRatio = 1.90, cjkPunctRatio = 1.10, otherRatio = 0.36, whitespaceRatio = 0.20)
    private val PRIOR_DEFAULT = ModelTokenPrior("default", hanRatio = 1.00, cjkPunctRatio = 0.95, otherRatio = 0.36, whitespaceRatio = 0.20)

    fun isHanChar(ch: Char): Boolean =
        Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN

    fun isCjkPunctChar(ch: Char): Boolean {
        val code = ch.code
        return code in 0x3000..0x303F || code in 0xFF00..0xFFEF
    }

    fun countChars(text: String): CharBreakdown {
        if (text.isEmpty()) return CharBreakdown(0, 0, 0, 0)
        var hanzi = 0
        var cjkPunct = 0
        var other = 0
        var whitespace = 0
        for (ch in text) {
            when {
                isHanChar(ch) -> hanzi++
                isCjkPunctChar(ch) -> cjkPunct++
                ch.isWhitespace() -> whitespace++
                else -> other++
            }
        }
        return CharBreakdown(
            hanzi = hanzi,
            cjkPunct = cjkPunct,
            other = other,
            whitespace = whitespace,
        )
    }

    /**
     * Maps any provider-prefixed or bare model ID (case-insensitive) to a stable model family key
     * suitable for cross-chat EMA calibration persistence.
     */
    fun modelFamilyKey(modelId: String?): String = priorFor(modelId).familyKey

    fun priorFor(modelId: String?): ModelTokenPrior {
        val m = modelId?.trim()?.lowercase().orEmpty()
        if (m.isEmpty()) return PRIOR_DEFAULT
        return when {
            m.contains("deepseek") -> PRIOR_DEEPSEEK
            m.contains("qwen") || m.contains("qwq") -> PRIOR_QWEN
            m.contains("glm") || m.contains("chatglm") -> PRIOR_GLM
            m.contains("kimi") || m.contains("moonshot") -> PRIOR_KIMI
            m.contains("gemini") || m.contains("gemma") -> PRIOR_GEMINI
            m.contains("claude") -> PRIOR_CLAUDE
            m.contains("minimax") || m.contains("abab") -> PRIOR_MINIMAX
            m.contains("doubao") || m.contains("skylark") -> PRIOR_DOUBAO
            m.contains("gpt-4o") ||
                m.contains("gpt-4.1") ||
                m.contains("gpt-4.5") ||
                m.contains("gpt-5") ||
                m.contains("chatgpt-4o") ||
                OPENAI_REASONING_RE.containsMatchIn(m) -> PRIOR_OPENAI_O200K
            m.contains("gpt-3.5") || m.contains("gpt-4") -> PRIOR_OPENAI_CL100K
            m.contains("llama-2") || m.contains("llama2") -> PRIOR_LLAMA2
            m.contains("llama-3") ||
                m.contains("llama3") ||
                m.contains("llama-4") ||
                m.contains("llama4") ||
                m.contains("meta-llama") -> PRIOR_LLAMA3
            else -> PRIOR_DEFAULT
        }
    }

    fun estimateText(text: String, modelId: String? = null): Int {
        if (text.isEmpty()) return 0
        val breakdown = countChars(text)
        val prior = priorFor(modelId)
        val raw = breakdown.hanzi * prior.hanRatio +
            breakdown.cjkPunct * prior.cjkPunctRatio +
            breakdown.other * prior.otherRatio +
            breakdown.whitespace * prior.whitespaceRatio
        return max(1, ceil(raw).toInt())
    }

    fun estimateMessage(text: String, imageCount: Int = 0, modelId: String? = null): Int =
        estimateText(text, modelId) + max(0, imageCount) * 765 + 8

    fun estimateItem(
        title: String?,
        body: String?,
        modelId: String? = null,
        overhead: Int = 8,
        minTokens: Int = 8,
    ): Int {
        val t = title?.trim().orEmpty()
        val b = body?.trim().orEmpty()
        val combined = when {
            t.isNotEmpty() && b.isNotEmpty() -> "$t: $b"
            t.isNotEmpty() -> t
            b.isNotEmpty() -> b
            else -> return 0
        }
        return max(minTokens, estimateText(combined, modelId) + overhead)
    }

    /**
     * Updates an EMA calibration factor relative to the model-family prior.
     * Skips unreliable samples (actualPromptTokens < 200 or non-positive estimate).
     */
    fun updateCalibrationEma(
        currentFactor: Double,
        actualPromptTokens: Int,
        estimatedRawTokens: Int,
        alpha: Double = 0.35,
    ): Double {
        val safeCurrent = currentFactor.coerceIn(0.60, 1.60)
        if (actualPromptTokens < 200 || estimatedRawTokens <= 0) return safeCurrent
        val observed = (actualPromptTokens.toDouble() / estimatedRawTokens.toDouble()).coerceIn(0.50, 2.00)
        return (safeCurrent * (1.0 - alpha) + observed * alpha).coerceIn(0.60, 1.60)
    }
}
