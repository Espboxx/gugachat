package com.gugachat.app.domain.chat

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.SseParser
import com.gugachat.app.data.api.WireChatMessageDto
import com.gugachat.app.ui.providers.API_PROVIDER_QUICK_PRESETS
import com.gugachat.app.ui.providers.ApiProviderDraft
import com.gugachat.app.ui.providers.canSubmitProviderDraft
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V037ImprovementsTest {

    @Test
    fun rpSystemPromptStaysByteStableAcrossDynamicNpcTurnChanges() {
        val baseNpc = RpCharacter(
            id = "npc_1",
            name = "林逸朗",
            role = "对手",
            personality = "冷静克制",
            speakingStyle = "简洁笃定",
            appearance = "深色西装",
            relationship = "商界旧识",
            secret = "暗中掌握旧账",
            affinity = 10,
            mood = "平静",
            status = "翻阅合同",
            inScene = true,
        )
        val rpTurn1 = RpConfig(
            title = "顶层晚餐",
            worldview = "都市商战",
            playerName = "沈辞",
            cast = listOf(baseNpc),
            state = RpGameState(scene = "顶层餐厅", time = "18:30"),
        )
        val rpTurn2 = rpTurn1.copy(
            cast = listOf(
                baseNpc.copy(
                    affinity = 75,
                    mood = "动容",
                    status = "握紧酒杯",
                    inScene = false,
                ),
            ),
        )

        val sys1 = RpEngine.buildRpSystemPrompt(rpTurn1)
        val sys2 = RpEngine.buildRpSystemPrompt(rpTurn2)
        assertEquals("Mutable NPC turn state must not bust static system prefix cache", sys1, sys2)
        assertFalse(sys1.contains("好感:"))
        assertFalse(sys1.contains("翻阅合同"))

        val user1 = RpEngine.wrapRpTurnUserMessage("举杯致意", RpInputKind.action, rpTurn1)
        val user2 = RpEngine.wrapRpTurnUserMessage("追问旧事", RpInputKind.speech, rpTurn2)
        assertTrue(user1.contains("林逸朗(平静/好感10"))
        assertTrue(user1.contains("状态:翻阅合同"))
        assertTrue(user2.contains("暂离场角色: 林逸朗(握紧酒杯)"))
    }

    @Test
    fun anthropicCachingAndThinkingPrefillSafety() {
        val req = OnlineChatCompletionRequestDto(
            group_id = -1L,
            model = "claude-sonnet-4-5",
            messages = listOf(
                WireChatMessageDto.text("system", "Static system rules"),
                WireChatMessageDto.text("user", "Turn 1 user"),
                WireChatMessageDto.text("assistant", "Turn 1 assistant"),
                WireChatMessageDto.text("user", "Turn 2 user"),
            ),
            temperature = 0.8,
            top_k = 40,
            stop = listOf("User:", "END"),
            enablePromptCaching = true,
            assistantPrefill = "「当然，」",
        )

        val jsonNormal = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildAnthropicRequestJson(req, stream = true),
        ).jsonObject

        // System uses cache_control array when enabled
        val sysArr = jsonNormal["system"] as JsonArray
        assertEquals("ephemeral", sysArr[0].jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        // Stable history turn (index 1 = Turn 1 assistant) carries 2nd cache_control breakpoint
        val msgs = jsonNormal["messages"]!!.jsonArray
        assertEquals(4, msgs.size) // 3 turns + appended assistant prefill
        assertEquals(
            "ephemeral",
            msgs[1].jsonObject["content"]!!.jsonArray.last().jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.content,
        )
        assertEquals("assistant", msgs.last().jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("「当然，」", msgs.last().jsonObject["content"]!!.jsonArray[0].jsonObject["text"]?.jsonPrimitive?.content)
        assertEquals("40", jsonNormal["top_k"]?.jsonPrimitive?.content)
        assertEquals(2, jsonNormal["stop_sequences"]?.jsonArray?.size)

        // With extended thinking enabled: Anthropic rejects prefill and top_k
        val jsonThinking = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildAnthropicRequestJson(req.copy(reasoning_effort = "high"), stream = true),
        ).jsonObject
        assertNull(jsonThinking["top_k"])
        assertEquals(3, jsonThinking["messages"]!!.jsonArray.size)
        assertEquals("user", jsonThinking["messages"]!!.jsonArray.last().jsonObject["role"]?.jsonPrimitive?.content)

        // With prompt caching disabled: plain string system and zero cache_control
        val jsonNoCache = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildAnthropicRequestJson(req.copy(enablePromptCaching = false), stream = true),
        ).jsonObject
        assertTrue(jsonNoCache["system"] is JsonPrimitive)
        assertNull(
            jsonNoCache["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray.last().jsonObject["cache_control"],
        )
    }

    @Test
    fun openAiAndGeminiSamplingAndCacheFieldFiltering() {
        val req = OnlineChatCompletionRequestDto(
            group_id = -1L,
            model = "deepseek-chat",
            messages = listOf(WireChatMessageDto.text("user", "Hi")),
            frequency_penalty = 0.3,
            presence_penalty = 0.2,
            repetition_penalty = 1.1,
            top_k = 50,
            min_p = 0.05,
            stop = listOf("STOP1", "STOP2"),
            enablePromptCaching = true,
            promptCacheKey = "gugachat_conv1",
            assistantPrefill = "Well,",
        )

        val officialObj = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildOpenAIChatRequestJson(req, stream = true, baseUrl = "https://api.openai.com/v1"),
        ).jsonObject
        assertNotNull(officialObj["frequency_penalty"])
        assertNotNull(officialObj["presence_penalty"])
        assertEquals("gugachat_conv1", officialObj["prompt_cache_key"]?.jsonPrimitive?.content)
        assertNull(officialObj["min_p"])
        assertNull(officialObj["repetition_penalty"])
        assertNull(officialObj["top_k"])
        assertEquals(1, officialObj["messages"]!!.jsonArray.size)

        val thirdPartyObj = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildOpenAIChatRequestJson(req, stream = true, baseUrl = "https://api.deepseek.com/v1"),
        ).jsonObject
        assertEquals("0.05", thirdPartyObj["min_p"]?.jsonPrimitive?.content)
        assertEquals("1.1", thirdPartyObj["repetition_penalty"]?.jsonPrimitive?.content)
        assertEquals("50", thirdPartyObj["top_k"]?.jsonPrimitive?.content)
        assertNull(thirdPartyObj["prompt_cache_key"])
        assertEquals(2, thirdPartyObj["messages"]!!.jsonArray.size)
        assertEquals("assistant", thirdPartyObj["messages"]!!.jsonArray.last().jsonObject["role"]?.jsonPrimitive?.content)

        val geminiObj = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildGeminiRequestJson(req),
        ).jsonObject
        val genCfg = geminiObj["generationConfig"]!!.jsonObject
        assertEquals("50", genCfg["topK"]?.jsonPrimitive?.content)
        assertEquals("0.3", genCfg["frequencyPenalty"]?.jsonPrimitive?.content)
        assertEquals(2, genCfg["stopSequences"]?.jsonArray?.size)
        assertEquals("model", geminiObj["contents"]!!.jsonArray.last().jsonObject["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun sseParserNormalizesDeepSeekAndOpenAiCachedTokens() {
        val deepSeekChunk = """{"choices":[{"delta":{"content":"ok"}}],"usage":{"prompt_tokens":10000,"completion_tokens":50,"total_tokens":10050,"prompt_cache_hit_tokens":8200}}"""
        val dsOutcome = SseParser.parseDataPayload(deepSeekChunk)
        assertEquals(8200, dsOutcome?.usage?.cache_read_input_tokens)

        val openAiChunk = """{"choices":[{"delta":{}}],"usage":{"prompt_tokens":5000,"completion_tokens":20,"total_tokens":5020,"prompt_tokens_details":{"cached_tokens":3840}}}"""
        val oaOutcome = SseParser.parseDataPayload(openAiChunk)
        assertEquals(3840, oaOutcome?.usage?.cache_read_input_tokens)
    }

    @Test
    fun contextBuilderHonorsAuthorNoteDepthAndPromptOnlyHistoryCleaning() = runBlocking {
        val hugeStatusBlock = "<status>" + "属性细节".repeat(120) + "</status>"
        val stripStatusScript = RpRegexScript(
            id = "strip_status_prompt",
            name = "Strip status from prompt",
            enabled = true,
            findRegex = "<status>[\\s\\S]*?</status>",
            replaceString = "",
            placement = "ai_output",
            markdownOnly = false,
            promptOnly = true,
            flags = "gims",
        )
        val rp = RpConfig(
            title = "测试剧本",
            playerName = "玩家",
            authorNote = "保持冷峻克制笔调，勿替{{user}}发言。",
            authorNoteDepth = 1,
            regexScripts = listOf(stripStatusScript),
        )
        val conv = ChatConversation(
            id = "c1",
            title = "测试",
            mode = ConversationMode.rp,
            rp = rp,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val messages = listOf(
            ChatMessage(id = "m1", conversationId = "c1", role = "user", content = "开场", createdAt = 1L, seq = 1),
            ChatMessage(id = "m2", conversationId = "c1", role = "assistant", content = "夜雨滂沱。$hugeStatusBlock", createdAt = 2L, seq = 2),
            ChatMessage(id = "m3", conversationId = "c1", role = "user", content = "推门而入", createdAt = 3L, seq = 3),
        )

        val builtDepth1 = ContextBuilder.buildChatContext(ContextBuildInput(conversation = conv, messages = messages))
        val wire = builtDepth1.wireMessages
        // History assistant message must have <status>...</status> stripped by promptOnly regex
        val historyAssistant = wire.first { it.role == "assistant" }
        assertFalse(historyAssistant.textContent.orEmpty().contains("<status>"))
        assertTrue(historyAssistant.textContent.orEmpty().contains("夜雨滂沱"))

        // authorNoteDepth = 1 inserts OOC system message right before the final user message (not inside user turn)
        assertEquals("system", wire[wire.lastIndex - 1].role)
        assertTrue(wire[wire.lastIndex - 1].textContent.orEmpty().contains("勿替玩家发言"))
        assertFalse(wire.last().textContent.orEmpty().contains("【OOC 作者备注】"))

        // Waterline token estimate must reflect cleaned history rather than bloated raw status block
        val convNoScript = conv.copy(rp = rp.copy(regexScripts = emptyList()))
        val builtNoScript = ContextBuilder.buildChatContext(ContextBuildInput(conversation = convNoScript, messages = messages))
        assertTrue(
            "promptOnly history stripping should reduce sent token estimate (${builtDepth1.summary.sentTokens} vs ${builtNoScript.summary.sentTokens})",
            builtDepth1.summary.sentTokens < builtNoScript.summary.sentTokens,
        )

        // authorNoteDepth = 0 keeps OOC note inside final user turn
        val convDepth0 = conv.copy(rp = rp.copy(authorNoteDepth = 0))
        val builtDepth0 = ContextBuilder.buildChatContext(ContextBuildInput(conversation = convDepth0, messages = messages))
        assertEquals(1, builtDepth0.wireMessages.count { it.role == "system" })
        assertTrue(builtDepth0.wireMessages.last().textContent.orEmpty().contains("【OOC 作者备注】"))
    }

    @Test
    fun quickProviderPresetsAndLanBlankKeyValidation() {
        assertEquals(8, API_PROVIDER_QUICK_PRESETS.size)
        for (preset in API_PROVIDER_QUICK_PRESETS) {
            val norm = DirectProtocolCodec.normalizeBaseOrigin(preset.baseUrl)
            assertTrue("Invalid preset base URL: ${preset.id}", norm.startsWith("http"))
        }
        val ollamaDraft = ApiProviderDraft(
            protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = "http://192.168.1.10:11434/v1",
            apiKey = "",
        )
        assertTrue(canSubmitProviderDraft(ollamaDraft))

        val deepSeekNoKey = ApiProviderDraft(
            protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "",
        )
        assertFalse(canSubmitProviderDraft(deepSeekNoKey))
        assertTrue(canSubmitProviderDraft(deepSeekNoKey.copy(apiKey = "sk-test")))
    }
}
