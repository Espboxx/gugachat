package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        assertEquals(RpPlayStyle.tavern, imported.rpConfig.playStyle)
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

    @Test
    fun parseCardJson_supportsAlternateGreetingsPostHistoryMesExampleAndObjectMapLorebook() {
        val v3MapLoreJson = """
            {
              "spec": "chara_card_v3",
              "spec_version": "3.0",
              "data": {
                "name": "江晚柠",
                "description": "深夜清吧主理人",
                "personality": "慵懒敏锐",
                "scenario": "雨夜第七站台",
                "first_mes": "欢迎来到第七站台，{{user}}。",
                "alternate_greetings": [
                  "今晚风很大，进来喝杯姜茶？",
                  "磁带卡住了，帮我修修吧。"
                ],
                "mes_example": "<START>\n{{user}}: 来杯威士忌\n{{char}}: 加冰吗？",
                "post_history_instructions": "保持慵懒口吻，不要替{{user}}说话。",
                "tags": ["都市", "治愈"],
                "character_book": {
                  "name": "站台秘录",
                  "entries": {
                    "0": {
                      "key": ["失眠蓝", "特调"],
                      "comment": "特调失眠蓝",
                      "content": "海盐金酒与蝶豆花冷萃。",
                      "constant": false,
                      "order": 75
                    },
                    "1": {
                      "key": ["店规"],
                      "name": "清吧店规",
                      "content": "禁止开闪光灯。",
                      "constant": true,
                      "disable": true,
                      "priority": 90
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val imported = RpCardCodec.parseCardJson(v3MapLoreJson, conversationId = "rp-map")
        assertEquals("v3", imported.specVersion)
        assertEquals(RpPlayStyle.tavern, imported.rpConfig.playStyle)
        assertEquals(2, imported.rpConfig.alternateGreetings.size)
        assertTrue(imported.rpConfig.mesExample.contains("加冰吗"))
        assertTrue(imported.rpConfig.postHistoryInstructions.contains("慵懒口吻"))
        assertEquals(2, imported.importedLore.size)

        val firstLore = imported.importedLore.first { it.title == "特调失眠蓝" }
        assertTrue(firstLore.enabled)
        assertEquals(75, firstLore.priority)
        assertTrue(firstLore.keywords.contains("失眠蓝"))

        val secondLore = imported.importedLore.first { it.title == "清吧店规" }
        assertTrue(secondLore.alwaysActive)
        assertFalse(secondLore.enabled)

        // Export to V3 and re-parse losslessly
        val exported = RpCardCodec.exportToV3Json(imported.rpConfig, imported.importedLore)
        val reparsed = RpCardCodec.parseCardJson(exported, conversationId = "rp-map-2")
        assertEquals(imported.rpConfig.alternateGreetings, reparsed.rpConfig.alternateGreetings)
        assertEquals(imported.rpConfig.postHistoryInstructions, reparsed.rpConfig.postHistoryInstructions)
        assertEquals(imported.rpConfig.mesExample, reparsed.rpConfig.mesExample)
        assertEquals(2, reparsed.importedLore.size)
    }

    @Test
    fun createMinimalCardPngBytes_generatesValidDecodablePngAndRoundTripsCard() {
        val png = RpCardCodec.createMinimalCardPngBytes(width = 32, height = 24, r = 46, g = 58, b = 92)
        assertTrue(RpCardCodec.isPngBytes(png))

        // Verify PNG chunk framing, CRC32 integrity, IHDR dimensions, and zlib-compressed RGB scanlines
        var pos = 8
        var ihdrWidth = -1
        var ihdrHeight = -1
        val idatBytes = ByteArrayOutputStream()
        var sawIend = false
        while (pos + 12 <= png.size) {
            val len = ByteBuffer.wrap(png, pos, 4).int
            val type = String(png, pos + 4, 4, StandardCharsets.US_ASCII)
            val dataStart = pos + 8
            val dataEnd = dataStart + len
            val storedCrc = ByteBuffer.wrap(png, dataEnd, 4).int
            val crc = CRC32().apply {
                update(png, pos + 4, 4 + len)
            }
            assertEquals("CRC mismatch for chunk $type", crc.value.toInt(), storedCrc)
            when (type) {
                "IHDR" -> {
                    val buf = ByteBuffer.wrap(png, dataStart, len)
                    ihdrWidth = buf.int
                    ihdrHeight = buf.int
                    assertEquals(8.toByte(), buf.get()) // bit depth
                    assertEquals(2.toByte(), buf.get()) // truecolor RGB
                }
                "IDAT" -> idatBytes.write(png, dataStart, len)
                "IEND" -> sawIend = true
            }
            pos = dataEnd + 4
        }
        assertEquals(32, ihdrWidth)
        assertEquals(24, ihdrHeight)
        assertTrue(sawIend)

        val inflater = Inflater()
        inflater.setInput(idatBytes.toByteArray())
        val raw = ByteArrayOutputStream()
        val tmp = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(tmp)
            if (n <= 0) break
            raw.write(tmp, 0, n)
        }
        inflater.end()
        val pixels = raw.toByteArray()
        assertEquals(24 * (1 + 32 * 3), pixels.size)
        assertEquals(0.toByte(), pixels[0])
        assertEquals(46.toByte(), pixels[1])
        assertEquals(58.toByte(), pixels[2])
        assertEquals(92.toByte(), pixels[3])

        val tavern = RpPresets.findById("modern_tavern").config
        val json = RpCardCodec.exportToV3Json(tavern)
        val embedded = RpCardCodec.embedCardJsonInPng(png, json)
        assertTrue(RpCardCodec.isPngBytes(embedded))
        val extracted = RpCardCodec.extractCardJsonFromPng(embedded)
        assertNotNull(extracted)
        val parsed = RpCardCodec.parseCardJson(extracted!!)
        assertEquals("江晚柠", parsed.rpConfig.title)
        assertEquals(3, parsed.rpConfig.alternateGreetings.size)
        assertEquals(2, parsed.rpConfig.regexScripts.size)
    }

    @Test
    fun parseAndExportV3_roundTripsSillyTavernExtensionsRegexScripts() {
        val cardJson = """
            {
              "spec": "chara_card_v3",
              "spec_version": "3.0",
              "data": {
                "name": "璃月",
                "description": "星穹旅店老板娘",
                "first_mes": "欢迎光临。",
                "extensions": {
                  "regex_scripts": [
                    {
                      "id": "st-r1",
                      "scriptName": "Status Box",
                      "findRegex": "/<status>([\\s\\S]*?)<\\/status>/gim",
                      "replaceString": "<div class=\"st\">$1</div>",
                      "placement": [2],
                      "disabled": false,
                      "markdownOnly": true,
                      "promptOnly": false
                    },
                    {
                      "id": "st-r2",
                      "scriptName": "User Alias Rewrite",
                      "findRegex": "/\\[cmd:([^\\]]+)\\]/gi",
                      "replaceString": "COMMAND($1)",
                      "placement": [1, 2],
                      "disabled": true,
                      "markdownOnly": false,
                      "promptOnly": true
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val imported = RpCardCodec.parseCardJson(cardJson, conversationId = "st-regex")
        assertEquals(2, imported.rpConfig.regexScripts.size)

        val r1 = imported.rpConfig.regexScripts[0]
        assertEquals("st-r1", r1.id)
        assertEquals("Status Box", r1.name)
        assertEquals("<status>([\\s\\S]*?)</status>", r1.findRegex)
        assertEquals("gim", r1.flags)
        assertEquals("ai_output", r1.placement)
        assertTrue(r1.enabled)
        assertTrue(r1.markdownOnly)
        assertFalse(r1.promptOnly)

        val r2 = imported.rpConfig.regexScripts[1]
        assertEquals("st-r2", r2.id)
        assertEquals("both", r2.placement)
        assertFalse(r2.enabled)
        assertFalse(r2.markdownOnly)
        assertTrue(r2.promptOnly)

        // Export back to V3 JSON and re-import losslessly
        val exported = RpCardCodec.exportToV3Json(imported.rpConfig, imported.importedLore)
        assertTrue(exported.contains("\"regex_scripts\""))
        assertTrue(exported.contains("\\/status>/gim"))

        val reparsed = RpCardCodec.parseCardJson(exported, conversationId = "st-regex-2")
        assertEquals(imported.rpConfig.regexScripts, reparsed.rpConfig.regexScripts)
    }

    @Test
    fun parseCardJson_toleratesChubNullFields() {
        val chubNullJson = """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "星见琉璃",
                "description": null,
                "personality": "清冷",
                "scenario": null,
                "first_mes": "你好，观测者。",
                "alternate_greetings": null,
                "tags": [null, "科幻", "星际"],
                "character_book": null,
                "extensions": null
              }
            }
        """.trimIndent()

        val imported = RpCardCodec.parseCardJson(chubNullJson, conversationId = "chub-null")
        assertEquals("v2", imported.specVersion)
        assertEquals("星见琉璃", imported.rpConfig.title)
        assertEquals("你好，观测者。", imported.rpConfig.greeting)
        assertTrue(imported.rpConfig.worldview.contains("科幻"))
        assertTrue(imported.importedLore.isEmpty())
    }

    @Test
    fun parseCardJson_supportsSpecLessV1AliasesCharacterEmbdingsAndDepthPrompt() {
        val v1Json = """
            {
              "char_name": "墨渊",
              "char_persona": "隐居剑阁的白衣剑客",
              "world_scenario": "落雪剑阁",
              "char_greeting": "剑已出鞘，阁下请赐教。",
              "example_dialogue": "{{user}}: 你是谁？\n{{char}}: 剑阁守剑人。",
              "extensions": {
                "depth_prompt": {
                  "prompt": "保持武侠留白感。",
                  "depth": 3
                }
              },
              "character_embdings": {
                "entries": [
                  {
                    "keys": ["寒光剑"],
                    "secondary_keys": ["佩剑"],
                    "comment": "寒光剑",
                    "content": "玄铁锻造的古剑。"
                  }
                ]
              }
            }
        """.trimIndent()

        val imported = RpCardCodec.parseCardJson(v1Json, conversationId = "v1-test")
        assertEquals("v1", imported.specVersion)
        assertEquals("墨渊", imported.rpConfig.title)
        assertEquals("剑已出鞘，阁下请赐教。", imported.rpConfig.greeting)
        assertEquals("保持武侠留白感。", imported.rpConfig.authorNote)
        assertEquals(3, imported.rpConfig.authorNoteDepth)
        assertEquals(1, imported.importedLore.size)
        assertEquals(listOf("佩剑"), imported.importedLore.first().aliases)
    }

    @Test
    fun extractCardFromZipBytes_charxPackageExtractsCardJsonAndAvatar() {
        val cardJson = """
            {
              "spec": "chara_card_v3",
              "data": { "name": "夏洛特", "first_mes": "欢迎来到钟楼。" }
            }
        """.trimIndent()
        val pngBytes = buildMinimalValidPng()
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("card.json"))
            zos.write(cardJson.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("assets/icon/images/main.png"))
            zos.write(pngBytes)
            zos.closeEntry()
        }

        val extracted = RpCardCodec.extractCardFromZipBytes(baos.toByteArray())
        assertNotNull(extracted)
        assertNotNull(extracted!!.avatarBytes)
        assertEquals("png", extracted.avatarExt)
        val parsed = RpCardCodec.parseCardJson(extracted.cardJson, conversationId = "charx-1")
        assertEquals("夏洛特", parsed.rpConfig.title)
    }

    @Test
    fun exportCardToPngBytesFromPaths_usesAssetReaderForBundledSprites() {
        val palace = RpPresets.findById("palace_intrigue").config
        val requestedAssets = mutableListOf<String>()
        val pngCover = buildMinimalValidPng()
        val exported = RpCardCodec.exportCardToPngBytesFromPaths(
            rp = palace,
            filesDirPath = "/tmp/nonexistent-files-dir",
            assetReader = { rel ->
                requestedAssets += rel
                pngCover
            },
            imageTranscoder = { it },
        )
        assertTrue(RpCardCodec.isPngBytes(exported))
        assertTrue(requestedAssets.contains("rp_sprites/npc_xiao.webp"))
        val extracted = RpCardCodec.extractCardJsonFromPng(exported)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("萧景珩"))
    }
}
