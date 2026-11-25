package com.gugachat.app.data.api

import com.gugachat.app.core.storage.TokenStore
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
        )

    suspend fun login(email: String, password: String): LoginResponseDto {
        val body = AppJson.encodeToString(
            AuthLoginRequest.serializer(),
            AuthLoginRequest(email = email.trim(), password = password),
        )
        val resp = client.postJson("/auth/login", body, LoginResponseDto.serializer())
        persistLoginIfComplete(resp)
        return resp
    }

    suspend fun login2FA(tempToken: String, totpCode: String): LoginResponseDto {
        val body = AppJson.encodeToString(
            Login2FARequest.serializer(),
            Login2FARequest(temp_token = tempToken, totp_code = totpCode.trim()),
        )
        val resp = client.postJson("/auth/login/2fa", body, LoginResponseDto.serializer())
        persistLoginIfComplete(resp)
        return resp
    }

    suspend fun register(
        email: String,
        password: String,
        verifyCode: String? = null,
        promoCode: String? = null,
        invitationCode: String? = null,
    ): LoginResponseDto {
        val body = AppJson.encodeToString(
            AuthRegisterRequest.serializer(),
            AuthRegisterRequest(
                email = email.trim(),
                password = password,
                verify_code = verifyCode?.trim()?.ifEmpty { null },
                promo_code = promoCode?.trim()?.ifEmpty { null },
                invitation_code = invitationCode?.trim()?.ifEmpty { null },
            ),
        )
        val resp = client.postJson("/auth/register", body, LoginResponseDto.serializer())
        persistLoginIfComplete(resp)
        return resp
    }

    suspend fun sendVerifyCode(email: String): SendVerifyCodeResponse {
        val body = AppJson.encodeToString(
            SendVerifyCodeRequest.serializer(),
            SendVerifyCodeRequest(email = email.trim()),
        )
        return client.postJson("/auth/send-verify-code", body, SendVerifyCodeResponse.serializer())
    }

    suspend fun getCurrentUser(): UserDto {
        val user = client.get("/auth/me", UserDto.serializer())
        tokenStore.saveUserJson(AppJson.encodeToString(UserDto.serializer(), user))
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
            } catch (_: Throwable) {
                // Clear local tokens regardless
            }
        }
        tokenStore.clear()
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
}
