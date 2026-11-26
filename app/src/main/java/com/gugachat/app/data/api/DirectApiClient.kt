package com.gugachat.app.data.api

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

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
        DirectProtocolCodec.defaultImageModels(provider.protocol, provider.customModels)

    suspend fun streamChat(
        provider: ApiProviderConfig,
        request: OnlineChatCompletionRequestDto,
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
        onUsage: (ChatTokenUsageDto) -> Unit = {},
    ): CompletionResult {
        validateProvider(provider)
        val (url, payload) = buildChatUrlAndPayload(provider, request, stream = true)
        val reqBuilder = applyAuthHeaders(
            Request.Builder().url(url).post(payload.toRequestBody(ApiClient.JSON_MEDIA_TYPE)),
            provider,
        ).header("Accept", "text/event-stream")
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache")
            .header("Content-Type", "application/json")

        val response = sseStreamingClient.awaitCall(reqBuilder.build())
        return withContext(Dispatchers.IO) {
            response.use { resp ->
                if (!resp.isSuccessful) {
                    throw parseHttpError(resp.code, resp.body?.string().orEmpty())
                }
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                val body = resp.body ?: throw ApiException(resp.code, "EMPTY_BODY", "Streaming response body unavailable")

                if (contentType.contains("application/json")) {
                    val raw = body.string()
                    val single = parseSingleResponse(provider.protocol, raw, resp.code)
                    if (single.reasoning.isNotEmpty()) onReasoning(single.reasoning)
                    if (single.content.isNotEmpty()) onDelta(single.content)
                    if (single.usage != null) onUsage(single.usage)
                    return@use single
                }

                val source = body.source()
                val contentBuf = StringBuilder()
                val reasoningBuf = StringBuilder()
                var lastUsage: ChatTokenUsageDto? = null
                var currentEvent: String? = null
                var sawDone = false
                var sawFinishReason = false
                val ctx = currentCoroutineContext()
                val cancelHandle = ctx.job.invokeOnCompletion { cause ->
                    if (cause != null) runCatching { resp.close() }
                }

                try {
                    while (true) {
                        ctx.ensureActive()
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) {
                            currentEvent = null
                            continue
                        }
                        if (line.startsWith("event:")) {
                            currentEvent = line.removePrefix("event:").trim()
                            continue
                        }
                        if (line.startsWith("data:")) {
                            val rawData = line.removePrefix("data:")
                            val outcome = when (provider.protocol) {
                                ApiProtocol.OPENAI_CHAT -> SseParser.parseDataPayload(rawData)
                                ApiProtocol.OPENAI_RESPONSES -> DirectProtocolCodec.parseOpenAIResponsesSse(currentEvent, rawData)
                                ApiProtocol.ANTHROPIC -> DirectProtocolCodec.parseAnthropicSse(currentEvent, rawData)
                                ApiProtocol.GEMINI -> DirectProtocolCodec.parseGeminiSseData(rawData)
                            } ?: continue

                            if (!outcome.finishReason.isNullOrBlank()) {
                                sawFinishReason = true
                            }
                            if (outcome.usage != null) {
                                lastUsage = SseParser.mergeUsage(lastUsage, outcome.usage)
                                lastUsage?.let(onUsage)
                            }
                            if (outcome.deltaReasoning.isNotEmpty()) {
                                reasoningBuf.append(outcome.deltaReasoning)
                                onReasoning(outcome.deltaReasoning)
                            }
                            if (outcome.deltaText.isNotEmpty()) {
                                contentBuf.append(outcome.deltaText)
                                onDelta(outcome.deltaText)
                            }
                            if (outcome.done) {
                                sawDone = true
                                break
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    throw e
                } catch (e: SocketTimeoutException) {
                    ctx.ensureActive()
                    throw ApiException(504, "STREAM_TIMEOUT", e.message ?: "SSE stream timed out")
                } catch (e: InterruptedIOException) {
                    ctx.ensureActive()
                    throw ApiException(504, "STREAM_TIMEOUT", e.message ?: "SSE stream timed out")
                } catch (e: Exception) {
                    ctx.ensureActive()
                    throw ApiException(502, "STREAM_READ_ERROR", e.message ?: "Failed to read stream")
                } finally {
                    cancelHandle.dispose()
                }

                val isPrematureEof = when (provider.protocol) {
                    ApiProtocol.GEMINI -> !sawDone && !sawFinishReason && contentBuf.isEmpty() && reasoningBuf.isEmpty()
                    ApiProtocol.OPENAI_CHAT,
                    ApiProtocol.OPENAI_RESPONSES,
                    ApiProtocol.ANTHROPIC -> !sawDone && !sawFinishReason
                }
                if (isPrematureEof) {
                    throw ApiException(502, "STREAM_UNEXPECTED_EOF", "SSE stream interrupted: unexpected EOF")
                }

                CompletionResult(
                    content = contentBuf.toString(),
                    reasoning = reasoningBuf.toString(),
                    usage = lastUsage,
                )
            }
        }
    }

    suspend fun completeChatOnce(
        provider: ApiProviderConfig,
        request: OnlineChatCompletionRequestDto,
    ): CompletionResult {
        validateProvider(provider)
        val (url, payload) = buildChatUrlAndPayload(provider, request, stream = false)
        val reqBuilder = applyAuthHeaders(
            Request.Builder().url(url).post(payload.toRequestBody(ApiClient.JSON_MEDIA_TYPE)),
            provider,
        ).header("Accept", "application/json")
            .header("Content-Type", "application/json")

        val resp = streamingClient.awaitCall(reqBuilder.build())
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    throw parseHttpError(r.code, raw)
                }
                parseSingleResponse(provider.protocol, raw, r.code)
            }
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
                    val req = applyAuthHeaders(
                        Request.Builder()
                            .url(DirectProtocolCodec.openaiImagesGenUrl(provider.baseUrl))
                            .post(payload.toRequestBody(ApiClient.JSON_MEDIA_TYPE)),
                        provider,
                    ).header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .build()
                    streamingClient.awaitCall(req).use { r ->
                        val raw = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw parseHttpError(r.code, raw)
                        DirectProtocolCodec.parseOpenAIImagesJson(raw, r.code)
                    }
                }
            }

            ApiProtocol.GEMINI -> {
                if (input.model.lowercase().startsWith("imagen-")) {
                    val payload = DirectProtocolCodec.buildGeminiImagenPredictJson(
                        prompt = input.prompt,
                        count = input.count,
                        aspectRatio = input.aspectRatio,
                    )
                    val req = applyAuthHeaders(
                        Request.Builder()
                            .url(DirectProtocolCodec.geminiImagenPredictUrl(provider.baseUrl, input.model))
                            .post(payload.toRequestBody(ApiClient.JSON_MEDIA_TYPE)),
                        provider,
                    ).header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .build()
                    streamingClient.awaitCall(req).use { r ->
                        val raw = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw parseHttpError(r.code, raw)
                        DirectProtocolCodec.parseGeminiImagesJson(raw, r.code)
                    }
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
                    val req = applyAuthHeaders(
                        Request.Builder()
                            .url(DirectProtocolCodec.geminiChatUrl(provider.baseUrl, input.model, stream = false))
                            .post(payload.toRequestBody(ApiClient.JSON_MEDIA_TYPE)),
                        provider,
                    ).header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .build()
                    streamingClient.awaitCall(req).use { r ->
                        val raw = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw parseHttpError(r.code, raw)
                        DirectProtocolCodec.parseGeminiImagesJson(raw, r.code)
                    }
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

    private fun validateProvider(provider: ApiProviderConfig) {
        if (provider.baseUrl.isBlank()) {
            throw ApiException(0, "NO_SERVER_URL", "Custom Base URL is empty")
        }
        if (provider.apiKey.isBlank()) {
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
            DirectProtocolCodec.buildOpenAIChatRequestJson(request, stream)
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

    private fun applyAuthHeaders(builder: Request.Builder, provider: ApiProviderConfig): Request.Builder =
        when (provider.protocol) {
            ApiProtocol.OPENAI_CHAT,
            ApiProtocol.OPENAI_RESPONSES -> builder.header("Authorization", "Bearer ${provider.apiKey.trim()}")
            ApiProtocol.ANTHROPIC -> builder
                .header("x-api-key", provider.apiKey.trim())
                .header("anthropic-version", "2023-06-01")
            ApiProtocol.GEMINI -> builder.header("x-goog-api-key", provider.apiKey.trim())
        }
}
