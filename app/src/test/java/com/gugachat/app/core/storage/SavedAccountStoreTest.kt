package com.gugachat.app.core.storage

import android.content.SharedPreferences
import com.gugachat.app.data.api.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedAccountStoreTest {

    @Test
    fun upsert_dedupesByServerAndUserId_isolatesServers_andPersistsSorted() {
        val prefs = InMemorySharedPreferences()
        val store = SavedAccountStore(prefs)

        val aliceServerA = UserDto(id = 1L, username = "alice", email = "alice@a.com", balance = 12.5)
        val bobServerA = UserDto(id = 2L, username = "bob", email = "bob@a.com", balance = 3.0)
        val sameIdServerB = UserDto(id = 1L, username = "aliceB", email = "alice@b.com", balance = 99.0)

        val a1 = store.upsert(
            serverUrl = "https://alpha.example.com/",
            user = aliceServerA,
            accessToken = "tok-a1",
            refreshToken = "ref-a1",
            tokenExpiresAtMs = 100_000L,
            lastUsedAt = 1000L,
        )
        val b1 = store.upsert(
            serverUrl = "https://alpha.example.com",
            user = bobServerA,
            accessToken = "tok-b1",
            refreshToken = "ref-b1",
            tokenExpiresAtMs = 200_000L,
            lastUsedAt = 2000L,
        )
        val a2 = store.upsert(
            serverUrl = "https://beta.example.com",
            user = sameIdServerB,
            accessToken = "tok-beta",
            refreshToken = "ref-beta",
            tokenExpiresAtMs = 300_000L,
            lastUsedAt = 3000L,
        )

        assertEquals(3, store.currentAccounts().size)
        assertEquals(listOf(a2.accountKey, b1.accountKey, a1.accountKey), store.currentAccounts().map { it.accountKey })
        assertTrue(a1.accountKey != a2.accountKey)

        // Updating Alice on alpha without passing refreshToken preserves existing refreshToken
        store.upsert(
            serverUrl = "https://alpha.example.com",
            user = aliceServerA.copy(balance = 18.0),
            accessToken = "tok-a1-new",
            refreshToken = null,
            tokenExpiresAtMs = 0L,
            lastUsedAt = 4000L,
        )
        val reloaded = SavedAccountStore(prefs)
        assertEquals(3, reloaded.currentAccounts().size)
        assertEquals(a1.accountKey, reloaded.currentAccounts().first().accountKey)
        val reloadedAlice = reloaded.findByKey(a1.accountKey)
        assertNotNull(reloadedAlice)
        assertEquals("tok-a1-new", reloadedAlice!!.accessToken)
        assertEquals("ref-a1", reloadedAlice.refreshToken)
        assertEquals(100_000L, reloadedAlice.tokenExpiresAtMs)
        assertEquals(18.0, reloadedAlice.user.balance, 0.001)
    }

    @Test
    fun syncTokens_syncUser_markExpired_andRemove_areIsolatedPerAccount() {
        val prefs = InMemorySharedPreferences()
        val store = SavedAccountStore(prefs)

        val alice = store.upsert(
            serverUrl = "https://api.example.com",
            user = UserDto(id = 10L, username = "alice", email = "alice@test.com", balance = 5.0),
            accessToken = "a-access-1",
            refreshToken = "a-refresh-1",
            tokenExpiresAtMs = 50_000L,
            lastUsedAt = 100L,
        )
        val bob = store.upsert(
            serverUrl = "https://api.example.com",
            user = UserDto(id = 20L, username = "bob", email = "bob@test.com", balance = 20.0),
            accessToken = "b-access-1",
            refreshToken = "b-refresh-1",
            tokenExpiresAtMs = 60_000L,
            lastUsedAt = 200L,
        )

        // Rotating refresh token sync updates only Alice
        assertTrue(store.syncTokens(alice.accountKey, "a-access-2", "a-refresh-2", 99_000L))
        assertTrue(store.syncUser(alice.accountKey, alice.user.copy(balance = 42.5)))
        val updatedAlice = store.findByKey(alice.accountKey)!!
        assertEquals("a-access-2", updatedAlice.accessToken)
        assertEquals("a-refresh-2", updatedAlice.refreshToken)
        assertEquals(99_000L, updatedAlice.tokenExpiresAtMs)
        assertEquals(42.5, updatedAlice.user.balance, 0.001)
        assertEquals("b-access-1", store.findByKey(bob.accountKey)!!.accessToken)

        // Marking Alice expired clears Alice's tokens but preserves metadata and keeps Bob valid
        assertTrue(store.markExpired(alice.accountKey))
        val expiredAlice = store.findByKey(alice.accountKey)!!
        assertTrue(expiredAlice.sessionExpired)
        assertFalse(expiredAlice.hasValidTokens)
        assertEquals("", expiredAlice.accessToken)
        assertNull(expiredAlice.refreshToken)
        assertEquals("alice@test.com", expiredAlice.user.email)
        assertEquals("https://api.example.com", expiredAlice.serverUrl)
        assertTrue(store.findByKey(bob.accountKey)!!.hasValidTokens)

        // Re-syncing tokens clears sessionExpired flag
        assertTrue(store.syncTokens(alice.accountKey, "a-access-3", "a-refresh-3", 120_000L))
        assertTrue(store.findByKey(alice.accountKey)!!.hasValidTokens)

        // Removing Alice leaves Bob intact
        assertTrue(store.remove(alice.accountKey))
        assertNull(store.findByKey(alice.accountKey))
        assertEquals(1, store.currentAccounts().size)
        assertEquals(bob.accountKey, store.currentAccounts().first().accountKey)
    }

    @Test
    fun secureTokenStore_restoreSnapshotSkipsHookAndPreservesExactExpiry() {
        val prefs = InMemorySharedPreferences()
        val tokenStore = SecureTokenStore(prefs)
        var savedHookCount = 0
        var userHookCount = 0
        tokenStore.onTokensSaved = { savedHookCount += 1 }
        tokenStore.onUserJsonSaved = { userHookCount += 1 }

        tokenStore.restoreSnapshot(
            accessToken = "restored-access",
            refreshToken = "restored-refresh",
            tokenExpiresAtMs = 777_888L,
            userJson = """{"id":1}""",
        )
        assertEquals(0, savedHookCount)
        assertEquals(0, userHookCount)
        val snap = tokenStore.readSnapshot()
        assertEquals("restored-access", snap.accessToken)
        assertEquals("restored-refresh", snap.refreshToken)
        assertEquals(777_888L, snap.tokenExpiresAtMs)
        assertEquals("""{"id":1}""", snap.cachedUserJson)

        // Regular saveTokens & saveUserJson invoke writeback hooks
        tokenStore.saveTokens("new-access", "new-refresh", 3600L)
        tokenStore.saveUserJson("""{"id":1,"balance":9.5}""")
        assertEquals(1, savedHookCount)
        assertEquals(1, userHookCount)
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
                if (key != null) pending[key] = REMOVE_SENTINEL
            }

            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) target.clear()
                for ((k, v) in pending) {
                    if (v === REMOVE_SENTINEL) target.remove(k) else target[k] = v
                }
            }

            companion object {
                private val REMOVE_SENTINEL = Any()
            }
        }
    }
}
