package com.gugachat.app.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseStreamConsumerTest {

    @Test
    fun consumeResponse_accumulatesTextReasoningUsageAndResetsEventOnBlankLine() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val sseBody = buildString {
                appendLine("event: content_block_delta")
                appendLine("""data: {"reasoning":"thinking..."}""")
                appendLine()
                appendLine("""data: {"text":"Hello ","event":"none"}""")
                appendLine("""data: {"text":"world","usage":{"prompt_tokens":10,"completion_tokens":2}}""")
                appendLine("""data: [DONE]""")
            }
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody),
            )

            val seenEvents = ArrayList<String?>()
            val deltas = StringBuilder()
            val reasonings = StringBuilder()
            var lastUsage: ChatTokenUsageDto? = null

            val resp = OkHttpClient().newCall(Request.Builder().url(server.url("/sse")).build()).execute()
            val result = SseStreamConsumer.consumeResponse(
                response = resp,
                parseHttpError = { code, body -> ApiException(code, "ERR", body) },
                parseSingleJson = { _, code -> ApiException(code, "UNEXPECTED", "").let { throw it } },
                parseSseLine = { event, rawData ->
                    seenEvents += event
                    val clean = rawData.trim()
                    when {
                        clean == "[DONE]" -> SseLineOutcome(done = true)
                        clean.contains("\"reasoning\"") -> SseLineOutcome(deltaReasoning = "thinking...")
                        clean.contains("Hello ") -> SseLineOutcome(deltaText = "Hello ")
                        else -> SseLineOutcome(
                            deltaText = "world",
                            usage = ChatTokenUsageDto(prompt_tokens = 10, completion_tokens = 2),
                        )
                    }
                },
                onDelta = { deltas.append(it) },
                onReasoning = { reasonings.append(it) },
                onUsage = { lastUsage = it },
            )

            assertEquals(listOf("content_block_delta", null, null, null), seenEvents)
            assertEquals("Hello world", result.content)
            assertEquals("thinking...", result.reasoning)
            assertEquals("Hello world", deltas.toString())
            assertEquals("thinking...", reasonings.toString())
            assertEquals(10, lastUsage?.prompt_tokens)
            assertEquals(2, lastUsage?.completion_tokens)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun consumeResponse_handlesSingleJsonFallbackAndPrematureEof() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"one-shot","reasoning_content":"why"}}]}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""data: {"choices":[{"delta":{"content":"partial"}}]}"""),
            )

            val client = OkHttpClient()
            val resp1 = client.newCall(Request.Builder().url(server.url("/json")).build()).execute()
            val single = SseStreamConsumer.consumeResponse(
                response = resp1,
                parseHttpError = { code, body -> ApiException(code, "ERR", body) },
                parseSingleJson = SseParser::parseSingleCompletionJson,
                parseSseLine = { _, raw -> SseParser.parseDataPayload(raw) },
            )
            assertEquals("one-shot", single.content)
            assertEquals("why", single.reasoning)

            val resp2 = client.newCall(Request.Builder().url(server.url("/eof")).build()).execute()
            val err = runCatching {
                SseStreamConsumer.consumeResponse(
                    response = resp2,
                    parseHttpError = { code, body -> ApiException(code, "ERR", body) },
                    parseSingleJson = SseParser::parseSingleCompletionJson,
                    parseSseLine = { _, raw -> SseParser.parseDataPayload(raw) },
                )
            }.exceptionOrNull() as? ApiException
            assertTrue(err != null)
            assertEquals("STREAM_UNEXPECTED_EOF", err?.code)
        } finally {
            server.shutdown()
        }
    }
}
