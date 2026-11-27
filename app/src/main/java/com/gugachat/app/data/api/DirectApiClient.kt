package com.gugachat.app.data.api

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.domain.chat.EmbeddingEngine
import com.gugachat.app.domain.chat.RemoteEmbeddingConfig
import com.gugachat.app.domain.chat.RemoteRerankConfig
import com.gugachat.app.domain.chat.RerankEngine
import com.gugachat.app.domain.chat.recommendedMinCosineForRemoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

data class EmbeddingConnectionTestResult(
    val dimension: Int,
    val latencyMs: Long,
    val modelId: String,
)

class DirectApiClient(
    private val standardClient: OkHttpClient,
    private val streamingClient: OkHttpClient,
    private val parseHttpError: (Int, String) -> ApiException,
    private val sseReadTimeoutMs: Long = SseRetryPolicy.DEFAULT_SSE_READ_TIMEOUT_MS,
) {
    private val sseStreamingClient: OkHttpClient = streamingClient.newBuilder()
        .readTimeout(sseReadTimeoutMs.coerceAtLeast(100L), TimeUnit.MILLISECONDS)
        .build()
    suspend fun testAndFetchModels(provider: ApiProviderConfig): List<OnlineChatModelDto> {
        validateProvider(provider)
        val url = modelsEndpoint(provider)
        val req = buildGetRequest(provider, url)
        val resp = standardClient.awaitCall(req)
        val fetched = withContext(Dispatchers.IO) {
            resp.use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    throw parseHttpError(r.code, raw)
                }
                DirectProtocolCodec.parseModelsJson(provider.protocol, raw)
            }
        }
        val custom = provider.customModels.map { DirectProtocolCodec.inferChatModelMeta(it) }
        return (custom + fetched).distinctBy { it.id }.ifEmpty {
            DirectProtocolCodec.defaultChatModels(provider.protocol)
        }
    }

    suspend fun listChatModels(provider: ApiProviderConfig): List<OnlineChatModelDto> {
        validateProvider(provider)
        val custom = provider.customModels.map { DirectProtocolCodec.inferChatModelMeta(it) }
        val fetched = runCatching {
            val url = modelsEndpoint(provider)
            val req = buildGetRequest(provider, url)
            val resp = standardClient.awaitCall(req)
            withContext(Dispatchers.IO) {
                resp.use { r ->
                    if (!r.isSuccessful) emptyList()
                    else DirectProtocolCodec.parseModelsJson(provider.protocol, r.body?.string().orEmpty())
                }
            }
        }.getOrDefault(emptyList())
        return (custom + fetched).distinctBy { it.id }.ifEmpty {
            DirectProtocolCodec.defaultChatModels(provider.protocol)
        }
    }

    fun listImageModels(provider: ApiProviderConfig): List<OnlineImageModelDto> =
        DirectProtocolCodec.defaultImageModels(
            protocol = provider.protocol,
            customModels = provider.customModels,
            baseUrl = provider.baseUrl,
        )

    fun supportsImageGeneration(provider: ApiProviderConfig): Boolean =
        DirectProtocolCodec.supportsImageGeneration(
            protocol = provider.protocol,
            baseUrl = provider.baseUrl,
            customModels = provider.customModels,
        )

    private suspend fun <T> postDirectJson(
        provider: ApiProviderConfig,
        url: String,
        payloadJson: String,
        client: OkHttpClient = streamingClient,
        parser: (raw: String, code: Int) -> T,
    ): T {
        val req = applyAuthHeaders(
            Request.Builder()
                .url(url)
                .post(payloadJson.toRequestBody(ApiClient.JSON_MEDIA_TYPE)),
            provider,
        ).header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        val resp = client.awaitCall(req)
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    throw parseHttpError(r.code, raw)
                }
                parser(raw, r.code)
            }
        }
    }

    suspend fun streamChat(
        provider: ApiProviderConfig,
        request: OnlineChatCompletionRequestDto,
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
        onUsage: (ChatTokenUsageDto) -> Unit = {},
    ): CompletionResult {
        validateProvider(provider)
        val (url, payload) = buildChatUrlAndPayload(provider, request, stream = true)
        val req = applyAuthHeaders(
            Request.Builder().url(url).post(payload.toRequestBody(ApiClient.JSON_MEDIA_TYPE)),
            provider,
        ).header("Accept", "text/event-stream")
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache")
            .header("Content-Type", "application/json")
            .build()

        val response = sseStreamingClient.awaitCall(req)
        return SseStreamConsumer.consumeResponse(
            response = response,
            parseHttpError = parseHttpError,
            parseSingleJson = { raw, code -> parseSingleResponse(provider.protocol, raw, code) },
            parseSseLine = { currentEvent, rawData ->
                when (provider.protocol) {
                    ApiProtocol.OPENAI_CHAT -> SseParser.parseDataPayload(rawData)
                    ApiProtocol.OPENAI_RESPONSES -> DirectProtocolCodec.parseOpenAIResponsesSse(currentEvent, rawData)
                    ApiProtocol.ANTHROPIC -> DirectProtocolCodec.parseAnthropicSse(currentEvent, rawData)
                    ApiProtocol.GEMINI -> DirectProtocolCodec.parseGeminiSseData(rawData)
                }
            },
            mergeUsage = { prev, inc -> mergeUsageForProtocol(provider.protocol, prev, inc) },
            isPrematureEof = { sawDone, sawFinishReason, content, reasoning ->
                when (provider.protocol) {
                    ApiProtocol.GEMINI -> !sawDone && !sawFinishReason && content.isEmpty() && reasoning.isEmpty()
                    ApiProtocol.OPENAI_CHAT,
                    ApiProtocol.OPENAI_RESPONSES,
                    ApiProtocol.ANTHROPIC -> !sawDone && !sawFinishReason
                }
            },
            onDelta = onDelta,
            onReasoning = onReasoning,
            onUsage = onUsage,
        )
    }

    suspend fun completeChatOnce(
        provider: ApiProviderConfig,
        request: OnlineChatCompletionRequestDto,
    ): CompletionResult {
        validateProvider(provider)
        val (url, payload) = buildChatUrlAndPayload(provider, request, stream = false)
        return postDirectJson(provider, url, payload) { raw, code ->
            parseSingleResponse(provider.protocol, raw, code)
        }
    }

    suspend fun generateImages(
        provider: ApiProviderConfig,
        input: OnlineImageSubmitInput,
        sourceImageBytes: ByteArray? = null,
    ): List<Pair<ByteArray, String>> = withContext(Dispatchers.IO) {
        validateProvider(provider)
        val decoded: List<DecodedDirectImage> = when (provider.protocol) {
            ApiProtocol.OPENAI_CHAT,
            ApiProtocol.OPENAI_RESPONSES -> {
                val editSource = sourceImageBytes ?: input.referenceImages.firstOrNull()?.bytes
                if (editSource != null || input.mask != null) {
                    val imgBytes = editSource
                        ?: throw ApiException(400, "MISSING_IMAGE", "Source image required for edit")
                    val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("model", input.model)
                        .addFormDataPart("prompt", input.prompt)
                        .addFormDataPart("n", input.count.coerceIn(1, 4).toString())
                        .addFormDataPart("size", DirectProtocolCodec.mapOpenAIImageSize(input.model, input.aspectRatio))
                        .addFormDataPart("image", "image.png", imgBytes.toRequestBody("image/png".toMediaType()))
                    input.mask?.let { m ->
                        mp.addFormDataPart("mask", m.fileName, m.bytes.toRequestBody(m.mimeType.toMediaType()))
                    }
                    val req = applyAuthHeaders(
                        Request.Builder()
                            .url(DirectProtocolCodec.openaiImagesEditUrl(provider.baseUrl))
                            .post(mp.build()),
                        provider,
                    ).header("Accept", "application/json").build()
                    streamingClient.awaitCall(req).use { r ->
                        val raw = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw parseHttpError(r.code, raw)
                        DirectProtocolCodec.parseOpenAIImagesJson(raw, r.code)
                    }
                } else {
                    val payload = DirectProtocolCodec.buildOpenAIImageGenerationJson(
                        model = input.model,
                        prompt = input.prompt,
                        count = input.count,
                        aspectRatio = input.aspectRatio,
                    )
                    postDirectJson(
                        provider = provider,
                        url = DirectProtocolCodec.openaiImagesGenUrl(provider.baseUrl),
                        payloadJson = payload,
                        parser = DirectProtocolCodec::parseOpenAIImagesJson,
                    )
                }
            }

            ApiProtocol.GEMINI -> {
                if (input.model.lowercase().startsWith("imagen-")) {
                    val payload = DirectProtocolCodec.buildGeminiImagenPredictJson(
                        prompt = input.prompt,
                        count = input.count,
                        aspectRatio = input.aspectRatio,
                    )
                    postDirectJson(
                        provider = provider,
                        url = DirectProtocolCodec.geminiImagenPredictUrl(provider.baseUrl, input.model),
                        payloadJson = payload,
                        parser = DirectProtocolCodec::parseGeminiImagesJson,
                    )
                } else {
                    val inlineImages = buildList {
                        if (sourceImageBytes != null) add(DirectInlineImage("image/png", sourceImageBytes))
                        input.referenceImages.forEach { add(DirectInlineImage(it.mimeType, it.bytes)) }
                        input.mask?.let { add(DirectInlineImage(it.mimeType, it.bytes)) }
                    }
                    val payload = DirectProtocolCodec.buildGeminiImageRequestJson(
                        prompt = input.prompt,
                        aspectRatio = input.aspectRatio,
                        inlineImages = inlineImages,
                    )
                    postDirectJson(
                        provider = provider,
                        url = DirectProtocolCodec.geminiChatUrl(provider.baseUrl, input.model, stream = false),
                        payloadJson = payload,
                        parser = DirectProtocolCodec::parseGeminiImagesJson,
                    )
                }
            }

            ApiProtocol.ANTHROPIC -> throw ApiException(400, "UNSUPPORTED", "Anthropic protocol does not support image generation")
        }

        val resolved = mutableListOf<Pair<ByteArray, String>>()
        for (item in decoded) {
            if (item.bytes != null) {
                resolved += item.bytes to item.mimeType
            } else if (!item.url.isNullOrBlank()) {
                val req = Request.Builder().url(item.url).get().build()
                streamingClient.awaitCall(req).use { imgResp ->
                    if (!imgResp.isSuccessful) {
                        throw parseHttpError(imgResp.code, "Failed to download generated image")
                    }
                    val bytes = imgResp.body?.bytes() ?: ByteArray(0)
                    val mime = imgResp.header("Content-Type") ?: item.mimeType
                    if (bytes.isNotEmpty()) resolved += bytes to mime
                }
            }
        }
        if (resolved.isEmpty()) {
            throw ApiException(502, "EMPTY_IMAGE_RESULT", "No image was returned by the provider")
        }
        resolved
    }

    suspend fun embedTexts(
        provider: ApiProviderConfig,
        modelId: String,
        texts: List<String>,
        isQuery: Boolean = false,
        dimensions: Int? = null,
        timeoutMs: Long = 15_000L,
    ): List<FloatArray> {
        validateProvider(provider)
        val cleanModel = modelId.trim()
        if (cleanModel.isEmpty()) {
            throw ApiException(400, "NO_EMBEDDING_MODEL", "Embedding model ID is empty")
        }
        if (texts.isEmpty()) return emptyList()

        val (url, payload) = when (provider.protocol) {
            ApiProtocol.OPENAI_CHAT,
            ApiProtocol.OPENAI_RESPONSES -> DirectProtocolCodec.openaiEmbeddingsUrl(provider.baseUrl) to
                DirectProtocolCodec.buildOpenAIEmbeddingRequestJson(
                    modelId = cleanModel,
                    texts = texts,
                    dimensions = dimensions,
                )

            ApiProtocol.GEMINI -> if (texts.size == 1) {
                DirectProtocolCodec.geminiEmbedContentUrl(provider.baseUrl, cleanModel) to
                    DirectProtocolCodec.buildGeminiEmbedContentRequestJson(
                        modelId = cleanModel,
                        text = texts[0],
                        isQuery = isQuery,
                        outputDimensionality = dimensions,
                    )
            } else {
                DirectProtocolCodec.geminiBatchEmbedContentsUrl(provider.baseUrl, cleanModel) to
                    DirectProtocolCodec.buildGeminiBatchEmbedContentsRequestJson(
                        modelId = cleanModel,
                        texts = texts,
                        isQuery = isQuery,
                        outputDimensionality = dimensions,
                    )
            }

            ApiProtocol.ANTHROPIC -> throw ApiException(
                400,
                "UNSUPPORTED",
                "Anthropic protocol does not support embeddings",
            )
        }

        val vectors = postDirectJson(
            provider = provider,
            url = url,
            payloadJson = payload,
            client = clientWithTimeout(timeoutMs),
        ) { raw, code ->
            when (provider.protocol) {
                ApiProtocol.GEMINI -> DirectProtocolCodec.parseGeminiEmbeddingsJson(raw, code)
                ApiProtocol.OPENAI_CHAT,
                ApiProtocol.OPENAI_RESPONSES,
                ApiProtocol.ANTHROPIC -> DirectProtocolCodec.parseOpenAIEmbeddingsJson(raw, code)
            }
        }
        if (vectors.size != texts.size) {
            throw ApiException(
                502,
                "EMBEDDING_COUNT_MISMATCH",
                "Expected ${texts.size} embeddings but received ${vectors.size}",
            )
        }
        return vectors
    }

    suspend fun testEmbeddingConnection(
        provider: ApiProviderConfig,
        modelId: String,
        dimensions: Int? = null,
        timeoutMs: Long = 10_000L,
    ): EmbeddingConnectionTestResult {
        val startNanos = System.nanoTime()
        val vectors = embedTexts(
            provider = provider,
            modelId = modelId,
            texts = listOf("GuGuChat embedding connectivity test"),
            isQuery = true,
            dimensions = dimensions,
            timeoutMs = timeoutMs,
        )
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos).coerceAtLeast(0L)
        val dim = vectors.firstOrNull()?.size ?: 0
        if (dim <= 0) {
            throw ApiException(502, "EMPTY_EMBEDDING", "Embedding test returned an empty vector")
        }
        return EmbeddingConnectionTestResult(
            dimension = dim,
            latencyMs = elapsedMs,
            modelId = modelId.trim(),
        )
    }

    suspend fun rerankDocuments(
        provider: ApiProviderConfig,
        modelId: String,
        query: String,
        documents: List<String>,
        topN: Int = documents.size,
        useTeiSchema: Boolean = false,
        timeoutMs: Long = 10_000L,
    ): Map<Int, Double> {
        validateProvider(provider)
        if (documents.isEmpty() || query.isBlank()) return emptyMap()
        if (provider.protocol == ApiProtocol.ANTHROPIC || provider.protocol == ApiProtocol.GEMINI) {
            throw ApiException(
                400,
                "UNSUPPORTED",
                "${provider.protocol.displayName} protocol does not support /rerank",
            )
        }
        val cleanModel = modelId.trim()
        if (!useTeiSchema && cleanModel.isEmpty()) {
            throw ApiException(400, "NO_RERANK_MODEL", "Rerank model ID is empty")
        }

        val url = DirectProtocolCodec.openaiRerankUrl(provider.baseUrl)
        val payload = DirectProtocolCodec.buildRerankRequestJson(
            modelId = cleanModel,
            query = query,
            documents = documents,
            topN = topN,
            useTeiSchema = useTeiSchema,
        )
        val parsed = postDirectJson(
            provider = provider,
            url = url,
            payloadJson = payload,
            client = clientWithTimeout(timeoutMs),
            parser = DirectProtocolCodec::parseRerankResponseJson,
        )
        val filtered = parsed.entries
            .filter { it.key in documents.indices }
            .sortedByDescending { it.value }
        val limited = if (topN > 0) filtered.take(topN) else filtered
        return LinkedHashMap<Int, Double>(limited.size).apply {
            for ((idx, score) in limited) {
                put(idx, score)
            }
        }
    }

    suspend fun fetchEmbeddingAndRerankModels(provider: ApiProviderConfig): List<DiscoveredRetrievalModel> {
        validateProvider(provider)
        val url = modelsEndpoint(provider)
        val req = buildGetRequest(provider, url)
        val resp = standardClient.awaitCall(req)
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    throw parseHttpError(r.code, raw)
                }
                DirectProtocolCodec.parseEmbeddingAndRerankModelsJson(provider.protocol, raw)
            }
        }
    }

    private fun clientWithTimeout(timeoutMs: Long): OkHttpClient {
        if (timeoutMs <= 0L) return standardClient
        val clamped = timeoutMs.coerceAtLeast(100L)
        return standardClient.newBuilder()
            .connectTimeout(clamped, TimeUnit.MILLISECONDS)
            .readTimeout(clamped, TimeUnit.MILLISECONDS)
            .writeTimeout(clamped, TimeUnit.MILLISECONDS)
            .callTimeout(clamped, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun mergeUsageForProtocol(
        protocol: ApiProtocol,
        previous: ChatTokenUsageDto?,
        incoming: ChatTokenUsageDto?,
    ): ChatTokenUsageDto? {
        val merged = SseParser.mergeUsage(previous, incoming) ?: return null
        if (protocol == ApiProtocol.ANTHROPIC) {
            val totalInput = DirectProtocolCodec.totalInputTokens(protocol, merged)
            val completion = merged.completion_tokens
            if (totalInput != null && completion != null) {
                return merged.copy(total_tokens = totalInput + completion)
            }
        }
        return merged
    }

    private fun validateProvider(provider: ApiProviderConfig) {
        if (provider.baseUrl.isBlank()) {
            throw ApiException(0, "NO_SERVER_URL", "Custom Base URL is empty")
        }
        if (provider.apiKey.isBlank() && !isLocalOrLanHost(provider.baseUrl)) {
            throw ApiException(401, "NO_API_KEY", "API Key is required")
        }
    }

    private fun modelsEndpoint(provider: ApiProviderConfig): String = when (provider.protocol) {
        ApiProtocol.OPENAI_CHAT,
        ApiProtocol.OPENAI_RESPONSES -> DirectProtocolCodec.openaiModelsUrl(provider.baseUrl)
        ApiProtocol.ANTHROPIC -> DirectProtocolCodec.anthropicModelsUrl(provider.baseUrl)
        ApiProtocol.GEMINI -> DirectProtocolCodec.geminiModelsUrl(provider.baseUrl)
    }

    private fun buildChatUrlAndPayload(
        provider: ApiProviderConfig,
        request: OnlineChatCompletionRequestDto,
        stream: Boolean,
    ): Pair<String, String> = when (provider.protocol) {
        ApiProtocol.OPENAI_CHAT -> DirectProtocolCodec.openaiChatUrl(provider.baseUrl) to
            DirectProtocolCodec.buildOpenAIChatRequestJson(request, stream, provider.baseUrl)
        ApiProtocol.OPENAI_RESPONSES -> DirectProtocolCodec.openaiResponsesUrl(provider.baseUrl) to
            DirectProtocolCodec.buildOpenAIResponsesRequestJson(request, stream)
        ApiProtocol.ANTHROPIC -> DirectProtocolCodec.anthropicMessagesUrl(provider.baseUrl) to
            DirectProtocolCodec.buildAnthropicRequestJson(request, stream)
        ApiProtocol.GEMINI -> DirectProtocolCodec.geminiChatUrl(provider.baseUrl, request.model, stream) to
            DirectProtocolCodec.buildGeminiRequestJson(request)
    }

    private fun parseSingleResponse(protocol: ApiProtocol, raw: String, statusCode: Int): CompletionResult = when (protocol) {
        ApiProtocol.OPENAI_CHAT -> SseParser.parseSingleCompletionJson(raw, statusCode)
        ApiProtocol.OPENAI_RESPONSES -> DirectProtocolCodec.parseOpenAIResponsesSingleJson(raw, statusCode)
        ApiProtocol.ANTHROPIC -> DirectProtocolCodec.parseAnthropicSingleJson(raw, statusCode)
        ApiProtocol.GEMINI -> DirectProtocolCodec.parseGeminiSingleJson(raw, statusCode)
    }

    private fun buildGetRequest(provider: ApiProviderConfig, url: String): Request =
        applyAuthHeaders(Request.Builder().url(url).get(), provider)
            .header("Accept", "application/json")
            .build()

    private fun applyAuthHeaders(builder: Request.Builder, provider: ApiProviderConfig): Request.Builder {
        val key = provider.apiKey.trim()
        return when (provider.protocol) {
            ApiProtocol.OPENAI_CHAT,
            ApiProtocol.OPENAI_RESPONSES -> if (key.isNotEmpty()) {
                builder.header("Authorization", "Bearer $key")
            } else {
                builder
            }
            ApiProtocol.ANTHROPIC -> {
                builder.header("anthropic-version", "2023-06-01")
                if (key.isNotEmpty()) builder.header("x-api-key", key) else builder
            }
            ApiProtocol.GEMINI -> if (key.isNotEmpty()) {
                builder.header("x-goog-api-key", key)
            } else {
                builder
            }
        }
    }

    companion object {
        fun isLocalOrLanHost(baseUrl: String): Boolean {
            val normalized = DirectProtocolCodec.normalizeBaseOrigin(baseUrl)
            val host = runCatching {
                URI(normalized.ifEmpty { baseUrl.trim() }).host
            }.getOrNull()?.lowercase()?.trim('[', ']') ?: return false
            if (host.isEmpty()) return false
            if (host == "localhost" || host == "::1" || host == "0:0:0:0:0:0:0:1" || host.endsWith(".local")) {
                return true
            }
            val octets = host.split('.')
            if (octets.size == 4 && octets.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
                val nums = octets.map { it.toIntOrNull() ?: return false }
                if (nums.any { it !in 0..255 }) return false
                val a = nums[0]
                val b = nums[1]
                return a == 127 ||
                    a == 10 ||
                    (a == 192 && b == 168) ||
                    (a == 172 && b in 16..31)
            }
            return false
        }

        private fun resolveConfiguredProvider(
            providerId: String,
            customBaseUrl: String,
            customApiKey: String,
            protocol: ApiProtocol,
            fallbackId: String,
            providerLookup: (String) -> ApiProviderConfig?,
        ): ApiProviderConfig? {
            val saved = providerId.trim().takeIf { it.isNotEmpty() }?.let(providerLookup)
            if (saved != null) return saved
            val baseUrl = customBaseUrl.trim()
            if (baseUrl.isEmpty()) return null
            return ApiProviderConfig(
                id = providerId.ifBlank { fallbackId },
                protocol = protocol,
                baseUrl = baseUrl,
                apiKey = customApiKey.trim(),
            )
        }

        fun resolveRemoteEmbeddingProvider(
            config: RemoteEmbeddingConfig,
            providerLookup: (String) -> ApiProviderConfig? = { null },
        ): ApiProviderConfig? = resolveConfiguredProvider(
            providerId = config.providerId,
            customBaseUrl = config.customBaseUrl,
            customApiKey = config.customApiKey,
            protocol = config.protocol,
            fallbackId = "custom_embed",
            providerLookup = providerLookup,
        )

        fun resolveRemoteRerankProvider(
            config: RemoteRerankConfig,
            providerLookup: (String) -> ApiProviderConfig? = { null },
        ): ApiProviderConfig? = resolveConfiguredProvider(
            providerId = config.providerId,
            customBaseUrl = config.customBaseUrl,
            customApiKey = config.customApiKey,
            protocol = ApiProtocol.OPENAI_CHAT,
            fallbackId = "custom_rerank",
            providerLookup = providerLookup,
        )
    }
}

class RemoteApiEmbeddingEngine(
    private val directApiClient: DirectApiClient,
    private val provider: ApiProviderConfig,
    private val config: RemoteEmbeddingConfig,
    private val requestTimeoutMs: Long = 15_000L,
) : EmbeddingEngine {
    override val profileKey: String =
        "remote|${config.providerId.ifBlank { provider.id }}|${config.modelId.trim()}|${config.dimensions ?: 0}"

    override val minCosineFloor: Double = recommendedMinCosineForRemoteModel(config.modelId)

    override suspend fun isAvailable(): Boolean =
        provider.baseUrl.isNotBlank() &&
            config.modelId.isNotBlank() &&
            provider.protocol != ApiProtocol.ANTHROPIC &&
            (provider.apiKey.isNotBlank() || DirectApiClient.isLocalOrLanHost(provider.baseUrl))

    override suspend fun embed(texts: List<String>, isQuery: Boolean): List<FloatArray> =
        directApiClient.embedTexts(
            provider = provider,
            modelId = config.modelId.trim(),
            texts = texts,
            isQuery = isQuery,
            dimensions = config.dimensions,
            timeoutMs = requestTimeoutMs,
        )
}

class RemoteApiRerankEngine(
    private val directApiClient: DirectApiClient,
    private val provider: ApiProviderConfig,
    private val config: RemoteRerankConfig,
    private val requestTimeoutMs: Long = 10_000L,
) : RerankEngine {
    override suspend fun rerank(query: String, documents: List<String>, topN: Int): Map<Int, Double> {
        if (!config.enabled || documents.isEmpty() || query.isBlank()) return emptyMap()
        return directApiClient.rerankDocuments(
            provider = provider,
            modelId = config.modelId.trim(),
            query = query,
            documents = documents,
            topN = topN,
            useTeiSchema = config.useTeiSchema,
            timeoutMs = requestTimeoutMs,
        )
    }
}
