package com.gugachat.app.data

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.core.storage.TokenSnapshot
import com.gugachat.app.core.storage.TokenStore
import com.gugachat.app.data.api.ApiClient
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.ChatTokenUsageDto
import com.gugachat.app.data.api.DirectApiClient
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.DiscoveredModelKind
import com.gugachat.app.data.chat.RetrievalCoordinator
import com.gugachat.app.data.chat.VectorMemoryIndex
import com.gugachat.app.data.local.LocalEmbeddingModelManager
import com.gugachat.app.data.local.LocalModelSpec
import com.gugachat.app.data.local.LocalOnnxEmbeddingEngine
import com.gugachat.app.data.local.LocalPoolingMode
import com.gugachat.app.data.local.MemoryEmbeddingDao
import com.gugachat.app.data.local.MemoryEmbeddingEntity
import com.gugachat.app.data.local.MemoryEmbeddingHashRow
import com.gugachat.app.data.local.OnnxEmbeddingSession
import com.gugachat.app.data.local.SessionRuntimeFactory
import com.gugachat.app.domain.chat.EncodedChunk
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RemoteEmbeddingConfig
import com.gugachat.app.domain.chat.RemoteRerankConfig
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.RetrievalSettings
import com.gugachat.app.domain.chat.TokenEstimator
import com.gugachat.app.domain.chat.UserMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class RetrievalCoordinatorAndSettingsTest {

    private lateinit var tempDir: File
    private lateinit var server: MockWebServer
    private lateinit var directClient: DirectApiClient
    private lateinit var fakeDao: FakeMemoryEmbeddingDao

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("gugachat_retrieval_coord_test").toFile()
        server = MockWebServer()
        server.start()
        val ok = OkHttpClient()
        val helper = ApiClient(
            tokenStore = DummyTokenStore,
            serverUrlProvider = { "" },
            languageTagProvider = { "zh" },
        )
        directClient = DirectApiClient(ok, ok, helper::parseHttpError)
        fakeDao = FakeMemoryEmbeddingDao()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun settingsStoreHelpers_normalizeBudgetAndClampCalibrationMap() {
        assertEquals("2K", SettingsStore.normalizeMemoryBudget("low"))
        assertEquals("2K", SettingsStore.normalizeMemoryBudget("2k"))
        assertEquals("2K", SettingsStore.normalizeMemoryBudget(" 2K "))
        assertEquals("4K", SettingsStore.normalizeMemoryBudget("standard"))
        assertEquals("4K", SettingsStore.normalizeMemoryBudget("4K"))
        assertEquals("4K", SettingsStore.normalizeMemoryBudget(null))
        assertEquals("8K", SettingsStore.normalizeMemoryBudget("high"))
        assertEquals("8K", SettingsStore.normalizeMemoryBudget("8k"))

        // Clamp bounds and ignore invalid inputs
        var map = emptyMap<String, Double>()
        map = SettingsStore.updateTokenCalibrationMap(map, "p1|deepseek", 0.20)
        assertEquals(0.60, map["p1|deepseek"]!!, 1e-6)
        map = SettingsStore.updateTokenCalibrationMap(map, "p1|claude", 2.90)
        assertEquals(1.60, map["p1|claude"]!!, 1e-6)
        map = SettingsStore.updateTokenCalibrationMap(map, "   ", 1.10)
        map = SettingsStore.updateTokenCalibrationMap(map, "p1|nan", Double.NaN)
        assertEquals(2, map.size)

        // Evict oldest when exceeding max 32 entries while refreshing touched keys
        var full = emptyMap<String, Double>()
        for (i in 1..32) {
            full = SettingsStore.updateTokenCalibrationMap(full, "k$i", 1.0)
        }
        // Touch k1 so k2 becomes oldest
        full = SettingsStore.updateTokenCalibrationMap(full, "k1", 1.15)
        full = SettingsStore.updateTokenCalibrationMap(full, "k33", 1.25)
        assertEquals(32, full.size)
        assertTrue("k1" in full)
        assertFalse("k2" in full)
        assertTrue("k33" in full)
        assertEquals(1.15, full["k1"]!!, 1e-6)

        // JSON decode fallbacks
        assertEquals(RetrievalSettings(), SettingsStore.decodeRetrievalSettings("not-json"))
        assertTrue(SettingsStore.decodeTokenCalibrationMap("not-json").isEmpty())
        val encoded = AppJson.encodeToString(
            RetrievalSettings.serializer(),
            RetrievalSettings(mode = RetrievalMode.REMOTE_API, localModelId = "bge-small-zh-v1.5-int8"),
        )
        val decoded = SettingsStore.decodeRetrievalSettings(encoded)
        assertEquals(RetrievalMode.REMOTE_API, decoded.mode)
        assertEquals("bge-small-zh-v1.5-int8", decoded.localModelId)
    }

    @Test
    fun lexicalOnlyMode_neverInvokesEngineAndReturnsEmptyScores() = runBlocking {
        val (manager, onnxEngine) = createLocalEngineFixture(installModel = false)
        val coordinator = RetrievalCoordinator(
            retrievalSettingsProvider = { RetrievalSettings(mode = RetrievalMode.LEXICAL_ONLY) },
            providerLookup = { null },
            directApiClient = directClient,
            localModelManager = manager,
            localOnnxEngine = onnxEngine,
            vectorMemoryIndex = VectorMemoryIndex(fakeDao),
        )

        assertNull(coordinator.resolveActiveEmbeddingEngine())
        assertNull(coordinator.resolveActiveRerankEngine())
        assertEquals(RetrievalCoordinator.MODE_LEXICAL, coordinator.activeModeLabel())

        val (scores, floor) = coordinator.prepareSendSemanticScores(
            conversationId = "c1",
            queryText = "青岚剑宗遗迹",
            candidateIds = setOf("lore-1"),
        )
        assertTrue(scores.isEmpty())
        assertEquals(0.50, floor, 1e-6)
        assertEquals(0, coordinator.countIndexedVectors())
    }

    @Test
    fun localSmallModelMode_indexesScoresUnloadsAndClearsCleanly() = runBlocking {
        val (manager, onnxEngine) = createLocalEngineFixture(installModel = true)
        var settings = RetrievalSettings(
            mode = RetrievalMode.LOCAL_SMALL_MODEL,
            localModelId = TEST_LOCAL_MODEL_ID,
            minCosineOverride = 0.40,
        )
        val coordinator = RetrievalCoordinator(
            retrievalSettingsProvider = { settings },
            providerLookup = { null },
            directApiClient = directClient,
            localModelManager = manager,
            localOnnxEngine = onnxEngine,
            vectorMemoryIndex = VectorMemoryIndex(fakeDao),
        )

        assertNotNull(coordinator.resolveActiveEmbeddingEngine())
        assertEquals(RetrievalCoordinator.MODE_LOCAL_VECTOR, coordinator.activeModeLabel())

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val job = coordinator.scheduleConversationIndexing(
                scope = scope,
                conversationId = "conv-1",
                lore = listOf(
                    NovelLoreEntry(
                        id = "lore-sword",
                        conversationId = "conv-1",
                        title = "青岚剑宗",
                        content = "青岚剑宗遗迹与残碑秘境",
                        enabled = true,
                    ),
                ),
                ledger = listOf(
                    MemoryLedgerItem(
                        id = "led-1",
                        conversationId = "conv-1",
                        title = "玉佩之约",
                        content = "与林晚棠约定在雨夜归还玉佩",
                        enabled = true,
                    ),
                ),
                episodes = listOf(
                    MemoryEpisode(
                        id = "ep-1",
                        conversationId = "conv-1",
                        title = "初入宗门",
                        summary = "夜探遗迹并取得剑诀",
                        fromSeq = 1,
                        toSeq = 6,
                    ),
                ),
                userMemories = listOf(
                    UserMemory(
                        id = "um-1",
                        category = MemoryCategory.preference,
                        content = "喜欢古风武侠与细腻人物刻画",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            )
            assertNotNull(job)
            job!!.join()

            assertEquals(4, coordinator.countIndexedVectors())
            assertTrue(coordinator.isLocalSessionLoaded())

            val (scores, floor) = coordinator.prepareSendSemanticScores(
                conversationId = "conv-1",
                queryText = "剑宗 残碑",
                candidateIds = setOf("lore-sword", "led-1", "ep-1", "um-1"),
            )
            assertEquals(0.40, floor, 1e-6)
            assertTrue("Expected lore-sword scored, got $scores", scores.containsKey("lore-sword"))
            assertTrue("Expected global user memory scored, got $scores", scores.containsKey("um-1"))

            coordinator.unloadLocalModelNow()
            assertFalse(coordinator.isLocalSessionLoaded())

            coordinator.clearAllVectors()
            assertEquals(0, coordinator.countIndexedVectors())
        } finally {
            scope.cancel()
        }

        // Switching to missing model ID gracefully degrades to lexical
        settings = settings.copy(localModelId = "non-existent-model")
        assertNull(coordinator.resolveActiveEmbeddingEngine())
        assertEquals(RetrievalCoordinator.MODE_LEXICAL, coordinator.activeModeLabel())
    }

    @Test
    fun remoteApiMode_supportsTestModelsIndexScoreRerankAndGracefulFallback() = runBlocking {
        val (manager, onnxEngine) = createLocalEngineFixture(installModel = false)
        val baseUrl = server.url("/v1").toString().trimEnd('/')
        val provider = ApiProviderConfig(
            id = "prov-mock",
            numericId = 1L,
            name = "Mock Cloud",
            protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = baseUrl,
            apiKey = "sk-test",
        )
        var settings = RetrievalSettings(
            mode = RetrievalMode.REMOTE_API,
            remoteEmbedding = RemoteEmbeddingConfig(
                providerId = "prov-mock",
                modelId = "BAAI/bge-m3",
                dimensions = 4,
            ),
            remoteRerank = RemoteRerankConfig(
                enabled = false,
                providerId = "prov-mock",
                modelId = "BAAI/bge-reranker-v2-m3",
            ),
            minCosineOverride = 0.50,
        )
        val coordinator = RetrievalCoordinator(
            retrievalSettingsProvider = { settings },
            providerLookup = { id -> if (id == "prov-mock") provider else null },
            directApiClient = directClient,
            localModelManager = manager,
            localOnnxEngine = onnxEngine,
            vectorMemoryIndex = VectorMemoryIndex(fakeDao),
        )

        assertEquals(RetrievalCoordinator.MODE_REMOTE_VECTOR, coordinator.activeModeLabel())

        // 1. Test remote embedding connection
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"index":0,"embedding":[1.0,0.0,0.0,0.0]}]}"""),
        )
        val testRes = coordinator.testRemoteEmbedding(settings.remoteEmbedding)
        assertTrue(testRes.isSuccess)
        assertEquals(4, testRes.getOrThrow().dimension)

        // 2. Discover embedding + rerank models
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"data":[{"id":"BAAI/bge-m3"},{"id":"BAAI/bge-reranker-v2-m3"},{"id":"gpt-4o"}]}""",
                ),
        )
        val modelsRes = coordinator.fetchRemoteRetrievalModels(
            providerId = "prov-mock",
            customBaseUrl = "",
            customApiKey = "",
            protocol = ApiProtocol.OPENAI_CHAT,
        )
        assertTrue(modelsRes.isSuccess)
        val discovered = modelsRes.getOrThrow()
        assertEquals(2, discovered.size)
        assertTrue(discovered.any { it.id == "BAAI/bge-m3" && it.kind == DiscoveredModelKind.EMBEDDING })
        assertTrue(discovered.any { it.id == "BAAI/bge-reranker-v2-m3" && it.kind == DiscoveredModelKind.RERANK })

        // 3. Index + query score + rerank
        settings = settings.copy(remoteRerank = settings.remoteRerank.copy(enabled = true))
        assertEquals(RetrievalCoordinator.MODE_REMOTE_VECTOR_RERANK, coordinator.activeModeLabel())

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"index":0,"embedding":[1.0,0.0,0.0,0.0]},{"index":1,"embedding":[0.0,1.0,0.0,0.0]}]}"""),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val job = coordinator.scheduleConversationIndexing(
                scope = scope,
                conversationId = "conv-r",
                lore = listOf(
                    NovelLoreEntry(id = "l1", conversationId = "conv-r", title = "A", content = "Sword"),
                    NovelLoreEntry(id = "l2", conversationId = "conv-r", title = "B", content = "Tea"),
                ),
                ledger = emptyList(),
                episodes = emptyList(),
                userMemories = emptyList(),
            )
            job!!.join()
            assertEquals(2, coordinator.countIndexedVectors())

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"data":[{"index":0,"embedding":[0.98,0.10,0.0,0.0]}]}"""),
            )
            val (scores, _) = coordinator.prepareSendSemanticScores(
                conversationId = "conv-r",
                queryText = "sword query",
                candidateIds = setOf("l1", "l2"),
            )
            assertTrue(scores["l1"]!! > 0.90)
            assertFalse("l2" in scores)

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"results":[{"index":0,"relevance_score":0.91},{"index":1,"relevance_score":0.08}]}"""),
            )
            val reranked = coordinator.rerankCandidatesIfEnabled(
                query = "sword query",
                candidates = listOf("novel_lore:l1" to "Sword", "novel_lore:l2" to "Tea"),
            )
            assertEquals(0.91, reranked["novel_lore:l1"]!!, 1e-6)
            assertEquals(0.08, reranked["novel_lore:l2"]!!, 1e-6)

            // 4. Server 500 error & timeout degrade cleanly to emptyMap()
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"down"}}"""))
            val (fallbackScores, _) = coordinator.prepareSendSemanticScores(
                conversationId = "conv-r",
                queryText = "fresh uncached query that errors",
                candidateIds = setOf("l1"),
            )
            assertTrue(fallbackScores.isEmpty())

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(600, TimeUnit.MILLISECONDS)
                    .setBody("""{"results":[{"index":0,"relevance_score":0.8}]}"""),
            )
            val timedOutRerank = coordinator.rerankCandidatesIfEnabled(
                query = "slow rerank",
                candidates = listOf("novel_lore:l1" to "Sword"),
                timeoutMs = 80L,
            )
            assertTrue(timedOutRerank.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun anthropicCacheUsage_preservesAccurateEmaCalibration() {
        // Anthropic reports input_tokens + cache_creation in prompt_tokens, and cache_read separately
        val usage = ChatTokenUsageDto(
            prompt_tokens = 1200,
            completion_tokens = 300,
            total_tokens = 11500,
            cache_read_input_tokens = 10000,
        )
        val totalInput = DirectProtocolCodec.totalInputTokens(ApiProtocol.ANTHROPIC, usage)
        assertEquals(11200, totalInput)

        // Estimated raw tokens = 10000 -> true ratio = 1.12 -> EMA from 1.0 should move upward to ~1.042, NOT collapse to 0.60!
        val updated = TokenEstimator.updateCalibrationEma(
            currentFactor = 1.0,
            actualPromptTokens = totalInput!!,
            estimatedRawTokens = 10000,
            alpha = 0.35,
        )
        assertEquals(1.042, updated, 1e-3)
    }

    @Test
    fun scheduleAttachmentIndexing_scoresWithFileScopeIdsAndCleansUpOnDelete() = runBlocking {
        val (manager, engine) = createLocalEngineFixture(installModel = true)
        val coordinator = RetrievalCoordinator(
            retrievalSettingsProvider = {
                RetrievalSettings(
                    mode = RetrievalMode.LOCAL_SMALL_MODEL,
                    localModelId = TEST_LOCAL_MODEL_ID,
                )
            },
            providerLookup = { null },
            directApiClient = directClient,
            localModelManager = manager,
            localOnnxEngine = engine,
            vectorMemoryIndex = VectorMemoryIndex(fakeDao),
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val docText = "# 剑宗残碑\n古风玉佩藏于后山石窟，剑宗残碑记载了失传心法。"
            val job = coordinator.scheduleAttachmentIndexing(
                scope = scope,
                attachmentId = "att-sword",
                fileName = "sword_lore.md",
                extractedText = docText,
            )
            assertNotNull(job)
            job!!.join()
            assertTrue("Expected file chunk vector to be indexed", coordinator.countIndexedVectors() >= 1)

            val (scores, _) = coordinator.prepareSendSemanticScores(
                conversationId = "conv-branch-2",
                queryText = "剑宗残碑与玉佩",
                candidateIds = setOf("fc:att-sword:0"),
                fileScopeIds = listOf("att-sword"),
            )
            assertTrue("Expected file chunk semantic score via file:att-sword scope", (scores["fc:att-sword:0"] ?: 0.0) > 0.5)

            coordinator.deleteFileVectors("att-sword")
            assertEquals(0, coordinator.countIndexedVectors())
        } finally {
            scope.cancel()
        }
    }

    private fun createLocalEngineFixture(installModel: Boolean): Pair<LocalEmbeddingModelManager, LocalOnnxEmbeddingEngine> {
        val spec = LocalModelSpec(
            id = TEST_LOCAL_MODEL_ID,
            displayName = "Test Local Mini",
            descriptionZh = "测试",
            descriptionEn = "Test",
            dimension = 4,
            maxTokens = 64,
            approxSizeMb = 1,
            pooling = LocalPoolingMode.CLS,
            queryPrefix = "",
            recommendedMinCosine = 0.50,
            modelMirrorUrls = emptyList(),
            vocabMirrorUrls = emptyList(),
        )
        val manager = LocalEmbeddingModelManager(
            modelsRootDir = File(tempDir, "models"),
            httpClient = OkHttpClient(),
            catalogSpecs = listOf(spec),
        )
        if (installModel) {
            val dir = manager.getModelDir(TEST_LOCAL_MODEL_ID)
            dir.mkdirs()
            manager.getModelFile(TEST_LOCAL_MODEL_ID).writeBytes(byteArrayOf(1, 2, 3, 4))
            manager.getVocabFile(TEST_LOCAL_MODEL_ID).writeText(
                listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "剑", "宗", "残", "碑", "玉", "佩", "古", "风").joinToString("\n"),
                Charsets.UTF_8,
            )
            File(dir, LocalEmbeddingModelManager.COMPLETE_MARKER_NAME).writeText("ok")
            manager.refreshStates()
        }

        val fakeFactory = SessionRuntimeFactory { _, _, _ ->
            object : OnnxEmbeddingSession {
                override fun embedChunk(chunk: EncodedChunk, pooling: LocalPoolingMode): FloatArray {
                    // Deterministic 4D embedding oriented strongly around first axis so all test docs score cleanly
                    val sum = chunk.inputIds.sum().toFloat()
                    return floatArrayOf(0.92f, 0.25f + (sum % 5) * 0.02f, 0.15f, 0.10f)
                }

                override fun close() {}
            }
        }
        val engine = LocalOnnxEmbeddingEngine(
            modelManager = manager,
            activeModelIdProvider = { TEST_LOCAL_MODEL_ID },
            idleUnloadTimeoutMs = 0L,
            intraOpThreads = 1,
            interOpThreads = 1,
            chunkStride = 16,
            maxDocumentChunks = 2,
            inferenceDispatcher = Dispatchers.Default,
            lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            runtimeFactory = fakeFactory,
        )
        return manager to engine
    }

    private class FakeMemoryEmbeddingDao : MemoryEmbeddingDao {
        private val lock = Any()
        private val rows = LinkedHashMap<Triple<String, String, String>, MemoryEmbeddingEntity>()

        override suspend fun getByConversationAndProfile(conversationId: String, profileKey: String): List<MemoryEmbeddingEntity> =
            synchronized(lock) {
                rows.values.filter { it.conversationId == conversationId && it.profileKey == profileKey }
            }

        override suspend fun getGlobalByProfile(profileKey: String): List<MemoryEmbeddingEntity> =
            synchronized(lock) {
                rows.values.filter { it.conversationId.isEmpty() && it.profileKey == profileKey }
            }

        override suspend fun getHashesForProfile(conversationId: String, profileKey: String): List<MemoryEmbeddingHashRow> =
            synchronized(lock) {
                rows.values
                    .filter { it.conversationId == conversationId && it.profileKey == profileKey }
                    .map { MemoryEmbeddingHashRow(it.ownerType, it.ownerId, it.contentHash) }
            }

        override suspend fun upsertAll(items: List<MemoryEmbeddingEntity>) {
            synchronized(lock) {
                for (item in items) {
                    rows[Triple(item.ownerType, item.ownerId, item.profileKey)] = item
                }
            }
        }

        override suspend fun deleteByOwner(ownerType: String, ownerId: String) {
            synchronized(lock) {
                val iter = rows.entries.iterator()
                while (iter.hasNext()) {
                    val k = iter.next().key
                    if (k.first == ownerType && k.second == ownerId) iter.remove()
                }
            }
        }

        override suspend fun deleteByConversationId(conversationId: String) {
            synchronized(lock) {
                val iter = rows.entries.iterator()
                while (iter.hasNext()) {
                    if (iter.next().value.conversationId == conversationId) iter.remove()
                }
            }
        }

        override suspend fun deleteStaleProfiles(activeProfileKey: String) {
            synchronized(lock) {
                val iter = rows.entries.iterator()
                while (iter.hasNext()) {
                    if (iter.next().key.third != activeProfileKey) iter.remove()
                }
            }
        }

        override suspend fun countByProfile(profileKey: String): Int =
            synchronized(lock) {
                rows.values.count { it.profileKey == profileKey }
            }

        override suspend fun deleteAll() {
            synchronized(lock) {
                rows.clear()
            }
        }
    }

    private object DummyTokenStore : TokenStore {
        override fun readSnapshot(): TokenSnapshot = TokenSnapshot(null, null, 0L, null)
        override fun getAccessToken(): String? = null
        override fun getRefreshToken(): String? = null
        override fun getTokenExpiresAtMs(): Long = 0L
        override fun getCachedUserJson(): String? = null
        override fun saveTokens(accessToken: String, refreshToken: String?, expiresInSec: Long?, userJson: String?) {}
        override fun saveUserJson(userJson: String) {}
        override fun clear() {}
    }

    companion object {
        private const val TEST_LOCAL_MODEL_ID = "test-mini-zh-int8"
    }
}
