package com.gugachat.app.core.util

import com.gugachat.app.core.util.StreamZipUtils.readBoundedBytesOrNull
import com.gugachat.app.core.util.StreamZipUtils.readBoundedBytesOrThrow
import com.gugachat.app.core.util.StreamZipUtils.readBoundedUtf8
import com.gugachat.app.core.util.StreamZipUtils.readTruncatedBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StreamZipUtilsTest {

    @Test
    fun isZipBytes_checksPkSignatures() {
        assertTrue(StreamZipUtils.isZipBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertTrue(StreamZipUtils.isZipBytes(byteArrayOf(0x50, 0x4B, 0x05, 0x06)))
        assertFalse(StreamZipUtils.isZipBytes(byteArrayOf(0x50, 0x4B, 0x01, 0x02)))
        assertFalse(StreamZipUtils.isZipBytes(byteArrayOf(0x50, 0x4B, 0x03)))
    }

    @Test
    fun boundedReaders_respectLimitsAndTruncateCleanly() {
        val payload = "hello-world".toByteArray(Charsets.UTF_8)
        assertArrayEquals(payload, ByteArrayInputStream(payload).readBoundedBytesOrNull(payload.size.toLong()))
        assertNull(ByteArrayInputStream(payload).readBoundedBytesOrNull((payload.size - 1).toLong()))

        assertEquals("hello-world", ByteArrayInputStream(payload).readBoundedUtf8(64))
        val err = runCatching {
            ByteArrayInputStream(payload).readBoundedBytesOrThrow(5, errorMessage = "too_big")
        }.exceptionOrNull()
        assertTrue(err is IllegalArgumentException)
        assertEquals("too_big", err?.message)

        assertArrayEquals(
            "hello".toByteArray(Charsets.UTF_8),
            ByteArrayInputStream(payload).readTruncatedBytes(5),
        )
    }

    @Test
    fun forEachZipEntry_normalizesPathsAndFiltersMacOsMetadata() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("folder\\sub/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("__MACOSX/._secret.json"))
            zos.write("meta".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("/nested\\._dotfile.json"))
            zos.write("meta2".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("/dir\\data.json"))
            zos.write("""{"ok":true}""".toByteArray())
            zos.closeEntry()
        }

        val visited = LinkedHashMap<String, String>()
        StreamZipUtils.forEachZipEntry(baos.toByteArray()) { normPath, zis ->
            visited[normPath] = zis.readBoundedUtf8(1024)
        }
        assertEquals(mapOf("dir/data.json" to """{"ok":true}"""), visited)

        val visitedAll = ArrayList<String>()
        StreamZipUtils.forEachZipEntry(baos.toByteArray(), skipMacOsMeta = false) { normPath, _ ->
            visitedAll += normPath
        }
        assertEquals(
            listOf("__MACOSX/._secret.json", "nested/._dotfile.json", "dir/data.json"),
            visitedAll,
        )
    }
}
