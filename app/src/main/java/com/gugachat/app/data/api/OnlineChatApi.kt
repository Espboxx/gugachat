package com.gugachat.app.data.api

import com.gugachat.app.core.storage.ApiProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
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

    private fun resolveCustomProvider(groupId: Long): Pair<ApiProviderConfig, DirectApiClient>? {
        if (!ApiProviderConfig.isCustomProviderId(groupId)) return null
        val provider = providerLookup?.invoke(groupId)
            ?: throw ApiException(404, "PROVIDER_NOT_FOUND", "Custom API provider not found")
        val direct = directClient ?: throw IllegalStateException("DirectApiClient not configured")
        return provider to direct
    }

    suspend fun capabilities(): OnlineChatCapabilitiesDto =
        client.get("/online-chat/capabilities", OnlineChatCapabilitiesDto.serializer())

    suspend fun models(groupId: Long): List<OnlineChatModelDto> {
        resolveCustomProvider(groupId)?.let { (provider, direct) ->
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
        resolveCustomProvider(request.group_id)?.let { (provider, direct) ->
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

        return SseStreamConsumer.consumeResponse(
            response = response,
            parseHttpError = client::parseHttpError,
            parseSingleJson = SseParser::parseSingleCompletionJson,
            parseSseLine = { _, rawData -> SseParser.parseDataPayload(rawData) },
            onDelta = onDelta,
            onReasoning = onReasoning,
            onUsage = onUsage,
        )
    }

    suspend fun completeOnce(request: OnlineChatCompletionRequestDto): CompletionResult {
        resolveCustomProvider(request.group_id)?.let { (provider, direct) ->
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
