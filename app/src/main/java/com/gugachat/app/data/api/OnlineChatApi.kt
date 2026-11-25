package com.gugachat.app.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.RequestBody.Companion.toRequestBody

class OnlineChatApi(private val client: ApiClient) {
    suspend fun capabilities(): OnlineChatCapabilitiesDto =
        client.get("/online-chat/capabilities", OnlineChatCapabilitiesDto.serializer())

    suspend fun models(groupId: Long): List<OnlineChatModelDto> =
        client.get(
            path = "/online-chat/models",
            queryParams = mapOf("group_id" to groupId.toString()),
            deserializer = OnlineChatModelsResponseDto.serializer(),
        ).models

    suspend fun streamCompletion(
        request: OnlineChatCompletionRequestDto,
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
        onUsage: (ChatTokenUsageDto) -> Unit = {},
    ): CompletionResult {
        val payloadJson = AppJson.encodeToString(
            OnlineChatCompletionRequestDto.serializer(),
            request.copy(stream = true),
        )
        val response = client.executeRaw(
            method = "POST",
            path = "/online-chat/completions",
            body = payloadJson.toRequestBody(ApiClient.JSON_MEDIA_TYPE),
            extraHeaders = mapOf("Accept" to "text/event-stream"),
            client = client.streamingClient,
        )

        return response.use { resp ->
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

            try {
                while (!source.exhausted()) {
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
            }

            CompletionResult(
                content = contentBuf.toString(),
                reasoning = reasoningBuf.toString(),
                usage = lastUsage,
            )
        }
    }

    suspend fun completeOnce(request: OnlineChatCompletionRequestDto): CompletionResult {
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
        return response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw client.parseHttpError(resp.code, raw)
            }
            SseParser.parseSingleCompletionJson(raw, resp.code)
        }
    }
}
