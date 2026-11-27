package com.gugachat.app.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

object FormatUtils {
    fun formatUsdEstimate(
        value: Double,
        prefix: String = "$",
        trimTrailingZeros: Boolean = false,
    ): String {
        if (!value.isFinite() || value <= 0.0) return "${prefix}0.00"
        if (trimTrailingZeros) {
            val rounded = (value * 10000.0).roundToLong() / 10000.0
            return prefix + String.format(Locale.US, "%.4f", rounded).trimEnd('0').trimEnd('.')
        }
        return when {
            value < 0.01 -> prefix + String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
            value < 1.0 -> prefix + String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
            else -> prefix + String.format(Locale.US, "%.2f", value)
        }
    }

    fun formatBalance(balance: Double): String {
        if (!balance.isFinite()) return "$0.00"
        return "$" + String.format(Locale.US, "%.2f", balance)
    }

    fun formatDateTimeShort(epochMs: Long): String {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val mb = bytes / (1024.0 * 1024.0)
        if (mb >= 1.0) return String.format(Locale.US, "%.1f MB", mb)
        val kb = bytes / 1024.0
        return String.format(Locale.US, "%.0f KB", kb)
    }

    fun formatAttachmentBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "${(bytes / 1024L).coerceAtLeast(1)} KB"
        bytes > 0L -> "$bytes B"
        else -> ""
    }

    fun formatSizeMb(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "0 MB"
        val mb = sizeBytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    fun formatCommaCount(value: Int): String =
        String.format(Locale.US, "%,d", value.coerceAtLeast(0))
}
