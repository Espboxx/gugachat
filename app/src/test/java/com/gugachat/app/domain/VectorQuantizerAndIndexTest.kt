package com.gugachat.app.domain

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.chat.VectorMemoryIndex
import com.gugachat.app.data.local.MemoryEmbeddingDao
import com.gugachat.app.data.local.MemoryEmbeddingEntity
import com.gugachat.app.data.local.MemoryEmbeddingHashRow
import com.gugachat.app.domain.chat.EmbeddingEngine
import com.gugachat.app.domain.chat.LruQueryEmbeddingCache
import com.gugachat.app.domain.chat.REMOTE_EMBEDDING_PRESETS
import com.gugachat.app.domain.chat.REMOTE_RERANK_PRESETS
import com.gugachat.app.domain.chat.RemoteEmbeddingConfig
import com.gugachat.app.domain.chat.RemoteRerankConfig
import com.gugachat.app.domain.chat.RerankEngine
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.RetrievalSettings
import com.gugachat.app.domain.chat.VectorIndexProgress
import com.gugachat.app.domain.chat.VectorIndexTarget
import com.gugachat.app.domain.chat.VectorQuantizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sqrt

class VectorQuantizerAndIndexTest {

    @Test
    fun quantizer_l2NormalizeAndInt8DotProductPreserveCosineWithinTolerance() {
        val rng = Random(20261127L)
        val dim = 512
        val rowCount = 32
        val flatMatrix = ByteArray(rowCount * dim)
        val scales = FloatArray(rowCount)
        val exactUnits = ArrayList<FloatArray>(rowCount)

        for (r in 0 until rowCount) {
            val raw = FloatArray(dim) { (rng.nextGaussian() * 3.5).toFloat() }
            val unit = VectorQuantizer.l2Normalize(raw)
            val norm = sqrt(unit.sumOf { (it * it).toDouble() })
            assertEquals(1.0, norm, 1e-5)

            val (blob, scale) = VectorQuantizer.quantizeInt8(unit)
            assertEquals(dim, blob.size)
            System.arraycopy(blob, 0, flatMatrix, r * dim, dim)
            scales[r] = scale
            exactUnits.add(unit)
        }

        val queryUnit = VectorQuantizer.l2Normalize(FloatArray(dim) { (rng.nextGaussian() * 2.0).toFloat() })
        var maxErr = 0.0
        for (r in 0 until rowCount) {
            var exactDot = 0.0
            for (i in 0 until dim) {
                exactDot += queryUnit[i].toDouble() * exactUnits[r][i].toDouble()
            }
            val qDot = VectorQuantizer.dotProductInt8(
                queryUnit = queryUnit,
                flatMatrix = flatMatrix,
                rowOffset = r * dim,
                dim = dim,
                scale = scales[r],
            ).toDouble()
            val err = abs(exactDot - qDot)
            if (err > maxErr) maxErr = err
            assertTrue("Expected INT8 error < 0.01 at row $r, got $err", err < 0.01)
        }

        // Self dot product must be ~1.0
        val selfDot = VectorQuantizer.dotProductInt8(
            queryUnit = exactUnits[0],
            flatMatrix = flatMatrix,
            rowOffset = 0,
            dim = dim,
            scale = scales[0],
        )
        assertEquals(1.0f, selfDot, 0.01f)
    }

    @Test
    fun quantizer_handlesEdgeCasesAndSha256() {
        val zero = VectorQuantizer.l2Normalize(floatArrayOf(0f, 0f, 0f))
        assertEquals(3, zero.size)
        assertTrue(zero.all { it == 0f })

        val (zeroBlob, zeroScale) = VectorQuantizer.quantizeInt8(zero)
        assertEquals(0f, zeroScale, 0f)
        assertTrue(zeroBlob.all { it == 0.toByte() })

        val nanSafe = VectorQuantizer.l2Normalize(floatArrayOf(Float.NaN, 3f, 4f))
        assertEquals(0f, nanSafe[0], 1e-6f)
        assertEquals(0.6f, nanSafe[1], 1e-5f)
        assertEquals(0.8f, nanSafe[2], 1e-5f)

        val hash1 = VectorQuantizer.sha256Hex("林晚棠在青岚剑宗遗迹中发现残碑")
        val hash2 = VectorQuantizer.sha256Hex("林晚棠在青岚剑宗遗迹中发现残碑")
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertTrue(hash1.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun lruQueryCache_evictsLeastRecentlyUsedAndIsolatesProfiles() {
        val cache = LruQueryEmbeddingCache(maxSize = 2)
        cache.put("p1", "q1", floatArrayOf(1f, 0f))
        cache.put("p1", "q2", floatArrayOf(0f, 1f))
        // Touch q1 so q2 becomes LRU
        assertNotNull(cache.get("p1", "q1"))
        cache.put("p2", "q3", floatArrayOf(0.5f, 0.5f))

        assertEquals(2, cache.size())
        assertNotNull(cache.get("p1", "q1"))
        assertNull(cache.get("p1", "q2"))
        assertNotNull(cache.get("p2", "q3"))

        cache.invalidateProfile("p1")
        assertNull(cache.get("p1", "q1"))
        assertNotNull(cache.get("p2", "q3"))
    }

    @Test
    fun vectorMemoryIndex_ensureIndexedDiffsHashesRetries429AndPrunesStaleProfiles() = runBlocking {
        val dao = FakeMemoryEmbeddingDao()
        // Pre-seed stale profile row
        dao.upsertAll(
            listOf(
                MemoryEmbeddingEntity(
                    ownerType = "lore",
                    ownerId = "old-1",
                    conversationId = "conv-1",
                    profileKey = "remote|old|model|4",
                    contentHash = "stale",
                    dim = 4,
                    quantType = VectorQuantizer.QUANT_INT8_UNIT,
                    scale = 1f / 127f,
                    vectorBlob = byteArrayOf(127, 0, 0, 0),
                    updatedAt = 1L,
                ),
            ),
        )

        val backoffDelays = mutableListOf<Long>()
        val embedBatches = AtomicInteger(0)
        var failFirstBatchWith429 = true

        val engine = object : EmbeddingEngine {
            override val profileKey: String = "remote|sf|BAAI/bge-m3|4"
            override val minCosineFloor: Double = 0.50
            override suspend fun isAvailable(): Boolean = true
            override suspend fun embed(texts: List<String>, isQuery: Boolean): List<FloatArray> {
                if (failFirstBatchWith429) {
                    failFirstBatchWith429 = false
                    throw ApiException(429, "RATE_LIMIT", "Too Many Requests")
                }
                embedBatches.incrementAndGet()
                return texts.map { deterministicVec4(it) }
            }
        }

        val index = VectorMemoryIndex(
            dao = dao,
            clockMs = { 1_700_000_000_000L },
            delayFn = { ms -> backoffDelays.add(ms) },
        )

        val targets = listOf(
            VectorIndexTarget("lore", "lore-1", "conv-1", "暗器防身习惯：袖中银针"),
            VectorIndexTarget("ledger", "led-1", "conv-1", "青岚剑宗的千年约定"),
            VectorIndexTarget("user_memory", "um-1", "", "用户偏好克制冷静的古风叙事"),
        )

        val progressLog = mutableListOf<VectorIndexProgress>()
        val firstRun = index.ensureIndexed(targets, engine, batchSize = 2) { progressLog.add(it) }

        assertFalse(firstRun.running)
        assertNull(firstRun.lastError)
        assertEquals(3, firstRun.indexed)
        assertEquals(3, firstRun.total)
        assertEquals(listOf(500L), backoffDelays)
        assertEquals(2, embedBatches.get()) // 2 + 1 batches
        assertEquals(3, dao.countByProfile(engine.profileKey))
        // Stale profile pruned
        assertEquals(0, dao.countByProfile("remote|old|model|4"))

        // Second run with identical text -> 0 new embeds
        val callsBefore = embedBatches.get()
        val secondRun = index.ensureIndexed(targets, engine, batchSize = 2)
        assertEquals(callsBefore, embedBatches.get())
        assertEquals(3, secondRun.indexed)

        // Third run with 1 edited item -> only 1 item re-embedded
        val editedTargets = listOf(
            targets[0].copy(canonicalText = "暗器防身习惯：袖中银针与毒镖"),
            targets[1],
            targets[2],
        )
        val thirdRun = index.ensureIndexed(editedTargets, engine, batchSize = 2)
        assertEquals(callsBefore + 1, embedBatches.get())
        assertEquals(3, thirdRun.indexed)
    }

    @Test
    fun vectorMemoryIndex_flatScanUsesCacheLruGlobalFallbackAndGracefulTimeout() = runBlocking {
        val dao = FakeMemoryEmbeddingDao()
        val queryCalls = AtomicInteger(0)

        val vectorsByText = mapOf(
            "暗器防身" to floatArrayOf(1f, 0f, 0f, 0f),
            "厨艺爱好" to floatArrayOf(0f, 1f, 0f, 0f),
            "全局古风偏好" to floatArrayOf(0.8f, 0.6f, 0f, 0f),
            "袖口藏银针" to floatArrayOf(0.95f, 0.31225f, 0f, 0f),
        )

        val engine = object : EmbeddingEngine {
            override val profileKey: String = "local|gte-small-zh-int8|4"
            override val minCosineFloor: Double = 0.55
            override suspend fun isAvailable(): Boolean = true
            override suspend fun embed(texts: List<String>, isQuery: Boolean): List<FloatArray> {
                if (isQuery) queryCalls.incrementAndGet()
                return texts.map { vectorsByText[it] ?: floatArrayOf(0f, 0f, 1f, 0f) }
            }
        }

        val index = VectorMemoryIndex(dao = dao)
        index.ensureIndexed(
            targets = listOf(
                VectorIndexTarget("lore", "m-needle", "conv-1", "暗器防身"),
                VectorIndexTarget("lore", "m-cook", "conv-1", "厨艺爱好"),
                VectorIndexTarget("user_memory", "m-global", "", "全局古风偏好"),
            ),
            engine = engine,
        )

        val candidates = setOf("m-needle", "m-cook", "m-global")
        val firstScores = index.scoreCandidates(
            conversationId = "conv-1",
            queryText = "袖口藏银针",
            candidateIds = candidates,
            engine = engine,
        )
        assertEquals(1, queryCalls.get())
        assertEquals(1, dao.convFetchCount.get())
        assertEquals(1, dao.globalFetchCount.get())

        // m-needle (~0.95) and m-global (~0.947) pass floor 0.55; m-cook (~0.31) filtered out
        assertTrue(firstScores.containsKey("m-needle"))
        assertTrue(firstScores.containsKey("m-global"))
        assertFalse(firstScores.containsKey("m-cook"))
        assertEquals(0.95, firstScores["m-needle"]!!, 0.02)

        // Repeat same query -> hits BOTH LRU query cache AND flat slice cache (0 extra embed / 0 extra DAO reads!)
        val secondScores = index.scoreCandidates(
            conversationId = "conv-1",
            queryText = "袖口藏银针",
            candidateIds = candidates,
            engine = engine,
        )
        assertEquals(1, queryCalls.get())
        assertEquals(1, dao.convFetchCount.get())
        assertEquals(1, dao.globalFetchCount.get())
        assertEquals(firstScores, secondScores)

        // Lower minCosineOverride allows m-cook (~0.31)
        val lowered = index.scoreCandidates(
            conversationId = "conv-1",
            queryText = "袖口藏银针",
            candidateIds = candidates,
            engine = engine,
            minCosineOverride = 0.20,
        )
        assertTrue(lowered.containsKey("m-cook"))

        // Slow engine times out -> degrades safely to emptyMap()
        val slowEngine = object : EmbeddingEngine {
            override val profileKey: String = "remote|slow|m|4"
            override val minCosineFloor: Double = 0.5
            override suspend fun isAvailable(): Boolean = true
            override suspend fun embed(texts: List<String>, isQuery: Boolean): List<FloatArray> {
                delay(500L)
                return listOf(floatArrayOf(1f, 0f, 0f, 0f))
            }
        }
        val timedOut = index.scoreCandidates(
            conversationId = "conv-1",
            queryText = "超时查询",
            candidateIds = candidates,
            engine = slowEngine,
            timeoutMs = 80L,
        )
        assertTrue(timedOut.isEmpty())
    }

    @Test
    fun vectorMemoryIndex_rerankIfEnabledMapsIdsCapsAndDegradesOnTimeout() = runBlocking {
        val index = VectorMemoryIndex(dao = FakeMemoryEmbeddingDao())
        val seenDocCount = AtomicInteger(0)

        val reranker = object : RerankEngine {
            override suspend fun rerank(query: String, documents: List<String>, topN: Int): Map<Int, Double> {
                seenDocCount.set(documents.size)
                return mapOf(1 to 0.92, 0 to 0.41)
            }
        }

        val candidates = (0 until 20).map { "id-$it" to "document body $it" }
        val scores = index.rerankIfEnabled(
            query = "查找关键伏笔",
            candidates = candidates,
            rerankEngine = reranker,
            maxCandidates = 12,
        )
        assertEquals(12, seenDocCount.get())
        assertEquals(0.92, scores["id-1"]!!, 1e-6)
        assertEquals(0.41, scores["id-0"]!!, 1e-6)

        // Null engine -> emptyMap
        assertTrue(index.rerankIfEnabled("q", candidates, null).isEmpty())

        // Timeout engine -> emptyMap
        val slowReranker = object : RerankEngine {
            override suspend fun rerank(query: String, documents: List<String>, topN: Int): Map<Int, Double> {
                delay(400L)
                return mapOf(0 to 0.9)
            }
        }
        assertTrue(index.rerankIfEnabled("q", candidates, slowReranker, timeoutMs = 60L).isEmpty())
    }

    @Test
    fun retrievalSettingsSerializationAndPresetCatalogsAreComplete() {
        val settings = RetrievalSettings(
            mode = RetrievalMode.REMOTE_API,
            localModelId = "gte-small-zh-int8",
            remoteEmbedding = RemoteEmbeddingConfig(
                providerId = "sf-1",
                protocol = ApiProtocol.OPENAI_CHAT,
                modelId = "BAAI/bge-m3",
                dimensions = 1024,
            ),
            remoteRerank = RemoteRerankConfig(
                enabled = true,
                providerId = "sf-1",
                modelId = "BAAI/bge-reranker-v2-m3",
            ),
            minCosineOverride = 0.52,
        )
        val encoded = AppJson.encodeToString(RetrievalSettings.serializer(), settings)
        val decoded = AppJson.decodeFromString(RetrievalSettings.serializer(), encoded)
        assertEquals(settings, decoded)

        val embModels = REMOTE_EMBEDDING_PRESETS.map { it.modelId }.toSet()
        assertTrue("BAAI/bge-m3" in embModels)
        assertTrue("Qwen/Qwen3-Embedding-0.6B" in embModels)
        assertTrue("gemini-embedding-001" in embModels)
        assertTrue("text-embedding-v4" in embModels)
        assertTrue("embedding-3" in embModels)
        assertTrue("text-embedding-3-small" in embModels)
        assertTrue("jina-embeddings-v3" in embModels)
        assertTrue("bge-m3" in embModels)
        assertTrue("qwen3-embedding" in embModels)

        val rerankModels = REMOTE_RERANK_PRESETS.map { it.modelId }.toSet()
        assertTrue("BAAI/bge-reranker-v2-m3" in rerankModels)
        assertTrue("Qwen/Qwen3-Reranker-4B" in rerankModels)
        assertTrue("gte-rerank-v2" in rerankModels)
        assertTrue("jina-reranker-v2-base-multilingual" in rerankModels)

        val longQuery = "开头草案内容 " + "剧情正文".repeat(300) + " 结尾核心问题"
        val truncated = VectorMemoryIndex.truncateQueryForEmbedding(longQuery, maxChars = 500)
        assertTrue(truncated.length <= 500)
        assertTrue(truncated.startsWith("开头草案内容"))
        assertTrue(truncated.endsWith("结尾核心问题"))
    }

    private fun deterministicVec4(text: String): FloatArray {
        val h = text.hashCode()
        return floatArrayOf(
            ((h and 0xFF) / 255f) + 0.1f,
            (((h ushr 8) and 0xFF) / 255f) + 0.1f,
            (((h ushr 16) and 0xFF) / 255f) + 0.1f,
            (((h ushr 24) and 0xFF) / 255f) + 0.1f,
        )
    }

    private class FakeMemoryEmbeddingDao : MemoryEmbeddingDao {
        private val rows = LinkedHashMap<Triple<String, String, String>, MemoryEmbeddingEntity>()
        val convFetchCount = AtomicInteger(0)
        val globalFetchCount = AtomicInteger(0)

        override suspend fun getByConversationAndProfile(
            conversationId: String,
            profileKey: String,
        ): List<MemoryEmbeddingEntity> {
            convFetchCount.incrementAndGet()
            return rows.values.filter { it.conversationId == conversationId && it.profileKey == profileKey }
        }

        override suspend fun getGlobalByProfile(profileKey: String): List<MemoryEmbeddingEntity> {
            globalFetchCount.incrementAndGet()
            return rows.values.filter { it.conversationId == "" && it.profileKey == profileKey }
        }

        override suspend fun getHashesForProfile(
            conversationId: String,
            profileKey: String,
        ): List<MemoryEmbeddingHashRow> = rows.values
            .filter { it.conversationId == conversationId && it.profileKey == profileKey }
            .map { MemoryEmbeddingHashRow(it.ownerType, it.ownerId, it.contentHash) }

        override suspend fun upsertAll(items: List<MemoryEmbeddingEntity>) {
            for (item in items) {
                rows[Triple(item.ownerType, item.ownerId, item.profileKey)] = item
            }
        }

        override suspend fun deleteByOwner(ownerType: String, ownerId: String) {
            rows.keys.removeIf { it.first == ownerType && it.second == ownerId }
        }

        override suspend fun deleteStaleProfiles(activeProfileKey: String) {
            rows.keys.removeIf { it.third != activeProfileKey }
        }

        override suspend fun countByProfile(profileKey: String): Int =
            rows.values.count { it.profileKey == profileKey }

        override suspend fun deleteAll() {
            rows.clear()
        }
    }
}
