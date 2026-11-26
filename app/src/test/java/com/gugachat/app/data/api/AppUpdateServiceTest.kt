package com.gugachat.app.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class AppUpdateServiceTest {

    @Test
    fun isNewerVersion_handlesSemVerEdgeCases() {
        assertTrue(AppUpdateService.isNewerVersion("v0.2", "0.1"))
        assertTrue(AppUpdateService.isNewerVersion("0.10", "0.2"))
        assertTrue(AppUpdateService.isNewerVersion("v0.1.1", "0.1"))
        assertTrue(AppUpdateService.isNewerVersion("1.0.0", "0.9.9"))
        assertTrue(AppUpdateService.isNewerVersion("0.2.0", "0.2.0-beta.1"))
        assertTrue(AppUpdateService.isNewerVersion("0.2.0-beta.2", "0.2.0-beta.1"))

        assertFalse(AppUpdateService.isNewerVersion("v0.1", "0.1"))
        assertFalse(AppUpdateService.isNewerVersion("0.1.0", "0.1"))
        assertFalse(AppUpdateService.isNewerVersion("0.0.9", "0.1"))
        assertFalse(AppUpdateService.isNewerVersion("0.2.0-beta.1", "0.2.0"))
        assertFalse(AppUpdateService.isNewerVersion("", "0.1"))
    }

    @Test
    fun checkLatestRelease_detectsNewerApkReleaseAndUpToDate() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = OkHttpClient()
            val newerJson = """
                {
                  "tag_name": "v0.2",
                  "name": "GuGuChat v0.2",
                  "body": "- 新增自动检测更新\n- 修复若干界面问题",
                  "html_url": "https://github.com/Espboxx/gugachat/releases/tag/v0.2",
                  "draft": false,
                  "prerelease": false,
                  "published_at": "2026-11-26T04:00:00Z",
                  "assets": [
                    {
                      "name": "checksums.txt",
                      "size": 128,
                      "browser_download_url": "https://github.com/Espboxx/gugachat/releases/download/v0.2/checksums.txt"
                    },
                    {
                      "name": "GuGuChat-v0.2-android.apk",
                      "size": 16777216,
                      "browser_download_url": "https://github.com/Espboxx/gugachat/releases/download/v0.2/GuGuChat-v0.2-android.apk"
                    }
                  ]
                }
            """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(newerJson))
            val res = AppUpdateService.checkLatestRelease(
                httpClient = client,
                currentVersion = "0.1",
                endpointUrl = server.url("/repos/Espboxx/gugachat/releases/latest").toString(),
            )
            assertTrue(res is UpdateCheckResult.UpdateAvailable)
            val info = (res as UpdateCheckResult.UpdateAvailable).info
            assertEquals("v0.2", info.tagName)
            assertEquals("0.2", info.versionName)
            assertEquals("GuGuChat-v0.2-android.apk", info.apkFileName)
            assertEquals(16777216L, info.apkSizeBytes)
            assertTrue(info.apkDownloadUrl!!.endsWith(".apk"))
            assertTrue(info.releaseNotes.contains("自动检测更新"))

            // Same version -> UpToDate
            server.enqueue(MockResponse().setResponseCode(200).setBody(newerJson))
            val sameRes = AppUpdateService.checkLatestRelease(
                httpClient = client,
                currentVersion = "0.2",
                endpointUrl = server.url("/repos/Espboxx/gugachat/releases/latest").toString(),
            )
            assertTrue(sameRes is UpdateCheckResult.UpToDate)

            // 404 (no releases yet) -> UpToDate gracefully
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
            val notFoundRes = AppUpdateService.checkLatestRelease(
                httpClient = client,
                currentVersion = "0.1",
                endpointUrl = server.url("/repos/Espboxx/gugachat/releases/latest").toString(),
            )
            assertTrue(notFoundRes is UpdateCheckResult.UpToDate)

            // Release without APK asset -> still UpdateAvailable with null apkDownloadUrl (manual download fallback)
            val noApkJson = """
                {
                  "tag_name": "v0.3",
                  "name": "GuGuChat v0.3",
                  "body": "Source only release",
                  "html_url": "https://github.com/Espboxx/gugachat/releases/tag/v0.3",
                  "assets": []
                }
            """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(noApkJson))
            val noApkRes = AppUpdateService.checkLatestRelease(
                httpClient = client,
                currentVersion = "0.1",
                endpointUrl = server.url("/repos/Espboxx/gugachat/releases/latest").toString(),
            )
            assertTrue(noApkRes is UpdateCheckResult.UpdateAvailable)
            assertNull((noApkRes as UpdateCheckResult.UpdateAvailable).info.apkDownloadUrl)
            assertEquals("https://github.com/Espboxx/gugachat/releases/tag/v0.3", noApkRes.info.htmlUrl)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun downloadApk_validatesZipMagicReportsProgressAndRejectsHtml() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("gugachat_update_test_").toFile()
        try {
            val client = OkHttpClient()
            val fakeApk = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(8192) { 0x42 }
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/vnd.android.package-archive")
                    .setBody(Buffer().write(fakeApk)),
            )

            val dest = File(tempDir, "gugachat-v0.2.apk")
            var lastDownloaded = 0L
            var lastTotal = 0L
            val out = AppUpdateService.downloadApk(
                httpClient = client,
                url = server.url("/download/gugachat.apk").toString(),
                destFile = dest,
            ) { dl, tot ->
                lastDownloaded = dl
                lastTotal = tot
            }
            assertTrue(out.exists())
            assertEquals(fakeApk.size.toLong(), out.length())
            assertEquals(fakeApk.size.toLong(), lastDownloaded)
            assertEquals(fakeApk.size.toLong(), lastTotal)

            // HTML error response rejected and does not leave corrupt APK
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "text/html")
                    .setBody("<html><body>Rate limited</body></html>"),
            )
            val badDest = File(tempDir, "bad.apk")
            var threw = false
            try {
                AppUpdateService.downloadApk(client, server.url("/download/bad.apk").toString(), badDest)
            } catch (_: Throwable) {
                threw = true
            }
            assertTrue(threw)
            assertFalse(badDest.exists())
        } finally {
            tempDir.deleteRecursively()
            server.shutdown()
        }
    }

    @Test
    fun parseStaticManifest_and_checkFallback_recoversFromBrokenMirror() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = OkHttpClient()
            val fakeSha = "d4df6f5cfa613eb170206969e41c874dc1e898661e971916e17014d8ae16e278"
            val manifestJson = """
                {
                  "tag_name": "v0.3.4",
                  "version_name": "0.3.4",
                  "name": "GuGuChat v0.3.4",
                  "body": "新增国内镜像更新渠道",
                  "html_url": "https://github.com/Espboxx/gugachat/releases/tag/v0.3.4",
                  "cn_download_page": "https://cn.example.com/downloads",
                  "apk_name": "GuGuChat-v0.3.4-android.apk",
                  "apk_size": 18000000,
                  "apk_sha256": "$fakeSha",
                  "apk_url": "https://github.com/Espboxx/gugachat/releases/download/v0.3.4/GuGuChat-v0.3.4-android.apk",
                  "cn_apk_urls": ["https://oss.example.cn/gugachat-v0.3.4.apk"],
                  "cn_proxies": ["https://gh-proxy.com", "https://cdn.gh-proxy.org/"]
                }
            """.trimIndent()

            // First endpoint returns 502 error, second returns HTML block page, third returns valid manifest
            server.enqueue(MockResponse().setResponseCode(502).setBody("Bad Gateway"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "text/html")
                    .setBody("<html>blocked</html>"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(manifestJson),
            )

            val endpoints = listOf(
                UpdateMetadataEndpoint("mirror-1", server.url("/m1").toString(), isGitHubApi = false),
                UpdateMetadataEndpoint("mirror-2", server.url("/m2").toString(), isGitHubApi = false),
                UpdateMetadataEndpoint("mirror-3", server.url("/m3").toString(), isGitHubApi = false),
            )
            val res = AppUpdateService.checkLatestReleaseWithFallback(
                httpClient = client,
                currentVersion = "0.3.3",
                channel = UpdateChannel.CN_MIRROR,
                endpointsOverride = endpoints,
            )
            assertTrue(res is UpdateCheckResult.UpdateAvailable)
            val info = (res as UpdateCheckResult.UpdateAvailable).info
            assertEquals("0.3.4", info.versionName)
            assertEquals(fakeSha, info.apkSha256)
            assertEquals(listOf("https://oss.example.cn/gugachat-v0.3.4.apk"), info.cnApkUrls)
            assertEquals("https://cn.example.com/downloads", info.cnDownloadPage)
            assertEquals("mirror-3", info.resolvedSourceLabel)

            // Verify candidate ordering for CN_MIRROR vs GITHUB_DIRECT
            val cnCandidates = AppUpdateService.buildApkDownloadCandidates(info, UpdateChannel.CN_MIRROR, preferCn = true)
            assertEquals("https://oss.example.cn/gugachat-v0.3.4.apk", cnCandidates.first().url)
            assertTrue(cnCandidates[1].url.startsWith("https://gh-proxy.com/https://github.com/"))
            assertEquals(info.apkDownloadUrl, cnCandidates.last().url)

            val ghCandidates = AppUpdateService.buildApkDownloadCandidates(info, UpdateChannel.GITHUB_DIRECT, preferCn = true)
            assertEquals(1, ghCandidates.size)
            assertEquals(info.apkDownloadUrl, ghCandidates.first().url)

            // Verify manual download URL respects channel
            assertEquals(
                "https://cn.example.com/downloads",
                AppUpdateService.resolveManualDownloadUrl(info, UpdateChannel.CN_MIRROR, preferCn = true),
            )
            assertEquals(
                info.htmlUrl,
                AppUpdateService.resolveManualDownloadUrl(info, UpdateChannel.GITHUB_DIRECT, preferCn = true),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun downloadApkWithFallback_rejectsTamperedSha256AndFallsBackToHealthyMirror() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("gugachat_cn_dl_test_").toFile()
        try {
            val client = OkHttpClient()
            val goodApk = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(4096) { 0x33 }
            val tamperedApk = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(4096) { 0x99.toByte() }
            val expectedSha = MessageDigest.getInstance("SHA-256")
                .digest(goodApk)
                .joinToString("") { "%02x".format(it) }

            // Mirror 1 returns tampered APK (valid ZIP header, wrong SHA-256)
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/vnd.android.package-archive")
                    .setBody(Buffer().write(tamperedApk)),
            )
            // Mirror 2 returns valid APK matching SHA-256
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/vnd.android.package-archive")
                    .setBody(Buffer().write(goodApk)),
            )

            val candidates = listOf(
                ApkDownloadCandidate("国内加速 · bad-mirror", server.url("/bad.apk").toString(), isCnChannel = true),
                ApkDownloadCandidate("国内加速 · good-mirror", server.url("/good.apk").toString(), isCnChannel = true),
            )
            val dest = File(tempDir, "gugachat-v0.3.4.apk")
            val attempts = mutableListOf<String>()
            val (outFile, winner) = AppUpdateService.downloadApkWithFallback(
                httpClient = client,
                candidates = candidates,
                destFile = dest,
                expectedSha256 = expectedSha,
                onCandidateAttempt = { cand, _, _ -> attempts.add(cand.label) },
            )
            assertTrue(outFile.exists())
            assertEquals(goodApk.size.toLong(), outFile.length())
            assertEquals("国内加速 · good-mirror", winner.label)
            assertEquals(listOf("国内加速 · bad-mirror", "国内加速 · good-mirror"), attempts)
            assertEquals(expectedSha, AppUpdateService.computeFileSha256(outFile))
        } finally {
            tempDir.deleteRecursively()
            server.shutdown()
        }
    }

    @Test
    fun updateChannel_fromWireRoundTripsAndFallsBackToAuto() {
        UpdateChannel.entries.forEach { ch ->
            assertEquals(ch, UpdateChannel.fromWire(ch.wireValue))
            assertEquals(ch, UpdateChannel.fromWire("  ${ch.wireValue.uppercase()}  "))
        }
        assertEquals(UpdateChannel.AUTO, UpdateChannel.fromWire(null))
        assertEquals(UpdateChannel.AUTO, UpdateChannel.fromWire("unknown"))
    }
}
