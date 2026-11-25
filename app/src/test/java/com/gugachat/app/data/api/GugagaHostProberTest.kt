package com.gugachat.app.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.TimeUnit

class GugagaHostProberTest {
    private lateinit var fastServer: MockWebServer
    private lateinit var slowServer: MockWebServer
    private lateinit var brokenServer: MockWebServer

    @Before
    fun setUp() {
        fastServer = MockWebServer().apply { start() }
        slowServer = MockWebServer().apply { start() }
        brokenServer = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        fastServer.shutdown()
        slowServer.shutdown()
        brokenServer.shutdown()
    }

    @Test
    fun probeAll_measuresConcurrentLatencyAndSelectsFastestReachable() = runBlocking {
        // Warm up OkHttp dispatcher/classloader before measuring simulated latency delta
        fastServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val prober = GugagaHostProber(OkHttpClient())
        prober.probeHost(GugagaPresetHost("warm", "warm.example.com", fastServer.url("/").toString().trimEnd('/')))

        fastServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"code":0,"data":{"site_name":"GuGuGa Fast","online_chat_enabled":true}}"""),
        )
        slowServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeadersDelay(250, TimeUnit.MILLISECONDS)
                .setBody("""{"code":0,"data":{"site_name":"GuGuGa Slow","online_chat_enabled":true}}"""),
        )
        brokenServer.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val hosts = listOf(
            GugagaPresetHost("slow", "slow.example.com", slowServer.url("/").toString().trimEnd('/')),
            GugagaPresetHost("broken", "broken.example.com", brokenServer.url("/").toString().trimEnd('/')),
            GugagaPresetHost("fast", "fast.example.com", fastServer.url("/").toString().trimEnd('/')),
        )

        val incremental = Collections.synchronizedList(mutableListOf<HostLatencyResult>())
        val results = prober.probeAll(hosts) { incremental += it }

        assertEquals(3, results.size)
        assertEquals(3, incremental.size)

        val slowRes = results.first { it.host.id == "slow" }
        val brokenRes = results.first { it.host.id == "broken" }
        val fastRes = results.first { it.host.id == "fast" }

        assertEquals(HostProbeStatus.REACHABLE, slowRes.status)
        assertEquals(HostProbeStatus.FAILED, brokenRes.status)
        assertEquals(HostProbeStatus.REACHABLE, fastRes.status)
        assertEquals("GuGuGa Fast", fastRes.publicSettings?.site_name)
        assertTrue((slowRes.latencyMs ?: 0L) > (fastRes.latencyMs ?: 0L))

        val fastest = GugagaHosts.pickFastestReachable(results)
        assertNotNull(fastest)
        assertEquals("fast", fastest?.host?.id)
    }

    @Test
    fun presetHostDetection_matchesAll3Domains() {
        assertEquals(3, GugagaHosts.PRESETS.size)
        assertTrue(GugagaHosts.isPresetUrl("https://v2.aicode2api.cc"))
        assertTrue(GugagaHosts.isPresetUrl("www.aicode2api.cc/api/v1/"))
        assertTrue(GugagaHosts.isPresetUrl("api.aicode2api.cc"))
        assertFalse(GugagaHosts.isPresetUrl("https://selfhosted.example.com"))
    }
}
