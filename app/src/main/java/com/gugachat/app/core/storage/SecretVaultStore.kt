package com.gugachat.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecretVaultStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(createEncryptedOrFallbackPrefs(context.applicationContext))

    private val _secretsFlow = MutableStateFlow(readSecretsSnapshotLocked())
    val secretsFlow: StateFlow<Map<String, String>> = _secretsFlow.asStateFlow()

    @Synchronized
    fun getSecret(key: String): String {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return ""
        return prefs.getString(cleanKey, null).orEmpty()
    }

    @Synchronized
    fun setSecret(key: String, value: String?) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return
        val normalized = normalizeSecretValue(cleanKey, value)
        val editor = prefs.edit()
        if (normalized.isEmpty()) {
            editor.remove(cleanKey)
        } else {
            editor.putString(cleanKey, normalized)
        }
        editor.apply()
        _secretsFlow.value = readSecretsSnapshotLocked()
    }

    @Synchronized
    fun clearSecret(key: String) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return
        prefs.edit().remove(cleanKey).apply()
        _secretsFlow.value = readSecretsSnapshotLocked()
    }

    @Synchronized
    fun clearAll() {
        val editor = prefs.edit()
        for (key in collectKnownAndStoredKeysLocked()) {
            editor.remove(key)
        }
        editor.apply()
        _secretsFlow.value = emptyMap()
    }

    fun getRemoteEmbeddingApiKey(): String = getSecret(KEY_REMOTE_EMBEDDING_API_KEY)
    fun setRemoteEmbeddingApiKey(value: String?) = setSecret(KEY_REMOTE_EMBEDDING_API_KEY, value)

    fun getRemoteRerankApiKey(): String = getSecret(KEY_REMOTE_RERANK_API_KEY)
    fun setRemoteRerankApiKey(value: String?) = setSecret(KEY_REMOTE_RERANK_API_KEY, value)

    fun getWebDavPassword(): String = getSecret(KEY_WEBDAV_PASSWORD)
    fun setWebDavPassword(value: String?) = setSecret(KEY_WEBDAV_PASSWORD, value)

    fun getWebDavSyncPassphrase(): String = getSecret(KEY_WEBDAV_SYNC_PASSPHRASE)
    fun setWebDavSyncPassphrase(value: String?) = setSecret(KEY_WEBDAV_SYNC_PASSPHRASE, value)

    @Synchronized
    fun exportSecretsMap(): Map<String, String> = readSecretsSnapshotLocked()

    @Synchronized
    fun importSecretsMap(
        map: Map<String, String>,
        overwriteExisting: Boolean = true,
    ): Int {
        if (map.isEmpty()) return 0
        val editor = prefs.edit()
        var imported = 0
        for ((rawKey, rawValue) in map) {
            val cleanKey = rawKey.trim()
            if (cleanKey.isEmpty() || isInternalCryptoKey(cleanKey)) continue
            val normalized = normalizeSecretValue(cleanKey, rawValue)
            if (normalized.isEmpty()) continue
            if (!overwriteExisting && prefs.getString(cleanKey, null).orEmpty().isNotEmpty()) {
                continue
            }
            editor.putString(cleanKey, normalized)
            imported += 1
        }
        if (imported > 0) {
            editor.apply()
            _secretsFlow.value = readSecretsSnapshotLocked()
        }
        return imported
    }

    private fun readSecretsSnapshotLocked(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (key in KNOWN_KEYS) {
            val value = prefs.getString(key, null)
            if (!value.isNullOrEmpty()) {
                out[key] = value
            }
        }
        val extraEntries = runCatching { prefs.all }.getOrNull().orEmpty()
        for ((key, rawValue) in extraEntries) {
            if (key.isBlank() || key in out || isInternalCryptoKey(key)) continue
            val strVal = rawValue as? String ?: continue
            if (strVal.isNotEmpty()) {
                out[key] = strVal
            }
        }
        return out
    }

    private fun collectKnownAndStoredKeysLocked(): Set<String> {
        val keys = LinkedHashSet<String>(KNOWN_KEYS)
        val stored = runCatching { prefs.all.keys }.getOrNull().orEmpty()
        for (k in stored) {
            if (!isInternalCryptoKey(k)) {
                keys.add(k)
            }
        }
        return keys
    }

    private fun normalizeSecretValue(key: String, rawValue: String?): String {
        val value = rawValue ?: return ""
        return if (key in TRIMMED_API_KEYS) {
            value.trim()
        } else {
            value.takeIf { it.isNotBlank() } ?: ""
        }
    }

    companion object {
        const val PREFS_NAME = "gugachat_secret_vault"
        const val FALLBACK_PREFS_NAME = "gugachat_secret_vault_fallback"

        const val KEY_REMOTE_EMBEDDING_API_KEY = "remoteEmbeddingApiKey"
        const val KEY_REMOTE_RERANK_API_KEY = "remoteRerankApiKey"
        const val KEY_WEBDAV_PASSWORD = "webDavPassword"
        const val KEY_WEBDAV_SYNC_PASSPHRASE = "webDavSyncPassphrase"

        val KNOWN_KEYS: List<String> = listOf(
            KEY_REMOTE_EMBEDDING_API_KEY,
            KEY_REMOTE_RERANK_API_KEY,
            KEY_WEBDAV_PASSWORD,
            KEY_WEBDAV_SYNC_PASSPHRASE,
        )

        private val TRIMMED_API_KEYS = setOf(
            KEY_REMOTE_EMBEDDING_API_KEY,
            KEY_REMOTE_RERANK_API_KEY,
        )

        private fun isInternalCryptoKey(key: String): Boolean =
            key.startsWith("__androidx_security_crypto_")

        private fun createEncryptedOrFallbackPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Throwable) {
                context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }
}
