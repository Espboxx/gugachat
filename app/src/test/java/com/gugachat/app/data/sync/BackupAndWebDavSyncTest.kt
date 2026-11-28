package com.gugachat.app.data.sync

import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.WebDavSyncConfig
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.OnlineImageAssetDto
import com.gugachat.app.data.api.OnlineImageGenerationDto
import com.gugachat.app.data.api.OnlineImagePresetDto
import com.gugachat.app.data.local.AssetDao
import com.gugachat.app.data.local.AttachmentDao
import com.gugachat.app.data.local.AttachmentMetaEntity
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.ChatBackupCodec
import com.gugachat.app.data.local.ChatDao
import com.gugachat.app.data.local.ConversationEntity
import com.gugachat.app.data.local.ConversationWorldbookCrossRef
import com.gugachat.app.data.local.GuGuChatBackupPayload
import com.gugachat.app.data.local.MemoryEmbeddingDao
import com.gugachat.app.data.local.MemoryEmbeddingEntity
import com.gugachat.app.data.local.MemoryEmbeddingHashRow
import com.gugachat.app.data.local.MemoryEpisodeEntity
import com.gugachat.app.data.local.MemoryLedgerEntity
import com.gugachat.app.data.local.MessageEntity
import com.gugachat.app.data.local.NovelChapterEntity
import com.gugachat.app.data.local.NovelLoreEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.RpSaveSlotEntity
import com.gugachat.app.data.local.UserMemoryDao
import com.gugachat.app.data.local.UserMemoryEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.RpCgRecord
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.UserMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipInputStream

class BackupAndWebDavSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ==================== 1. SyncCryptoCodec Tests ====================

    @Test
    fun syncCryptoCodec_kdfAndVaultEnvelopeRoundtripAndRejectTamper() {
        val salt = SyncCryptoCodec.generateSalt()
        assertEquals(16, salt.size)

        val keys1 = SyncCryptoCodec.deriveKeys("correct-horse-battery-staple", salt)
        val keys2 = SyncCryptoCodec.deriveKeys("correct-horse-battery-staple", salt)
        assertArrayEquals(keys1.vaultKey, keys2.vaultKey)
        assertArrayEquals(keys1.streamKey, keys2.streamKey)
        assertFalse(keys1.vaultKey.contentEquals(keys1.streamKey))

        val plain = """{"apiKey":"sk-secret-value","webDavPassword":"app-password"}""".toByteArray(StandardCharsets.UTF_8)
        val cipher = SyncCryptoCodec.encryptVaultBytes("correct-horse-battery-staple", salt, plain)
        assertArrayEquals(plain, SyncCryptoCodec.decryptVaultBytes("correct-horse-battery-staple", salt, cipher))

        // Wrong passphrase must fail GCM authentication
        assertFailsWithSecurity("Wrong passphrase must fail vault decryption") {
            SyncCryptoCodec.decryptVaultBytes("wrong-passphrase", salt, cipher)
        }

        // Bit-flip in ciphertext must fail
        val flipped = cipher.copyOf()
        flipped[flipped.size - 1] = (flipped[flipped.size - 1].toInt() xor 0x01).toByte()
        assertFailsWithSecurity("Bit-flipped vault payload must fail GCM tag verification") {
            SyncCryptoCodec.decryptVaultBytes("correct-horse-battery-staple", salt, flipped)
        }

        // Truncated payload must fail
        assertFailsWithSecurity("Truncated vault payload must fail") {
            SyncCryptoCodec.decryptVaultBytes("correct-horse-battery-staple", salt, cipher.copyOf(16))
        }
    }

    @Test
    fun syncCryptoCodec_chunked64KbStreamRoundtripAndRejectsTruncationTamperingAndReordering() {
        val salt = SyncCryptoCodec.generateSalt()
        val pass = "gugachat-v04-sync-pass"

        // Test 0-byte, exact 64KB, 64KB+1, and ~210KB multi-chunk streams
        val sizes = listOf(0, 64 * 1024, 64 * 1024 + 1, 210 * 1024 + 37)
        for (sz in sizes) {
            val plain = ByteArray(sz) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }
            val encOut = ByteArrayOutputStream()
            SyncCryptoCodec.encryptStream(ByteArrayInputStream(plain), encOut, pass, salt)
            val cipherBytes = encOut.toByteArray()

            // Verify magic header starts with "GGENC01"
            assertEquals("GGENC01", String(cipherBytes, 0, 7, StandardCharsets.US_ASCII))

            val decOut = ByteArrayOutputStream()
            SyncCryptoCodec.decryptStream(ByteArrayInputStream(cipherBytes), decOut, pass, salt)
            assertArrayEquals("Roundtrip failed for size=$sz", plain, decOut.toByteArray())

            // Wrong passphrase fails
            assertFailsWithSecurity("Wrong passphrase must fail for size=$sz") {
                SyncCryptoCodec.decryptStream(ByteArrayInputStream(cipherBytes), ByteArrayOutputStream(), "bad-pass", salt)
            }
        }

        // Multi-chunk tampering / truncation / reordering checks
        val largePlain = ByteArray(200 * 1024) { idx -> ((idx * 17 + 3) and 0xFF).toByte() }
        val encStream = ByteArrayOutputStream()
        SyncCryptoCodec.encryptStream(ByteArrayInputStream(largePlain), encStream, pass, salt)
        val encrypted = encStream.toByteArray()

        // 1. Truncated right after 19-byte header
        assertFailsWithSecurity("Truncated after header must fail") {
            SyncCryptoCodec.decryptStream(ByteArrayInputStream(encrypted.copyOf(19)), ByteArrayOutputStream(), pass, salt)
        }

        // 2. Truncated cleanly at chunk boundary (after chunk 0, before chunk 1)
        val chunk0HeaderOffset = 19
        val chunk0CipherLen = readInt32BE(encrypted, chunk0HeaderOffset + 1)
        val chunk0End = chunk0HeaderOffset + 5 + chunk0CipherLen
        assertFailsWithSecurity("Truncated after chunk 0 must fail") {
            SyncCryptoCodec.decryptStream(ByteArrayInputStream(encrypted.copyOf(chunk0End)), ByteArrayOutputStream(), pass, salt)
        }

        // 3. Flipping isFinal bit from 0 -> 1 on chunk 0 (attacker trying to truncate stream silently!)
        val fakeFinalChunk0 = encrypted.copyOf(chunk0End)
        fakeFinalChunk0[chunk0HeaderOffset] = 1
        assertFailsWithSecurity("Forged isFinal=1 on chunk 0 must fail AAD authentication") {
            SyncCryptoCodec.decryptStream(ByteArrayInputStream(fakeFinalChunk0), ByteArrayOutputStream(), pass, salt)
        }

        // 4. Bit-flip in chunk 1 ciphertext
        val bitFlipped = encrypted.copyOf()
        bitFlipped[chunk0End + 10] = (bitFlipped[chunk0End + 10].toInt() xor 0x40).toByte()
        assertFailsWithSecurity("Bit-flipped chunk 1 must fail GCM authentication") {
            SyncCryptoCodec.decryptStream(ByteArrayInputStream(bitFlipped), ByteArrayOutputStream(), pass, salt)
        }

        // 5. Reordering / swapping chunk 0 and chunk 1
        val chunk1CipherLen = readInt32BE(encrypted, chunk0End + 1)
        val chunk1End = chunk0End + 5 + chunk1CipherLen
        val swapped = ByteArray(encrypted.size)
        System.arraycopy(encrypted, 0, swapped, 0, 19)
        System.arraycopy(encrypted, chunk0End, swapped, 19, chunk1End - chunk0End)
        System.arraycopy(encrypted, 19, swapped, 19 + (chunk1End - chunk0End), chunk0End - 19)
        System.arraycopy(encrypted, chunk1End, swapped, chunk1End, encrypted.size - chunk1End)
        assertFailsWithSecurity("Swapped chunks must fail GCM sequence authentication") {
            SyncCryptoCodec.decryptStream(ByteArrayInputStream(swapped), ByteArrayOutputStream(), pass, salt)
        }

        // 6. Trailing bytes appended after final chunk
        val withTrailing = encrypted + byteArrayOf(0x01, 0x02, 0x03)
        assertFailsWithSecurity("Trailing garbage after final chunk must fail") {
            SyncCryptoCodec.decryptStream(ByteArrayInputStream(withTrailing), ByteArrayOutputStream(), pass, salt)
        }
    }

    // ==================== 2. BackupArchiveEngine Tests ====================

    @Test
    fun backupArchiveEngine_full15TablesRoundtripMediaNormalizationAndSkipsEmbeddings() = runBlocking {
        val srcFilesDir = tempFolder.newFolder("src_files")
        val rpMediaDir = File(srcFilesDir, "rp_media").apply { mkdirs() }
        val attachDir = File(srcFilesDir, "chat_attachments").apply { mkdirs() }
        val directDir = File(srcFilesDir, "direct_images").apply { mkdirs() }

        val avatarBytes = "PNG_AVATAR_BYTES_0123456789".toByteArray()
        val spriteBytes = "PNG_SPRITE_BYTES_ABCDEFGHIJ".toByteArray()
        val sharedCgBytes = "SHARED_CG_AND_SCENE_BYTES".toByteArray()
        val attachBytes = "JPEG_ATTACHMENT_DATA".toByteArray()
        val attachTextBytes = "Extracted PDF text content".toByteArray()
        val directGenBytes = "DIRECT_GEN_IMAGE_BYTES".toByteArray()

        val avatarFile = File(rpMediaDir, "card_avatar.png").apply { writeBytes(avatarBytes) }
        val spriteFile = File(rpMediaDir, "card_sprite.png").apply { writeBytes(spriteBytes) }
        val sceneFile = File(rpMediaDir, "scene_bg.png").apply { writeBytes(sharedCgBytes) }
        val cgDupFile = File(rpMediaDir, "cg_copy.png").apply { writeBytes(sharedCgBytes) } // duplicate content for blob dedupe
        val coverFile = File(rpMediaDir, "novel_cover.jpg").apply { writeBytes("NOVEL_COVER".toByteArray()) }
        val attImgFile = File(attachDir, "att_1.jpg").apply { writeBytes(attachBytes) }
        val attTxtFile = File(attachDir, "att_2.txt").apply { writeBytes(attachTextBytes) }
        File(directDir, "gen_1_0.png").apply { writeBytes(directGenBytes) }

        val directGen = OnlineImageGenerationDto(
            id = "gen_1",
            provider = "openai",
            model = "gpt-image-1",
            prompt = "A quiet moonlit tavern",
            status = "completed",
            images = listOf(OnlineImageAssetDto(index = 0, bytes = directGenBytes.size.toLong())),
            created_at = 1000L,
            updated_at = 1000L,
        )
        File(directDir, "generations.json").writeText(
            AppJson.encodeToString(ListSerializer(OnlineImageGenerationDto.serializer()), listOf(directGen)),
            StandardCharsets.UTF_8,
        )
        File(directDir, "presets.json").writeText(
            AppJson.encodeToString(
                ListSerializer(OnlineImagePresetDto.serializer()),
                listOf(OnlineImagePresetDto(id = "p1", name = "Moonlit", provider = "openai", model = "gpt-image-1")),
            ),
            StandardCharsets.UTF_8,
        )

        val srcDb = FakeBackupDbGateway()
        // Populate all 15 tables + memory_embeddings (which must be skipped!)
        srcDb.assetDao.upsertWorldbook(WorldbookEntity(id = "wb-1", name = "World A", updatedAt = 100L))
        srcDb.assetDao.upsertWorldbookEntry(
            WorldbookEntryEntity(id = "wbe-1", worldbookId = "wb-1", uid = 1, title = "Lore", content = "Detail\nLine2", updatedAt = 100L),
        )
        srcDb.assetDao.upsertCharacterCard(
            CharacterCardEntity(
                id = "card-1",
                name = "Alice",
                avatarPath = avatarFile.absolutePath,
                spritePath = "/data/user/0/com.gugachat.app/files/rp_media/card_sprite.png",
                expressionSpritesJson = AppJson.encodeToString(
                    kotlinx.serialization.builtins.MapSerializer(
                        kotlinx.serialization.serializer<String>(),
                        kotlinx.serialization.serializer<String>(),
                    ),
                    mapOf("happy" to spriteFile.absolutePath, "remote" to "https://cdn.example.com/e.png"),
                ),
                castJson = AppJson.encodeToString(
                    ListSerializer(RpCharacter.serializer()),
                    listOf(RpCharacter(id = "npc1", name = "Bob", avatarPath = avatarFile.absolutePath)),
                ),
                primaryWorldbookId = "wb-1",
                updatedAt = 100L,
            ),
        )
        srcDb.assetDao.upsertPersona(
            PersonaEntity(
                id = "persona-1",
                name = "Traveler",
                avatarPath = "file://${avatarFile.absolutePath}",
                isDefault = true,
                updatedAt = 100L,
            ),
        )
        srcDb.assetDao.upsertPromptPreset(PromptPresetEntity(id = "preset-1", name = "RP Preset", isDefaultRp = true, updatedAt = 100L))
        srcDb.userMemoryDao.upsert(UserMemoryEntity("um-1", "preference", "Prefers dark mode", "[]", true, true, 50L, 100L, 6))

        val rpCfg = RpConfig(
            title = "Tavern",
            cast = listOf(RpCharacter(id = "c1", name = "Alice", avatarPath = avatarFile.absolutePath, spritePath = spriteFile.absolutePath)),
            state = RpGameState(
                sceneImagePath = sceneFile.absolutePath,
                unlockedCgs = listOf(RpCgRecord(id = "cg1", title = "CG 1", imagePath = cgDupFile.absolutePath)),
            ),
        )
        val novelCfg = NovelConfig(title = "Chronicle", coverImagePath = coverFile.absolutePath)
        srcDb.chatDao.upsertConversation(
            ChatConversation(
                id = "conv-1",
                title = "Adventure",
                mode = ConversationMode.rp,
                novel = novelCfg,
                rp = rpCfg,
                createdAt = 50L,
                updatedAt = 200L,
                characterCardId = "card-1",
                personaId = "persona-1",
                presetId = "preset-1",
            ).let {
                ConversationEntity(
                    id = it.id,
                    title = it.title,
                    mode = it.mode.name,
                    groupId = it.groupId,
                    model = it.model,
                    systemPrompt = it.systemPrompt,
                    paramsJson = AppJson.encodeToString(it.params),
                    novelJson = AppJson.encodeToString(novelCfg),
                    rpJson = AppJson.encodeToString(rpCfg),
                    archivedSummary = "",
                    summarizedUntilSeq = 0,
                    tokenCalibration = 1.0,
                    createdAt = 50L,
                    updatedAt = 200L,
                    pinned = false,
                    characterCardId = "card-1",
                    personaId = "persona-1",
                    presetId = "preset-1",
                )
            },
        )
        srcDb.assetDao.upsertConversationWorldbooks(listOf(ConversationWorldbookCrossRef("conv-1", "wb-1", 0, true, 100L)))
        srcDb.chatDao.upsertMessages(
            listOf(
                MessageEntity(
                    id = "m-1", conversationId = "conv-1", role = "user", content = "Hello", reasoning = "",
                    model = null, createdAt = 10L, seq = 1, attachmentIdsJson = """["att-1"]""",
                    inputKind = null, rpInputKind = null, rpDiceRollJson = null,
                    rpChoicesJson = "[]", novelChoicesJson = "[]", status = "done", error = null,
                    usageJson = null, tokenEstimate = 2, variantsJson = "[]", activeVariantIndex = 0,
                    editedAt = null, supersededAtSeq = null, deletedAt = null,
                ),
                MessageEntity(
                    id = "m-2", conversationId = "conv-1", role = "assistant", content = "Partial...", reasoning = "",
                    model = "gpt-4o", createdAt = 20L, seq = 2, attachmentIdsJson = "[]",
                    inputKind = null, rpInputKind = null, rpDiceRollJson = null,
                    rpChoicesJson = "[]", novelChoicesJson = "[]",
                    status = "streaming", // MUST normalize to "aborted" on export!
                    error = null, usageJson = null, tokenEstimate = 5,
                    variantsJson = AppJson.encodeToString(
                        ListSerializer(MessageVariant.serializer()),
                        listOf(
                            MessageVariant(
                                id = "v1",
                                content = "Partial...",
                                createdAt = 20L,
                                rpStateSnapshot = RpGameState(sceneImagePath = sceneFile.absolutePath),
                            ),
                        ),
                    ),
                    activeVariantIndex = 0, editedAt = null, supersededAtSeq = null, deletedAt = null,
                ),
            ),
        )
        srcDb.chatDao.upsertNovelChapter(NovelChapterEntity("ch-1", "conv-1", 1, "Chapter 1", "", "", "writing", 120, 1, 2, 10L, 20L))
        srcDb.chatDao.upsertNovelLore(NovelLoreEntity("nl-1", "conv-1", "item", "Key", "Golden key", "[]", "[]", false, 50, true, 0, null, null, false, 10L, 20L, 4))
        srcDb.chatDao.upsertMemoryLedger(MemoryLedgerEntity("ml-1", "conv-1", "fact", "Gate", "Locked", "[]", "[]", null, null, false, false, true, false, false, 0, null, null, null, "ai", 10L, 20L, 4))
        srcDb.chatDao.upsertMemoryEpisode(MemoryEpisodeEntity("me-1", "conv-1", 1, "Act 1", "Summary", "[]", 1, 2, false, false, 10L, 20L, 8))
        srcDb.chatDao.upsertRpSaveSlot(
            RpSaveSlotEntity(
                id = "slot-1", conversationId = "conv-1", name = "Slot 1", note = "", createdAt = 30L, messageSeq = 2,
                rpConfigJson = AppJson.encodeToString(rpCfg),
                ledgerItemsJson = "[]", episodesJson = "[]", activeMessagesSnapshotJson = "[]",
            ),
        )
        srcDb.attachmentDao.upsert(
            AttachmentMetaEntity(
                id = "att-1", mimeType = "image/jpeg", width = 100, height = 100, sizeBytes = attachBytes.size.toLong(),
                localPath = attImgFile.absolutePath, createdAt = 10L, kind = "image", fileName = "att_1.jpg",
            ),
        )
        srcDb.attachmentDao.upsert(
            AttachmentMetaEntity(
                id = "att-2", mimeType = "application/pdf", width = 0, height = 0, sizeBytes = attachTextBytes.size.toLong(),
                localPath = attTxtFile.absolutePath, createdAt = 11L, kind = "file", fileName = "doc.pdf", textPath = attTxtFile.absolutePath,
            ),
        )
        // Add memory embeddings that MUST NOT be exported
        srcDb.memoryEmbeddingDao?.upsertAll(
            listOf(
                MemoryEmbeddingEntity(
                    ownerType = "user_memory", ownerId = "um-1", conversationId = "", profileKey = "local",
                    contentHash = "h1", dim = 4, quantType = "int8_unit", scale = 1f, vectorBlob = byteArrayOf(1, 2, 3, 4), updatedAt = 100L,
                ),
            ),
        )

        val srcVault = InMemoryVaultAdapter(
            VaultPayloadV2(
                apiProviders = listOf(
                    ApiProviderConfig(id = "prov-1", numericId = 1L, name = "OpenAI", protocol = ApiProtocol.OPENAI_CHAT, baseUrl = "https://api.openai.com/v1", apiKey = "sk-super-secret-123"),
                ),
                secrets = mapOf("remoteEmbeddingApiKey" to "sk-embed-secret"),
            ),
        )

        // A) Export passphrase-free .gugubackup (secrets stripped, direct_images excluded by default)
        val noPassBackup = File(tempFolder.root, "nopass.gugubackup")
        val noPassManifest = BackupArchiveEngine.exportBackupToFile(
            gateway = srcDb,
            filesDir = srcFilesDir,
            outputFile = noPassBackup,
            vaultAdapter = srcVault,
            options = BackupExportOptions(passphrase = null, includeVault = true, includeDirectImages = false),
        )
        assertEquals(2, noPassManifest.formatVersion)
        assertEquals(4, noPassManifest.dbSchemaVersion)

        // Inspect ZIP entries directly
        val zipContents = readZipEntriesAsMap(noPassBackup)
        assertTrue(zipContents.containsKey("manifest.json"))
        assertTrue(zipContents.containsKey("vault.json"))
        assertFalse(zipContents.containsKey("vault.enc"))
        for (tableFile in BackupArchiveEngine.TABLE_FILES_IN_ORDER) {
            assertTrue("Missing tables/$tableFile", zipContents.containsKey("tables/$tableFile"))
        }
        assertFalse("memory_embeddings MUST be skipped!", zipContents.keys.any { it.contains("memory_embeddings") })
        assertFalse("direct_images_meta.json should not be exported when includeDirectImages=false", zipContents.containsKey("tables/direct_images_meta.json"))
        assertFalse("direct_images blob should not be exported when includeDirectImages=false", noPassManifest.mediaIndex.any { it.relPath.startsWith("direct_images/") })

        // Verify vault.json stripped apiKey and secrets
        val exportedStrippedVault = AppJson.decodeFromString(
            VaultPayloadV2.serializer(),
            zipContents["vault.json"]!!.toString(StandardCharsets.UTF_8),
        )
        assertEquals("", exportedStrippedVault.apiProviders.single().apiKey)
        assertTrue(exportedStrippedVault.secrets.isEmpty())

        // Verify relative path normalization & streaming->aborted inside archived NDJSON
        val cardNdjson = zipContents["tables/character_cards.ndjson"]!!.toString(StandardCharsets.UTF_8)
        assertFalse("Absolute srcFilesDir leaked into NDJSON!", cardNdjson.contains(srcFilesDir.absolutePath))
        assertFalse("Legacy /data/user/0 prefix leaked into NDJSON!", cardNdjson.contains("/data/user/0/"))
        assertTrue(cardNdjson.contains("rp_media/card_avatar.png"))
        assertTrue(cardNdjson.contains("rp_media/card_sprite.png"))
        assertTrue(cardNdjson.contains("https://cdn.example.com/e.png"))

        val msgNdjson = zipContents["tables/messages.ndjson"]!!.toString(StandardCharsets.UTF_8)
        assertFalse("streaming status must be normalized to aborted", msgNdjson.contains("\"status\":\"streaming\""))
        assertTrue(msgNdjson.contains("\"status\":\"aborted\""))

        // Verify blob deduplication: scene_bg.png & cg_copy.png share 1 blob in blobs/<sha256>
        val sharedSha = SyncCryptoCodec.sha256Hex(sharedCgBytes)
        assertEquals(2, noPassManifest.mediaIndex.count { it.sha256 == sharedSha })
        assertEquals(1, zipContents.keys.count { it == "blobs/$sharedSha" })

        // B) Export WITH passphrase and includeDirectImages=true, then restore to fresh DB & dir
        val encryptedBackup = File(tempFolder.root, "encrypted.gugubackup")
        val encManifest = BackupArchiveEngine.exportBackupToFile(
            gateway = srcDb,
            filesDir = srcFilesDir,
            outputFile = encryptedBackup,
            vaultAdapter = srcVault,
            options = BackupExportOptions(passphrase = "backup-secret-99", includeVault = true, includeDirectImages = true),
        )
        assertTrue(encManifest.mediaIndex.any { it.relPath == "direct_images/gen_1_0.png" })

        val destDb = FakeBackupDbGateway()
        val destFilesDir = tempFolder.newFolder("dest_files")
        val destVault = InMemoryVaultAdapter()

        // Missing passphrase must abort BEFORE mutating DB or writing files
        try {
            BackupArchiveEngine.restoreFromFile(
                gateway = destDb,
                filesDir = destFilesDir,
                inputFile = encryptedBackup,
                replaceExisting = true,
                passphrase = null,
                vaultAdapter = destVault,
            )
            fail("Expected BackupPassphraseRequiredException")
        } catch (_: BackupPassphraseRequiredException) {
            assertTrue(destDb.chatDao.getAllConversations().isEmpty())
            assertFalse(File(destFilesDir, "rp_media/card_avatar.png").exists())
        }

        // Valid passphrase restores all 15 tables + media + direct images + vault secrets
        val restoreRes = BackupArchiveEngine.restoreFromFile(
            gateway = destDb,
            filesDir = destFilesDir,
            inputFile = encryptedBackup,
            replaceExisting = true,
            passphrase = "backup-secret-99",
            vaultAdapter = destVault,
            includeDirectImages = true,
        )
        assertEquals(2, restoreRes.formatVersion)
        assertEquals(1, restoreRes.conversationsCount)
        assertEquals(2, restoreRes.messagesCount)
        assertEquals(1, restoreRes.worldbooksCount)
        assertEquals(1, restoreRes.characterCardsCount)
        assertEquals(1, restoreRes.personasCount)
        assertEquals(1, restoreRes.promptPresetsCount)
        assertEquals(1, restoreRes.userMemoriesCount)
        assertTrue(restoreRes.vaultRestored)
        assertEquals(1, restoreRes.directImagesRestored)

        // Verify restored files exist in destFilesDir and match byte-for-byte
        assertArrayEquals(avatarBytes, File(destFilesDir, "rp_media/card_avatar.png").readBytes())
        assertArrayEquals(spriteBytes, File(destFilesDir, "rp_media/card_sprite.png").readBytes())
        assertArrayEquals(sharedCgBytes, File(destFilesDir, "rp_media/scene_bg.png").readBytes())
        assertArrayEquals(sharedCgBytes, File(destFilesDir, "rp_media/cg_copy.png").readBytes())
        assertArrayEquals(attachBytes, File(destFilesDir, "chat_attachments/att_1.jpg").readBytes())
        assertArrayEquals(attachTextBytes, File(destFilesDir, "chat_attachments/att_2.txt").readBytes())
        assertArrayEquals(directGenBytes, File(destFilesDir, "direct_images/gen_1_0.png").readBytes())

        // Verify DB records point to destFilesDir and normalized status
        val restoredCard = destDb.assetDao.getCharacterCardById("card-1")!!
        assertEquals(File(destFilesDir, "rp_media/card_avatar.png").path, restoredCard.avatarPath)
        assertEquals(File(destFilesDir, "rp_media/card_sprite.png").path, restoredCard.spritePath)
        val restoredMsgs = destDb.chatDao.getMessagesForConversation("conv-1")
        assertEquals(2, restoredMsgs.size)
        assertEquals("aborted", restoredMsgs.first { it.id == "m-2" }.status)
        assertEquals(1, destDb.chatDao.getAllNovelChapters().size)
        assertEquals(1, destDb.chatDao.getAllNovelLore().size)
        assertEquals(1, destDb.chatDao.getAllMemoryLedger().size)
        assertEquals(1, destDb.chatDao.getAllMemoryEpisodes().size)
        assertEquals(1, destDb.chatDao.getAllRpSaveSlots().size)
        assertEquals(1, destDb.assetDao.getMountedWorldbookRefs("conv-1").size)
        assertEquals("sk-super-secret-123", destVault.appliedPayload!!.apiProviders.single().apiKey)
        assertEquals("sk-embed-secret", destVault.appliedPayload!!.secrets["remoteEmbeddingApiKey"])
    }

    @Test
    fun backupArchiveEngine_wholeConversationAtomicMergeKeepsDeletesAndProtectsNewerLocal() = runBlocking {
        // Remote device has:
        // - conv-1 (updatedAt=2000) where m2 was deleted, leaving only [m1, m3]
        // - conv-2 (updatedAt=3000) stale version with [m10]
        // - conv-3 (updatedAt=1500) brand new with [m20]
        val remoteDb = FakeBackupDbGateway()
        val remoteFiles = tempFolder.newFolder("merge_remote_files")
        remoteDb.assetDao.upsertWorldbook(WorldbookEntity(id = "wb-1", name = "Remote Newer WB", updatedAt = 2000L))
        remoteDb.assetDao.upsertWorldbookEntry(WorldbookEntryEntity(id = "e-new", worldbookId = "wb-1", title = "New Entry", updatedAt = 2000L))

        remoteDb.chatDao.upsertConversations(
            listOf(
                testConvEntity("conv-1", "Conv 1 New", updatedAt = 2000L),
                testConvEntity("conv-2", "Conv 2 Stale", updatedAt = 3000L),
                testConvEntity("conv-3", "Conv 3 Brand New", updatedAt = 1500L),
            ),
        )
        remoteDb.chatDao.upsertMessages(
            listOf(
                testMsgEntity("m1", "conv-1", seq = 1, content = "m1"),
                testMsgEntity("m3", "conv-1", seq = 3, content = "m3-updated"),
                testMsgEntity("m10", "conv-2", seq = 1, content = "stale"),
                testMsgEntity("m20", "conv-3", seq = 1, content = "brand-new"),
            ),
        )
        remoteDb.chatDao.upsertNovelChapters(listOf(NovelChapterEntity("ch1", "conv-1", 1, "Ch 1", "", "", "done", 100, 1, 3, 10L, 2000L)))
        remoteDb.assetDao.upsertConversationWorldbooks(listOf(ConversationWorldbookCrossRef("conv-1", "wb-1", 0, true, 2000L)))

        val archiveFile = File(tempFolder.root, "merge.gugubackup")
        BackupArchiveEngine.exportBackupToFile(remoteDb, remoteFiles, archiveFile)

        // Local device has:
        // - conv-1 (updatedAt=1000) with deleted m2, extra ch2, extra ledger, extra wb-old crossref
        // - conv-2 (updatedAt=5000) newer local with [m10, m11]
        val localDb = FakeBackupDbGateway()
        val localFiles = tempFolder.newFolder("merge_local_files")
        localDb.assetDao.upsertWorldbook(WorldbookEntity(id = "wb-1", name = "Local Older WB", updatedAt = 1000L))
        localDb.assetDao.upsertWorldbookEntry(WorldbookEntryEntity(id = "e-deleted-in-wb", worldbookId = "wb-1", title = "Old Entry", updatedAt = 1000L))

        localDb.chatDao.upsertConversations(
            listOf(
                testConvEntity("conv-1", "Conv 1 Old", updatedAt = 1000L),
                testConvEntity("conv-2", "Conv 2 Newer Local", updatedAt = 5000L),
            ),
        )
        localDb.chatDao.upsertMessages(
            listOf(
                testMsgEntity("m1", "conv-1", seq = 1, content = "m1"),
                testMsgEntity("m2", "conv-1", seq = 2, content = "SHOULD_STAY_DELETED"),
                testMsgEntity("m3", "conv-1", seq = 3, content = "m3-old"),
                testMsgEntity("m10", "conv-2", seq = 1, content = "local-10"),
                testMsgEntity("m11", "conv-2", seq = 2, content = "local-11"),
            ),
        )
        localDb.chatDao.upsertNovelChapters(
            listOf(
                NovelChapterEntity("ch1", "conv-1", 1, "Ch 1 old", "", "", "writing", 10, 1, null, 10L, 1000L),
                NovelChapterEntity("ch2", "conv-1", 2, "Ch 2 deleted", "", "", "draft", 0, 2, null, 10L, 1000L),
            ),
        )
        localDb.chatDao.upsertMemoryLedger(
            MemoryLedgerEntity("led-deleted", "conv-1", "fact", "Old", "Old", "[]", "[]", null, null, false, false, true, false, false, 0, null, null, null, "ai", 10L, 1000L, 2),
        )
        localDb.assetDao.upsertConversationWorldbooks(
            listOf(
                ConversationWorldbookCrossRef("conv-1", "wb-1", 0, true, 1000L),
                ConversationWorldbookCrossRef("conv-1", "wb-old", 1, true, 1000L),
            ),
        )

        BackupArchiveEngine.restoreFromFile(
            gateway = localDb,
            filesDir = localFiles,
            inputFile = archiveFile,
            replaceExisting = false,
        )

        // conv-1 was overwritten atomically: m2, ch2, led-deleted, wb-old are ALL GONE!
        assertEquals("Conv 1 New", localDb.chatDao.getConversationById("conv-1")!!.title)
        val c1Msgs = localDb.chatDao.getMessagesForConversation("conv-1")
        assertEquals(listOf("m1", "m3"), c1Msgs.map { it.id })
        assertEquals("m3-updated", c1Msgs.last().content)
        assertEquals(listOf("ch1"), localDb.chatDao.getNovelChaptersForConversation("conv-1").map { it.id })
        assertTrue(localDb.chatDao.getMemoryLedgerForConversation("conv-1").isEmpty())
        assertEquals(listOf("wb-1"), localDb.assetDao.getMountedWorldbookRefs("conv-1").map { it.worldbookId })

        // conv-2 was newer locally -> untouched!
        assertEquals("Conv 2 Newer Local", localDb.chatDao.getConversationById("conv-2")!!.title)
        assertEquals(listOf("m10", "m11"), localDb.chatDao.getMessagesForConversation("conv-2").map { it.id })

        // conv-3 was added cleanly
        assertEquals("Conv 3 Brand New", localDb.chatDao.getConversationById("conv-3")!!.title)
        assertEquals(listOf("m20"), localDb.chatDao.getMessagesForConversation("conv-3").map { it.id })

        // Worldbook wb-1 won LWW and replaced its entries atomically
        assertEquals("Remote Newer WB", localDb.assetDao.getWorldbookById("wb-1")!!.name)
        assertEquals(listOf("e-new"), localDb.assetDao.getWorldbookEntries("wb-1").map { it.id })
    }

    @Test
    fun backupArchiveEngine_detectsMagicBytesAndDelegatesLegacyV1Json() = runBlocking {
        assertEquals(
            DetectedBackupFormat.ARCHIVE_V2_ZIP,
            BackupArchiveEngine.detectBackupFormat(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)),
        )
        assertEquals(
            DetectedBackupFormat.LEGACY_V1_JSON,
            BackupArchiveEngine.detectBackupFormat("\uFEFF  \n  {\"version\":1}".toByteArray(StandardCharsets.UTF_8)),
        )
        assertEquals(
            DetectedBackupFormat.UNKNOWN,
            BackupArchiveEngine.detectBackupFormat("not-a-backup".toByteArray(StandardCharsets.UTF_8)),
        )

        val v1Payload = GuGuChatBackupPayload(
            version = 1,
            exportedAt = 12345L,
            conversations = listOf(ChatConversation(id = "v1-conv", title = "Legacy Chat", createdAt = 1L, updatedAt = 2L)),
            messages = listOf(ChatMessage(id = "v1-msg", conversationId = "v1-conv", role = "user", content = "Hi v1", createdAt = 1L, seq = 1)),
            userMemories = listOf(UserMemory(id = "v1-um", category = MemoryCategory.rule, content = "Be brief", createdAt = 1L, updatedAt = 2L)),
        )
        val v1JsonWithBom = "\uFEFF" + AppJson.encodeToString(GuGuChatBackupPayload.serializer(), v1Payload)

        val db = FakeBackupDbGateway()
        val filesDir = tempFolder.newFolder("v1_restore_files")
        val res = BackupArchiveEngine.restoreFromStream(
            gateway = db,
            filesDir = filesDir,
            input = ByteArrayInputStream(v1JsonWithBom.toByteArray(StandardCharsets.UTF_8)),
            replaceExisting = false,
        )
        assertEquals(1, res.formatVersion)
        assertEquals(1, res.conversationsCount)
        assertEquals(1, res.messagesCount)
        assertEquals(1, res.userMemoriesCount)
        assertEquals("Legacy Chat", db.chatDao.getConversationById("v1-conv")?.title)
        assertEquals("Hi v1", db.chatDao.getMessagesForConversation("v1-conv").single().content)
        assertEquals("Be brief", db.userMemoryDao.getAll().single().content)
    }

    @Test
    fun backupArchiveEngine_streamsAcrossMultiple200RowPagesAndRejectsTamperedTables() = runBlocking {
        val srcDb = FakeBackupDbGateway()
        val srcFiles = tempFolder.newFolder("paged_src_files")
        srcDb.chatDao.upsertConversation(testConvEntity("conv-paged", "Paged Conv", updatedAt = 100L))
        // 450 messages -> 3 pages (200 + 200 + 50)
        val manyMsgs = (1..450).map { i ->
            testMsgEntity(
                id = "pm-${i.toString().padStart(4, '0')}",
                convId = "conv-paged",
                seq = i,
                content = "Line $i\nSecond line",
            )
        }
        srcDb.chatDao.upsertMessages(manyMsgs)

        val stageDir = tempFolder.newFolder("paged_stage")
        val staged = BackupArchiveEngine.prepareStageDirectory(stageDir, srcDb, srcFiles)
        assertEquals(450, File(staged.tablesDir, "messages.ndjson").readLines().size)

        // Restore cleanly to fresh DB
        val destDb = FakeBackupDbGateway()
        val destFiles = tempFolder.newFolder("paged_dest_files")
        val res = BackupArchiveEngine.applyStageDirectory(staged.stageDir, destDb, destFiles, replaceExisting = true)
        assertEquals(450, res.messagesCount)
        assertEquals(450, destDb.chatDao.getMessagesForConversation("conv-paged").size)

        // Tamper a line inside staged messages.ndjson -> SHA-256 verification must abort BEFORE touching destDb!
        File(staged.tablesDir, "messages.ndjson").appendText("{\"id\":\"tampered\"}\n", StandardCharsets.UTF_8)
        try {
            BackupArchiveEngine.applyStageDirectory(staged.stageDir, destDb, destFiles, replaceExisting = true)
            fail("Expected SecurityException on tampered tables/*.ndjson")
        } catch (_: SecurityException) {
            // destDb must still retain original 450 messages (not wiped by replaceExisting = true!)
            assertEquals(450, destDb.chatDao.getMessagesForConversation("conv-paged").size)
        }
    }

    // ==================== 3. WebDavClient & WebDavSyncManager Tests ====================

    @Test
    fun webDavClient_enforcesDepth0Or1_handles405Mkcol_fixedLengthPut_basicAuth_xml207_andRetries429_503() = runBlocking {
        val creds = WebDavCredentials("alice", "app-secret-pw")
        val client = WebDavClient(maxConcurrency = 2, maxRetries = 3, retryBaseDelayMs = 5L)

        // Depth: infinity or invalid depth must be rejected before any HTTP call
        for (badDepth in listOf(-1, 2, Int.MAX_VALUE)) {
            try {
                client.propfind(server.url("/dav/").toString(), badDepth, creds)
                fail("Expected IllegalArgumentException for depth=$badDepth")
            } catch (_: IllegalArgumentException) {
            }
        }

        val recordedHeaders = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        var putAttempt = 0

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val headers = LinkedHashMap<String, String>()
                for (name in request.headers.names()) {
                    headers[name.lowercase()] = request.headers[name].orEmpty()
                }
                recordedHeaders.add("${request.method} ${request.path}" to headers)

                return when {
                    // Jianguoyun / Nextcloud mixed XML 207 parsing test
                    request.method == "PROPFIND" && request.path == "/dav/GuGuChatSync/" -> {
                        MockResponse().setResponseCode(207).setBody(
                            """
                            <?xml version="1.0" encoding="utf-8"?>
                            <D:multistatus xmlns:D="DAV:">
                              <D:response>
                                <D:href>/dav/GuGuChatSync/</D:href>
                                <D:propstat>
                                  <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                                  <D:status>HTTP/1.1 200 OK</D:status>
                                </D:propstat>
                              </D:response>
                              <D:response>
                                <D:href>/dav/GuGuChatSync/blobs/</D:href>
                                <D:propstat>
                                  <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                                  <D:status>HTTP/1.1 200 OK</D:status>
                                </D:propstat>
                              </D:response>
                              <D:response>
                                <D:href>/dav/GuGuChatSync/%E6%B5%8B%E8%AF%95.enc</D:href>
                                <D:propstat>
                                  <D:prop>
                                    <D:resourcetype/>
                                    <D:getcontentlength>42</D:getcontentlength>
                                  </D:prop>
                                  <D:status>HTTP/1.1 200 OK</D:status>
                                </D:propstat>
                                <D:propstat>
                                  <D:prop><D:getetag/></D:prop>
                                  <D:status>HTTP/1.1 404 Not Found</D:status>
                                </D:propstat>
                              </D:response>
                            </D:multistatus>
                            """.trimIndent(),
                        )
                    }
                    // Step-by-step hierarchy: /dav/a/ returns 404 -> MKCOL returns 405 (Nextcloud existing dir)
                    request.method == "PROPFIND" && request.path == "/dav/a/" -> MockResponse().setResponseCode(404)
                    request.method == "MKCOL" && request.path == "/dav/a/" -> MockResponse().setResponseCode(405)
                    // /dav/a/b/ returns 404 -> MKCOL returns 201 Created
                    request.method == "PROPFIND" && request.path == "/dav/a/b/" -> MockResponse().setResponseCode(404)
                    request.method == "MKCOL" && request.path == "/dav/a/b/" -> MockResponse().setResponseCode(201)
                    // PUT retries 429 -> 503 -> 201
                    request.method == "PUT" && request.path == "/dav/a/b/item.bin" -> {
                        putAttempt += 1
                        when (putAttempt) {
                            1 -> MockResponse().setResponseCode(429).addHeader("Retry-After", "0")
                            2 -> MockResponse().setResponseCode(503)
                            else -> MockResponse().setResponseCode(201)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        // Verify pure-JVM XML 207 parsing + self exclusion in listDirectoryIfExists
        val listed = client.listDirectoryIfExists(server.url("/dav/GuGuChatSync/").toString(), creds)
        assertEquals(2, listed.size)
        assertEquals("blobs", listed[0].displayName)
        assertTrue(listed[0].isCollection)
        assertEquals("测试.enc", listed[1].displayName)
        assertFalse(listed[1].isCollection)
        assertEquals(42L, listed[1].contentLength)

        // Verify ensureDirectoryHierarchy handles both 405 and 201
        val createdDir = client.ensureDirectoryHierarchy(server.url("/dav").toString(), "a/b", creds)
        assertTrue(createdDir.endsWith("/dav/a/b/"))

        // Verify fixed-length PUT + preemptive Basic Auth + retry on 429 & 503
        val payloadFile = File(tempFolder.root, "upload_item.bin").apply { writeBytes("FIXED_LEN_PAYLOAD_1234".toByteArray()) }
        client.putFileFixedLength(
            url = server.url("/dav/a/b/item.bin").toString(),
            file = payloadFile,
            contentType = "application/octet-stream",
            creds = creds,
        )
        assertEquals(3, putAttempt)

        // Audit every recorded request
        val expectedAuth = creds.basicAuthHeader()
        for ((reqLine, hdrs) in recordedHeaders) {
            assertEquals("Preemptive Basic Auth missing on $reqLine", expectedAuth, hdrs["authorization"])
            if (reqLine.startsWith("PROPFIND ")) {
                val depth = hdrs["depth"]
                assertTrue("Invalid PROPFIND Depth '$depth' on $reqLine", depth == "0" || depth == "1")
            }
            if (reqLine.startsWith("PUT ")) {
                assertEquals(payloadFile.length().toString(), hdrs["content-length"])
                assertFalse("Chunked transfer must NOT be used on PUT!", hdrs["transfer-encoding"]?.contains("chunked") == true)
            }
        }
    }

    @Test
    fun webDavSyncManager_incrementalBlobDiffRolling5SnapshotsAndMultiDeviceSync() = runBlocking {
        val davServer = InMemoryWebDavDispatcher()
        server.dispatcher = davServer
        val davRootUrl = server.url("/dav/").toString()

        val client = WebDavClient(maxConcurrency = 2, maxRetries = 2, retryBaseDelayMs = 5L)

        // Device A setup
        val devADb = FakeBackupDbGateway()
        val devAFiles = tempFolder.newFolder("devA_files")
        val devAMedia = File(devAFiles, "rp_media").apply { mkdirs() }
        val devADirect = File(devAFiles, "direct_images").apply { mkdirs() }
        val blob1File = File(devAMedia, "hero_a.png").apply { writeBytes("HERO_A_IMAGE_CONTENT".toByteArray()) }
        val blob2File = File(devAMedia, "scene_a.png").apply { writeBytes("SCENE_A_IMAGE_CONTENT".toByteArray()) }
        // Direct image present on disk, but syncDirectImages defaults to false!
        File(devADirect, "gen_ignored_0.png").apply { writeBytes("DIRECT_IMG_IGNORED".toByteArray()) }
        File(devADirect, "generations.json").writeText(
            AppJson.encodeToString(
                ListSerializer(OnlineImageGenerationDto.serializer()),
                listOf(
                    OnlineImageGenerationDto(
                        id = "gen_ignored",
                        model = "gpt-image-1",
                        prompt = "ignored",
                        status = "completed",
                        images = listOf(OnlineImageAssetDto(index = 0)),
                    ),
                ),
            ),
            StandardCharsets.UTF_8,
        )

        devADb.assetDao.upsertCharacterCard(
            CharacterCardEntity(id = "card-a", name = "Hero A", avatarPath = blob1File.absolutePath, spritePath = blob2File.absolutePath, updatedAt = 100L),
        )
        devADb.chatDao.upsertConversation(testConvEntity("conv-a", "Dev A Story", updatedAt = 1000L))
        devADb.chatDao.upsertMessages(listOf(testMsgEntity("ma-1", "conv-a", seq = 1, content = "Hello from A")))
        val devAVault = InMemoryVaultAdapter(
            VaultPayloadV2(
                apiProviders = listOf(ApiProviderConfig(id = "p-a", numericId = 1L, name = "Claude", protocol = ApiProtocol.ANTHROPIC, apiKey = "sk-ant-key")),
                secrets = mapOf("webDavSyncPassphrase" to "e2ee-phrase"),
            ),
        )

        val mgrA = WebDavSyncManager(client, devADb, devAFiles, devAVault)
        val configA = WebDavSyncConfig(url = davRootUrl, username = "u1", syncDirectImages = false)

        // 1. Test connection
        val conn = mgrA.testConnection(configA, "dav-pw")
        assertFalse(conn.hasRemoteManifest)

        // 2. Push #1 -> uploads 2 blobs, vault, snapshot #1, manifest last; direct_images skipped!
        val push1 = mgrA.backupToWebDav(configA, "dav-pw", "e2ee-phrase", nowMs = 1001L)
        assertEquals(2, push1.uploadedBlobs)
        assertEquals(0, push1.skippedExistingBlobs)
        assertEquals(0, push1.prunedSnapshots)
        assertFalse(push1.manifest.includesDirectImages)
        assertTrue(davServer.files.containsKey("/dav/GuGuChatSync/manifest.json"))
        assertTrue(davServer.files.containsKey("/dav/GuGuChatSync/vault/providers_settings.enc"))
        assertFalse(davServer.files.keys.any { it.contains(SyncCryptoCodec.sha256Hex("DIRECT_IMG_IGNORED".toByteArray())) })

        // Verify manifest.json was written LAST in push #1
        val manifestPutIdx = davServer.putOrder.indexOfLast { it == "/dav/GuGuChatSync/manifest.json" }
        val snapPutIdx = davServer.putOrder.indexOfLast { it.startsWith("/dav/GuGuChatSync/snapshots/") }
        val lastBlobPutIdx = davServer.putOrder.indexOfLast { it.startsWith("/dav/GuGuChatSync/blobs/") }
        assertTrue("manifest.json must be committed last", manifestPutIdx > snapPutIdx && manifestPutIdx > lastBlobPutIdx)

        // 3. Push #2 after adding 1 new blob -> PROPFIND Depth:1 skips the 2 existing blobs and uploads ONLY 1 new blob!
        val blob3File = File(devAMedia, "villain_a.png").apply { writeBytes("VILLAIN_A_IMAGE_CONTENT".toByteArray()) }
        devADb.assetDao.upsertPersona(PersonaEntity(id = "per-a", name = "Villain", avatarPath = blob3File.absolutePath, updatedAt = 200L))
        davServer.putCalls.clear()
        val push2 = mgrA.backupToWebDav(configA, "dav-pw", "e2ee-phrase", nowMs = 2000L)
        assertEquals(1, push2.uploadedBlobs)
        assertEquals(2, push2.skippedExistingBlobs)
        assertEquals(1, davServer.putCalls.count { it.startsWith("/dav/GuGuChatSync/blobs/") })

        // 4. Push #3..#7 -> rolling 5 snapshots cleanup deletes older snapshots!
        for (i in 3..7) {
            mgrA.backupToWebDav(configA, "dav-pw", "e2ee-phrase", nowMs = i * 1000L)
        }
        val remainingSnaps = davServer.files.keys.filter { it.startsWith("/dav/GuGuChatSync/snapshots/db_") }
        assertEquals(5, remainingSnaps.size)
        assertFalse(remainingSnaps.any { it.contains("db_1001_") })
        assertFalse(remainingSnaps.any { it.contains("db_2000_") })
        assertTrue(remainingSnaps.any { it.contains("db_7000_") })

        // Verify ALL PROPFIND headers across the entire test strictly used Depth 0 or 1
        assertTrue(davServer.observedPropfindDepths.isNotEmpty())
        assertTrue(davServer.observedPropfindDepths.all { it == "0" || it == "1" })

        // 5. Device B restore with wrong passphrase -> WebDavPassphraseMismatchException
        val devBDb = FakeBackupDbGateway()
        val devBFiles = tempFolder.newFolder("devB_files")
        val devBVault = InMemoryVaultAdapter()
        val mgrB = WebDavSyncManager(client, devBDb, devBFiles, devBVault)
        try {
            mgrB.restoreFromWebDav(configA, "dav-pw", "wrong-phrase", replaceExisting = true)
            fail("Expected WebDavPassphraseMismatchException")
        } catch (_: WebDavPassphraseMismatchException) {
            assertTrue(devBDb.chatDao.getAllConversations().isEmpty())
        }

        // 6. Device B restore with right passphrase -> restores DB, media blobs, and vault
        val devBRestore = mgrB.restoreFromWebDav(configA, "dav-pw", "e2ee-phrase", replaceExisting = true)
        assertEquals(1, devBRestore.conversationsCount)
        assertEquals("Hello from A", devBDb.chatDao.getMessagesForConversation("conv-a").single().content)
        assertArrayEquals("HERO_A_IMAGE_CONTENT".toByteArray(), File(devBFiles, "rp_media/hero_a.png").readBytes())
        assertArrayEquals("VILLAIN_A_IMAGE_CONTENT".toByteArray(), File(devBFiles, "rp_media/villain_a.png").readBytes())
        assertEquals("sk-ant-key", devBVault.appliedPayload!!.apiProviders.single().apiKey)

        // 7. Device B creates a new conversation & runs syncNow -> merges & uploads; Device A syncNow pulls it & skips redundant push when converged
        devBDb.chatDao.upsertConversation(testConvEntity("conv-b", "Dev B Story", updatedAt = 8000L))
        devBDb.chatDao.upsertMessages(listOf(testMsgEntity("mb-1", "conv-b", seq = 1, content = "From B")))
        val syncB = mgrB.syncNow(configA, "dav-pw", "e2ee-phrase", nowMs = 8001L)
        assertTrue(syncB.remotePulled)
        assertFalse(syncB.backupResult!!.skippedNoChanges)

        val syncA = mgrA.syncNow(configA, "dav-pw", "e2ee-phrase", nowMs = 9000L)
        assertTrue(syncA.remotePulled)
        assertEquals("From B", devADb.chatDao.getMessagesForConversation("conv-b").single().content)
        assertTrue("Second converged sync should skip redundant snapshot upload", syncA.backupResult!!.skippedNoChanges)
    }

    // ==================== Helpers & Fakes ====================

    private fun readZipEntriesAsMap(zipFile: File): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(zipFile.inputStream()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (!e.isDirectory) {
                    out[e.name] = zis.readBytes()
                }
                zis.closeEntry()
            }
        }
        return out
    }

    private fun assertFailsWithSecurity(message: String, block: () -> Unit) {
        try {
            block()
            fail(message)
        } catch (_: GeneralSecurityException) {
            // Expected
        }
    }

    private fun readInt32BE(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)

    private fun testConvEntity(id: String, title: String, updatedAt: Long) = ConversationEntity(
        id = id,
        title = title,
        mode = "chat",
        groupId = null,
        model = "gpt-4o",
        systemPrompt = "",
        paramsJson = "{}",
        novelJson = null,
        rpJson = null,
        archivedSummary = "",
        summarizedUntilSeq = 0,
        tokenCalibration = 1.0,
        createdAt = updatedAt - 100L,
        updatedAt = updatedAt,
        pinned = false,
    )

    private fun testMsgEntity(id: String, convId: String, seq: Int, content: String) = MessageEntity(
        id = id,
        conversationId = convId,
        role = "user",
        content = content,
        reasoning = "",
        model = null,
        createdAt = seq * 10L,
        seq = seq,
        attachmentIdsJson = "[]",
        inputKind = null,
        rpInputKind = null,
        rpDiceRollJson = null,
        rpChoicesJson = "[]",
        novelChoicesJson = "[]",
        status = "done",
        error = null,
        usageJson = null,
        tokenEstimate = 1,
        variantsJson = "[]",
        activeVariantIndex = 0,
        editedAt = null,
        supersededAtSeq = null,
        deletedAt = null,
    )

    private class InMemoryVaultAdapter(
        private var currentPayload: VaultPayloadV2 = VaultPayloadV2(),
    ) : BackupVaultAdapter {
        var appliedPayload: VaultPayloadV2? = null

        override suspend fun readVaultPayload(): VaultPayloadV2 = currentPayload

        override suspend fun applyVaultPayload(payload: VaultPayloadV2, replaceExisting: Boolean): Boolean {
            appliedPayload = payload
            currentPayload = payload
            return true
        }
    }

    private class InMemoryWebDavDispatcher : Dispatcher() {
        val dirs = ConcurrentHashMap.newKeySet<String>().apply { add("/dav/") }
        val files = ConcurrentHashMap<String, ByteArray>()
        val putOrder = CopyOnWriteArrayList<String>()
        val putCalls = CopyOnWriteArrayList<String>()
        val observedPropfindDepths = CopyOnWriteArrayList<String>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path ?: "/"
            return when (request.method) {
                "PROPFIND" -> {
                    val depth = request.getHeader("Depth") ?: "missing"
                    observedPropfindDepths.add(depth)
                    if (depth != "0" && depth != "1") {
                        return MockResponse().setResponseCode(403)
                    }
                    val dirPath = if (path.endsWith("/")) path else "$path/"
                    if (files.containsKey(path)) {
                        return MockResponse().setResponseCode(207).setBody(
                            multiStatus(listOf(ResponseEntry(path, isDir = false, size = files[path]!!.size.toLong()))),
                        )
                    }
                    if (dirPath in dirs) {
                        val entries = ArrayList<ResponseEntry>()
                        entries.add(ResponseEntry(dirPath, isDir = true, size = 0L))
                        if (depth == "1") {
                            for (d in dirs) {
                                if (d != dirPath && d.startsWith(dirPath)) {
                                    val rest = d.removePrefix(dirPath).trimEnd('/')
                                    if (rest.isNotEmpty() && !rest.contains('/')) {
                                        entries.add(ResponseEntry(d, isDir = true, size = 0L))
                                    }
                                }
                            }
                            for ((fPath, bytes) in files) {
                                if (fPath.startsWith(dirPath)) {
                                    val rest = fPath.removePrefix(dirPath)
                                    if (rest.isNotEmpty() && !rest.contains('/')) {
                                        entries.add(ResponseEntry(fPath, isDir = false, size = bytes.size.toLong()))
                                    }
                                }
                            }
                        }
                        return MockResponse().setResponseCode(207).setBody(multiStatus(entries))
                    }
                    MockResponse().setResponseCode(404)
                }
                "MKCOL" -> {
                    val dirPath = if (path.endsWith("/")) path else "$path/"
                    if (dirs.add(dirPath)) MockResponse().setResponseCode(201) else MockResponse().setResponseCode(405)
                }
                "PUT" -> {
                    val bodyBytes = request.body.readByteArray()
                    val declaredLen = request.getHeader("Content-Length")?.toLongOrNull()
                    if (declaredLen == null || declaredLen != bodyBytes.size.toLong()) {
                        return MockResponse().setResponseCode(411)
                    }
                    files[path] = bodyBytes
                    putOrder.add(path)
                    putCalls.add(path)
                    MockResponse().setResponseCode(201)
                }
                "GET" -> {
                    val data = files[path] ?: return MockResponse().setResponseCode(404)
                    MockResponse().setResponseCode(200).setBody(Buffer().write(data))
                }
                "DELETE" -> {
                    val removed = files.remove(path) != null || dirs.remove(if (path.endsWith("/")) path else "$path/")
                    if (removed) MockResponse().setResponseCode(204) else MockResponse().setResponseCode(404)
                }
                else -> MockResponse().setResponseCode(405)
            }
        }

        private data class ResponseEntry(val href: String, val isDir: Boolean, val size: Long)

        private fun multiStatus(entries: List<ResponseEntry>): String = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:">""")
            for (e in entries) {
                append("<D:response><D:href>").append(e.href).append("</D:href><D:propstat><D:prop>")
                if (e.isDir) {
                    append("<D:resourcetype><D:collection/></D:resourcetype>")
                } else {
                    append("<D:resourcetype/><D:getcontentlength>").append(e.size).append("</D:getcontentlength>")
                }
                append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>")
            }
            append("</D:multistatus>")
        }
    }

    /**
     * In-memory transactional implementation of all 16 Room tables for pure-JVM testing.
     * Supports atomic rollback on exception in [withTransaction].
     */
    private class FakeBackupDbGateway : BackupDbGateway {
        private val conversations = LinkedHashMap<String, ConversationEntity>()
        private val messages = LinkedHashMap<String, MessageEntity>()
        private val userMemories = LinkedHashMap<String, UserMemoryEntity>()
        private val novelChapters = LinkedHashMap<String, NovelChapterEntity>()
        private val novelLore = LinkedHashMap<String, NovelLoreEntity>()
        private val memoryLedger = LinkedHashMap<String, MemoryLedgerEntity>()
        private val memoryEpisodes = LinkedHashMap<String, MemoryEpisodeEntity>()
        private val rpSaveSlots = LinkedHashMap<String, RpSaveSlotEntity>()
        private val attachments = LinkedHashMap<String, AttachmentMetaEntity>()
        private val characterCards = LinkedHashMap<String, CharacterCardEntity>()
        private val worldbooks = LinkedHashMap<String, WorldbookEntity>()
        private val worldbookEntries = LinkedHashMap<String, WorldbookEntryEntity>()
        private val personas = LinkedHashMap<String, PersonaEntity>()
        private val promptPresets = LinkedHashMap<String, PromptPresetEntity>()
        private val convWorldbooks = LinkedHashMap<Pair<String, String>, ConversationWorldbookCrossRef>()
        private val embeddings = LinkedHashMap<Triple<String, String, String>, MemoryEmbeddingEntity>()

        override suspend fun <T> withTransaction(block: suspend () -> T): T {
            val snapConv = LinkedHashMap(conversations)
            val snapMsg = LinkedHashMap(messages)
            val snapMem = LinkedHashMap(userMemories)
            val snapChap = LinkedHashMap(novelChapters)
            val snapLore = LinkedHashMap(novelLore)
            val snapLed = LinkedHashMap(memoryLedger)
            val snapEp = LinkedHashMap(memoryEpisodes)
            val snapSlot = LinkedHashMap(rpSaveSlots)
            val snapAtt = LinkedHashMap(attachments)
            val snapCard = LinkedHashMap(characterCards)
            val snapWb = LinkedHashMap(worldbooks)
            val snapWbe = LinkedHashMap(worldbookEntries)
            val snapPer = LinkedHashMap(personas)
            val snapPre = LinkedHashMap(promptPresets)
            val snapCw = LinkedHashMap(convWorldbooks)
            val snapEmb = LinkedHashMap(embeddings)

            return try {
                block()
            } catch (t: Throwable) {
                conversations.clear(); conversations.putAll(snapConv)
                messages.clear(); messages.putAll(snapMsg)
                userMemories.clear(); userMemories.putAll(snapMem)
                novelChapters.clear(); novelChapters.putAll(snapChap)
                novelLore.clear(); novelLore.putAll(snapLore)
                memoryLedger.clear(); memoryLedger.putAll(snapLed)
                memoryEpisodes.clear(); memoryEpisodes.putAll(snapEp)
                rpSaveSlots.clear(); rpSaveSlots.putAll(snapSlot)
                attachments.clear(); attachments.putAll(snapAtt)
                characterCards.clear(); characterCards.putAll(snapCard)
                worldbooks.clear(); worldbooks.putAll(snapWb)
                worldbookEntries.clear(); worldbookEntries.putAll(snapWbe)
                personas.clear(); personas.putAll(snapPer)
                promptPresets.clear(); promptPresets.putAll(snapPre)
                convWorldbooks.clear(); convWorldbooks.putAll(snapCw)
                embeddings.clear(); embeddings.putAll(snapEmb)
                throw t
            }
        }

        override val chatDao: ChatDao = object : ChatDao {
            override fun observeConversations(): Flow<List<ConversationEntity>> = MutableStateFlow(emptyList())
            override suspend fun getAllConversations() = conversations.values.sortedWith(compareByDescending<ConversationEntity> { it.pinned }.thenByDescending { it.updatedAt })
            override suspend fun getConversationsPaged(limit: Int, offset: Int) = conversations.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun getConversationById(id: String) = conversations[id]
            override suspend fun getConversationsByIds(ids: List<String>) = ids.distinct().mapNotNull { conversations[it] }
            override suspend fun upsertConversation(entity: ConversationEntity) { conversations[entity.id] = entity }
            override suspend fun upsertConversations(entities: List<ConversationEntity>) { entities.forEach { conversations[it.id] = it } }
            override suspend fun deleteConversation(id: String) { conversations.remove(id) }

            override fun observeMessages(convId: String): Flow<List<MessageEntity>> = MutableStateFlow(emptyList())
            override suspend fun getMessagesForConversation(convId: String) = messages.values.filter { it.conversationId == convId }.sortedWith(compareBy({ it.seq }, { it.createdAt }))
            override suspend fun getAllMessages() = messages.values.sortedWith(compareBy({ it.conversationId }, { it.seq }))
            override suspend fun getMessagesPaged(limit: Int, offset: Int) = messages.values.sortedWith(compareBy({ it.conversationId }, { it.seq }, { it.id })).drop(offset).take(limit)
            override suspend fun upsertMessage(entity: MessageEntity) { messages[entity.id] = entity }
            override suspend fun upsertMessages(entities: List<MessageEntity>) { entities.forEach { messages[it.id] = it } }
            override suspend fun abortOrphanStreamingMessages() {
                messages.replaceAll { _, m -> if (m.status == "streaming") m.copy(status = "aborted") else m }
            }
            override suspend fun deleteMessagesForConversation(convId: String) { messages.entries.removeIf { it.value.conversationId == convId } }
            override suspend fun deleteMessagesForConversations(convIds: List<String>) {
                val set = convIds.toHashSet()
                messages.entries.removeIf { it.value.conversationId in set }
            }

            override fun observeNovelChapters(convId: String): Flow<List<NovelChapterEntity>> = MutableStateFlow(emptyList())
            override suspend fun getNovelChaptersForConversation(convId: String) = novelChapters.values.filter { it.conversationId == convId }.sortedBy { it.sortOrder }
            override suspend fun getAllNovelChapters() = novelChapters.values.sortedWith(compareBy({ it.conversationId }, { it.sortOrder }))
            override suspend fun getNovelChaptersPaged(limit: Int, offset: Int) = novelChapters.values.sortedWith(compareBy({ it.conversationId }, { it.sortOrder }, { it.id })).drop(offset).take(limit)
            override suspend fun upsertNovelChapter(entity: NovelChapterEntity) { novelChapters[entity.id] = entity }
            override suspend fun upsertNovelChapters(entities: List<NovelChapterEntity>) { entities.forEach { novelChapters[it.id] = it } }
            override suspend fun deleteNovelChapter(id: String) { novelChapters.remove(id) }
            override suspend fun deleteNovelChaptersForConversation(convId: String) { novelChapters.entries.removeIf { it.value.conversationId == convId } }
            override suspend fun deleteNovelChaptersForConversations(convIds: List<String>) {
                val set = convIds.toHashSet()
                novelChapters.entries.removeIf { it.value.conversationId in set }
            }

            override fun observeNovelLore(convId: String): Flow<List<NovelLoreEntity>> = MutableStateFlow(emptyList())
            override suspend fun getNovelLoreForConversation(convId: String) = novelLore.values.filter { it.conversationId == convId }
            override suspend fun getAllNovelLore() = novelLore.values.toList()
            override suspend fun getNovelLorePaged(limit: Int, offset: Int) = novelLore.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun upsertNovelLore(entity: NovelLoreEntity) { novelLore[entity.id] = entity }
            override suspend fun upsertNovelLoreList(entities: List<NovelLoreEntity>) { entities.forEach { novelLore[it.id] = it } }
            override suspend fun deleteNovelLore(id: String) { novelLore.remove(id) }
            override suspend fun deleteNovelLoreForConversation(convId: String) { novelLore.entries.removeIf { it.value.conversationId == convId } }
            override suspend fun deleteNovelLoreForConversations(convIds: List<String>) {
                val set = convIds.toHashSet()
                novelLore.entries.removeIf { it.value.conversationId in set }
            }

            override fun observeMemoryLedger(convId: String): Flow<List<MemoryLedgerEntity>> = MutableStateFlow(emptyList())
            override suspend fun getMemoryLedgerForConversation(convId: String) = memoryLedger.values.filter { it.conversationId == convId }
            override suspend fun getAllMemoryLedger() = memoryLedger.values.toList()
            override suspend fun getMemoryLedgerPaged(limit: Int, offset: Int) = memoryLedger.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun upsertMemoryLedger(entity: MemoryLedgerEntity) { memoryLedger[entity.id] = entity }
            override suspend fun upsertMemoryLedgerList(entities: List<MemoryLedgerEntity>) { entities.forEach { memoryLedger[it.id] = it } }
            override suspend fun deleteMemoryLedger(id: String) { memoryLedger.remove(id) }
            override suspend fun deleteMemoryLedgerForConversation(convId: String) { memoryLedger.entries.removeIf { it.value.conversationId == convId } }
            override suspend fun deleteMemoryLedgerForConversations(convIds: List<String>) {
                val set = convIds.toHashSet()
                memoryLedger.entries.removeIf { it.value.conversationId in set }
            }

            override fun observeMemoryEpisodes(convId: String): Flow<List<MemoryEpisodeEntity>> = MutableStateFlow(emptyList())
            override suspend fun getMemoryEpisodesForConversation(convId: String) = memoryEpisodes.values.filter { it.conversationId == convId }
            override suspend fun getAllMemoryEpisodes() = memoryEpisodes.values.toList()
            override suspend fun getMemoryEpisodesPaged(limit: Int, offset: Int) = memoryEpisodes.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun upsertMemoryEpisode(entity: MemoryEpisodeEntity) { memoryEpisodes[entity.id] = entity }
            override suspend fun upsertMemoryEpisodes(entities: List<MemoryEpisodeEntity>) { entities.forEach { memoryEpisodes[it.id] = it } }
            override suspend fun deleteMemoryEpisode(id: String) { memoryEpisodes.remove(id) }
            override suspend fun deleteMemoryEpisodesForConversation(convId: String) { memoryEpisodes.entries.removeIf { it.value.conversationId == convId } }
            override suspend fun deleteMemoryEpisodesForConversations(convIds: List<String>) {
                val set = convIds.toHashSet()
                memoryEpisodes.entries.removeIf { it.value.conversationId in set }
            }

            override fun observeRpSaveSlots(convId: String): Flow<List<RpSaveSlotEntity>> = MutableStateFlow(emptyList())
            override suspend fun getRpSaveSlotsForConversation(convId: String) = rpSaveSlots.values.filter { it.conversationId == convId }
            override suspend fun getAllRpSaveSlots() = rpSaveSlots.values.toList()
            override suspend fun getRpSaveSlotsPaged(limit: Int, offset: Int) = rpSaveSlots.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun upsertRpSaveSlot(entity: RpSaveSlotEntity) { rpSaveSlots[entity.id] = entity }
            override suspend fun upsertRpSaveSlots(entities: List<RpSaveSlotEntity>) { entities.forEach { rpSaveSlots[it.id] = it } }
            override suspend fun deleteRpSaveSlot(id: String) { rpSaveSlots.remove(id) }
            override suspend fun deleteRpSaveSlotsForConversation(convId: String) { rpSaveSlots.entries.removeIf { it.value.conversationId == convId } }
            override suspend fun deleteRpSaveSlotsForConversations(convIds: List<String>) {
                val set = convIds.toHashSet()
                rpSaveSlots.entries.removeIf { it.value.conversationId in set }
            }

            override suspend fun deleteConversationWorldbooksForConversation(convId: String) { convWorldbooks.entries.removeIf { it.key.first == convId } }
            override suspend fun deleteMemoryEmbeddingsForConversation(convId: String) { embeddings.entries.removeIf { it.value.conversationId == convId } }
            override suspend fun deleteMemoryEmbeddingsForConversations(convIds: List<String>) {
                val set = convIds.toHashSet()
                embeddings.entries.removeIf { it.value.conversationId in set }
            }

            override suspend fun clearAllConversations() { conversations.clear() }
            override suspend fun clearAllMessages() { messages.clear() }
            override suspend fun clearAllNovelChapters() { novelChapters.clear() }
            override suspend fun clearAllNovelLore() { novelLore.clear() }
            override suspend fun clearAllMemoryLedger() { memoryLedger.clear() }
            override suspend fun clearAllMemoryEpisodes() { memoryEpisodes.clear() }
            override suspend fun clearAllRpSaveSlots() { rpSaveSlots.clear() }
        }

        override val userMemoryDao: UserMemoryDao = object : UserMemoryDao {
            override fun observeAll(): Flow<List<UserMemoryEntity>> = MutableStateFlow(emptyList())
            override suspend fun getAll() = userMemories.values.toList()
            override suspend fun getPaged(limit: Int, offset: Int) = userMemories.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun getByIds(ids: List<String>) = ids.distinct().mapNotNull { userMemories[it] }
            override suspend fun upsert(entity: UserMemoryEntity) { userMemories[entity.id] = entity }
            override suspend fun upsertAll(entities: List<UserMemoryEntity>) { entities.forEach { userMemories[it.id] = it } }
            override suspend fun deleteById(id: String) { userMemories.remove(id) }
            override suspend fun clearAll() { userMemories.clear() }
        }

        override val attachmentDao: AttachmentDao = object : AttachmentDao {
            override suspend fun getById(id: String) = attachments[id]
            override suspend fun getByIds(ids: List<String>) = ids.mapNotNull { attachments[it] }
            override suspend fun getPaged(limit: Int, offset: Int) = attachments.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun updateInjectMode(id: String, injectMode: String) {
                attachments[id]?.let { attachments[id] = it.copy(injectMode = injectMode) }
            }
            override suspend fun upsert(entity: AttachmentMetaEntity) { attachments[entity.id] = entity }
            override suspend fun upsertAll(entities: List<AttachmentMetaEntity>) { entities.forEach { attachments[it.id] = it } }
            override suspend fun deleteById(id: String) { attachments.remove(id) }
            override suspend fun clearAll() { attachments.clear() }
        }

        override val assetDao: AssetDao = object : AssetDao {
            override fun observeCharacterCards(): Flow<List<CharacterCardEntity>> = MutableStateFlow(emptyList())
            override suspend fun getAllCharacterCards() = characterCards.values.toList()
            override suspend fun getCharacterCardsPaged(limit: Int, offset: Int) = characterCards.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun getCharacterCardById(id: String) = characterCards[id]
            override suspend fun getCharacterCardsByIds(ids: List<String>) = ids.distinct().mapNotNull { characterCards[it] }
            override suspend fun getCardByFingerprint(fingerprint: String) = characterCards.values.firstOrNull { it.contentFingerprint == fingerprint }
            override suspend fun upsertCharacterCard(entity: CharacterCardEntity) { characterCards[entity.id] = entity }
            override suspend fun upsertCharacterCards(entities: List<CharacterCardEntity>) { entities.forEach { characterCards[it.id] = it } }
            override suspend fun deleteCharacterCard(id: String) { characterCards.remove(id) }
            override suspend fun clearAllCharacterCards() { characterCards.clear() }

            override fun observeWorldbooks(): Flow<List<WorldbookEntity>> = MutableStateFlow(emptyList())
            override fun observeGlobalWorldbooks(): Flow<List<WorldbookEntity>> = MutableStateFlow(emptyList())
            override suspend fun getAllWorldbooks() = worldbooks.values.toList()
            override suspend fun getGlobalEnabledWorldbooks() = worldbooks.values.filter { it.globalEnabled }
            override suspend fun getWorldbooksPaged(limit: Int, offset: Int) = worldbooks.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun getWorldbookById(id: String) = worldbooks[id]
            override suspend fun getWorldbooksByIds(ids: List<String>) = ids.distinct().mapNotNull { worldbooks[it] }
            override suspend fun getWorldbookByFingerprint(fingerprint: String) = worldbooks.values.firstOrNull { it.contentFingerprint == fingerprint }
            override suspend fun upsertWorldbook(entity: WorldbookEntity) { worldbooks[entity.id] = entity }
            override suspend fun upsertWorldbooks(entities: List<WorldbookEntity>) { entities.forEach { worldbooks[it.id] = it } }
            override suspend fun deleteWorldbook(id: String) { worldbooks.remove(id) }
            override suspend fun clearAllWorldbooks() { worldbooks.clear() }

            override fun observeWorldbookEntries(worldbookId: String): Flow<List<WorldbookEntryEntity>> = MutableStateFlow(emptyList())
            override suspend fun getWorldbookEntries(worldbookId: String) = worldbookEntries.values.filter { it.worldbookId == worldbookId }
            override suspend fun getEnabledEntriesForWorldbooks(worldbookIds: List<String>) = worldbookEntries.values.filter { it.worldbookId in worldbookIds && it.enabled }
            override suspend fun getWorldbookEntriesPaged(limit: Int, offset: Int) = worldbookEntries.values.sortedWith(compareBy({ it.worldbookId }, { it.id })).drop(offset).take(limit)
            override suspend fun getWorldbookEntryById(id: String) = worldbookEntries[id]
            override suspend fun getWorldbookEntriesByIds(ids: List<String>) = ids.distinct().mapNotNull { worldbookEntries[it] }
            override suspend fun upsertWorldbookEntry(entity: WorldbookEntryEntity) { worldbookEntries[entity.id] = entity }
            override suspend fun upsertWorldbookEntries(entities: List<WorldbookEntryEntity>) { entities.forEach { worldbookEntries[it.id] = it } }
            override suspend fun deleteWorldbookEntry(id: String) { worldbookEntries.remove(id) }
            override suspend fun deleteEntriesForWorldbook(worldbookId: String) { worldbookEntries.entries.removeIf { it.value.worldbookId == worldbookId } }
            override suspend fun deleteEntriesForWorldbooks(worldbookIds: List<String>) {
                val set = worldbookIds.toHashSet()
                worldbookEntries.entries.removeIf { it.value.worldbookId in set }
            }
            override suspend fun clearAllWorldbookEntries() { worldbookEntries.clear() }

            override fun observePersonas(): Flow<List<PersonaEntity>> = MutableStateFlow(emptyList())
            override suspend fun getAllPersonas() = personas.values.toList()
            override suspend fun getPersonasPaged(limit: Int, offset: Int) = personas.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun getPersonaById(id: String) = personas[id]
            override suspend fun getPersonasByIds(ids: List<String>) = ids.distinct().mapNotNull { personas[it] }
            override suspend fun getDefaultPersona() = personas.values.firstOrNull { it.isDefault }
            override suspend fun upsertPersona(entity: PersonaEntity) { personas[entity.id] = entity }
            override suspend fun upsertPersonas(entities: List<PersonaEntity>) { entities.forEach { personas[it.id] = it } }
            override suspend fun setDefaultPersona(personaId: String) {
                personas.replaceAll { _, p -> p.copy(isDefault = p.id == personaId) }
            }
            override suspend fun deletePersona(id: String) { personas.remove(id) }
            override suspend fun clearAllPersonas() { personas.clear() }

            override fun observePromptPresets(): Flow<List<PromptPresetEntity>> = MutableStateFlow(emptyList())
            override suspend fun getAllPromptPresets() = promptPresets.values.toList()
            override suspend fun getPromptPresetsPaged(limit: Int, offset: Int) = promptPresets.values.sortedBy { it.id }.drop(offset).take(limit)
            override suspend fun getPromptPresetById(id: String) = promptPresets[id]
            override suspend fun getPromptPresetsByIds(ids: List<String>) = ids.distinct().mapNotNull { promptPresets[it] }
            override suspend fun getDefaultRpPromptPreset() = promptPresets.values.firstOrNull { it.isDefaultRp }
            override suspend fun upsertPromptPreset(entity: PromptPresetEntity) { promptPresets[entity.id] = entity }
            override suspend fun upsertPromptPresets(entities: List<PromptPresetEntity>) { entities.forEach { promptPresets[it.id] = it } }
            override suspend fun setDefaultRpPromptPreset(presetId: String) {
                promptPresets.replaceAll { _, p -> p.copy(isDefaultRp = p.id == presetId) }
            }
            override suspend fun deletePromptPreset(id: String) { promptPresets.remove(id) }
            override suspend fun clearAllPromptPresets() { promptPresets.clear() }

            override fun observeMountedWorldbookRefs(convId: String): Flow<List<ConversationWorldbookCrossRef>> = MutableStateFlow(emptyList())
            override suspend fun getMountedWorldbookRefs(convId: String) = convWorldbooks.values.filter { it.conversationId == convId }.sortedBy { it.sortOrder }
            override suspend fun getMountedWorldbooks(convId: String) = getMountedWorldbookRefs(convId).filter { it.enabled }.mapNotNull { worldbooks[it.worldbookId] }
            override suspend fun getConversationWorldbooksPaged(limit: Int, offset: Int) = convWorldbooks.values.sortedWith(compareBy({ it.conversationId }, { it.worldbookId })).drop(offset).take(limit)
            override suspend fun upsertConversationWorldbooks(refs: List<ConversationWorldbookCrossRef>) { refs.forEach { convWorldbooks[it.conversationId to it.worldbookId] = it } }
            override suspend fun deleteConversationWorldbooks(convId: String) { convWorldbooks.entries.removeIf { it.key.first == convId } }
            override suspend fun deleteConversationWorldbooksForConversations(convIds: List<String>) {
                val set = convIds.toHashSet()
                convWorldbooks.entries.removeIf { it.key.first in set }
            }
            override suspend fun deleteWorldbookRefsByWorldbookId(worldbookId: String) { convWorldbooks.entries.removeIf { it.key.second == worldbookId } }
            override suspend fun clearAllConversationWorldbooks() { convWorldbooks.clear() }
        }

        override val memoryEmbeddingDao: MemoryEmbeddingDao = object : MemoryEmbeddingDao {
            override suspend fun getByConversationAndProfile(conversationId: String, profileKey: String) =
                embeddings.values.filter { it.conversationId == conversationId && it.profileKey == profileKey }
            override suspend fun getGlobalByProfile(profileKey: String) =
                embeddings.values.filter { it.conversationId == "" && it.profileKey == profileKey }
            override suspend fun getHashesForProfile(conversationId: String, profileKey: String) =
                embeddings.values.filter { it.conversationId == conversationId && it.profileKey == profileKey }
                    .map { MemoryEmbeddingHashRow(it.ownerType, it.ownerId, it.contentHash) }
            override suspend fun upsertAll(items: List<MemoryEmbeddingEntity>) {
                items.forEach { embeddings[Triple(it.ownerType, it.ownerId, it.profileKey)] = it }
            }
            override suspend fun deleteByOwner(ownerType: String, ownerId: String) {
                embeddings.entries.removeIf { it.key.first == ownerType && it.key.second == ownerId }
            }
            override suspend fun deleteStaleProfiles(activeProfileKey: String) {
                embeddings.entries.removeIf { it.key.third != activeProfileKey }
            }
            override suspend fun countByProfile(profileKey: String) = embeddings.values.count { it.profileKey == profileKey }
            override suspend fun deleteAll() { embeddings.clear() }
        }
    }
}
