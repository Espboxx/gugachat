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
}
