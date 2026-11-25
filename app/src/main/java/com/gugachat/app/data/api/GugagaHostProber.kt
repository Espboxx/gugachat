package com.gugachat.app.data.api

import com.gugachat.app.core.util.ServerUrlUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class GugagaPresetHost(
    val id: String,
    val label: String,
    val url: String,
)

enum class HostProbeStatus {
    IDLE,
    TESTING,
    REACHABLE,
    FAILED,
}

data class HostLatencyResult(
    val host: GugagaPresetHost,
    val status: HostProbeStatus = HostProbeStatus.IDLE,
    val latencyMs: Long? = null,
    val publicSettings: PublicSettingsDto? = null,
    val error: String? = null,
)

object GugagaHosts {
    val PRESETS: List<GugagaPresetHost> = listOf(
        GugagaPresetHost(id = "v2", label = "v2.aicode2api.cc", url = "https://v2.aicode2api.cc"),
        GugagaPresetHost(id = "www", label = "www.aicode2api.cc", url = "https://www.aicode2api.cc"),
        GugagaPresetHost(id = "api", label = "api.aicode2api.cc", url = "https://api.aicode2api.cc"),
    )

    fun isPresetUrl(rawUrl: String): Boolean {
        val norm = ServerUrlUtils.normalizeServerUrl(rawUrl)
        if (norm.isEmpty()) return false
        return PRESETS.any { ServerUrlUtils.normalizeServerUrl(it.url).equals(norm, ignoreCase = true) }
    }

    fun pickFastestReachable(results: List<HostLatencyResult>): HostLatencyResult? =
        results
            .filter { it.status == HostProbeStatus.REACHABLE && it.latencyMs != null }
            .minByOrNull { it.latencyMs!! }
}

class GugagaHostProber(baseClient: OkHttpClient) {
    private val probeClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun probeHost(host: GugagaPresetHost): HostLatencyResult {
        val url = runCatching { ServerUrlUtils.buildApiUrl(host.url, "/settings/public") }
            .getOrElse { err ->
                return HostLatencyResult(
                    host = host,
                    status = HostProbeStatus.FAILED,
                    error = err.message ?: "Invalid URL",
                )
            }
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        val startNs = System.nanoTime()
        return try {
            probeClient.awaitCall(req).use { resp ->
                val raw = resp.body?.string().orEmpty()
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                if (!resp.isSuccessful) {
                    return@use HostLatencyResult(
                        host = host,
                        status = HostProbeStatus.FAILED,
                        latencyMs = elapsedMs,
                        error = "HTTP ${resp.code}",
                    )
                }
                val settings = decodePublicSettingsSafe(raw)
                HostLatencyResult(
                    host = host,
                    status = HostProbeStatus.REACHABLE,
                    latencyMs = elapsedMs,
                    publicSettings = settings,
                )
            }
        } catch (e: Exception) {
            HostLatencyResult(
                host = host,
                status = HostProbeStatus.FAILED,
                error = e.message ?: "Network error",
            )
        }
    }

    suspend fun probeAll(
        hosts: List<GugagaPresetHost> = GugagaHosts.PRESETS,
        onEachCompleted: (HostLatencyResult) -> Unit = {},
    ): List<HostLatencyResult> = coroutineScope {
        hosts.map { host ->
            async {
                val res = probeHost(host)
                onEachCompleted(res)
                res
            }
        }.awaitAll()
    }

    private fun decodePublicSettingsSafe(raw: String): PublicSettingsDto? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val elem = AppJson.parseToJsonElement(trimmed)
            if (elem is JsonObject && elem.containsKey("code") && elem["code"]?.jsonPrimitive?.intOrNull == 0) {
                val data = elem["data"] ?: return@runCatching null
                AppJson.decodeFromJsonElement(PublicSettingsDto.serializer(), data)
            } else {
                AppJson.decodeFromJsonElement(PublicSettingsDto.serializer(), elem)
            }
        }.getOrNull()
    }
}
