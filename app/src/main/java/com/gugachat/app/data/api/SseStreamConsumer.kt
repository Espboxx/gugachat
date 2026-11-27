package com.gugachat.app.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Response
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

internal object SseStreamConsumer {
    suspend fun consumeResponse(
        response: Response,
        parseHttpError: (Int, String) -> ApiException,
        parseSingleJson: (raw: String, statusCode: Int) -> CompletionResult,
        parseSseLine: (event: String?, rawData: String) -> SseLineOutcome?,
        mergeUsage: (ChatTokenUsageDto?, ChatTokenUsageDto?) -> ChatTokenUsageDto? = SseParser::mergeUsage,
        isPrematureEof: (
            sawDone: Boolean,
            sawFinishReason: Boolean,
            content: CharSequence,
            reasoning: CharSequence,
        ) -> Boolean = { sawDone, sawFinishReason, _, _ -> !sawDone && !sawFinishReason },
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
        onUsage: (ChatTokenUsageDto) -> Unit = {},
    ): CompletionResult = withContext(Dispatchers.IO) {
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                throw parseHttpError(resp.code, errBody)
            }

            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            val body = resp.body
                ?: throw ApiException(resp.code, "EMPTY_BODY", "Streaming response body unavailable")

            if (contentType.contains("application/json")) {
                val raw = body.string()
                val single = parseSingleJson(raw, resp.code)
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
                        val outcome = parseSseLine(currentEvent, rawData) ?: continue
                        if (!outcome.finishReason.isNullOrBlank()) {
                            sawFinishReason = true
                        }
                        if (outcome.usage != null) {
                            lastUsage = mergeUsage(lastUsage, outcome.usage)
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

            if (isPrematureEof(sawDone, sawFinishReason, contentBuf, reasoningBuf)) {
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
