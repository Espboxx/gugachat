package com.gugachat.app.core.storage

import android.content.Context
import android.content.SharedPreferences

data class TokenSnapshot(
    val accessToken: String?,
    val refreshToken: String?,
    val tokenExpiresAtMs: Long,
    val cachedUserJson: String?,
)

interface TokenStore {
    fun readSnapshot(): TokenSnapshot
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getTokenExpiresAtMs(): Long
    fun getCachedUserJson(): String?
    fun saveTokens(
        accessToken: String,
        refreshToken: String? = null,
        expiresInSec: Long? = null,
        userJson: String? = null,
    )
    fun saveUserJson(userJson: String)
    fun clear()
}

class SecureTokenStore internal constructor(
    private val prefs: SharedPreferences,
) : TokenStore {
    constructor(context: Context) : this(
        EncryptedPrefsFactory.create(
            context = context.applicationContext,
            encryptedName = "gugachat_secure_tokens",
            fallbackName = "gugachat_tokens_fallback",
        ),
    )

    @Volatile
    var onTokensSaved: ((TokenSnapshot) -> Unit)? = null

    @Volatile
    var onUserJsonSaved: ((String) -> Unit)? = null

    private object Keys {
        const val ACCESS_TOKEN = "auth_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val TOKEN_EXPIRES_AT = "token_expires_at"
        const val AUTH_USER_JSON = "auth_user"
    }

    @Synchronized
    override fun readSnapshot(): TokenSnapshot = TokenSnapshot(
        accessToken = prefs.getString(Keys.ACCESS_TOKEN, null),
        refreshToken = prefs.getString(Keys.REFRESH_TOKEN, null),
        tokenExpiresAtMs = prefs.getLong(Keys.TOKEN_EXPIRES_AT, 0L),
        cachedUserJson = prefs.getString(Keys.AUTH_USER_JSON, null),
    )

    @Synchronized
    override fun getAccessToken(): String? = prefs.getString(Keys.ACCESS_TOKEN, null)

    @Synchronized
    override fun getRefreshToken(): String? = prefs.getString(Keys.REFRESH_TOKEN, null)

    @Synchronized
    override fun getTokenExpiresAtMs(): Long = prefs.getLong(Keys.TOKEN_EXPIRES_AT, 0L)

    @Synchronized
    override fun getCachedUserJson(): String? = prefs.getString(Keys.AUTH_USER_JSON, null)

    @Synchronized
    override fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSec: Long?,
        userJson: String?,
    ) {
        val editor = prefs.edit()
        editor.putString(Keys.ACCESS_TOKEN, accessToken)
        if (expiresInSec != null && expiresInSec > 0L) {
            editor.putLong(Keys.TOKEN_EXPIRES_AT, System.currentTimeMillis() + expiresInSec * 1000L)
        }
        if (!userJson.isNullOrBlank()) {
            editor.putString(Keys.AUTH_USER_JSON, userJson)
        }
        // Commit rotating refresh token last as commit marker
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(Keys.REFRESH_TOKEN, refreshToken)
        }
        editor.apply()
        onTokensSaved?.invoke(readSnapshot())
    }

    /**
     * Atomically replaces the active token snapshot when switching between saved accounts,
     * preserving the exact expiry timestamp without triggering the active-account writeback hook.
     */
    @Synchronized
    fun restoreSnapshot(
        accessToken: String,
        refreshToken: String?,
        tokenExpiresAtMs: Long,
        userJson: String?,
    ) {
        val editor = prefs.edit()
        editor.putString(Keys.ACCESS_TOKEN, accessToken)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(Keys.REFRESH_TOKEN, refreshToken)
        } else {
            editor.remove(Keys.REFRESH_TOKEN)
        }
        if (tokenExpiresAtMs > 0L) {
            editor.putLong(Keys.TOKEN_EXPIRES_AT, tokenExpiresAtMs)
        } else {
            editor.remove(Keys.TOKEN_EXPIRES_AT)
        }
        if (!userJson.isNullOrBlank()) {
            editor.putString(Keys.AUTH_USER_JSON, userJson)
        } else {
            editor.remove(Keys.AUTH_USER_JSON)
        }
        editor.apply()
    }

    @Synchronized
    override fun saveUserJson(userJson: String) {
        prefs.edit().putString(Keys.AUTH_USER_JSON, userJson).apply()
        onUserJsonSaved?.invoke(userJson)
    }

    @Synchronized
    override fun clear() {
        prefs.edit()
            .remove(Keys.ACCESS_TOKEN)
            .remove(Keys.REFRESH_TOKEN)
            .remove(Keys.TOKEN_EXPIRES_AT)
            .remove(Keys.AUTH_USER_JSON)
            .apply()
    }
}
