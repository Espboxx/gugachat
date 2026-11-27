package com.gugachat.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HashUtilsTest {

    @Test
    fun sha256Hex_computesExpectedDigestsForBytesTextAndFile() {
        val emptyExpected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val abcExpected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertEquals(emptyExpected, HashUtils.sha256Hex(ByteArray(0)))
        assertEquals(abcExpected, HashUtils.sha256Hex("abc"))

        val tempDir = Files.createTempDirectory("hash_utils_test").toFile()
        try {
            val f = File(tempDir, "sample.bin")
            f.writeBytes("abc".toByteArray(Charsets.UTF_8))
            assertEquals(abcExpected, HashUtils.sha256Hex(f))
            assertEquals(abcExpected, HashUtils.sha256HexOrNull(f))
            assertNull(HashUtils.sha256HexOrNull(File(tempDir, "missing.bin")))
            assertNull(HashUtils.sha256HexOrNull(tempDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun normalizeSha256_handlesPrefixCaseAndInvalidInputs() {
        val valid = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        val expected = valid.lowercase()
        assertEquals(expected, HashUtils.normalizeSha256("sha256:$valid"))
        assertEquals(expected, HashUtils.normalizeSha256("  SHA256: $valid "))
        assertNull(HashUtils.normalizeSha256(null))
        assertNull(HashUtils.normalizeSha256("short"))
        assertNull(HashUtils.normalizeSha256(valid.dropLast(1) + "z"))
    }

    @Test
    fun toHex_formatsAllNibbleRangesLowercase() {
        assertEquals("000f7f80ff", HashUtils.toHex(byteArrayOf(0x00, 0x0F, 0x7F, 0x80.toByte(), 0xFF.toByte())))
    }
}
