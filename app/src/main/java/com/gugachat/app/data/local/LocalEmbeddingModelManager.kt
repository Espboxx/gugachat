package com.gugachat.app.data.local

import android.content.Context
import com.gugachat.app.BuildConfig
import com.gugachat.app.data.api.awaitCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class LocalPoolingMode {
    MEAN,
    CLS,
}

data class LocalModelSpec(
    val id: String,
    val displayName: String,
    val descriptionZh: String,
    val descriptionEn: String,
    val dimension: Int,
    val maxTokens: Int,
    val approxSizeMb: Int,
    val pooling: LocalPoolingMode,
    val queryPrefix: String,
    val recommendedMinCosine: Double,
    val modelMirrorUrls: List<String>,
    val vocabMirrorUrls: List<String>,
    val expectedModelSha256: String? = null,
)

sealed interface LocalModelDownloadState {
    data object NotDownloaded : LocalModelDownloadState

    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long,
        val mirrorLabel: String,
    ) : LocalModelDownloadState

    data class Ready(val sizeBytes: Long) : LocalModelDownloadState

    data class Error(val message: String) : LocalModelDownloadState
}

/**
 * Manages on-demand download, resumable multi-mirror failover, SHA-256 verification,
 * lifecycle state, and 1-tap cleanup of ultra-lightweight on-device ONNX embedding models.
 *
 * Files are stored under `<filesDir>/embedding_models/<modelId>/`:
 * - `model.onnx` (quantized INT8 ONNX model weights)
 * - `vocab.txt` (BERT WordPiece vocabulary)
 * - `.complete` (atomic completion marker written only after full integrity checks)
 */
class LocalEmbeddingModelManager internal constructor(
    private val modelsRootDir: File,
    private val httpClient: OkHttpClient,
    private val catalogSpecs: List<LocalModelSpec>,
) {
    constructor(
        context: Context,
        httpClient: OkHttpClient = defaultDownloadHttpClient(),
        catalog: List<LocalModelSpec> = DEFAULT_CATALOG,
    ) : this(
        modelsRootDir = File(context.applicationContext.filesDir, MODELS_DIR_NAME),
        httpClient = httpClient,
        catalogSpecs = catalog,
    )

    private val specsById: Map<String, LocalModelSpec> = catalogSpecs.associateBy { it.id }
    private val modelMutexes = ConcurrentHashMap<String, Mutex>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow<Map<String, LocalModelDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, LocalModelDownloadState>> = _states.asStateFlow()

    init {
        refreshStates()
    }

    fun catalog(): List<LocalModelSpec> = catalogSpecs

    fun getSpec(modelId: String): LocalModelSpec? = specsById[modelId]

    fun getModelDir(modelId: String): File = File(modelsRootDir, sanitizeModelId(modelId))

    fun getModelFile(modelId: String): File = File(getModelDir(modelId), MODEL_FILE_NAME)

    fun getVocabFile(modelId: String): File = File(getModelDir(modelId), VOCAB_FILE_NAME)

    private fun getCompleteMarkerFile(modelId: String): File = File(getModelDir(modelId), COMPLETE_MARKER_NAME)

    fun isModelInstalled(modelId: String): Boolean {
        if (modelId.isBlank()) return false
        val marker = getCompleteMarkerFile(modelId)
        val model = getModelFile(modelId)
        val vocab = getVocabFile(modelId)
        return marker.isFile && model.isFile && model.length() > 0L && vocab.isFile && vocab.length() > 0L
    }

    fun getInstalledSizeBytes(modelId: String): Long {
        if (!isModelInstalled(modelId)) return 0L
        return getModelFile(modelId).length() + getVocabFile(modelId).length()
    }

    fun refreshStates() {
        val next = LinkedHashMap<String, LocalModelDownloadState>(catalogSpecs.size)
        val current = _states.value
        for (spec in catalogSpecs) {
            val prev = current[spec.id]
            next[spec.id] = when {
                prev is LocalModelDownloadState.Downloading && activeJobs[spec.id]?.isActive == true -> prev
                isModelInstalled(spec.id) -> LocalModelDownloadState.Ready(getInstalledSizeBytes(spec.id))
                else -> LocalModelDownloadState.NotDownloaded
            }
        }
        _states.value = next
    }

    /**
     * Launches a download in [scope] (reusing any already-active download job for [modelId]).
     */
    fun startDownload(modelId: String, scope: CoroutineScope): Job {
        val existing = activeJobs[modelId]
        if (existing != null && existing.isActive) return existing
        return scope.launch(Dispatchers.IO) {
            downloadModel(modelId)
        }
    }

    /**
     * Downloads `vocab.txt` and `model.onnx` for [modelId] with automatic China-first mirror
     * failover, HTTP Range resumption (`.part` files), and optional SHA-256 verification.
     */
    suspend fun downloadModel(modelId: String): Result<File> {
        val spec = getSpec(modelId)
            ?: return Result.failure(IllegalArgumentException("Unknown local embedding model: $modelId"))

        val mutex = modelMutexes.computeIfAbsent(modelId) { Mutex() }
        return mutex.withLock {
            val myJob = currentCoroutineContext().job
            activeJobs[modelId] = myJob
            try {
                if (isModelInstalled(modelId)) {
                    val size = getInstalledSizeBytes(modelId)
                    setModelState(modelId, LocalModelDownloadState.Ready(size))
                    return@withLock Result.success(getModelDir(modelId))
                }

                val dir = getModelDir(modelId)
                withContext(Dispatchers.IO) {
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw IllegalStateException("Unable to create model directory: ${dir.absolutePath}")
                    }
                    // Clear stale completion marker before writing new files
                    getCompleteMarkerFile(modelId).delete()
                }

                val vocabFile = getVocabFile(modelId)
                if (!vocabFile.isFile || vocabFile.length() == 0L) {
                    downloadWithMirrorFailover(
                        urls = spec.vocabMirrorUrls,
                        destFile = vocabFile,
                        expectedSha256 = null,
                        maxBytes = MAX_VOCAB_BYTES,
                        isBinaryModel = false,
                        onProgress = { read, total, mirrorLabel ->
                            setModelState(
                                modelId,
                                LocalModelDownloadState.Downloading(
                                    bytesRead = read,
                                    totalBytes = total.coerceAtLeast(read),
                                    mirrorLabel = mirrorLabel,
                                ),
                            )
                        },
                    )
                }

                val modelFile = getModelFile(modelId)
                val expectedApproxBytes = spec.approxSizeMb.toLong() * 1024L * 1024L
                downloadWithMirrorFailover(
                    urls = spec.modelMirrorUrls,
                    destFile = modelFile,
                    expectedSha256 = spec.expectedModelSha256,
                    maxBytes = MAX_MODEL_BYTES,
                    isBinaryModel = true,
                    onProgress = { read, total, mirrorLabel ->
                        val effectiveTotal = when {
                            total > 0L -> total
                            expectedApproxBytes > 0L -> maxOf(expectedApproxBytes, read)
                            else -> read
                        }
                        setModelState(
                            modelId,
                            LocalModelDownloadState.Downloading(
                                bytesRead = read,
                                totalBytes = effectiveTotal,
                                mirrorLabel = mirrorLabel,
                            ),
                        )
                    },
                )

                val totalInstalledSize = modelFile.length() + vocabFile.length()
                withContext(Dispatchers.IO) {
                    val marker = getCompleteMarkerFile(modelId)
                    marker.writeText(
                        "id=${spec.id}\nsize=$totalInstalledSize\ninstalled_at=${System.currentTimeMillis()}\n",
                        Charsets.UTF_8,
                    )
                }
                setModelState(modelId, LocalModelDownloadState.Ready(totalInstalledSize))
                Result.success(dir)
            } catch (ce: CancellationException) {
                val fallbackState = if (isModelInstalled(modelId)) {
                    LocalModelDownloadState.Ready(getInstalledSizeBytes(modelId))
                } else {
                    LocalModelDownloadState.NotDownloaded
                }
                setModelState(modelId, fallbackState)
                throw ce
            } catch (t: Throwable) {
                val msg = t.message?.takeIf { it.isNotBlank() } ?: "Model download failed"
                setModelState(modelId, LocalModelDownloadState.Error(msg))
                Result.failure(t)
            } finally {
                activeJobs.remove(modelId, myJob)
            }
        }
    }

    fun cancelDownload(modelId: String) {
        val job = activeJobs.remove(modelId)
        job?.cancel()
        if (!isModelInstalled(modelId)) {
            setModelState(modelId, LocalModelDownloadState.NotDownloaded)
        } else {
            setModelState(modelId, LocalModelDownloadState.Ready(getInstalledSizeBytes(modelId)))
        }
    }

    /**
     * Cancels any active download for [modelId], removes installed weights/vocab/part files,
     * and resets the state to [LocalModelDownloadState.NotDownloaded].
     */
    fun deleteModel(modelId: String): Boolean {
        activeJobs.remove(modelId)?.cancel()
        val dir = getModelDir(modelId)
        val existed = dir.exists()
        val deleted = if (existed) dir.deleteRecursively() else true
        setModelState(modelId, LocalModelDownloadState.NotDownloaded)
        return deleted
    }

    private suspend fun downloadWithMirrorFailover(
        urls: List<String>,
        destFile: File,
        expectedSha256: String?,
        maxBytes: Long,
        isBinaryModel: Boolean,
        onProgress: (bytesRead: Long, totalBytes: Long, mirrorLabel: String) -> Unit,
    ) {
        if (urls.isEmpty()) {
            throw IllegalStateException("No download mirrors configured for ${destFile.name}")
        }
        val errors = ArrayList<String>(urls.size)
        for (url in urls) {
            currentCoroutineContext().ensureActive()
            val label = mirrorLabelOf(url)
            try {
                downloadResumableFile(
                    url = url,
                    mirrorLabel = label,
                    destFile = destFile,
                    expectedSha256 = expectedSha256,
                    maxBytes = maxBytes,
                    isBinaryModel = isBinaryModel,
                    onProgress = onProgress,
                )
                return
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val detail = t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
                errors.add("$label: $detail")
            }
        }
        throw IllegalStateException(errors.joinToString(" | ").ifBlank { "All mirrors failed" })
    }

    private suspend fun downloadResumableFile(
        url: String,
        mirrorLabel: String,
        destFile: File,
        expectedSha256: String?,
        maxBytes: Long,
        isBinaryModel: Boolean,
        onProgress: (bytesRead: Long, totalBytes: Long, mirrorLabel: String) -> Unit,
    ) {
        val partFile = File(destFile.parentFile, "${destFile.name}.part")
        val normalizedExpectedSha = normalizeSha256(expectedSha256)

        val dlClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()

        // Allow one internal retry when resuming encounters HTTP 416, mismatched Content-Range,
        // or a poisoned `.part` prefix failing SHA-256 after resume.
        var attempt = 0
        while (attempt < 2) {
            attempt++
            currentCoroutineContext().ensureActive()
            val existingBytes = if (partFile.isFile) partFile.length().coerceAtLeast(0L) else 0L
            onProgress(existingBytes, existingBytes, mirrorLabel)

            val reqBuilder = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "*/*")
                .header("User-Agent", USER_AGENT)
            if (existingBytes > 0L) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            }

            val response = dlClient.awaitCall(reqBuilder.build())
            var resumedThisAttempt = false
            val needsFreshRestart = withContext(Dispatchers.IO) {
                response.use { resp ->
                    if (resp.code == 416) {
                        val rangeTotal = parseTotalFromContentRange(resp.header("Content-Range"))
                        if (rangeTotal != null && rangeTotal == existingBytes && existingBytes > 0L) {
                            resumedThisAttempt = true
                            // Already downloaded every byte into `.part` — verify integrity below
                            return@withContext false
                        }
                        partFile.delete()
                        return@withContext true
                    }

                    if (resp.code != 200 && resp.code != 206) {
                        throw IllegalStateException("HTTP ${resp.code}")
                    }

                    val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                    if (contentType.contains("text/html")) {
                        partFile.delete()
                        throw IllegalStateException("Unexpected HTML response")
                    }

                    val isPartial = resp.code == 206
                    val appendMode: Boolean
                    val startOffset: Long
                    val totalBytes: Long

                    if (isPartial) {
                        val contentRange = resp.header("Content-Range")
                        val parsedRange = parseContentRange(contentRange)
                        if (parsedRange != null && parsedRange.start != existingBytes) {
                            // Server returned an unexpected byte range; reset and retry cleanly from 0
                            partFile.delete()
                            return@withContext true
                        }
                        appendMode = existingBytes > 0L
                        resumedThisAttempt = appendMode
                        startOffset = existingBytes
                        val bodyLen = resp.body?.contentLength()?.takeIf { it > 0L } ?: -1L
                        totalBytes = parsedRange?.total
                            ?: if (bodyLen > 0L) existingBytes + bodyLen else 0L
                    } else {
                        // Server ignored Range header (or fresh download): overwrite from byte 0
                        appendMode = false
                        startOffset = 0L
                        totalBytes = resp.body?.contentLength()?.takeIf { it > 0L } ?: 0L
                    }

                    val body = resp.body ?: throw IllegalStateException("Empty download body")
                    val buf = ByteArray(STREAM_BUFFER_BYTES)
                    var downloaded = startOffset
                    var lastEmitMs = 0L
                    var checkedPrefix = startOffset > 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, appendMode).use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buf)
                                if (n <= 0) break

                                if (!checkedPrefix) {
                                    checkedPrefix = true
                                    if (looksLikeHtmlPayload(buf, n)) {
                                        output.close()
                                        partFile.delete()
                                        throw IllegalStateException("Mirror returned HTML error page")
                                    }
                                }

                                downloaded += n
                                if (downloaded > maxBytes) {
                                    output.close()
                                    partFile.delete()
                                    throw IllegalStateException("Downloaded file exceeds size limit")
                                }
                                output.write(buf, 0, n)

                                val now = System.currentTimeMillis()
                                if (now - lastEmitMs >= PROGRESS_EMIT_INTERVAL_MS || (totalBytes > 0L && downloaded >= totalBytes)) {
                                    lastEmitMs = now
                                    onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded, mirrorLabel)
                                }
                            }
                            output.flush()
                        }
                    }

                    if (totalBytes > 0L && downloaded < totalBytes) {
                        // Keep `.part` file intact so next mirror/retry can resume from `downloaded`
                        throw IllegalStateException("Truncated stream ($downloaded/$totalBytes bytes)")
                    }
                    onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded, mirrorLabel)
                    false
                }
            }

            if (needsFreshRestart) {
                if (attempt >= 2) {
                    throw IllegalStateException("Unable to synchronize download byte range")
                }
                continue
            }

            // Post-download integrity checks & atomic promote
            val retryFromZero = withContext(Dispatchers.IO) {
                if (!partFile.isFile || partFile.length() <= 0L) {
                    partFile.delete()
                    throw IllegalStateException("Downloaded file is empty")
                }

                if (isBinaryModel && looksLikeHtmlFile(partFile)) {
                    partFile.delete()
                    if (resumedThisAttempt && attempt < 2) return@withContext true
                    throw IllegalStateException("Downloaded ONNX file appears to be HTML")
                }

                if (normalizedExpectedSha != null) {
                    val actualSha = computeFileSha256(partFile)
                    if (actualSha == null || !actualSha.equals(normalizedExpectedSha, ignoreCase = true)) {
                        partFile.delete()
                        if (resumedThisAttempt && attempt < 2) return@withContext true
                        throw IllegalStateException("SHA-256 verification failed")
                    }
                }

                if (destFile.exists()) {
                    destFile.delete()
                }
                if (!partFile.renameTo(destFile)) {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                }
                false
            }
            if (!retryFromZero) {
                return
            }
        }
    }

    private fun setModelState(modelId: String, state: LocalModelDownloadState) {
        _states.update { current ->
            val updated = LinkedHashMap(current)
            updated[modelId] = state
            updated
        }
    }

    private data class ParsedContentRange(val start: Long, val end: Long, val total: Long?)

    companion object {
        const val MODELS_DIR_NAME = "embedding_models"
        const val MODEL_FILE_NAME = "model.onnx"
        const val VOCAB_FILE_NAME = "vocab.txt"
        const val COMPLETE_MARKER_NAME = ".complete"

        const val MODEL_ID_GTE_SMALL_ZH_INT8 = "gte-small-zh-int8"
        const val MODEL_ID_BGE_SMALL_ZH_V15_INT8 = "bge-small-zh-v1.5-int8"
        const val DEFAULT_MODEL_ID = MODEL_ID_GTE_SMALL_ZH_INT8

        private const val CONNECT_TIMEOUT_SEC = 15L
        private const val READ_TIMEOUT_SEC = 60L
        private const val STREAM_BUFFER_BYTES = 32 * 1024
        private const val PROGRESS_EMIT_INTERVAL_MS = 80L
        private const val MAX_MODEL_BYTES = 250L * 1024L * 1024L
        private const val MAX_VOCAB_BYTES = 16L * 1024L * 1024L
        private val USER_AGENT: String
            get() = "GuGuChat-Android/${BuildConfig.VERSION_NAME}"

        /**
         * Live-verified ONNX INT8 download catalog (all URLs, byte sizes, pooling configs,
         * and SHA-256 digests verified against live mirrors).
         *
         * Notes on verified configs:
         * - `thenlper/gte-small-zh` (`towing/gte-small-zh` INT8 ONNX: 30,468,930 B; `vocab.txt`: 69,998 B):
         *   Official `1_Pooling/config.json` specifies `pooling_mode_cls_token = true`,
         *   `pooling_mode_mean_tokens = false`, no query prefix required.
         * - `BAAI/bge-small-zh-v1.5` (`Xenova/bge-small-zh-v1.5` INT8 ONNX: 24,010,842 B; `vocab.txt`: 109,540 B):
         *   Official `1_Pooling/config.json` specifies `pooling_mode_cls_token = true`,
         *   with query retrieval instruction prefix `"为这个句子生成表示以用于检索相关文章："`.
         */
        val DEFAULT_CATALOG: List<LocalModelSpec> = listOf(
            LocalModelSpec(
                id = MODEL_ID_GTE_SMALL_ZH_INT8,
                displayName = "GTE-Small-ZH (INT8)",
                descriptionZh = "阿里达摩院极轻中文向量模型（~30 MB，512 维，C-MTEB 检索 65.5，无需查询前缀，默认推荐）",
                descriptionEn = "Alibaba DAMO ultra-light Chinese embedder (~30 MB, 512d, C-MTEB Retrieval 65.5, no prefix, recommended)",
                dimension = 512,
                maxTokens = 512,
                approxSizeMb = 30,
                pooling = LocalPoolingMode.CLS,
                queryPrefix = "",
                recommendedMinCosine = 0.60,
                modelMirrorUrls = listOf(
                    "https://hf-mirror.com/towing/gte-small-zh/resolve/main/onnx/model_quantized.onnx",
                    "https://huggingface.co/towing/gte-small-zh/resolve/main/onnx/model_quantized.onnx",
                ),
                vocabMirrorUrls = listOf(
                    "https://modelscope.cn/models/AI-ModelScope/gte-small-zh/resolve/master/vocab.txt",
                    "https://hf-mirror.com/towing/gte-small-zh/resolve/main/vocab.txt",
                    "https://huggingface.co/towing/gte-small-zh/resolve/main/vocab.txt",
                ),
                expectedModelSha256 = "697f3b20b8a4f8e245e8aca75b0bfe33a56d60bd0c1de9ad5c94a1e08d2d48dc",
            ),
            LocalModelSpec(
                id = MODEL_ID_BGE_SMALL_ZH_V15_INT8,
                displayName = "BGE-Small-ZH v1.5 (INT8)",
                descriptionZh = "智源研究院极轻中文向量模型（~24 MB，512 维，魔搭国内直连镜像，余弦分布均衡）",
                descriptionEn = "BAAI ultra-light Chinese embedder (~24 MB, 512d, ModelScope China mirror, balanced cosine spread)",
                dimension = 512,
                maxTokens = 512,
                approxSizeMb = 24,
                pooling = LocalPoolingMode.CLS,
                queryPrefix = "为这个句子生成表示以用于检索相关文章：",
                recommendedMinCosine = 0.58,
                modelMirrorUrls = listOf(
                    "https://modelscope.cn/api/v1/models/Xenova/bge-small-zh-v1.5/repo?Revision=master&FilePath=onnx/model_quantized.onnx",
                    "https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main/onnx/model_quantized.onnx",
                    "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/onnx/model_quantized.onnx",
                ),
                vocabMirrorUrls = listOf(
                    "https://modelscope.cn/models/Xenova/bge-small-zh-v1.5/resolve/master/vocab.txt",
                    "https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main/vocab.txt",
                    "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/vocab.txt",
                ),
                expectedModelSha256 = "15b717c382bcb518ba457b93ea6850ede7f4f1cd8937454aa06972366cd19bcc",
            ),
        )

        fun defaultDownloadHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()

        internal fun mirrorLabelOf(url: String): String {
            val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
            return when {
                host.contains("modelscope.cn") -> "ModelScope 国内镜像"
                host.contains("hf-mirror.com") -> "HF-Mirror 国内镜像"
                host.contains("huggingface.co") -> "Hugging Face"
                host.isNotBlank() -> host
                else -> url
            }
        }

        internal fun normalizeSha256(raw: String?): String? =
            com.gugachat.app.core.util.HashUtils.normalizeSha256(raw)

        internal fun computeFileSha256(file: File): String? =
            com.gugachat.app.core.util.HashUtils.sha256HexOrNull(file)

        private val CONTENT_RANGE_REGEX = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
        private val CONTENT_RANGE_TOTAL_REGEX = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)

        private fun parseContentRange(header: String?): ParsedContentRange? {
            if (header.isNullOrBlank()) return null
            val match = CONTENT_RANGE_REGEX.find(header.trim()) ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val end = match.groupValues[2].toLongOrNull() ?: return null
            val total = match.groupValues[3].takeIf { it != "*" }?.toLongOrNull()
            return ParsedContentRange(start = start, end = end, total = total)
        }

        private fun parseTotalFromContentRange(header: String?): Long? {
            if (header.isNullOrBlank()) return null
            val m = CONTENT_RANGE_TOTAL_REGEX.find(header.trim()) ?: return null
            return m.groupValues[1].toLongOrNull()
        }

        private fun sanitizeModelId(modelId: String): String =
            modelId.trim().replace(Regex("""[^a-zA-Z0-9._-]"""), "_").ifBlank { "default" }

        private fun looksLikeHtmlPayload(buf: ByteArray, length: Int): Boolean {
            val prefix = String(buf, 0, minOf(length, 64), Charsets.UTF_8).trimStart().lowercase(Locale.ROOT)
            return prefix.startsWith("<!doctype html") ||
                prefix.startsWith("<html") ||
                prefix.startsWith("<head")
        }

        private fun looksLikeHtmlFile(file: File): Boolean {
            return runCatching {
                val buf = ByteArray(64)
                FileInputStream(file).use { fis ->
                    val n = fis.read(buf)
                    n > 0 && looksLikeHtmlPayload(buf, n)
                }
            }.getOrDefault(false)
        }
    }
}
