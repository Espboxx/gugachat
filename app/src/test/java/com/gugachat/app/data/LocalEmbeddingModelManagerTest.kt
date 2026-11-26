package com.gugachat.app.data.local

import com.gugachat.app.domain.chat.EncodedChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sqrt

class LocalEmbeddingModelManagerTest {

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun sampleVocabText(): String =
        listOf(
            "[PAD]", "[UNK]", "[CLS]", "[SEP]",
            "为", "这", "个", "句", "子", "生", "成", "表", "示", "以", "用", "于", "检", "索", "相", "关", "文", "章", ":",
            "青", "云", "剑", "宗", "银", "针", "伏", "笔",
        ).joinToString("\n") + "\n"

    @Test
    fun defaultCatalog_containsVerifiedGteAndBgeModelsWithChinaFirstMirrors() {
        val catalog = LocalEmbeddingModelManager.DEFAULT_CATALOG
        assertTrue(catalog.size >= 2)

        val gte = catalog.firstOrNull { it.id == LocalEmbeddingModelManager.MODEL_ID_GTE_SMALL_ZH_INT8 }
        val bge = catalog.firstOrNull { it.id == LocalEmbeddingModelManager.MODEL_ID_BGE_SMALL_ZH_V15_INT8 }
        assertNotNull(gte)
        assertNotNull(bge)
        assertEquals(LocalEmbeddingModelManager.MODEL_ID_GTE_SMALL_ZH_INT8, LocalEmbeddingModelManager.DEFAULT_MODEL_ID)

        // GTE-Small-ZH checks
        assertEquals(512, gte!!.dimension)
        assertEquals(512, gte.maxTokens)
        assertEquals("", gte.queryPrefix)
        assertEquals(0.60, gte.recommendedMinCosine, 1e-6)
        assertTrue(gte.modelMirrorUrls.first().contains("hf-mirror.com"))
        assertTrue(gte.modelMirrorUrls.any { it.contains("huggingface.co") })
        assertTrue(gte.vocabMirrorUrls.first().contains("modelscope.cn"))
        assertEquals(64, gte.expectedModelSha256?.length)

        // BGE-Small-ZH-v1.5 checks
        assertEquals(512, bge!!.dimension)
        assertEquals(512, bge.maxTokens)
        assertEquals("为这个句子生成表示以用于检索相关文章：", bge.queryPrefix)
        assertEquals(0.58, bge.recommendedMinCosine, 1e-6)
        assertEquals(LocalPoolingMode.CLS, bge.pooling)
        assertTrue(bge.modelMirrorUrls.first().contains("modelscope.cn"))
        assertTrue(bge.modelMirrorUrls.any { it.contains("hf-mirror.com") })
        assertTrue(bge.modelMirrorUrls.any { it.contains("huggingface.co") })
        assertEquals(64, bge.expectedModelSha256?.length)
    }

    @Test
    fun downloadModel_autoFailsOverAcrossBrokenHtmlAndTamperedMirrors() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("local_embed_failover_").toFile()
        try {
            val vocabBytes = sampleVocabText().toByteArray(Charsets.UTF_8)
            val goodModelBytes = ByteArray(4096) { i -> ((i * 31) and 0xFF).toByte() }
            val tamperedModelBytes = ByteArray(4096) { 0x7F }
            val expectedSha = sha256Hex(goodModelBytes)

            val spec = LocalModelSpec(
                id = "test-model",
                displayName = "Test Model",
                descriptionZh = "测试模型",
                descriptionEn = "Test Model",
                dimension = 4,
                maxTokens = 512,
                approxSizeMb = 1,
                pooling = LocalPoolingMode.CLS,
                queryPrefix = "",
                recommendedMinCosine = 0.60,
                vocabMirrorUrls = listOf(
                    server.url("/vocab-bad").toString(),
                    server.url("/vocab-good").toString(),
                ),
                modelMirrorUrls = listOf(
                    server.url("/model-502").toString(),
                    server.url("/model-html").toString(),
                    server.url("/model-tampered").toString(),
                    server.url("/model-good").toString(),
                ),
                expectedModelSha256 = expectedSha,
            )

            // Vocab mirror 1 -> 500, mirror 2 -> 200 OK
            server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "text/plain; charset=utf-8")
                    .setBody(Buffer().write(vocabBytes)),
            )

            // Model mirror 1 -> 502, mirror 2 -> HTML block page, mirror 3 -> tampered bytes, mirror 4 -> valid ONNX
            server.enqueue(MockResponse().setResponseCode(502).setBody("Bad Gateway"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "text/html")
                    .setBody("<!DOCTYPE html><html><body>Mirror blocked</body></html>"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/octet-stream")
                    .setBody(Buffer().write(tamperedModelBytes)),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/octet-stream")
                    .setBody(Buffer().write(goodModelBytes)),
            )

            val manager = LocalEmbeddingModelManager(
                modelsRootDir = tempDir,
                httpClient = OkHttpClient(),
                catalogSpecs = listOf(spec),
            )
            assertFalse(manager.isModelInstalled("test-model"))
            assertEquals(LocalModelDownloadState.NotDownloaded, manager.states.value["test-model"])

            val res = manager.downloadModel("test-model")
            assertTrue("Expected download success, got $res", res.isSuccess)
            assertTrue(manager.isModelInstalled("test-model"))
            val expectedTotal = vocabBytes.size.toLong() + goodModelBytes.size.toLong()
            assertEquals(expectedTotal, manager.getInstalledSizeBytes("test-model"))
            assertEquals(LocalModelDownloadState.Ready(expectedTotal), manager.states.value["test-model"])
            assertArrayEquals(goodModelBytes, manager.getModelFile("test-model").readBytes())
            assertArrayEquals(vocabBytes, manager.getVocabFile("test-model").readBytes())

            // 1-tap delete cleans everything and resets StateFlow
            assertTrue(manager.deleteModel("test-model"))
            assertFalse(manager.isModelInstalled("test-model"))
            assertEquals(0L, manager.getInstalledSizeBytes("test-model"))
            assertEquals(LocalModelDownloadState.NotDownloaded, manager.states.value["test-model"])
        } finally {
            tempDir.deleteRecursively()
            server.shutdown()
        }
    }

    @Test
    fun downloadModel_resumesFromExistingPartFileViaHttpRange206AndHealsCorruptPart() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("local_embed_resume_").toFile()
        try {
            val vocabBytes = sampleVocabText().toByteArray(Charsets.UTF_8)
            val fullModelBytes = ByteArray(8192) { i -> ((i * 17 + 5) and 0xFF).toByte() }
            val expectedSha = sha256Hex(fullModelBytes)
            val splitOffset = 3000

            val spec = LocalModelSpec(
                id = "resume-model",
                displayName = "Resume Model",
                descriptionZh = "断点续传模型",
                descriptionEn = "Resumable Model",
                dimension = 4,
                maxTokens = 512,
                approxSizeMb = 1,
                pooling = LocalPoolingMode.MEAN,
                queryPrefix = "",
                recommendedMinCosine = 0.60,
                vocabMirrorUrls = listOf(server.url("/vocab.txt").toString()),
                modelMirrorUrls = listOf(server.url("/model.onnx").toString()),
                expectedModelSha256 = expectedSha,
            )

            val manager = LocalEmbeddingModelManager(
                modelsRootDir = tempDir,
                httpClient = OkHttpClient(),
                catalogSpecs = listOf(spec),
            )

            // Pre-seed completed vocab + partial first 3000 bytes of model.onnx.part
            val modelDir = manager.getModelDir("resume-model").apply { mkdirs() }
            File(modelDir, LocalEmbeddingModelManager.VOCAB_FILE_NAME).writeBytes(vocabBytes)
            val partFile = File(modelDir, "${LocalEmbeddingModelManager.MODEL_FILE_NAME}.part")
            partFile.writeBytes(fullModelBytes.copyOfRange(0, splitOffset))

            // Server replies 206 Partial Content for remaining bytes
            val remaining = fullModelBytes.copyOfRange(splitOffset, fullModelBytes.size)
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Type", "application/octet-stream")
                    .addHeader("Content-Range", "bytes $splitOffset-${fullModelBytes.size - 1}/${fullModelBytes.size}")
                    .setBody(Buffer().write(remaining)),
            )

            val res = manager.downloadModel("resume-model")
            assertTrue("Expected resumed download success, got $res", res.isSuccess)
            val recordedReq = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("bytes=$splitOffset-", recordedReq?.getHeader("Range"))
            assertArrayEquals(fullModelBytes, manager.getModelFile("resume-model").readBytes())
            assertFalse(partFile.exists())

            // Case 2: Delete & pre-seed a CORRUPTED `.part` file -> resume fails SHA-256 -> auto-restarts from byte 0
            manager.deleteModel("resume-model")
            modelDir.mkdirs()
            File(modelDir, LocalEmbeddingModelManager.VOCAB_FILE_NAME).writeBytes(vocabBytes)
            partFile.writeBytes(ByteArray(splitOffset) { 0x11 }) // corrupted prefix

            // First request attempts Range resume -> combined SHA fails -> manager deletes .part and retries full 200
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Type", "application/octet-stream")
                    .addHeader("Content-Range", "bytes $splitOffset-${fullModelBytes.size - 1}/${fullModelBytes.size}")
                    .setBody(Buffer().write(remaining)),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/octet-stream")
                    .setBody(Buffer().write(fullModelBytes)),
            )

            val healedRes = manager.downloadModel("resume-model")
            assertTrue("Expected self-healing download success, got $healedRes", healedRes.isSuccess)
            val req1 = server.takeRequest(2, TimeUnit.SECONDS)
            val req2 = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("bytes=$splitOffset-", req1?.getHeader("Range"))
            assertEquals(null, req2?.getHeader("Range"))
            assertArrayEquals(fullModelBytes, manager.getModelFile("resume-model").readBytes())
        } finally {
            tempDir.deleteRecursively()
            server.shutdown()
        }
    }

    @Test
    fun cancelDownload_stopsActiveTransferAndResetsStateToNotDownloaded() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("local_embed_cancel_").toFile()
        try {
            val vocabBytes = sampleVocabText().toByteArray(Charsets.UTF_8)
            val slowPayload = ByteArray(256 * 1024) { 0x22 }
            val spec = LocalModelSpec(
                id = "cancel-model",
                displayName = "Cancel Model",
                descriptionZh = "取消测试",
                descriptionEn = "Cancel Test",
                dimension = 4,
                maxTokens = 512,
                approxSizeMb = 1,
                pooling = LocalPoolingMode.CLS,
                queryPrefix = "",
                recommendedMinCosine = 0.60,
                vocabMirrorUrls = listOf(server.url("/vocab.txt").toString()),
                modelMirrorUrls = listOf(server.url("/slow.onnx").toString()),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(vocabBytes)))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/octet-stream")
                    .throttleBody(4096, 150, TimeUnit.MILLISECONDS)
                    .setBody(Buffer().write(slowPayload)),
            )

            val manager = LocalEmbeddingModelManager(
                modelsRootDir = tempDir,
                httpClient = OkHttpClient(),
                catalogSpecs = listOf(spec),
            )

            var caughtCancel = false
            val job = launch(Dispatchers.IO) {
                try {
                    manager.downloadModel("cancel-model")
                } catch (_: CancellationException) {
                    caughtCancel = true
                }
            }
            delay(200)
            manager.cancelDownload("cancel-model")
            job.join()

            assertTrue(caughtCancel)
            assertFalse(manager.isModelInstalled("cancel-model"))
            assertEquals(LocalModelDownloadState.NotDownloaded, manager.states.value["cancel-model"])
        } finally {
            tempDir.deleteRecursively()
            server.shutdown()
        }
    }

    @Test
    fun localOnnxEngine_poolingMathMultiChunkWeightingQueryPrefixAndIdleAutoUnload() = runBlocking {
        val tempDir = Files.createTempDirectory("local_onnx_engine_test_").toFile()
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // 1. Unit-test CLS & MEAN pooling + L2 norm directly
            val hidden = arrayOf(
                floatArrayOf(3f, 4f, 0f, 0f), // [CLS]
                floatArrayOf(0f, 0f, 3f, 4f), // token 1
                floatArrayOf(99f, 99f, 99f, 99f), // masked out padding token
            )
            val mask = longArrayOf(1L, 1L, 0L)

            val clsVec = LocalOnnxEmbeddingEngine.poolHiddenStates(hidden, mask, LocalPoolingMode.CLS)
            assertEquals(0.6f, clsVec[0], 1e-5f)
            assertEquals(0.8f, clsVec[1], 1e-5f)
            assertEquals(0f, clsVec[2], 1e-5f)

            val meanVec = LocalOnnxEmbeddingEngine.poolHiddenStates(hidden, mask, LocalPoolingMode.MEAN)
            // Mean of [3,4,0,0] and [0,0,3,4] is [1.5, 2.0, 1.5, 2.0], norm = sqrt(12.5) = 3.5355339
            val expectedNorm = sqrt(1.5 * 1.5 + 2.0 * 2.0 + 1.5 * 1.5 + 2.0 * 2.0).toFloat()
            assertEquals(1.5f / expectedNorm, meanVec[0], 1e-5f)
            assertEquals(2.0f / expectedNorm, meanVec[1], 1e-5f)
            assertEquals(1.5f / expectedNorm, meanVec[2], 1e-5f)
            assertEquals(2.0f / expectedNorm, meanVec[3], 1e-5f)

            // Verify zero-copy FloatBuffer pooling matches poolHiddenStates for 3D and 2D tensors
            val flat3d = FloatBuffer.wrap(hidden.flatMap { it.asIterable() }.toFloatArray())
            val bufMean = LocalOnnxEmbeddingEngine.poolFlatBuffer(
                buffer = flat3d,
                shape = longArrayOf(1L, 3L, 4L),
                attentionMask = mask,
                pooling = LocalPoolingMode.MEAN,
            )
            for (i in 0 until 4) {
                assertEquals(meanVec[i], bufMean[i], 1e-5f)
            }
            val flat2d = FloatBuffer.wrap(floatArrayOf(0f, 3f, 4f, 0f))
            val buf2d = LocalOnnxEmbeddingEngine.poolFlatBuffer(
                buffer = flat2d,
                shape = longArrayOf(1L, 4L),
                attentionMask = longArrayOf(1L),
                pooling = LocalPoolingMode.CLS,
            )
            assertEquals(0.6f, buf2d[1], 1e-5f)
            assertEquals(0.8f, buf2d[2], 1e-5f)

            // Verify weighted multi-chunk merging + unit L2 normalization
            val merged = LocalOnnxEmbeddingEngine.mergeChunkEmbeddings(
                chunkVectors = listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)),
                chunkWeights = intArrayOf(300, 100),
            )
            val mNorm = sqrt(300.0 * 300.0 + 100.0 * 100.0).toFloat()
            assertEquals(300f / mNorm, merged[0], 1e-5f)
            assertEquals(100f / mNorm, merged[1], 1e-5f)
            val unitCheck = sqrt(merged[0] * merged[0] + merged[1] * merged[1])
            assertTrue(abs(unitCheck - 1f) < 1e-5f)

            // 2. End-to-end engine lifecycle test with fake ONNX runtime session
            val specBge = LocalModelSpec(
                id = "bge-test",
                displayName = "BGE Test",
                descriptionZh = "测试",
                descriptionEn = "Test",
                dimension = 4,
                maxTokens = 16, // windowSize = 14 so long doc triggers multi-chunk
                approxSizeMb = 1,
                pooling = LocalPoolingMode.CLS,
                queryPrefix = "为这个句子生成表示以用于检索相关文章：",
                recommendedMinCosine = 0.58,
                modelMirrorUrls = emptyList(),
                vocabMirrorUrls = emptyList(),
            )
            val manager = LocalEmbeddingModelManager(
                modelsRootDir = tempDir,
                httpClient = OkHttpClient(),
                catalogSpecs = listOf(specBge),
            )
            // Install synthetic model files on disk
            val modelDir = manager.getModelDir("bge-test").apply { mkdirs() }
            File(modelDir, LocalEmbeddingModelManager.MODEL_FILE_NAME).writeBytes(byteArrayOf(1, 2, 3, 4))
            File(modelDir, LocalEmbeddingModelManager.VOCAB_FILE_NAME).writeText(sampleVocabText(), Charsets.UTF_8)
            File(modelDir, LocalEmbeddingModelManager.COMPLETE_MARKER_NAME).writeText("ok", Charsets.UTF_8)
            manager.refreshStates()

            val openCount = AtomicInteger(0)
            val closeCount = AtomicInteger(0)
            val observedChunks = ArrayList<EncodedChunk>()

            val fakeFactory = SessionRuntimeFactory { _, intra, inter ->
                assertEquals(2, intra)
                assertEquals(1, inter)
                openCount.incrementAndGet()
                object : OnnxEmbeddingSession {
                    override fun embedChunk(chunk: EncodedChunk, pooling: LocalPoolingMode): FloatArray {
                        observedChunks.add(chunk)
                        // Deterministic vector depending on whether chunk starts with query prefix token "为" (id=4)
                        val hasPrefix = chunk.inputIds.size > 1 && chunk.inputIds[1] == 4L
                        return if (hasPrefix) floatArrayOf(1f, 0f, 0f, 0f) else floatArrayOf(0f, 1f, 0f, 0f)
                    }

                    override fun close() {
                        closeCount.incrementAndGet()
                    }
                }
            }

            val engine = LocalOnnxEmbeddingEngine(
                modelManager = manager,
                activeModelIdProvider = { "bge-test" },
                idleUnloadTimeoutMs = 120L,
                intraOpThreads = 2,
                interOpThreads = 1,
                chunkStride = 4,
                maxDocumentChunks = 3,
                inferenceDispatcher = Dispatchers.Default,
                lifecycleScope = engineScope,
                runtimeFactory = fakeFactory,
            )

            assertFalse(engine.isSessionLoaded)
            assertTrue(engine.isModelInstalled("bge-test"))
            assertEquals("local|bge-test|4", engine.profileKey("bge-test"))
            assertEquals(0.58, engine.minCosineFloor("bge-test"), 1e-6)

            // Query embed prepends queryPrefix ("为这个句子生成表示以用于检索相关文章：")
            val qVecs = engine.embed("bge-test", listOf("青云剑宗"), isQuery = true)
            assertTrue(engine.isSessionLoaded)
            assertEquals(1, openCount.get())
            assertEquals(1, qVecs.size)
            assertEquals(1f, qVecs[0][0], 1e-5f)

            // Document embed does NOT prepend queryPrefix and chunks long text (>14 tokens)
            observedChunks.clear()
            val longDoc = "青云剑宗".repeat(8) // 32 tokens -> 3 overlapping chunks (window=14, stride=4, step=10)
            val dVecs = engine.embed("bge-test", listOf(longDoc), isQuery = false)
            assertEquals(1, dVecs.size)
            assertEquals(3, observedChunks.size)
            assertEquals(1f, dVecs[0][1], 1e-5f)
            assertEquals(1, openCount.get()) // reused already-loaded session!

            // Wait past idle timeout (120ms) -> session automatically unloads (0 MB idle RAM)
            delay(350L)
            assertFalse("Expected idle auto-unload after timeout", engine.isSessionLoaded)
            assertEquals(1, closeCount.get())

            // Next call lazily reloads, and explicit unloadNow() releases immediately
            engine.embed("bge-test", listOf("银针"), isQuery = false)
            assertTrue(engine.isSessionLoaded)
            assertEquals(2, openCount.get())
            engine.unloadNow()
            assertFalse(engine.isSessionLoaded)
            assertEquals(2, closeCount.get())
        } finally {
            engineScope.cancel()
            tempDir.deleteRecursively()
        }
    }
}
