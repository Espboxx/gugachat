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
}
