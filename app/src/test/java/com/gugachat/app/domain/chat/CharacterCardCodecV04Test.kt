package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.AssetDao
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.ChatDao
import com.gugachat.app.data.local.ConversationEntity
import com.gugachat.app.data.local.ConversationWorldbookCrossRef
import com.gugachat.app.data.local.MemoryEpisodeEntity
import com.gugachat.app.data.local.MemoryLedgerEntity
import com.gugachat.app.data.local.MessageEntity
import com.gugachat.app.data.local.NovelChapterEntity
import com.gugachat.app.data.local.NovelLoreEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.RpSaveSlotEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CharacterCardCodecV04Test {

    private fun writePngChunk(out: ByteArrayOutputStream, type: String, payload: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(ByteBuffer.allocate(4).putInt(payload.size).array())
        out.write(typeBytes)
        out.write(payload)
        val crc = CRC32().apply {
            update(typeBytes)
            update(payload)
        }
        out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
    }

    private fun buildZtxtPngWithPayload(keyword: String, utf8Value: String): ByteArray {
        val base = RpCardCodec.createMinimalCardPngBytes(width = 32, height = 32)
        val iendOffset = base.size - 12 // final empty IEND chunk
        val deflater = Deflater()
        deflater.setInput(utf8Value.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val comp = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n <= 0) break
            comp.write(buf, 0, n)
        }
        deflater.end()

        val payload = ByteArrayOutputStream()
        payload.write(keyword.toByteArray(Charsets.ISO_8859_1))
        payload.write(0) // NUL separator
        payload.write(0) // compression method 0
        payload.write(comp.toByteArray())

        val out = ByteArrayOutputStream()
        out.write(base, 0, iendOffset)
        writePngChunk(out, "zTXt", payload.toByteArray())
        out.write(base, iendOffset, 12)
        return out.toByteArray()
    }

    @Test
    fun rpCardCodecLenientBase64ZtxtUtf8AndCharxAssetsAndNonPngExport() {
        // 1) Pure-JVM lenient Base64 decoder handles URL-safe unpadded + MIME line breaks + CJK UTF-8
        val rawText = "角色扮演测试：星河旅人与{{char}}"
        val urlSafeUnpadded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawText.toByteArray(Charsets.UTF_8))
        val mimeWrapped = Base64.getMimeEncoder(12, "\r\n".toByteArray()).encodeToString(rawText.toByteArray(Charsets.UTF_8))
        assertEquals(rawText, RpCardCodec.decodeBase64Lenient(urlSafeUnpadded).toString(Charsets.UTF_8))
        assertEquals(rawText, RpCardCodec.decodeBase64Lenient(mimeWrapped).toString(Charsets.UTF_8))

        // 2) PNG zTXt UTF-8 fallback preserves CJK characters inside compressed chunk
        val cjkCardJson = """{"spec":"chara_card_v3","spec_version":"3.0","data":{"name":"星野凛","description":"夜空守望者","first_mes":"欢迎，{{user}}，我是{{char}}。"}}"""
        val ztxtB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(cjkCardJson.toByteArray(Charsets.UTF_8))
        val ztxtPng = buildZtxtPngWithPayload("ccv3", ztxtB64)
        val extractedZtxt = RpCardCodec.extractCardJsonFromPng(ztxtPng)
        assertNotNull(extractedZtxt)
        assertTrue(extractedZtxt!!.contains("星野凛"))
        assertTrue(extractedZtxt.contains("夜空守望者"))

        // 3) .charx CCv3 data.assets (icon, emotion map, background) extracted cleanly
        val charxCardJson = """
            {
              "spec": "chara_card_v3",
              "spec_version": "3.0",
              "data": {
                "name": "艾蕾娜",
                "description": "炼金术士",
                "first_mes": "你好，{{user}}，这里有{{char}}刚调配好的药水。",
                "alternate_greetings": ["{{char}}递给{{user}}一瓶蓝药剂。"],
                "assets": [
                  {"type": "icon", "name": "main", "uri": "embeded://assets/icon/avatar.png", "ext": "png"},
                  {"type": "emotion", "name": "happy", "uri": "embeded://assets/emotion/happy.png", "ext": "png"},
                  {"type": "emotion", "name": "angry", "uri": "assets/emotion/angry.png", "ext": "png"},
                  {"type": "background", "name": "lab", "uri": "assets/bg/lab.jpg", "ext": "jpg"}
                ]
              }
            }
        """.trimIndent()
        val fakeIconPng = RpCardCodec.createMinimalCardPngBytes(width = 48, height = 48, r = 10, g = 20, b = 30)
        val fakeHappyPng = RpCardCodec.createMinimalCardPngBytes(width = 40, height = 40, r = 200, g = 180, b = 10)
        val fakeAngryPng = RpCardCodec.createMinimalCardPngBytes(width = 40, height = 40, r = 220, g = 40, b = 40)
        val fakeBgJpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

        val charxBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                fun putEntry(name: String, data: ByteArray) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(data)
                    zos.closeEntry()
                }
                putEntry("card.json", charxCardJson.toByteArray(Charsets.UTF_8))
                putEntry("assets/icon/avatar.png", fakeIconPng)
                putEntry("assets/emotion/happy.png", fakeHappyPng)
                putEntry("assets/emotion/angry.png", fakeAngryPng)
                putEntry("assets/bg/lab.jpg", fakeBgJpg)
            }
            bos.toByteArray()
        }

        val zipExtracted = RpCardCodec.extractCardFromZipBytes(charxBytes)
        assertNotNull(zipExtracted)
        assertTrue(zipExtracted!!.avatarBytes!!.contentEquals(fakeIconPng))
        assertEquals(setOf("happy", "angry"), zipExtracted.expressionSprites.keys)
        assertTrue(zipExtracted.expressionSprites["happy"]!!.first.contentEquals(fakeHappyPng))
        assertTrue(zipExtracted.backgroundBytes!!.contentEquals(fakeBgJpg))
        assertEquals("jpg", zipExtracted.backgroundExt)

        // 4) Non-PNG (JPG) or spritePath-only export via transcoder avoids 64x64 fallback & embeds both ccv3 + chara chunks
        val highResPng = RpCardCodec.createMinimalCardPngBytes(width = 192, height = 256, r = 80, g = 120, b = 160)
        val rpForExport = RpConfig(
            title = "导出测试卡",
            playerName = "旅人",
            cast = listOf(RpCharacter(id = "lead", name = "艾蕾娜", background = "炼金术士")),
            greeting = "欢迎回来",
        )
        val exportedBytes = RpCardCodec.exportCardToPngBytes(
            rp = rpForExport,
            candidateImages = listOf(fakeBgJpg),
            imageTranscoder = { raw -> if (raw.contentEquals(fakeBgJpg)) highResPng else null },
        )
        // Parse IHDR width/height right after PNG signature (8) + len(4) + "IHDR"(4) = offset 16
        val bb = ByteBuffer.wrap(exportedBytes, 16, 8)
        assertEquals(192, bb.int)
        assertEquals(256, bb.int)
        val roundtripJson = RpCardCodec.extractCardJsonFromPng(exportedBytes)
        assertNotNull(roundtripJson)
        assertTrue(roundtripJson!!.contains("艾蕾娜"))
    }

    @Test
    fun tavernAssetCodecPreservesFullWorldbookV2AndPromptPresetV4() {
        val worldbookJson = """
            {
              "name": "北境秘典",
              "scan_depth": 6,
              "token_budget": 2400,
              "recursive_scanning": false,
              "entries": {
                "0": {
                  "uid": 10,
                  "comment": "龙族禁咒",
                  "content": "只有冰霜与火焰共鸣时才能咏唱。",
                  "key": ["龙族"],
                  "keysecondary": ["火元素", "封印"],
                  "selectiveLogic": 3,
                  "constant": false,
                  "position": 4,
                  "depth": 2,
                  "role": 1,
                  "sticky": 3,
                  "cooldown": 2,
                  "delay": 1,
                  "probability": 65,
                  "order": 350,
                  "preventRecursion": true,
                  "excludeRecursion": true
                },
                "1": {
                  "uid": 11,
                  "comment": "纯注释条目",
                  "content": "这是开发者备注，不应被注释名误触发。",
                  "key": [],
                  "constant": false
                }
              }
            }
        """.trimIndent()

        val wbV4 = TavernAssetCodec.parseWorldbookV4(worldbookJson)
        assertEquals("北境秘典", wbV4.worldbook.name)
        assertEquals(6, wbV4.worldbook.scanDepth)
        assertEquals(2400, wbV4.worldbook.tokenBudget)
        assertFalse(wbV4.worldbook.recursiveScanning)
        assertEquals(2, wbV4.entries.size)

        val e0 = wbV4.entries.first { it.uid == 10 }
        assertEquals(listOf("龙族"), AppJson.decodeFromString(ListSerializer(kotlinx.serialization.json.JsonPrimitive.serializer()), e0.keysJson).map { it.content })
        assertEquals(listOf("火元素", "封印"), AppJson.decodeFromString(ListSerializer(kotlinx.serialization.json.JsonPrimitive.serializer()), e0.secondaryKeysJson).map { it.content })
        assertEquals(3, e0.selectiveLogic)
        assertEquals(4, e0.position)
        assertEquals(2, e0.depth)
        assertEquals("user", e0.outletRole)
        assertEquals(3, e0.sticky)
        assertEquals(2, e0.cooldown)
        assertEquals(1, e0.delay)
        assertEquals(65, e0.probability)
        assertEquals(350, e0.insertionOrder)
        assertTrue(e0.preventRecursion)
        assertTrue(e0.excludeRecursion)

        val commentOnly = wbV4.entries.first { it.uid == 11 }
        // Empty primary keys MUST remain [] rather than falling back to comment title!
        assertEquals("[]", commentOnly.keysJson)

        val presetJson = """
            {
              "temperature": 0.75,
              "top_p": 0.92,
              "prompts": [
                {"identifier": "main", "name": "System Main", "role": "system", "content": "主系统指令", "marker": false, "enabled": true, "injection_position": 0, "injection_depth": 4},
                {"identifier": "charDesc", "name": "Char Desc", "role": "system", "content": "", "marker": true, "enabled": true},
                {"identifier": "cot", "name": "Hidden CoT", "role": "system", "content": "已关闭思维链", "marker": false, "enabled": true},
                {"identifier": "d1_note", "name": "Depth1 Note", "role": "user", "content": "保持细节", "marker": false, "enabled": true, "injection_position": 1, "injection_depth": 1}
              ],
              "prompt_order": [
                {
                  "character_id": 100001,
                  "order": [
                    {"identifier": "main", "enabled": true},
                    {"identifier": "charDesc", "enabled": true},
                    {"identifier": "cot", "enabled": false},
                    {"identifier": "d1_note", "enabled": true}
                  ]
                }
              ]
            }
        """.trimIndent()
        val presetEntity = TavernAssetCodec.parsePromptPresetV4(presetJson, fileName = "moonlight_preset.json")
        assertEquals("moonlight_preset", presetEntity.name)
        val decodedBlocks = AppJson.decodeFromString(ListSerializer(PromptPresetBlock.serializer()), presetEntity.blocksJson)
        assertEquals(4, decodedBlocks.size)
        assertEquals(listOf("main", "charDesc", "cot", "d1_note"), decodedBlocks.map { it.identifier })
        assertFalse("Order override disabled=false must be respected", decodedBlocks.first { it.identifier == "cot" }.enabled)
        assertTrue(decodedBlocks.first { it.identifier == "charDesc" }.marker)
        assertEquals(1, decodedBlocks.first { it.identifier == "d1_note" }.injectionPosition)
        assertEquals(1, decodedBlocks.first { it.identifier == "d1_note" }.injectionDepth)
        assertEquals("user", decodedBlocks.first { it.identifier == "d1_note" }.role)
    }

    @Test
    fun externalImportCodecPreservesBinaryAssetsExpandsGreetingMacrosAndPopulatesCastSnapshot() {
        val rawCardJson = """
            {
              "spec": "chara_card_v3",
              "spec_version": "3.0",
              "data": {
                "name": "卡莲",
                "description": "女巫",
                "first_mes": "你好{{user}}，{{char}}正在等你。",
                "alternate_greetings": ["{{char}}合上书页看向{{user}}。"],
                "character_book": {
                  "name": "魔女手札",
                  "entries": [
                    {"uid": 1, "comment": "魔药", "content": "月光草配方", "keys": ["魔药"], "keysecondary": ["坩埚"], "selectiveLogic": 2}
                  ]
                }
              }
            }
        """.trimIndent()
        val basePng = RpCardCodec.createMinimalCardPngBytes(width = 64, height = 64, r = 90, g = 60, b = 120)
        val embeddedPng = RpCardCodec.embedCardJsonInPng(basePng, rawCardJson)

        val bundle = ExternalImportCodec.parseBytes(embeddedPng, fileName = "karen.png")
        assertEquals(1, bundle.items.size)
        val item = bundle.items.first()
        assertNotNull("PNG import must preserve avatarBytes", item.avatarBytes)
        assertEquals("png", item.avatarExt)
        assertNotNull(item.worldbookV4)
        assertEquals("魔女手札", item.worldbookV4!!.worldbook.name)
        assertEquals(2, item.worldbookV4!!.entries.first().selectiveLogic)

        val greetingMsg = item.messages.first()
        assertEquals("你好旅人，卡莲正在等你。", greetingMsg.content)
        assertEquals(2, greetingMsg.variants.size)
        assertEquals("卡莲合上书页看向旅人。", greetingMsg.variants[1].content)
        assertEquals("卡莲", greetingMsg.variants[0].rpSpeakerName)
        assertTrue(!greetingMsg.variants[0].rpCastSnapshot.isNullOrEmpty())
        assertEquals("卡莲", greetingMsg.variants[0].rpCastSnapshot!!.first().name)
    }

    @Test
    fun legacyAssetHarvesterIdempotentlyHarvestsWithoutMutatingLegacyConversations() = runBlocking {
        val chatDao = FakeChatDao()
        val assetDao = FakeAssetDao()

        val conv1 = ChatConversation(
            id = "conv_1",
            title = "雪夜茶馆",
            mode = ConversationMode.rp,
            rp = RpConfig(
                title = "雪夜茶馆",
                worldview = "古风山庄",
                playerName = "顾尘",
                playerPersona = "流浪剑客",
                cast = listOf(RpCharacter(id = "npc1", name = "沈清秋", background = "茶馆老板娘")),
                greeting = "雪下大了，进来喝杯热茶吧。",
            ),
            createdAt = 100L,
            updatedAt = 200L,
        ).toEntity()
        val conv2Duplicate = ChatConversation(
            id = "conv_2_dup",
            title = "雪夜茶馆二周目",
            mode = ConversationMode.rp,
            rp = RpConfig(
                title = "雪夜茶馆",
                worldview = "古风山庄",
                playerName = "旅人",
                cast = listOf(RpCharacter(id = "npc1", name = "沈清秋", background = "茶馆老板娘")),
                greeting = "雪下大了，进来喝杯热茶吧。",
            ),
            createdAt = 300L,
            updatedAt = 400L,
        ).toEntity()
        chatDao.upsertConversations(listOf(conv1, conv2Duplicate))

        val firstRun = LegacyAssetHarvester.harvestIfNeeded(chatDao, assetDao, nowMs = { 999L })
        assertEquals(1, firstRun.harvestedCardCount)
        assertEquals(1, firstRun.harvestedPersonaCount) // 顾尘 harvested; default anonymous "旅人" skipped
        assertEquals(1, firstRun.skippedDuplicateCount) // conv_2_dup deduplicated by sha256 fingerprint

        val secondRun = LegacyAssetHarvester.harvestIfNeeded(chatDao, assetDao, nowMs = { 1000L })
        assertEquals(0, secondRun.harvestedCardCount)
        assertEquals(0, secondRun.harvestedPersonaCount)
        assertEquals(2, secondRun.skippedDuplicateCount)

        // Legacy ConversationEntity rows MUST NEVER be mutated!
        val conv1After = chatDao.getConversationById("conv_1")!!
        assertEquals(conv1.rpJson, conv1After.rpJson)
        assertEquals(conv1.characterCardId, conv1After.characterCardId)
        assertEquals(conv1.personaId, conv1After.personaId)
        assertEquals(conv1.presetId, conv1After.presetId)
        assertEquals(0, chatDao.upsertCallsAfterInitial)
    }

    private class FakeChatDao : ChatDao {
        private val convs = LinkedHashMap<String, ConversationEntity>()
        var upsertCallsAfterInitial = 0

        override fun observeConversations(): Flow<List<ConversationEntity>> = flowOf(convs.values.toList())
        override suspend fun getAllConversations(): List<ConversationEntity> = convs.values.toList()
        override suspend fun getConversationsPaged(limit: Int, offset: Int): List<ConversationEntity> = convs.values.drop(offset).take(limit)
        override suspend fun getConversationById(id: String): ConversationEntity? = convs[id]
        override suspend fun getConversationsByIds(ids: List<String>): List<ConversationEntity> = ids.distinct().mapNotNull { convs[it] }
        override suspend fun upsertConversation(entity: ConversationEntity) {
            upsertCallsAfterInitial++
            convs[entity.id] = entity
        }
        override suspend fun upsertConversations(entities: List<ConversationEntity>) {
            entities.forEach { convs[it.id] = it }
        }
        override suspend fun deleteConversation(id: String) { convs.remove(id) }
        override fun observeMessages(convId: String): Flow<List<MessageEntity>> = flowOf(emptyList())
        override suspend fun getMessagesForConversation(convId: String): List<MessageEntity> = emptyList()
        override suspend fun getAllMessages(): List<MessageEntity> = emptyList()
        override suspend fun getMessagesPaged(limit: Int, offset: Int): List<MessageEntity> = emptyList()
        override suspend fun upsertMessage(entity: MessageEntity) = Unit
        override suspend fun upsertMessages(entities: List<MessageEntity>) = Unit
        override suspend fun abortOrphanStreamingMessages() = Unit
        override suspend fun deleteMessagesForConversation(convId: String) = Unit
        override suspend fun deleteMessagesForConversations(convIds: List<String>) = Unit
        override fun observeNovelChapters(convId: String): Flow<List<NovelChapterEntity>> = flowOf(emptyList())
        override suspend fun getNovelChaptersForConversation(convId: String): List<NovelChapterEntity> = emptyList()
        override suspend fun getAllNovelChapters(): List<NovelChapterEntity> = emptyList()
        override suspend fun getNovelChaptersPaged(limit: Int, offset: Int): List<NovelChapterEntity> = emptyList()
        override suspend fun upsertNovelChapter(entity: NovelChapterEntity) = Unit
        override suspend fun upsertNovelChapters(entities: List<NovelChapterEntity>) = Unit
        override suspend fun deleteNovelChapter(id: String) = Unit
        override suspend fun deleteNovelChaptersForConversation(convId: String) = Unit
        override suspend fun deleteNovelChaptersForConversations(convIds: List<String>) = Unit
        override fun observeNovelLore(convId: String): Flow<List<NovelLoreEntity>> = flowOf(emptyList())
        override suspend fun getNovelLoreForConversation(convId: String): List<NovelLoreEntity> = emptyList()
        override suspend fun getAllNovelLore(): List<NovelLoreEntity> = emptyList()
        override suspend fun getNovelLorePaged(limit: Int, offset: Int): List<NovelLoreEntity> = emptyList()
        override suspend fun upsertNovelLore(entity: NovelLoreEntity) = Unit
        override suspend fun upsertNovelLoreList(entities: List<NovelLoreEntity>) = Unit
        override suspend fun deleteNovelLore(id: String) = Unit
        override suspend fun deleteNovelLoreForConversation(convId: String) = Unit
        override suspend fun deleteNovelLoreForConversations(convIds: List<String>) = Unit
        override fun observeMemoryLedger(convId: String): Flow<List<MemoryLedgerEntity>> = flowOf(emptyList())
        override suspend fun getMemoryLedgerForConversation(convId: String): List<MemoryLedgerEntity> = emptyList()
        override suspend fun getAllMemoryLedger(): List<MemoryLedgerEntity> = emptyList()
        override suspend fun getMemoryLedgerPaged(limit: Int, offset: Int): List<MemoryLedgerEntity> = emptyList()
        override suspend fun upsertMemoryLedger(entity: MemoryLedgerEntity) = Unit
        override suspend fun upsertMemoryLedgerList(entities: List<MemoryLedgerEntity>) = Unit
        override suspend fun deleteMemoryLedger(id: String) = Unit
        override suspend fun deleteMemoryLedgerForConversation(convId: String) = Unit
        override suspend fun deleteMemoryLedgerForConversations(convIds: List<String>) = Unit
        override fun observeMemoryEpisodes(convId: String): Flow<List<MemoryEpisodeEntity>> = flowOf(emptyList())
        override suspend fun getMemoryEpisodesForConversation(convId: String): List<MemoryEpisodeEntity> = emptyList()
        override suspend fun getAllMemoryEpisodes(): List<MemoryEpisodeEntity> = emptyList()
        override suspend fun getMemoryEpisodesPaged(limit: Int, offset: Int): List<MemoryEpisodeEntity> = emptyList()
        override suspend fun upsertMemoryEpisode(entity: MemoryEpisodeEntity) = Unit
        override suspend fun upsertMemoryEpisodes(entities: List<MemoryEpisodeEntity>) = Unit
        override suspend fun deleteMemoryEpisode(id: String) = Unit
        override suspend fun deleteMemoryEpisodesForConversation(convId: String) = Unit
        override suspend fun deleteMemoryEpisodesForConversations(convIds: List<String>) = Unit
        override fun observeRpSaveSlots(convId: String): Flow<List<RpSaveSlotEntity>> = flowOf(emptyList())
        override suspend fun getRpSaveSlotsForConversation(convId: String): List<RpSaveSlotEntity> = emptyList()
        override suspend fun getAllRpSaveSlots(): List<RpSaveSlotEntity> = emptyList()
        override suspend fun getRpSaveSlotsPaged(limit: Int, offset: Int): List<RpSaveSlotEntity> = emptyList()
        override suspend fun upsertRpSaveSlot(entity: RpSaveSlotEntity) = Unit
        override suspend fun upsertRpSaveSlots(entities: List<RpSaveSlotEntity>) = Unit
        override suspend fun deleteRpSaveSlot(id: String) = Unit
        override suspend fun deleteRpSaveSlotsForConversation(convId: String) = Unit
        override suspend fun deleteRpSaveSlotsForConversations(convIds: List<String>) = Unit
        override suspend fun deleteConversationWorldbooksForConversation(convId: String) = Unit
        override suspend fun deleteMemoryEmbeddingsForConversation(convId: String) = Unit
        override suspend fun deleteMemoryEmbeddingsForConversations(convIds: List<String>) = Unit
        override suspend fun clearAllConversations() { convs.clear() }
        override suspend fun clearAllMessages() = Unit
        override suspend fun clearAllNovelChapters() = Unit
        override suspend fun clearAllNovelLore() = Unit
        override suspend fun clearAllMemoryLedger() = Unit
        override suspend fun clearAllMemoryEpisodes() = Unit
        override suspend fun clearAllRpSaveSlots() = Unit
    }

    private class FakeAssetDao : AssetDao {
        private val cards = LinkedHashMap<String, CharacterCardEntity>()
        private val worldbooks = LinkedHashMap<String, WorldbookEntity>()
        private val wbEntries = LinkedHashMap<String, WorldbookEntryEntity>()
        private val personas = LinkedHashMap<String, PersonaEntity>()
        private val presets = LinkedHashMap<String, PromptPresetEntity>()
        private val refs = ArrayList<ConversationWorldbookCrossRef>()

        override fun observeCharacterCards(): Flow<List<CharacterCardEntity>> = flowOf(cards.values.toList())
        override suspend fun getAllCharacterCards(): List<CharacterCardEntity> = cards.values.toList()
        override suspend fun getCharacterCardsPaged(limit: Int, offset: Int): List<CharacterCardEntity> = cards.values.drop(offset).take(limit)
        override suspend fun getCharacterCardById(id: String): CharacterCardEntity? = cards[id]
        override suspend fun getCharacterCardsByIds(ids: List<String>): List<CharacterCardEntity> = ids.distinct().mapNotNull { cards[it] }
        override suspend fun getCardByFingerprint(fingerprint: String): CharacterCardEntity? =
            cards.values.firstOrNull { it.contentFingerprint == fingerprint }
        override suspend fun upsertCharacterCard(entity: CharacterCardEntity) { cards[entity.id] = entity }
        override suspend fun upsertCharacterCards(entities: List<CharacterCardEntity>) { entities.forEach { cards[it.id] = it } }
        override suspend fun deleteCharacterCard(id: String) { cards.remove(id) }
        override suspend fun clearAllCharacterCards() { cards.clear() }

        override fun observeWorldbooks(): Flow<List<WorldbookEntity>> = flowOf(worldbooks.values.toList())
        override fun observeGlobalWorldbooks(): Flow<List<WorldbookEntity>> = flowOf(worldbooks.values.filter { it.globalEnabled })
        override suspend fun getAllWorldbooks(): List<WorldbookEntity> = worldbooks.values.toList()
        override suspend fun getGlobalEnabledWorldbooks(): List<WorldbookEntity> = worldbooks.values.filter { it.globalEnabled }
        override suspend fun getWorldbooksPaged(limit: Int, offset: Int): List<WorldbookEntity> = worldbooks.values.drop(offset).take(limit)
        override suspend fun getWorldbookById(id: String): WorldbookEntity? = worldbooks[id]
        override suspend fun getWorldbooksByIds(ids: List<String>): List<WorldbookEntity> = ids.distinct().mapNotNull { worldbooks[it] }
        override suspend fun getWorldbookByFingerprint(fingerprint: String): WorldbookEntity? =
            worldbooks.values.firstOrNull { it.contentFingerprint == fingerprint }
        override suspend fun upsertWorldbook(entity: WorldbookEntity) { worldbooks[entity.id] = entity }
        override suspend fun upsertWorldbooks(entities: List<WorldbookEntity>) { entities.forEach { worldbooks[it.id] = it } }
        override suspend fun deleteWorldbook(id: String) { worldbooks.remove(id) }
        override suspend fun clearAllWorldbooks() { worldbooks.clear() }

        override fun observeWorldbookEntries(worldbookId: String): Flow<List<WorldbookEntryEntity>> = flowOf(emptyList())
        override suspend fun getWorldbookEntries(worldbookId: String): List<WorldbookEntryEntity> = wbEntries.values.filter { it.worldbookId == worldbookId }
        override suspend fun getEnabledEntriesForWorldbooks(worldbookIds: List<String>): List<WorldbookEntryEntity> =
            wbEntries.values.filter { it.worldbookId in worldbookIds && it.enabled }
        override suspend fun getWorldbookEntriesPaged(limit: Int, offset: Int): List<WorldbookEntryEntity> = wbEntries.values.drop(offset).take(limit)
        override suspend fun getWorldbookEntryById(id: String): WorldbookEntryEntity? = wbEntries[id]
        override suspend fun getWorldbookEntriesByIds(ids: List<String>): List<WorldbookEntryEntity> = ids.distinct().mapNotNull { wbEntries[it] }
        override suspend fun upsertWorldbookEntry(entity: WorldbookEntryEntity) { wbEntries[entity.id] = entity }
        override suspend fun upsertWorldbookEntries(entities: List<WorldbookEntryEntity>) { entities.forEach { wbEntries[it.id] = it } }
        override suspend fun deleteWorldbookEntry(id: String) { wbEntries.remove(id) }
        override suspend fun deleteEntriesForWorldbook(worldbookId: String) { wbEntries.entries.removeAll { it.value.worldbookId == worldbookId } }
        override suspend fun deleteEntriesForWorldbooks(worldbookIds: List<String>) {
            val set = worldbookIds.toHashSet()
            wbEntries.entries.removeAll { it.value.worldbookId in set }
        }
        override suspend fun clearAllWorldbookEntries() { wbEntries.clear() }

        override fun observePersonas(): Flow<List<PersonaEntity>> = flowOf(personas.values.toList())
        override suspend fun getAllPersonas(): List<PersonaEntity> = personas.values.toList()
        override suspend fun getPersonasPaged(limit: Int, offset: Int): List<PersonaEntity> = personas.values.drop(offset).take(limit)
        override suspend fun getPersonaById(id: String): PersonaEntity? = personas[id]
        override suspend fun getPersonasByIds(ids: List<String>): List<PersonaEntity> = ids.distinct().mapNotNull { personas[it] }
        override suspend fun getDefaultPersona(): PersonaEntity? = personas.values.firstOrNull { it.isDefault }
        override suspend fun upsertPersona(entity: PersonaEntity) { personas[entity.id] = entity }
        override suspend fun upsertPersonas(entities: List<PersonaEntity>) { entities.forEach { personas[it.id] = it } }
        override suspend fun setDefaultPersona(personaId: String) = Unit
        override suspend fun deletePersona(id: String) { personas.remove(id) }
        override suspend fun clearAllPersonas() { personas.clear() }

        override fun observePromptPresets(): Flow<List<PromptPresetEntity>> = flowOf(presets.values.toList())
        override suspend fun getAllPromptPresets(): List<PromptPresetEntity> = presets.values.toList()
        override suspend fun getPromptPresetsPaged(limit: Int, offset: Int): List<PromptPresetEntity> = presets.values.drop(offset).take(limit)
        override suspend fun getPromptPresetById(id: String): PromptPresetEntity? = presets[id]
        override suspend fun getPromptPresetsByIds(ids: List<String>): List<PromptPresetEntity> = ids.distinct().mapNotNull { presets[it] }
        override suspend fun getDefaultRpPromptPreset(): PromptPresetEntity? = presets.values.firstOrNull { it.isDefaultRp }
        override suspend fun upsertPromptPreset(entity: PromptPresetEntity) { presets[entity.id] = entity }
        override suspend fun upsertPromptPresets(entities: List<PromptPresetEntity>) { entities.forEach { presets[it.id] = it } }
        override suspend fun setDefaultRpPromptPreset(presetId: String) = Unit
        override suspend fun deletePromptPreset(id: String) { presets.remove(id) }
        override suspend fun clearAllPromptPresets() { presets.clear() }

        override fun observeMountedWorldbookRefs(convId: String): Flow<List<ConversationWorldbookCrossRef>> = flowOf(emptyList())
        override suspend fun getMountedWorldbookRefs(convId: String): List<ConversationWorldbookCrossRef> = refs.filter { it.conversationId == convId }
        override suspend fun getMountedWorldbooks(convId: String): List<WorldbookEntity> = emptyList()
        override suspend fun getConversationWorldbooksPaged(limit: Int, offset: Int): List<ConversationWorldbookCrossRef> = refs.drop(offset).take(limit)
        override suspend fun upsertConversationWorldbooks(refs: List<ConversationWorldbookCrossRef>) { this.refs.addAll(refs) }
        override suspend fun deleteConversationWorldbooks(convId: String) { refs.removeAll { it.conversationId == convId } }
        override suspend fun deleteConversationWorldbooksForConversations(convIds: List<String>) {
            val set = convIds.toHashSet()
            refs.removeAll { it.conversationId in set }
        }
        override suspend fun deleteWorldbookRefsByWorldbookId(worldbookId: String) { refs.removeAll { it.worldbookId == worldbookId } }
        override suspend fun clearAllConversationWorldbooks() { refs.clear() }
    }
}
