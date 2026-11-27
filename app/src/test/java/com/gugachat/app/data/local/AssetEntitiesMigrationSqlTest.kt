package com.gugachat.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.LoreTimedEntryState
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.WireChatMessage
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class AssetEntitiesMigrationSqlTest {

    @Test
    fun allV4AssetEntitiesAndV3Entities_roundtripJsonCleanly() {
        val card = CharacterCardEntity(
            id = "card-1",
            name = "林晚棠",
            creator = "GuGu",
            characterVersion = "1.0",
            avatarPath = "rp_media/card-1_avatar.png",
            spritePath = "rp_media/card-1_sprite.png",
            expressionSpritesJson = """{"happy":"rp_media/card-1_happy.png"}""",
            tagsJson = """["仙侠","剧情"]""",
            playStyle = "tavern",
            description = "剑修师姐",
            personality = "外冷内热",
            scenario = "雨夜古庙",
            firstMes = "你来了。",
            alternateGreetingsJson = """["师弟，别来无恙。"]""",
            groupOnlyGreetingsJson = """[]""",
            mesExample = "<START>\n{{user}}: 你好",
            systemPrompt = "保持古风叙事",
            postHistoryInstructions = "输出动作描写",
            creatorNotes = "测试卡",
            depthPromptText = "记住师姐的伤势",
            depthPromptDepth = 3,
            depthPromptRole = "system",
            castJson = """[]""",
            regexScriptsJson = """[]""",
            visualThemeJson = """{}""",
            primaryWorldbookId = "wb-1",
            sourceSpec = "v3",
            contentFingerprint = "abc123",
            favorite = true,
            createdAt = 100L,
            updatedAt = 200L,
        )
        assertEquals(card, AppJson.decodeFromString(CharacterCardEntity.serializer(), AppJson.encodeToString(CharacterCardEntity.serializer(), card)))
        val minimalCard = AppJson.decodeFromString(CharacterCardEntity.serializer(), """{"id":"card-min"}""")
        assertEquals("card-min", minimalCard.id)
        assertEquals(4, minimalCard.depthPromptDepth)
        assertEquals("system", minimalCard.depthPromptRole)

        val worldbook = WorldbookEntity(
            id = "wb-1",
            name = "青岚志",
            description = "世界观设定",
            sourceFormat = "sillytavern",
            globalEnabled = true,
            scanDepth = 6,
            tokenBudget = 2000,
            recursiveScanning = true,
            entryCount = 12,
            totalEstimatedTokens = 980,
            contentFingerprint = "wbhash",
            createdAt = 10L,
            updatedAt = 20L,
        )
        assertEquals(worldbook, AppJson.decodeFromString(WorldbookEntity.serializer(), AppJson.encodeToString(WorldbookEntity.serializer(), worldbook)))
        val minimalWb = AppJson.decodeFromString(WorldbookEntity.serializer(), """{"id":"wb-min"}""")
        assertEquals(1600, minimalWb.tokenBudget)

        val wbEntry = WorldbookEntryEntity(
            id = "wbe-1",
            worldbookId = "wb-1",
            uid = 7,
            title = "青岚剑宗",
            content = "千年剑修宗门",
            category = "faction",
            keysJson = """["青岚","剑宗"]""",
            secondaryKeysJson = """["山门"]""",
            selectiveLogic = 1,
            constant = false,
            enabled = true,
            caseSensitive = false,
            matchWholeWords = true,
            useRegexKey = false,
            probability = 85,
            insertionOrder = 200,
            position = 4,
            depth = 2,
            outletRole = "assistant",
            scanDepthOverride = 5,
            sticky = 3,
            cooldown = 2,
            delay = 1,
            preventRecursion = true,
            excludeRecursion = false,
            groupName = "sects",
            groupWeight = 50,
            estimatedTokens = 64,
            createdAt = 10L,
            updatedAt = 20L,
        )
        assertEquals(wbEntry, AppJson.decodeFromString(WorldbookEntryEntity.serializer(), AppJson.encodeToString(WorldbookEntryEntity.serializer(), wbEntry)))
        val minimalEntry = AppJson.decodeFromString(WorldbookEntryEntity.serializer(), """{"id":"wbe-min"}""")
        assertEquals(100, minimalEntry.probability)
        assertEquals(true, minimalEntry.enabled)

        val persona = PersonaEntity(
            id = "persona-1",
            name = "沈清秋",
            avatarPath = "rp_media/persona.png",
            description = "外门弟子",
            injectionPosition = 1,
            injectionDepth = 2,
            isDefault = true,
            createdAt = 1L,
            updatedAt = 2L,
        )
        assertEquals(persona, AppJson.decodeFromString(PersonaEntity.serializer(), AppJson.encodeToString(PersonaEntity.serializer(), persona)))
        assertEquals(4, AppJson.decodeFromString(PersonaEntity.serializer(), """{"id":"p-min"}""").injectionDepth)

        val preset = PromptPresetEntity(
            id = "preset-1",
            name = "沉浸式剧场预设",
            sourceFormat = "sillytavern",
            blocksJson = AppJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(PromptPresetBlock.serializer()),
                listOf(PromptPresetBlock(identifier = "main", name = "Main", content = "You are a storyteller.")),
            ),
            regexScriptsJson = "[]",
            samplingPatchJson = """{"temperature":0.85}""",
            isDefaultRp = true,
            createdAt = 1L,
            updatedAt = 2L,
        )
        assertEquals(preset, AppJson.decodeFromString(PromptPresetEntity.serializer(), AppJson.encodeToString(PromptPresetEntity.serializer(), preset)))
        assertEquals("[]", AppJson.decodeFromString(PromptPresetEntity.serializer(), """{"id":"pr-min"}""").blocksJson)

        val crossRef = ConversationWorldbookCrossRef(
            conversationId = "conv-1",
            worldbookId = "wb-1",
            sortOrder = 2,
            enabled = true,
            updatedAt = 99L,
        )
        assertEquals(crossRef, AppJson.decodeFromString(ConversationWorldbookCrossRef.serializer(), AppJson.encodeToString(ConversationWorldbookCrossRef.serializer(), crossRef)))

        // Domain model extensions
        val timedState = LoreTimedEntryState(lastTriggeredTurn = 5, stickyUntilTurn = 8, cooldownUntilTurn = 10)
        assertEquals(timedState, AppJson.decodeFromString(LoreTimedEntryState.serializer(), AppJson.encodeToString(LoreTimedEntryState.serializer(), timedState)))

        val conv = ChatConversation(
            id = "conv-1",
            title = "Test",
            novel = NovelConfig(coverImagePath = "rp_media/cover.jpg"),
            rp = RpConfig(isPromptForked = true),
            createdAt = 1L,
            updatedAt = 2L,
            characterCardId = "card-1",
            personaId = "persona-1",
            presetId = "preset-1",
            loreTimedStatesJson = """{"wbe-1":{"lastTriggeredTurn":5,"stickyUntilTurn":8,"cooldownUntilTurn":10}}""",
        )
        val convEntity = conv.toEntity()
        assertEquals(conv, convEntity.toDomain())
        assertEquals(convEntity, AppJson.decodeFromString(ConversationEntity.serializer(), AppJson.encodeToString(ConversationEntity.serializer(), convEntity)))

        val wireMsg = WireChatMessage(role = "system", textContent = "depth", isDepthInjection = true)
        assertEquals(wireMsg, AppJson.decodeFromString(WireChatMessage.serializer(), AppJson.encodeToString(WireChatMessage.serializer(), wireMsg)))

        // Verify all existing Room v3 entities serialize cleanly for NDJSON backup
        val msg = MessageEntity(
            id = "m1", conversationId = "c1", role = "user", content = "hi", reasoning = "",
            model = null, createdAt = 1L, seq = 1, attachmentIdsJson = "[]", inputKind = null,
            rpInputKind = null, rpDiceRollJson = null, rpChoicesJson = "[]", novelChoicesJson = "[]",
            status = "done", error = null, usageJson = null, tokenEstimate = 1, variantsJson = "[]",
            activeVariantIndex = 0, editedAt = null, supersededAtSeq = null, deletedAt = null,
        )
        assertEquals(msg, AppJson.decodeFromString(MessageEntity.serializer(), AppJson.encodeToString(MessageEntity.serializer(), msg)))

        val um = UserMemoryEntity("um1", "preference", "likes tea", "[]", false, true, 1L, 2L, 3)
        assertEquals(um, AppJson.decodeFromString(UserMemoryEntity.serializer(), AppJson.encodeToString(UserMemoryEntity.serializer(), um)))

        val ch = NovelChapterEntity("ch1", "c1", 1, "Ch 1", "", "", "writing", 100, 1, null, 1L, 2L)
        assertEquals(ch, AppJson.decodeFromString(NovelChapterEntity.serializer(), AppJson.encodeToString(NovelChapterEntity.serializer(), ch)))

        val lore = NovelLoreEntity("l1", "c1", "character", "Hero", "desc", "[]", "[]", false, 50, true, 0, null, null, false, 1L, 2L, 5)
        assertEquals(lore, AppJson.decodeFromString(NovelLoreEntity.serializer(), AppJson.encodeToString(NovelLoreEntity.serializer(), lore)))

        val ledger = MemoryLedgerEntity("led1", "c1", "fact", "Fact", "desc", "[]", "[]", null, null, false, false, true, false, false, 0, null, null, null, "ai", 1L, 2L, 4)
        assertEquals(ledger, AppJson.decodeFromString(MemoryLedgerEntity.serializer(), AppJson.encodeToString(MemoryLedgerEntity.serializer(), ledger)))

        val ep = MemoryEpisodeEntity("ep1", "c1", 1, "Ep 1", "sum", "[]", 1, 5, false, false, 1L, 2L, 6)
        assertEquals(ep, AppJson.decodeFromString(MemoryEpisodeEntity.serializer(), AppJson.encodeToString(MemoryEpisodeEntity.serializer(), ep)))

        val slot = RpSaveSlotEntity("s1", "c1", "Slot 1", "", 1L, 2, "{}", "[]", "[]", "[]")
        assertEquals(slot, AppJson.decodeFromString(RpSaveSlotEntity.serializer(), AppJson.encodeToString(RpSaveSlotEntity.serializer(), slot)))

        val att = AttachmentMetaEntity("a1", "image/jpeg", 100, 100, 200L, "chat_attachments/a1.jpg", 1L)
        assertEquals(att, AppJson.decodeFromString(AttachmentMetaEntity.serializer(), AppJson.encodeToString(AttachmentMetaEntity.serializer(), att)))
    }

    @Test
    fun migration3To4_matchesGeneratedRoomV4SchemaByteForByte() {
        val executedSql = ArrayList<String>()
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty() && args[0] is String) {
                executedSql.add(args[0] as String)
            }
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_3_4.migrate(dbProxy)
        assertEquals(AppDatabase.MIGRATION_3_4_STATEMENTS, executedSql)

        assertTrue(executedSql.contains("ALTER TABLE `conversations` ADD COLUMN `characterCardId` TEXT"))
        assertTrue(executedSql.contains("ALTER TABLE `conversations` ADD COLUMN `personaId` TEXT"))
        assertTrue(executedSql.contains("ALTER TABLE `conversations` ADD COLUMN `presetId` TEXT"))
        assertTrue(executedSql.contains("ALTER TABLE `conversations` ADD COLUMN `loreTimedStatesJson` TEXT NOT NULL DEFAULT '{}'"))

        val schemaFile = listOf(
            File("schemas/com.gugachat.app.data.local.AppDatabase/4.json"),
            File("app/schemas/com.gugachat.app.data.local.AppDatabase/4.json"),
        ).firstOrNull { it.isFile }
        assertNotNull("Expected Room 4.json schema file to exist", schemaFile)

        val root = AppJson.parseToJsonElement(schemaFile!!.readText(Charsets.UTF_8)).jsonObject
        val dbObj = root["database"]!!.jsonObject
        assertEquals("4", dbObj["version"]!!.jsonPrimitive.content)

        val entities = dbObj["entities"]!!.jsonArray.associateBy {
            it.jsonObject["tableName"]!!.jsonPrimitive.content
        }

        // Verify conversations table has the 4 new columns
        val convFields = entities["conversations"]!!.jsonObject["fields"]!!.jsonArray.associateBy {
            it.jsonObject["columnName"]!!.jsonPrimitive.content
        }
        assertEquals("TEXT", convFields["characterCardId"]!!.jsonObject["affinity"]!!.jsonPrimitive.content)
        assertEquals("false", convFields["characterCardId"]!!.jsonObject["notNull"]!!.jsonPrimitive.content)
        assertEquals("TEXT", convFields["personaId"]!!.jsonObject["affinity"]!!.jsonPrimitive.content)
        assertEquals("false", convFields["personaId"]!!.jsonObject["notNull"]!!.jsonPrimitive.content)
        assertEquals("TEXT", convFields["presetId"]!!.jsonObject["affinity"]!!.jsonPrimitive.content)
        assertEquals("false", convFields["presetId"]!!.jsonObject["notNull"]!!.jsonPrimitive.content)
        assertEquals("TEXT", convFields["loreTimedStatesJson"]!!.jsonObject["affinity"]!!.jsonPrimitive.content)
        assertEquals("true", convFields["loreTimedStatesJson"]!!.jsonObject["notNull"]!!.jsonPrimitive.content)

        val expectedNewTables = listOf(
            "character_cards",
            "worldbooks",
            "worldbook_entries",
            "personas",
            "prompt_presets",
            "conversation_worldbooks",
        )
        for (tableName in expectedNewTables) {
            val entityObj = entities[tableName]?.jsonObject
            assertNotNull("Missing table $tableName in 4.json", entityObj)
            val createTableSql = entityObj!!["createSql"]!!.jsonPrimitive.content
                .replace("\${TABLE_NAME}", tableName)
            assertTrue(
                "MIGRATION_3_4 missing exact CREATE TABLE for $tableName.\nExpected: $createTableSql",
                executedSql.contains(createTableSql),
            )
            val indices = entityObj["indices"]!!.jsonArray
            for (idxElem in indices) {
                val createIndexSql = idxElem.jsonObject["createSql"]!!.jsonPrimitive.content
                    .replace("\${TABLE_NAME}", tableName)
                assertTrue(
                    "MIGRATION_3_4 missing exact CREATE INDEX for $tableName.\nExpected: $createIndexSql",
                    executedSql.contains(createIndexSql),
                )
            }
        }
    }
}
