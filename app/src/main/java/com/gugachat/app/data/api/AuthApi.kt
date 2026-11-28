package com.gugachat.app.data.api

import com.gugachat.app.core.storage.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class AuthApi(
    private val client: ApiClient,
    private val tokenStore: TokenStore,
) {
    suspend fun getPublicSettings(serverUrlOverride: String? = null): PublicSettingsDto =
        client.get(
            path = "/settings/public",
            deserializer = PublicSettingsDto.serializer(),
            baseUrlOverride = serverUrlOverride,
            accessTokenOverride = if (serverUrlOverride != null) "" else null,
        )

    suspend fun login(
        email: String,
        password: String,
        captchaProof: CaptchaProof? = null,
        baseUrlOverride: String? = null,
        persistToTokenStore: Boolean = true,
    ): LoginResponseDto {
        val (turnstile, ticket, randstr) = captchaProof.toWireTriple()
        val body = AppJson.encodeToString(
            AuthLoginRequest.serializer(),
            AuthLoginRequest(
                email = email.trim(),
                password = password,
                turnstile_token = turnstile,
                tencent_captcha_ticket = ticket,
                tencent_captcha_randstr = randstr,
            ),
        )
        val resp = client.postJson(
            path = "/auth/login",
            body = body,
            deserializer = LoginResponseDto.serializer(),
            baseUrlOverride = baseUrlOverride,
            accessTokenOverride = if (!persistToTokenStore) "" else null,
        )
        if (persistToTokenStore) {
            persistLoginIfComplete(resp)
        }
        return resp
    }

    suspend fun login2FA(
        tempToken: String,
        totpCode: String,
        baseUrlOverride: String? = null,
        persistToTokenStore: Boolean = true,
    ): LoginResponseDto {
        val body = AppJson.encodeToString(
            Login2FARequest.serializer(),
            Login2FARequest(temp_token = tempToken, totp_code = totpCode.trim()),
        )
        val resp = client.postJson(
            path = "/auth/login/2fa",
            body = body,
            deserializer = LoginResponseDto.serializer(),
            baseUrlOverride = baseUrlOverride,
            accessTokenOverride = if (!persistToTokenStore) "" else null,
        )
        if (persistToTokenStore) {
            persistLoginIfComplete(resp)
        }
        return resp
    }

    suspend fun register(
        email: String,
        password: String,
        verifyCode: String? = null,
        promoCode: String? = null,
        invitationCode: String? = null,
        captchaProof: CaptchaProof? = null,
        baseUrlOverride: String? = null,
        persistToTokenStore: Boolean = true,
    ): LoginResponseDto {
        val (turnstile, ticket, randstr) = captchaProof.toWireTriple()
        val body = AppJson.encodeToString(
            AuthRegisterRequest.serializer(),
            AuthRegisterRequest(
                email = email.trim(),
                password = password,
                verify_code = verifyCode?.trim()?.ifEmpty { null },
                promo_code = promoCode?.trim()?.ifEmpty { null },
                invitation_code = invitationCode?.trim()?.ifEmpty { null },
                turnstile_token = turnstile,
                tencent_captcha_ticket = ticket,
                tencent_captcha_randstr = randstr,
            ),
        )
        val resp = client.postJson(
            path = "/auth/register",
            body = body,
            deserializer = LoginResponseDto.serializer(),
            baseUrlOverride = baseUrlOverride,
            accessTokenOverride = if (!persistToTokenStore) "" else null,
        )
        if (persistToTokenStore) {
            persistLoginIfComplete(resp)
        }
        return resp
    }

    suspend fun sendVerifyCode(
        email: String,
        captchaProof: CaptchaProof? = null,
        baseUrlOverride: String? = null,
    ): SendVerifyCodeResponse {
        val (turnstile, ticket, randstr) = captchaProof.toWireTriple()
        val body = AppJson.encodeToString(
            SendVerifyCodeRequest.serializer(),
            SendVerifyCodeRequest(
                email = email.trim(),
                turnstile_token = turnstile,
                tencent_captcha_ticket = ticket,
                tencent_captcha_randstr = randstr,
            ),
        )
        return client.postJson(
            path = "/auth/send-verify-code",
            body = body,
            deserializer = SendVerifyCodeResponse.serializer(),
            baseUrlOverride = baseUrlOverride,
            accessTokenOverride = if (baseUrlOverride != null) "" else null,
        )
    }

    suspend fun getCurrentUser(
        baseUrlOverride: String? = null,
        accessTokenOverride: String? = null,
        persistToTokenStore: Boolean = true,
    ): UserDto {
        val user = client.get(
            path = "/auth/me",
            deserializer = UserDto.serializer(),
            baseUrlOverride = baseUrlOverride,
            accessTokenOverride = accessTokenOverride,
        )
        if (persistToTokenStore) {
            tokenStore.saveUserJson(AppJson.encodeToString(UserDto.serializer(), user))
        }
        return user
    }

    suspend fun getUserProfile(): UserDto {
        val user = client.get("/user/profile", UserDto.serializer())
        tokenStore.saveUserJson(AppJson.encodeToString(UserDto.serializer(), user))
        return user
    }

    suspend fun logout() {
        val refreshToken = tokenStore.getRefreshToken()
        if (!refreshToken.isNullOrBlank()) {
            try {
                val body = AppJson.encodeToString(
                    LogoutRequest.serializer(),
                    LogoutRequest(refresh_token = refreshToken),
                )
                client.postJson("/auth/logout", body, JsonObject.serializer())
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Clear local tokens regardless
            }
        }
        tokenStore.clear()
    }

    /**
     * Revokes a specific saved account's refresh token on its target server without
     * touching the currently active session's TokenStore or serverUrl.
     */
    suspend fun revokeRefreshToken(serverUrl: String, refreshToken: String?) {
        val cleanRefresh = refreshToken?.trim().orEmpty()
        if (serverUrl.isBlank() || cleanRefresh.isEmpty()) return
        try {
            val body = AppJson.encodeToString(
                LogoutRequest.serializer(),
                LogoutRequest(refresh_token = cleanRefresh),
            )
            client.postJson(
                path = "/auth/logout",
                body = body,
                deserializer = JsonObject.serializer(),
                baseUrlOverride = serverUrl,
                accessTokenOverride = "",
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Best-effort revocation; local removal proceeds regardless
        }
    }

    private fun persistLoginIfComplete(resp: LoginResponseDto) {
        if (!resp.requires_2fa && !resp.access_token.isNullOrBlank()) {
            val userJson = resp.user?.let { AppJson.encodeToString(UserDto.serializer(), it) }
            tokenStore.saveTokens(
                accessToken = resp.access_token,
                refreshToken = resp.refresh_token,
                expiresInSec = resp.expires_in,
                userJson = userJson,
            )
        }
    }

    private fun CaptchaProof?.toWireTriple(): Triple<String?, String?, String?> =
        Triple(
            this?.turnstileToken?.trim()?.ifEmpty { null },
            this?.tencentTicket?.trim()?.ifEmpty { null },
            this?.tencentRandstr?.trim()?.ifEmpty { null },
        )
}
