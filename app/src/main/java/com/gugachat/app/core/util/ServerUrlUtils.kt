package com.gugachat.app.core.util

import java.net.URI

object ServerUrlUtils {
    private val ANY_SCHEME_RE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val DATA_IMG_RE = Regex("^data:image/[a-z0-9.+-]+;base64,", RegexOption.IGNORE_CASE)

    /**
     * Normalizes a user-supplied sub2api-auto server URL to its base origin/path
     * without trailing slashes or `/api/v1`.
     */
    fun normalizeServerUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (ANY_SCHEME_RE.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return ""
        }
        val scheme = uri.scheme?.lowercase() ?: return ""
        if (scheme != "http" && scheme != "https") return ""
        val host = uri.host ?: return ""
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        var path = (uri.rawPath ?: "").trimEnd('/')
        val apiIdx = path.indexOf("/api/v1")
        if (apiIdx >= 0 && (apiIdx + "/api/v1".length == path.length || path[apiIdx + "/api/v1".length] == '/')) {
            path = path.substring(0, apiIdx)
        }
        return "$scheme://$host$portPart$path"
    }

    /**
     * Builds an absolute `/api/v1/...` URL from a normalized server URL and endpoint path.
     */
    fun buildApiUrl(serverUrl: String, path: String): String {
        val base = normalizeServerUrl(serverUrl)
        require(base.isNotEmpty()) { "Server URL is not configured" }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return if (cleanPath == "/api/v1" || cleanPath.startsWith("/api/v1/")) {
            "$base$cleanPath"
        } else {
            "$base/api/v1$cleanPath"
        }
    }

    /**
     * Builds an absolute web login URL from a normalized server URL and web route path.
     */
    fun buildWebLoginUrl(serverUrl: String, loginPath: String = "/login"): String {
        val base = normalizeServerUrl(serverUrl)
        require(base.isNotEmpty()) { "Server URL is not configured" }
        val cleanPath = if (loginPath.startsWith("/")) loginPath else "/$loginPath"
        return "$base$cleanPath"
    }

    /**
     * Resolves a site logo or relative asset path against the connected server base URL.
     */
    fun resolveServerAssetUrl(serverUrl: String, rawUrl: String?): String? {
        val valTrimmed = rawUrl?.trim().orEmpty()
        if (valTrimmed.isEmpty()) return null
        if (DATA_IMG_RE.containsMatchIn(valTrimmed)) return valTrimmed
        if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(valTrimmed)) return valTrimmed
        val base = normalizeServerUrl(serverUrl)
        if (base.isEmpty()) return null
        return if (valTrimmed.startsWith("/")) "$base$valTrimmed" else "$base/$valTrimmed"
    }

    /**
     * Normalizes an API request path and checks whether `X-User-UI-Request: 1` should be sent.
     */
    fun isUserTimingApiPath(requestUrl: String): Boolean {
        val noQuery = requestUrl.split('?', '#', limit = 2)[0].trim()
        if (noQuery.isEmpty()) return false
        var path = noQuery
        if (ANY_SCHEME_RE.containsMatchIn(path)) {
            path = try {
                URI(path).rawPath ?: ""
            } catch (_: Exception) {
                ""
            }
        }
        if (path == "/api/v1" || path.startsWith("/api/v1/")) {
            path = path.removePrefix("/api/v1").ifEmpty { "/" }
        } else if (!path.startsWith("/")) {
            path = "/$path"
        }
        return path == "/auth/me" ||
            path == "/auth/revoke-all-sessions" ||
            path == "/auth/oauth/bind-token" ||
            path == "/user" || path.startsWith("/user/") ||
            path == "/keys" || path.startsWith("/keys/") ||
            path == "/groups/available" || path == "/groups/rates" ||
            path == "/channels/available" ||
            path == "/usage" || path.startsWith("/usage/") ||
            path == "/announcements" || path.startsWith("/announcements/") ||
            path == "/redeem" || path.startsWith("/redeem/") ||
            path == "/subscriptions" || path.startsWith("/subscriptions/")
    }
}
