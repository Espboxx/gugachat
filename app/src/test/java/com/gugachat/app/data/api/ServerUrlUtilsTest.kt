package com.gugachat.app.data.api

import com.gugachat.app.core.util.ServerUrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlUtilsTest {

    @Test
    fun normalizeServerUrl_addsHttpsStripsTrailingSlashAndApiPrefix() {
        assertEquals("https://chat.example.com", ServerUrlUtils.normalizeServerUrl("chat.example.com/"))
        assertEquals("https://chat.example.com", ServerUrlUtils.normalizeServerUrl("https://chat.example.com/api/v1"))
        assertEquals("https://chat.example.com", ServerUrlUtils.normalizeServerUrl("https://chat.example.com/api/v1/online-chat"))
        assertEquals("http://127.0.0.1:8080/sub", ServerUrlUtils.normalizeServerUrl("http://127.0.0.1:8080/sub/api/v1/"))
    }

    @Test
    fun normalizeServerUrl_rejectsBlankAndInvalidUrls() {
        assertEquals("", ServerUrlUtils.normalizeServerUrl(""))
        assertEquals("", ServerUrlUtils.normalizeServerUrl("   "))
        assertEquals("", ServerUrlUtils.normalizeServerUrl("ftp://example.com"))
        assertEquals("", ServerUrlUtils.normalizeServerUrl("http://exa mple.com"))
    }

    @Test
    fun buildApiUrlAndWebLoginUrl_constructExpectedEndpoints() {
        assertEquals(
            "https://api.example.com/api/v1/online-chat/capabilities",
            ServerUrlUtils.buildApiUrl("api.example.com/api/v1", "/online-chat/capabilities"),
        )
        assertEquals(
            "https://api.example.com/api/v1/auth/login",
            ServerUrlUtils.buildApiUrl("https://api.example.com/", "auth/login"),
        )
        assertEquals(
            "https://api.example.com/login",
            ServerUrlUtils.buildWebLoginUrl("https://api.example.com/api/v1"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlUtils.buildApiUrl("", "/auth/me")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlUtils.buildWebLoginUrl("   ")
        }
    }

    @Test
    fun resolveServerAssetUrlAndTimingPaths() {
        assertEquals(
            "https://example.com/assets/logo.png",
            ServerUrlUtils.resolveServerAssetUrl("https://example.com/api/v1", "/assets/logo.png"),
        )
        assertEquals(
            "data:image/png;base64,AAAA",
            ServerUrlUtils.resolveServerAssetUrl("https://example.com", "data:image/png;base64,AAAA"),
        )
        assertNull(ServerUrlUtils.resolveServerAssetUrl("https://example.com", "   "))

        assertTrue(ServerUrlUtils.isUserTimingApiPath("https://example.com/api/v1/user/profile?timezone=UTC"))
        assertTrue(ServerUrlUtils.isUserTimingApiPath("/auth/me"))
        assertFalse(ServerUrlUtils.isUserTimingApiPath("https://example.com/api/v1/online-chat/completions"))
        assertFalse(ServerUrlUtils.isUserTimingApiPath("/online-images/generations"))
    }

    @Test
    fun isPrivateLanOrLocalhost_detectsLoopbackLanAndRejectsPublicHosts() {
        assertTrue(ServerUrlUtils.isPrivateLanOrLocalhost("http://127.0.0.1:11434/v1"))
        assertTrue(ServerUrlUtils.isPrivateLanOrLocalhost("http://localhost:8080/v1"))
        assertTrue(ServerUrlUtils.isPrivateLanOrLocalhost("http://[::1]:11434/v1"))
        assertTrue(ServerUrlUtils.isPrivateLanOrLocalhost("http://mac-mini.local:11434/v1"))
        assertTrue(ServerUrlUtils.isPrivateLanOrLocalhost("http://192.168.1.50:11434/v1"))
        assertTrue(ServerUrlUtils.isPrivateLanOrLocalhost("http://10.0.0.12:8000/v1"))
        assertTrue(ServerUrlUtils.isPrivateLanOrLocalhost("http://172.16.4.2:8000/v1"))
        assertFalse(ServerUrlUtils.isPrivateLanOrLocalhost("http://172.32.0.1:8000/v1"))
        assertFalse(ServerUrlUtils.isPrivateLanOrLocalhost("https://api.openai.com/v1"))
        assertFalse(ServerUrlUtils.isPrivateLanOrLocalhost(""))
    }
}
