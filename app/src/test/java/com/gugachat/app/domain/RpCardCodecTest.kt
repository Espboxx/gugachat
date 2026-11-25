package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

class RpCardCodecTest {

    private fun buildMinimalValidPng(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun writeChunk(type: String, data: ByteArray) {
            val tBytes = type.toByteArray(StandardCharsets.US_ASCII)
            val crc = CRC32().apply {
                update(tBytes)
                update(data)
            }
            out.write(ByteBuffer.allocate(4).putInt(data.size).array())
            out.write(tBytes)
            out.write(data)
            out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
        }
        // 1x1 RGB minimal IHDR + empty IDAT + IEND
        val ihdr = ByteBuffer.allocate(13)
            .putInt(1)
            .putInt(1)
            .put(8)
            .put(2)
            .put(0)
            .put(0)
            .put(0)
            .array()
        writeChunk("IHDR", ihdr)
        writeChunk("IDAT", byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01))
        writeChunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    @Test
    fun parseV2AndExportV3AndPngRoundTrip_preservesCharacterAndLorebook() {
        val v2Json = """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "艾莉丝",
                "description": "红发旅团剑士",
                "personality": "豪爽果敢",
                "scenario": "边境集市重逢",
                "first_mes": "哟，好久不见！",
                "character_book": {
                  "name": "边境志",
                  "entries": [
                    { "keys": ["赤铁令"], "content": "旅团最高信物", "constant": true, "priority": 80 }
                  ]
                }
              }
            }
        """.trimIndent()

        val imported = RpCardCodec.parseCardJson(v2Json, conversationId = "rp-test")
        assertEquals("v2", imported.specVersion)
        assertEquals("艾莉丝", imported.rpConfig.title)
        assertEquals("哟，好久不见！", imported.rpConfig.greeting)
        assertEquals(1, imported.importedLore.size)
        assertTrue(imported.importedLore[0].alwaysActive)

        val exportedV3 = RpCardCodec.exportToV3Json(imported.rpConfig, imported.importedLore)
        assertTrue(exportedV3.contains("chara_card_v3"))

        val rawPng = buildMinimalValidPng()
        val cardPng = RpCardCodec.embedCardJsonInPng(rawPng, exportedV3)
        assertTrue(RpCardCodec.isPngBytes(cardPng))

        val extracted = RpCardCodec.extractCardJsonFromPng(cardPng)
        assertNotNull(extracted)
        val reparsed = RpCardCodec.parseCardJson(extracted!!, conversationId = "rp-test-2")
        assertEquals("v3", reparsed.specVersion)
        assertEquals("艾莉丝", reparsed.rpConfig.title)
        assertEquals(1, reparsed.importedLore.size)
    }
}
