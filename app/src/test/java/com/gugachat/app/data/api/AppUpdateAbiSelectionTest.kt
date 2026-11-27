package com.gugachat.app.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdateAbiSelectionTest {

    private val arm64Sha = "dccaa277c1425072842a847386b59aecfe1885660719f516604c8ea4e69cc597"
    private val x8664Sha = "654ec3c97b517511af80e22cac6ad258c6a801f8d76b00b7a75124b71c862701"
    private val universalSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Test
    fun defaultSupportedAbis_handlesNullBuildSupportedAbisSafelyOnJvm() {
        val abis = AppUpdateService.defaultSupportedAbis()
        assertEquals(listOf("arm64-v8a"), abis)
    }

    @Test
    fun classifyApkAbi_distinguishesAllAbisAndUniversalTokens() {
        assertEquals("arm64-v8a", AppUpdateService.classifyApkAbi("GuGuChat-v0.3.7-arm64-v8a.apk"))
        assertEquals("armeabi-v7a", AppUpdateService.classifyApkAbi("GuGuChat-v0.3.8-armeabi-v7a.apk"))
        assertEquals("x86_64", AppUpdateService.classifyApkAbi("GuGuChat-v0.3.7-x86_64.apk"))
        assertEquals("x86", AppUpdateService.classifyApkAbi("GuGuChat-v0.3.8-x86.apk"))
        assertEquals("universal", AppUpdateService.classifyApkAbi("GuGuChat-v0.3.8-universal.apk"))
        assertEquals("universal", AppUpdateService.classifyApkAbi("GuGuChat-v0.3.6-android.apk"))
        assertEquals(
            "x86_64",
            AppUpdateService.classifyApkAbi("https://github.com/Espboxx/gugachat/releases/download/v0.3.7/GuGuChat-v0.3.7-x86_64.apk?raw=1"),
        )
    }

    @Test
    fun parseReleasePayload_manifestAbiApks_selectsArm64AndX8664Accurately() {
        val manifestJson = """
            {
              "tag_name": "v0.3.7",
              "version_name": "0.3.7",
              "name": "GuGuChat v0.3.7",
              "html_url": "https://github.com/Espboxx/gugachat/releases/tag/v0.3.7",
              "apk_name": "GuGuChat-v0.3.7-arm64-v8a.apk",
              "apk_size": 25276895,
              "apk_sha256": "$arm64Sha",
              "apk_url": "https://github.com/Espboxx/gugachat/releases/download/v0.3.7/GuGuChat-v0.3.7-arm64-v8a.apk",
              "abi_apks": {
                "arm64-v8a": {
                  "apk_name": "GuGuChat-v0.3.7-arm64-v8a.apk",
                  "apk_size": 25276895,
                  "apk_sha256": "$arm64Sha",
                  "apk_url": "https://github.com/Espboxx/gugachat/releases/download/v0.3.7/GuGuChat-v0.3.7-arm64-v8a.apk"
                },
                "x86_64": {
                  "apk_name": "GuGuChat-v0.3.7-x86_64.apk",
                  "apk_size": 27974019,
                  "apk_sha256": "$x8664Sha",
                  "apk_url": "https://github.com/Espboxx/gugachat/releases/download/v0.3.7/GuGuChat-v0.3.7-x86_64.apk"
                }
              }
            }
        """.trimIndent()

        // 1. Standard arm64 device
        val armRes = AppUpdateService.parseReleasePayload(
            bodyText = manifestJson,
            currentVersion = "0.3.6",
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        ) as UpdateCheckResult.UpdateAvailable
        assertEquals("arm64-v8a", armRes.info.resolvedAbi)
        assertEquals("GuGuChat-v0.3.7-arm64-v8a.apk", armRes.info.apkFileName)
        assertEquals(25276895L, armRes.info.apkSizeBytes)
        assertEquals(arm64Sha, armRes.info.apkSha256)

        // 2. Pure x86_64 emulator
        val x8664Res = AppUpdateService.parseReleasePayload(
            bodyText = manifestJson,
            currentVersion = "0.3.6",
            supportedAbis = listOf("x86_64"),
        ) as UpdateCheckResult.UpdateAvailable
        assertEquals("x86_64", x8664Res.info.resolvedAbi)
        assertEquals("GuGuChat-v0.3.7-x86_64.apk", x8664Res.info.apkFileName)
        assertEquals(27974019L, x8664Res.info.apkSizeBytes)
        assertEquals(x8664Sha, x8664Res.info.apkSha256)
        assertTrue(x8664Res.info.apkDownloadUrl!!.endsWith("GuGuChat-v0.3.7-x86_64.apk"))

        // 3. ARM-translated x86_64 emulator prioritizes native x86_64 over arm64-v8a
        val translatedRes = AppUpdateService.parseReleasePayload(
            bodyText = manifestJson,
            currentVersion = "0.3.6",
            supportedAbis = listOf("x86_64", "arm64-v8a"),
        ) as UpdateCheckResult.UpdateAvailable
        assertEquals("x86_64", translatedRes.info.resolvedAbi)
        assertEquals("GuGuChat-v0.3.7-x86_64.apk", translatedRes.info.apkFileName)
    }

    @Test
    fun parseReleasePayload_armTranslatedEmulatorFallsBackToArm64WhenX8664Absent() {
        val armOnlyManifest = """
            {
              "tag_name": "v0.3.7",
              "version_name": "0.3.7",
              "html_url": "https://github.com/Espboxx/gugachat/releases/tag/v0.3.7",
              "apk_name": "GuGuChat-v0.3.7-arm64-v8a.apk",
              "apk_size": 25276895,
              "apk_sha256": "$arm64Sha",
              "apk_url": "https://github.com/Espboxx/gugachat/releases/download/v0.3.7/GuGuChat-v0.3.7-arm64-v8a.apk"
            }
        """.trimIndent()

        val translatedRes = AppUpdateService.parseReleasePayload(
            bodyText = armOnlyManifest,
            currentVersion = "0.3.6",
            supportedAbis = listOf("x86_64", "arm64-v8a"),
        ) as UpdateCheckResult.UpdateAvailable

        assertEquals("arm64-v8a", translatedRes.info.resolvedAbi)
        assertEquals("GuGuChat-v0.3.7-arm64-v8a.apk", translatedRes.info.apkFileName)
        assertEquals(arm64Sha, translatedRes.info.apkSha256)
    }

    @Test
    fun parseReleasePayload_universalFallbackPreservesLegacyAndUniversalApks() {
        val legacyJson = """
            {
              "tag_name": "v0.3.6",
              "version_name": "0.3.6",
              "html_url": "https://github.com/Espboxx/gugachat/releases/tag/v0.3.6",
              "assets": [
                {
                  "name": "GuGuChat-v0.3.6-android.apk",
                  "size": 63300000,
                  "digest": "sha256:$universalSha",
                  "browser_download_url": "https://github.com/Espboxx/gugachat/releases/download/v0.3.6/GuGuChat-v0.3.6-android.apk"
                }
              ]
            }
        """.trimIndent()

        val res = AppUpdateService.parseReleasePayload(
            bodyText = legacyJson,
            currentVersion = "0.3.5",
            supportedAbis = listOf("x86_64"),
        ) as UpdateCheckResult.UpdateAvailable
        assertEquals("universal", res.info.resolvedAbi)
        assertEquals("GuGuChat-v0.3.6-android.apk", res.info.apkFileName)
        assertEquals(universalSha, res.info.apkSha256)
    }

    @Test
    fun parseReleasePayload_incompatibleAbiSplitsClearsDownloadFieldsForCleanWebFallback() {
        val githubArmOnlyRelease = """
            {
              "tag_name": "v0.4.0",
              "version_name": "0.4.0",
              "html_url": "https://github.com/Espboxx/gugachat/releases/tag/v0.4.0",
              "assets": [
                {
                  "name": "GuGuChat-v0.4.0-arm64-v8a.apk",
                  "size": 25000000,
                  "browser_download_url": "https://github.com/Espboxx/gugachat/releases/download/v0.4.0/GuGuChat-v0.4.0-arm64-v8a.apk"
                },
                {
                  "name": "GuGuChat-v0.4.0-armeabi-v7a.apk",
                  "size": 23000000,
                  "browser_download_url": "https://github.com/Espboxx/gugachat/releases/download/v0.4.0/GuGuChat-v0.4.0-armeabi-v7a.apk"
                }
              ],
              "cn_apk_urls": ["https://oss.example.cn/GuGuChat-v0.4.0-arm64-v8a.apk"]
            }
        """.trimIndent()

        val pureX8664 = AppUpdateService.parseReleasePayload(
            bodyText = githubArmOnlyRelease,
            currentVersion = "0.3.7",
            supportedAbis = listOf("x86_64"),
        ) as UpdateCheckResult.UpdateAvailable

        assertNull(pureX8664.info.apkDownloadUrl)
        assertNull(pureX8664.info.apkFileName)
        assertEquals(0L, pureX8664.info.apkSizeBytes)
        assertNull(pureX8664.info.apkSha256)
        assertNull(pureX8664.info.resolvedAbi)
        assertTrue(pureX8664.info.cnApkUrls.isEmpty())
        assertTrue(
            AppUpdateService.buildApkDownloadCandidates(pureX8664.info, UpdateChannel.AUTO, preferCn = true).isEmpty(),
        )
        assertEquals(
            "https://github.com/Espboxx/gugachat/releases/tag/v0.4.0",
            AppUpdateService.resolveManualDownloadUrl(pureX8664.info, UpdateChannel.AUTO, preferCn = true),
        )
    }

    @Test
    fun parseReleasePayload_githubAssetsSelectsExactAbiRegardlessOfAssetOrdering() {
        val githubMultiAbi = """
            {
              "tag_name": "v0.4.0",
              "version_name": "0.4.0",
              "html_url": "https://github.com/Espboxx/gugachat/releases/tag/v0.4.0",
              "assets": [
                {
                  "name": "GuGuChat-v0.4.0-x86_64.apk",
                  "size": 28000000,
                  "digest": "sha256:$x8664Sha",
                  "browser_download_url": "https://github.com/Espboxx/gugachat/releases/download/v0.4.0/GuGuChat-v0.4.0-x86_64.apk"
                },
                {
                  "name": "GuGuChat-v0.4.0-arm64-v8a.apk",
                  "size": 25000000,
                  "digest": "sha256:$arm64Sha",
                  "browser_download_url": "https://github.com/Espboxx/gugachat/releases/download/v0.4.0/GuGuChat-v0.4.0-arm64-v8a.apk"
                }
              ]
            }
        """.trimIndent()

        val armRes = AppUpdateService.parseReleasePayload(
            bodyText = githubMultiAbi,
            currentVersion = "0.3.7",
            supportedAbis = listOf("arm64-v8a"),
        ) as UpdateCheckResult.UpdateAvailable
        assertEquals("arm64-v8a", armRes.info.resolvedAbi)
        assertEquals("GuGuChat-v0.4.0-arm64-v8a.apk", armRes.info.apkFileName)
        assertEquals(arm64Sha, armRes.info.apkSha256)
    }

    @Test
    fun checkLatestRelease_forwardsSupportedAbisOverNetwork() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val manifestFile = File("docs/latest-release.json")
            val body = if (manifestFile.isFile) {
                manifestFile.readText()
            } else {
                File("../docs/latest-release.json").readText()
            }
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val res = AppUpdateService.checkLatestRelease(
                httpClient = OkHttpClient(),
                currentVersion = "0.3.6",
                endpointUrl = server.url("/latest-release.json").toString(),
                supportedAbis = listOf("x86_64"),
            ) as UpdateCheckResult.UpdateAvailable

            assertEquals("0.3.7", res.info.versionName)
            assertEquals("x86_64", res.info.resolvedAbi)
            assertEquals("GuGuChat-v0.3.7-x86_64.apk", res.info.apkFileName)
            assertEquals(27974019L, res.info.apkSizeBytes)
            assertEquals(x8664Sha, res.info.apkSha256)
        } finally {
            server.shutdown()
        }
    }
}
