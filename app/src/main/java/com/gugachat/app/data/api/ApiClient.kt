package com.gugachat.app.data.api

import android.content.Context
import coil.ImageLoader
import com.gugachat.app.core.storage.TokenStore
import com.gugachat.app.core.util.ServerUrlUtils
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiClient(
    private val tokenStore: TokenStore,
    private val serverUrlProvider: () -> String,
    private val languageTagProvider: () -> String,
    private val onSessionExpired: () -> Unit = {},
) {
    private val refreshMutex = Mutex()

    val standardClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val streamingClient: OkHttpClient = standardClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun createAuthenticatedImageLoader(context: Context): ImageLoader {
        val imageClient = standardClient.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val orig = chain.request()
                val reqUrl = orig.url.toString()
                val base = ServerUrlUtils.normalizeServerUrl(serverUrlProvider())
                val matchesServer = base.isNotEmpty() && reqUrl.startsWith(base)
                val firstToken = if (matchesServer) tokenStore.getAccessToken() else null
                val requestWithAuth = if (!firstToken.isNullOrBlank() && orig.header("Authorization") == null) {
                    orig.newBuilder().header("Authorization", "Bearer $firstToken").build()
                } else {
                    orig
                }
                val response = chain.proceed(requestWithAuth)
                if (response.code == 401 && matchesServer && !tokenStore.getRefreshToken().isNullOrBlank()) {
                    response.close()
                    val refreshed = try {
                        runBlocking { refreshTokensSingleFlight(firstToken) }
                    } catch (_: Throwable) {
                        null
                    }
                    if (!refreshed.isNullOrBlank()) {
                        val retried = orig.newBuilder().header("Authorization", "Bearer $refreshed").build()
                        return@addInterceptor chain.proceed(retried)
                    }
                }
                response
            }
            .build()

        return ImageLoader.Builder(context.applicationContext)
            .okHttpClient(imageClient)
            .crossfade(true)
            .build()
    }

    suspend fun <T> get(
        path: String,
        deserializer: KSerializer<T>,
        queryParams: Map<String, String> = emptyMap(),
        baseUrlOverride: String? = null,
    ): T = requestJson(
        method = "GET",
        path = path,
        deserializer = deserializer,
        queryParams = queryParams,
        baseUrlOverride = baseUrlOverride,
    )

    suspend fun <T> postJson(
        path: String,
        body: String,
        deserializer: KSerializer<T>,
        queryParams: Map<String, String> = emptyMap(),
        baseUrlOverride: String? = null,
    ): T = requestJson(
        method = "POST",
        path = path,
        body = body.toRequestBody(JSON_MEDIA_TYPE),
        deserializer = deserializer,
        queryParams = queryParams,
        baseUrlOverride = baseUrlOverride,
    )

    suspend fun <T> putJson(
        path: String,
        body: String,
        deserializer: KSerializer<T>,
    ): T = requestJson(
        method = "PUT",
        path = path,
        body = body.toRequestBody(JSON_MEDIA_TYPE),
        deserializer = deserializer,
    )

    suspend fun <T> patchJson(
        path: String,
        body: String,
        deserializer: KSerializer<T>,
    ): T = requestJson(
        method = "PATCH",
        path = path,
        body = body.toRequestBody(JSON_MEDIA_TYPE),
        deserializer = deserializer,
    )

    suspend fun <T> delete(
        path: String,
        deserializer: KSerializer<T>,
    ): T = requestJson(
        method = "DELETE",
        path = path,
        deserializer = deserializer,
    )

    suspend fun <T> postMultipart(
        path: String,
        body: RequestBody,
        headers: Map<String, String> = emptyMap(),
        deserializer: KSerializer<T>,
    ): T = requestJson(
        method = "POST",
        path = path,
        body = body,
        extraHeaders = headers,
        deserializer = deserializer,
        client = streamingClient,
    )

    suspend fun executeRaw(
        method: String,
        path: String,
        body: RequestBody? = null,
        queryParams: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
        client: OkHttpClient = standardClient,
        baseUrlOverride: String? = null,
    ): Response {
        val base = ServerUrlUtils.normalizeServerUrl(baseUrlOverride ?: serverUrlProvider())
        if (base.isEmpty()) {
            throw ApiException(0, "NO_SERVER_URL", "Server URL is not configured")
        }
        val requestUrl = buildUrlWithParams(base, path, method, queryParams)
        val isAuthEndpoint = isAuthEndpointPath(path)

        val firstToken = tokenStore.getAccessToken()
        val firstReq = buildRequest(method, requestUrl, body, extraHeaders, firstToken)
        val firstResp = client.awaitCall(firstReq)

        if (firstResp.code == 401 && !isAuthEndpoint && !tokenStore.getRefreshToken().isNullOrBlank()) {
            withContext(Dispatchers.IO) { firstResp.close() }
            val refreshedToken = refreshTokensSingleFlight(firstToken)
            val retryReq = buildRequest(method, requestUrl, body, extraHeaders, refreshedToken)
            return client.awaitCall(retryReq)
        }
        return firstResp
    }

    suspend fun getBytesAbsoluteOrApi(
        urlOrPath: String,
        queryParams: Map<String, String> = emptyMap(),
        attachAuth: Boolean = true,
    ): ByteArray {
        val targetUrl = if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            urlOrPath
        } else {
            val base = ServerUrlUtils.normalizeServerUrl(serverUrlProvider())
            if (base.isEmpty()) throw ApiException(0, "NO_SERVER_URL", "Server URL is not configured")
            buildUrlWithParams(base, urlOrPath, "GET", queryParams)
        }
        val reqBuilder = Request.Builder().url(targetUrl).get()
        if (attachAuth) {
            val token = tokenStore.getAccessToken()
            if (!token.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $token")
        }
        val resp = standardClient.newBuilder().readTimeout(90, TimeUnit.SECONDS).build().awaitCall(reqBuilder.build())
        return withContext(Dispatchers.IO) {
            resp.use {
                if (!it.isSuccessful) {
                    val errBody = it.body?.string().orEmpty()
                    throw parseHttpError(it.code, errBody)
                }
                it.body?.bytes() ?: ByteArray(0)
            }
        }
    }

    suspend fun <T> requestJson(
        method: String,
        path: String,
        deserializer: KSerializer<T>,
        body: RequestBody? = null,
        queryParams: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
        client: OkHttpClient = standardClient,
        baseUrlOverride: String? = null,
    ): T {
        val resp = executeRaw(
            method = method,
            path = path,
            body = body,
            queryParams = queryParams,
            extraHeaders = extraHeaders,
            client = client,
            baseUrlOverride = baseUrlOverride,
        )
        return withContext(Dispatchers.IO) {
            resp.use { response ->
                val rawText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw parseHttpError(response.code, rawText)
                }
                unwrapEnvelopeAndDecode(response.code, rawText, deserializer)
            }
        }
    }

    suspend fun refreshTokensSingleFlight(failedAccessToken: String?): String = refreshMutex.withLock {
        val snapshot = tokenStore.readSnapshot()
        if (!snapshot.accessToken.isNullOrBlank() &&
            !failedAccessToken.isNullOrBlank() &&
            snapshot.accessToken != failedAccessToken &&
            snapshot.tokenExpiresAtMs > System.currentTimeMillis()
        ) {
            return@withLock snapshot.accessToken
        }

        val refreshToken = snapshot.refreshToken
        if (refreshToken.isNullOrBlank()) {
            tokenStore.clear()
            onSessionExpired()
            throw ApiException(401, "TOKEN_REFRESH_FAILED", "Session expired. Please sign in again.")
        }

        val base = ServerUrlUtils.normalizeServerUrl(serverUrlProvider())
        if (base.isEmpty()) {
            throw ApiException(0, "NO_SERVER_URL", "Server URL is not configured")
        }

        val url = ServerUrlUtils.buildApiUrl(base, "/auth/refresh")
        val payloadJson = AppJson.encodeToString(RefreshTokenRequest.serializer(), RefreshTokenRequest(refreshToken))
        val req = Request.Builder()
            .url(url)
            .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()

        val resp = try {
            standardClient.awaitCall(req)
        } catch (e: IOException) {
            throw ApiException(0, "TOKEN_REFRESH_UNAVAILABLE", e.message ?: "Network error while refreshing token")
        }

        val (respCode, respSuccessful, bodyText) = withContext(Dispatchers.IO) {
            resp.use { response ->
                Triple(response.code, response.isSuccessful, response.body?.string().orEmpty())
            }
        }
        if (!respSuccessful) {
            if (respCode in 400..499 && respCode != 429) {
                val latest = tokenStore.readSnapshot()
                if (latest.refreshToken == refreshToken) {
                    tokenStore.clear()
                    onSessionExpired()
                }
                throw ApiException(401, "TOKEN_REFRESH_FAILED", "Session expired. Please sign in again.")
            }
            throw ApiException(respCode, "TOKEN_REFRESH_UNAVAILABLE", "Token refresh unavailable")
        }

        val parsed = unwrapEnvelopeAndDecode(respCode, bodyText, RefreshTokenResponseDto.serializer())
        val latest = tokenStore.readSnapshot()
        if (latest.refreshToken != refreshToken) {
            if (!latest.accessToken.isNullOrBlank()) return@withLock latest.accessToken
            throw ApiException(401, "AUTH_SESSION_CHANGED", "Authentication session changed while refreshing.")
        }
        tokenStore.saveTokens(
            accessToken = parsed.access_token,
            refreshToken = parsed.refresh_token,
            expiresInSec = parsed.expires_in,
        )
        parsed.access_token
    }

    private fun buildUrlWithParams(
        base: String,
        path: String,
        method: String,
        queryParams: Map<String, String>,
    ): String {
        val rawUrl = ServerUrlUtils.buildApiUrl(base, path)
        val parsed = rawUrl.toHttpUrlOrNull() ?: return rawUrl
        val builder = parsed.newBuilder()
        if (method.equals("GET", ignoreCase = true) && !queryParams.containsKey("timezone")) {
            builder.addQueryParameter("timezone", TimeZone.getDefault().id)
        }
        for ((k, v) in queryParams) {
            builder.addQueryParameter(k, v)
        }
        return builder.build().toString()
    }

    private fun buildRequest(
        method: String,
        url: String,
        body: RequestBody?,
        extraHeaders: Map<String, String>,
        accessToken: String?,
    ): Request {
        val builder = Request.Builder().url(url)
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
            "PUT" -> builder.put(body ?: ByteArray(0).toRequestBody(null))
            "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> builder.delete(body)
            else -> builder.method(method.uppercase(), body)
        }

        if (!accessToken.isNullOrBlank() && !extraHeaders.containsKey("Authorization")) {
            builder.header("Authorization", "Bearer $accessToken")
        }
        builder.header("Accept-Language", languageTagProvider())
        if (!extraHeaders.containsKey("Accept")) {
            builder.header("Accept", "application/json")
        }
        if (ServerUrlUtils.isUserTimingApiPath(url)) {
            builder.header("X-User-UI-Request", "1")
        }
        for ((k, v) in extraHeaders) {
            builder.header(k, v)
        }
        return builder.build()
    }

    private fun isAuthEndpointPath(path: String): Boolean =
        path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/auth/refresh")

    fun parseHttpError(statusCode: Int, rawBody: String): ApiException {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) {
            return ApiException(statusCode, statusCode.toString(), "HTTP $statusCode")
        }
        return try {
            val elem = AppJson.parseToJsonElement(trimmed)
            if (elem is JsonObject) {
                val errObj = elem["error"]
                if (errObj is JsonObject) {
                    val msg = errObj["message"]?.jsonPrimitive?.content
                        ?: elem["message"]?.jsonPrimitive?.content
                        ?: trimmed
                    val code = errObj["code"]?.jsonPrimitive?.content
                        ?: errObj["type"]?.jsonPrimitive?.content
                    return ApiException(statusCode, code, msg)
                }
                if (errObj is JsonPrimitive && errObj.isString) {
                    return ApiException(statusCode, statusCode.toString(), errObj.content)
                }
                val code = elem["code"]?.jsonPrimitive?.content
                val reason = elem["reason"]?.jsonPrimitive?.content
                val msg = elem["message"]?.jsonPrimitive?.content
                    ?: elem["detail"]?.jsonPrimitive?.content
                    ?: trimmed.take(400)
                return ApiException(statusCode, code, msg, reason)
            }
            ApiException(statusCode, statusCode.toString(), trimmed.take(400))
        } catch (_: Exception) {
            ApiException(statusCode, statusCode.toString(), trimmed.take(400))
        }
    }

    private fun <T> unwrapEnvelopeAndDecode(
        statusCode: Int,
        rawText: String,
        deserializer: KSerializer<T>,
    ): T {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return AppJson.decodeFromString(deserializer, "{}")
        }
        val elem: JsonElement = try {
            AppJson.parseToJsonElement(trimmed)
        } catch (e: Exception) {
            throw ApiException(statusCode, "INVALID_JSON", "Invalid JSON response: ${e.message}")
        }

        if (elem is JsonObject && elem.containsKey("code")) {
            val codeNum = elem["code"]?.jsonPrimitive?.intOrNull
            if (codeNum != null) {
                if (codeNum != 0) {
                    val msg = elem["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    val reason = elem["reason"]?.jsonPrimitive?.content
                    throw ApiException(statusCode, codeNum.toString(), msg, reason)
                }
                val dataElem = elem["data"] ?: JsonObject(emptyMap())
                return AppJson.decodeFromJsonElement(deserializer, dataElem)
            }
        }
        return AppJson.decodeFromJsonElement(deserializer, elem)
    }

    companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

suspend fun OkHttpClient.awaitCall(request: Request): Response =
    suspendCancellableCoroutine { cont: CancellableContinuation<Response> ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled || call.isCanceled()) {
                    cont.resumeWithException(CancellationException("Request cancelled", e))
                } else {
                    cont.resumeWithException(ApiException(0, "ERR_NETWORK", e.message ?: "Network error"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isCancelled) {
                    response.close()
                    return
                }
                cont.resume(response)
            }
        })
    }
