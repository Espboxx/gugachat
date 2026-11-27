package com.gugachat.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun formatUsdEstimate_formatsStandardAndTrimmedPrefixVariants() {
        assertEquals("$0.00", FormatUtils.formatUsdEstimate(0.0))
        assertEquals("$0.00", FormatUtils.formatUsdEstimate(Double.NaN))
        assertEquals("$0.0025", FormatUtils.formatUsdEstimate(0.0025))
        assertEquals("$0.125", FormatUtils.formatUsdEstimate(0.1250))
        assertEquals("$1.50", FormatUtils.formatUsdEstimate(1.5))

        assertEquals("~$0.12", FormatUtils.formatUsdEstimate(0.120001, prefix = "~$", trimTrailingZeros = true))
        assertEquals("~$0.00", FormatUtils.formatUsdEstimate(-1.0, prefix = "~$", trimTrailingZeros = true))
        assertEquals("$0.00", FormatUtils.formatBalance(Double.POSITIVE_INFINITY))
        assertEquals("$3.40", FormatUtils.formatBalance(3.4))
    }

    @Test
    fun byteAndCountFormatters_handleBoundariesCleanly() {
        assertEquals("0 B", FormatUtils.formatBytes(0L))
        assertEquals("512 KB", FormatUtils.formatBytes(512L * 1024L))
        assertEquals("1.5 MB", FormatUtils.formatBytes(1572864L))

        assertEquals("", FormatUtils.formatAttachmentBytes(0L))
        assertEquals("500 B", FormatUtils.formatAttachmentBytes(500L))
        assertEquals("2 KB", FormatUtils.formatAttachmentBytes(2048L))
        assertEquals("2.0 MB", FormatUtils.formatAttachmentBytes(2L * 1024L * 1024L))

        assertEquals("0 MB", FormatUtils.formatSizeMb(0L))
        assertEquals("3.5 MB", FormatUtils.formatSizeMb((3.5 * 1024.0 * 1024.0).toLong()))

        assertEquals("0", FormatUtils.formatCommaCount(-5))
        assertEquals("12,345", FormatUtils.formatCommaCount(12345))
    }
}
