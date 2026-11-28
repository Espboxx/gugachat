package com.gugachat.app.data.sync

import androidx.room.withTransaction
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.ApiProviderImportCodec
import com.gugachat.app.core.storage.ApiProviderStore
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.storage.SecretVaultStore
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.core.storage.WebDavSyncConfig
import com.gugachat.app.core.storage.sanitizedForPlaintextStorage
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.UiScalePreset
import com.gugachat.app.core.util.StreamZipUtils
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.OnlineImageGenerationDto
import com.gugachat.app.data.api.OnlineImagePresetDto
import com.gugachat.app.data.api.SseRetryPolicy
import com.gugachat.app.data.api.UpdateChannel
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.AssetDao
import com.gugachat.app.data.local.AttachmentDao
import com.gugachat.app.data.local.AttachmentMetaEntity
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.ChatBackupCodec
import com.gugachat.app.data.local.ChatDao
import com.gugachat.app.data.local.ConversationEntity
import com.gugachat.app.data.local.ConversationWorldbookCrossRef
import com.gugachat.app.data.local.MemoryEmbeddingDao
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
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.RetrievalSettings
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpGameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PushbackInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class MediaBlobRef(
    val sha256: String,
    val relPath: String,
    val sizeBytes: Long,
)

@Serializable
data class BackupManifestV2(
    val formatVersion: Int = 2,
    val dbSchemaVersion: Int = 4,
    val createdAt: Long,
    val kdfSaltHex: String? = null,
    val tablesSha256: String,
    val vaultSha256: String? = null,
    val mediaIndex: List<MediaBlobRef> = emptyList(),
)

@Serializable
data class VaultSettingsSnapshot(
    val serverUrl: String = "",
    val themeStyle: String = ThemeStyle.GUGUGA.wireValue,
    val colorMode: String = ColorMode.SYSTEM.wireValue,
    val uiScale: String = UiScalePreset.DEFAULT.wireValue,
    val language: String = AppLanguage.ZH.tag,
    val memoryBudget: String = "4K",
    val sseAutoRetryCount: Int = SseRetryPolicy.DEFAULT_SSE_AUTO_RETRY_COUNT,
    val updateChannel: String = UpdateChannel.AUTO.wireValue,
    val lastChatGroupId: Long = 0L,
    val lastChatModel: String = "",
    val lastImageKeyId: Long = 0L,
    val lastImageModel: String = "",
    val studioArtKeyId: Long = 0L,
    val studioArtModel: String = "",
    val studioArtChannelName: String = "",
    val directModeActive: Boolean = false,
    val hasChosenEntryMode: Boolean = false,
    val preferredEntryMode: String = "",
    val readerPaper: String = "cream",
    val readerFontSizeSp: Int = 17,
    val readerLineHeight: Float = 1.75f,
    val readerSerif: Boolean = true,
    val readerIndent: Boolean = true,
    val defaultContextWindowOverride: Int? = null,
    val defaultMaxHistoryMessages: Int? = null,
    val defaultMaxOutputTokens: Int? = null,
    val defaultMinOutputChat: Int? = null,
    val defaultMinOutputRp: Int? = null,
    val defaultMinOutputNovel: Int? = null,
    val defaultAutoContinueShort: Boolean = false,
    val retrievalSettings: RetrievalSettings = RetrievalSettings(),
    val tokenCalibrationMap: Map<String, Double> = emptyMap(),
    val lastStudioSubtab: Int? = null,
    val webDavConfig: WebDavSyncConfig = WebDavSyncConfig(),
) {
    companion object {
        fun fromAppSettings(settings: AppSettings): VaultSettingsSnapshot = VaultSettingsSnapshot(
            serverUrl = settings.serverUrl,
            themeStyle = settings.themeStyle.wireValue,
            colorMode = settings.colorMode.wireValue,
            uiScale = settings.uiScale.wireValue,
            language = settings.language.tag,
            memoryBudget = settings.memoryBudget,
            sseAutoRetryCount = settings.sseAutoRetryCount,
            updateChannel = settings.updateChannel.wireValue,
            lastChatGroupId = settings.lastChatGroupId,
            lastChatModel = settings.lastChatModel,
            lastImageKeyId = settings.lastImageKeyId,
            lastImageModel = settings.lastImageModel,
            studioArtKeyId = settings.studioArtKeyId,
            studioArtModel = settings.studioArtModel,
            studioArtChannelName = settings.studioArtChannelName,
            directModeActive = settings.directModeActive,
            hasChosenEntryMode = settings.hasChosenEntryMode,
            preferredEntryMode = settings.preferredEntryMode,
            readerPaper = settings.readerPaper,
            readerFontSizeSp = settings.readerFontSizeSp,
            readerLineHeight = settings.readerLineHeight,
            readerSerif = settings.readerSerif,
            readerIndent = settings.readerIndent,
            defaultContextWindowOverride = settings.defaultContextWindowOverride,
            defaultMaxHistoryMessages = settings.defaultMaxHistoryMessages,
            defaultMaxOutputTokens = settings.defaultMaxOutputTokens,
            defaultMinOutputChat = settings.defaultMinOutputChat,
            defaultMinOutputRp = settings.defaultMinOutputRp,
            defaultMinOutputNovel = settings.defaultMinOutputNovel,
            defaultAutoContinueShort = settings.defaultAutoContinueShort,
            retrievalSettings = settings.retrievalSettings.sanitizedForPlaintextStorage(),
            tokenCalibrationMap = settings.tokenCalibrationMap,
            lastStudioSubtab = settings.lastStudioSubtab,
            webDavConfig = settings.webDavConfig,
        )
    }
}

@Serializable
data class VaultPayloadV2(
    val schemaVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val apiProviders: List<ApiProviderConfig> = emptyList(),
    val secrets: Map<String, String> = emptyMap(),
    val settings: VaultSettingsSnapshot? = null,
) {
    fun strippedOfSecrets(): VaultPayloadV2 = copy(
        apiProviders = apiProviders.map { it.copy(apiKey = "") },
        secrets = emptyMap(),
        settings = settings?.copy(
            retrievalSettings = settings.retrievalSettings.sanitizedForPlaintextStorage(),
        ),
    )
}

@Serializable
data class DirectImagesMetaPayload(
    val generations: List<OnlineImageGenerationDto> = emptyList(),
    val presets: List<OnlineImagePresetDto> = emptyList(),
)

interface BackupDbGateway {
    val chatDao: ChatDao
    val userMemoryDao: UserMemoryDao
    val attachmentDao: AttachmentDao
    val assetDao: AssetDao
    val memoryEmbeddingDao: MemoryEmbeddingDao?
    val roomDatabase: AppDatabase? get() = null

    suspend fun <T> withTransaction(block: suspend () -> T): T
}

class RoomBackupDbGateway(private val db: AppDatabase) : BackupDbGateway {
    override val chatDao: ChatDao = db.chatDao()
    override val userMemoryDao: UserMemoryDao = db.userMemoryDao()
    override val attachmentDao: AttachmentDao = db.attachmentDao()
    override val assetDao: AssetDao = db.assetDao()
    override val memoryEmbeddingDao: MemoryEmbeddingDao = db.memoryEmbeddingDao()
    override val roomDatabase: AppDatabase = db

    override suspend fun <T> withTransaction(block: suspend () -> T): T = db.withTransaction(block)
}

interface BackupVaultAdapter {
    suspend fun readVaultPayload(): VaultPayloadV2
    suspend fun applyVaultPayload(payload: VaultPayloadV2, replaceExisting: Boolean): Boolean

    companion object {
        fun fromStores(
            apiProviderStore: ApiProviderStore,
            secretVaultStore: SecretVaultStore,
            settingsStore: SettingsStore,
        ): BackupVaultAdapter = StoreBackedVaultAdapter(
            apiProviderStore = apiProviderStore,
            secretVaultStore = secretVaultStore,
            settingsStore = settingsStore,
        )
    }
}

private class StoreBackedVaultAdapter(
    private val apiProviderStore: ApiProviderStore,
    private val secretVaultStore: SecretVaultStore,
    private val settingsStore: SettingsStore,
) : BackupVaultAdapter {
    override suspend fun readVaultPayload(): VaultPayloadV2 {
        val currentSettings = settingsStore.currentSettings()
        return VaultPayloadV2(
            schemaVersion = 2,
            exportedAt = System.currentTimeMillis(),
            apiProviders = apiProviderStore.currentProviders(),
            secrets = secretVaultStore.exportSecretsMap(),
            settings = VaultSettingsSnapshot.fromAppSettings(currentSettings),
        )
    }

    override suspend fun applyVaultPayload(
        payload: VaultPayloadV2,
        replaceExisting: Boolean,
    ): Boolean {
        var applied = false

        if (payload.secrets.isNotEmpty()) {
            if (replaceExisting) {
                secretVaultStore.clearAll()
            }
            secretVaultStore.importSecretsMap(payload.secrets, overwriteExisting = replaceExisting)
            applied = true
        } else if (replaceExisting) {
            secretVaultStore.clearAll()
        }

        if (payload.apiProviders.isNotEmpty() || replaceExisting) {
            if (replaceExisting) {
                for (existing in apiProviderStore.currentProviders()) {
                    apiProviderStore.delete(existing.id)
                }
            }
            for (incoming in payload.apiProviders) {
                val byId = apiProviderStore.findById(incoming.id)
                val byKey = apiProviderStore.currentProviders().firstOrNull { existing ->
                    existing.protocol == incoming.protocol &&
                        existing.baseUrl.trim().trimEnd('/').equals(incoming.baseUrl.trim().trimEnd('/'), ignoreCase = true) &&
                        (incoming.apiKey.isBlank() || existing.apiKey == incoming.apiKey.trim())
                }
                val match = byId ?: byKey
                when {
                    match == null -> {
                        apiProviderStore.upsert(incoming)
                        applied = true
                    }
                    incoming.apiKey.isNotBlank() &&
                        (replaceExisting || incoming.updatedAt >= match.updatedAt || match.apiKey.isBlank()) -> {
                        apiProviderStore.upsert(incoming.copy(id = match.id, numericId = match.numericId))
                        applied = true
                    }
                }
            }
        }

        payload.settings?.let { snap ->
            applySettingsSnapshot(snap)
            applied = true
        }

        return applied
    }

    private suspend fun applySettingsSnapshot(snap: VaultSettingsSnapshot) {
        if (snap.serverUrl.isNotBlank()) {
            settingsStore.setServerUrl(snap.serverUrl)
        }
        settingsStore.setThemeStyle(ThemeStyle.fromWire(snap.themeStyle))
        settingsStore.setColorMode(ColorMode.fromWire(snap.colorMode))
        settingsStore.setUiScale(UiScalePreset.fromWire(snap.uiScale))
        settingsStore.setLanguage(AppLanguage.fromTag(snap.language))
        settingsStore.setMemoryBudget(snap.memoryBudget)
        settingsStore.setSseAutoRetryCount(snap.sseAutoRetryCount)
        settingsStore.setUpdateChannel(UpdateChannel.fromWire(snap.updateChannel))
        if (snap.lastChatGroupId != 0L || snap.lastChatModel.isNotBlank()) {
            settingsStore.setLastChatSelection(snap.lastChatGroupId, snap.lastChatModel)
        }
        if (snap.lastImageKeyId != 0L || snap.lastImageModel.isNotBlank()) {
            settingsStore.setLastImageSelection(snap.lastImageKeyId, snap.lastImageModel)
        }
        if (snap.studioArtKeyId != 0L && snap.studioArtModel.isNotBlank()) {
            settingsStore.setStudioArtModelSelection(
                keyId = snap.studioArtKeyId,
                model = snap.studioArtModel,
                channelName = snap.studioArtChannelName,
            )
        }
        settingsStore.setDirectModeActive(snap.directModeActive)
        if (snap.hasChosenEntryMode && snap.preferredEntryMode.isNotBlank()) {
            settingsStore.setPreferredEntryMode(snap.preferredEntryMode)
        }
        settingsStore.setReaderPrefs(
            paper = snap.readerPaper,
            fontSizeSp = snap.readerFontSizeSp,
            lineHeight = snap.readerLineHeight,
            serif = snap.readerSerif,
            indent = snap.readerIndent,
        )
        settingsStore.setDefaultContextPrefs(
            contextWindowOverride = snap.defaultContextWindowOverride,
            maxHistoryMessages = snap.defaultMaxHistoryMessages,
            maxOutputTokens = snap.defaultMaxOutputTokens,
        )
        settingsStore.setAllDefaultMinOutputPrefs(
            chatMinOutput = snap.defaultMinOutputChat,
            rpMinOutput = snap.defaultMinOutputRp,
            novelMinOutput = snap.defaultMinOutputNovel,
            autoContinueShort = snap.defaultAutoContinueShort,
        )
        settingsStore.setRetrievalSettings(
            settings = snap.retrievalSettings.sanitizedForPlaintextStorage(),
            secretVaultStore = null,
        )
        for ((k, v) in snap.tokenCalibrationMap) {
            settingsStore.updateTokenCalibration(k, v)
        }
        snap.lastStudioSubtab?.let { settingsStore.setLastStudioSubtab(it) }
        if (snap.webDavConfig.url.isNotBlank() || snap.webDavConfig.username.isNotBlank()) {
            settingsStore.setWebDavConfig(snap.webDavConfig)
        }
    }
}

data class BackupExportOptions(
    val passphrase: String? = null,
    val kdfSalt: ByteArray? = null,
    val includeVault: Boolean = true,
    val includeDirectImages: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

data class StagedMediaBlob(
    val sha256: String,
    val relPath: String,
    val sizeBytes: Long,
    val sourceFile: File,
)

data class BackupStageResult(
    val stageDir: File,
    val tablesDir: File,
    val manifestFile: File,
    val manifest: BackupManifestV2,
    val vaultFile: File?,
    val vaultPlainSha256: String?,
    val stagedBlobs: List<StagedMediaBlob>,
) {
    fun uniqueBlobsBySha(): List<StagedMediaBlob> = stagedBlobs.distinctBy { it.sha256 }
}

data class BackupRestoreResult(
    val formatVersion: Int,
    val conversationsCount: Int,
    val conversationsOverwrittenOrInserted: Int = conversationsCount,
    val messagesCount: Int,
    val userMemoriesCount: Int,
    val worldbooksCount: Int = 0,
    val characterCardsCount: Int = 0,
    val personasCount: Int = 0,
    val promptPresetsCount: Int = 0,
    val mediaBlobsRestored: Int = 0,
    val vaultRestored: Boolean = false,
    val directImagesRestored: Int = 0,
)

enum class DetectedBackupFormat {
    ARCHIVE_V2_ZIP,
    LEGACY_V1_JSON,
    UNKNOWN,
}

class BackupPassphraseRequiredException(
    message: String = "This encrypted backup requires a passphrase",
) : IllegalArgumentException(message)

/**
 * Streaming `.gugubackup` v2 archive & whole-conversation atomic restore engine.
 *
 * Shares 100% of stage serialization, relative media normalization, SHA-256 verification,
 * and transactional merge logic with [WebDavSyncManager] while preserving `<4MB` O(1) memory usage.
 */
object BackupArchiveEngine {
    const val PAGE_SIZE = 200
    private const val IO_BUFFER_BYTES = 64 * 1024
    private const val MAX_LEGACY_JSON_BYTES = 128L * 1024L * 1024L
    private const val MAX_VAULT_BYTES = 16L * 1024L * 1024L
    private const val MAX_MANIFEST_BYTES = 32L * 1024L * 1024L
    private const val MAX_UNPACKED_ARCHIVE_BYTES = 512L * 1024L * 1024L
    private const val MAX_ARCHIVE_ENTRY_COUNT = 20_000

    const val MANIFEST_FILE = "manifest.json"
    const val VAULT_ENC_FILE = "vault.enc"
    const val VAULT_JSON_FILE = "vault.json"
    const val DIRECT_IMAGES_META_FILE = "direct_images_meta.json"

    val TABLE_FILES_IN_ORDER: List<String> = listOf(
        "worldbooks.ndjson",
        "worldbook_entries.ndjson",
        "character_cards.ndjson",
        "personas.ndjson",
        "prompt_presets.ndjson",
        "user_memories.ndjson",
        "conversations.ndjson",
        "conversation_worldbooks.ndjson",
        "messages.ndjson",
        "novel_chapters.ndjson",
        "novel_lore.ndjson",
        "memory_ledger.ndjson",
        "memory_episodes.ndjson",
        "rp_save_slots.ndjson",
        "attachment_meta.ndjson",
    )

    private val ZIP_LOCAL_FILE_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val ALLOWED_MEDIA_PREFIXES = listOf("rp_media/", "chat_attachments/", "direct_images/")
    private val STRING_MAP_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    private val RP_CHAR_LIST_SERIALIZER = ListSerializer(RpCharacter.serializer())
    private val VARIANT_LIST_SERIALIZER = ListSerializer(MessageVariant.serializer())
    private val CHAT_MSG_LIST_SERIALIZER = ListSerializer(ChatMessage.serializer())
    private val DIRECT_GEN_LIST_SERIALIZER = ListSerializer(OnlineImageGenerationDto.serializer())
    private val DIRECT_PRESET_LIST_SERIALIZER = ListSerializer(OnlineImagePresetDto.serializer())

    fun detectBackupFormat(headerBytes: ByteArray): DetectedBackupFormat {
        if (headerBytes.size >= 4 &&
            headerBytes[0] == ZIP_LOCAL_FILE_MAGIC[0] &&
            headerBytes[1] == ZIP_LOCAL_FILE_MAGIC[1] &&
            headerBytes[2] == ZIP_LOCAL_FILE_MAGIC[2] &&
            headerBytes[3] == ZIP_LOCAL_FILE_MAGIC[3]
        ) {
            return DetectedBackupFormat.ARCHIVE_V2_ZIP
        }
        var idx = 0
        if (headerBytes.size >= 3 &&
            headerBytes[0] == 0xEF.toByte() &&
            headerBytes[1] == 0xBB.toByte() &&
            headerBytes[2] == 0xBF.toByte()
        ) {
            idx = 3
        }
        while (idx < headerBytes.size) {
            val b = headerBytes[idx].toInt() and 0xFF
            if (b == ' '.code || b == '\t'.code || b == '\n'.code || b == '\r'.code) {
                idx += 1
                continue
            }
            return if (b == '{'.code) DetectedBackupFormat.LEGACY_V1_JSON else DetectedBackupFormat.UNKNOWN
        }
        return DetectedBackupFormat.UNKNOWN
    }

    suspend fun exportBackupToFile(
        db: AppDatabase,
        filesDir: File,
        outputFile: File,
        vaultAdapter: BackupVaultAdapter? = null,
        options: BackupExportOptions = BackupExportOptions(),
        tempRootDir: File? = null,
    ): BackupManifestV2 = exportBackupToFile(
        gateway = RoomBackupDbGateway(db),
        filesDir = filesDir,
        outputFile = outputFile,
        vaultAdapter = vaultAdapter,
        options = options,
        tempRootDir = tempRootDir,
    )

    suspend fun exportBackupToFile(
        gateway: BackupDbGateway,
        filesDir: File,
        outputFile: File,
        vaultAdapter: BackupVaultAdapter? = null,
        options: BackupExportOptions = BackupExportOptions(),
        tempRootDir: File? = null,
    ): BackupManifestV2 {
        outputFile.parentFile?.mkdirs()
        val tmpOut = File(outputFile.parentFile, "${outputFile.name}.${System.nanoTime()}.tmp")
        var ok = false
        return try {
            val manifest = BufferedOutputStream(FileOutputStream(tmpOut), IO_BUFFER_BYTES).use { bos ->
                exportBackupToStream(
                    gateway = gateway,
                    filesDir = filesDir,
                    output = bos,
                    vaultAdapter = vaultAdapter,
                    options = options,
                    tempRootDir = tempRootDir,
                )
            }
            StreamZipUtils.atomicReplace(tmpOut, outputFile)
            ok = true
            manifest
        } finally {
            if (!ok || tmpOut.exists()) {
                tmpOut.delete()
            }
        }
    }

    suspend fun exportBackupToStream(
        db: AppDatabase,
        filesDir: File,
        output: OutputStream,
        vaultAdapter: BackupVaultAdapter? = null,
        options: BackupExportOptions = BackupExportOptions(),
        tempRootDir: File? = null,
    ): BackupManifestV2 = exportBackupToStream(
        gateway = RoomBackupDbGateway(db),
        filesDir = filesDir,
        output = output,
        vaultAdapter = vaultAdapter,
        options = options,
        tempRootDir = tempRootDir,
    )

    suspend fun exportBackupToStream(
        gateway: BackupDbGateway,
        filesDir: File,
        output: OutputStream,
        vaultAdapter: BackupVaultAdapter? = null,
        options: BackupExportOptions = BackupExportOptions(),
        tempRootDir: File? = null,
    ): BackupManifestV2 = withContext(Dispatchers.IO) {
        val stageParent = tempRootDir ?: File(filesDir, ".backup_stage_tmp")
        stageParent.mkdirs()
        val stageDir = File(stageParent, "export_${System.nanoTime()}").apply { mkdirs() }
        try {
            val staged = prepareStageDirectory(
                stageDir = stageDir,
                gateway = gateway,
                filesDir = filesDir,
                vaultAdapter = vaultAdapter,
                options = options,
            )
            ZipOutputStream(BufferedOutputStream(output, IO_BUFFER_BYTES)).use { zos ->
                writeFileToZip(zos, MANIFEST_FILE, staged.manifestFile)
                for (name in TABLE_FILES_IN_ORDER) {
                    val f = File(staged.tablesDir, name)
                    if (f.isFile) {
                        writeFileToZip(zos, "tables/$name", f)
                    }
                }
                val directMetaFile = File(staged.tablesDir, DIRECT_IMAGES_META_FILE)
                if (directMetaFile.isFile) {
                    writeFileToZip(zos, "tables/$DIRECT_IMAGES_META_FILE", directMetaFile)
                }
                staged.vaultFile?.takeIf { it.isFile }?.let { vf ->
                    writeFileToZip(zos, vf.name, vf)
                }
                for (blob in staged.uniqueBlobsBySha()) {
                    if (blob.sourceFile.isFile) {
                        writeFileToZip(zos, "blobs/${blob.sha256}", blob.sourceFile)
                    }
                }
                zos.finish()
            }
            staged.manifest
        } finally {
            stageDir.deleteRecursively()
        }
    }

    suspend fun prepareStageDirectory(
        stageDir: File,
        db: AppDatabase,
        filesDir: File,
        vaultAdapter: BackupVaultAdapter? = null,
        options: BackupExportOptions = BackupExportOptions(),
    ): BackupStageResult = prepareStageDirectory(
        stageDir = stageDir,
        gateway = RoomBackupDbGateway(db),
        filesDir = filesDir,
        vaultAdapter = vaultAdapter,
        options = options,
    )

    suspend fun prepareStageDirectory(
        stageDir: File,
        gateway: BackupDbGateway,
        filesDir: File,
        vaultAdapter: BackupVaultAdapter? = null,
        options: BackupExportOptions = BackupExportOptions(),
    ): BackupStageResult = withContext(Dispatchers.IO) {
        stageDir.mkdirs()
        val tablesDir = File(stageDir, "tables").apply { mkdirs() }
        val filesDirPath = filesDir.absolutePath
        val referencedRelPaths = LinkedHashSet<String>()

        gateway.withTransaction {
            // 1. Parent global asset tables
            dumpTablePaged(File(tablesDir, "worldbooks.ndjson"), WorldbookEntity.serializer()) { offset ->
                gateway.assetDao.getWorldbooksPaged(PAGE_SIZE, offset)
            }
            dumpTablePaged(File(tablesDir, "worldbook_entries.ndjson"), WorldbookEntryEntity.serializer()) { offset ->
                gateway.assetDao.getWorldbookEntriesPaged(PAGE_SIZE, offset)
            }
            dumpTablePaged(File(tablesDir, "character_cards.ndjson"), CharacterCardEntity.serializer()) { offset ->
                gateway.assetDao.getCharacterCardsPaged(PAGE_SIZE, offset).map { card ->
                    normalizeCharacterCardForExport(card, filesDirPath, options.includeDirectImages, referencedRelPaths)
                }
            }
            dumpTablePaged(File(tablesDir, "personas.ndjson"), PersonaEntity.serializer()) { offset ->
                gateway.assetDao.getPersonasPaged(PAGE_SIZE, offset).map { persona ->
                    normalizePersonaForExport(persona, filesDirPath, options.includeDirectImages, referencedRelPaths)
                }
            }
            dumpTablePaged(File(tablesDir, "prompt_presets.ndjson"), PromptPresetEntity.serializer()) { offset ->
                gateway.assetDao.getPromptPresetsPaged(PAGE_SIZE, offset)
            }

            // 2. Business & association tables (memory_embeddings strictly skipped)
            dumpTablePaged(File(tablesDir, "user_memories.ndjson"), UserMemoryEntity.serializer()) { offset ->
                gateway.userMemoryDao.getPaged(PAGE_SIZE, offset)
            }
            dumpTablePaged(File(tablesDir, "conversations.ndjson"), ConversationEntity.serializer()) { offset ->
                gateway.chatDao.getConversationsPaged(PAGE_SIZE, offset).map { conv ->
                    normalizeConversationForExport(conv, filesDirPath, options.includeDirectImages, referencedRelPaths)
                }
            }
            dumpTablePaged(
                File(tablesDir, "conversation_worldbooks.ndjson"),
                ConversationWorldbookCrossRef.serializer(),
            ) { offset ->
                gateway.assetDao.getConversationWorldbooksPaged(PAGE_SIZE, offset)
            }
            dumpTablePaged(File(tablesDir, "messages.ndjson"), MessageEntity.serializer()) { offset ->
                gateway.chatDao.getMessagesPaged(PAGE_SIZE, offset).map { msg ->
                    normalizeMessageForExport(msg, filesDirPath, options.includeDirectImages, referencedRelPaths)
                }
            }
            dumpTablePaged(File(tablesDir, "novel_chapters.ndjson"), NovelChapterEntity.serializer()) { offset ->
                gateway.chatDao.getNovelChaptersPaged(PAGE_SIZE, offset)
            }
            dumpTablePaged(File(tablesDir, "novel_lore.ndjson"), NovelLoreEntity.serializer()) { offset ->
                gateway.chatDao.getNovelLorePaged(PAGE_SIZE, offset)
            }
            dumpTablePaged(File(tablesDir, "memory_ledger.ndjson"), MemoryLedgerEntity.serializer()) { offset ->
                gateway.chatDao.getMemoryLedgerPaged(PAGE_SIZE, offset)
            }
            dumpTablePaged(File(tablesDir, "memory_episodes.ndjson"), MemoryEpisodeEntity.serializer()) { offset ->
                gateway.chatDao.getMemoryEpisodesPaged(PAGE_SIZE, offset)
            }
            dumpTablePaged(File(tablesDir, "rp_save_slots.ndjson"), RpSaveSlotEntity.serializer()) { offset ->
                gateway.chatDao.getRpSaveSlotsPaged(PAGE_SIZE, offset).map { slot ->
                    normalizeRpSaveSlotForExport(slot, filesDirPath, options.includeDirectImages, referencedRelPaths)
                }
            }
            dumpTablePaged(File(tablesDir, "attachment_meta.ndjson"), AttachmentMetaEntity.serializer()) { offset ->
                gateway.attachmentDao.getPaged(PAGE_SIZE, offset).map { att ->
                    normalizeAttachmentForExport(att, filesDirPath, options.includeDirectImages, referencedRelPaths)
                }
            }
        }

        // Optional DirectImageStore metadata & images
        if (options.includeDirectImages) {
            exportDirectImagesMeta(filesDir, File(tablesDir, DIRECT_IMAGES_META_FILE), referencedRelPaths)
        }

        val tablesSha256 = computeTablesSha256(tablesDir)

        // Collect and SHA-256 hash referenced media files via 64KB streaming
        val stagedBlobs = ArrayList<StagedMediaBlob>()
        val mediaIndex = ArrayList<MediaBlobRef>()
        val canonicalFilesDir = filesDir.canonicalFile
        for (relPath in referencedRelPaths) {
            val resolvedFile = resolveValidatedMediaFile(filesDir, canonicalFilesDir, relPath) ?: continue
            val sha = SyncCryptoCodec.sha256Hex(resolvedFile)
            val sizeBytes = resolvedFile.length()
            mediaIndex.add(MediaBlobRef(sha256 = sha, relPath = relPath, sizeBytes = sizeBytes))
            stagedBlobs.add(StagedMediaBlob(sha256 = sha, relPath = relPath, sizeBytes = sizeBytes, sourceFile = resolvedFile))
        }

        // Vault handling
        val passphrase = options.passphrase?.takeIf { it.isNotEmpty() }
        val salt = if (passphrase != null) {
            options.kdfSalt?.copyOf() ?: SyncCryptoCodec.generateSalt()
        } else {
            options.kdfSalt?.copyOf()
        }
        val kdfSaltHex = salt?.let { SyncCryptoCodec.toHex(it) }

        var vaultFile: File? = null
        var vaultSha256: String? = null
        var vaultPlainSha256: String? = null
        if (options.includeVault && vaultAdapter != null) {
            val rawPayload = vaultAdapter.readVaultPayload()
            if (passphrase != null && salt != null) {
                val plainJsonBytes = AppJson.encodeToString(VaultPayloadV2.serializer(), rawPayload)
                    .toByteArray(StandardCharsets.UTF_8)
                vaultPlainSha256 = SyncCryptoCodec.sha256Hex(plainJsonBytes)
                val encryptedBytes = SyncCryptoCodec.encryptVaultBytes(passphrase, salt, plainJsonBytes)
                vaultSha256 = SyncCryptoCodec.sha256Hex(encryptedBytes)
                vaultFile = File(stageDir, VAULT_ENC_FILE).apply { writeBytes(encryptedBytes) }
            } else {
                val stripped = rawPayload.strippedOfSecrets()
                val strippedBytes = AppJson.encodeToString(VaultPayloadV2.serializer(), stripped)
                    .toByteArray(StandardCharsets.UTF_8)
                vaultPlainSha256 = SyncCryptoCodec.sha256Hex(strippedBytes)
                vaultSha256 = vaultPlainSha256
                vaultFile = File(stageDir, VAULT_JSON_FILE).apply { writeBytes(strippedBytes) }
            }
        }

        val manifest = BackupManifestV2(
            formatVersion = 2,
            dbSchemaVersion = 4,
            createdAt = options.createdAt,
            kdfSaltHex = kdfSaltHex,
            tablesSha256 = tablesSha256,
            vaultSha256 = vaultSha256,
            mediaIndex = mediaIndex,
        )
        val manifestFile = File(stageDir, MANIFEST_FILE)
        manifestFile.writeText(AppJson.encodeToString(BackupManifestV2.serializer(), manifest), StandardCharsets.UTF_8)

        BackupStageResult(
            stageDir = stageDir,
            tablesDir = tablesDir,
            manifestFile = manifestFile,
            manifest = manifest,
            vaultFile = vaultFile,
            vaultPlainSha256 = vaultPlainSha256,
            stagedBlobs = stagedBlobs,
        )
    }

    suspend fun restoreFromFile(
        db: AppDatabase,
        filesDir: File,
        inputFile: File,
        replaceExisting: Boolean = false,
        passphrase: String? = null,
        vaultAdapter: BackupVaultAdapter? = null,
        includeDirectImages: Boolean = true,
        tempRootDir: File? = null,
    ): BackupRestoreResult = restoreFromFile(
        gateway = RoomBackupDbGateway(db),
        filesDir = filesDir,
        inputFile = inputFile,
        replaceExisting = replaceExisting,
        passphrase = passphrase,
        vaultAdapter = vaultAdapter,
        includeDirectImages = includeDirectImages,
        tempRootDir = tempRootDir,
    )

    suspend fun restoreFromFile(
        gateway: BackupDbGateway,
        filesDir: File,
        inputFile: File,
        replaceExisting: Boolean = false,
        passphrase: String? = null,
        vaultAdapter: BackupVaultAdapter? = null,
        includeDirectImages: Boolean = true,
        tempRootDir: File? = null,
    ): BackupRestoreResult = BufferedInputStream(FileInputStream(inputFile), IO_BUFFER_BYTES).use { bis ->
        restoreFromStream(
            gateway = gateway,
            filesDir = filesDir,
            input = bis,
            replaceExisting = replaceExisting,
            passphrase = passphrase,
            vaultAdapter = vaultAdapter,
            includeDirectImages = includeDirectImages,
            tempRootDir = tempRootDir,
        )
    }

    suspend fun restoreFromStream(
        db: AppDatabase,
        filesDir: File,
        input: InputStream,
        replaceExisting: Boolean = false,
        passphrase: String? = null,
        vaultAdapter: BackupVaultAdapter? = null,
        includeDirectImages: Boolean = true,
        tempRootDir: File? = null,
    ): BackupRestoreResult = restoreFromStream(
        gateway = RoomBackupDbGateway(db),
        filesDir = filesDir,
        input = input,
        replaceExisting = replaceExisting,
        passphrase = passphrase,
        vaultAdapter = vaultAdapter,
        includeDirectImages = includeDirectImages,
        tempRootDir = tempRootDir,
    )

    suspend fun restoreFromStream(
        gateway: BackupDbGateway,
        filesDir: File,
        input: InputStream,
        replaceExisting: Boolean = false,
        passphrase: String? = null,
        vaultAdapter: BackupVaultAdapter? = null,
        includeDirectImages: Boolean = true,
        tempRootDir: File? = null,
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        val pushback = PushbackInputStream(BufferedInputStream(input, IO_BUFFER_BYTES), 64)
        val header = ByteArray(64)
        val peekLen = readUpTo(pushback, header)
        if (peekLen <= 0) {
            throw IllegalArgumentException("备份文件为空")
        }
        pushback.unread(header, 0, peekLen)
        val format = detectBackupFormat(header.copyOf(peekLen))

        when (format) {
            DetectedBackupFormat.LEGACY_V1_JSON -> {
                val rawJson = readUtf8Bounded(pushback, MAX_LEGACY_JSON_BYTES)
                restoreLegacyV1Json(gateway, rawJson, replaceExisting)
            }
            DetectedBackupFormat.ARCHIVE_V2_ZIP -> {
                val stageParent = tempRootDir ?: File(filesDir, ".backup_stage_tmp")
                stageParent.mkdirs()
                val stageDir = File(stageParent, "restore_${System.nanoTime()}").apply { mkdirs() }
                try {
                    unpackArchiveToStageDirectory(pushback, stageDir)
                    applyStageDirectory(
                        stageDir = stageDir,
                        gateway = gateway,
                        filesDir = filesDir,
                        replaceExisting = replaceExisting,
                        passphrase = passphrase,
                        vaultAdapter = vaultAdapter,
                        includeDirectImages = includeDirectImages,
                    )
                } finally {
                    stageDir.deleteRecursively()
                }
            }
            DetectedBackupFormat.UNKNOWN -> {
                throw IllegalArgumentException("不支持的备份文件格式（仅支持 .gugubackup 或 GuGuChat JSON 备份）")
            }
        }
    }

    internal suspend fun restoreLegacyV1Json(
        gateway: BackupDbGateway,
        rawJson: String,
        replaceExisting: Boolean,
    ): BackupRestoreResult {
        val roomDb = gateway.roomDatabase
        if (roomDb != null) {
            val res = ChatBackupCodec.importJson(roomDb, rawJson, replaceExisting)
            return BackupRestoreResult(
                formatVersion = 1,
                conversationsCount = res.conversationsCount,
                conversationsOverwrittenOrInserted = res.conversationsCount,
                messagesCount = res.messagesCount,
                userMemoriesCount = res.memoriesCount,
            )
        }
        // Pure-JVM gateway fallback still delegates parsing/validation to ChatBackupCodec
        val payload = ChatBackupCodec.decodePayload(rawJson)
        val chatDao = gateway.chatDao
        val memDao = gateway.userMemoryDao
        val assetDao = gateway.assetDao
        val embDao = gateway.memoryEmbeddingDao
        gateway.withTransaction {
            ChatBackupCodec.restorePayloadInTransaction(
                chatDao = chatDao,
                memDao = memDao,
                payload = payload,
                replaceExisting = replaceExisting,
                assetDao = assetDao,
                memoryEmbeddingDao = embDao,
            )
        }
        return BackupRestoreResult(
            formatVersion = 1,
            conversationsCount = payload.conversations.size,
            conversationsOverwrittenOrInserted = payload.conversations.size,
            messagesCount = payload.messages.size,
            userMemoriesCount = payload.userMemories.size,
        )
    }

    internal fun unpackArchiveToStageDirectory(input: InputStream, stageDir: File) {
        stageDir.mkdirs()
        val canonicalStageDir = stageDir.canonicalFile
        val buf = ByteArray(IO_BUFFER_BYTES)
        var entryCount = 0
        var totalUnpackedBytes = 0L

        ZipInputStream(input).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entryCount += 1
                if (entryCount > MAX_ARCHIVE_ENTRY_COUNT) {
                    throw IllegalArgumentException("Backup archive exceeds maximum entry count ($MAX_ARCHIVE_ENTRY_COUNT)")
                }
                val normName = entry.name.replace('\\', '/').trimStart('/')
                if (normName.isEmpty() || entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val segments = normName.split('/')
                if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
                    throw SecurityException("Unsafe path in backup archive: ${entry.name}")
                }
                val targetFile = File(stageDir, normName)
                val canonicalTarget = targetFile.canonicalFile
                if (!canonicalTarget.path.startsWith(canonicalStageDir.path + File.separator)) {
                    throw SecurityException("Zip slip detected in backup archive: ${entry.name}")
                }
                targetFile.parentFile?.mkdirs()

                var totalWritten = 0L
                val maxEntryBytes = when {
                    normName == MANIFEST_FILE -> MAX_MANIFEST_BYTES
                    normName == VAULT_ENC_FILE || normName == VAULT_JSON_FILE -> MAX_VAULT_BYTES
                    else -> MAX_UNPACKED_ARCHIVE_BYTES
                }
                BufferedOutputStream(FileOutputStream(targetFile), IO_BUFFER_BYTES).use { outs ->
                    while (true) {
                        val n = zis.read(buf)
                        if (n <= 0) break
                        totalWritten += n
                        if (totalWritten > maxEntryBytes) {
                            throw IllegalArgumentException("Backup entry exceeds size limit: $normName")
                        }
                        totalUnpackedBytes += n
                        if (totalUnpackedBytes > MAX_UNPACKED_ARCHIVE_BYTES) {
                            throw IllegalArgumentException(
                                "Backup archive exceeds cumulative unpacked size limit ($MAX_UNPACKED_ARCHIVE_BYTES bytes)",
                            )
                        }
                        outs.write(buf, 0, n)
                    }
                    outs.flush()
                }
                zis.closeEntry()
            }
        }
    }

    suspend fun applyStageDirectory(
        stageDir: File,
        db: AppDatabase,
        filesDir: File,
        replaceExisting: Boolean = false,
        passphrase: String? = null,
        vaultAdapter: BackupVaultAdapter? = null,
        includeDirectImages: Boolean = true,
    ): BackupRestoreResult = applyStageDirectory(
        stageDir = stageDir,
        gateway = RoomBackupDbGateway(db),
        filesDir = filesDir,
        replaceExisting = replaceExisting,
        passphrase = passphrase,
        vaultAdapter = vaultAdapter,
        includeDirectImages = includeDirectImages,
    )

    suspend fun applyStageDirectory(
        stageDir: File,
        gateway: BackupDbGateway,
        filesDir: File,
        replaceExisting: Boolean = false,
        passphrase: String? = null,
        vaultAdapter: BackupVaultAdapter? = null,
        includeDirectImages: Boolean = true,
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        val manifestFile = File(stageDir, MANIFEST_FILE)
        require(manifestFile.isFile) { "Backup stage missing $MANIFEST_FILE" }
        val manifest = AppJson.decodeFromString(
            BackupManifestV2.serializer(),
            manifestFile.readText(StandardCharsets.UTF_8),
        )
        require(manifest.formatVersion == 2) { "Unsupported backup format version: ${manifest.formatVersion}" }

        val tablesDir = File(stageDir, "tables")
        require(tablesDir.isDirectory) { "Backup stage missing tables directory" }

        // 1. Validate tables integrity before any DB or file mutation
        val actualTablesSha = computeTablesSha256(tablesDir)
        if (!actualTablesSha.equals(manifest.tablesSha256, ignoreCase = true)) {
            throw SecurityException("Backup tables SHA-256 verification failed")
        }

        // 2. Decrypt & validate vault BEFORE DB mutation (so wrong passphrase aborts cleanly!)
        val vaultPayload: VaultPayloadV2? = loadAndVerifyVaultFromStage(stageDir, manifest, passphrase)

        // 3. Validate staged media blobs before writing them to filesDir
        val blobsDir = File(stageDir, "blobs")
        val validatedBlobsToInstall = ArrayList<Pair<File?, MediaBlobRef>>()
        val verifiedShaCache = HashMap<String, File?>()
        val canonicalFilesDir = filesDir.canonicalFile

        for (ref in manifest.mediaIndex) {
            val cleanRel = sanitizeMediaRelPath(ref.relPath) ?: continue
            if (!includeDirectImages && cleanRel.startsWith("direct_images/")) continue

            val stagedBlob = verifiedShaCache.getOrPut(ref.sha256) {
                val candidate = File(blobsDir, ref.sha256)
                if (candidate.isFile) {
                    val actualSha = SyncCryptoCodec.sha256Hex(candidate)
                    if (!actualSha.equals(ref.sha256, ignoreCase = true)) {
                        throw SecurityException("Media blob SHA-256 mismatch for ${ref.sha256}")
                    }
                    candidate
                } else {
                    val existingLocal = resolveValidatedMediaFile(filesDir, canonicalFilesDir, cleanRel)
                    if (existingLocal != null && SyncCryptoCodec.sha256Hex(existingLocal).equals(ref.sha256, ignoreCase = true)) {
                        null // already intact on disk
                    } else {
                        throw IllegalStateException("Missing media blob ${ref.sha256} for $cleanRel")
                    }
                }
            }
            validatedBlobsToInstall.add(stagedBlob to ref.copy(relPath = cleanRel))
        }

        // 4. Write media files to filesDir (atomic temp + rename, skipping identical local files)
        var mediaBlobsRestored = 0
        for ((stagedBlob, ref) in validatedBlobsToInstall) {
            if (stagedBlob == null) continue
            val destFile = resolveDestinationMediaFile(filesDir, canonicalFilesDir, ref.relPath)
            if (destFile.isFile && destFile.length() == ref.sizeBytes &&
                SyncCryptoCodec.sha256Hex(destFile).equals(ref.sha256, ignoreCase = true)
            ) {
                continue
            }
            destFile.parentFile?.mkdirs()
            val tmp = File(destFile.parentFile, "${destFile.name}.${System.nanoTime()}.tmp")
            var copied = false
            try {
                BufferedInputStream(FileInputStream(stagedBlob), IO_BUFFER_BYTES).use { ins ->
                    BufferedOutputStream(FileOutputStream(tmp), IO_BUFFER_BYTES).use { outs ->
                        copyStream(ins, outs)
                        outs.flush()
                    }
                }
                StreamZipUtils.atomicReplace(tmp, destFile)
                copied = true
                mediaBlobsRestored += 1
            } finally {
                if (!copied || tmp.exists()) tmp.delete()
            }
        }

        // 5. Atomic Room database restore / whole-conversation LWW merge
        val filesDirPath = filesDir.absolutePath
        var worldbooksCount = 0
        var cardsCount = 0
        var personasCount = 0
        var presetsCount = 0
        var memoriesCount = 0
        var conversationsTotal = 0
        var conversationsApplied = 0
        var messagesApplied = 0

        gateway.withTransaction {
            val chatDao = gateway.chatDao
            val memDao = gateway.userMemoryDao
            val attDao = gateway.attachmentDao
            val assetDao = gateway.assetDao

            if (replaceExisting) {
                chatDao.clearAllRpSaveSlots()
                chatDao.clearAllMemoryEpisodes()
                chatDao.clearAllMemoryLedger()
                chatDao.clearAllNovelLore()
                chatDao.clearAllNovelChapters()
                chatDao.clearAllMessages()
                assetDao.clearAllConversationWorldbooks()
                chatDao.clearAllConversations()
                memDao.clearAll()
                attDao.clearAll()
                assetDao.clearAllWorldbookEntries()
                assetDao.clearAllWorldbooks()
                assetDao.clearAllCharacterCards()
                assetDao.clearAllPersonas()
                assetDao.clearAllPromptPresets()
                gateway.memoryEmbeddingDao?.deleteAll()
            }

            // Parent global asset tables first
            val acceptedWorldbooks = HashSet<String>()
            val skippedWorldbooks = HashSet<String>()

            streamNdjsonBatches(File(tablesDir, "worldbooks.ndjson"), WorldbookEntity.serializer()) { batch ->
                if (replaceExisting) {
                    assetDao.upsertWorldbooks(batch)
                    worldbooksCount += batch.size
                    batch.forEach { acceptedWorldbooks.add(it.id) }
                } else {
                    val existingById = fetchByIdsChunked(batch.map { it.id }, assetDao::getWorldbooksByIds) { it.id }
                    val toUpsert = ArrayList<WorldbookEntity>(batch.size)
                    for (wb in batch) {
                        val local = existingById[wb.id]
                        if (local == null || wb.updatedAt >= local.updatedAt) {
                            toUpsert.add(wb)
                            acceptedWorldbooks.add(wb.id)
                        } else {
                            skippedWorldbooks.add(wb.id)
                        }
                    }
                    if (toUpsert.isNotEmpty()) {
                        assetDao.upsertWorldbooks(toUpsert)
                        worldbooksCount += toUpsert.size
                    }
                }
            }

            if (!replaceExisting && acceptedWorldbooks.isNotEmpty()) {
                acceptedWorldbooks.toList().chunked(PAGE_SIZE).forEach { chunk ->
                    assetDao.deleteEntriesForWorldbooks(chunk)
                }
            }

            streamNdjsonBatches(File(tablesDir, "worldbook_entries.ndjson"), WorldbookEntryEntity.serializer()) { batch ->
                if (replaceExisting) {
                    assetDao.upsertWorldbookEntries(batch)
                } else {
                    val lookupIds = batch
                        .filter { it.worldbookId !in acceptedWorldbooks && it.worldbookId !in skippedWorldbooks }
                        .map { it.id }
                    val existingById = fetchByIdsChunked(lookupIds, assetDao::getWorldbookEntriesByIds) { it.id }
                    val toUpsert = ArrayList<WorldbookEntryEntity>(batch.size)
                    for (entry in batch) {
                        when {
                            entry.worldbookId in acceptedWorldbooks -> toUpsert.add(entry)
                            entry.worldbookId in skippedWorldbooks -> Unit
                            else -> {
                                val local = existingById[entry.id]
                                if (local == null || entry.updatedAt >= local.updatedAt) {
                                    toUpsert.add(entry)
                                }
                            }
                        }
                    }
                    if (toUpsert.isNotEmpty()) assetDao.upsertWorldbookEntries(toUpsert)
                }
            }

            streamNdjsonBatches(File(tablesDir, "character_cards.ndjson"), CharacterCardEntity.serializer()) { batch ->
                val resolved = batch.map { resolveCharacterCardForImport(it, filesDirPath) }
                if (replaceExisting) {
                    assetDao.upsertCharacterCards(resolved)
                    cardsCount += resolved.size
                } else {
                    val existingById = fetchByIdsChunked(resolved.map { it.id }, assetDao::getCharacterCardsByIds) { it.id }
                    val toUpsert = ArrayList<CharacterCardEntity>(resolved.size)
                    for (card in resolved) {
                        val local = existingById[card.id]
                        if (local == null || card.updatedAt >= local.updatedAt) {
                            toUpsert.add(card)
                        }
                    }
                    if (toUpsert.isNotEmpty()) {
                        assetDao.upsertCharacterCards(toUpsert)
                        cardsCount += toUpsert.size
                    }
                }
            }

            streamNdjsonBatches(File(tablesDir, "personas.ndjson"), PersonaEntity.serializer()) { batch ->
                val resolved = batch.map { resolvePersonaForImport(it, filesDirPath) }
                if (replaceExisting) {
                    assetDao.upsertPersonas(resolved)
                    personasCount += resolved.size
                } else {
                    val existingById = fetchByIdsChunked(resolved.map { it.id }, assetDao::getPersonasByIds) { it.id }
                    val toUpsert = ArrayList<PersonaEntity>(resolved.size)
                    for (persona in resolved) {
                        val local = existingById[persona.id]
                        if (local == null || persona.updatedAt >= local.updatedAt) {
                            toUpsert.add(persona)
                        }
                    }
                    if (toUpsert.isNotEmpty()) {
                        assetDao.upsertPersonas(toUpsert)
                        personasCount += toUpsert.size
                    }
                }
            }

            streamNdjsonBatches(File(tablesDir, "prompt_presets.ndjson"), PromptPresetEntity.serializer()) { batch ->
                if (replaceExisting) {
                    assetDao.upsertPromptPresets(batch)
                    presetsCount += batch.size
                } else {
                    val existingById = fetchByIdsChunked(batch.map { it.id }, assetDao::getPromptPresetsByIds) { it.id }
                    val toUpsert = ArrayList<PromptPresetEntity>(batch.size)
                    for (preset in batch) {
                        val local = existingById[preset.id]
                        if (local == null || preset.updatedAt >= local.updatedAt) {
                            toUpsert.add(preset)
                        }
                    }
                    if (toUpsert.isNotEmpty()) {
                        assetDao.upsertPromptPresets(toUpsert)
                        presetsCount += toUpsert.size
                    }
                }
            }

            // User memories: LWW by id/updatedAt
            streamNdjsonBatches(File(tablesDir, "user_memories.ndjson"), UserMemoryEntity.serializer()) { batch ->
                if (replaceExisting) {
                    memDao.upsertAll(batch)
                    memoriesCount += batch.size
                } else {
                    val existingById = fetchByIdsChunked(batch.map { it.id }, memDao::getByIds) { it.id }
                    val toUpsert = batch.filter { incoming ->
                        val local = existingById[incoming.id]
                        local == null || incoming.updatedAt >= local.updatedAt
                    }
                    if (toUpsert.isNotEmpty()) {
                        memDao.upsertAll(toUpsert)
                        memoriesCount += toUpsert.size
                    }
                }
            }

            // Attachment metadata
            streamNdjsonBatches(File(tablesDir, "attachment_meta.ndjson"), AttachmentMetaEntity.serializer()) { batch ->
                val resolved = batch.map { resolveAttachmentForImport(it, filesDirPath) }
                if (replaceExisting) {
                    attDao.upsertAll(resolved)
                } else {
                    val existingById = fetchByIdsChunked(resolved.map { it.id }, attDao::getByIds) { it.id }
                    val toUpsert = resolved.filter { incoming ->
                        val local = existingById[incoming.id]
                        local == null || !File(local.localPath).isFile
                    }
                    if (toUpsert.isNotEmpty()) attDao.upsertAll(toUpsert)
                }
            }

            // Conversations: whole-conversation atomic LWW merge
            val winningConvIds = HashSet<String>()
            streamNdjsonBatches(File(tablesDir, "conversations.ndjson"), ConversationEntity.serializer()) { batch ->
                conversationsTotal += batch.size
                val resolved = batch.map { resolveConversationForImport(it, filesDirPath) }
                if (replaceExisting) {
                    chatDao.upsertConversations(resolved)
                    resolved.forEach { winningConvIds.add(it.id) }
                    conversationsApplied += resolved.size
                } else {
                    val existingById = fetchByIdsChunked(resolved.map { it.id }, chatDao::getConversationsByIds) { it.id }
                    val toUpsert = ArrayList<ConversationEntity>(resolved.size)
                    val replacedLocalConvIds = ArrayList<String>(resolved.size)
                    for (conv in resolved) {
                        val local = existingById[conv.id]
                        if (local == null || conv.updatedAt >= local.updatedAt) {
                            winningConvIds.add(conv.id)
                            if (local != null) {
                                replacedLocalConvIds.add(conv.id)
                            }
                            toUpsert.add(conv)
                        }
                    }
                    if (replacedLocalConvIds.isNotEmpty()) {
                        replacedLocalConvIds.distinct().chunked(PAGE_SIZE).forEach { chunk ->
                            // Atomically wipe ALL children of replaced conversations before reinserting
                            chatDao.deleteMessagesForConversations(chunk)
                            chatDao.deleteNovelChaptersForConversations(chunk)
                            chatDao.deleteNovelLoreForConversations(chunk)
                            chatDao.deleteMemoryLedgerForConversations(chunk)
                            chatDao.deleteMemoryEpisodesForConversations(chunk)
                            chatDao.deleteRpSaveSlotsForConversations(chunk)
                            assetDao.deleteConversationWorldbooksForConversations(chunk)
                            chatDao.deleteMemoryEmbeddingsForConversations(chunk)
                        }
                    }
                    if (toUpsert.isNotEmpty()) {
                        chatDao.upsertConversations(toUpsert)
                        conversationsApplied += toUpsert.size
                    }
                }
            }

            // Child tables: strictly filtered by winningConvIds (NEVER per-message LWW!)
            streamNdjsonBatches(
                File(tablesDir, "conversation_worldbooks.ndjson"),
                ConversationWorldbookCrossRef.serializer(),
            ) { batch ->
                val filtered = batch.filter { it.conversationId in winningConvIds }
                if (filtered.isNotEmpty()) assetDao.upsertConversationWorldbooks(filtered)
            }

            streamNdjsonBatches(File(tablesDir, "messages.ndjson"), MessageEntity.serializer()) { batch ->
                val filtered = batch
                    .filter { it.conversationId in winningConvIds }
                    .map { resolveMessageForImport(it, filesDirPath) }
                if (filtered.isNotEmpty()) {
                    chatDao.upsertMessages(filtered)
                    messagesApplied += filtered.size
                }
            }

            streamNdjsonBatches(File(tablesDir, "novel_chapters.ndjson"), NovelChapterEntity.serializer()) { batch ->
                val filtered = batch.filter { it.conversationId in winningConvIds }
                if (filtered.isNotEmpty()) chatDao.upsertNovelChapters(filtered)
            }

            streamNdjsonBatches(File(tablesDir, "novel_lore.ndjson"), NovelLoreEntity.serializer()) { batch ->
                val filtered = batch.filter { it.conversationId in winningConvIds }
                if (filtered.isNotEmpty()) chatDao.upsertNovelLoreList(filtered)
            }

            streamNdjsonBatches(File(tablesDir, "memory_ledger.ndjson"), MemoryLedgerEntity.serializer()) { batch ->
                val filtered = batch.filter { it.conversationId in winningConvIds }
                if (filtered.isNotEmpty()) chatDao.upsertMemoryLedgerList(filtered)
            }

            streamNdjsonBatches(File(tablesDir, "memory_episodes.ndjson"), MemoryEpisodeEntity.serializer()) { batch ->
                val filtered = batch.filter { it.conversationId in winningConvIds }
                if (filtered.isNotEmpty()) chatDao.upsertMemoryEpisodes(filtered)
            }

            streamNdjsonBatches(File(tablesDir, "rp_save_slots.ndjson"), RpSaveSlotEntity.serializer()) { batch ->
                val filtered = batch
                    .filter { it.conversationId in winningConvIds }
                    .map { resolveRpSaveSlotForImport(it, filesDirPath) }
                if (filtered.isNotEmpty()) chatDao.upsertRpSaveSlots(filtered)
            }
        }

        // 6. DirectImageStore metadata merge (when present and requested)
        val directImagesRestored = if (includeDirectImages) {
            restoreDirectImagesMeta(filesDir, File(tablesDir, DIRECT_IMAGES_META_FILE), replaceExisting)
        } else {
            0
        }

        // 7. Vault restoration
        val vaultRestored = if (vaultPayload != null && vaultAdapter != null) {
            vaultAdapter.applyVaultPayload(vaultPayload, replaceExisting)
        } else {
            false
        }

        BackupRestoreResult(
            formatVersion = 2,
            conversationsCount = conversationsTotal,
            conversationsOverwrittenOrInserted = conversationsApplied,
            messagesCount = messagesApplied,
            userMemoriesCount = memoriesCount,
            worldbooksCount = worldbooksCount,
            characterCardsCount = cardsCount,
            personasCount = personasCount,
            promptPresetsCount = presetsCount,
            mediaBlobsRestored = mediaBlobsRestored,
            vaultRestored = vaultRestored,
            directImagesRestored = directImagesRestored,
        )
    }

    internal fun computeTablesSha256(tablesDir: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(IO_BUFFER_BYTES)
        val lenBuf = ByteArray(8)
        val orderedNames = TABLE_FILES_IN_ORDER + DIRECT_IMAGES_META_FILE
        for (name in orderedNames) {
            val f = File(tablesDir, name)
            if (!f.isFile) continue
            md.update(name.toByteArray(StandardCharsets.UTF_8))
            md.update(0.toByte())
            val len = f.length()
            for (i in 0 until 8) {
                lenBuf[i] = ((len ushr ((7 - i) * 8)) and 0xFF).toByte()
            }
            md.update(lenBuf)
            BufferedInputStream(FileInputStream(f), IO_BUFFER_BYTES).use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
        }
        return SyncCryptoCodec.toHex(md.digest())
    }

    private fun loadAndVerifyVaultFromStage(
        stageDir: File,
        manifest: BackupManifestV2,
        passphrase: String?,
    ): VaultPayloadV2? {
        val encFile = File(stageDir, VAULT_ENC_FILE)
        val plainFile = File(stageDir, VAULT_JSON_FILE)
        return when {
            encFile.isFile -> {
                val cipherBytes = encFile.readBytes()
                manifest.vaultSha256?.let { expectedSha ->
                    val actual = SyncCryptoCodec.sha256Hex(cipherBytes)
                    if (!actual.equals(expectedSha, ignoreCase = true)) {
                        throw SecurityException("Encrypted vault SHA-256 verification failed")
                    }
                }
                val cleanPass = passphrase?.takeIf { it.isNotEmpty() }
                    ?: throw BackupPassphraseRequiredException()
                val saltHex = manifest.kdfSaltHex
                    ?: throw IllegalStateException("Encrypted backup missing kdfSaltHex in manifest")
                val salt = SyncCryptoCodec.fromHex(saltHex)
                val plainBytes = SyncCryptoCodec.decryptVaultBytes(cleanPass, salt, cipherBytes)
                AppJson.decodeFromString(VaultPayloadV2.serializer(), plainBytes.toString(StandardCharsets.UTF_8))
            }
            plainFile.isFile -> {
                val plainBytes = plainFile.readBytes()
                manifest.vaultSha256?.let { expectedSha ->
                    val actual = SyncCryptoCodec.sha256Hex(plainBytes)
                    if (!actual.equals(expectedSha, ignoreCase = true)) {
                        throw SecurityException("Vault SHA-256 verification failed")
                    }
                }
                AppJson.decodeFromString(VaultPayloadV2.serializer(), plainBytes.toString(StandardCharsets.UTF_8))
            }
            else -> null
        }
    }

    private suspend fun <T> dumpTablePaged(
        outFile: File,
        serializer: KSerializer<T>,
        fetchPage: suspend (offset: Int) -> List<T>,
    ) {
        BufferedWriter(OutputStreamWriter(FileOutputStream(outFile), StandardCharsets.UTF_8), IO_BUFFER_BYTES).use { writer ->
            var offset = 0
            while (true) {
                val rows = fetchPage(offset)
                if (rows.isEmpty()) break
                for (row in rows) {
                    writer.write(AppJson.encodeToString(serializer, row))
                    writer.write("\n")
                }
                if (rows.size < PAGE_SIZE) break
                offset += rows.size
            }
            writer.flush()
        }
    }

    private suspend fun <T> streamNdjsonBatches(
        ndjsonFile: File,
        serializer: KSerializer<T>,
        onBatch: suspend (List<T>) -> Unit,
    ) {
        if (!ndjsonFile.isFile) return
        BufferedReader(InputStreamReader(FileInputStream(ndjsonFile), StandardCharsets.UTF_8), IO_BUFFER_BYTES).use { reader ->
            val batch = ArrayList<T>(PAGE_SIZE)
            var isFirstLine = true
            while (true) {
                val rawLine = reader.readLine() ?: break
                val clean = if (isFirstLine) {
                    isFirstLine = false
                    rawLine.removePrefix("\uFEFF").trim()
                } else {
                    rawLine.trim()
                }
                if (clean.isEmpty()) continue
                batch.add(AppJson.decodeFromString(serializer, clean))
                if (batch.size >= PAGE_SIZE) {
                    onBatch(batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                onBatch(batch)
                batch.clear()
            }
        }
    }

    private fun writeFileToZip(zos: ZipOutputStream, entryName: String, file: File) {
        zos.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(FileInputStream(file), IO_BUFFER_BYTES).use { ins ->
            copyStream(ins, zos)
        }
        zos.closeEntry()
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buf = ByteArray(IO_BUFFER_BYTES)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
        }
    }

    private fun readUpTo(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private suspend inline fun <T> fetchByIdsChunked(
        ids: Iterable<String>,
        crossinline queryChunk: suspend (List<String>) -> List<T>,
        crossinline idOf: (T) -> String,
    ): Map<String, T> {
        val unique = ids.distinct()
        if (unique.isEmpty()) return emptyMap()
        val out = HashMap<String, T>(unique.size)
        for (chunk in unique.chunked(PAGE_SIZE)) {
            for (row in queryChunk(chunk)) {
                out[idOf(row)] = row
            }
        }
        return out
    }

    private fun readUtf8Bounded(input: InputStream, maxBytes: Long): String =
        com.gugachat.app.core.util.StreamZipUtils.run {
            input.readBoundedUtf8(maxBytes, "JSON backup exceeds size limit ($maxBytes bytes)")
        }

    // ==================== Path Normalization & Media Collection ====================

    private fun normalizePathAndCollect(
        rawPath: String?,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collectedRelPaths: MutableSet<String>?,
    ): String? {
        val strippedFileScheme = rawPath?.trim()?.let { p ->
            if (LocalMediaPathResolver.isAssetUri(p)) {
                p
            } else if (p.startsWith("file://", ignoreCase = true)) {
                p.substring("file://".length)
            } else {
                p
            }
        }
        val rel = LocalMediaPathResolver.toRelativePath(filesDirPath, strippedFileScheme) ?: return null
        if (collectedRelPaths != null) {
            sanitizeMediaRelPath(rel)?.let { clean ->
                if (includeDirectImages || !clean.startsWith("direct_images/")) {
                    collectedRelPaths.add(clean)
                }
            }
        }
        return rel
    }

    private fun resolvePathForImport(storedPath: String?, filesDirPath: String): String? =
        LocalMediaPathResolver.resolveAbsolutePath(filesDirPath, storedPath)

    private fun sanitizeMediaRelPath(raw: String?): String? {
        val clean = raw?.trim()?.replace('\\', '/')?.removePrefix("./")?.trimStart('/')?.takeIf { it.isNotEmpty() }
            ?: return null
        if (clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("content://", ignoreCase = true) ||
            clean.startsWith("data:", ignoreCase = true)
        ) {
            return null
        }
        val segments = clean.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        return if (ALLOWED_MEDIA_PREFIXES.any { clean.startsWith(it) }) clean else null
    }

    private fun resolveValidatedMediaFile(
        filesDir: File,
        canonicalFilesDir: File,
        relPath: String,
    ): File? {
        val clean = sanitizeMediaRelPath(relPath) ?: return null
        val file = File(filesDir, clean)
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (!canonical.path.startsWith(canonicalFilesDir.path + File.separator)) return null
        return canonical.takeIf { it.isFile }
    }

    private fun resolveDestinationMediaFile(
        filesDir: File,
        canonicalFilesDir: File,
        relPath: String,
    ): File {
        val clean = sanitizeMediaRelPath(relPath)
            ?: throw SecurityException("Disallowed media relative path: $relPath")
        val target = File(filesDir, clean)
        val canonical = target.canonicalFile
        if (!canonical.path.startsWith(canonicalFilesDir.path + File.separator)) {
            throw SecurityException("Media path escapes filesDir: $relPath")
        }
        return canonical
    }

    private fun normalizeExpressionSpritesJson(
        rawJson: String,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>?,
        forExport: Boolean,
    ): String {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || trimmed == "{}") return rawJson
        val map = runCatching { AppJson.decodeFromString(STRING_MAP_SERIALIZER, trimmed) }.getOrNull()
            ?: return rawJson
        if (map.isEmpty()) return rawJson
        val updated = LinkedHashMap<String, String>(map.size)
        for ((k, v) in map) {
            val next = if (forExport) {
                normalizePathAndCollect(v, filesDirPath, includeDirectImages, collected)
            } else {
                resolvePathForImport(v, filesDirPath)
            }
            if (!next.isNullOrBlank()) {
                updated[k] = next
            }
        }
        return AppJson.encodeToString(STRING_MAP_SERIALIZER, updated)
    }

    private fun transformRpCast(
        cast: List<RpCharacter>,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>?,
        forExport: Boolean,
    ): List<RpCharacter> {
        if (cast.isEmpty()) return cast
        return cast.map { c ->
            val nextAvatar = if (forExport) {
                normalizePathAndCollect(c.avatarPath, filesDirPath, includeDirectImages, collected)
            } else {
                resolvePathForImport(c.avatarPath, filesDirPath)
            }
            val nextSprite = if (forExport) {
                normalizePathAndCollect(c.spritePath, filesDirPath, includeDirectImages, collected)
            } else {
                resolvePathForImport(c.spritePath, filesDirPath)
            }
            val nextExpr = if (c.expressionSprites.isEmpty()) {
                c.expressionSprites
            } else {
                val out = LinkedHashMap<String, String>(c.expressionSprites.size)
                for ((k, v) in c.expressionSprites) {
                    val normV = if (forExport) {
                        normalizePathAndCollect(v, filesDirPath, includeDirectImages, collected)
                    } else {
                        resolvePathForImport(v, filesDirPath)
                    }
                    if (!normV.isNullOrBlank()) out[k] = normV
                }
                out
            }
            c.copy(avatarPath = nextAvatar, spritePath = nextSprite, expressionSprites = nextExpr)
        }
    }

    private fun transformRpState(
        state: RpGameState,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>?,
        forExport: Boolean,
    ): RpGameState {
        val nextScene = if (forExport) {
            normalizePathAndCollect(state.sceneImagePath, filesDirPath, includeDirectImages, collected)
        } else {
            resolvePathForImport(state.sceneImagePath, filesDirPath)
        }
        val nextCgs = if (state.unlockedCgs.isEmpty()) {
            state.unlockedCgs
        } else {
            state.unlockedCgs.map { cg ->
                val nextImg = if (forExport) {
                    normalizePathAndCollect(cg.imagePath, filesDirPath, includeDirectImages, collected) ?: cg.imagePath
                } else {
                    resolvePathForImport(cg.imagePath, filesDirPath) ?: cg.imagePath
                }
                cg.copy(imagePath = nextImg)
            }
        }
        return state.copy(sceneImagePath = nextScene, unlockedCgs = nextCgs)
    }

    private fun transformRpConfig(
        rp: RpConfig,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>?,
        forExport: Boolean,
    ): RpConfig = rp.copy(
        cast = transformRpCast(rp.cast, filesDirPath, includeDirectImages, collected, forExport),
        state = transformRpState(rp.state, filesDirPath, includeDirectImages, collected, forExport),
    )

    private fun transformVariantsJson(
        rawJson: String,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>?,
        forExport: Boolean,
    ): String {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return rawJson
        val variants = runCatching { AppJson.decodeFromString(VARIANT_LIST_SERIALIZER, trimmed) }.getOrNull()
            ?: return rawJson
        if (variants.isEmpty()) return rawJson
        var changed = false
        val updated = variants.map { v ->
            val nextState = v.rpStateSnapshot?.let {
                changed = true
                transformRpState(it, filesDirPath, includeDirectImages, collected, forExport)
            }
            val nextCast = v.rpCastSnapshot?.let {
                changed = true
                transformRpCast(it, filesDirPath, includeDirectImages, collected, forExport)
            }
            if (nextState !== v.rpStateSnapshot || nextCast !== v.rpCastSnapshot) {
                v.copy(rpStateSnapshot = nextState, rpCastSnapshot = nextCast)
            } else {
                v
            }
        }
        return if (changed) AppJson.encodeToString(VARIANT_LIST_SERIALIZER, updated) else rawJson
    }

    private fun normalizeCharacterCardForExport(
        card: CharacterCardEntity,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>,
    ): CharacterCardEntity {
        val nextAvatar = normalizePathAndCollect(card.avatarPath, filesDirPath, includeDirectImages, collected)
        val nextSprite = normalizePathAndCollect(card.spritePath, filesDirPath, includeDirectImages, collected)
        val nextExpr = normalizeExpressionSpritesJson(card.expressionSpritesJson, filesDirPath, includeDirectImages, collected, forExport = true)
        val nextCast = card.castJson.trim().let { raw ->
            if (raw.isEmpty() || raw == "[]") raw
            else runCatching {
                val list = AppJson.decodeFromString(RP_CHAR_LIST_SERIALIZER, raw)
                AppJson.encodeToString(
                    RP_CHAR_LIST_SERIALIZER,
                    transformRpCast(list, filesDirPath, includeDirectImages, collected, forExport = true),
                )
            }.getOrDefault(raw)
        }
        return card.copy(
            avatarPath = nextAvatar,
            spritePath = nextSprite,
            expressionSpritesJson = nextExpr,
            castJson = nextCast,
        )
    }

    private fun resolveCharacterCardForImport(
        card: CharacterCardEntity,
        filesDirPath: String,
    ): CharacterCardEntity {
        val nextAvatar = resolvePathForImport(card.avatarPath, filesDirPath)
        val nextSprite = resolvePathForImport(card.spritePath, filesDirPath)
        val nextExpr = normalizeExpressionSpritesJson(card.expressionSpritesJson, filesDirPath, false, null, forExport = false)
        val nextCast = card.castJson.trim().let { raw ->
            if (raw.isEmpty() || raw == "[]") raw
            else runCatching {
                val list = AppJson.decodeFromString(RP_CHAR_LIST_SERIALIZER, raw)
                AppJson.encodeToString(
                    RP_CHAR_LIST_SERIALIZER,
                    transformRpCast(list, filesDirPath, false, null, forExport = false),
                )
            }.getOrDefault(raw)
        }
        return card.copy(
            avatarPath = nextAvatar,
            spritePath = nextSprite,
            expressionSpritesJson = nextExpr,
            castJson = nextCast,
        )
    }

    private fun normalizePersonaForExport(
        persona: PersonaEntity,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>,
    ): PersonaEntity = persona.copy(
        avatarPath = normalizePathAndCollect(persona.avatarPath, filesDirPath, includeDirectImages, collected),
    )

    private fun resolvePersonaForImport(
        persona: PersonaEntity,
        filesDirPath: String,
    ): PersonaEntity = persona.copy(
        avatarPath = resolvePathForImport(persona.avatarPath, filesDirPath),
    )

    private fun normalizeConversationForExport(
        conv: ConversationEntity,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>,
    ): ConversationEntity {
        val nextNovelJson = conv.novelJson?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                val cfg = AppJson.decodeFromString(NovelConfig.serializer(), raw)
                val nextCover = normalizePathAndCollect(cfg.coverImagePath, filesDirPath, includeDirectImages, collected)
                AppJson.encodeToString(NovelConfig.serializer(), cfg.copy(coverImagePath = nextCover))
            }.getOrDefault(raw)
        }
        val nextRpJson = conv.rpJson?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                val rp = AppJson.decodeFromString(RpConfig.serializer(), raw)
                AppJson.encodeToString(
                    RpConfig.serializer(),
                    transformRpConfig(rp, filesDirPath, includeDirectImages, collected, forExport = true),
                )
            }.getOrDefault(raw)
        }
        return conv.copy(novelJson = nextNovelJson, rpJson = nextRpJson)
    }

    private fun resolveConversationForImport(
        conv: ConversationEntity,
        filesDirPath: String,
    ): ConversationEntity {
        val nextNovelJson = conv.novelJson?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                val cfg = AppJson.decodeFromString(NovelConfig.serializer(), raw)
                val nextCover = resolvePathForImport(cfg.coverImagePath, filesDirPath)
                AppJson.encodeToString(NovelConfig.serializer(), cfg.copy(coverImagePath = nextCover))
            }.getOrDefault(raw)
        }
        val nextRpJson = conv.rpJson?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                val rp = AppJson.decodeFromString(RpConfig.serializer(), raw)
                AppJson.encodeToString(
                    RpConfig.serializer(),
                    transformRpConfig(rp, filesDirPath, false, null, forExport = false),
                )
            }.getOrDefault(raw)
        }
        return conv.copy(novelJson = nextNovelJson, rpJson = nextRpJson)
    }

    private fun normalizeMessageForExport(
        msg: MessageEntity,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>,
    ): MessageEntity {
        val safeStatus = if (msg.status.equals("streaming", ignoreCase = true)) "aborted" else msg.status
        val nextVariants = transformVariantsJson(msg.variantsJson, filesDirPath, includeDirectImages, collected, forExport = true)
        return msg.copy(status = safeStatus, variantsJson = nextVariants)
    }

    private fun resolveMessageForImport(
        msg: MessageEntity,
        filesDirPath: String,
    ): MessageEntity {
        val safeStatus = if (msg.status.equals("streaming", ignoreCase = true)) "aborted" else msg.status
        val nextVariants = transformVariantsJson(msg.variantsJson, filesDirPath, false, null, forExport = false)
        return msg.copy(status = safeStatus, variantsJson = nextVariants)
    }

    private fun normalizeRpSaveSlotForExport(
        slot: RpSaveSlotEntity,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>,
    ): RpSaveSlotEntity {
        val nextRpConfigJson = slot.rpConfigJson.trim().let { raw ->
            if (raw.isEmpty() || raw == "{}") raw
            else runCatching {
                val rp = AppJson.decodeFromString(RpConfig.serializer(), raw)
                AppJson.encodeToString(
                    RpConfig.serializer(),
                    transformRpConfig(rp, filesDirPath, includeDirectImages, collected, forExport = true),
                )
            }.getOrDefault(raw)
        }
        val nextSnapshotMsgs = slot.activeMessagesSnapshotJson.trim().let { raw ->
            if (raw.isEmpty() || raw == "[]") raw
            else runCatching {
                val list = AppJson.decodeFromString(CHAT_MSG_LIST_SERIALIZER, raw)
                val norm = list.map { m ->
                    val st = if (m.status.equals("streaming", ignoreCase = true)) "aborted" else m.status
                    val vars = m.variants.map { v ->
                        v.copy(
                            rpStateSnapshot = v.rpStateSnapshot?.let {
                                transformRpState(it, filesDirPath, includeDirectImages, collected, forExport = true)
                            },
                            rpCastSnapshot = v.rpCastSnapshot?.let {
                                transformRpCast(it, filesDirPath, includeDirectImages, collected, forExport = true)
                            },
                        )
                    }
                    m.copy(status = st, variants = vars)
                }
                AppJson.encodeToString(CHAT_MSG_LIST_SERIALIZER, norm)
            }.getOrDefault(raw)
        }
        return slot.copy(rpConfigJson = nextRpConfigJson, activeMessagesSnapshotJson = nextSnapshotMsgs)
    }

    private fun resolveRpSaveSlotForImport(
        slot: RpSaveSlotEntity,
        filesDirPath: String,
    ): RpSaveSlotEntity {
        val nextRpConfigJson = slot.rpConfigJson.trim().let { raw ->
            if (raw.isEmpty() || raw == "{}") raw
            else runCatching {
                val rp = AppJson.decodeFromString(RpConfig.serializer(), raw)
                AppJson.encodeToString(
                    RpConfig.serializer(),
                    transformRpConfig(rp, filesDirPath, false, null, forExport = false),
                )
            }.getOrDefault(raw)
        }
        val nextSnapshotMsgs = slot.activeMessagesSnapshotJson.trim().let { raw ->
            if (raw.isEmpty() || raw == "[]") raw
            else runCatching {
                val list = AppJson.decodeFromString(CHAT_MSG_LIST_SERIALIZER, raw)
                val resolved = list.map { m ->
                    val st = if (m.status.equals("streaming", ignoreCase = true)) "aborted" else m.status
                    val vars = m.variants.map { v ->
                        v.copy(
                            rpStateSnapshot = v.rpStateSnapshot?.let {
                                transformRpState(it, filesDirPath, false, null, forExport = false)
                            },
                            rpCastSnapshot = v.rpCastSnapshot?.let {
                                transformRpCast(it, filesDirPath, false, null, forExport = false)
                            },
                        )
                    }
                    m.copy(status = st, variants = vars)
                }
                AppJson.encodeToString(CHAT_MSG_LIST_SERIALIZER, resolved)
            }.getOrDefault(raw)
        }
        return slot.copy(rpConfigJson = nextRpConfigJson, activeMessagesSnapshotJson = nextSnapshotMsgs)
    }

    private fun normalizeAttachmentForExport(
        att: AttachmentMetaEntity,
        filesDirPath: String,
        includeDirectImages: Boolean,
        collected: MutableSet<String>,
    ): AttachmentMetaEntity {
        val nextLocal = normalizePathAndCollect(att.localPath, filesDirPath, includeDirectImages, collected).orEmpty()
        val nextText = normalizePathAndCollect(att.textPath, filesDirPath, includeDirectImages, collected).orEmpty()
        return att.copy(localPath = nextLocal, textPath = nextText)
    }

    private fun resolveAttachmentForImport(
        att: AttachmentMetaEntity,
        filesDirPath: String,
    ): AttachmentMetaEntity {
        val nextLocal = resolvePathForImport(att.localPath, filesDirPath).orEmpty()
        val nextText = resolvePathForImport(att.textPath, filesDirPath).orEmpty()
        return att.copy(localPath = nextLocal, textPath = nextText)
    }

    private fun exportDirectImagesMeta(
        filesDir: File,
        outFile: File,
        collectedRelPaths: MutableSet<String>,
    ) {
        val dir = File(filesDir, "direct_images")
        if (!dir.isDirectory) return
        val genFile = File(dir, "generations.json")
        val presetsFile = File(dir, "presets.json")

        val generations = if (genFile.isFile) {
            runCatching {
                AppJson.decodeFromString(DIRECT_GEN_LIST_SERIALIZER, genFile.readText(StandardCharsets.UTF_8))
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val presets = if (presetsFile.isFile) {
            runCatching {
                AppJson.decodeFromString(DIRECT_PRESET_LIST_SERIALIZER, presetsFile.readText(StandardCharsets.UTF_8))
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        if (generations.isEmpty() && presets.isEmpty()) return

        val payload = DirectImagesMetaPayload(generations = generations, presets = presets)
        outFile.writeText(AppJson.encodeToString(DirectImagesMetaPayload.serializer(), payload), StandardCharsets.UTF_8)

        for (gen in generations) {
            for (asset in gen.images) {
                val rel = "direct_images/${gen.id}_${asset.index}.png"
                if (File(filesDir, rel).isFile) {
                    collectedRelPaths.add(rel)
                }
            }
        }
    }

    private fun restoreDirectImagesMeta(
        filesDir: File,
        metaFile: File,
        replaceExisting: Boolean,
    ): Int {
        if (!metaFile.isFile) return 0
        val payload = runCatching {
            AppJson.decodeFromString(DirectImagesMetaPayload.serializer(), metaFile.readText(StandardCharsets.UTF_8))
        }.getOrNull() ?: return 0

        val dir = File(filesDir, "direct_images").apply { mkdirs() }
        val genFile = File(dir, "generations.json")
        val presetsFile = File(dir, "presets.json")

        val mergedGens = if (replaceExisting || !genFile.isFile) {
            payload.generations
        } else {
            val localGens = runCatching {
                AppJson.decodeFromString(DIRECT_GEN_LIST_SERIALIZER, genFile.readText(StandardCharsets.UTF_8))
            }.getOrDefault(emptyList())
            val byId = LinkedHashMap<String, OnlineImageGenerationDto>()
            for (g in localGens) byId[g.id] = g
            for (incoming in payload.generations) {
                val local = byId[incoming.id]
                val incTs = incoming.updated_at ?: incoming.created_at
                val locTs = local?.updated_at ?: local?.created_at ?: Long.MIN_VALUE
                if (local == null || incTs >= locTs) {
                    byId[incoming.id] = incoming
                }
            }
            byId.values.sortedByDescending { it.created_at }
        }
        writeTextAtomic(genFile, AppJson.encodeToString(DIRECT_GEN_LIST_SERIALIZER, mergedGens))

        val mergedPresets = if (replaceExisting || !presetsFile.isFile) {
            payload.presets
        } else {
            val localPresets = runCatching {
                AppJson.decodeFromString(DIRECT_PRESET_LIST_SERIALIZER, presetsFile.readText(StandardCharsets.UTF_8))
            }.getOrDefault(emptyList())
            val byId = LinkedHashMap<String, OnlineImagePresetDto>()
            for (p in localPresets) byId[p.id] = p
            for (incoming in payload.presets) {
                val local = byId[incoming.id]
                if (local == null || incoming.updatedAtSafe() >= local.updatedAtSafe()) {
                    byId[incoming.id] = incoming
                }
            }
            byId.values.sortedByDescending { it.created_at }
        }
        writeTextAtomic(presetsFile, AppJson.encodeToString(DIRECT_PRESET_LIST_SERIALIZER, mergedPresets))
        return payload.generations.size
    }

    private fun OnlineImagePresetDto.updatedAtSafe(): Long =
        if (updated_at > 0L) updated_at else created_at

    private fun writeTextAtomic(target: File, content: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.${System.nanoTime()}.tmp")
        var ok = false
        try {
            tmp.writeText(content, StandardCharsets.UTF_8)
            StreamZipUtils.atomicReplace(tmp, target)
            ok = true
        } finally {
            if (!ok || tmp.exists()) tmp.delete()
        }
    }
}
