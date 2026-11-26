package com.gugachat.app.data.api

import com.gugachat.app.core.storage.ApiProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class OnlineChatApi(
    private val client: ApiClient,
    private val providerLookup: ((Long) -> ApiProviderConfig?)? = null,
    private val directClient: DirectApiClient? = null,
    private val maxSseRetriesProvider: () -> Int = { 0 },
    private val sseReadTimeoutMs: Long = SseRetryPolicy.DEFAULT_SSE_READ_TIMEOUT_MS,
    private val retryBaseDelayMs: Long = SseRetryPolicy.DEFAULT_RETRY_BASE_DELAY_MS,
) {
    private val sseStreamingClient = client.streamingClient.newBuilder()
        .readTimeout(sseReadTimeoutMs.coerceAtLeast(100L), TimeUnit.MILLISECONDS)
        .build()

    suspend fun capabilities(): OnlineChatCapabilitiesDto =
        client.get("/online-chat/capabilities", OnlineChatCapabilitiesDto.serializer())

    suspend fun models(groupId: Long): List<OnlineChatModelDto> {
        if (ApiProviderConfig.isCustomProviderId(groupId)) {
            val provider = providerLookup?.invoke(groupId)
                ?: throw ApiException(404, "PROVIDER_NOT_FOUND", "Custom API provider not found")
            val direct = directClient ?: throw IllegalStateException("DirectApiClient not configured")
            return direct.listChatModels(provider)
        }
        return client.get(
            path = "/online-chat/models",
            queryParams = mapOf("group_id" to groupId.toString()),
            deserializer = OnlineChatModelsResponseDto.serializer(),
        ).models
    }

    suspend fun streamCompletion(
        request: OnlineChatCompletionRequestDto,
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
        onUsage: (ChatTokenUsageDto) -> Unit = {},
        maxRetries: Int? = null,
        onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit = { _, _, _ -> },
        onResetForRetry: () -> Unit = {},
    ): CompletionResult {
        val effectiveRetries = SseRetryPolicy.clampRetryCount(maxRetries ?: maxSseRetriesProvider())
        return SseRetryPolicy.executeWithSseRetry(
            maxRetries = effectiveRetries,
            baseDelayMs = retryBaseDelayMs,
            onRetryAttempt = onRetryAttempt,
            onResetForRetry = onResetForRetry,
        ) {
            streamCompletionOnce(
                request = request,
                onDelta = onDelta,
                onReasoning = onReasoning,
                onUsage = onUsage,
            )
        }
    }

    private suspend fun streamCompletionOnce(
        request: OnlineChatCompletionRequestDto,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onUsage: (ChatTokenUsageDto) -> Unit,
    ): CompletionResult {
        if (ApiProviderConfig.isCustomProviderId(request.group_id)) {
            val provider = providerLookup?.invoke(request.group_id)
                ?: throw ApiException(404, "PROVIDER_NOT_FOUND", "Custom API provider not found")
            val direct = directClient ?: throw IllegalStateException("DirectApiClient not configured")
            return direct.streamChat(provider, request, onDelta, onReasoning, onUsage)
        }
        val payloadJson = AppJson.encodeToString(
            OnlineChatCompletionRequestDto.serializer(),
            request.copy(stream = true),
        )
        val response = client.executeRaw(
            method = "POST",
            path = "/online-chat/completions",
            body = payloadJson.toRequestBody(ApiClient.JSON_MEDIA_TYPE),
            extraHeaders = mapOf(
                "Accept" to "text/event-stream",
                "Accept-Encoding" to "identity",
                "Cache-Control" to "no-cache",
            ),
            client = sseStreamingClient,
        )

        return withContext(Dispatchers.IO) {
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty()
                    throw client.parseHttpError(resp.code, errBody)
                }

                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                val body = resp.body ?: throw ApiException(resp.code, "EMPTY_BODY", "Streaming response body unavailable")

                if (contentType.contains("application/json")) {
                    val raw = body.string()
                    val single = SseParser.parseSingleCompletionJson(raw, resp.code)
                    if (single.reasoning.isNotEmpty()) onReasoning(single.reasoning)
                    if (single.content.isNotEmpty()) onDelta(single.content)
                    if (single.usage != null) onUsage(single.usage)
                    return@use single
                }

                val source = body.source()
                val contentBuf = StringBuilder()
                val reasoningBuf = StringBuilder()
                var lastUsage: ChatTokenUsageDto? = null
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
                        if (line.startsWith("data:")) {
                            val outcome = SseParser.parseDataPayload(line.removePrefix("data:")) ?: continue
                            if (outcome.done) {
                                sawDone = true
                                break
                            }
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

                if (!sawDone && !sawFinishReason) {
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

    suspend fun completeOnce(request: OnlineChatCompletionRequestDto): CompletionResult {
        if (ApiProviderConfig.isCustomProviderId(request.group_id)) {
            val provider = providerLookup?.invoke(request.group_id)
                ?: throw ApiException(404, "PROVIDER_NOT_FOUND", "Custom API provider not found")
            val direct = directClient ?: throw IllegalStateException("DirectApiClient not configured")
            return direct.completeChatOnce(provider, request)
        }
        val payloadJson = AppJson.encodeToString(
            OnlineChatCompletionRequestDto.serializer(),
            request.copy(stream = false),
        )
        val response = client.executeRaw(
            method = "POST",
            path = "/online-chat/completions",
            body = payloadJson.toRequestBody(ApiClient.JSON_MEDIA_TYPE),
            extraHeaders = mapOf("Accept" to "application/json"),
            client = client.streamingClient,
        )
        return withContext(Dispatchers.IO) {
            response.use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw client.parseHttpError(resp.code, raw)
                }
                SseParser.parseSingleCompletionJson(raw, resp.code)
            }
        }
    }
}
