package com.gugachat.app.data.api

import com.gugachat.app.core.storage.ApiProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody

class OnlineChatApi(
    private val client: ApiClient,
    private val providerLookup: ((Long) -> ApiProviderConfig?)? = null,
    private val directClient: DirectApiClient? = null,
) {
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
            client = client.streamingClient,
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
                            if (outcome.done) break
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
                } catch (e: Exception) {
                    ctx.ensureActive()
                    throw ApiException(502, "STREAM_READ_ERROR", e.message ?: "Failed to read stream")
                } finally {
                    cancelHandle.dispose()
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
