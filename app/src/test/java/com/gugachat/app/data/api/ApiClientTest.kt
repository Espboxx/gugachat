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
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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

    @Test
    fun authApi_parsesPublicCaptchaSettingsAndSerializesProofFields() = runBlocking {
        val authApi = AuthApi(client, tokenStore)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "code": 0,
                      "data": {
                        "site_name": "GuGuGa",
                        "turnstile_enabled": true,
                        "turnstile_site_key": "0x4AAAAAAFm2MCrnWLTlaRjZ",
                        "tencent_captcha_enabled": false,
                        "tencent_captcha_app_id": "190001",
                        "tencent_captcha_region": "intl",
                        "aliyun_captcha_enabled": false,
                        "aliyun_captcha_scene_id": "scn1",
                        "aliyun_captcha_prefix": "pre1",
                        "aliyun_captcha_region": "sgp"
                      }
                    }
                    """.trimIndent(),
                ),
        )
        val settings = authApi.getPublicSettings()
        assertEquals("0x4AAAAAAFm2MCrnWLTlaRjZ", settings.turnstile_site_key)
        assertEquals("190001", settings.tencent_captcha_app_id)
        assertEquals("intl", settings.tencent_captcha_region)
        assertEquals("scn1", settings.aliyun_captcha_scene_id)
        assertEquals("pre1", settings.aliyun_captcha_prefix)
        assertEquals("sgp", settings.aliyun_captcha_region)
        assertEquals(
            ActiveCaptchaConfig.Turnstile("0x4AAAAAAFm2MCrnWLTlaRjZ"),
            settings.resolveActiveCaptcha(),
        )
        server.takeRequest()

        // Turnstile/Aliyun token on login
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"code":0,"data":{"access_token":"tok1","refresh_token":"ref1","user":{"id":1,"email":"u@test.com","username":"u"}}}"""),
        )
        authApi.login(" u@test.com ", "secret", CaptchaProof(turnstileToken = "cf-tok-123"))
        val loginBody = server.takeRequest().body.readUtf8()
        assertTrue(loginBody.contains(""""turnstile_token":"cf-tok-123""""))
        assertFalse(loginBody.contains("tencent_captcha_ticket"))

        // Tencent ticket + randstr on sendVerifyCode
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"code":0,"data":{"message":"sent","countdown":60}}"""),
        )
        authApi.sendVerifyCode("u@test.com", CaptchaProof(tencentTicket = "tkt-9", tencentRandstr = "@rnd"))
        val codeBody = server.takeRequest().body.readUtf8()
        assertTrue(codeBody.contains(""""tencent_captcha_ticket":"tkt-9""""))
        assertTrue(codeBody.contains(""""tencent_captcha_randstr":"@rnd""""))
        assertFalse(codeBody.contains("turnstile_token"))

        // Null CaptchaProof omits captcha keys on register
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"code":0,"data":{"access_token":"tok2","refresh_token":"ref2","user":{"id":2,"email":"n@test.com","username":"n"}}}"""),
        )
        authApi.register(email = "n@test.com", password = "secret", captchaProof = null)
        val regBody = server.takeRequest().body.readUtf8()
        assertFalse(regBody.contains("turnstile_token"))
        assertFalse(regBody.contains("tencent_captcha_ticket"))
        assertFalse(regBody.contains("tencent_captcha_randstr"))
    }

    @Test
    fun onlineChatApi_streamCompletion_deliversIncrementalChunksOffCallerThreadWithIdentityEncoding() = runBlocking {
        val chatApi = OnlineChatApi(client)
        val sseBody = (1..4).joinToString("") { idx ->
            "data: {\"choices\":[{\"delta\":{\"content\":\"part$idx \"}}]}\n\n"
        } + "data: [DONE]\n\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setChunkedBody(sseBody, 56)
                .throttleBody(56, 70, TimeUnit.MILLISECONDS),
        )

        val callerThread = Thread.currentThread().name
        val deltaThreads = Collections.synchronizedList(mutableListOf<String>())
        val firstDeltaAt = AtomicLong(0L)
        val chunks = Collections.synchronizedList(mutableListOf<String>())

        val res = chatApi.streamCompletion(
            request = OnlineChatCompletionRequestDto(
                group_id = 12L,
                model = "deepseek-v4.1-flash",
                messages = listOf(WireChatMessageDto.text("user", "stream please")),
            ),
            onDelta = { delta ->
                firstDeltaAt.compareAndSet(0L, System.currentTimeMillis())
                deltaThreads.add(Thread.currentThread().name)
                chunks.add(delta)
            },
        )
        val doneAt = System.currentTimeMillis()

        val recorded = server.takeRequest()
        assertEquals("identity", recorded.getHeader("Accept-Encoding"))
        assertEquals("no-cache", recorded.getHeader("Cache-Control"))
        assertEquals("part1 part2 part3 part4 ", res.content)
        assertEquals(4, chunks.size)
        assertTrue("Expected SSE streaming off caller thread ($callerThread), got $deltaThreads", deltaThreads.all { it != callerThread })
        assertTrue("Expected incremental chunks before EOF, span was ${doneAt - firstDeltaAt.get()}ms", doneAt - firstDeltaAt.get() >= 100L)
    }
}
