package com.gugachat.app.data.api

import com.gugachat.app.core.util.HashUtils
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ResumableFileDownloaderTest {

    @Test
    fun downloadWithFallback_handlesFreshVerifiedDownloadAndMirrorFailover() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("resumable_dl_test_").toFile()
        try {
            val payload = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "mock-apk-body".toByteArray()
            val expectedSha = HashUtils.sha256Hex(payload)

            // First mirror returns HTML error page; second succeeds with valid bytes
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html>rate limited</html>"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/octet-stream")
                    .setBody(okio.Buffer().write(payload)),
            )

            val destFile = File(tempDir, "app.apk")
            val attempts = ArrayList<String>()
            val (outFile, winner) = ResumableFileDownloader.downloadWithFallback(
                candidates = listOf(server.url("/m1").toString(), server.url("/m2").toString()),
                labelOf = { it },
                emptyCandidatesError = "empty",
                onCandidateAttempt = { url, _, _ -> attempts += url },
            ) { url ->
                ResumableFileDownloader.downloadFile(
                    httpClient = OkHttpClient(),
                    url = url,
                    destFile = destFile,
                    options = ResumableFileDownloader.DownloadOptions(
                        resumable = false,
                        expectedSha256 = expectedSha,
                        maxBytes = 1024L * 1024L,
                        userAgent = "TestAgent/1.0",
                        verifyFirstChunk = { buf, n ->
                            if (n < 4 || buf[0] != 0x50.toByte() || buf[1] != 0x4B.toByte()) {
                                throw IllegalStateException("Invalid magic")
                            }
                        },
                    ),
                )
            }

            assertEquals(2, attempts.size)
            assertTrue(winner.endsWith("/m2"))
            assertArrayEquals(payload, outFile.readBytes())
            assertFalse(File(tempDir, "app.apk.part").exists())
        } finally {
            server.shutdown()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun downloadFile_resumes206FromPartFileAndHandles416CompletedPart() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("resumable_range_test_").toFile()
        try {
            val full = "0123456789abcdefghijklmnopqrstuvwxyz".toByteArray()
            val prefixLen = 10
            val destFile = File(tempDir, "model.onnx")
            val partFile = File(tempDir, "model.onnx.part")
            partFile.writeBytes(full.copyOfRange(0, prefixLen))

            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $prefixLen-${full.size - 1}/${full.size}")
                    .setBody(okio.Buffer().write(full.copyOfRange(prefixLen, full.size))),
            )

            val options = ResumableFileDownloader.DownloadOptions(
                resumable = true,
                expectedSha256 = HashUtils.sha256Hex(full),
                maxBytes = 1024L * 1024L,
                userAgent = "TestAgent/1.0",
                rejectHtmlFile = true,
            )
            ResumableFileDownloader.downloadFile(OkHttpClient(), server.url("/model").toString(), destFile, options)
            val recorded = server.takeRequest()
            assertEquals("bytes=$prefixLen-", recorded.getHeader("Range"))
            assertArrayEquals(full, destFile.readBytes())

            // Now test HTTP 416 when `.part` already equals total
            destFile.delete()
            partFile.writeBytes(full)
            server.enqueue(
                MockResponse()
                    .setResponseCode(416)
                    .setHeader("Content-Range", "bytes */${full.size}"),
            )
            ResumableFileDownloader.downloadFile(OkHttpClient(), server.url("/model2").toString(), destFile, options)
            assertArrayEquals(full, destFile.readBytes())
        } finally {
            server.shutdown()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun downloadFile_rejectsTruncatedStreamEvenWhenNotResumable() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("resumable_trunc_test_").toFile()
        try {
            val truncatedBytes = ByteArray(300) { 0x41 }
            server.enqueue(MockResponse().setResponseCode(200).setBody("placeholder"))

            val truncatingClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val origResp = chain.proceed(chain.request())
                    val origType = origResp.body?.contentType()
                    origResp.body?.close()
                    origResp.newBuilder()
                        .body(
                            object : okhttp3.ResponseBody() {
                                override fun contentType() = origType
                                override fun contentLength() = 1000L
                                override fun source(): okio.BufferedSource = okio.Buffer().write(truncatedBytes)
                            },
                        )
                        .build()
                }
                .build()

            val destFile = File(tempDir, "app.apk")
            val err = try {
                ResumableFileDownloader.downloadFile(
                    httpClient = truncatingClient,
                    url = server.url("/truncated").toString(),
                    destFile = destFile,
                    options = ResumableFileDownloader.DownloadOptions(
                        resumable = false,
                        maxBytes = 1024L * 1024L,
                        userAgent = "TestAgent/1.0",
                    ),
                )
                null
            } catch (e: IllegalStateException) {
                e
            }
            assertTrue(err?.message?.contains("Truncated stream (300/1000 bytes)") == true)
            assertFalse(destFile.exists())
            assertFalse(File(tempDir, "app.apk.part").exists())
        } finally {
            server.shutdown()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun downloadFile_accumulatesFourBytesBeforeVerifyFirstChunkForTwoByteReads() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("resumable_2byte_test_").toFile()
        try {
            val apkPayload = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "mock-apk-payload".toByteArray()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/vnd.android.package-archive")
                    .setChunkedBody(okio.Buffer().write(apkPayload), 2),
            )

            var verifiedLen = 0
            val destFile = File(tempDir, "chunked.apk")
            val out = ResumableFileDownloader.downloadFile(
                httpClient = OkHttpClient(),
                url = server.url("/chunked.apk").toString(),
                destFile = destFile,
                options = ResumableFileDownloader.DownloadOptions(
                    resumable = false,
                    expectedSha256 = HashUtils.sha256Hex(apkPayload),
                    maxBytes = 1024L * 1024L,
                    userAgent = "TestAgent/1.0",
                    verifyFirstChunk = { buf, n ->
                        verifiedLen = n
                        if (n < 4 || buf[0] != 0x50.toByte() || buf[1] != 0x4B.toByte() ||
                            buf[2] != 0x03.toByte() || buf[3] != 0x04.toByte()
                        ) {
                            throw IllegalStateException("Invalid APK file header")
                        }
                    },
                ),
            )
            assertTrue(verifiedLen >= 4)
            assertArrayEquals(apkPayload, out.readBytes())
        } finally {
            server.shutdown()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun downloadFile_restartsFreshOn206MissingContentRangeAndEmitsInitialExpectedTotal() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("resumable_206_missing_range_").toFile()
        try {
            val full = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toByteArray()
            val destFile = File(tempDir, "model.onnx")
            val partFile = File(tempDir, "model.onnx.part")
            partFile.writeBytes(ByteArray(10) { 0x11 })

            // First 206 response omits Content-Range -> must delete .part and restart from byte 0
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setBody(okio.Buffer().write(full.copyOfRange(10, full.size))),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(full)),
            )

            val progressEvents = ArrayList<Pair<Long, Long>>()
            ResumableFileDownloader.downloadFile(
                httpClient = OkHttpClient(),
                url = server.url("/model").toString(),
                destFile = destFile,
                options = ResumableFileDownloader.DownloadOptions(
                    resumable = true,
                    expectedSizeBytes = full.size.toLong(),
                    expectedSha256 = HashUtils.sha256Hex(full),
                    maxBytes = 1024L * 1024L,
                    userAgent = "TestAgent/1.0",
                ),
                onProgress = { dl, tot -> progressEvents += dl to tot },
            )

            val req1 = server.takeRequest()
            val req2 = server.takeRequest()
            assertEquals("bytes=10-", req1.getHeader("Range"))
            assertEquals(null, req2.getHeader("Range"))
            assertEquals(10L to full.size.toLong(), progressEvents.first())
            assertArrayEquals(full, destFile.readBytes())
            assertFalse(partFile.exists())
        } finally {
            server.shutdown()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun downloadFile_verifiesInlineSha256OnFreshDownloadAndRejectsMismatch() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tempDir = Files.createTempDirectory("resumable_inline_sha_").toFile()
        try {
            val payload = ByteArray(4096) { idx -> ((idx * 13 + 7) and 0xFF).toByte() }
            val goodSha = HashUtils.sha256Hex(payload)
            val wrongSha = "0".repeat(64)

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(payload)),
            )
            val okDest = File(tempDir, "ok.bin")
            ResumableFileDownloader.downloadFile(
                httpClient = OkHttpClient(),
                url = server.url("/ok.bin").toString(),
                destFile = okDest,
                options = ResumableFileDownloader.DownloadOptions(
                    resumable = false,
                    expectedSha256 = goodSha,
                    maxBytes = 1024L * 1024L,
                    userAgent = "TestAgent/1.0",
                ),
            )
            assertArrayEquals(payload, okDest.readBytes())

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(payload)),
            )
            val badDest = File(tempDir, "bad.bin")
            val err = try {
                ResumableFileDownloader.downloadFile(
                    httpClient = OkHttpClient(),
                    url = server.url("/bad.bin").toString(),
                    destFile = badDest,
                    options = ResumableFileDownloader.DownloadOptions(
                        resumable = false,
                        expectedSha256 = wrongSha,
                        maxBytes = 1024L * 1024L,
                        userAgent = "TestAgent/1.0",
                        shaMismatchErrorMessage = "SHA mismatch inline",
                    ),
                )
                null
            } catch (e: IllegalStateException) {
                e
            }
            assertEquals("SHA mismatch inline", err?.message)
            assertFalse(badDest.exists())
            assertFalse(File(tempDir, "bad.bin.part").exists())
        } finally {
            server.shutdown()
            tempDir.deleteRecursively()
        }
    }
}
