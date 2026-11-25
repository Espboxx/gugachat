package com.gugachat.app.data.api

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.TokenSnapshot
import com.gugachat.app.core.storage.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DirectApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var directClient: DirectApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val ok = OkHttpClient()
        val helper = ApiClient(
            tokenStore = DummyTokenStore,
            serverUrlProvider = { "" },
            languageTagProvider = { "en" },
        )
        directClient = DirectApiClient(ok, ok, helper::parseHttpError)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun all4Protocols_streamChatWithCorrectPathsAndAuthHeaders() = runBlocking {
        // 1. OpenAI Chat
        val oaChat = ApiProviderConfig(
            id = "p1", numericId = 1, protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = baseUrl(), apiKey = "sk-chat-key",
        )
        server.enqueue(
            MockResponse().setResponseCode(200).addHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"OA Chat\"}}]}\n\ndata: [DONE]\n\n"),
        )
        val r1 = directClient.streamChat(
            oaChat,
            OnlineChatCompletionRequestDto(oaChat.syntheticGroupId, "gpt-4o", listOf(WireChatMessageDto.text("user", "hi"))),
        )
        assertEquals("OA Chat", r1.content)
        val req1 = server.takeRequest()
        assertEquals("/v1/chat/completions", req1.path)
        assertEquals("Bearer sk-chat-key", req1.getHeader("Authorization"))

        // 2. OpenAI Responses
        val oaResp = ApiProviderConfig(
            id = "p2", numericId = 2, protocol = ApiProtocol.OPENAI_RESPONSES,
            baseUrl = "${baseUrl()}/v1", apiKey = "sk-resp-key",
        )
        server.enqueue(
            MockResponse().setResponseCode(200).addHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: response.reasoning_summary_text.delta\n" +
                        "data: {\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"think\"}\n\n" +
                        "event: response.output_text.delta\n" +
                        "data: {\"type\":\"response.output_text.delta\",\"delta\":\"OA Resp\"}\n\n" +
                        "event: response.completed\n" +
                        "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"total_tokens\":9}}}\n\n",
                ),
        )
        val r2 = directClient.streamChat(
            oaResp,
            OnlineChatCompletionRequestDto(oaResp.syntheticGroupId, "gpt-5", listOf(WireChatMessageDto.text("user", "hi"))),
        )
        assertEquals("think", r2.reasoning)
        assertEquals("OA Resp", r2.content)
        assertEquals(9, r2.usage?.total_tokens)
        val req2 = server.takeRequest()
        assertEquals("/v1/responses", req2.path)
        assertEquals("Bearer sk-resp-key", req2.getHeader("Authorization"))

        // 3. Anthropic Messages
        val anthropic = ApiProviderConfig(
            id = "p3", numericId = 3, protocol = ApiProtocol.ANTHROPIC,
            baseUrl = baseUrl(), apiKey = "sk-ant-key",
        )
        server.enqueue(
            MockResponse().setResponseCode(200).addHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: content_block_delta\n" +
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"plan\"}}\n\n" +
                        "event: content_block_delta\n" +
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Claude\"}}\n\n" +
                        "event: message_stop\n" +
                        "data: {\"type\":\"message_stop\"}\n\n",
                ),
        )
        val r3 = directClient.streamChat(
            anthropic,
            OnlineChatCompletionRequestDto(anthropic.syntheticGroupId, "claude-sonnet-4-20250514", listOf(WireChatMessageDto.text("user", "hi"))),
        )
        assertEquals("plan", r3.reasoning)
        assertEquals("Claude", r3.content)
        val req3 = server.takeRequest()
        assertEquals("/v1/messages", req3.path)
        assertEquals("sk-ant-key", req3.getHeader("x-api-key"))
        assertEquals("2023-06-01", req3.getHeader("anthropic-version"))

        // 4. Google Gemini
        val gemini = ApiProviderConfig(
            id = "p4", numericId = 4, protocol = ApiProtocol.GEMINI,
            baseUrl = baseUrl(), apiKey = "AIza-gem-key",
        )
        server.enqueue(
            MockResponse().setResponseCode(200).addHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Gemini Hi\"}]}}]}\n\n"),
        )
        val r4 = directClient.streamChat(
            gemini,
            OnlineChatCompletionRequestDto(gemini.syntheticGroupId, "gemini-2.5-flash", listOf(WireChatMessageDto.text("user", "hi"))),
        )
        assertEquals("Gemini Hi", r4.content)
        val req4 = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse", req4.path)
        assertEquals("AIza-gem-key", req4.getHeader("x-goog-api-key"))
    }

    @Test
    fun testAndFetchModels_andGenerateImages_workForOpenAIAndGemini() = runBlocking {
        val oa = ApiProviderConfig(
            id = "p1", numericId = 1, protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = baseUrl(), apiKey = "sk-1", customModels = listOf("my-custom-model"),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"gpt-4o"},{"id":"text-embedding-3-small"}]}"""),
        )
        val oaModels = directClient.testAndFetchModels(oa)
        assertEquals(listOf("my-custom-model", "gpt-4o"), oaModels.map { it.id })
        assertEquals("/v1/models", server.takeRequest().path)

        val pngStub = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val b64Stub = Base64.getEncoder().encodeToString(pngStub)

        // OpenAI /images/generations
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"b64_json":"$b64Stub"}]}"""))
        val oaImgs = directClient.generateImages(
            oa,
            OnlineImageSubmitInput(oa.syntheticGroupId, "openai_chat", "direct", "gpt-image-1", "a duck", count = 1),
        )
        assertEquals(1, oaImgs.size)
        assertArrayEquals(pngStub, oaImgs[0].first)
        assertEquals("/v1/images/generations", server.takeRequest().path)

        // OpenAI /images/edits when sourceImageBytes supplied
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"b64_json":"$b64Stub"}]}"""))
        val editedImgs = directClient.generateImages(
            oa,
            OnlineImageSubmitInput(
                oa.syntheticGroupId,
                "openai_chat",
                "direct",
                "gpt-image-1",
                "add sunglasses",
                mask = ImageBinaryUpload("mask.png", "image/png", byteArrayOf(1, 2, 3)),
            ),
            sourceImageBytes = pngStub,
        )
        assertEquals(1, editedImgs.size)
        val editReq = server.takeRequest()
        assertEquals("/v1/images/edits", editReq.path)
        assertTrue(editReq.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))

        // Gemini image generateContent
        val gem = ApiProviderConfig(
            id = "p4", numericId = 4, protocol = ApiProtocol.GEMINI,
            baseUrl = baseUrl(), apiKey = "AIza-gem",
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/png","data":"$b64Stub"}}]}}]}""",
            ),
        )
        val gemImgs = directClient.generateImages(
            gem,
            OnlineImageSubmitInput(gem.syntheticGroupId, "gemini", "direct", "gemini-2.5-flash-image", "a swan"),
        )
        assertEquals(1, gemImgs.size)
        assertArrayEquals(pngStub, gemImgs[0].first)
        assertEquals("/v1beta/models/gemini-2.5-flash-image:generateContent", server.takeRequest().path)
    }

    @Test
    fun streamChat_deliversIncrementalChunksOffCallerThreadWithIdentityEncoding() = runBlocking {
        val oaChat = ApiProviderConfig(
            id = "p1", numericId = 1, protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = baseUrl(), apiKey = "sk-stream-key",
        )
        val sseBody = (1..4).joinToString("") { idx ->
            "data: {\"choices\":[{\"delta\":{\"content\":\"chunk$idx \"}}]}\n\n"
        } + "data: [DONE]\n\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setChunkedBody(sseBody, 56)
                .throttleBody(56, 70, TimeUnit.MILLISECONDS),
        )

        val callerThread = Thread.currentThread().name
        val deltaThreads = Collections.synchronizedList(mutableListOf<String>())
        val firstDeltaAt = AtomicLong(0L)
        val chunks = Collections.synchronizedList(mutableListOf<String>())

        val result = directClient.streamChat(
            provider = oaChat,
            request = OnlineChatCompletionRequestDto(oaChat.syntheticGroupId, "gpt-4o", listOf(WireChatMessageDto.text("user", "hi"))),
            onDelta = { delta ->
                firstDeltaAt.compareAndSet(0L, System.currentTimeMillis())
                deltaThreads.add(Thread.currentThread().name)
                chunks.add(delta)
            },
        )
        val doneAt = System.currentTimeMillis()

        val recorded = server.takeRequest()
        assertEquals("identity", recorded.getHeader("Accept-Encoding"))
        assertEquals("no-cache", recorded.getHeader("Cache-Control"))
        assertEquals("chunk1 chunk2 chunk3 chunk4 ", result.content)
        assertEquals(4, chunks.size)
        assertTrue("Expected SSE streaming off caller thread ($callerThread), got $deltaThreads", deltaThreads.all { it != callerThread })
        assertTrue("Expected incremental chunks before EOF, delta span was ${doneAt - firstDeltaAt.get()}ms", doneAt - firstDeltaAt.get() >= 100L)
    }
}

private object DummyTokenStore : TokenStore {
    override fun readSnapshot() = TokenSnapshot(null, null, 0L, null)
    override fun getAccessToken(): String? = null
    override fun getRefreshToken(): String? = null
    override fun getTokenExpiresAtMs(): Long = 0L
    override fun getCachedUserJson(): String? = null
    override fun saveTokens(accessToken: String, refreshToken: String?, expiresInSec: Long?, userJson: String?) {}
    override fun saveUserJson(userJson: String) {}
    override fun clear() {}
}
