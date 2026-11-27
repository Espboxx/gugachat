package com.gugachat.app.core.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.min

object StreamZipUtils {
    private const val DEFAULT_BUFFER_SIZE = 8192

    fun isZipBytes(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())

    fun InputStream.readBoundedBytesOrNull(
        maxBytes: Long,
        bufSize: Int = DEFAULT_BUFFER_SIZE,
    ): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(bufSize)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun InputStream.readBoundedBytesOrThrow(
        maxBytes: Long,
        errorMessage: String = "Entry size exceeds limit",
        bufSize: Int = DEFAULT_BUFFER_SIZE,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(bufSize)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) throw IllegalArgumentException(errorMessage)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun InputStream.readBoundedUtf8(
        maxBytes: Long,
        errorMessage: String = "Content size exceeds limit",
        bufSize: Int = DEFAULT_BUFFER_SIZE,
    ): String = readBoundedBytesOrThrow(maxBytes = maxBytes, errorMessage = errorMessage, bufSize = bufSize)
        .toString(Charsets.UTF_8)

    fun InputStream.readTruncatedBytes(
        maxBytes: Int,
        bufSize: Int = DEFAULT_BUFFER_SIZE,
    ): ByteArray {
        val buf = ByteArray(bufSize)
        val out = ByteArrayOutputStream()
        var total = 0
        while (total < maxBytes) {
            val n = read(buf, 0, min(buf.size, maxBytes - total))
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    inline fun forEachZipEntry(
        zipBytes: ByteArray,
        skipMacOsMeta: Boolean = true,
        block: (normPath: String, zis: ZipInputStream) -> Unit,
    ) {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                try {
                    if (!entry.isDirectory) {
                        val normPath = entry.name.replace('\\', '/').trimStart('/')
                        val lower = normPath.lowercase()
                        val baseName = lower.substringAfterLast('/')
                        val isMacMeta = skipMacOsMeta && (lower.contains("__macosx/") || baseName.startsWith("._"))
                        if (!isMacMeta) {
                            block(normPath, zis)
                        }
                    }
                } finally {
                    zis.closeEntry()
                }
            }
        }
    }
}
