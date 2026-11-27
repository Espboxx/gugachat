package com.gugachat.app.data.local

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.gugachat.app.domain.chat.BertWordPieceTokenizer
import com.gugachat.app.domain.chat.EncodedChunk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Internal inference session abstraction so [LocalOnnxEmbeddingEngine] runs real ONNX Runtime
 * Mobile in production while staying 100% pure-JVM testable without Android `.so` binaries.
 */
internal interface OnnxEmbeddingSession : Closeable {
    fun embedChunk(chunk: EncodedChunk, pooling: LocalPoolingMode): FloatArray
}

internal fun interface SessionRuntimeFactory {
    fun open(modelFile: File, intraOpThreads: Int, interOpThreads: Int): OnnxEmbeddingSession
}

private object OrtSessionRuntimeFactory : SessionRuntimeFactory {
    override fun open(modelFile: File, intraOpThreads: Int, interOpThreads: Int): OnnxEmbeddingSession {
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(intraOpThreads.coerceAtLeast(1))
            setInterOpNumThreads(interOpThreads.coerceAtLeast(1))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setMemoryPatternOptimization(true)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            // Disable busy-wait spinning on mobile CPUs to avoid battery drain and thermal throttling
            runCatching { addConfigEntry("session.intra_op.allow_spinning", "0") }
            runCatching { addConfigEntry("session.inter_op.allow_spinning", "0") }
        }
        val session = try {
            env.createSession(modelFile.absolutePath, options)
        } finally {
            runCatching { options.close() }
        }
        return RealOrtEmbeddingSession(env, session)
    }
}

private class RealOrtEmbeddingSession(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) : OnnxEmbeddingSession {
    private val acceptsTokenTypeIds: Boolean = "token_type_ids" in session.inputNames
    private val preferredOutputName: String? = when {
        "last_hidden_state" in session.outputNames -> "last_hidden_state"
        "token_embeddings" in session.outputNames -> "token_embeddings"
        else -> null
    }

    override fun embedChunk(chunk: EncodedChunk, pooling: LocalPoolingMode): FloatArray {
        val seqLen = chunk.inputIds.size.toLong()
        val shape = longArrayOf(1L, seqLen)

        var inputIdsTensor: OnnxTensor? = null
        var attentionMaskTensor: OnnxTensor? = null
        var tokenTypeTensor: OnnxTensor? = null
        try {
            inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(chunk.inputIds), shape)
            attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(chunk.attentionMask), shape)
            val inputs = LinkedHashMap<String, OnnxTensor>(3)
            inputs["input_ids"] = inputIdsTensor
            inputs["attention_mask"] = attentionMaskTensor
            if (acceptsTokenTypeIds) {
                tokenTypeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(chunk.tokenTypeIds), shape)
                inputs["token_type_ids"] = tokenTypeTensor
            }

            session.run(inputs).use { result ->
                val outputTensor = selectOutputTensor(result)
                val info = outputTensor.info
                val outShape = info.shape
                val floatBuf = outputTensor.floatBuffer
                return LocalOnnxEmbeddingEngine.poolFlatBuffer(
                    buffer = floatBuf,
                    shape = outShape,
                    attentionMask = chunk.attentionMask,
                    pooling = pooling,
                )
            }
        } finally {
            runCatching { tokenTypeTensor?.close() }
            runCatching { attentionMaskTensor?.close() }
            runCatching { inputIdsTensor?.close() }
        }
    }

    private fun selectOutputTensor(result: OrtSession.Result): OnnxTensor {
        if (preferredOutputName != null) {
            val opt = result.get(preferredOutputName)
            if (opt.isPresent && opt.get() is OnnxTensor) {
                return opt.get() as OnnxTensor
            }
        }
        // Prefer the first 3D token-level hidden-state tensor if available
        for (entry in result) {
            val tensor = entry.value as? OnnxTensor ?: continue
            if (tensor.info.shape.size == 3) return tensor
        }
        return result.get(0) as? OnnxTensor
            ?: throw IllegalStateException("ONNX embedding model returned non-tensor output")
    }

    override fun close() {
        runCatching { session.close() }
    }
}

/**
 * Heavily optimized on-device ONNX Runtime Mobile embedding engine for GuGuChat.
 *
 * Optimizations implemented:
 * 1. Overlapping chunk windows for long documents (>512 tokens, up to 3 chunks) with
 *    `contentTokenCount`-weighted vector merging + L2 re-normalization.
 * 2. Controlled 2-thread intra-op / 1-thread inter-op CPU execution + `ALL_OPT` graph
 *    optimization + memory pattern reuse + spinning disabled to avoid mobile thermal throttling.
 * 3. Zero-copy `FloatBuffer` CLS / masked-MEAN pooling + `Double`-precision L2 normalization.
 * 4. Lazy session loading + automatic idle auto-unload (default 180s -> 0 MB idle RAM) +
 *    non-blocking [unloadNow] that safely defers teardown if an inference is in-flight.
 * 5. Cooperative batching on [Dispatchers.Default] with `yield()` between chunks and items
 *    so background indexing keeps the Compose UI silky at 60fps.
 */
class LocalOnnxEmbeddingEngine internal constructor(
    private val modelManager: LocalEmbeddingModelManager,
    private val activeModelIdProvider: () -> String,
    private val idleUnloadTimeoutMs: Long,
    private val intraOpThreads: Int,
    private val interOpThreads: Int,
    private val chunkStride: Int,
    private val maxDocumentChunks: Int,
    private val inferenceDispatcher: CoroutineDispatcher,
    private val lifecycleScope: CoroutineScope,
    private val runtimeFactory: SessionRuntimeFactory,
) {
    constructor(
        modelManager: LocalEmbeddingModelManager,
        activeModelIdProvider: () -> String = { LocalEmbeddingModelManager.DEFAULT_MODEL_ID },
        idleUnloadTimeoutMs: Long = DEFAULT_IDLE_UNLOAD_TIMEOUT_MS,
        intraOpThreads: Int = DEFAULT_INTRA_OP_THREADS,
        interOpThreads: Int = DEFAULT_INTER_OP_THREADS,
        chunkStride: Int = BertWordPieceTokenizer.DEFAULT_CHUNK_STRIDE,
        maxDocumentChunks: Int = BertWordPieceTokenizer.DEFAULT_MAX_CHUNKS,
        inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
        lifecycleScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ) : this(
        modelManager = modelManager,
        activeModelIdProvider = activeModelIdProvider,
        idleUnloadTimeoutMs = idleUnloadTimeoutMs,
        intraOpThreads = intraOpThreads,
        interOpThreads = interOpThreads,
        chunkStride = chunkStride,
        maxDocumentChunks = maxDocumentChunks,
        inferenceDispatcher = inferenceDispatcher,
        lifecycleScope = lifecycleScope,
        runtimeFactory = OrtSessionRuntimeFactory,
    )

    private val sessionMutex = Mutex()
    private var loadedModelId: String? = null
    private var loadedSession: OnnxEmbeddingSession? = null
    private var loadedTokenizer: BertWordPieceTokenizer? = null
    private val idleUnloadJobRef = AtomicReference<Job?>(null)
    private val activityGeneration = AtomicLong(0L)

    @Volatile
    private var pendingUnload: Boolean = false

    @Volatile
    var isSessionLoaded: Boolean = false
        private set

    val activeModelId: String
        get() = activeModelIdProvider().takeIf { it.isNotBlank() }
            ?: LocalEmbeddingModelManager.DEFAULT_MODEL_ID

    fun profileKey(modelId: String = activeModelId): String {
        val dim = modelManager.getSpec(modelId)?.dimension ?: DEFAULT_DIMENSION
        return "local|$modelId|$dim"
    }

    fun minCosineFloor(modelId: String = activeModelId): Double =
        modelManager.getSpec(modelId)?.recommendedMinCosine ?: DEFAULT_MIN_COSINE_FLOOR

    fun isModelInstalled(modelId: String = activeModelId): Boolean =
        modelManager.isModelInstalled(modelId)

    /**
     * Embeds [texts] using the active local model [activeModelId].
     */
    suspend fun embed(texts: List<String>, isQuery: Boolean): List<FloatArray> =
        embed(modelId = activeModelId, texts = texts, isQuery = isQuery)

    /**
     * Embeds [texts] using [modelId] on [inferenceDispatcher].
     *
     * - Automatically prepends `spec.queryPrefix` when [isQuery] is `true`.
     * - Short queries use the single-pass fast path (`encodeSingle`).
     * - Long documents (`isQuery == false`) use overlapping chunks (`encodeChunked`, up to
     *   [maxDocumentChunks] windows) combined via `contentTokenCount`-weighted averaging + L2 norm.
     * - Calls `yield()` cooperatively between chunks and batch items to keep UI responsive.
     */
    suspend fun embed(
        modelId: String,
        texts: List<String>,
        isQuery: Boolean,
    ): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val spec = modelManager.getSpec(modelId)
            ?: throw IllegalArgumentException("Unknown local embedding model: $modelId")
        if (!modelManager.isModelInstalled(modelId)) {
            throw IllegalStateException("Local embedding model '$modelId' is not installed")
        }

        return withContext(inferenceDispatcher) {
            val results = ArrayList<FloatArray>(texts.size)
            for (index in texts.indices) {
                currentCoroutineContext().ensureActive()
                val rawText = texts[index]
                val effectiveText = if (isQuery && spec.queryPrefix.isNotEmpty()) {
                    spec.queryPrefix + rawText
                } else {
                    rawText
                }

                // Step 1: Ensure session & tokenizer are loaded, tokenize outside long native lock
                val tokenizer = ensureTokenizer(modelId)
                val chunks = if (isQuery) {
                    listOf(tokenizer.encodeSingle(effectiveText, maxSeqLen = spec.maxTokens))
                } else {
                    tokenizer.encodeChunked(
                        text = effectiveText,
                        maxSeqLen = spec.maxTokens,
                        stride = chunkStride,
                        maxChunks = maxDocumentChunks,
                    )
                }

                // Step 2: Run each chunk cooperatively through ONNX Runtime
                val chunkVectors = ArrayList<FloatArray>(chunks.size)
                val chunkWeights = IntArray(chunks.size)
                for (cIdx in chunks.indices) {
                    currentCoroutineContext().ensureActive()
                    val chunk = chunks[cIdx]
                    val vec = runChunkLocked(modelId, chunk, spec.pooling)
                    chunkVectors.add(vec)
                    chunkWeights[cIdx] = chunk.contentTokenCount
                    if (cIdx + 1 < chunks.size) {
                        yield()
                    }
                }

                results.add(mergeChunkEmbeddings(chunkVectors, chunkWeights))
                if (index + 1 < texts.size) {
                    yield()
                }
            }
            scheduleIdleUnload()
            results
        }
    }

    /**
     * Immediately releases the loaded ONNX session and tokenizer to reclaim RAM (0 MB idle).
     * Non-blocking: if an inference call is currently in-flight, marks a deferred unload so
     * resources are closed as soon as that chunk finishes.
     */
    fun unloadNow() {
        cancelIdleTimer()
        if (sessionMutex.tryLock()) {
            try {
                pendingUnload = false
                closeSessionLocked()
            } finally {
                sessionMutex.unlock()
            }
        } else {
            pendingUnload = true
        }
    }

    private suspend fun ensureTokenizer(modelId: String): BertWordPieceTokenizer =
        sessionMutex.withLock {
            pendingUnload = false
            activityGeneration.incrementAndGet()
            cancelIdleTimer()
            ensureLoadedLocked(modelId)
            loadedTokenizer ?: throw IllegalStateException("Tokenizer failed to initialize for $modelId")
        }

    private suspend fun runChunkLocked(
        modelId: String,
        chunk: EncodedChunk,
        pooling: LocalPoolingMode,
    ): FloatArray = sessionMutex.withLock {
        try {
            activityGeneration.incrementAndGet()
            ensureLoadedLocked(modelId)
            val session = loadedSession
                ?: throw IllegalStateException("ONNX session failed to initialize for $modelId")
            session.embedChunk(chunk, pooling)
        } finally {
            if (pendingUnload) {
                pendingUnload = false
                closeSessionLocked()
            }
        }
    }

    private fun ensureLoadedLocked(modelId: String) {
        if (loadedModelId == modelId && loadedSession != null && loadedTokenizer != null) {
            return
        }
        // Release any previous model before loading a different one
        closeSessionLocked()

        val modelFile = modelManager.getModelFile(modelId)
        val vocabFile = modelManager.getVocabFile(modelId)
        if (!modelFile.isFile || !vocabFile.isFile) {
            throw IllegalStateException("Model files missing for '$modelId'")
        }

        val tokenizer = BertWordPieceTokenizer.fromFile(vocabFile)
        val session = runtimeFactory.open(
            modelFile = modelFile,
            intraOpThreads = intraOpThreads,
            interOpThreads = interOpThreads,
        )
        loadedModelId = modelId
        loadedTokenizer = tokenizer
        loadedSession = session
        isSessionLoaded = true
    }

    private fun closeSessionLocked() {
        runCatching { loadedSession?.close() }
        loadedSession = null
        loadedTokenizer = null
        loadedModelId = null
        isSessionLoaded = false
    }

    private fun cancelIdleTimer() {
        idleUnloadJobRef.getAndSet(null)?.cancel()
    }

    private fun scheduleIdleUnload() {
        if (idleUnloadTimeoutMs <= 0L) return
        val expectedGen = activityGeneration.incrementAndGet()
        val job = lifecycleScope.launch {
            delay(idleUnloadTimeoutMs)
            sessionMutex.withLock {
                if (isSessionLoaded && activityGeneration.get() == expectedGen) {
                    closeSessionLocked()
                }
            }
        }
        idleUnloadJobRef.getAndSet(job)?.cancel()
    }

    companion object {
        const val DEFAULT_INTRA_OP_THREADS = 2
        const val DEFAULT_INTER_OP_THREADS = 1
        const val DEFAULT_IDLE_UNLOAD_TIMEOUT_MS = 180_000L
        const val DEFAULT_DIMENSION = 512
        const val DEFAULT_MIN_COSINE_FLOOR = 0.58

        /**
         * Pools a flattened ONNX output [FloatBuffer] (`[1, seqLen, dim]` or `[1, dim]`)
         * with zero per-token array allocation, then L2-normalizes to unit length.
         */
        internal fun poolFlatBuffer(
            buffer: FloatBuffer,
            shape: LongArray,
            attentionMask: LongArray,
            pooling: LocalPoolingMode,
        ): FloatArray {
            val readOnly = buffer.duplicate()
            readOnly.rewind()

            if (shape.size == 2) {
                val dim = shape[1].toInt()
                require(dim > 0) { "Invalid 2D embedding output dimension: $dim" }
                val out = FloatArray(dim)
                readOnly.get(out, 0, dim)
                return l2NormalizeInPlace(out)
            }

            require(shape.size == 3) { "Unexpected ONNX output tensor rank: ${shape.contentToString()}" }
            val seqLen = shape[1].toInt()
            val dim = shape[2].toInt()
            require(seqLen > 0 && dim > 0) { "Invalid 3D output shape: ${shape.contentToString()}" }

            return when (pooling) {
                LocalPoolingMode.CLS -> {
                    val out = FloatArray(dim)
                    readOnly.get(out, 0, dim)
                    l2NormalizeInPlace(out)
                }
                LocalPoolingMode.MEAN -> {
                    val accum = DoubleArray(dim)
                    var validTokens = 0
                    val effectiveLen = minOf(seqLen, attentionMask.size)
                    for (t in 0 until effectiveLen) {
                        if (attentionMask[t] != 0L) {
                            val baseOffset = t * dim
                            for (d in 0 until dim) {
                                accum[d] += readOnly.get(baseOffset + d).toDouble()
                            }
                            validTokens++
                        }
                    }
                    val denom = validTokens.coerceAtLeast(1).toDouble()
                    val out = FloatArray(dim) { d -> (accum[d] / denom).toFloat() }
                    l2NormalizeInPlace(out)
                }
            }
        }

        /**
         * Pure-Kotlin pooling helper for 2D `[seqLen][dim]` token embeddings.
         */
        internal fun poolHiddenStates(
            hiddenStates: Array<FloatArray>,
            attentionMask: LongArray,
            pooling: LocalPoolingMode,
        ): FloatArray {
            require(hiddenStates.isNotEmpty()) { "hiddenStates must not be empty" }
            val dim = hiddenStates[0].size
            require(dim > 0) { "Embedding dimension must be positive" }

            return when (pooling) {
                LocalPoolingMode.CLS -> l2NormalizeInPlace(hiddenStates[0].copyOf())
                LocalPoolingMode.MEAN -> {
                    val accum = DoubleArray(dim)
                    var validTokens = 0
                    val effectiveLen = minOf(hiddenStates.size, attentionMask.size)
                    for (t in 0 until effectiveLen) {
                        if (attentionMask[t] != 0L) {
                            val row = hiddenStates[t]
                            for (d in 0 until dim) {
                                accum[d] += row[d].toDouble()
                            }
                            validTokens++
                        }
                    }
                    val denom = validTokens.coerceAtLeast(1).toDouble()
                    l2NormalizeInPlace(FloatArray(dim) { d -> (accum[d] / denom).toFloat() })
                }
            }
        }

        /**
         * Computes a `contentTokenCount`-weighted average of L2-normalized chunk embeddings
         * and re-normalizes the merged vector to unit L2 norm.
         */
        internal fun mergeChunkEmbeddings(
            chunkVectors: List<FloatArray>,
            chunkWeights: IntArray,
        ): FloatArray {
            require(chunkVectors.isNotEmpty()) { "chunkVectors must not be empty" }
            if (chunkVectors.size == 1) {
                return l2NormalizeInPlace(chunkVectors[0].copyOf())
            }
            val dim = chunkVectors[0].size
            val accum = DoubleArray(dim)
            for (i in chunkVectors.indices) {
                val vec = chunkVectors[i]
                val w = (if (i < chunkWeights.size) chunkWeights[i] else 1).coerceAtLeast(1).toDouble()
                for (d in 0 until dim) {
                    accum[d] += vec[d].toDouble() * w
                }
            }
            val merged = FloatArray(dim) { d -> accum[d].toFloat() }
            return l2NormalizeInPlace(merged)
        }

        internal fun l2NormalizeInPlace(vector: FloatArray): FloatArray {
            var sumSq = 0.0
            for (v in vector) {
                sumSq += v.toDouble() * v.toDouble()
            }
            val norm = sqrt(sumSq)
            if (norm > 1e-12) {
                val inv = 1.0 / norm
                for (i in vector.indices) {
                    vector[i] = (vector[i].toDouble() * inv).toFloat()
                }
            }
            return vector
        }
    }
}
