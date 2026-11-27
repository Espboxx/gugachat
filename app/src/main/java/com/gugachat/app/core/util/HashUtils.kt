package com.gugachat.app.core.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

object HashUtils {
    private val HEX_CHARS = "0123456789abcdef".toCharArray()
    private const val STREAM_BUFFER_SIZE = 16 * 1024

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return toHex(digest)
    }

    fun sha256Hex(text: String): String =
        sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(STREAM_BUFFER_SIZE)
        BufferedInputStream(FileInputStream(file), STREAM_BUFFER_SIZE).use { ins ->
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return toHex(md.digest())
    }

    fun sha256HexOrNull(file: File): String? {
        if (!file.isFile) return null
        return runCatching { sha256Hex(file) }.getOrNull()
    }

    fun normalizeSha256(raw: String?): String? {
        val clean = raw?.trim()
            ?.removePrefix("sha256:")
            ?.removePrefix("SHA256:")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return null
        return if (clean.length == 64 && clean.all { it in '0'..'9' || it in 'a'..'f' }) clean else null
    }

    fun toHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[v ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(chars)
    }
}
