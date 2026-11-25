package com.gugachat.app.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object FormatUtils {
    fun formatUsdEstimate(value: Double): String {
        if (!value.isFinite() || value <= 0.0) return "$0.00"
        return when {
            value < 0.01 -> "$" + String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
            value < 1.0 -> "$" + String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
            else -> "$" + String.format(Locale.US, "%.2f", value)
        }
    }

    fun formatBalance(balance: Double): String {
        if (!balance.isFinite()) return "$0.00"
        return "$" + String.format(Locale.US, "%.2f", balance)
    }

    fun formatFreeQuotaAmount(quotaType: String, amount: Double): String {
        if (!amount.isFinite()) return "0"
        if (quotaType == "requests") {
            return amount.roundToLong().toString()
        }
        val rounded = (amount * 100.0).roundToLong() / 100.0
        val text = if (abs(rounded - rounded.toLong()) < 1e-6) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
        return "$$text"
    }

    fun formatDateTimeShort(epochMs: Long): String {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatClockFromIso(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val tIndex = iso.indexOf('T')
        if (tIndex >= 0 && iso.length >= tIndex + 6) {
            return iso.substring(tIndex + 1, tIndex + 6)
        }
        return ""
    }
}
