package com.gugachat.app.core.storage

import android.content.SharedPreferences
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.domain.chat.RemoteEmbeddingConfig
import com.gugachat.app.domain.chat.RemoteRerankConfig
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.RetrievalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretVaultStoreTest {

    @Test
    fun vault_storesExportsImportsAndClearsSecretsSafely() {
        val prefs = InMemorySharedPreferences()
        val vault = SecretVaultStore(prefs)

        vault.setRemoteEmbeddingApiKey("  sk-embed-123  ")
        vault.setRemoteRerankApiKey("sk-rerank-456")
        vault.setWebDavPassword("dav-pass-!@#")
        vault.setWebDavSyncPassphrase("sync-phrase-xyz")

        assertEquals("sk-embed-123", vault.getRemoteEmbeddingApiKey())
        assertEquals("sk-rerank-456", vault.getRemoteRerankApiKey())
        assertEquals("dav-pass-!@#", vault.getWebDavPassword())
        assertEquals("sync-phrase-xyz", vault.getWebDavSyncPassphrase())

        val exported = vault.exportSecretsMap()
        assertEquals(4, exported.size)
        assertEquals("sk-embed-123", exported[SecretVaultStore.KEY_REMOTE_EMBEDDING_API_KEY])
        assertEquals("sk-rerank-456", exported[SecretVaultStore.KEY_REMOTE_RERANK_API_KEY])
        assertEquals("dav-pass-!@#", exported[SecretVaultStore.KEY_WEBDAV_PASSWORD])
        assertEquals("sync-phrase-xyz", exported[SecretVaultStore.KEY_WEBDAV_SYNC_PASSPHRASE])

        vault.clearSecret(SecretVaultStore.KEY_WEBDAV_PASSWORD)
        assertEquals("", vault.getWebDavPassword())
        assertFalse(SecretVaultStore.KEY_WEBDAV_PASSWORD in vault.exportSecretsMap())

        val restoredVault = SecretVaultStore(InMemorySharedPreferences())
        restoredVault.setWebDavPassword("existing-pass")
        val skippedCount = restoredVault.importSecretsMap(exported, overwriteExisting = false)
        assertEquals(3, skippedCount)
        assertEquals("existing-pass", restoredVault.getWebDavPassword())
        assertEquals("sk-embed-123", restoredVault.getRemoteEmbeddingApiKey())

        restoredVault.importSecretsMap(exported, overwriteExisting = true)
        assertEquals("dav-pass-!@#", restoredVault.getWebDavPassword())

        restoredVault.clearAll()
        assertTrue(restoredVault.exportSecretsMap().isEmpty())
    }

    @Test
    fun settingsStore_sanitizesRetrievalSecretsAndRoundtripsWebDavConfig() {
        val settings = RetrievalSettings(
            mode = RetrievalMode.REMOTE_API,
            remoteEmbedding = RemoteEmbeddingConfig(
                providerId = "custom",
                customBaseUrl = "https://api.example.com/v1",
                customApiKey = "sk-secret-embed",
            ),
            remoteRerank = RemoteRerankConfig(
                enabled = true,
                customBaseUrl = "https://rerank.example.com/v1",
                customApiKey = "sk-secret-rerank",
            ),
        )

        val sanitized = settings.sanitizedForPlaintextStorage()
        assertEquals("", sanitized.remoteEmbedding.customApiKey)
        assertEquals("", sanitized.remoteRerank.customApiKey)
        assertEquals("https://api.example.com/v1", sanitized.remoteEmbedding.customBaseUrl)
        assertEquals("https://rerank.example.com/v1", sanitized.remoteRerank.customBaseUrl)

        val dav = WebDavSyncConfig(
            url = "https://dav.example.com/gugachat",
            username = "alice",
            syncDirectImages = true,
            autoSyncOnBackground = true,
            lastSyncAt = 1_700_000_000_000L,
        )
        val encoded = AppJson.encodeToString(WebDavSyncConfig.serializer(), dav)
        assertEquals(dav, SettingsStore.decodeWebDavConfig(encoded))
        assertEquals(WebDavSyncConfig(), SettingsStore.decodeWebDavConfig("bad-json"))
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = LinkedHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)
        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class Editor(private val target: LinkedHashMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) target.clear()
                for ((k, v) in pending) {
                    if (v == null) target.remove(k) else target[k] = v
                }
            }
        }
    }
}
