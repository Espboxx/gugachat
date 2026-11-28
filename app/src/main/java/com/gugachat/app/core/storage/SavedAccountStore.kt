package com.gugachat.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.gugachat.app.core.util.CoreAppJson
import com.gugachat.app.core.util.ServerUrlUtils
import com.gugachat.app.data.api.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class SavedAccount(
    val accountKey: String,
    val serverUrl: String,
    val user: UserDto,
    val accessToken: String = "",
    val refreshToken: String? = null,
    val tokenExpiresAtMs: Long = 0L,
    val sessionExpired: Boolean = false,
    val lastUsedAt: Long = System.currentTimeMillis(),
) {
    val hasValidTokens: Boolean
        get() = !sessionExpired && accessToken.isNotBlank()

    fun displayName(): String =
        user.username.trim()
            .ifBlank { user.email.trim() }
            .ifBlank { "ID ${user.id}" }

    fun serverHostLabel(): String =
        serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')

    companion object {
        fun buildAccountKey(serverUrl: String, userId: Long): String {
            val normalized = ServerUrlUtils.normalizeServerUrl(serverUrl)
            return "sub2api:$userId:$normalized"
        }
    }
}

class SavedAccountStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        EncryptedPrefsFactory.create(
            context = context.applicationContext,
            encryptedName = PREFS_NAME,
            fallbackName = FALLBACK_PREFS_NAME,
        ),
    )

    private val _accounts = MutableStateFlow(readPersistedAccountsLocked())
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    @Synchronized
    fun currentAccounts(): List<SavedAccount> = _accounts.value

    @Synchronized
    fun findByKey(accountKey: String): SavedAccount? {
        val cleanKey = accountKey.trim()
        if (cleanKey.isEmpty()) return null
        return _accounts.value.firstOrNull { it.accountKey == cleanKey }
    }

    @Synchronized
    fun upsert(
        serverUrl: String,
        user: UserDto,
        accessToken: String,
        refreshToken: String? = null,
        tokenExpiresAtMs: Long = 0L,
        sessionExpired: Boolean = false,
        lastUsedAt: Long = System.currentTimeMillis(),
    ): SavedAccount {
        val normalizedUrl = ServerUrlUtils.normalizeServerUrl(serverUrl)
        val key = SavedAccount.buildAccountKey(normalizedUrl, user.id)
        val list = _accounts.value.toMutableList()
        val existingIdx = list.indexOfFirst { it.accountKey == key }
        val existing = if (existingIdx >= 0) list[existingIdx] else null

        val cleanAccess = if (sessionExpired) "" else accessToken.trim()
        val cleanRefresh = when {
            sessionExpired -> null
            !refreshToken.isNullOrBlank() -> refreshToken.trim()
            else -> existing?.refreshToken?.takeIf { it.isNotBlank() }
        }
        val resolvedExpiresAt = when {
            sessionExpired -> 0L
            tokenExpiresAtMs > 0L -> tokenExpiresAtMs
            else -> existing?.tokenExpiresAtMs ?: 0L
        }

        val updated = SavedAccount(
            accountKey = key,
            serverUrl = normalizedUrl,
            user = user,
            accessToken = cleanAccess,
            refreshToken = cleanRefresh,
            tokenExpiresAtMs = resolvedExpiresAt,
            sessionExpired = sessionExpired,
            lastUsedAt = lastUsedAt,
        )
        if (existingIdx >= 0) {
            list[existingIdx] = updated
        } else {
            list.add(updated)
        }
        persistSortedLocked(list)
        return updated
    }

    @Synchronized
    fun syncTokens(
        accountKey: String,
        accessToken: String,
        refreshToken: String?,
        tokenExpiresAtMs: Long,
    ): Boolean {
        val cleanKey = accountKey.trim()
        val cleanAccess = accessToken.trim()
        if (cleanKey.isEmpty() || cleanAccess.isEmpty()) return false
        val list = _accounts.value.toMutableList()
        val idx = list.indexOfFirst { it.accountKey == cleanKey }
        if (idx < 0) return false
        val existing = list[idx]
        list[idx] = existing.copy(
            accessToken = cleanAccess,
            refreshToken = refreshToken?.trim()?.ifBlank { null } ?: existing.refreshToken,
            tokenExpiresAtMs = if (tokenExpiresAtMs > 0L) tokenExpiresAtMs else existing.tokenExpiresAtMs,
            sessionExpired = false,
        )
        persistSortedLocked(list)
        return true
    }

    @Synchronized
    fun syncUser(accountKey: String, user: UserDto): Boolean {
        val cleanKey = accountKey.trim()
        if (cleanKey.isEmpty()) return false
        val list = _accounts.value.toMutableList()
        val idx = list.indexOfFirst { it.accountKey == cleanKey }
        if (idx < 0) return false
        list[idx] = list[idx].copy(user = user)
        persistSortedLocked(list)
        return true
    }

    @Synchronized
    fun markExpired(accountKey: String): Boolean {
        val cleanKey = accountKey.trim()
        if (cleanKey.isEmpty()) return false
        val list = _accounts.value.toMutableList()
        val idx = list.indexOfFirst { it.accountKey == cleanKey }
        if (idx < 0) return false
        list[idx] = list[idx].copy(
            accessToken = "",
            refreshToken = null,
            tokenExpiresAtMs = 0L,
            sessionExpired = true,
        )
        persistSortedLocked(list)
        return true
    }

    @Synchronized
    fun touchLastUsed(accountKey: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val cleanKey = accountKey.trim()
        if (cleanKey.isEmpty()) return false
        val list = _accounts.value.toMutableList()
        val idx = list.indexOfFirst { it.accountKey == cleanKey }
        if (idx < 0) return false
        list[idx] = list[idx].copy(lastUsedAt = nowMs)
        persistSortedLocked(list)
        return true
    }

    @Synchronized
    fun remove(accountKey: String): Boolean {
        val cleanKey = accountKey.trim()
        if (cleanKey.isEmpty()) return false
        val next = _accounts.value.filterNot { it.accountKey == cleanKey }
        if (next.size == _accounts.value.size) return false
        persistSortedLocked(next)
        return true
    }

    private fun readPersistedAccountsLocked(): List<SavedAccount> {
        val raw = prefs.getString(KEY_SAVED_ACCOUNTS_JSON, null)?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val decoded = runCatching {
            CoreAppJson.decodeFromString(ListSerializer(SavedAccount.serializer()), raw)
        }.getOrDefault(emptyList())
        return decoded
            .filter { it.accountKey.isNotBlank() && it.serverUrl.isNotBlank() }
            .distinctBy { it.accountKey }
            .sortedByDescending { it.lastUsedAt }
    }

    private fun persistSortedLocked(items: List<SavedAccount>) {
        val sorted = items.sortedByDescending { it.lastUsedAt }
        val encoded = CoreAppJson.encodeToString(ListSerializer(SavedAccount.serializer()), sorted)
        prefs.edit().putString(KEY_SAVED_ACCOUNTS_JSON, encoded).apply()
        _accounts.value = sorted
    }

    companion object {
        const val PREFS_NAME = "gugachat_saved_accounts"
        const val FALLBACK_PREFS_NAME = "gugachat_saved_accounts_fallback"
        private const val KEY_SAVED_ACCOUNTS_JSON = "saved_accounts_json"
    }
}
