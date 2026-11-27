package com.gugachat.app.ui.studio

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.ConversationWorldbookCrossRef
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.data.local.decodedAltGreetings
import com.gugachat.app.data.local.decodedCast
import com.gugachat.app.data.local.decodedExpressionSprites
import com.gugachat.app.data.local.decodedRegexScripts
import com.gugachat.app.data.local.decodedVisualTheme
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextBuilder
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.LegacyAssetHarvester
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.ParsedWorldbookV4
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.RpCardCodec
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpPresetTemplate
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.RpVisualTheme
import com.gugachat.app.domain.chat.TavernAssetCodec
import com.gugachat.app.data.chat.RetrievalCoordinator
import com.gugachat.app.data.chat.VectorMemoryIndex
import com.gugachat.app.domain.chat.TokenEstimator
import com.gugachat.app.domain.chat.VectorIndexProgress
import com.gugachat.app.domain.chat.WorldbookEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Pure-JVM routing policy deciding which Creative Studio top-level tab renders on first entry.
 */
object StudioRoutingPolicy {
    const val TAB_CHARACTERS = 0
    const val TAB_SETTINGS = 1
    const val TAB_ATELIER = 2

    fun resolveInitialSubtab(
        savedSubtab: Int?,
        imageWorksCount: Int,
        directImageWorksCount: Int,
        hasCustomCards: Boolean,
    ): Int {
        if (savedSubtab != null && savedSubtab in TAB_CHARACTERS..TAB_ATELIER) {
            return savedSubtab
        }
        val hasAnyArtwork = imageWorksCount > 0 || directImageWorksCount > 0
        return if (hasAnyArtwork && !hasCustomCards) TAB_ATELIER else TAB_CHARACTERS
    }
}

data class AssetHubUiState(
    val activeStudioSubtab: Int = StudioRoutingPolicy.TAB_CHARACTERS,
    val initialSubtabResolved: Boolean = false,
    val characterCards: List<CharacterCardEntity> = emptyList(),
    val worldbooks: List<WorldbookEntity> = emptyList(),
    val worldbookVectorReady: Map<String, Boolean> = emptyMap(),
    val personas: List<PersonaEntity> = emptyList(),
    val defaultPersona: PersonaEntity? = null,
    val promptPresets: List<PromptPresetEntity> = emptyList(),
    val vectorIndexProgress: VectorIndexProgress = VectorIndexProgress(),
    val activeWorldbookId: String? = null,
    val activeWorldbookEntries: List<WorldbookEntryEntity> = emptyList(),
    val activePresetId: String? = null,
    val legacyLoreSessionCount: Int = 0,
    val isBusy: Boolean = false,
    val isLaunching: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AssetHubViewModel(private val container: AppContainer) : ViewModel() {

    private val database = container.database
    private val assetDao = database.assetDao()
    private val chatDao = database.chatDao()
    private val embeddingDao = database.memoryEmbeddingDao()
    private val filesDir: File get() = container.appContext.filesDir

    private val _ui = MutableStateFlow(AssetHubUiState())
    val ui: StateFlow<AssetHubUiState> = _ui.asStateFlow()

    private val activeWorldbookIdFlow = MutableStateFlow<String?>(null)

    @Volatile
    private var userManuallySelectedSubtab = false

    private val isZh: Boolean
        get() = container.settingsState.value.language == AppLanguage.ZH

    init {
        viewModelScope.launch {
            assetDao.observeCharacterCards().collect { cards ->
                _ui.update { it.copy(characterCards = cards) }
                if (cards.isNotEmpty() && !userManuallySelectedSubtab) {
                    val saved = container.settingsState.value.lastStudioSubtab
                    if (saved == null || saved !in StudioRoutingPolicy.TAB_CHARACTERS..StudioRoutingPolicy.TAB_ATELIER) {
                        _ui.update { it.copy(activeStudioSubtab = StudioRoutingPolicy.TAB_CHARACTERS) }
                    }
                }
            }
        }

        viewModelScope.launch {
            assetDao.observeWorldbooks().collect { books ->
                _ui.update { it.copy(worldbooks = books) }
                refreshWorldbookVectorStatus(books)
            }
        }

        viewModelScope.launch {
            assetDao.observePersonas().collect { personas ->
                _ui.update { cur ->
                    cur.copy(
                        personas = personas,
                        defaultPersona = personas.firstOrNull { it.isDefault },
                    )
                }
            }
        }

        viewModelScope.launch {
            assetDao.observePromptPresets().collect { presets ->
                _ui.update { it.copy(promptPresets = presets) }
            }
        }

        viewModelScope.launch {
            activeWorldbookIdFlow
                .flatMapLatest { wbId ->
                    if (wbId.isNullOrBlank()) flowOf(emptyList())
                    else assetDao.observeWorldbookEntries(wbId)
                }
                .collect { entries ->
                    _ui.update { it.copy(activeWorldbookEntries = entries) }
                }
        }

        viewModelScope.launch {
            container.retrievalCoordinator.indexProgress.collect { progress ->
                _ui.update { it.copy(vectorIndexProgress = progress) }
                if (!progress.running) {
                    refreshWorldbookVectorStatus(_ui.value.worldbooks)
                }
            }
        }

        viewModelScope.launch {
            container.settingsState.collect { st ->
                val saved = st.lastStudioSubtab
                if (!userManuallySelectedSubtab && saved != null && saved in StudioRoutingPolicy.TAB_CHARACTERS..StudioRoutingPolicy.TAB_ATELIER) {
                    _ui.update { it.copy(activeStudioSubtab = saved, initialSubtabResolved = true) }
                }
                refreshWorldbookVectorStatus(_ui.value.worldbooks)
            }
        }

        viewModelScope.launch {
            container.imageWorksTotal.collect { count ->
                if (!userManuallySelectedSubtab && !_ui.value.initialSubtabResolved) {
                    val saved = container.settingsStore.currentSettings().lastStudioSubtab
                    if (saved == null && count > 0 && _ui.value.characterCards.isEmpty()) {
                        _ui.update { it.copy(activeStudioSubtab = StudioRoutingPolicy.TAB_ATELIER) }
                    }
                }
            }
        }

        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { container.settingsStore.currentSettings() }
            val directCount = withContext(Dispatchers.IO) {
                runCatching { container.directImageStore.list(search = null, filter = "all").all_total ?: 0 }
                    .getOrDefault(0)
            }
            val cards = withContext(Dispatchers.IO) { assetDao.getAllCharacterCards() }
            if (!userManuallySelectedSubtab) {
                val resolved = StudioRoutingPolicy.resolveInitialSubtab(
                    savedSubtab = settings.lastStudioSubtab,
                    imageWorksCount = container.imageWorksTotal.value,
                    directImageWorksCount = directCount,
                    hasCustomCards = cards.isNotEmpty(),
                )
                _ui.update {
                    it.copy(
                        activeStudioSubtab = resolved,
                        initialSubtabResolved = true,
                    )
                }
            }

            // Delay slightly so the initial Studio frame stays completely jank-free
            delay(180L)
            runCatching {
                withContext(Dispatchers.IO) {
                    LegacyAssetHarvester.harvestIfNeeded(chatDao, assetDao)
                }
            }
            refreshLegacyLoreSessionCount()
        }
    }

    fun selectStudioSubtab(index: Int) {
        val clamped = index.coerceIn(StudioRoutingPolicy.TAB_CHARACTERS, StudioRoutingPolicy.TAB_ATELIER)
        userManuallySelectedSubtab = true
        _ui.update { it.copy(activeStudioSubtab = clamped, initialSubtabResolved = true) }
        viewModelScope.launch {
            container.settingsStore.setLastStudioSubtab(clamped)
        }
    }

    // ==================== Character Card Import & CRUD ====================

    fun importCharacterCardUri(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true) }
            try {
                val imported = container.assetImportCoordinator.importUri(
                    uri = uri,
                    contentResolver = contentResolver,
                    persistRegexAsPreset = true,
                )
                val summary = container.assetImportCoordinator.formatSettingImportSummary(imported, isZh)
                    ?: if (isZh) "已导入资产" else "Asset imported"
                container.toastController.show(summary)
            } catch (e: Throwable) {
                container.toastController.show(
                    e.message ?: if (isZh) "角色卡导入失败" else "Card import failed",
                    isError = true,
                )
            } finally {
                _ui.update { it.copy(isBusy = false) }
            }
        }
    }

    fun toggleCardFavorite(cardId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val card = assetDao.getCharacterCardById(cardId) ?: return@withContext
                assetDao.upsertCharacterCard(
                    card.copy(
                        favorite = !card.favorite,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun deleteCharacterCard(cardId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                assetDao.deleteCharacterCard(cardId)
            }
            container.toastController.show(if (isZh) "已从角色库移除" else "Removed from Character Hub")
        }
    }

    fun createCustomCharacterCard(
        name: String,
        description: String,
        personality: String,
        scenario: String,
        firstMes: String,
        playStyle: RpPlayStyle = RpPlayStyle.chengguang,
        tags: List<String> = emptyList(),
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cardId = UUID.randomUUID().toString()
            val leadChar = RpCharacter(
                id = "npc_${cardId.take(8)}",
                name = cleanName,
                role = "companion",
                appearance = description.trim(),
                personality = personality.trim(),
                background = description.trim(),
                inScene = true,
                stageSlot = "center",
            )
            val card = CharacterCardEntity(
                id = cardId,
                name = cleanName,
                creator = if (isZh) "原创" else "Original",
                characterVersion = "1.0",
                tagsJson = AppJson.encodeToString(tags.ifEmpty { listOf(if (isZh) "原创" else "Original") }),
                playStyle = playStyle.name,
                description = description.trim(),
                personality = personality.trim(),
                scenario = scenario.trim(),
                firstMes = firstMes.trim(),
                castJson = AppJson.encodeToString(listOf(leadChar)),
                sourceSpec = "gugachat_original",
                contentFingerprint = LegacyAssetHarvester.computeCardFingerprint(
                    title = cleanName,
                    greeting = firstMes,
                    leadDesc = description,
                ),
                createdAt = now,
                updatedAt = now,
            )
            withContext(Dispatchers.IO) {
                assetDao.upsertCharacterCard(card)
            }
            container.toastController.show(
                if (isZh) "已创建原创角色「$cleanName」" else "Created \"$cleanName\"",
            )
        }
    }

    fun attachCardPortraitBytes(cardId: String, imageBytes: ByteArray) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val card = assetDao.getCharacterCardById(cardId) ?: return@withContext
                    val ext = if (RpCardCodec.isPngBytes(imageBytes)) "png" else "jpg"
                    val relPath = LocalMediaPathResolver.saveRpMediaBytes(
                        filesDir = filesDir,
                        bytes = imageBytes,
                        prefix = "card_ai_portrait",
                        ext = ext,
                        shortUuid = true,
                    ).first
                    val existingCast = card.decodedCast()
                    val leadIdx = existingCast.indexOfFirst { !it.isPlayer }.takeIf { it >= 0 } ?: 0
                    val nextCast = if (existingCast.isNotEmpty()) {
                        existingCast.mapIndexed { idx, ch ->
                            if (idx == leadIdx) ch.copy(avatarPath = relPath, spritePath = relPath) else ch
                        }
                    } else {
                        listOf(
                            RpCharacter(
                                id = "npc_${card.id.take(8)}",
                                name = card.name.ifBlank { "主角" },
                                role = "companion",
                                background = card.description,
                                personality = card.personality,
                                avatarPath = relPath,
                                spritePath = relPath,
                                stageSlot = "center",
                            ),
                        )
                    }
                    assetDao.upsertCharacterCard(
                        card.copy(
                            avatarPath = relPath,
                            spritePath = relPath,
                            castJson = AppJson.encodeToString(nextCast),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                container.toastController.show(
                    if (isZh) "已将 AI 立绘绑定到角色卡" else "Portrait attached to character card",
                )
            } catch (e: Throwable) {
                container.toastController.show(
                    e.message ?: if (isZh) "立绘保存失败" else "Failed to attach portrait",
                    isError = true,
                )
            }
        }
    }

    fun attachCardPortraitUri(cardId: String, uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null || bytes.isEmpty()) {
                container.toastController.show(if (isZh) "无法读取图片" else "Invalid image", isError = true)
                return@launch
            }
            val normalizedBytes = withContext(Dispatchers.IO) {
                if (RpCardCodec.isPngBytes(bytes)) bytes else RpCardCodec.transcodeToPngBytes(bytes, maxSide = 1024) ?: bytes
            }
            attachCardPortraitBytes(cardId, normalizedBytes)
        }
    }

    // ==================== Personas CRUD ====================

    fun savePersona(
        id: String?,
        name: String,
        description: String,
        injectionPosition: Int = 0,
        injectionDepth: Int = 4,
        makeDefault: Boolean = false,
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                val existing = id?.let { assetDao.getPersonaById(it) }
                val personaId = existing?.id ?: "persona_${UUID.randomUUID().toString().take(12)}"
                val hasAnyDefault = assetDao.getDefaultPersona() != null
                val shouldDefault = makeDefault || !hasAnyDefault || (existing?.isDefault == true)
                val entity = PersonaEntity(
                    id = personaId,
                    name = cleanName,
                    avatarPath = existing?.avatarPath,
                    description = description.trim(),
                    injectionPosition = injectionPosition.coerceIn(0, 1),
                    injectionDepth = injectionDepth.coerceIn(0, 24),
                    isDefault = shouldDefault,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                database.withTransaction {
                    assetDao.upsertPersona(entity)
                    if (shouldDefault) {
                        assetDao.setDefaultPersona(personaId)
                    }
                }
            }
            container.toastController.show(
                if (isZh) "已保存自设「$cleanName」" else "Saved persona \"$cleanName\"",
            )
        }
    }

    fun setDefaultPersona(personaId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                assetDao.setDefaultPersona(personaId)
            }
        }
    }

    fun deletePersona(personaId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                assetDao.deletePersona(personaId)
            }
        }
    }

    // ==================== Worldbook / Preset / Regex Import & CRUD ====================

    fun importSettingAssetUri(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true) }
            try {
                val imported = container.assetImportCoordinator.importSettingAssetUri(uri, contentResolver)
                val resultMsg = container.assetImportCoordinator.formatSettingImportSummary(imported, isZh)
                    ?: error(if (isZh) "未识别的世界书/预设/正则格式" else "Unrecognized setting JSON format")
                container.toastController.show(resultMsg)
            } catch (e: Throwable) {
                container.toastController.show(
                    e.message ?: if (isZh) "设定导入失败" else "Setting import failed",
                    isError = true,
                )
            } finally {
                _ui.update { it.copy(isBusy = false) }
            }
        }
    }

    fun openWorldbookDetail(worldbookId: String?) {
        activeWorldbookIdFlow.value = worldbookId
        _ui.update { it.copy(activeWorldbookId = worldbookId) }
    }

    fun openPresetDetail(presetId: String?) {
        _ui.update { it.copy(activePresetId = presetId) }
    }

    fun createBlankWorldbook(name: String, description: String = "") {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val now = System.currentTimeMillis()
        val wb = WorldbookEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            description = description.trim(),
            sourceFormat = "gugachat",
            globalEnabled = false,
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                assetDao.upsertWorldbook(wb)
            }
            openWorldbookDetail(wb.id)
        }
    }

    fun toggleWorldbookGlobalEnabled(worldbookId: String, enabled: Boolean) {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                val wb = assetDao.getWorldbookById(worldbookId) ?: return@withContext null
                assetDao.upsertWorldbook(
                    wb.copy(
                        globalEnabled = enabled,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                if (enabled) assetDao.getWorldbookEntries(worldbookId) else emptyList()
            }
            if (enabled && !entries.isNullOrEmpty()) {
                container.retrievalCoordinator.scheduleWorldbookIndexing(
                    scope = viewModelScope,
                    worldbookId = worldbookId,
                    entries = entries,
                )
            }
        }
    }

    fun updateWorldbookMeta(
        worldbookId: String,
        name: String,
        description: String,
        scanDepth: Int,
        tokenBudget: Int,
        recursiveScanning: Boolean,
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val wb = assetDao.getWorldbookById(worldbookId) ?: return@withContext
                assetDao.upsertWorldbook(
                    wb.copy(
                        name = cleanName,
                        description = description.trim(),
                        scanDepth = scanDepth.coerceIn(1, 30),
                        tokenBudget = tokenBudget.coerceIn(200, 16_000),
                        recursiveScanning = recursiveScanning,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            container.toastController.show(if (isZh) "已更新世界书配置" else "Updated worldbook settings")
        }
    }

    fun reindexWorldbook(worldbookId: String) {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { assetDao.getWorldbookEntries(worldbookId) }
            val job = container.retrievalCoordinator.scheduleWorldbookIndexing(
                scope = viewModelScope,
                worldbookId = worldbookId,
                entries = entries,
            )
            if (job == null) {
                container.toastController.show(
                    if (isZh) "当前检索模式未启用向量引擎" else "Vector indexing requires local or remote embedding mode",
                )
            } else {
                container.toastController.show(
                    if (isZh) "已在后台重建词条向量索引" else "Re-indexing worldbook entries in background",
                )
            }
        }
    }

    fun deleteWorldbook(worldbookId: String) {
        viewModelScope.launch {
            val entryIds = withContext(Dispatchers.IO) {
                val ids = assetDao.getWorldbookEntries(worldbookId).map { it.id }
                assetDao.deleteWorldbookCascade(worldbookId)
                ids
            }
            for (eid in entryIds) {
                runCatching {
                    container.retrievalCoordinator.deleteOwnerVector(
                        ownerType = RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY,
                        ownerId = eid,
                        conversationId = VectorMemoryIndex.worldbookScopeId(worldbookId),
                    )
                }
            }
            if (_ui.value.activeWorldbookId == worldbookId) {
                openWorldbookDetail(null)
            }
            container.toastController.show(if (isZh) "已删除世界书" else "Worldbook deleted")
        }
    }

    /**
     * Saves a 7-field edited [WorldbookEntryEntity] while preserving any untouched V2/V3 metadata
     * (`uid`, regex/wholeWord flags, probability, insertionOrder, outletRole, group, etc.) losslessly.
     */
    fun saveWorldbookEntry(
        worldbookId: String,
        entryId: String?,
        title: String,
        content: String,
        primaryKeys: List<String>,
        secondaryKeys: List<String>,
        selectiveLogic: Int,
        constant: Boolean,
        enabled: Boolean,
        position: Int,
        depth: Int,
        sticky: Int,
        cooldown: Int,
    ) {
        val cleanContent = content.trim()
        val cleanKeys = primaryKeys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val cleanSecondary = secondaryKeys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val resolvedTitle = title.trim().ifBlank {
            cleanKeys.firstOrNull() ?: if (isZh) "未命名词条" else "Untitled Entry"
        }
        viewModelScope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val existing = entryId?.let { assetDao.getWorldbookEntryById(it) }
                val allExisting = assetDao.getWorldbookEntries(worldbookId)
                val nextUid = existing?.uid ?: ((allExisting.maxOfOrNull { it.uid } ?: 0) + 1)
                val estTokens = TokenEstimator.estimateItem(
                    title = resolvedTitle,
                    body = cleanContent,
                    overhead = 8,
                    minTokens = 8,
                )
                val updatedEntry = if (existing != null) {
                    existing.copy(
                        title = resolvedTitle,
                        content = cleanContent,
                        keysJson = AppJson.encodeToString(cleanKeys),
                        secondaryKeysJson = AppJson.encodeToString(cleanSecondary),
                        selectiveLogic = selectiveLogic.coerceIn(0, 3),
                        constant = constant,
                        enabled = enabled,
                        position = position.coerceIn(0, 5),
                        depth = depth.coerceIn(0, 32),
                        sticky = sticky.coerceAtLeast(0),
                        cooldown = cooldown.coerceAtLeast(0),
                        estimatedTokens = estTokens,
                        updatedAt = now,
                    )
                } else {
                    WorldbookEntryEntity(
                        id = UUID.randomUUID().toString(),
                        worldbookId = worldbookId,
                        uid = nextUid,
                        title = resolvedTitle,
                        content = cleanContent,
                        keysJson = AppJson.encodeToString(cleanKeys),
                        secondaryKeysJson = AppJson.encodeToString(cleanSecondary),
                        selectiveLogic = selectiveLogic.coerceIn(0, 3),
                        constant = constant,
                        enabled = enabled,
                        position = position.coerceIn(0, 5),
                        depth = depth.coerceIn(0, 32),
                        sticky = sticky.coerceAtLeast(0),
                        cooldown = cooldown.coerceAtLeast(0),
                        estimatedTokens = estTokens,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                database.withTransaction {
                    assetDao.upsertWorldbookEntry(updatedEntry)
                    recalculateWorldbookSummary(worldbookId, now)
                }
                assetDao.getWorldbookEntries(worldbookId)
            }
            container.retrievalCoordinator.scheduleWorldbookIndexing(
                scope = viewModelScope,
                worldbookId = worldbookId,
                entries = refreshed,
            )
            container.toastController.show(if (isZh) "已保存词条" else "Entry saved")
        }
    }

    fun toggleWorldbookEntryEnabled(entry: WorldbookEntryEntity, enabled: Boolean) {
        viewModelScope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                database.withTransaction {
                    assetDao.upsertWorldbookEntry(entry.copy(enabled = enabled, updatedAt = now))
                    recalculateWorldbookSummary(entry.worldbookId, now)
                }
                assetDao.getWorldbookEntries(entry.worldbookId)
            }
            container.retrievalCoordinator.scheduleWorldbookIndexing(
                scope = viewModelScope,
                worldbookId = entry.worldbookId,
                entries = refreshed,
            )
        }
    }

    fun deleteWorldbookEntry(entry: WorldbookEntryEntity) {
        viewModelScope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                database.withTransaction {
                    assetDao.deleteWorldbookEntry(entry.id)
                    recalculateWorldbookSummary(entry.worldbookId, now)
                }
                assetDao.getWorldbookEntries(entry.worldbookId)
            }
            runCatching {
                container.retrievalCoordinator.deleteOwnerVector(
                    ownerType = RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY,
                    ownerId = entry.id,
                    conversationId = VectorMemoryIndex.worldbookScopeId(entry.worldbookId),
                )
            }
            container.retrievalCoordinator.scheduleWorldbookIndexing(
                scope = viewModelScope,
                worldbookId = entry.worldbookId,
                entries = refreshed,
            )
        }
    }

    fun setDefaultRpPreset(presetId: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (enabled) {
                    assetDao.setDefaultRpPromptPreset(presetId)
                } else {
                    val preset = assetDao.getPromptPresetById(presetId) ?: return@withContext
                    assetDao.upsertPromptPreset(
                        preset.copy(
                            isDefaultRp = false,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    fun togglePresetBlockEnabled(presetId: String, identifier: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val preset = assetDao.getPromptPresetById(presetId) ?: return@withContext
                val blocks = com.gugachat.app.core.util.JsonUtils.decodeOrDefault<List<PromptPresetBlock>>(
                    preset.blocksJson,
                    emptyList(),
                )
                val updated = blocks.map { b ->
                    if (b.identifier == identifier) b.copy(enabled = enabled) else b
                }
                assetDao.upsertPromptPreset(
                    preset.copy(
                        blocksJson = AppJson.encodeToString(updated),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun togglePresetRegexEnabled(presetId: String, scriptId: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val preset = assetDao.getPromptPresetById(presetId) ?: return@withContext
                val scripts = com.gugachat.app.core.util.JsonUtils.decodeOrDefault<List<RpRegexScript>>(
                    preset.regexScriptsJson,
                    emptyList(),
                )
                val updated = scripts.map { s ->
                    if (s.id == scriptId) s.copy(enabled = enabled) else s
                }
                assetDao.upsertPromptPreset(
                    preset.copy(
                        regexScriptsJson = AppJson.encodeToString(updated),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun deletePromptPreset(presetId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                assetDao.deletePromptPreset(presetId)
            }
            if (_ui.value.activePresetId == presetId) {
                openPresetDetail(null)
            }
            container.toastController.show(if (isZh) "已删除设定预设" else "Deleted preset")
        }
    }

    /**
     * Extracts `novel_lore` entries from legacy RP/Novel sessions into standalone [WorldbookEntity]
     * records without touching legacy conversation data.
     */
    fun harvestLegacyNovelLoreToWorldbooks() {
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true) }
            try {
                val createdBooks = withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val allConvs = chatDao.getAllConversations()
                    val extracted = ArrayList<ParsedWorldbookV4>()

                    for (conv in allConvs) {
                        val lore = chatDao.getNovelLoreForConversation(conv.id).map { it.toDomain() }
                        if (lore.isEmpty()) continue
                        val wbId = "wb_legacy_lore_${conv.id.take(24)}"
                        if (assetDao.getWorldbookById(wbId) != null) continue
                        val entries = lore.mapIndexed { idx, l ->
                            WorldbookEntryEntity(
                                id = "${wbId}_${l.id.take(12)}_$idx",
                                worldbookId = wbId,
                                uid = idx + 1,
                                title = l.title.ifBlank { if (isZh) "词条 #${idx + 1}" else "Entry #${idx + 1}" },
                                content = l.content,
                                category = l.category,
                                keysJson = AppJson.encodeToString(l.keywords),
                                secondaryKeysJson = AppJson.encodeToString(l.aliases),
                                constant = l.alwaysActive,
                                enabled = l.enabled,
                                insertionOrder = l.priority,
                                position = WorldbookEngine.POSITION_AFTER_CHAR,
                                depth = 4,
                                estimatedTokens = l.estimatedTokens.coerceAtLeast(8),
                                createdAt = l.createdAt,
                                updatedAt = now,
                            )
                        }
                        val wb = WorldbookEntity(
                            id = wbId,
                            name = "${conv.title.ifBlank { if (isZh) "旧会话" else "Session" }} · 提炼设定",
                            description = if (isZh) "从旧会话正文词条提炼的可复用世界书" else "Extracted from legacy session lore",
                            sourceFormat = "legacy_novel_lore",
                            globalEnabled = false,
                            entryCount = entries.size,
                            totalEstimatedTokens = entries.sumOf { it.estimatedTokens },
                            contentFingerprint = com.gugachat.app.core.util.HashUtils.sha256Hex("legacy_lore_${conv.id}_${entries.size}"),
                            createdAt = now,
                            updatedAt = now,
                        )
                        database.withTransaction {
                            assetDao.upsertWorldbook(wb)
                            assetDao.replaceWorldbookEntries(wb.id, entries)
                        }
                        extracted.add(ParsedWorldbookV4(wb, entries))
                    }
                    extracted
                }

                for (parsed in createdBooks) {
                    container.retrievalCoordinator.scheduleWorldbookIndexing(
                        scope = viewModelScope,
                        worldbookId = parsed.worldbook.id,
                        entries = parsed.entries,
                    )
                }
                refreshLegacyLoreSessionCount()

                if (createdBooks.isEmpty()) {
                    container.toastController.show(
                        if (isZh) "暂无需要提炼的旧会话设定" else "No unharvested legacy session lore found",
                    )
                } else {
                    container.toastController.show(
                        if (isZh) "已提炼 ${createdBooks.size} 本世界书至设定库"
                        else "Extracted ${createdBooks.size} worldbook(s) from legacy sessions",
                    )
                }
            } catch (e: Throwable) {
                container.toastController.show(
                    e.message ?: if (isZh) "提炼失败" else "Extraction failed",
                    isError = true,
                )
            } finally {
                _ui.update { it.copy(isBusy = false) }
            }
        }
    }

    // ==================== Campaign Launchers ====================

    /**
     * Launches a new RP campaign conversation from a saved [CharacterCardEntity], wiring relative
     * media paths, selected [PersonaEntity], mounted [ConversationWorldbookCrossRef]s, optional
     * [PromptPresetEntity], and expanded greeting `variants` atomically in a Room transaction.
     */
    suspend fun launchCampaignFromCard(
        card: CharacterCardEntity,
        selectedGreetingIndex: Int,
        personaId: String?,
        worldbookIds: List<String>,
        presetId: String?,
        onOpenConversation: (String) -> Unit,
    ): String {
        _ui.update { it.copy(isLaunching = true) }
        try {
            val convId = withContext(Dispatchers.IO) {
                container.campaignLaunchUseCase.launchFromCard(
                    card = card,
                    selectedGreetingIndex = selectedGreetingIndex,
                    personaId = personaId,
                    worldbookIds = worldbookIds,
                    presetId = presetId,
                )
            }
            withContext(Dispatchers.Main) {
                onOpenConversation(convId)
            }
            return convId
        } finally {
            _ui.update { it.copy(isLaunching = false) }
        }
    }

    /**
     * Launches a new RP campaign from a built-in [RpPresetTemplate], optionally mounting custom
     * Persona, global Worldbooks, and a Prompt Preset.
     */
    suspend fun launchCampaignFromPreset(
        presetTemplate: RpPresetTemplate,
        selectedGreetingIndex: Int,
        personaId: String?,
        worldbookIds: List<String>,
        promptPresetId: String?,
        onOpenConversation: (String) -> Unit,
    ): String {
        _ui.update { it.copy(isLaunching = true) }
        try {
            val convId = withContext(Dispatchers.IO) {
                container.campaignLaunchUseCase.launchFromPreset(
                    presetTemplate = presetTemplate,
                    selectedGreetingIndex = selectedGreetingIndex,
                    personaId = personaId,
                    worldbookIds = worldbookIds,
                    promptPresetId = promptPresetId,
                )
            }
            withContext(Dispatchers.Main) {
                onOpenConversation(convId)
            }
            return convId
        } finally {
            _ui.update { it.copy(isLaunching = false) }
        }
    }

    // ==================== Private Helpers ====================

    private fun refreshLegacyLoreSessionCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val allConvs = chatDao.getAllConversations()
            var count = 0
            for (conv in allConvs) {
                val wbId = "wb_legacy_lore_${conv.id.take(24)}"
                if (assetDao.getWorldbookById(wbId) != null) continue
                if (chatDao.getNovelLoreForConversation(conv.id).isNotEmpty()) {
                    count++
                }
            }
            _ui.update { it.copy(legacyLoreSessionCount = count) }
        }
    }

    private fun refreshWorldbookVectorStatus(books: List<WorldbookEntity>) {
        if (books.isEmpty()) {
            _ui.update { it.copy(worldbookVectorReady = emptyMap()) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val engine = container.retrievalCoordinator.resolveActiveEmbeddingEngine()
            if (engine == null) {
                _ui.update { it.copy(worldbookVectorReady = emptyMap()) }
                return@launch
            }
            val map = HashMap<String, Boolean>(books.size)
            for (wb in books) {
                if (wb.entryCount == 0) {
                    map[wb.id] = false
                    continue
                }
                val rows = runCatching {
                    embeddingDao.getHashesForProfile(
                        conversationId = VectorMemoryIndex.worldbookScopeId(wb.id),
                        profileKey = engine.profileKey,
                    )
                }.getOrDefault(emptyList())
                val enabledCount = runCatching {
                    assetDao.getEnabledEntriesForWorldbooks(listOf(wb.id)).size
                }.getOrDefault(wb.entryCount)
                map[wb.id] = rows.isNotEmpty() && (enabledCount == 0 || rows.size >= enabledCount)
            }
            _ui.update { it.copy(worldbookVectorReady = map) }
        }
    }

    private suspend fun recalculateWorldbookSummary(worldbookId: String, now: Long) {
        container.assetImportCoordinator.recalculateWorldbookSummary(worldbookId, now)
    }
}
