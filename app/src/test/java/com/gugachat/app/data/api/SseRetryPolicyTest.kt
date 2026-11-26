package com.gugachat.app.data.api

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.TokenSnapshot
import com.gugachat.app.core.storage.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.EOFException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SseRetryPolicyTest {
    private lateinit var server: MockWebServer
    private lateinit var apiClient: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        apiClient = ApiClient(
            tokenStore = NoopTokenStore,
            serverUrlProvider = { server.url("/").toString() },
            languageTagProvider = { "zh" },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun isRetryableSseError_classifiesDisconnectsTimeoutsVsClientErrors() {
        assertTrue(SseRetryPolicy.isRetryableSseError(ApiException(502, null, "upstream SSE stream failed: unexpected EOF")))
        assertTrue(SseRetryPolicy.isRetryableSseError(ApiException(502, "STREAM_UNEXPECTED_EOF", "SSE stream interrupted")))
        assertTrue(SseRetryPolicy.isRetryableSseError(ApiException(504, "STREAM_TIMEOUT", "timeout")))
        assertTrue(SseRetryPolicy.isRetryableSseError(ApiException(0, "ERR_NETWORK", "Connection reset")))
        assertTrue(SseRetryPolicy.isRetryableSseError(ApiException(503, null, "Service Unavailable")))
        assertTrue(SseRetryPolicy.isRetryableSseError(ApiException(408, null, "Request Timeout")))
        assertTrue(SseRetryPolicy.isRetryableSseError(EOFException("unexpected EOF")))
        assertTrue(SseRetryPolicy.isRetryableSseError(SocketTimeoutException("Read timed out")))

        assertFalse(SseRetryPolicy.isRetryableSseError(CancellationException("user stopped")))
        assertFalse(SseRetryPolicy.isRetryableSseError(ApiException(401, "UNAUTHORIZED", "Invalid token")))
        assertFalse(SseRetryPolicy.isRetryableSseError(ApiException(400, "INVALID_REQUEST", "Bad parameter")))
        assertFalse(SseRetryPolicy.isRetryableSseError(ApiException(400, "context_length_exceeded", "maximum context length exceeded")))
    }

    @Test
    fun streamCompletion_retriesOnUpstreamUnexpectedEofErrorEventAndResetsBuffers() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"half \"}}]}\n\n" +
                        "data: {\"error\":{\"message\":\"upstream SSE stream failed: unexpected EOF\"}}\n\n",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Full clean reply\"}}]}\n\n" +
                        "data: [DONE]\n\n",
                ),
        )

        val chatApi = OnlineChatApi(
            client = apiClient,
            maxSseRetriesProvider = { 3 },
            retryBaseDelayMs = 10L,
        )
        val retries = mutableListOf<Pair<Int, Int>>()
        val resetCount = AtomicInteger(0)
        val liveBuf = StringBuilder()

        val result = chatApi.streamCompletion(
            request = OnlineChatCompletionRequestDto(1L, "deepseek-v4", listOf(WireChatMessageDto.text("user", "hi"))),
            onDelta = { liveBuf.append(it) },
            onRetryAttempt = { attempt, max, _ -> retries += attempt to max },
            onResetForRetry = {
                resetCount.incrementAndGet()
                liveBuf.clear()
            },
        )

        assertEquals("Full clean reply", result.content)
        assertEquals("Full clean reply", liveBuf.toString())
        assertEquals(listOf(1 to 3), retries)
        assertEquals(1, resetCount.get())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun streamCompletion_retriesOnSilentPrematureEofWithoutDoneMarker() = runBlocking {
        // Attempt 1: sends partial chunk then silently closes TCP stream with no [DONE] and no finish_reason
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"truncated\"}}]}\n\n"),
        )
        // Attempt 2: completes cleanly with finish_reason="stop" (even without [DONE])
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Recovered!\"},\"finish_reason\":\"stop\"}]}\n\n",
                ),
        )

        val chatApi = OnlineChatApi(
            client = apiClient,
            maxSseRetriesProvider = { 3 },
            retryBaseDelayMs = 10L,
        )
        val result = chatApi.streamCompletion(
            request = OnlineChatCompletionRequestDto(1L, "gpt-4o", listOf(WireChatMessageDto.text("user", "hi"))),
        )
        assertEquals("Recovered!", result.content)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun streamCompletion_retriesOnReadTimeoutAndRespectsConfiguredMaxRetries() = runBlocking {
        // Attempt 1 stalls > 250ms read timeout
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"slow\"}}]}\n\n")
                .setHeadersDelay(450, TimeUnit.MILLISECONDS),
        )
        // Attempt 2 succeeds immediately
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"After timeout\"}}]}\n\ndata: [DONE]\n\n"),
        )

        val chatApi = OnlineChatApi(
            client = apiClient,
            maxSseRetriesProvider = { 3 },
            sseReadTimeoutMs = 250L,
            retryBaseDelayMs = 10L,
        )
        val result = chatApi.streamCompletion(
            request = OnlineChatCompletionRequestDto(1L, "gpt-4o", listOf(WireChatMessageDto.text("user", "hi"))),
        )
        assertEquals("After timeout", result.content)
        assertEquals(2, server.requestCount)

        // Verify maxRetries=0 disables retries
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"error\":{\"message\":\"upstream SSE stream failed: unexpected EOF\"}}\n\n"),
        )
        var failed = false
        try {
            chatApi.streamCompletion(
                request = OnlineChatCompletionRequestDto(1L, "gpt-4o", listOf(WireChatMessageDto.text("user", "hi"))),
                maxRetries = 0,
            )
        } catch (e: ApiException) {
            failed = e.message.contains("unexpected EOF")
        }
        assertTrue(failed)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun streamCompletion_retriesDirectProtocolOnPrematureEofAndSkipsNonRetryable401() = runBlocking {
        val provider = ApiProviderConfig(
            id = "p_direct",
            numericId = 1,
            protocol = ApiProtocol.ANTHROPIC,
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKey = "sk-ant-test",
        )
        val directClient = DirectApiClient(
            standardClient = OkHttpClient(),
            streamingClient = OkHttpClient(),
            parseHttpError = apiClient::parseHttpError,
            sseReadTimeoutMs = 2000L,
        )
        val chatApi = OnlineChatApi(
            client = apiClient,
            providerLookup = { id -> if (id == provider.syntheticGroupId) provider else null },
            directClient = directClient,
            maxSseRetriesProvider = { 3 },
            retryBaseDelayMs = 10L,
        )

        // Attempt 1: Anthropic stream drops before message_stop
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: content_block_delta\n" +
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"partial\"}}\n\n",
                ),
        )
        // Attempt 2: completes cleanly with message_stop
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: content_block_delta\n" +
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Complete Claude\"}}\n\n" +
                        "event: message_stop\n" +
                        "data: {\"type\":\"message_stop\"}\n\n",
                ),
        )

        val res = chatApi.streamCompletion(
            request = OnlineChatCompletionRequestDto(provider.syntheticGroupId, "claude-sonnet-4", listOf(WireChatMessageDto.text("user", "hi"))),
        )
        assertEquals("Complete Claude", res.content)
        assertEquals(2, server.requestCount)

        // Non-retryable 401 should NOT retry
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid api key"}}"""))
        var got401 = false
        try {
            chatApi.streamCompletion(
                request = OnlineChatCompletionRequestDto(provider.syntheticGroupId, "claude-sonnet-4", listOf(WireChatMessageDto.text("user", "hi"))),
            )
        } catch (e: ApiException) {
            got401 = e.status == 401
        }
        assertTrue(got401)
        assertEquals(3, server.requestCount)
    }
}

private object NoopTokenStore : TokenStore {
    override fun readSnapshot() = TokenSnapshot("tok", "ref", System.currentTimeMillis() + 60_000L, null)
    override fun getAccessToken(): String = "tok"
    override fun getRefreshToken(): String = "ref"
    override fun getTokenExpiresAtMs(): Long = System.currentTimeMillis() + 60_000L
    override fun getCachedUserJson(): String? = null
    override fun saveTokens(accessToken: String, refreshToken: String?, expiresInSec: Long?, userJson: String?) {}
    override fun saveUserJson(userJson: String) {}
    override fun clear() {}
}
