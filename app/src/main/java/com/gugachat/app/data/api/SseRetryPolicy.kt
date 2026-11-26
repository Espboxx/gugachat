package com.gugachat.app.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import java.io.IOException

object SseRetryPolicy {
    const val DEFAULT_SSE_AUTO_RETRY_COUNT = 3
    const val MAX_SSE_AUTO_RETRY_COUNT = 10
    const val DEFAULT_SSE_READ_TIMEOUT_MS = 90_000L
    const val DEFAULT_RETRY_BASE_DELAY_MS = 1000L
    const val DEFAULT_RETRY_MAX_DELAY_MS = 4000L

    private val RETRYABLE_HTTP_STATUSES = setOf(0, 408, 502, 503, 504, 520, 521, 522, 524)

    private val RETRYABLE_ERROR_CODES = setOf(
        "ERR_NETWORK",
        "STREAM_READ_ERROR",
        "STREAM_UNEXPECTED_EOF",
        "STREAM_TIMEOUT",
        "TIMEOUT",
        "GATEWAY_TIMEOUT",
        "BAD_GATEWAY",
        "UPSTREAM_ERROR",
        "UPSTREAM_STREAM_ERROR",
    )

    private val RETRYABLE_MESSAGE_MARKERS = listOf(
        "unexpected eof",
        "upstream sse",
        "sse stream",
        "stream failed",
        "stream closed",
        "stream interrupted",
        "stream read",
        "end of stream",
        "eof",
        "timeout",
        "timed out",
        "deadline exceeded",
        "connection reset",
        "broken pipe",
        "connection abort",
        "connection closed",
        "socket closed",
        "network error",
        "bad gateway",
        "gateway timeout",
        "service unavailable",
        "temporarily unavailable",
        "protocol exception",
        "connection refused",
    )

    fun clampRetryCount(value: Int): Int = value.coerceIn(0, MAX_SSE_AUTO_RETRY_COUNT)

    fun isRetryableSseError(error: Throwable): Boolean {
        if (error is TimeoutCancellationException) return true
        if (error is CancellationException) return false
        if (error is IOException) return true

        if (error is ApiException) {
            if (error.isContextTooLong) return false
            if (error.status in 400..499 && error.status != 408) return false
            if (error.status in RETRYABLE_HTTP_STATUSES) return true
            val codeUpper = error.code?.trim()?.uppercase().orEmpty()
            if (codeUpper in RETRYABLE_ERROR_CODES) return true
            val hay = "$codeUpper ${error.message} ${error.reason.orEmpty()}".lowercase()
            return RETRYABLE_MESSAGE_MARKERS.any { hay.contains(it) }
        }

        val msg = error.message.orEmpty().lowercase()
        return RETRYABLE_MESSAGE_MARKERS.any { msg.contains(it) }
    }

    fun computeRetryDelayMs(
        retryNumber: Int,
        baseDelayMs: Long = DEFAULT_RETRY_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_RETRY_MAX_DELAY_MS,
    ): Long {
        if (baseDelayMs <= 0L) return 0L
        val safeNum = retryNumber.coerceAtLeast(1).toLong()
        return (baseDelayMs * safeNum).coerceAtMost(maxDelayMs.coerceAtLeast(baseDelayMs))
    }

    suspend fun <T> executeWithSseRetry(
        maxRetries: Int,
        baseDelayMs: Long = DEFAULT_RETRY_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_RETRY_MAX_DELAY_MS,
        onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit = { _, _, _ -> },
        onResetForRetry: () -> Unit = {},
        block: suspend (attempt: Int) -> T,
    ): T {
        val safeMax = clampRetryCount(maxRetries)
        var attempt = 0
        while (true) {
            try {
                return block(attempt)
            } catch (e: Throwable) {
                if (e is CancellationException && e !is TimeoutCancellationException) {
                    throw e
                }
                if (attempt >= safeMax || !isRetryableSseError(e)) {
                    throw e
                }
                val retryNumber = attempt + 1
                onRetryAttempt(retryNumber, safeMax, e)
                val waitMs = computeRetryDelayMs(retryNumber, baseDelayMs, maxDelayMs)
                if (waitMs > 0L) {
                    delay(waitMs)
                }
                onResetForRetry()
                attempt = retryNumber
            }
        }
    }
}
