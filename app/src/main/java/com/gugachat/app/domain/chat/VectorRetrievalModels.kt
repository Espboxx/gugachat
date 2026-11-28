package com.gugachat.app.domain.chat

import com.gugachat.app.core.storage.ApiProtocol
import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Serializable
enum class RetrievalMode {
    LEXICAL_ONLY,
    LOCAL_SMALL_MODEL,
    REMOTE_API,
}

@Serializable
data class RemoteEmbeddingConfig(
    val providerId: String = "",
    val customBaseUrl: String = "",
    val customApiKey: String = "",
    val protocol: ApiProtocol = ApiProtocol.OPENAI_CHAT,
    val modelId: String = "BAAI/bge-m3",
    val dimensions: Int? = null,
)

@Serializable
data class RemoteRerankConfig(
    val enabled: Boolean = false,
    val providerId: String = "",
    val customBaseUrl: String = "",
    val customApiKey: String = "",
    val modelId: String = "BAAI/bge-reranker-v2-m3",
    val useTeiSchema: Boolean = false,
)

@Serializable
data class RetrievalSettings(
    val mode: RetrievalMode = RetrievalMode.LEXICAL_ONLY,
    val localModelId: String = "gte-small-zh-int8",
    val remoteEmbedding: RemoteEmbeddingConfig = RemoteEmbeddingConfig(),
    val remoteRerank: RemoteRerankConfig = RemoteRerankConfig(),
    val minCosineOverride: Double? = null,
)

interface EmbeddingEngine {
    val profileKey: String
    val minCosineFloor: Double
    suspend fun isAvailable(): Boolean
    suspend fun embed(texts: List<String>, isQuery: Boolean): List<FloatArray>
}

interface RerankEngine {
    suspend fun rerank(query: String, documents: List<String>, topN: Int): Map<Int, Double>
}

data class VectorIndexTarget(
    val ownerType: String,
    val ownerId: String,
    val conversationId: String,
    val canonicalText: String,
)

data class VectorIndexProgress(
    val running: Boolean = false,
    val indexed: Int = 0,
    val total: Int = 0,
    val profileKey: String = "",
    val lastError: String? = null,
)

object VectorQuantizer {
    const val QUANT_INT8_UNIT = "int8_unit"
    const val QUANT_FP16 = "fp16"

    private val WHITESPACE_COLLAPSE_RE = Regex("\\s+")

    fun l2Normalize(vec: FloatArray): FloatArray {
        val n = vec.size
        if (n == 0) return FloatArray(0)
        var sumSq = 0.0
        for (i in 0 until n) {
            val v = vec[i]
            if (v.isFinite()) {
                val d = v.toDouble()
                sumSq += d * d
            }
        }
        val norm = sqrt(sumSq)
        if (norm <= 1e-12 || !norm.isFinite()) {
            return FloatArray(n)
        }
        val invNorm = 1.0 / norm
        val out = FloatArray(n)
        for (i in 0 until n) {
            val v = vec[i]
            out[i] = if (v.isFinite()) (v.toDouble() * invNorm).toFloat() else 0f
        }
        return out
    }

    fun quantizeInt8(unitVec: FloatArray): Pair<ByteArray, Float> {
        val n = unitVec.size
        if (n == 0) return ByteArray(0) to 0f
        var maxAbs = 0f
        for (i in 0 until n) {
            val v = unitVec[i]
            if (v.isFinite()) {
                val a = abs(v)
                if (a > maxAbs) maxAbs = a
            }
        }
        if (maxAbs <= 0f || !maxAbs.isFinite()) {
            return ByteArray(n) to 0f
        }
        val scale = maxAbs / 127f
        val invScale = 1f / scale
        val out = ByteArray(n)
        for (i in 0 until n) {
            val v = unitVec[i]
            out[i] = if (v.isFinite()) {
                (v * invScale).roundToInt().coerceIn(-127, 127).toByte()
            } else {
                0
            }
        }
        return out to scale
    }

    fun dequantizeInt8(
        flatMatrix: ByteArray,
        scale: Float,
        rowOffset: Int = 0,
        dim: Int = flatMatrix.size - rowOffset,
    ): FloatArray {
        if (dim <= 0 || rowOffset < 0 || rowOffset + dim > flatMatrix.size) return FloatArray(0)
        val out = FloatArray(dim)
        for (i in 0 until dim) {
            out[i] = flatMatrix[rowOffset + i].toInt() * scale
        }
        return out
    }

    fun dotProductInt8(
        queryUnit: FloatArray,
        flatMatrix: ByteArray,
        rowOffset: Int,
        dim: Int,
        scale: Float,
    ): Float {
        if (dim <= 0 || scale == 0f || !scale.isFinite()) return 0f
        if (rowOffset < 0 || queryUnit.size < dim || rowOffset + dim > flatMatrix.size) return 0f
        var acc = 0f
        var i = 0
        while (i < dim) {
            acc += queryUnit[i] * flatMatrix[rowOffset + i].toInt()
            i++
        }
        return acc * scale
    }

    fun sha256Hex(text: String): String =
        com.gugachat.app.core.util.HashUtils.sha256Hex(text)

    fun normalizeCanonicalText(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(WHITESPACE_COLLAPSE_RE, " ")
            .trim()
}

class LruQueryEmbeddingCache(
    maxSize: Int = 64,
) {
    private val capacity = maxSize.coerceAtLeast(1)
    private val lock = Any()
    private val map = object : LinkedHashMap<String, FloatArray>(capacity + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > capacity
    }

    fun cacheKey(profileKey: String, queryHash: String): String = "$profileKey|$queryHash"

    fun get(profileKey: String, queryHash: String): FloatArray? =
        getByKey(cacheKey(profileKey, queryHash))

    fun put(profileKey: String, queryHash: String, vector: FloatArray) {
        putByKey(cacheKey(profileKey, queryHash), vector)
    }

    fun getByKey(key: String): FloatArray? = synchronized(lock) {
        map[key]
    }

    fun putByKey(key: String, vector: FloatArray) {
        synchronized(lock) {
            map[key] = vector
        }
    }

    fun invalidateProfile(profileKey: String) {
        val prefix = "$profileKey|"
        synchronized(lock) {
            val iter = map.entries.iterator()
            while (iter.hasNext()) {
                if (iter.next().key.startsWith(prefix)) {
                    iter.remove()
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
        }
    }

    fun size(): Int = synchronized(lock) { map.size }
}

@Serializable
data class RemoteEmbeddingPreset(
    val id: String,
    val providerName: String,
    val protocol: ApiProtocol,
    val defaultBaseUrl: String,
    val modelId: String,
    val defaultDimension: Int,
    val supportsMrl: Boolean = false,
    val mrlDimensions: List<Int> = emptyList(),
    val maxTokens: Int = 8192,
    val recommendedMinCosine: Double = 0.48,
    val isFreeTier: Boolean = false,
    val isLocalLan: Boolean = false,
    val noteZh: String = "",
    val noteEn: String = "",
)

@Serializable
data class RemoteRerankPreset(
    val id: String,
    val providerName: String,
    val defaultBaseUrl: String,
    val modelId: String,
    val useTeiSchema: Boolean = false,
    val isFreeTier: Boolean = false,
    val isLocalLan: Boolean = false,
    val noteZh: String = "",
    val noteEn: String = "",
)

val REMOTE_EMBEDDING_PRESETS: List<RemoteEmbeddingPreset> = listOf(
    RemoteEmbeddingPreset(
        id = "siliconflow_bge_m3",
        providerName = "SiliconFlow",
        protocol = ApiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        modelId = "BAAI/bge-m3",
        defaultDimension = 1024,
        supportsMrl = false,
        maxTokens = 8192,
        recommendedMinCosine = 0.50,
        isFreeTier = true,
        noteZh = "硅基流动免费额度，8K 上下文，中英双语检索首选",
        noteEn = "SiliconFlow free tier, 8K context, top bilingual retrieval pick",
    ),
    RemoteEmbeddingPreset(
        id = "siliconflow_qwen3_embedding_06b",
        providerName = "SiliconFlow",
        protocol = ApiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        modelId = "Qwen/Qwen3-Embedding-0.6B",
        defaultDimension = 1024,
        supportsMrl = true,
        mrlDimensions = listOf(256, 512, 1024),
        maxTokens = 32768,
        recommendedMinCosine = 0.48,
        noteZh = "支持 MRL 维度裁剪与 32K 长上下文",
        noteEn = "Supports MRL dimensionality reduction and 32K context",
    ),
    RemoteEmbeddingPreset(
        id = "gemini_embedding_001",
        providerName = "Google Gemini",
        protocol = ApiProtocol.GEMINI,
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        modelId = "gemini-embedding-001",
        defaultDimension = 768,
        supportsMrl = true,
        mrlDimensions = listOf(256, 512, 768, 1536, 3072),
        maxTokens = 2048,
        recommendedMinCosine = 0.50,
        isFreeTier = true,
        noteZh = "原生支持查询/文档非对称任务类型与 MRL 维度",
        noteEn = "Native query/document task types with MRL outputDimensionality",
    ),
    RemoteEmbeddingPreset(
        id = "dashscope_text_embedding_v4",
        providerName = "Alibaba DashScope",
        protocol = ApiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        modelId = "text-embedding-v4",
        defaultDimension = 1024,
        supportsMrl = true,
        mrlDimensions = listOf(256, 512, 1024, 1536, 2048),
        maxTokens = 8192,
        recommendedMinCosine = 0.48,
        noteZh = "阿里百炼 Qwen3 架构向量模型，国内低延迟稳定",
        noteEn = "DashScope Qwen3-lineage embedding with MRL support",
    ),
    RemoteEmbeddingPreset(
        id = "zhipu_embedding_3",
        providerName = "Zhipu AI",
        protocol = ApiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        modelId = "embedding-3",
        defaultDimension = 1024,
        supportsMrl = true,
        mrlDimensions = listOf(256, 512, 1024, 2048),
        maxTokens = 3072,
        recommendedMinCosine = 0.48,
        noteZh = "智谱 OpenAI 兼容 /v4/embeddings，支持自定义维度",
        noteEn = "Zhipu OpenAI-compatible /v4/embeddings with custom dimensions",
    ),
    RemoteEmbeddingPreset(
        id = "openai_text_embedding_3_small",
        providerName = "OpenAI",
        protocol = ApiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://api.openai.com/v1",
        modelId = "text-embedding-3-small",
        defaultDimension = 512,
        supportsMrl = true,
        mrlDimensions = listOf(256, 512, 1024, 1536),
        maxTokens = 8192,
        recommendedMinCosine = 0.45,
        noteZh = "OpenAI 高性价比向量模型，推荐 512 维",
        noteEn = "Cost-effective OpenAI embeddings, 512d recommended",
    ),
    RemoteEmbeddingPreset(
        id = "jina_embeddings_v3",
        providerName = "Jina AI",
        protocol = ApiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://api.jina.ai/v1",
        modelId = "jina-embeddings-v3",
        defaultDimension = 1024,
        supportsMrl = true,
        mrlDimensions = listOf(256, 512, 1024),
        maxTokens = 8192,
        recommendedMinCosine = 0.48,
        isFreeTier = true,
        noteZh = "多语言长文本检索，含千万级入门免费额度",
        noteEn = "Multilingual 8K embeddings with generous starter quota",
    ),
    RemoteEmbeddingPreset(
        id = "ollama_bge_m3",
        providerName = "Ollama (LAN)",
        protocol = ApiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "http://127.0.0.1:11434/v1",
        modelId = "bge-m3",
        defaultDimension = 1024,
        supportsMrl = false,
        maxTokens = 8192,
        recommendedMinCosine = 0.50,
        isFreeTier = true,
        isLocalLan = true,
        noteZh = "局域网/本机自托管 Ollama，无需 API Key",
        noteEn = "Self-hosted local/LAN Ollama, no API key required",
    ),
    RemoteEmbeddingPreset(
        id = "ollama_qwen3_embedding",
        providerName = "Ollama (LAN)",
        protocol = ApiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "http://127.0.0.1:11434/v1",
        modelId = "qwen3-embedding",
        defaultDimension = 1024,
        supportsMrl = true,
        mrlDimensions = listOf(256, 512, 1024),
        maxTokens = 32768,
        recommendedMinCosine = 0.48,
        isFreeTier = true,
        isLocalLan = true,
        noteZh = "局域网自托管 Qwen3 向量模型，无需 API Key",
        noteEn = "Self-hosted local/LAN Qwen3 embeddings, no API key required",
    ),
)

val REMOTE_RERANK_PRESETS: List<RemoteRerankPreset> = listOf(
    RemoteRerankPreset(
        id = "siliconflow_bge_reranker_v2_m3",
        providerName = "SiliconFlow",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        modelId = "BAAI/bge-reranker-v2-m3",
        isFreeTier = true,
        noteZh = "硅基流动免费中文精排模型，与 bge-m3 绝配",
        noteEn = "SiliconFlow free bilingual reranker, pairs cleanly with bge-m3",
    ),
    RemoteRerankPreset(
        id = "siliconflow_qwen3_reranker_4b",
        providerName = "SiliconFlow",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        modelId = "Qwen/Qwen3-Reranker-4B",
        noteZh = "Qwen3 高精度精排模型，适合复杂剧情推理召回",
        noteEn = "High-precision Qwen3 reranker for nuanced narrative recall",
    ),
    RemoteRerankPreset(
        id = "dashscope_gte_rerank_v2",
        providerName = "Alibaba DashScope",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-api/v1",
        modelId = "gte-rerank-v2",
        noteZh = "阿里百炼中文精排服务，稳定低延迟",
        noteEn = "Alibaba DashScope multilingual reranker",
    ),
    RemoteRerankPreset(
        id = "jina_reranker_v2_multilingual",
        providerName = "Jina AI",
        defaultBaseUrl = "https://api.jina.ai/v1",
        modelId = "jina-reranker-v2-base-multilingual",
        isFreeTier = true,
        noteZh = "Jina 多语言精排，标准 Cohere 协议",
        noteEn = "Jina multilingual reranker using Cohere wire format",
    ),
)

fun recommendedMinCosineForRemoteModel(modelId: String): Double {
    val clean = modelId.trim()
    REMOTE_EMBEDDING_PRESETS.firstOrNull {
        it.modelId.equals(clean, ignoreCase = true)
    }?.let { return it.recommendedMinCosine }

    val lower = clean.lowercase()
    return when {
        lower.contains("bge-small") || lower.contains("gte-small") -> 0.58
        lower.contains("bge-m3") || lower.contains("gemini-embedding") -> 0.50
        lower.contains("text-embedding-3") -> 0.45
        lower.contains("qwen3-embedding") || lower.contains("text-embedding-v4") || lower.contains("jina-embeddings") -> 0.48
        else -> 0.48
    }
}
