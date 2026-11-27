package com.gugachat.app.data.sync

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-JVM compatible cryptographic codec for GuGuChat v0.4.0 backups and WebDAV sync.
 *
 * - KDF: PBKDF2WithHmacSHA256 (150,000 iterations, 16-byte random salt) -> 256-bit master key.
 * - Subkeys via HmacSHA256:
 *   - vaultKey: info = "gugachat-v2-vault"
 *   - streamKey: info = "gugachat-v2-stream"
 * - Small envelope encryption: AES/GCM/NoPadding (12B nonce + 128-bit tag).
 * - Large file streaming encryption: 64KB chunked AES/GCM/NoPadding with header magic "GGENC01",
 *   unique per-chunk nonce (8B random sessionPrefix || 4B big-endian chunkSeq),
 *   and AAD = [chunkSeq (4B), isFinal (1B)] in O(1) heap memory.
 */
object SyncCryptoCodec {
    const val PBKDF2_ITERATIONS: Int = 150_000
    const val SALT_SIZE_BYTES: Int = 16
    const val KEY_SIZE_BITS: Int = 256
    const val KEY_SIZE_BYTES: Int = 32
    const val DEFAULT_CHUNK_SIZE: Int = 64 * 1024
    const val MIN_CHUNK_SIZE: Int = 1024
    const val MAX_CHUNK_SIZE: Int = 1024 * 1024
    const val GCM_NONCE_BYTES: Int = 12
    const val STREAM_PREFIX_BYTES: Int = 8
    const val GCM_TAG_BITS: Int = 128
    const val GCM_TAG_BYTES: Int = 16

    const val VAULT_SUBKEY_INFO: String = "gugachat-v2-vault"
    const val STREAM_SUBKEY_INFO: String = "gugachat-v2-stream"

    private val STREAM_MAGIC: ByteArray = "GGENC01".toByteArray(StandardCharsets.US_ASCII)
    private val VAULT_AAD: ByteArray = VAULT_SUBKEY_INFO.toByteArray(StandardCharsets.UTF_8)
    private val HEX_CHARS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )

    private val secureRandom: SecureRandom = SecureRandom()

    data class DerivedKeys(
        val salt: ByteArray,
        val vaultKey: ByteArray,
        val streamKey: ByteArray,
    ) {
        fun clear() {
            Arrays.fill(vaultKey, 0.toByte())
            Arrays.fill(streamKey, 0.toByte())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DerivedKeys) return false
            return salt.contentEquals(other.salt) &&
                vaultKey.contentEquals(other.vaultKey) &&
                streamKey.contentEquals(other.streamKey)
        }

        override fun hashCode(): Int {
            var result = salt.contentHashCode()
            result = 31 * result + vaultKey.contentHashCode()
            result = 31 * result + streamKey.contentHashCode()
            return result
        }
    }

    fun generateSalt(): ByteArray = ByteArray(SALT_SIZE_BYTES).also { secureRandom.nextBytes(it) }

    fun deriveMasterKey(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
    ): ByteArray {
        require(passphrase.isNotEmpty()) { "Passphrase must not be empty" }
        require(salt.size == SALT_SIZE_BYTES) { "KDF salt must be $SALT_SIZE_BYTES bytes (got ${salt.size})" }
        require(iterations >= 1) { "PBKDF2 iterations must be positive" }

        val chars = passphrase.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_SIZE_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            Arrays.fill(chars, '\u0000')
        }
    }

    fun deriveSubkey(masterKey: ByteArray, info: String): ByteArray {
        require(masterKey.size == KEY_SIZE_BYTES) { "Master key must be $KEY_SIZE_BYTES bytes" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        return mac.doFinal(info.toByteArray(StandardCharsets.UTF_8))
    }

    fun deriveKeys(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
    ): DerivedKeys {
        val master = deriveMasterKey(passphrase, salt, iterations)
        return try {
            DerivedKeys(
                salt = salt.copyOf(),
                vaultKey = deriveSubkey(master, VAULT_SUBKEY_INFO),
                streamKey = deriveSubkey(master, STREAM_SUBKEY_INFO),
            )
        } finally {
            Arrays.fill(master, 0.toByte())
        }
    }

    fun encryptVaultBytes(
        passphrase: String,
        salt: ByteArray,
        plainBytes: ByteArray,
    ): ByteArray {
        val keys = deriveKeys(passphrase, salt)
        return try {
            encryptVaultBytesWithKey(keys.vaultKey, plainBytes)
        } finally {
            keys.clear()
        }
    }

    fun encryptVaultBytesWithKey(
        vaultKey: ByteArray,
        plainBytes: ByteArray,
    ): ByteArray {
        require(vaultKey.size == KEY_SIZE_BYTES) { "Vault key must be $KEY_SIZE_BYTES bytes" }
        val nonce = ByteArray(GCM_NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(vaultKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        cipher.updateAAD(VAULT_AAD)
        val encrypted = cipher.doFinal(plainBytes)
        val out = ByteArray(GCM_NONCE_BYTES + encrypted.size)
        System.arraycopy(nonce, 0, out, 0, GCM_NONCE_BYTES)
        System.arraycopy(encrypted, 0, out, GCM_NONCE_BYTES, encrypted.size)
        return out
    }

    fun decryptVaultBytes(
        passphrase: String,
        salt: ByteArray,
        cipherBytes: ByteArray,
    ): ByteArray {
        val keys = deriveKeys(passphrase, salt)
        return try {
            decryptVaultBytesWithKey(keys.vaultKey, cipherBytes)
        } finally {
            keys.clear()
        }
    }

    fun decryptVaultBytesWithKey(
        vaultKey: ByteArray,
        cipherBytes: ByteArray,
    ): ByteArray {
        require(vaultKey.size == KEY_SIZE_BYTES) { "Vault key must be $KEY_SIZE_BYTES bytes" }
        if (cipherBytes.size < GCM_NONCE_BYTES + GCM_TAG_BYTES) {
            throw GeneralSecurityException("Encrypted vault payload is truncated or invalid")
        }
        val nonce = cipherBytes.copyOfRange(0, GCM_NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(vaultKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        cipher.updateAAD(VAULT_AAD)
        return cipher.doFinal(cipherBytes, GCM_NONCE_BYTES, cipherBytes.size - GCM_NONCE_BYTES)
    }

    fun encryptStream(
        input: InputStream,
        output: OutputStream,
        passphrase: String,
        salt: ByteArray,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
    ) {
        val keys = deriveKeys(passphrase, salt)
        try {
            encryptStreamWithKey(input, output, keys.streamKey, chunkSize)
        } finally {
            keys.clear()
        }
    }

    fun encryptStreamWithKey(
        input: InputStream,
        output: OutputStream,
        streamKey: ByteArray,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
    ) {
        require(streamKey.size == KEY_SIZE_BYTES) { "Stream key must be $KEY_SIZE_BYTES bytes" }
        require(chunkSize in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE) {
            "Chunk size must be between $MIN_CHUNK_SIZE and $MAX_CHUNK_SIZE"
        }

        val sessionPrefix = ByteArray(STREAM_PREFIX_BYTES).also { secureRandom.nextBytes(it) }
        val keySpec = SecretKeySpec(streamKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        val header = ByteArray(STREAM_MAGIC.size + 4 + STREAM_PREFIX_BYTES)
        System.arraycopy(STREAM_MAGIC, 0, header, 0, STREAM_MAGIC.size)
        writeInt32BE(header, STREAM_MAGIC.size, chunkSize)
        System.arraycopy(sessionPrefix, 0, header, STREAM_MAGIC.size + 4, STREAM_PREFIX_BYTES)
        output.write(header)

        val plainBuf = ByteArray(chunkSize)
        val cipherBuf = ByteArray(chunkSize + GCM_TAG_BYTES)
        val nonce = ByteArray(GCM_NONCE_BYTES)
        System.arraycopy(sessionPrefix, 0, nonce, 0, STREAM_PREFIX_BYTES)
        val aad = ByteArray(5)
        val chunkHeader = ByteArray(5)

        var seq = 0
        var carryByte = -1

        while (true) {
            var filled = 0
            if (carryByte >= 0) {
                plainBuf[0] = carryByte.toByte()
                filled = 1
                carryByte = -1
            }
            if (filled < chunkSize) {
                filled += readFullyUpTo(input, plainBuf, filled, chunkSize - filled)
            }

            val isFinal: Boolean
            if (filled < chunkSize) {
                isFinal = true
            } else {
                val peek = input.read()
                if (peek < 0) {
                    isFinal = true
                } else {
                    isFinal = false
                    carryByte = peek
                }
            }

            if (seq == Int.MAX_VALUE && !isFinal) {
                throw GeneralSecurityException("Stream exceeds maximum chunk count")
            }

            writeInt32BE(nonce, STREAM_PREFIX_BYTES, seq)
            writeInt32BE(aad, 0, seq)
            val isFinalByte: Byte = if (isFinal) 1 else 0
            aad[4] = isFinalByte

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad)
            val cipherLen = cipher.doFinal(plainBuf, 0, filled, cipherBuf, 0)

            chunkHeader[0] = isFinalByte
            writeInt32BE(chunkHeader, 1, cipherLen)
            output.write(chunkHeader)
            output.write(cipherBuf, 0, cipherLen)

            if (isFinal) break
            seq += 1
        }
        output.flush()
    }

    fun decryptStream(
        input: InputStream,
        output: OutputStream,
        passphrase: String,
        salt: ByteArray,
    ) {
        val keys = deriveKeys(passphrase, salt)
        try {
            decryptStreamWithKey(input, output, keys.streamKey)
        } finally {
            keys.clear()
        }
    }

    fun decryptStreamWithKey(
        input: InputStream,
        output: OutputStream,
        streamKey: ByteArray,
    ) {
        require(streamKey.size == KEY_SIZE_BYTES) { "Stream key must be $KEY_SIZE_BYTES bytes" }

        val header = ByteArray(STREAM_MAGIC.size + 4 + STREAM_PREFIX_BYTES)
        if (!readExact(input, header, 0, header.size)) {
            throw GeneralSecurityException("Truncated encrypted stream header")
        }
        for (i in STREAM_MAGIC.indices) {
            if (header[i] != STREAM_MAGIC[i]) {
                throw GeneralSecurityException("Invalid encrypted stream magic header")
            }
        }
        val chunkSize = readInt32BE(header, STREAM_MAGIC.size)
        if (chunkSize !in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE) {
            throw GeneralSecurityException("Invalid encrypted stream chunk size: $chunkSize")
        }

        val sessionPrefix = header.copyOfRange(STREAM_MAGIC.size + 4, header.size)
        val keySpec = SecretKeySpec(streamKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        val cipherBuf = ByteArray(chunkSize + GCM_TAG_BYTES)
        val plainBuf = ByteArray(chunkSize + GCM_TAG_BYTES)
        val nonce = ByteArray(GCM_NONCE_BYTES)
        System.arraycopy(sessionPrefix, 0, nonce, 0, STREAM_PREFIX_BYTES)
        val aad = ByteArray(5)
        val chunkHeader = ByteArray(5)

        var seq = 0
        while (true) {
            val firstByte = input.read()
            if (firstByte < 0) {
                throw GeneralSecurityException("Truncated encrypted stream: unexpected EOF before final chunk")
            }
            chunkHeader[0] = firstByte.toByte()
            if (!readExact(input, chunkHeader, 1, 4)) {
                throw GeneralSecurityException("Truncated encrypted chunk header")
            }

            val isFinalByte = chunkHeader[0]
            if (isFinalByte != 0.toByte() && isFinalByte != 1.toByte()) {
                throw GeneralSecurityException("Invalid chunk final flag: $isFinalByte")
            }
            val isFinal = isFinalByte == 1.toByte()
            val cipherLen = readInt32BE(chunkHeader, 1)
            if (cipherLen < GCM_TAG_BYTES || cipherLen > chunkSize + GCM_TAG_BYTES) {
                throw GeneralSecurityException("Invalid encrypted chunk length: $cipherLen")
            }
            if (!readExact(input, cipherBuf, 0, cipherLen)) {
                throw GeneralSecurityException("Truncated encrypted chunk payload")
            }

            writeInt32BE(nonce, STREAM_PREFIX_BYTES, seq)
            writeInt32BE(aad, 0, seq)
            aad[4] = isFinalByte

            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad)
            val plainLen = cipher.doFinal(cipherBuf, 0, cipherLen, plainBuf, 0)
            if (plainLen > 0) {
                output.write(plainBuf, 0, plainLen)
            }

            if (isFinal) {
                if (input.read() != -1) {
                    throw GeneralSecurityException("Unexpected trailing bytes after final encrypted chunk")
                }
                break
            }
            if (seq == Int.MAX_VALUE) {
                throw GeneralSecurityException("Encrypted stream exceeds maximum chunk count")
            }
            seq += 1
        }
        output.flush()
    }

    fun encryptFile(
        source: File,
        dest: File,
        passphrase: String,
        salt: ByteArray,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
    ) {
        val keys = deriveKeys(passphrase, salt)
        try {
            encryptFileWithKey(source, dest, keys.streamKey, chunkSize)
        } finally {
            keys.clear()
        }
    }

    fun encryptFileWithKey(
        source: File,
        dest: File,
        streamKey: ByteArray,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
    ) {
        dest.parentFile?.mkdirs()
        BufferedInputStream(FileInputStream(source), DEFAULT_CHUNK_SIZE).use { ins ->
            BufferedOutputStream(FileOutputStream(dest), DEFAULT_CHUNK_SIZE).use { outs ->
                encryptStreamWithKey(ins, outs, streamKey, chunkSize)
            }
        }
    }

    fun decryptFile(
        source: File,
        dest: File,
        passphrase: String,
        salt: ByteArray,
    ) {
        val keys = deriveKeys(passphrase, salt)
        try {
            decryptFileWithKey(source, dest, keys.streamKey)
        } finally {
            keys.clear()
        }
    }

    fun decryptFileWithKey(
        source: File,
        dest: File,
        streamKey: ByteArray,
    ) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.${System.nanoTime()}.tmp")
        var succeeded = false
        try {
            BufferedInputStream(FileInputStream(source), DEFAULT_CHUNK_SIZE).use { ins ->
                BufferedOutputStream(FileOutputStream(tmp), DEFAULT_CHUNK_SIZE).use { outs ->
                    decryptStreamWithKey(ins, outs, streamKey)
                }
            }
            if (dest.exists() && !dest.delete()) {
                throw GeneralSecurityException("Unable to replace destination file: ${dest.name}")
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
            }
            succeeded = true
        } finally {
            if (!succeeded || tmp.exists()) {
                tmp.delete()
            }
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return toHex(digest)
    }

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(DEFAULT_CHUNK_SIZE)
        BufferedInputStream(FileInputStream(file), DEFAULT_CHUNK_SIZE).use { ins ->
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return toHex(md.digest())
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

    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Invalid hex string length: ${clean.length}" }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character in '$clean'" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun readFullyUpTo(input: InputStream, buf: ByteArray, offset: Int, maxLen: Int): Int {
        var total = 0
        while (total < maxLen) {
            val n = input.read(buf, offset + total, maxLen - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun readExact(input: InputStream, buf: ByteArray, offset: Int, len: Int): Boolean {
        var total = 0
        while (total < len) {
            val n = input.read(buf, offset + total, len - total)
            if (n < 0) return false
            total += n
        }
        return true
    }

    private fun writeInt32BE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value ushr 24).toByte()
        buf[offset + 1] = (value ushr 16).toByte()
        buf[offset + 2] = (value ushr 8).toByte()
        buf[offset + 3] = value.toByte()
    }

    private fun readInt32BE(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)
}
