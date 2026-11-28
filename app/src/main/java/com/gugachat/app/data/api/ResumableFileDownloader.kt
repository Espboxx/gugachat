package com.gugachat.app.data.api

import com.gugachat.app.core.util.HashUtils
import com.gugachat.app.core.util.StreamZipUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object ResumableFileDownloader {
    data class ParsedContentRange(val start: Long, val end: Long, val total: Long?)

    data class DownloadOptions(
        val resumable: Boolean,
        val expectedSizeBytes: Long = 0L,
        val expectedSha256: String? = null,
        val maxBytes: Long,
        val acceptHeader: String = "*/*",
        val userAgent: String,
        val connectTimeoutSec: Long = 15L,
        val readTimeoutSec: Long = 60L,
        val bufferSizeBytes: Int = 32 * 1024,
        val progressIntervalMs: Long = 80L,
        val sizeLimitErrorMessage: String = "Downloaded file exceeds size limit",
        val emptyFileErrorMessage: String = "Downloaded file is empty",
        val shaMismatchErrorMessage: String = "SHA-256 verification failed",
        val keepPartOnTruncatedOrFailure: Boolean = resumable,
        val verifyFirstChunk: ((buf: ByteArray, length: Int) -> Unit)? = null,
        val rejectHtmlFile: Boolean = false,
        val htmlFileErrorMessage: String = "Downloaded file appears to be HTML",
    )

    private val CONTENT_RANGE_REGEX = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
    private val CONTENT_RANGE_TOTAL_REGEX = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)

    fun parseContentRange(header: String?): ParsedContentRange? {
        if (header.isNullOrBlank()) return null
        val match = CONTENT_RANGE_REGEX.find(header.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].takeIf { it != "*" }?.toLongOrNull()
        return ParsedContentRange(start = start, end = end, total = total)
    }

    fun parseTotalFromContentRange(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val m = CONTENT_RANGE_TOTAL_REGEX.find(header.trim()) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    fun looksLikeHtmlPayload(buf: ByteArray, length: Int): Boolean {
        val prefix = String(buf, 0, minOf(length, 64), Charsets.UTF_8).trimStart().lowercase(Locale.ROOT)
        return prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            prefix.startsWith("<head")
    }

    fun looksLikeHtmlFile(file: File): Boolean = runCatching {
        val buf = ByteArray(64)
        FileInputStream(file).use { fis ->
            val n = fis.read(buf)
            n > 0 && looksLikeHtmlPayload(buf, n)
        }
    }.getOrDefault(false)

    suspend fun downloadFile(
        httpClient: OkHttpClient,
        url: String,
        destFile: File,
        options: DownloadOptions,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File {
        val parent = destFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val partFile = File(destFile.parentFile, "${destFile.name}.part")
        val normalizedExpectedSha = HashUtils.normalizeSha256(options.expectedSha256)

        val dlClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(options.connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(options.readTimeoutSec, TimeUnit.SECONDS)
            .build()

        val maxAttempts = if (options.resumable) 2 else 1
        var attempt = 0
        try {
            while (attempt < maxAttempts) {
                attempt++
                currentCoroutineContext().ensureActive()
                val existingBytes = if (options.resumable && partFile.isFile) {
                    partFile.length().coerceAtLeast(0L)
                } else {
                    0L
                }
                if (options.resumable) {
                    onProgress(existingBytes, maxOf(existingBytes, options.expectedSizeBytes))
                }

                val reqBuilder = Request.Builder()
                    .url(url)
                    .get()
                    .header("Accept", options.acceptHeader)
                    .header("User-Agent", options.userAgent)
                if (existingBytes > 0L) {
                    reqBuilder.header("Range", "bytes=$existingBytes-")
                }

                val response = dlClient.awaitCall(reqBuilder.build())
                var resumedThisAttempt = false
                var inlineShaHex: String? = null
                val needsFreshRestart = withContext(Dispatchers.IO) {
                    response.use { resp ->
                        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion {
                            if (it != null) runCatching { resp.close() }
                        }
                        try {
                            if (options.resumable && resp.code == 416) {
                                val rangeTotal = parseTotalFromContentRange(resp.header("Content-Range"))
                                if (rangeTotal != null && rangeTotal == existingBytes && existingBytes > 0L) {
                                    resumedThisAttempt = true
                                    return@use false
                                }
                                partFile.delete()
                                return@use true
                            }

                            val validStatus = if (options.resumable) {
                                resp.code == 200 || resp.code == 206
                            } else {
                                resp.isSuccessful
                            }
                            if (!validStatus) {
                                throw IllegalStateException("HTTP ${resp.code}")
                            }

                            val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                            if (contentType.contains("text/html")) {
                                if (options.resumable) partFile.delete()
                                throw IllegalStateException("Unexpected HTML response")
                            }

                            val isPartial = options.resumable && resp.code == 206
                            val rawBodyLen = resp.body?.contentLength()?.takeIf { it > 0L } ?: -1L
                            val appendMode: Boolean
                            val startOffset: Long
                            val totalBytes: Long
                            val serverTotal: Long

                            if (isPartial) {
                                val parsedRange = parseContentRange(resp.header("Content-Range"))
                                if (parsedRange == null || parsedRange.start != existingBytes) {
                                    partFile.delete()
                                    return@use true
                                }
                                appendMode = existingBytes > 0L
                                resumedThisAttempt = appendMode
                                startOffset = existingBytes
                                totalBytes = parsedRange.total
                                    ?: if (rawBodyLen > 0L) existingBytes + rawBodyLen else options.expectedSizeBytes
                                serverTotal = if (rawBodyLen > 0L) existingBytes + rawBodyLen else -1L
                            } else {
                                appendMode = false
                                startOffset = 0L
                                totalBytes = if (rawBodyLen > 0L) rawBodyLen else options.expectedSizeBytes
                                serverTotal = rawBodyLen
                            }

                            val body = resp.body ?: throw IllegalStateException("Empty download body")
                            val buf = ByteArray(options.bufferSizeBytes)
                            var downloaded = startOffset
                            var lastEmitMs = 0L
                            val verifier = if (startOffset == 0L) options.verifyFirstChunk else null
                            val pendingPrefix = if (verifier != null) ByteArrayOutputStream(64) else null
                            var checkedPrefix = verifier == null
                            val shaDigest = if (startOffset == 0L && normalizedExpectedSha != null) {
                                MessageDigest.getInstance("SHA-256")
                            } else {
                                null
                            }

                            body.byteStream().use { input ->
                                FileOutputStream(partFile, appendMode).use { output ->
                                    fun flushPendingPrefixOrThrow() {
                                        val acc = pendingPrefix ?: return
                                        val bytes = acc.toByteArray()
                                        if (bytes.isNotEmpty()) {
                                            try {
                                                verifier!!(bytes, bytes.size)
                                            } catch (t: Throwable) {
                                                output.close()
                                                partFile.delete()
                                                throw t
                                            }
                                            downloaded += bytes.size
                                            if (downloaded > options.maxBytes) {
                                                output.close()
                                                partFile.delete()
                                                throw IllegalStateException(options.sizeLimitErrorMessage)
                                            }
                                            shaDigest?.update(bytes, 0, bytes.size)
                                            output.write(bytes, 0, bytes.size)
                                        }
                                        checkedPrefix = true
                                    }

                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val n = input.read(buf)
                                        if (n <= 0) {
                                            if (!checkedPrefix && pendingPrefix != null && pendingPrefix.size() > 0) {
                                                flushPendingPrefixOrThrow()
                                            }
                                            break
                                        }
                                        if (!checkedPrefix && pendingPrefix != null) {
                                            if (pendingPrefix.size() == 0 && n >= 4) {
                                                checkedPrefix = true
                                                try {
                                                    verifier!!(buf, n)
                                                } catch (t: Throwable) {
                                                    output.close()
                                                    partFile.delete()
                                                    throw t
                                                }
                                            } else {
                                                if (pendingPrefix.size().toLong() + n > options.maxBytes) {
                                                    output.close()
                                                    partFile.delete()
                                                    throw IllegalStateException(options.sizeLimitErrorMessage)
                                                }
                                                pendingPrefix.write(buf, 0, n)
                                                if (pendingPrefix.size() >= 4) {
                                                    flushPendingPrefixOrThrow()
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastEmitMs >= options.progressIntervalMs || (totalBytes > 0L && downloaded >= totalBytes)) {
                                                        lastEmitMs = now
                                                        onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded)
                                                    }
                                                }
                                                continue
                                            }
                                        }
                                        downloaded += n
                                        if (downloaded > options.maxBytes) {
                                            output.close()
                                            partFile.delete()
                                            throw IllegalStateException(options.sizeLimitErrorMessage)
                                        }
                                        shaDigest?.update(buf, 0, n)
                                        output.write(buf, 0, n)

                                        val now = System.currentTimeMillis()
                                        if (now - lastEmitMs >= options.progressIntervalMs || (totalBytes > 0L && downloaded >= totalBytes)) {
                                            lastEmitMs = now
                                            onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded)
                                        }
                                    }
                                    output.flush()
                                }
                            }

                            val expectedTotal = when {
                                serverTotal > 0L -> serverTotal
                                options.resumable && totalBytes > 0L -> totalBytes
                                else -> -1L
                            }
                            if (expectedTotal > 0L && downloaded < expectedTotal) {
                                throw IllegalStateException("Truncated stream ($downloaded/$expectedTotal bytes)")
                            }
                            if (shaDigest != null) {
                                inlineShaHex = HashUtils.toHex(shaDigest.digest())
                            }
                            onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded)
                            false
                        } finally {
                            cancelHandle.dispose()
                        }
                    }
                }

                if (needsFreshRestart) {
                    if (attempt >= maxAttempts) {
                        throw IllegalStateException("Unable to synchronize download byte range")
                    }
                    continue
                }

                val retryFromZero = withContext(Dispatchers.IO) {
                    if (!partFile.isFile || partFile.length() <= 0L) {
                        partFile.delete()
                        throw IllegalStateException(options.emptyFileErrorMessage)
                    }
                    if (options.rejectHtmlFile && looksLikeHtmlFile(partFile)) {
                        partFile.delete()
                        if (resumedThisAttempt && attempt < maxAttempts) return@withContext true
                        throw IllegalStateException(options.htmlFileErrorMessage)
                    }
                    if (normalizedExpectedSha != null) {
                        val actualSha = inlineShaHex ?: HashUtils.sha256HexOrNull(partFile)
                        if (actualSha == null || !actualSha.equals(normalizedExpectedSha, ignoreCase = true)) {
                            partFile.delete()
                            if (resumedThisAttempt && attempt < maxAttempts) return@withContext true
                            throw IllegalStateException(options.shaMismatchErrorMessage)
                        }
                    }
                    StreamZipUtils.atomicReplace(partFile, destFile)
                    false
                }
                if (!retryFromZero) {
                    return destFile
                }
            }
            throw IllegalStateException("Download did not complete")
        } catch (t: Throwable) {
            if (!options.keepPartOnTruncatedOrFailure) {
                runCatching { partFile.delete() }
            }
            throw t
        }
    }

    suspend fun <T> downloadWithFallback(
        candidates: List<T>,
        labelOf: (T) -> String,
        emptyCandidatesError: String,
        joinErrors: Boolean = true,
        onCandidateAttempt: (candidate: T, attemptIndex: Int, totalCandidates: Int) -> Unit = { _, _, _ -> },
        downloadCandidate: suspend (T) -> File,
    ): Pair<File, T> {
        if (candidates.isEmpty()) {
            throw IllegalStateException(emptyCandidatesError)
        }
        val errors = ArrayList<String>(candidates.size)
        candidates.forEachIndexed { idx, candidate ->
            currentCoroutineContext().ensureActive()
            onCandidateAttempt(candidate, idx + 1, candidates.size)
            try {
                return downloadCandidate(candidate) to candidate
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val detail = t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
                errors.add("${labelOf(candidate)}: $detail")
            }
        }
        val errMsg = if (joinErrors) {
            errors.joinToString(" | ").ifBlank { "All candidates failed" }
        } else {
            errors.lastOrNull() ?: "All candidates failed"
        }
        throw IllegalStateException(errMsg)
    }
}
