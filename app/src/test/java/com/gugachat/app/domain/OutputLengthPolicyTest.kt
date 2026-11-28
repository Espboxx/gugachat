package com.gugachat.app.domain

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.OutputLengthPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputLengthPolicyTest {

    @Test
    fun normalizeMinOutputChars_clampsToAllowedRangeAndTreatsNonPositiveAsNull() {
        assertNull(OutputLengthPolicy.normalizeMinOutputChars(null))
        assertNull(OutputLengthPolicy.normalizeMinOutputChars(0))
        assertNull(OutputLengthPolicy.normalizeMinOutputChars(-200))
        assertEquals(50, OutputLengthPolicy.normalizeMinOutputChars(10))
        assertEquals(800, OutputLengthPolicy.normalizeMinOutputChars(800))
        assertEquals(20_000, OutputLengthPolicy.normalizeMinOutputChars(50_000))
    }

    @Test
    fun countEffectiveLength_countsCjkCharsEnglishWordsAndMixedTextAccurately() {
        // Pure Chinese prose with CJK punctuation
        val zh = "夜风吹过长街，灯火摇曳。"
        assertEquals(12, OutputLengthPolicy.countEffectiveLength(zh, ConversationMode.chat))

        // Pure English prose counts words (not letters), keeping contractions/hyphenations as 1 word
        val en = "Don't stop believing in well-known patterns, even at 300 words!"
        assertEquals(10, OutputLengthPolicy.countEffectiveLength(en, ConversationMode.chat))

        // Mixed CJK + Latin/digits
        val mixed = "使用 Kotlin 2.0 编写测试"
        // CJK chars: 使 用 编 写 测 试 (6) + words: Kotlin (1), 2 (1), 0 (1) = 9
        assertEquals(9, OutputLengthPolicy.countEffectiveLength(mixed, ConversationMode.chat))
    }

    @Test
    fun countEffectiveLength_stripsRpAndNovelStructuredTags() {
        val rpRaw = """
            雨点敲打着酒馆的木窗，火光微颤。
            <rp_state>{"scene":"酒馆","hp":90,"inventory":["长剑","灯笼"]}</rp_state>
            <rp_choices>[{"label":"推门","actionText":"推门走出酒馆"}]</rp_choices>
            <rp_ledger>[{"kind":"fact","title":"酒馆","content":"暴雨夜"}]</rp_ledger>
        """.trimIndent()
        val rpCleanCount = OutputLengthPolicy.countEffectiveLength("雨点敲打着酒馆的木窗，火光微颤。", ConversationMode.rp)
        assertEquals(rpCleanCount, OutputLengthPolicy.countEffectiveLength(rpRaw, ConversationMode.rp))

        val novelRaw = """
            月色沉入古井，风声渐歇。
            <status_hud>{"chapterTitle":"第一章","location":"古井","mood":"肃杀"}</status_hud>
            <lore_sync>[{"category":"location","title":"古井","content":"传闻藏有残卷"}]</lore_sync>
            <choices>["上前探查","原地戒备"]</choices>
        """.trimIndent()
        val novelCleanCount = OutputLengthPolicy.countEffectiveLength("月色沉入古井，风声渐歇。", ConversationMode.novel)
        assertEquals(novelCleanCount, OutputLengthPolicy.countEffectiveLength(novelRaw, ConversationMode.novel))
    }

    @Test
    fun isEnglishPreference_detectsCjkVsLatinAndFallsBackToAppLanguage() {
        assertFalse(OutputLengthPolicy.isEnglishPreference("请分析这段代码", appLanguageIsEnglish = true))
        assertTrue(OutputLengthPolicy.isEnglishPreference("Explain quicksort in detail", appLanguageIsEnglish = false))
        assertTrue(OutputLengthPolicy.isEnglishPreference("   ", appLanguageIsEnglish = true))
        assertFalse(OutputLengthPolicy.isEnglishPreference("12345", appLanguageIsEnglish = false))
    }

    @Test
    fun buildLengthDirective_variesByModeLanguageAndNovelChapterTarget() {
        assertEquals("", OutputLengthPolicy.buildLengthDirective(ConversationMode.chat, null))

        val zhChat = OutputLengthPolicy.buildLengthDirective(ConversationMode.chat, 800, isEnglish = false)
        assertTrue(zhChat.contains("800"))
        assertTrue(zhChat.contains("字"))
        assertFalse(zhChat.contains("<rp_state>"))

        val enChat = OutputLengthPolicy.buildLengthDirective(ConversationMode.chat, 500, isEnglish = true)
        assertTrue(enChat.contains("at least 500 words"))

        val zhRp = OutputLengthPolicy.buildLengthDirective(ConversationMode.rp, 1200, isEnglish = false)
        assertTrue(zhRp.contains("1200"))
        assertTrue(zhRp.contains("<rp_state>"))
        assertTrue(zhRp.contains("<rp_choices>"))

        val enRp = OutputLengthPolicy.buildLengthDirective(ConversationMode.rp, 600, isEnglish = true)
        assertTrue(enRp.contains("at least 600 words"))
        assertTrue(enRp.contains("<rp_state>"))

        val novelCfg = NovelConfig(targetWordsPerChapter = 3000)
        val activeCh = NovelChapter(
            id = "ch1",
            conversationId = "c1",
            order = 1,
            title = "第一章",
            wordCount = 1200,
        )
        val zhNovel = OutputLengthPolicy.buildLengthDirective(
            mode = ConversationMode.novel,
            minOutputChars = 1500,
            novelConfig = novelCfg,
            activeChapter = activeCh,
            isEnglish = false,
        )
        assertTrue(zhNovel.contains("1500"))
        assertTrue(zhNovel.contains("<status_hud>"))
        assertTrue(zhNovel.contains("3000"))
        assertTrue(zhNovel.contains("1800"))

        val enNovel = OutputLengthPolicy.buildLengthDirective(
            mode = ConversationMode.novel,
            minOutputChars = 1000,
            novelConfig = novelCfg,
            activeChapter = activeCh,
            isEnglish = true,
        )
        assertTrue(enNovel.contains("at least 1000 words"))
        assertTrue(enNovel.contains("<status_hud>"))
        assertTrue(enNovel.contains("1800"))
    }

    @Test
    fun computeEffectiveMaxTokens_respectsModelPriorReasoningHeadroomAndProtocol() {
        // Without minOutputChars, returns params.maxTokens untouched
        val unsetParams = ChatParams(maxTokens = 1024, minOutputChars = null)
        assertEquals(
            1024,
            OutputLengthPolicy.computeEffectiveMaxTokens(
                params = unsetParams,
                mode = ConversationMode.chat,
                modelId = "claude-sonnet-4-20250514",
                protocol = ApiProtocol.ANTHROPIC,
            ),
        )

        // Claude (1.30 tok/hanzi) requires a larger visible floor than DeepSeek (0.62 tok/hanzi)
        val nonReasoningParams = ChatParams(maxTokens = 512, minOutputChars = 1500, enableReasoning = false)
        val claudeFloor = OutputLengthPolicy.computeRequiredMaxTokensFloor(
            params = nonReasoningParams,
            mode = ConversationMode.rp,
            modelId = "claude-3-5-haiku-20241022",
        )!!
        val deepseekFloor = OutputLengthPolicy.computeRequiredMaxTokensFloor(
            params = nonReasoningParams,
            mode = ConversationMode.rp,
            modelId = "deepseek-chat",
        )!!
        assertTrue("Expected Claude floor ($claudeFloor) > DeepSeek floor ($deepseekFloor)", claudeFloor > deepseekFloor)
        assertEquals(
            claudeFloor,
            OutputLengthPolicy.computeEffectiveMaxTokens(
                params = nonReasoningParams,
                mode = ConversationMode.rp,
                modelId = "claude-3-5-haiku-20241022",
                protocol = ApiProtocol.ANTHROPIC,
            ),
        )

        // Reasoning enabled adds explicit thinking budget (high = 8192) on top of visible floor
        val reasoningParams = ChatParams(
            maxTokens = 2048,
            minOutputChars = 1500,
            enableReasoning = true,
            reasoningEffort = "high",
        )
        val reasoningMeta = OnlineChatModelDto(
            id = "claude-sonnet-4-20250514",
            supports_reasoning = true,
            context_window = 200_000,
        )
        val claudeReasoningMax = OutputLengthPolicy.computeEffectiveMaxTokens(
            params = reasoningParams,
            mode = ConversationMode.novel,
            modelId = "claude-sonnet-4-20250514",
            modelMeta = reasoningMeta,
            protocol = ApiProtocol.ANTHROPIC,
            windowLimit = 200_000,
        )!!
        val visibleOnly = OutputLengthPolicy.computeVisibleOutputTokenFloor(1500, ConversationMode.novel, "claude-sonnet-4-20250514")!!
        assertEquals(visibleOnly + 8192, claudeReasoningMax)

        // Anthropic with maxTokens = null still gets floored above its 4096 default
        val anthropicAuto = OutputLengthPolicy.computeEffectiveMaxTokens(
            params = ChatParams(maxTokens = null, minOutputChars = 3000, enableReasoning = true, reasoningEffort = "medium"),
            mode = ConversationMode.novel,
            modelId = "claude-sonnet-4-20250514",
            modelMeta = reasoningMeta,
            protocol = ApiProtocol.ANTHROPIC,
            windowLimit = 200_000,
        )
        assertNotNull(anthropicAuto)
        assertTrue(anthropicAuto!! > 4096)

        // OpenAI / Gemini with maxTokens = null keeps null (full native output limit)
        val openAiAuto = OutputLengthPolicy.computeEffectiveMaxTokens(
            params = ChatParams(maxTokens = null, minOutputChars = 3000, enableReasoning = false),
            mode = ConversationMode.chat,
            modelId = "gpt-4o",
            protocol = ApiProtocol.OPENAI_CHAT,
        )
        assertNull(openAiAuto)

        // Respects safe half-window cap on tiny windows
        val cappedFloor = OutputLengthPolicy.computeRequiredMaxTokensFloor(
            params = ChatParams(minOutputChars = 20_000, enableReasoning = true, reasoningEffort = "high"),
            mode = ConversationMode.novel,
            modelId = "claude-sonnet-4-20250514",
            windowLimit = 16_000,
        )!!
        assertEquals(8_000, cappedFloor)
    }

    @Test
    fun buildContinueNudgeAndStandardChatContinueMessages_includeRemainingTarget() {
        val zhNudge = OutputLengthPolicy.buildContinueNudge(ConversationMode.rp, actual = 200, target = 800, isEnglish = false)
        assertTrue(zhNudge.contains("200"))
        assertTrue(zhNudge.contains("800"))
        assertTrue(zhNudge.contains("600"))

        val enNudge = OutputLengthPolicy.buildContinueNudge(ConversationMode.chat, actual = 150, target = 500, isEnglish = true)
        assertTrue(enNudge.contains("150"))
        assertTrue(enNudge.contains("500"))
        assertTrue(enNudge.contains("350"))

        val conv = ChatConversation(
            id = "c1",
            title = "Chat",
            mode = ConversationMode.chat,
            systemPrompt = "You are helpful.",
            params = ChatParams(minOutputChars = 500),
            createdAt = 1L,
            updatedAt = 1L,
        )
        val msgs = listOf(
            ChatMessage(id = "m1", conversationId = "c1", role = "user", content = "Explain Raft.", createdAt = 1L, seq = 1),
            ChatMessage(id = "m2", conversationId = "c1", role = "assistant", content = "Raft is a consensus protocol.", createdAt = 2L, seq = 2),
        )
        val wire = OutputLengthPolicy.buildStandardChatContinueMessages(conv, msgs, isEnglish = true)
        assertEquals("system", wire.first().role)
        assertEquals("user", wire.last().role)
        assertTrue(wire.last().content.toString().contains("500"))
    }

    @Test
    fun appSettingsAndCampaignDefaultChatParams_inheritPerModeMinOutputDefaults() {
        val settings = com.gugachat.app.core.storage.AppSettings(
            defaultMaxOutputTokens = 4096,
            defaultMinOutputChat = 300,
            defaultMinOutputRp = 800,
            defaultMinOutputNovel = 1500,
            defaultAutoContinueShort = true,
        )
        assertEquals(300, settings.defaultMinOutputFor(ConversationMode.chat))
        assertEquals(800, settings.defaultMinOutputFor(ConversationMode.rp))
        assertEquals(1500, settings.defaultMinOutputFor(ConversationMode.novel))

        val chatParams = com.gugachat.app.data.chat.CampaignLaunchUseCase.defaultChatParams(settings, ConversationMode.chat)
        val rpParams = com.gugachat.app.data.chat.CampaignLaunchUseCase.defaultChatParams(settings, ConversationMode.rp)
        val novelParams = com.gugachat.app.data.chat.CampaignLaunchUseCase.defaultChatParams(settings, ConversationMode.novel)

        assertEquals(300, chatParams.minOutputChars)
        assertEquals(800, rpParams.minOutputChars)
        assertEquals(1500, novelParams.minOutputChars)
        assertTrue(chatParams.autoContinueShortReply)
        assertTrue(rpParams.autoContinueShortReply)
        assertTrue(novelParams.autoContinueShortReply)
        assertEquals(4096, novelParams.maxTokens)
    }
}
