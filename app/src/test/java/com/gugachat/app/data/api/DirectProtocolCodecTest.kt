package com.gugachat.app.data.api

import com.gugachat.app.core.storage.ApiProtocol
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class DirectProtocolCodecTest {

    @Test
    fun urlBuilders_handleBareHostsVersionedPathsAndPastedEndpoints() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            DirectProtocolCodec.openaiChatUrl("api.openai.com"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            DirectProtocolCodec.openaiChatUrl("https://api.openai.com/v1/"),
        )
        assertEquals(
            "https://proxy.example.com/custom/v4/responses",
            DirectProtocolCodec.openaiResponsesUrl("https://proxy.example.com/custom/v4"),
        )
        assertEquals(
            "https://proxy.example.com/v1/chat/completions",
            DirectProtocolCodec.openaiChatUrl("https://proxy.example.com/v1/chat/completions"),
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            DirectProtocolCodec.anthropicMessagesUrl("https://api.anthropic.com"),
        )
        assertEquals(
            "https://relay.example.com/claude/v1/messages",
            DirectProtocolCodec.anthropicMessagesUrl("https://relay.example.com/claude/v1/messages"),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
            DirectProtocolCodec.geminiChatUrl("generativelanguage.googleapis.com", "models/gemini-2.5-flash", stream = true),
        )
        assertEquals(
            "https://gemini.proxy.com/v1/models/gemini-2.5-pro:generateContent",
            DirectProtocolCodec.geminiChatUrl("https://gemini.proxy.com/v1", "gemini-2.5-pro", stream = false),
        )
    }

    @Test
    fun requestBuilders_encodeSystemVisionAndThinkingPerProtocol() {
        val b64Png = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        val req = OnlineChatCompletionRequestDto(
            group_id = -1_000_000_001L,
            model = "claude-sonnet-4-20250514",
            messages = listOf(
                WireChatMessageDto.text("system", "You are GuGu."),
                WireChatMessageDto.parts(
                    "user",
                    listOf(
                        WireChatContentPartDto("text", text = "Describe this"),
                        WireChatContentPartDto("image_url", image_url = WireImageUrlDto("data:image/png;base64,$b64Png")),
                    ),
                ),
            ),
            temperature = 0.7,
            max_tokens = 2048,
            reasoning_effort = "medium",
        )

        val responsesObj = AppJson.parseToJsonElement(DirectProtocolCodec.buildOpenAIResponsesRequestJson(req, true)).jsonObject
        assertEquals("You are GuGu.", responsesObj["instructions"]?.jsonPrimitive?.content)
        assertEquals(2048, responsesObj["max_output_tokens"]?.jsonPrimitive?.content?.toInt())
        assertEquals("medium", responsesObj["reasoning"]?.jsonObject?.get("effort")?.jsonPrimitive?.content)
        val respContent = responsesObj["input"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("input_text", respContent[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("input_image", respContent[1].jsonObject["type"]?.jsonPrimitive?.content)

        val claudeObj = AppJson.parseToJsonElement(DirectProtocolCodec.buildAnthropicRequestJson(req, true)).jsonObject
        val claudeSysBlock = claudeObj["system"]!!.jsonArray[0].jsonObject
        assertEquals("You are GuGu.", claudeSysBlock["text"]?.jsonPrimitive?.content)
        assertEquals("ephemeral", claudeSysBlock["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertFalse(claudeObj.containsKey("temperature"))
        assertEquals("enabled", claudeObj["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals(5120, claudeObj["max_tokens"]?.jsonPrimitive?.content?.toInt())
        val claudeBlocks = claudeObj["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("image", claudeBlocks[1].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals(b64Png, claudeBlocks[1].jsonObject["source"]?.jsonObject?.get("data")?.jsonPrimitive?.content)

        val gemObj = AppJson.parseToJsonElement(DirectProtocolCodec.buildGeminiRequestJson(req)).jsonObject
        assertEquals("You are GuGu.", gemObj["systemInstruction"]?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content)
        val gemParts = gemObj["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals(b64Png, gemParts[1].jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content)
        assertEquals("true", gemObj["generationConfig"]?.jsonObject?.get("thinkingConfig")?.jsonObject?.get("includeThoughts")?.jsonPrimitive?.content)
    }

    @Test
    fun sseAndSingleParsers_decodeResponsesAnthropicAndGemini() {
        val respThink = DirectProtocolCodec.parseOpenAIResponsesSse(
            "response.reasoning_summary_text.delta",
            """{"type":"response.reasoning_summary_text.delta","delta":"step 1"}""",
        )
        assertEquals("step 1", respThink?.deltaReasoning)

        val respDelta = DirectProtocolCodec.parseOpenAIResponsesSse(
            "response.output_text.delta",
            """{"type":"response.output_text.delta","delta":"Hello!"}""",
        )
        assertEquals("Hello!", respDelta?.deltaText)

        val respDone = DirectProtocolCodec.parseOpenAIResponsesSse(
            "response.completed",
            """{"type":"response.completed","response":{"usage":{"input_tokens":12,"output_tokens":5,"total_tokens":17}}}""",
        )
        assertTrue(respDone?.done == true)
        assertEquals(17, respDone?.usage?.total_tokens)

        val claudeStart = DirectProtocolCodec.parseAnthropicSse(
            "message_start",
            """{"type":"message_start","message":{"usage":{"input_tokens":100,"cache_read_input_tokens":40}}}""",
        )
        assertEquals(100, claudeStart?.usage?.prompt_tokens)
        assertEquals(40, claudeStart?.usage?.cache_read_input_tokens)

        val claudeThink = DirectProtocolCodec.parseAnthropicSse(
            "content_block_delta",
            """{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"hmm"}}""",
        )
        assertEquals("hmm", claudeThink?.deltaReasoning)

        val claudeText = DirectProtocolCodec.parseAnthropicSse(
            "content_block_delta",
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Claude reply"}}""",
        )
        assertEquals("Claude reply", claudeText?.deltaText)

        val gemThought = DirectProtocolCodec.parseGeminiSseData(
            """{"candidates":[{"content":{"parts":[{"text":"gem think","thought":true},{"text":"gem ans"}]}}],"usageMetadata":{"promptTokenCount":9,"candidatesTokenCount":4,"totalTokenCount":13}}""",
        )
        assertEquals("gem think", gemThought?.deltaReasoning)
        assertEquals("gem ans", gemThought?.deltaText)
        assertEquals(13, gemThought?.usage?.total_tokens)

        val singleClaude = DirectProtocolCodec.parseAnthropicSingleJson(
            """{"content":[{"type":"thinking","thinking":"t"},{"type":"text","text":"ans"}],"usage":{"input_tokens":3,"output_tokens":7}}""",
            200,
        )
        assertEquals("ans", singleClaude.content)
        assertEquals("t", singleClaude.reasoning)
        assertEquals(10, singleClaude.usage?.total_tokens)
    }

    @Test
    fun modelsAndImagesParsers_workAcrossProtocols() {
        val gemModels = DirectProtocolCodec.parseModelsJson(
            ApiProtocol.GEMINI,
            """{"models":[{"name":"models/gemini-2.5-flash","inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent"]},{"name":"models/embedding-001","supportedGenerationMethods":["embedContent"]}]}""",
        )
        assertEquals(1, gemModels.size)
        assertEquals("gemini-2.5-flash", gemModels[0].id)
        assertEquals(1048576, gemModels[0].context_window)

        val rawImg = byteArrayOf(9, 8, 7, 6)
        val b64Img = Base64.getEncoder().encodeToString(rawImg)
        val oaImages = DirectProtocolCodec.parseOpenAIImagesJson("""{"data":[{"b64_json":"$b64Img"}]}""", 200)
        assertEquals(1, oaImages.size)
        assertArrayEquals(rawImg, oaImages[0].bytes)
        assertNull(oaImages[0].url)

        val gemImages = DirectProtocolCodec.parseGeminiImagesJson(
            """{"candidates":[{"content":{"parts":[{"text":"done"},{"inlineData":{"mimeType":"image/png","data":"$b64Img"}}]}}]}""",
            200,
        )
        assertEquals(1, gemImages.size)
        assertNotNull(gemImages[0].bytes)
        assertArrayEquals(rawImg, gemImages[0].bytes)
    }

    @Test
    fun jsonNullFields_doNotThrowOrEvaluateAsLiteralNull() {
        val respDoneNullUsage = DirectProtocolCodec.parseOpenAIResponsesSse(
            "response.completed",
            """{"type":"response.completed","response":{"usage":null}}""",
        )
        assertTrue(respDoneNullUsage?.done == true)
        assertNull(respDoneNullUsage?.usage)

        val respNullDelta = DirectProtocolCodec.parseOpenAIResponsesSse(
            "response.output_text.delta",
            """{"type":"response.output_text.delta","delta":null}""",
        )
        assertEquals("", respNullDelta?.deltaText)

        val respSingle = DirectProtocolCodec.parseOpenAIResponsesSingleJson(
            """{"output_text":null,"output":[{"type":"message","content":[{"type":"output_text","text":"Hi"}]}],"usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15,"input_tokens_details":null}}""",
            200,
        )
        assertEquals("Hi", respSingle.content)
        assertEquals(15, respSingle.usage?.total_tokens)
        assertNull(respSingle.usage?.cache_read_input_tokens)

        val gemNullFinish = DirectProtocolCodec.parseGeminiSseData(
            """{"candidates":[{"content":{"parts":[{"text":null},{"text":"ok"}]},"finishReason":null}]}""",
        )
        assertEquals("ok", gemNullFinish?.deltaText)
        assertNull(gemNullFinish?.finishReason)

        val errEx = try {
            DirectProtocolCodec.parseOpenAIResponsesSse(
                "response.failed",
                """{"type":"response.failed","response":{"error":{"code":null,"message":"Upstream failure"}}}""",
            )
            null
        } catch (e: ApiException) {
            e
        }
        assertNotNull(errEx)
        assertEquals("RESPONSES_ERROR", errEx?.code)
        assertEquals("Upstream failure", errEx?.message)

        val nullMsgErr = try {
            DirectProtocolCodec.parseAnthropicSingleJson(
                """{"error":{"code":null,"message":null}}""",
                500,
            )
            null
        } catch (e: ApiException) {
            e
        }
        assertNotNull(nullMsgErr)
        assertNull(nullMsgErr?.code)
        assertEquals("API error", nullMsgErr?.message)

        val nullCodeFallbackErr = try {
            DirectProtocolCodec.parseOpenAIResponsesSingleJson(
                """{"error":{"code":null,"type":"rate_limit_error","message":"Too many requests"}}""",
                429,
            )
            null
        } catch (e: ApiException) {
            e
        }
        assertNotNull(nullCodeFallbackErr)
        assertEquals("rate_limit_error", nullCodeFallbackErr?.code)
        assertEquals("Too many requests", nullCodeFallbackErr?.message)
    }

    @Test
    fun buildGeminiRequestJson_mergesAssistantPrefillIntoTrailingModelTurn() {
        val mergedReq = OnlineChatCompletionRequestDto(
            group_id = -1_000_000_001L,
            model = "gemini-2.5-flash",
            messages = listOf(
                WireChatMessageDto.text("user", "Start a story"),
                WireChatMessageDto.text("assistant", "Chapter 1: "),
            ),
            assistantPrefill = "Once upon a time",
        )
        val mergedObj = AppJson.parseToJsonElement(DirectProtocolCodec.buildGeminiRequestJson(mergedReq)).jsonObject
        val mergedContents = mergedObj["contents"]!!.jsonArray
        assertEquals(2, mergedContents.size)
        assertEquals("user", mergedContents[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("model", mergedContents[1].jsonObject["role"]?.jsonPrimitive?.content)
        val modelParts = mergedContents[1].jsonObject["parts"]!!.jsonArray
        assertEquals(2, modelParts.size)
        assertEquals("Chapter 1: ", modelParts[0].jsonObject["text"]?.jsonPrimitive?.content)
        assertEquals("Once upon a time", modelParts[1].jsonObject["text"]?.jsonPrimitive?.content)

        val appendedReq = OnlineChatCompletionRequestDto(
            group_id = -1_000_000_001L,
            model = "gemini-2.5-flash",
            messages = listOf(WireChatMessageDto.text("user", "Hello")),
            assistantPrefill = "Sure,",
        )
        val appendedObj = AppJson.parseToJsonElement(DirectProtocolCodec.buildGeminiRequestJson(appendedReq)).jsonObject
        val appendedContents = appendedObj["contents"]!!.jsonArray
        assertEquals(2, appendedContents.size)
        assertEquals("user", appendedContents[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("model", appendedContents[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("Sure,", appendedContents[1].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun thinkingWithMaxTokens_respectsVisibleHeadroomFloorAcrossAnthropicAndGemini() {
        // Anthropic: max_tokens slightly above thinkingBudget (4500 vs 4096) still gets floored to 4096 + 1024 = 5120
        val tightReq = OnlineChatCompletionRequestDto(
            group_id = -1_000_000_001L,
            model = "claude-sonnet-4-20250514",
            messages = listOf(WireChatMessageDto.text("user", "Write deeply")),
            max_tokens = 4500,
            reasoning_effort = "medium",
        )
        val tightClaude = AppJson.parseToJsonElement(DirectProtocolCodec.buildAnthropicRequestJson(tightReq, true)).jsonObject
        assertEquals(5120, tightClaude["max_tokens"]?.jsonPrimitive?.content?.toInt())

        // Gemini: maxOutputTokens below thinkingBudget (2048 vs 4096) gets floored to 5120 so thinking never starves visible text
        val tightGemini = AppJson.parseToJsonElement(DirectProtocolCodec.buildGeminiRequestJson(tightReq.copy(max_tokens = 2048))).jsonObject
        assertEquals(
            5120,
            tightGemini["generationConfig"]?.jsonObject?.get("maxOutputTokens")?.jsonPrimitive?.content?.toInt(),
        )

        // Larger floored max_tokens (e.g. from OutputLengthPolicy for minOutputChars + thinking) is preserved cleanly
        val largeFloorReq = OnlineChatCompletionRequestDto(
            group_id = -1_000_000_001L,
            model = "claude-sonnet-4-20250514",
            messages = listOf(WireChatMessageDto.text("user", "Write 3000 chars")),
            max_tokens = 12_800,
            reasoning_effort = "high",
        )
        val largeClaude = AppJson.parseToJsonElement(DirectProtocolCodec.buildAnthropicRequestJson(largeFloorReq, true)).jsonObject
        assertEquals(12_800, largeClaude["max_tokens"]?.jsonPrimitive?.content?.toInt())
        assertEquals(8192, largeClaude["thinking"]?.jsonObject?.get("budget_tokens")?.jsonPrimitive?.content?.toInt())

        val largeGemini = AppJson.parseToJsonElement(DirectProtocolCodec.buildGeminiRequestJson(largeFloorReq)).jsonObject
        assertEquals(
            12_800,
            largeGemini["generationConfig"]?.jsonObject?.get("maxOutputTokens")?.jsonPrimitive?.content?.toInt(),
        )
        assertEquals(
            8192,
            largeGemini["generationConfig"]?.jsonObject?.get("thinkingConfig")?.jsonObject?.get("thinkingBudget")?.jsonPrimitive?.content?.toInt(),
        )

        // Gemini with max_tokens = null still omits maxOutputTokens to preserve native model limit
        val autoGemini = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildGeminiRequestJson(largeFloorReq.copy(max_tokens = null)),
        ).jsonObject
        assertFalse(autoGemini["generationConfig"]!!.jsonObject.containsKey("maxOutputTokens"))
    }
}
