package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun countChars_splitsHanziCjkPunctOtherAndWhitespace() {
        val b = TokenEstimator.countChars("青岚剑宗，AI-2026！")
        assertEquals(4, b.hanzi)
        assertEquals(2, b.cjkPunct) // ， ！
        assertEquals(7, b.other) // A I - 2 0 2 6
        assertEquals(0, b.whitespace)

        val bWs = TokenEstimator.countChars("你好 世界\n")
        assertEquals(4, bWs.hanzi)
        assertEquals(0, bWs.cjkPunct)
        assertEquals(0, bWs.other)
        assertEquals(2, bWs.whitespace)
    }

    @Test
    fun modelFamilyKeyAndPriors_mapAllFamiliesCaseInsensitiveWithoutFalseMatches() {
        assertEquals("deepseek", TokenEstimator.modelFamilyKey("Pro/deepseek-ai/DeepSeek-V3"))
        assertEquals(0.62, TokenEstimator.priorFor("deepseek-reasoner").hanRatio, 1e-6)

        assertEquals("qwen", TokenEstimator.modelFamilyKey("Qwen/Qwen2.5-72B-Instruct"))
        assertEquals(0.65, TokenEstimator.priorFor("qwen3-235b").hanRatio, 1e-6)

        assertEquals("glm", TokenEstimator.modelFamilyKey("GLM-4-Plus"))
        assertEquals(0.65, TokenEstimator.priorFor("chatglm3-6b").hanRatio, 1e-6)

        assertEquals("kimi", TokenEstimator.modelFamilyKey("moonshot-v1-128k"))
        assertEquals(0.65, TokenEstimator.priorFor("kimi-k2").hanRatio, 1e-6)

        assertEquals("gemini", TokenEstimator.modelFamilyKey("google/gemini-2.5-pro"))
        assertEquals(0.72, TokenEstimator.priorFor("gemini-2.0-flash").hanRatio, 1e-6)

        assertEquals("minimax", TokenEstimator.modelFamilyKey("MiniMax-M1"))
        assertEquals(0.85, TokenEstimator.priorFor("minimax-text-01").hanRatio, 1e-6)

        assertEquals("doubao", TokenEstimator.modelFamilyKey("doubao-1.5-pro-32k"))
        assertEquals(0.85, TokenEstimator.priorFor("doubao-pro").hanRatio, 1e-6)

        assertEquals("openai_o200k", TokenEstimator.modelFamilyKey("openai/gpt-4o-mini"))
        assertEquals("openai_o200k", TokenEstimator.modelFamilyKey("gpt-4.1"))
        assertEquals("openai_o200k", TokenEstimator.modelFamilyKey("gpt-5"))
        assertEquals("openai_o200k", TokenEstimator.modelFamilyKey("o1-preview"))
        assertEquals("openai_o200k", TokenEstimator.modelFamilyKey("o3-mini"))
        assertEquals("openai_o200k", TokenEstimator.modelFamilyKey("openai/o4-mini"))
        assertEquals(0.90, TokenEstimator.priorFor("gpt-4o").hanRatio, 1e-6)

        assertEquals("openai_cl100k", TokenEstimator.modelFamilyKey("gpt-3.5-turbo"))
        assertEquals("openai_cl100k", TokenEstimator.modelFamilyKey("gpt-4-0613"))
        assertEquals(1.40, TokenEstimator.priorFor("gpt-4-0613").hanRatio, 1e-6)

        assertEquals("llama3", TokenEstimator.modelFamilyKey("meta-llama/Llama-3.3-70B-Instruct"))
        assertEquals(1.15, TokenEstimator.priorFor("llama3-8b").hanRatio, 1e-6)

        assertEquals("claude", TokenEstimator.modelFamilyKey("anthropic/claude-3-7-sonnet-20250219"))
        assertEquals(1.30, TokenEstimator.priorFor("claude-sonnet-4").hanRatio, 1e-6)

        // Unknown / custom models & words containing o1/o3/o4 substrings must not misclassify as OpenAI o-series
        assertEquals("default", TokenEstimator.modelFamilyKey(null))
        assertEquals("default", TokenEstimator.modelFamilyKey("my-foo1-model"))
        assertEquals("default", TokenEstimator.modelFamilyKey("video4-pro3"))
        assertEquals(1.00, TokenEstimator.priorFor("custom-model").hanRatio, 1e-6)
    }

    @Test
    fun estimateText_reducesColdStartOverestimationAcrossModelFamilies() {
        val sampleCjk = "兰溪谷公馆二楼书房的落地窗外正下着细雨何瑞璋翻开账本轻轻颔首".repeat(100) // 3100 Han chars
        val deepseekTok = TokenEstimator.estimateText(sampleCjk, "deepseek-chat")
        val qwenTok = TokenEstimator.estimateText(sampleCjk, "qwen2.5-72b")
        val geminiTok = TokenEstimator.estimateText(sampleCjk, "gemini-2.5-pro")
        val gpt4oTok = TokenEstimator.estimateText(sampleCjk, "gpt-4o")
        val defaultTok = TokenEstimator.estimateText(sampleCjk, null)
        val llama3Tok = TokenEstimator.estimateText(sampleCjk, "llama-3.1-70b")
        val claudeTok = TokenEstimator.estimateText(sampleCjk, "claude-3.7-sonnet")
        val legacyTok = TokenEstimator.estimateText(sampleCjk, "gpt-3.5-turbo")

        assertTrue(deepseekTok < qwenTok)
        assertTrue(qwenTok < geminiTok)
        assertTrue(geminiTok < gpt4oTok)
        assertTrue(gpt4oTok < defaultTok)
        assertTrue(defaultTok < llama3Tok)
        assertTrue(llama3Tok < claudeTok)
        assertTrue(claudeTok < legacyTok)

        // DeepSeek ~0.62x on 3000 pure Han chars -> 1860 tokens (far below legacy 1.45x = 4350)
        assertEquals(3000, sampleCjk.length)
        assertEquals(1860, deepseekTok)
    }

    @Test
    fun updateCalibrationEma_skipsTinyTurnsAndClampsSafely() {
        // Tiny turn (< 200 prompt tokens) must be ignored
        assertEquals(1.0, TokenEstimator.updateCalibrationEma(1.0, actualPromptTokens = 120, estimatedRawTokens = 200), 1e-6)

        // Observed ratio 0.80 with alpha 0.35 -> 1.0 * 0.65 + 0.80 * 0.35 = 0.93
        val updated = TokenEstimator.updateCalibrationEma(1.0, actualPromptTokens = 800, estimatedRawTokens = 1000, alpha = 0.35)
        assertEquals(0.93, updated, 1e-6)

        // Extreme outliers are clamped within [0.60, 1.60]
        var factor = 1.0
        repeat(20) {
            factor = TokenEstimator.updateCalibrationEma(factor, actualPromptTokens = 200, estimatedRawTokens = 5000)
        }
        assertEquals(0.60, factor, 1e-6)
    }
}
