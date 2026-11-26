package com.gugachat.app.data.api

import kotlinx.serialization.Serializable

@Serializable
private data class StreamErrorPayload(
    val message: String? = null,
    val code: String? = null,
    val type: String? = null,
)

@Serializable
private data class StreamDeltaPayload(
    val content: String? = null,
    val reasoning_content: String? = null,
    val reasoning: String? = null,
    val thinking: String? = null,
)

@Serializable
private data class StreamMessagePayload(
    val content: String? = null,
    val reasoning_content: String? = null,
    val reasoning: String? = null,
    val thinking: String? = null,
)

@Serializable
private data class StreamChoicePayload(
    val delta: StreamDeltaPayload? = null,
    val message: StreamMessagePayload? = null,
    val finish_reason: String? = null,
)

@Serializable
private data class StreamChunkPayload(
    val choices: List<StreamChoicePayload>? = null,
    val usage: ChatTokenUsageDto? = null,
    val error: StreamErrorPayload? = null,
)

data class SseLineOutcome(
    val done: Boolean = false,
    val deltaText: String = "",
    val deltaReasoning: String = "",
    val usage: ChatTokenUsageDto? = null,
    val finishReason: String? = null,
)

object SseParser {
    fun parseDataPayload(rawData: String): SseLineOutcome? {
        val trimmed = rawData.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "[DONE]") return SseLineOutcome(done = true)

        val chunk = try {
            AppJson.decodeFromString<StreamChunkPayload>(trimmed)
        } catch (_: Exception) {
            return null
        }

        if (chunk.error != null) {
            throw ApiException(
                status = 502,
                code = chunk.error.code ?: chunk.error.type,
                message = chunk.error.message ?: "Stream error",
            )
        }

        val choice = chunk.choices?.firstOrNull()
        val delta = choice?.delta
        val msg = choice?.message
        val reasoningPiece = delta?.reasoning_content
            ?: delta?.reasoning
            ?: delta?.thinking
            ?: msg?.reasoning_content
            ?: msg?.reasoning
            ?: msg?.thinking
        val contentPiece = delta?.content ?: msg?.content

        return SseLineOutcome(
            done = false,
            deltaText = contentPiece.orEmpty(),
            deltaReasoning = reasoningPiece.orEmpty(),
            usage = chunk.usage,
            finishReason = choice?.finish_reason?.takeIf { it.isNotBlank() },
        )
    }

    fun parseSingleCompletionJson(rawBody: String, statusCode: Int): CompletionResult {
        val parsed = try {
            AppJson.decodeFromString<StreamChunkPayload>(rawBody)
        } catch (_: Exception) {
            throw ApiException(statusCode, "INVALID_JSON", "Invalid completion JSON response")
        }
        if (parsed.error != null) {
            throw ApiException(
                status = statusCode,
                code = parsed.error.code ?: parsed.error.type,
                message = parsed.error.message ?: "Upstream error",
            )
        }
        val msg = parsed.choices?.firstOrNull()?.message
        return CompletionResult(
            content = msg?.content.orEmpty(),
            reasoning = (msg?.reasoning_content ?: msg?.reasoning ?: msg?.thinking).orEmpty(),
            usage = parsed.usage,
        )
    }

    fun mergeUsage(previous: ChatTokenUsageDto?, incoming: ChatTokenUsageDto?): ChatTokenUsageDto? {
        if (incoming == null) return previous
        if (previous == null) return incoming
        return ChatTokenUsageDto(
            prompt_tokens = incoming.prompt_tokens ?: previous.prompt_tokens,
            completion_tokens = incoming.completion_tokens ?: previous.completion_tokens,
            total_tokens = incoming.total_tokens ?: previous.total_tokens,
            cache_read_input_tokens = incoming.cache_read_input_tokens ?: previous.cache_read_input_tokens,
        )
    }
}

data class CompletionResult(
    val content: String,
    val reasoning: String = "",
    val usage: ChatTokenUsageDto? = null,
)
