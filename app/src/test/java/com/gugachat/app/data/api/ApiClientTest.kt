package com.gugachat.app.data.api

import com.gugachat.app.core.storage.TokenSnapshot
import com.gugachat.app.core.storage.TokenStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private class FakeTokenStore(
    accessToken: String? = null,
    refreshToken: String? = null,
    expiresAtMs: Long = 0L,
) : TokenStore {
    private var access = accessToken
    private var refresh = refreshToken
    private var expiresAt = expiresAtMs
    private var userJson: String? = null

    @Synchronized
    override fun readSnapshot(): TokenSnapshot = TokenSnapshot(access, refresh, expiresAt, userJson)

    @Synchronized
    override fun getAccessToken(): String? = access

    @Synchronized
    override fun getRefreshToken(): String? = refresh

    @Synchronized
    override fun getTokenExpiresAtMs(): Long = expiresAt

    @Synchronized
    override fun getCachedUserJson(): String? = userJson

    @Synchronized
    override fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSec: Long?,
        userJson: String?,
    ) {
        access = accessToken
        if (!refreshToken.isNullOrBlank()) refresh = refreshToken
        if (expiresInSec != null && expiresInSec > 0L) {
            expiresAt = System.currentTimeMillis() + expiresInSec * 1000L
        }
        if (!userJson.isNullOrBlank()) this.userJson = userJson
    }

    @Synchronized
    override fun saveUserJson(userJson: String) {
        this.userJson = userJson
    }

    @Synchronized
    override fun clear() {
        access = null
        refresh = null
        expiresAt = 0L
        userJson = null
    }
}

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var tokenStore: FakeTokenStore
    private lateinit var client: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenStore = FakeTokenStore(
            accessToken = "initial-access",
            refreshToken = "initial-refresh",
            expiresAtMs = System.currentTimeMillis() + 60_000L,
        )
        client = ApiClient(
            tokenStore = tokenStore,
            serverUrlProvider = { server.url("/").toString() },
            languageTagProvider = { "zh-CN" },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun get_unwrapsEnvelopeAndMapsNonZeroCodeErrors() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"code":0,"message":"ok","data":{"id":7,"email":"u@example.com","username":"gugu"}}"""),
        )
        val user = client.get("/user/profile", UserDto.serializer())
        assertEquals(7L, user.id)
        assertEquals("u@example.com", user.email)

        val recorded = server.takeRequest()
        assertEquals("Bearer initial-access", recorded.getHeader("Authorization"))
        assertEquals("1", recorded.getHeader("X-User-UI-Request"))
        assertTrue(recorded.path!!.contains("timezone="))

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"code":4001,"message":"maximum context length exceeded","reason":"TOKEN_LIMIT"}"""),
        )
        val ex = try {
            client.get("/online-chat/capabilities", OnlineChatCapabilitiesDto.serializer())
            null
        } catch (e: ApiException) {
            e
        }
        assertEquals("4001", ex?.code)
        assertTrue(ex?.isContextTooLong == true)
    }

    @Test
    fun concurrent401_triggersSingleFlightRefreshAndRetriesWithRotatedToken() = runBlocking {
        val refreshCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/v1/auth/refresh") -> {
                        val n = refreshCalls.incrementAndGet()
                        Thread.sleep(80)
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""{"code":0,"data":{"access_token":"rotated-access-$n","refresh_token":"rotated-refresh-$n","expires_in":3600}}""")
                    }
                    path.startsWith("/api/v1/online-chat/capabilities") -> {
                        val auth = request.getHeader("Authorization")
                        if (auth == "Bearer rotated-access-1") {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody("""{"code":0,"data":{"enabled":true,"groups":[]}}""")
                        } else {
                            MockResponse().setResponseCode(401).setBody("""{"message":"expired"}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val chatApi = OnlineChatApi(client)
        val results = listOf(
            async { chatApi.capabilities() },
            async { chatApi.capabilities() },
            async { chatApi.capabilities() },
        ).awaitAll()

        assertEquals(1, refreshCalls.get())
        assertEquals(listOf(true, true, true), results.map { it.enabled })
        assertEquals("rotated-access-1", tokenStore.getAccessToken())
        assertEquals("rotated-refresh-1", tokenStore.getRefreshToken())
    }

    @Test
    fun onlineChatApi_streamCompletion_handlesSseAndJsonFallback() = runBlocking {
        val chatApi = OnlineChatApi(client)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"plan\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Hi \"}}]}\r\n\r\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"GuGu\"}}],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":2,\"total_tokens\":10}}\n\n" +
                        "data: [DONE]\n\n",
                ),
        )

        val deltas = StringBuilder()
        val reasonings = StringBuilder()
        var seenUsage: ChatTokenUsageDto? = null
        val sseRes = chatApi.streamCompletion(
            request = OnlineChatCompletionRequestDto(
                group_id = 12L,
                model = "gpt-4o",
                messages = listOf(WireChatMessageDto.text("user", "hello")),
            ),
            onDelta = { deltas.append(it) },
            onReasoning = { reasonings.append(it) },
            onUsage = { seenUsage = it },
        )
        assertEquals("Hi GuGu", sseRes.content)
        assertEquals("Hi GuGu", deltas.toString())
        assertEquals("plan", reasonings.toString())
        assertEquals(10, seenUsage?.total_tokens)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"reasoning":"think","content":"single"}}],"usage":{"total_tokens":20}}"""),
        )
        val jsonRes = chatApi.streamCompletion(
            request = OnlineChatCompletionRequestDto(
                group_id = 12L,
                model = "gpt-4o",
                messages = listOf(WireChatMessageDto.text("user", "hello")),
            ),
        )
        assertEquals("single", jsonRes.content)
        assertEquals("think", jsonRes.reasoning)
        assertEquals(20, jsonRes.usage?.total_tokens)
    }

    @Test
    fun onlineImagesApi_submit_sendsMultipartWithNegativeGroupIdAndRejectsZero() = runBlocking {
        val imagesApi = OnlineImagesApi(client)

        var zeroRejected = false
        try {
            imagesApi.submit(
                input = OnlineImageSubmitInput(
                    apiKeyId = 0L,
                    provider = "openai",
                    model = "gpt-image-1",
                    prompt = "a duck",
                ),
                idempotencyKey = "idem-0",
            )
        } catch (_: IllegalArgumentException) {
            zeroRejected = true
        }
        assertTrue(zeroRejected)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"code":0,"data":{"id":"gen-1","api_key_id":-12,"provider":"openai","model":"gpt-image-1","prompt":"a duck","status":"pending"}}"""),
        )

        val res = imagesApi.submit(
            input = OnlineImageSubmitInput(
                apiKeyId = -12L,
                provider = "openai",
                model = "gpt-image-1",
                prompt = "a duck",
                action = "inpaint",
                sourceGenerationId = "gen-0",
                sourceImageIndex = 0,
                mask = ImageBinaryUpload("mask.png", "image/png", byteArrayOf(1, 2, 3)),
                referenceImages = listOf(
                    ImageBinaryUpload("ref1.jpg", "image/jpeg", byteArrayOf(4, 5)),
                    ImageBinaryUpload("ref2.png", "image/png", byteArrayOf(6, 7)),
                ),
            ),
            idempotencyKey = "idem-123",
        )

        assertEquals("gen-1", res.id)
        assertEquals(-12L, res.api_key_id)

        val recorded = server.takeRequest()
        assertEquals("idem-123", recorded.getHeader("Idempotency-Key"))
        assertFalse(recorded.headers["X-User-UI-Request"] == "1")
        val contentType = recorded.getHeader("Content-Type").orEmpty()
        assertTrue(contentType.startsWith("multipart/form-data"))
        val rawBody = recorded.body.readUtf8()
        assertTrue(rawBody.contains("""name="api_key_id"""") && rawBody.contains("-12"))
        assertTrue(rawBody.contains("""name="mask""""))
        assertTrue(rawBody.contains("""name="reference_images[]""""))
        assertTrue(rawBody.contains("ref1.jpg") && rawBody.contains("ref2.png"))
    }
}
