package com.gugachat.app.data

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.TokenSnapshot
import com.gugachat.app.core.storage.TokenStore
import com.gugachat.app.data.api.ApiClient
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.ChatTokenUsageDto
import com.gugachat.app.data.api.DirectApiClient
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.DiscoveredModelKind
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.RemoteApiEmbeddingEngine
import com.gugachat.app.data.api.RemoteApiRerankEngine
import com.gugachat.app.data.api.WireChatMessageDto
import com.gugachat.app.domain.chat.RemoteEmbeddingConfig
import com.gugachat.app.domain.chat.RemoteRerankConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DirectEmbeddingRerankCodecTest {
    private lateinit var server: MockWebServer
    private lateinit var directClient: DirectApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val ok = OkHttpClient()
        val helper = ApiClient(
            tokenStore = NoopTokenStore,
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
    fun urlBuilders_handleOpenAiZhipuDashScopeOllamaAndGeminiPaths() {
        assertEquals(
            "https://api.siliconflow.cn/v1/embeddings",
            DirectProtocolCodec.openaiEmbeddingsUrl("https://api.siliconflow.cn/v1"),
        )
        assertEquals(
            "https://api.siliconflow.cn/v1/embeddings",
            DirectProtocolCodec.openaiEmbeddingsUrl("https://api.siliconflow.cn/v1/embeddings"),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/embeddings",
            DirectProtocolCodec.openaiEmbeddingsUrl("https://open.bigmodel.cn/api/paas/v4"),
        )
        assertEquals(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings",
            DirectProtocolCodec.openaiEmbeddingsUrl("https://dashscope.aliyuncs.com/compatible-mode/v1"),
        )
        assertEquals(
            "http://127.0.0.1:11434/v1/embeddings",
            DirectProtocolCodec.openaiEmbeddingsUrl("http://127.0.0.1:11434"),
        )
        assertEquals(
            "https://api.siliconflow.cn/v1/rerank",
            DirectProtocolCodec.openaiRerankUrl("https://api.siliconflow.cn/v1/rerank"),
        )
        assertEquals(
            "https://api.jina.ai/v1/rerank",
            DirectProtocolCodec.openaiRerankUrl("https://api.jina.ai"),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent",
            DirectProtocolCodec.geminiEmbedContentUrl("https://generativelanguage.googleapis.com", "gemini-embedding-001"),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1/models/gemini-embedding-001:batchEmbedContents",
            DirectProtocolCodec.geminiBatchEmbedContentsUrl(
                "https://generativelanguage.googleapis.com/v1/models/gemini-embedding-001:embedContent",
                "models/gemini-embedding-001",
            ),
        )
    }

    @Test
    fun codecs_parseSortedOpenAiEmbeddingsGeminiAndCohereOrTeiRerank() {
        val oaReq = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildOpenAIEmbeddingRequestJson(
                modelId = "text-embedding-3-small",
                texts = listOf("a", "b"),
                dimensions = 512,
            ),
        ).jsonObject
        assertEquals("text-embedding-3-small", oaReq["model"]?.jsonPrimitive?.content)
        assertEquals(512, oaReq["dimensions"]?.jsonPrimitive?.int)
        assertEquals(2, oaReq["input"]?.jsonArray?.size)

        // Out-of-order response indices must be sorted back into input order
        val oaParsed = DirectProtocolCodec.parseOpenAIEmbeddingsJson(
            """{"data":[{"index":1,"embedding":[0.2,0.8]},{"index":0,"embedding":[0.9,0.1]}]}""",
            200,
        )
        assertEquals(2, oaParsed.size)
        assertArrayEquals(floatArrayOf(0.9f, 0.1f), oaParsed[0], 1e-6f)
        assertArrayEquals(floatArrayOf(0.2f, 0.8f), oaParsed[1], 1e-6f)

        // Gemini batch request & response
        val gemReq = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildGeminiBatchEmbedContentsRequestJson(
                modelId = "gemini-embedding-001",
                texts = listOf("doc1", "doc2"),
                isQuery = false,
                outputDimensionality = 768,
            ),
        ).jsonObject
        val reqArr = gemReq["requests"]!!.jsonArray
        assertEquals(2, reqArr.size)
        assertEquals("RETRIEVAL_DOCUMENT", reqArr[0].jsonObject["taskType"]?.jsonPrimitive?.content)
        assertEquals(768, reqArr[0].jsonObject["outputDimensionality"]?.jsonPrimitive?.int)
        assertEquals("models/gemini-embedding-001", reqArr[0].jsonObject["model"]?.jsonPrimitive?.content)

        val gemParsed = DirectProtocolCodec.parseGeminiEmbeddingsJson(
            """{"embeddings":[{"values":[0.1,0.2,0.3]},{"values":[0.4,0.5,0.6]}]}""",
            200,
        )
        assertEquals(2, gemParsed.size)
        assertArrayEquals(floatArrayOf(0.4f, 0.5f, 0.6f), gemParsed[1], 1e-6f)

        // Cohere rerank schema
        val cohereReq = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildRerankRequestJson(
                modelId = "BAAI/bge-reranker-v2-m3",
                query = "银针",
                documents = listOf("d0", "d1"),
                topN = 2,
                useTeiSchema = false,
            ),
        ).jsonObject
        assertEquals("BAAI/bge-reranker-v2-m3", cohereReq["model"]?.jsonPrimitive?.content)
        assertEquals("false", cohereReq["return_documents"]?.jsonPrimitive?.content)
        assertEquals(2, cohereReq["documents"]?.jsonArray?.size)

        val cohereParsed = DirectProtocolCodec.parseRerankResponseJson(
            """{"results":[{"index":1,"relevance_score":0.94},{"index":0,"relevance_score":0.18}]}""",
            200,
        )
        assertEquals(0.94, cohereParsed[1]!!, 1e-6)
        assertEquals(0.18, cohereParsed[0]!!, 1e-6)

        // TEI rerank schema + top-level array response
        val teiReq = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildRerankRequestJson(
                modelId = "unused",
                query = "银针",
                documents = listOf("d0", "d1"),
                topN = 2,
                useTeiSchema = true,
            ),
        ).jsonObject
        assertTrue(teiReq.containsKey("texts"))
        assertFalse(teiReq.containsKey("documents"))

        val teiParsed = DirectProtocolCodec.parseRerankResponseJson(
            """[{"index":0,"score":0.87},{"index":1,"score":0.23}]""",
            200,
        )
        assertEquals(0.87, teiParsed[0]!!, 1e-6)
        assertEquals(0.23, teiParsed[1]!!, 1e-6)

        // DashScope nested output.results rerank format & Ollama native embeddings fallback
        val dashParsed = DirectProtocolCodec.parseRerankResponseJson(
            """{"output":{"results":[{"index":2,"relevance_score":0.91}]}}""",
            200,
        )
        assertEquals(0.91, dashParsed[2]!!, 1e-6)

        val ollamaNative = DirectProtocolCodec.parseOpenAIEmbeddingsJson(
            """{"embeddings":[[0.3,0.4],[0.5,0.6]]}""",
            200,
        )
        assertEquals(2, ollamaNative.size)
        assertArrayEquals(floatArrayOf(0.5f, 0.6f), ollamaNative[1], 1e-6f)
    }

    @Test
    fun parseEmbeddingAndRerankModelsJson_classifiesModelsAccurately() {
        val sfJson = """
            {
              "data": [
                {"id": "deepseek-ai/DeepSeek-V3"},
                {"id": "BAAI/bge-m3"},
                {"id": "BAAI/bge-reranker-v2-m3"},
                {"id": "Qwen/Qwen3-Embedding-0.6B"},
                {"id": "Qwen/Qwen3-Reranker-4B"},
                {"id": "gte-rerank-v2"},
                {"id": "intfloat/multilingual-e5-small"},
                {"id": "moka-ai/m3e-base"},
                {"id": "text-embedding-3-small"},
                {"id": "tts-1"}
              ]
            }
        """.trimIndent()
        val parsed = DirectProtocolCodec.parseEmbeddingAndRerankModelsJson(ApiProtocol.OPENAI_CHAT, sfJson)
        val byId = parsed.associate { it.id to it.kind }

        assertFalse(byId.containsKey("deepseek-ai/DeepSeek-V3"))
        assertFalse(byId.containsKey("tts-1"))
        assertEquals(DiscoveredModelKind.EMBEDDING, byId["BAAI/bge-m3"])
        assertEquals(DiscoveredModelKind.RERANK, byId["BAAI/bge-reranker-v2-m3"])
        assertEquals(DiscoveredModelKind.EMBEDDING, byId["Qwen/Qwen3-Embedding-0.6B"])
        assertEquals(DiscoveredModelKind.RERANK, byId["Qwen/Qwen3-Reranker-4B"])
        assertEquals(DiscoveredModelKind.RERANK, byId["gte-rerank-v2"])
        assertEquals(DiscoveredModelKind.EMBEDDING, byId["intfloat/multilingual-e5-small"])
        assertEquals(DiscoveredModelKind.EMBEDDING, byId["moka-ai/m3e-base"])
        assertEquals(DiscoveredModelKind.EMBEDDING, byId["text-embedding-3-small"])

        val gemJson = """
            {
              "models": [
                {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-embedding-001", "supportedGenerationMethods": ["embedContent"]}
              ]
            }
        """.trimIndent()
        val gemModels = DirectProtocolCodec.parseEmbeddingAndRerankModelsJson(ApiProtocol.GEMINI, gemJson)
        assertEquals(1, gemModels.size)
        assertEquals("gemini-embedding-001", gemModels[0].id)
        assertEquals(DiscoveredModelKind.EMBEDDING, gemModels[0].kind)
    }

    @Test
    fun parseAnthropicUsage_accountsForCacheCreationAndTotalInputTokens() {
        val single = DirectProtocolCodec.parseAnthropicSingleJson(
            """{
                "content": [{"type": "text", "text": "ok"}],
                "usage": {
                    "input_tokens": 120,
                    "cache_creation_input_tokens": 800,
                    "cache_read_input_tokens": 2000,
                    "output_tokens": 50
                }
            }""",
            200,
        )
        val usage = single.usage!!
        // prompt_tokens includes uncached input + cache_creation; cache_read kept separate for backward compatibility
        assertEquals(920, usage.prompt_tokens)
        assertEquals(2000, usage.cache_read_input_tokens)
        assertEquals(50, usage.completion_tokens)
        assertEquals(2970, usage.total_tokens)
        // Accurate total input tokens for EMA calibration = 120 + 800 + 2000 = 2920
        assertEquals(2920, DirectProtocolCodec.totalInputTokens(ApiProtocol.ANTHROPIC, usage))
        // OpenAI prompt_tokens already includes cached tokens -> no double-counting
        assertEquals(
            1000,
            DirectProtocolCodec.totalInputTokens(
                ApiProtocol.OPENAI_CHAT,
                ChatTokenUsageDto(prompt_tokens = 1000, cache_read_input_tokens = 600),
            ),
        )
    }

    @Test
    fun isLocalOrLanHost_recognizesLoopbackRfc1918AndMdnsOnly() {
        assertTrue(DirectApiClient.isLocalOrLanHost("http://localhost:11434/v1"))
        assertTrue(DirectApiClient.isLocalOrLanHost("http://127.0.0.1:11434"))
        assertTrue(DirectApiClient.isLocalOrLanHost("http://[::1]:1234/v1"))
        assertTrue(DirectApiClient.isLocalOrLanHost("http://10.0.0.8:8080/v1"))
        assertTrue(DirectApiClient.isLocalOrLanHost("http://192.168.1.50:11434/v1"))
        assertTrue(DirectApiClient.isLocalOrLanHost("http://172.16.0.1:1234/v1"))
        assertTrue(DirectApiClient.isLocalOrLanHost("http://172.31.255.1:1234/v1"))
        assertTrue(DirectApiClient.isLocalOrLanHost("http://mac-mini.local:11434/v1"))

        assertFalse(DirectApiClient.isLocalOrLanHost("http://172.15.0.1:1234/v1"))
        assertFalse(DirectApiClient.isLocalOrLanHost("http://172.32.0.1:1234/v1"))
        assertFalse(DirectApiClient.isLocalOrLanHost("https://api.openai.com/v1"))
        assertFalse(DirectApiClient.isLocalOrLanHost("https://api.siliconflow.cn/v1"))
        assertFalse(DirectApiClient.isLocalOrLanHost("https://localhost.evil.com/v1"))
        assertFalse(DirectApiClient.isLocalOrLanHost("https://192.168.1.1.evil.com/v1"))
    }

    @Test
    fun mockWebServer_endToEndEmbeddingsRerankLanOllamaAndAnthropicStreamUsage(): Unit = runBlocking {
        // 1. OpenAI / SiliconFlow embedding + testEmbeddingConnection
        val sfProvider = ApiProviderConfig(
            id = "sf",
            numericId = 1,
            protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = baseUrl(),
            apiKey = "sk-sf-key",
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"index":1,"embedding":[0.1,0.2,0.3,0.4]},{"index":0,"embedding":[0.9,0.8,0.7,0.6]}]}""",
            ),
        )
        val embEngine = RemoteApiEmbeddingEngine(
            directApiClient = directClient,
            provider = sfProvider,
            config = RemoteEmbeddingConfig(
                providerId = "sf",
                protocol = ApiProtocol.OPENAI_CHAT,
                modelId = "Qwen/Qwen3-Embedding-0.6B",
                dimensions = 4,
            ),
        )
        assertTrue(embEngine.isAvailable())
        assertEquals("remote|sf|Qwen/Qwen3-Embedding-0.6B|4", embEngine.profileKey)
        val sfVecs = embEngine.embed(listOf("first", "second"), isQuery = false)
        assertEquals(2, sfVecs.size)
        assertArrayEquals(floatArrayOf(0.9f, 0.8f, 0.7f, 0.6f), sfVecs[0], 1e-6f)
        val req1 = server.takeRequest()
        assertEquals("/v1/embeddings", req1.path)
        assertEquals("Bearer sk-sf-key", req1.getHeader("Authorization"))

        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"data":[{"index":0,"embedding":[0.5,0.5,0.5]}]}"""),
        )
        val connTest = directClient.testEmbeddingConnection(sfProvider, "BAAI/bge-m3")
        assertEquals(3, connTest.dimension)
        assertEquals("BAAI/bge-m3", connTest.modelId)
        assertEquals("/v1/embeddings", server.takeRequest().path)

        // 2. Gemini single (:embedContent) and batch (:batchEmbedContents)
        val gemProvider = ApiProviderConfig(
            id = "gem",
            numericId = 2,
            protocol = ApiProtocol.GEMINI,
            baseUrl = baseUrl(),
            apiKey = "AIza-test",
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"embedding":{"values":[0.2,0.4]}}"""))
        val gemSingle = directClient.embedTexts(gemProvider, "gemini-embedding-001", listOf("query"), isQuery = true, dimensions = 2)
        assertEquals(1, gemSingle.size)
        val gemReq1 = server.takeRequest()
        assertEquals("/v1beta/models/gemini-embedding-001:embedContent", gemReq1.path)
        assertEquals("AIza-test", gemReq1.getHeader("x-goog-api-key"))
        assertTrue(gemReq1.body.readUtf8().contains("RETRIEVAL_QUERY"))

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"embeddings":[{"values":[0.1,0.9]},{"values":[0.8,0.2]}]}""",
            ),
        )
        val gemBatch = directClient.embedTexts(gemProvider, "gemini-embedding-001", listOf("d1", "d2"), isQuery = false, dimensions = 2)
        assertEquals(2, gemBatch.size)
        val gemReq2 = server.takeRequest()
        assertEquals("/v1beta/models/gemini-embedding-001:batchEmbedContents", gemReq2.path)
        assertTrue(gemReq2.body.readUtf8().contains("RETRIEVAL_DOCUMENT"))

        // 3. LAN Ollama (127.0.0.1) with EMPTY API key succeeds & omits Authorization header
        val ollamaProvider = ApiProviderConfig(
            id = "ollama",
            numericId = 3,
            protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = baseUrl(), // MockWebServer binds to localhost / 127.0.0.1
            apiKey = "",
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"index":0,"embedding":[0.3,0.4]}]}"""))
        val ollamaVecs = directClient.embedTexts(ollamaProvider, "bge-m3", listOf("本地检索"))
        assertEquals(1, ollamaVecs.size)
        val ollamaReq = server.takeRequest()
        assertEquals("/v1/embeddings", ollamaReq.path)
        assertNull(ollamaReq.getHeader("Authorization"))

        // 4. Remote public host with empty API key still fails with NO_API_KEY
        try {
            directClient.embedTexts(
                ApiProviderConfig(
                    id = "pub",
                    protocol = ApiProtocol.OPENAI_CHAT,
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "",
                ),
                "text-embedding-3-small",
                listOf("hi"),
            )
            fail("Expected NO_API_KEY for public cloud host without apiKey")
        } catch (e: ApiException) {
            assertEquals("NO_API_KEY", e.code)
        }

        // 5. Cohere & TEI Rerank via RemoteApiRerankEngine
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"results":[{"index":1,"relevance_score":0.95},{"index":0,"relevance_score":0.12}]}""",
            ),
        )
        val cohereReranker = RemoteApiRerankEngine(
            directApiClient = directClient,
            provider = sfProvider,
            config = RemoteRerankConfig(enabled = true, providerId = "sf", modelId = "BAAI/bge-reranker-v2-m3"),
        )
        val cohereScores = cohereReranker.rerank("暗器", listOf("厨艺", "袖口银针"), topN = 2)
        assertEquals(0.95, cohereScores[1]!!, 1e-6)
        assertEquals("/v1/rerank", server.takeRequest().path)

        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""[{"index":0,"score":0.88}]"""),
        )
        val teiReranker = RemoteApiRerankEngine(
            directApiClient = directClient,
            provider = ollamaProvider,
            config = RemoteRerankConfig(enabled = true, modelId = "", useTeiSchema = true),
        )
        val teiScores = teiReranker.rerank("暗器", listOf("袖口银针"), topN = 1)
        assertEquals(0.88, teiScores[0]!!, 1e-6)
        val teiReq = server.takeRequest()
        assertEquals("/v1/rerank", teiReq.path)
        assertTrue(teiReq.body.readUtf8().contains("\"texts\""))

        // 6. fetchEmbeddingAndRerankModels
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"id":"BAAI/bge-m3"},{"id":"BAAI/bge-reranker-v2-m3"},{"id":"gpt-4o"}]}""",
            ),
        )
        val discovered = directClient.fetchEmbeddingAndRerankModels(sfProvider)
        assertEquals(2, discovered.size)
        assertEquals("/v1/models", server.takeRequest().path)

        // 7. Anthropic streaming merges message_start cache_creation + cache_read with message_delta output
        val anthropic = ApiProviderConfig(
            id = "ant",
            numericId = 4,
            protocol = ApiProtocol.ANTHROPIC,
            baseUrl = baseUrl(),
            apiKey = "sk-ant",
        )
        server.enqueue(
            MockResponse().setResponseCode(200).addHeader("Content-Type", "text/event-stream").setBody(
                "event: message_start\n" +
                    "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":100,\"cache_creation_input_tokens\":500,\"cache_read_input_tokens\":400,\"output_tokens\":1}}}\n\n" +
                    "event: content_block_delta\n" +
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n" +
                    "event: message_delta\n" +
                    "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":25}}\n\n" +
                    "event: message_stop\n" +
                    "data: {\"type\":\"message_stop\"}\n\n",
            ),
        )
        val streamRes = directClient.streamChat(
            anthropic,
            OnlineChatCompletionRequestDto(anthropic.syntheticGroupId, "claude-sonnet-4-20250514", listOf(WireChatMessageDto.text("user", "hi"))),
        )
        assertEquals(600, streamRes.usage?.prompt_tokens)
        assertEquals(400, streamRes.usage?.cache_read_input_tokens)
        assertEquals(25, streamRes.usage?.completion_tokens)
        assertEquals(1025, streamRes.usage?.total_tokens)
        assertEquals(1000, DirectProtocolCodec.totalInputTokens(ApiProtocol.ANTHROPIC, streamRes.usage!!))
        server.takeRequest()
    }
}

private object NoopTokenStore : TokenStore {
    override fun readSnapshot() = TokenSnapshot(null, null, 0L, null)
    override fun getAccessToken(): String? = null
    override fun getRefreshToken(): String? = null
    override fun getTokenExpiresAtMs(): Long = 0L
    override fun getCachedUserJson(): String? = null
    override fun saveTokens(accessToken: String, refreshToken: String?, expiresInSec: Long?, userJson: String?) {}
    override fun saveUserJson(userJson: String) {}
    override fun clear() {}
}
