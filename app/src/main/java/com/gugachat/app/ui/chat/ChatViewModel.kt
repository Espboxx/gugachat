package com.gugachat.app.ui.chat

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gugachat.app.AppContainer
import com.gugachat.app.AuthSessionState
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.EnStrings
import com.gugachat.app.core.i18n.ZhStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.ExternalImportService
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.OnlineChatGroupDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.data.api.WireChatMessageDto
import com.gugachat.app.data.chat.AssetImportResult
import com.gugachat.app.data.chat.ChatContextAssembler
import com.gugachat.app.data.chat.RetrievalCoordinator
import com.gugachat.app.data.chat.StreamLaunchRequest
import com.gugachat.app.data.local.AttachmentSaveResult
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
import com.gugachat.app.domain.chat.TokenEstimator
import com.gugachat.app.domain.chat.WorldbookEngine
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextBuildInput
import com.gugachat.app.domain.chat.ContextBuilder
import com.gugachat.app.domain.chat.ContextCompactor
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.EpisodeConsolidator
import com.gugachat.app.domain.chat.FileInjectMode
import com.gugachat.app.domain.chat.FreeQuotaUtils
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryExtractor
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.NovelPresets
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.RpCardCodec
import com.gugachat.app.domain.chat.RpCgRecord
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpDiceRoll
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpPresets
import com.gugachat.app.domain.chat.RpRegexEngine
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.RpVisualTheme
import com.gugachat.app.domain.chat.TavernAssetCodec
import com.gugachat.app.domain.chat.TimelineReconciler
import com.gugachat.app.domain.chat.UserMemory
import com.gugachat.app.domain.chat.UuSaveCodec
import com.gugachat.app.domain.chat.VectorIndexProgress
import com.gugachat.app.domain.chat.activeVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.UUID
import kotlin.math.roundToInt

val LocalChatViewModel = staticCompositionLocalOf<ChatViewModel?> { null }

data class PendingScannedPdf(
    val fileName: String,
    val pageCount: Int,
    val uri: Uri,
)

data class ChatUiState(
    val groups: List<OnlineChatGroupDto> = emptyList(),
    val models: List<OnlineChatModelDto> = emptyList(),
    val selectedGroupId: Long? = null,
    val selectedModelId: String? = null,
    val conversations: List<ChatConversation> = emptyList(),
    val activeConversation: ChatConversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
    val userMemories: List<UserMemory> = emptyList(),
    val novelChapters: List<NovelChapter> = emptyList(),
    val novelLore: List<NovelLoreEntry> = emptyList(),
    val memoryLedger: List<MemoryLedgerItem> = emptyList(),
    val memoryEpisodes: List<MemoryEpisode> = emptyList(),
    val rpSaveSlots: List<RpSaveSlot> = emptyList(),
    val draftText: String = "",
    val draftAttachments: List<ChatAttachmentMeta> = emptyList(),
    val isImportingAttachment: Boolean = false,
    val pendingScannedPdf: PendingScannedPdf? = null,
    val draftNovelKind: NovelInputKind = NovelInputKind.director,
    val draftRpKind: RpInputKind = RpInputKind.action,
    val draftDiceRoll: RpDiceRoll? = null,
    val isStreaming: Boolean = false,
    val streamingStatusHint: String? = null,
    val isImpersonating: Boolean = false,
    val isRefreshingChoices: Boolean = false,
    val isGeneratingRpArt: Boolean = false,
    val rpArtStatusLabel: String = "",
    val isExtractingMemory: Boolean = false,
    val isCompacting: Boolean = false,
    val threePathDrafts: List<NovelDraftOption> = emptyList(),
    val isGeneratingDrafts: Boolean = false,
    val isCreatingNovelWithAi: Boolean = false,
    val isExtractingNovelLore: Boolean = false,
    val isGeneratingRecap: Boolean = false,
    val isSyncingOutlineChapters: Boolean = false,
    val outlineChatReply: String = "",
    val polishResult: String = "",
    val pureReadingMode: Boolean = false,
    val readerFontSizeSp: Int = 17,
    val readerLineHeight: Float = 1.75f,
    val readerPaper: String = "cream", // cream | parchment | sage | night
    val readerSerif: Boolean = true,
    val readerIndent: Boolean = true,
    val defaultContextWindowOverride: Int? = null,
    val defaultMaxHistoryMessages: Int? = null,
    val defaultMaxOutputTokens: Int? = null,
    val waterline: ContextWaterlineSummary = ContextWaterlineSummary(),
    val mutedRecallKeys: Set<String> = emptySet(),
    val vectorIndexProgress: VectorIndexProgress = VectorIndexProgress(),
    val activeRetrievalMode: RetrievalMode = RetrievalMode.LEXICAL_ONLY,
    val compactingProgressText: String? = null,
    val streamingConversationIds: Set<String> = emptySet(),
    val characterCards: List<CharacterCardEntity> = emptyList(),
    val worldbooks: List<WorldbookEntity> = emptyList(),
    val personas: List<PersonaEntity> = emptyList(),
    val promptPresets: List<PromptPresetEntity> = emptyList(),
    val mountedWorldbookRefs: List<ConversationWorldbookCrossRef> = emptyList(),
) {
    val effectiveChatParams: ChatParams
        get() = activeConversation?.params ?: ChatParams(
            maxTokens = defaultMaxOutputTokens,
            contextWindowOverride = defaultContextWindowOverride,
            maxHistoryMessages = defaultMaxHistoryMessages,
        )

    val boundCharacterCard: CharacterCardEntity?
        get() = activeConversation?.characterCardId?.let { id -> characterCards.firstOrNull { it.id == id } }

    val boundPromptPreset: PromptPresetEntity?
        get() = activeConversation?.presetId?.let { id -> promptPresets.firstOrNull { it.id == id } }

    val activeGreetingVariants: List<String>
        get() {
            val card = boundCharacterCard
            if (card != null) {
                val alts = card.decodedAltGreetings()
                return (listOf(card.firstMes) + alts).map { it.trim() }.filter { it.isNotEmpty() }
            }
            val rp = activeConversation?.rp ?: return emptyList()
            return (listOf(rp.greeting) + rp.alternateGreetings).map { it.trim() }.filter { it.isNotEmpty() }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val container: AppContainer) : ViewModel() {
    private val chatDao = container.database.chatDao()
    private val userMemoryDao = container.database.userMemoryDao()
    private val assetDao = container.database.assetDao()
    private val chatStreamManager = container.chatStreamManager

    private val _activeConvId = MutableStateFlow<String?>(null)
    private val _ui = MutableStateFlow(
        ChatUiState(
            activeRetrievalMode = container.settingsState.value.retrievalSettings.mode,
            vectorIndexProgress = container.retrievalCoordinator.indexProgress.value,
        ),
    )
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    private var waterlineJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                chatDao.observeConversations(),
                _activeConvId,
                assetDao.observeCharacterCards(),
            ) { list, curId, cards ->
                Triple(list.map { it.toDomain() }, curId, cards)
            }.collect { (domainList, curId, cards) ->
                val cardMap = cards.associateBy { it.id }
                val matched = domainList.firstOrNull { it.id == curId }
                val rawActive = when {
                    matched != null -> matched
                    curId == null -> domainList.firstOrNull()
                    else -> _ui.value.activeConversation ?: domainList.firstOrNull()
                }
                if (curId == null && rawActive != null) {
                    _activeConvId.value = rawActive.id
                }
                val active = rawActive?.let { conv ->
                    enrichConversationWithBoundCardMedia(conv, conv.characterCardId?.let { cardMap[it] })
                }
                val activeSession = active?.id?.let { chatStreamManager.activeSessions.value[it] }
                _ui.update {
                    it.copy(
                        conversations = domainList,
                        activeConversation = active,
                        characterCards = cards,
                        isStreaming = activeSession?.isStreaming == true,
                        streamingStatusHint = activeSession?.statusHint,
                    )
                }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            assetDao.observeWorldbooks().collect { books ->
                _ui.update { it.copy(worldbooks = books) }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            assetDao.observePersonas().collect { list ->
                _ui.update { it.copy(personas = list) }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            assetDao.observePromptPresets().collect { presets ->
                _ui.update { it.copy(promptPresets = presets) }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            _activeConvId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else assetDao.observeMountedWorldbookRefs(id)
            }.collect { refs ->
                _ui.update { it.copy(mountedWorldbookRefs = refs) }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            userMemoryDao.observeAll().collect { rows ->
                _ui.update { it.copy(userMemories = rows.map { r -> r.toDomain() }) }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            combine(
                chatStreamManager.activeSessions,
                _activeConvId,
            ) { sessions, curId ->
                sessions to curId
            }.collect { (sessions, curId) ->
                val streamingIds = sessions.filterValues { it.isStreaming }.keys
                val targetId = curId ?: _ui.value.activeConversation?.id
                val activeSession = targetId?.let { sessions[it] }
                _ui.update {
                    it.copy(
                        streamingConversationIds = streamingIds,
                        isStreaming = activeSession?.isStreaming == true,
                        streamingStatusHint = activeSession?.statusHint,
                    )
                }
            }
        }

        viewModelScope.launch {
            chatStreamManager.lastWaterlineContext.collect { built ->
                if (built != null) {
                    _ui.update { it.copy(waterline = built.summary) }
                }
            }
        }

        viewModelScope.launch {
            _activeConvId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(Triple<String?, List<ChatMessage>, Pair<ChatMessage?, ChatMessage?>>(null, emptyList(), null to null))
                } else {
                    val dbFlow = chatDao.observeMessages(id).map { rows -> rows.map { r -> r.toDomain() } }
                    combine(
                        dbFlow,
                        chatStreamManager.activeSessions,
                        chatStreamManager.lastCommittedMessages,
                    ) { dbMsgs, sessions, committed ->
                        val live = sessions[id]?.streamingMessage
                        val bridge = committed[id]
                        Triple(id, dbMsgs, live to bridge)
                    }
                }
            }.collect { (convId, dbMsgs, livePair) ->
                val (live, bridge) = livePair
                val activeLive = live ?: bridge
                val merged = if (convId != null && activeLive != null && activeLive.conversationId == convId) {
                    val dbMatch = dbMsgs.firstOrNull { it.id == activeLive.id }
                    if (dbMatch != null && dbMatch.status != "streaming" && activeLive.status != "streaming") {
                        if (bridge != null && bridge.id == dbMatch.id) {
                            chatStreamManager.clearCommittedBridge(convId, dbMatch.id)
                        }
                        dbMsgs
                    } else if (dbMatch != null) {
                        dbMsgs.map { if (it.id == activeLive.id) activeLive else it }
                    } else {
                        dbMsgs + activeLive
                    }
                } else {
                    dbMsgs
                }
                _ui.update { it.copy(messages = merged) }
                val missingAttIds = merged.asSequence()
                    .flatMap { it.attachmentIds.asSequence() }
                    .filter { it.isNotBlank() && it !in _ui.value.attachmentsById }
                    .distinct()
                    .toList()
                if (missingAttIds.isNotEmpty()) {
                    viewModelScope.launch {
                        val loaded = container.attachmentStore.loadAttachmentsMap(missingAttIds)
                        if (loaded.isNotEmpty()) {
                            _ui.update { st -> st.copy(attachmentsById = st.attachmentsById + loaded) }
                            recomputeWaterline()
                        }
                    }
                }
                if (live == null || live.status != "streaming") {
                    recomputeWaterline()
                }
            }
        }

        viewModelScope.launch {
            _activeConvId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else chatDao.observeNovelChapters(id)
            }.collect { rows ->
                _ui.update { it.copy(novelChapters = rows.map { r -> r.toDomain() }) }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            _activeConvId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else chatDao.observeNovelLore(id)
            }.collect { rows ->
                _ui.update { it.copy(novelLore = rows.map { r -> r.toDomain() }) }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            _activeConvId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else chatDao.observeMemoryLedger(id)
            }.collect { rows ->
                _ui.update { it.copy(memoryLedger = rows.map { r -> r.toDomain() }) }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            _activeConvId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else chatDao.observeMemoryEpisodes(id)
            }.collect { rows ->
                _ui.update { it.copy(memoryEpisodes = rows.map { r -> r.toDomain() }) }
                recomputeWaterline()
            }
        }

        viewModelScope.launch {
            _activeConvId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else chatDao.observeRpSaveSlots(id)
            }.collect { rows ->
                _ui.update { it.copy(rpSaveSlots = rows.map { r -> r.toDomain() }) }
            }
        }

        viewModelScope.launch {
            container.apiProviderStore.providers.drop(1).collect {
                loadCapabilities()
            }
        }

        viewModelScope.launch {
            var prevSettings: AppSettings? = null
            container.settingsStore.settingsFlow.collect { s ->
                val prev = prevSettings
                prevSettings = s
                _ui.update {
                    it.copy(
                        readerPaper = s.readerPaper,
                        readerFontSizeSp = s.readerFontSizeSp,
                        readerLineHeight = s.readerLineHeight,
                        readerSerif = s.readerSerif,
                        readerIndent = s.readerIndent,
                        defaultContextWindowOverride = s.defaultContextWindowOverride,
                        defaultMaxHistoryMessages = s.defaultMaxHistoryMessages,
                        defaultMaxOutputTokens = s.defaultMaxOutputTokens,
                        activeRetrievalMode = s.retrievalSettings.mode,
                    )
                }
                if (prev == null ||
                    prev.memoryBudget != s.memoryBudget ||
                    prev.retrievalSettings != s.retrievalSettings ||
                    prev.tokenCalibrationMap != s.tokenCalibrationMap
                ) {
                    scheduleWaterlineRecompute(debounceMs = 0L)
                }
                if (prev != null && prev.retrievalSettings != s.retrievalSettings &&
                    s.retrievalSettings.mode != RetrievalMode.LEXICAL_ONLY
                ) {
                    scheduleActiveConversationIndexing()
                }
            }
        }

        viewModelScope.launch {
            container.retrievalCoordinator.indexProgress.collect { progress ->
                _ui.update { it.copy(vectorIndexProgress = progress) }
            }
        }

        viewModelScope.launch {
            _activeConvId.collect { convId ->
                if (!convId.isNullOrBlank()) {
                    scheduleActiveConversationIndexing(convId)
                }
            }
        }

        loadCapabilities()
    }

    fun loadCapabilities() {
        viewModelScope.launch {
            try {
                val isDirectOnly = (container.authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
                val customGroups = container.apiProviderStore.enabledProviders().map { p ->
                    OnlineChatGroupDto(
                        id = p.syntheticGroupId,
                        name = p.displayTitle(),
                        platform = p.protocol.wireValue,
                    )
                }
                val cap = if (isDirectOnly) null else runCatching { container.onlineChatApi.capabilities() }.getOrElse { err ->
                    if (customGroups.isEmpty()) throw err else null
                }
                val serverGroups = cap?.groups?.let { FreeQuotaUtils.sortChatGroupsByFreeQuota(it) }.orEmpty()
                val sortedGroups = serverGroups + customGroups
                val saved = container.settingsStore.currentSettings()
                val pickedGroupId = sortedGroups.firstOrNull { it.id == _ui.value.selectedGroupId }?.id
                    ?: sortedGroups.firstOrNull { it.id == saved.lastChatGroupId }?.id
                    ?: sortedGroups.firstOrNull()?.id

                _ui.update {
                    it.copy(
                        groups = sortedGroups,
                        selectedGroupId = pickedGroupId,
                    )
                }
                if (pickedGroupId != null) {
                    loadModels(pickedGroupId)
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Failed to load chat groups", isError = true)
            }
        }
    }

    fun selectGroup(groupId: Long) {
        _ui.update { it.copy(selectedGroupId = groupId) }
        viewModelScope.launch {
            _ui.value.activeConversation?.let { conv ->
                chatDao.upsertConversation(conv.copy(groupId = groupId, updatedAt = System.currentTimeMillis()).toEntity())
            }
            loadModels(groupId)
        }
    }

    private suspend fun loadModels(groupId: Long) {
        try {
            val rawModels = container.onlineChatApi.models(groupId)
            val sorted = FreeQuotaUtils.sortChatModelsByFreeQuota(rawModels)
            val saved = container.settingsStore.currentSettings()
            val chosen = sorted.firstOrNull { it.id == _ui.value.selectedModelId }?.id
                ?: sorted.firstOrNull { it.id == saved.lastChatModel }?.id
                ?: sorted.firstOrNull()?.id
            _ui.update { it.copy(models = sorted, selectedModelId = chosen) }
            if (chosen != null) {
                container.settingsStore.setLastChatSelection(groupId, chosen)
            }
            recomputeWaterline()
        } catch (e: Throwable) {
            container.toastController.show(e.message ?: "Failed to load models", isError = true)
        }
    }

    fun selectModel(modelId: String) {
        val groupId = _ui.value.selectedGroupId
        _ui.update { it.copy(selectedModelId = modelId) }
        viewModelScope.launch {
            if (groupId != null) {
                container.settingsStore.setLastChatSelection(groupId, modelId)
            }
            _ui.value.activeConversation?.let { conv ->
                chatDao.upsertConversation(conv.copy(model = modelId, updatedAt = System.currentTimeMillis()).toEntity())
            }
            recomputeWaterline()
        }
    }

    fun selectCustomModel(modelId: String) {
        val clean = modelId.trim()
        if (clean.isEmpty()) return
        val groupId = _ui.value.selectedGroupId
        if (groupId != null && com.gugachat.app.core.storage.ApiProviderConfig.isCustomProviderId(groupId)) {
            container.apiProviderStore.addCustomModel(groupId, clean)
        }
        _ui.update { state ->
            val exists = state.models.any { it.id == clean }
            val nextModels = if (exists) state.models else listOf(DirectProtocolCodec.inferChatModelMeta(clean)) + state.models
            state.copy(models = nextModels)
        }
        selectModel(clean)
    }

    fun updateDraftText(text: String) {
        _ui.update { it.copy(draftText = text) }
        scheduleWaterlineRecompute(debounceMs = 180L)
    }

    fun setDraftNovelKind(kind: NovelInputKind) {
        _ui.update { it.copy(draftNovelKind = kind) }
    }

    fun setDraftRpKind(kind: RpInputKind) {
        _ui.update { it.copy(draftRpKind = kind) }
    }

    fun addImageUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val s = if (container.settingsState.value.language == AppLanguage.EN) EnStrings else ZhStrings
            val maxImages = 6
            val currentImgCount = _ui.value.draftAttachments.count { it.isImage }
            val slots = (maxImages - currentImgCount).coerceAtLeast(0)
            if (slots <= 0) {
                container.toastController.show(s.chat.imageLimitHint.fmt("max" to maxImages), isError = true)
                return@launch
            }
            _ui.update { it.copy(isImportingAttachment = true) }
            try {
                val added = ArrayList<ChatAttachmentMeta>()
                var failedCount = 0
                for (uri in uris.take(slots)) {
                    val entity = container.attachmentStore.saveFromUri(uri)
                    if (entity != null) {
                        added.add(entity.toDomain())
                    } else {
                        failedCount++
                    }
                }
                if (added.isNotEmpty()) {
                    _ui.update { st ->
                        st.copy(
                            draftAttachments = st.draftAttachments + added,
                            attachmentsById = st.attachmentsById + added.associateBy { it.id },
                        )
                    }
                    recomputeWaterline()
                }
                if (uris.size > slots) {
                    container.toastController.show(s.chat.imageLimitHint.fmt("max" to maxImages), isError = true)
                } else if (failedCount > 0 && added.isEmpty()) {
                    container.toastController.show(s.chat.invalidImage, isError = true)
                }
            } finally {
                _ui.update { it.copy(isImportingAttachment = false) }
            }
        }
    }

    fun addFileUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val s = if (container.settingsState.value.language == AppLanguage.EN) EnStrings else ZhStrings
            val maxFiles = 5
            val maxImages = 6
            var fileSlots = (maxFiles - _ui.value.draftAttachments.count { it.isFile }).coerceAtLeast(0)
            var imageSlots = (maxImages - _ui.value.draftAttachments.count { it.isImage }).coerceAtLeast(0)
            if (fileSlots <= 0 && imageSlots <= 0) {
                container.toastController.show(s.attachments.fileLimitHint.fmt("max" to maxFiles), isError = true)
                return@launch
            }
            _ui.update { it.copy(isImportingAttachment = true) }
            try {
                val added = ArrayList<ChatAttachmentMeta>()
                var firstError: String? = null
                var hitLimit = false
                for (uri in uris) {
                    when (val res = container.attachmentStore.saveDocumentFromUri(uri)) {
                        is AttachmentSaveResult.Success -> {
                            val meta = res.meta
                            if (meta.isImage) {
                                if (imageSlots > 0) {
                                    added.add(meta)
                                    imageSlots--
                                } else {
                                    hitLimit = true
                                }
                            } else {
                                if (fileSlots > 0) {
                                    added.add(meta)
                                    fileSlots--
                                } else {
                                    hitLimit = true
                                }
                            }
                        }
                        is AttachmentSaveResult.ScannedPdf -> {
                            _ui.update {
                                it.copy(
                                    pendingScannedPdf = PendingScannedPdf(
                                        fileName = res.fileName,
                                        pageCount = res.pageCount,
                                        uri = res.uri,
                                    ),
                                )
                            }
                        }
                        is AttachmentSaveResult.Error -> {
                            if (firstError == null) firstError = res.message
                        }
                    }
                }
                if (added.isNotEmpty()) {
                    _ui.update { st ->
                        st.copy(
                            draftAttachments = st.draftAttachments + added,
                            attachmentsById = st.attachmentsById + added.associateBy { it.id },
                        )
                    }
                    recomputeWaterline()
                }
                if (firstError != null) {
                    container.toastController.show(firstError, isError = true)
                } else if (hitLimit) {
                    container.toastController.show(s.attachments.fileLimitHint.fmt("max" to maxFiles), isError = true)
                }
            } finally {
                _ui.update { it.copy(isImportingAttachment = false) }
            }
        }
    }

    fun updateDraftAttachmentInjectMode(id: String, mode: FileInjectMode) {
        _ui.update { st ->
            val updatedDraft = st.draftAttachments.map { if (it.id == id) it.copy(injectMode = mode) else it }
            val existing = st.attachmentsById[id]
            val updatedMap = if (existing != null) {
                st.attachmentsById + (id to existing.copy(injectMode = mode))
            } else {
                st.attachmentsById
            }
            st.copy(draftAttachments = updatedDraft, attachmentsById = updatedMap)
        }
        viewModelScope.launch {
            container.attachmentStore.updateInjectMode(id, mode)
            recomputeWaterline()
        }
    }

    fun dismissPendingScannedPdf() {
        _ui.update { it.copy(pendingScannedPdf = null) }
    }

    fun convertPendingScannedPdfToImages(maxPages: Int = 4) {
        val pending = _ui.value.pendingScannedPdf ?: return
        _ui.update { it.copy(pendingScannedPdf = null) }
        viewModelScope.launch {
            val s = if (container.settingsState.value.language == AppLanguage.EN) EnStrings else ZhStrings
            val maxImages = 6
            val slots = (maxImages - _ui.value.draftAttachments.count { it.isImage }).coerceAtLeast(0)
            if (slots <= 0) {
                container.toastController.show(s.chat.imageLimitHint.fmt("max" to maxImages), isError = true)
                return@launch
            }
            _ui.update { it.copy(isImportingAttachment = true) }
            try {
                val rendered = container.attachmentStore.renderScannedPdfPagesAsImages(
                    uri = pending.uri,
                    maxPages = minOf(maxPages, slots),
                )
                if (rendered.isNotEmpty()) {
                    _ui.update { st ->
                        st.copy(
                            draftAttachments = st.draftAttachments + rendered,
                            attachmentsById = st.attachmentsById + rendered.associateBy { it.id },
                        )
                    }
                    recomputeWaterline()
                } else {
                    container.toastController.show(s.chat.invalidImage, isError = true)
                }
            } finally {
                _ui.update { it.copy(isImportingAttachment = false) }
            }
        }
    }

    suspend fun readAttachmentText(id: String): String? = container.attachmentStore.readExtractedText(id)

    fun removeDraftAttachment(id: String) {
        _ui.update { it.copy(draftAttachments = it.draftAttachments.filterNot { a -> a.id == id }) }
        recomputeWaterline()
    }

    fun selectConversation(convId: String) {
        val changed = _activeConvId.value != convId
        _activeConvId.value = convId
        val rawConv = _ui.value.conversations.firstOrNull { it.id == convId }
        val conv = rawConv?.let { c ->
            enrichConversationWithBoundCardMedia(c, _ui.value.characterCards.firstOrNull { it.id == c.characterCardId })
        }
        val activeSession = chatStreamManager.activeSessions.value[convId]
        _ui.update {
            it.copy(
                activeConversation = conv ?: it.activeConversation,
                isStreaming = activeSession?.isStreaming == true,
                streamingStatusHint = activeSession?.statusHint,
                draftDiceRoll = null,
                threePathDrafts = if (changed) emptyList() else it.threePathDrafts,
                outlineChatReply = if (changed) "" else it.outlineChatReply,
                polishResult = if (changed) "" else it.polishResult,
                mutedRecallKeys = if (changed) emptySet() else it.mutedRecallKeys,
            )
        }
        if (conv != null) {
            if (conv.groupId != null && conv.groupId != _ui.value.selectedGroupId) {
                selectGroup(conv.groupId)
            } else if (!conv.model.isNullOrBlank()) {
                _ui.update { it.copy(selectedModelId = conv.model) }
            }
        }
    }

    private fun defaultChatParams(): ChatParams {
        val st = _ui.value
        return ChatParams(
            maxTokens = st.defaultMaxOutputTokens,
            contextWindowOverride = st.defaultContextWindowOverride,
            maxHistoryMessages = st.defaultMaxHistoryMessages,
        )
    }

    fun createNewChatSession(title: String = "新对话") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val defaultPersonaId = assetDao.getDefaultPersona()?.id
            val conv = ChatConversation(
                id = UUID.randomUUID().toString(),
                title = title,
                mode = ConversationMode.chat,
                groupId = _ui.value.selectedGroupId,
                model = _ui.value.selectedModelId,
                params = defaultChatParams(),
                personaId = defaultPersonaId,
                createdAt = now,
                updatedAt = now,
            )
            chatDao.upsertConversation(conv.toEntity())
            _ui.update { it.copy(draftDiceRoll = null) }
            _activeConvId.value = conv.id
        }
    }

    fun renameConversation(convId: String, newTitle: String) {
        val t = newTitle.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            val existing = _ui.value.conversations.firstOrNull { it.id == convId } ?: return@launch
            chatDao.upsertConversation(existing.copy(title = t, updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun togglePinConversation(convId: String) {
        viewModelScope.launch {
            val existing = _ui.value.conversations.firstOrNull { it.id == convId } ?: return@launch
            chatDao.upsertConversation(existing.copy(pinned = !existing.pinned).toEntity())
        }
    }

    fun deleteConversation(convId: String) {
        viewModelScope.launch {
            chatStreamManager.cancelStreamAndWait(convId)
            chatDao.deleteConversationCascade(convId)
            if (_activeConvId.value == convId) {
                _activeConvId.value = _ui.value.conversations.firstOrNull { it.id != convId }?.id
            }
        }
    }

    fun updateConversationParams(systemPrompt: String, params: ChatParams) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: ensureActiveConversation()
            val sanitized = params.copy(
                contextWindowOverride = params.contextWindowOverride?.coerceIn(4096, 2_000_000),
                maxHistoryMessages = params.maxHistoryMessages?.coerceIn(1, 500),
                maxTokens = params.maxTokens?.coerceIn(256, 131_072),
            )
            chatDao.upsertConversation(
                conv.copy(
                    systemPrompt = systemPrompt,
                    params = sanitized,
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            container.settingsStore.setDefaultContextPrefs(
                contextWindowOverride = sanitized.contextWindowOverride,
                maxHistoryMessages = sanitized.maxHistoryMessages,
                maxOutputTokens = sanitized.maxTokens,
            )
            recomputeWaterline()
        }
    }

    fun updateContextSettings(
        contextWindowOverride: Int?,
        maxHistoryMessages: Int?,
        maxTokens: Int?,
    ) {
        viewModelScope.launch {
            val cleanWin = contextWindowOverride?.coerceIn(4096, 2_000_000)
            val cleanHist = maxHistoryMessages?.coerceIn(1, 500)
            val cleanOut = maxTokens?.coerceIn(256, 131_072)
            container.settingsStore.setDefaultContextPrefs(
                contextWindowOverride = cleanWin,
                maxHistoryMessages = cleanHist,
                maxOutputTokens = cleanOut,
            )
            val conv = _ui.value.activeConversation
            if (conv != null) {
                val updated = conv.copy(
                    params = conv.params.copy(
                        contextWindowOverride = cleanWin,
                        maxHistoryMessages = cleanHist,
                        maxTokens = cleanOut,
                    ),
                    updatedAt = System.currentTimeMillis(),
                )
                chatDao.upsertConversation(updated.toEntity())
                _ui.update { it.copy(activeConversation = updated) }
                recomputeWaterline()
            }
        }
    }

    fun toggleActiveReasoning() {
        val conv = _ui.value.activeConversation
        val params = conv?.params ?: defaultChatParams()
        updateConversationParams(
            systemPrompt = conv?.systemPrompt.orEmpty(),
            params = params.copy(enableReasoning = !params.enableReasoning),
        )
    }

    fun toggleActiveMemory() {
        val conv = _ui.value.activeConversation
        val params = conv?.params ?: defaultChatParams()
        updateConversationParams(
            systemPrompt = conv?.systemPrompt.orEmpty(),
            params = params.copy(enableAutoMemory = !params.enableAutoMemory),
        )
    }

    fun applyStarterPrompt(prompt: String) {
        _ui.update { it.copy(draftText = prompt) }
        sendMessage()
    }

    fun sendMessage(overrideText: String? = null) {
        val targetConvId = _activeConvId.value ?: _ui.value.activeConversation?.id
        if (targetConvId != null && chatStreamManager.isStreaming(targetConvId)) return
        val groupId = _ui.value.selectedGroupId
        val modelId = _ui.value.selectedModelId
        if (groupId == null || modelId.isNullOrBlank()) {
            container.toastController.show("Please select a group and model first", isError = true)
            return
        }
        val text = (overrideText ?: _ui.value.draftText).trim()
        val attachments = if (overrideText == null) _ui.value.draftAttachments else emptyList()
        if (text.isEmpty() && attachments.isEmpty()) return

        if (attachments.any { it.isImage }) {
            val activeModel = _ui.value.models.firstOrNull { it.id == modelId }
            if (activeModel != null && !activeModel.supports_vision) {
                val s = if (container.settingsState.value.language == AppLanguage.EN) EnStrings else ZhStrings
                container.toastController.show(s.attachments.visionSendBlocked, isError = true)
                return
            }
        }

        viewModelScope.launch {
            val conv = ensureActiveConversation()
            if (chatStreamManager.isStreaming(conv.id)) return@launch
            val effectiveText = if (conv.mode == ConversationMode.rp && conv.rp != null && conv.rp.regexScripts.isNotEmpty() && text.isNotEmpty()) {
                val hasNonDisplayUserScripts = conv.rp.regexScripts.any { s ->
                    s.enabled &&
                        (s.placement.equals("user_input", ignoreCase = true) || s.placement.equals("both", ignoreCase = true)) &&
                        (!s.markdownOnly || s.promptOnly)
                }
                if (hasNonDisplayUserScripts) {
                    RpRegexEngine.applyScripts(
                        text = text,
                        scripts = conv.rp.regexScripts,
                        placement = "user_input",
                        forDisplay = false,
                        rp = conv.rp,
                    ).ifBlank { text }
                } else {
                    text
                }
            } else {
                text
            }
            val curMsgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val nextSeq = (curMsgs.maxOfOrNull { it.seq } ?: 0) + 1
            val now = System.currentTimeMillis()
            val imgCount = attachments.count { it.isImage }
            val fileTokSum = attachments.filter { it.isFile }.sumOf { it.estimatedTokens }

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                role = "user",
                content = effectiveText,
                createdAt = now,
                seq = nextSeq,
                attachmentIds = attachments.map { it.id },
                inputKind = if (conv.mode == ConversationMode.novel) _ui.value.draftNovelKind else null,
                rpInputKind = if (conv.mode == ConversationMode.rp) _ui.value.draftRpKind else null,
                rpDiceRoll = if (conv.mode == ConversationMode.rp) _ui.value.draftDiceRoll else null,
                status = "done",
                tokenEstimate = ContextBuilder.estimateMessageTokens(effectiveText, imgCount) + fileTokSum,
            )
            chatDao.upsertMessage(userMsg.toEntity())

            val updatedTitle = if (conv.mode == ConversationMode.chat && conv.title == "新对话") {
                when {
                    effectiveText.isNotBlank() ->
                        effectiveText.lineSequence().firstOrNull()?.take(22)?.trim().orEmpty().ifBlank { conv.title }
                    attachments.any { it.isFile } ->
                        attachments.first { it.isFile }.fileName.take(22).ifBlank { conv.title }
                    attachments.isNotEmpty() ->
                        if (container.settingsState.value.language == AppLanguage.EN) "Image Chat" else "图片对话"
                    else -> conv.title
                }
            } else {
                conv.title
            }
            val updatedConv = conv.copy(title = updatedTitle, groupId = groupId, model = modelId, updatedAt = now)
            chatDao.upsertConversation(updatedConv.toEntity())

            if (overrideText == null) {
                _ui.update { st ->
                    st.copy(
                        draftText = "",
                        draftAttachments = emptyList(),
                        attachmentsById = st.attachmentsById + attachments.associateBy { it.id },
                        draftDiceRoll = null,
                    )
                }
            } else {
                _ui.update { it.copy(draftDiceRoll = null) }
            }
            launchCompletionForConversation(updatedConv)
        }
    }

    fun stopStreaming() {
        val convId = _activeConvId.value ?: _ui.value.activeConversation?.id ?: return
        chatStreamManager.cancelStream(convId)
    }

    fun regenerateFromAssistant(assistantMsgId: String) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            if (chatStreamManager.isStreaming(conv.id)) return@launch
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val target = msgs.firstOrNull { it.id == assistantMsgId } ?: return@launch
            val reconciled = TimelineReconciler.reconcileOnRollback(
                cutoffSeq = target.seq,
                inclusiveOfCutoff = true,
                messages = msgs,
                ledgerItems = _ui.value.memoryLedger,
                loreEntries = _ui.value.novelLore,
                episodes = _ui.value.memoryEpisodes,
                chapters = _ui.value.novelChapters,
                summarizedUntilSeq = conv.summarizedUntilSeq,
                activeWindowStartSeq = conv.params.activeWindowStartSeq,
            )
            persistReconciliation(reconciled)
            val rolledConv = TimelineReconciler.applyRollbackToConversation(conv, reconciled)
            if (rolledConv != conv) {
                chatDao.upsertConversation(rolledConv.toEntity())
            }
            val convForGen = when {
                rolledConv.mode == ConversationMode.rp && rolledConv.rp != null -> {
                    val prevAssistant = msgs
                        .filter { it.role == "assistant" && it.seq < target.seq && it.deletedAt == null && it.supersededAtSeq == null }
                        .maxByOrNull { it.seq }
                    val prevVar = prevAssistant?.activeVariant()
                    if (prevVar != null && (prevVar.rpStateSnapshot != null || prevVar.rpCastSnapshot != null)) {
                        rolledConv.copy(
                            rp = rolledConv.rp.copy(
                                state = prevVar.rpStateSnapshot ?: rolledConv.rp.state,
                                cast = prevVar.rpCastSnapshot ?: rolledConv.rp.cast,
                            ),
                        )
                    } else {
                        rolledConv
                    }
                }
                rolledConv.mode == ConversationMode.novel && rolledConv.novel != null -> {
                    val prevAssistant = msgs
                        .filter { it.role == "assistant" && it.seq < target.seq && it.deletedAt == null && it.supersededAtSeq == null }
                        .maxByOrNull { it.seq }
                    val prevVar = prevAssistant?.activeVariant()
                    if (prevVar?.novelStatusSnapshot != null) {
                        rolledConv.copy(novel = rolledConv.novel.copy(currentStatus = prevVar.novelStatusSnapshot))
                    } else {
                        rolledConv
                    }
                }
                else -> rolledConv
            }
            if (rolledConv.mode == ConversationMode.novel) {
                syncNovelChapterWordCounts(rolledConv.id)
            }
            launchCompletionForConversation(
                conv = convForGen,
                existingMessageForVariant = if (rolledConv.mode == ConversationMode.rp || rolledConv.mode == ConversationMode.novel) target else null,
            )
        }
    }

    fun editAndResendUserMessage(userMsgId: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            if (chatStreamManager.isStreaming(conv.id)) return@launch
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val target = msgs.firstOrNull { it.id == userMsgId } ?: return@launch
            val reconciled = TimelineReconciler.reconcileOnRollback(
                cutoffSeq = target.seq,
                inclusiveOfCutoff = false,
                messages = msgs.map {
                    if (it.id == userMsgId) it.copy(content = newText.trim(), editedAt = System.currentTimeMillis()) else it
                },
                ledgerItems = _ui.value.memoryLedger,
                loreEntries = _ui.value.novelLore,
                episodes = _ui.value.memoryEpisodes,
                chapters = _ui.value.novelChapters,
                summarizedUntilSeq = conv.summarizedUntilSeq,
                activeWindowStartSeq = conv.params.activeWindowStartSeq,
            )
            persistReconciliation(reconciled)
            val rolledConv = TimelineReconciler.applyRollbackToConversation(conv, reconciled)
            if (rolledConv != conv) {
                chatDao.upsertConversation(rolledConv.toEntity())
            }
            launchCompletionForConversation(rolledConv)
        }
    }

    fun editAssistantMessage(msgId: String, newText: String) {
        val clean = newText.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            if (chatStreamManager.isStreaming(conv.id)) return@launch
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val target = msgs.firstOrNull { it.id == msgId && it.role == "assistant" } ?: return@launch
            val now = System.currentTimeMillis()
            val updatedVariants = if (target.variants.isNotEmpty()) {
                val activeIdx = target.activeVariantIndex.coerceIn(0, target.variants.lastIndex)
                target.variants.mapIndexed { idx, v ->
                    if (idx == activeIdx) v.copy(content = clean) else v
                }
            } else {
                target.variants
            }
            val updatedMsg = target.copy(
                content = clean,
                editedAt = now,
                tokenEstimate = ContextBuilder.estimateMessageTokens(clean),
                variants = updatedVariants,
            )
            chatDao.upsertMessage(updatedMsg.toEntity())
            if (conv.mode == ConversationMode.novel) {
                syncNovelChapterWordCounts(conv.id)
            }
            recomputeWaterline()
        }
    }

    fun deleteMessage(msgId: String) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            if (chatStreamManager.isStreaming(conv.id)) return@launch
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val updated = TimelineReconciler.softDeleteMessage(msgId, msgs)
            chatDao.upsertMessages(updated.map { it.toEntity() })
            if (conv.mode == ConversationMode.novel) {
                syncNovelChapterWordCounts(conv.id)
            }
        }
    }

    fun branchFromMessage(msgId: String) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            val msgs = TimelineReconciler.activeMessages(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })
            val target = msgs.firstOrNull { it.id == msgId } ?: return@launch
            val now = System.currentTimeMillis()
            val newConvId = UUID.randomUUID().toString()
            val copiedMsgs = msgs.filter { it.seq <= target.seq }.map {
                it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId)
            }

            val existingChapters = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
            val chapterIdMap = HashMap<String, String>()
            val clonedChaptersRaw = existingChapters
                .filter { it.startSeq <= target.seq || it.order == 1 }
                .sortedBy { it.order }
                .mapIndexed { idx, ch ->
                    val newChId = UUID.randomUUID().toString()
                    chapterIdMap[ch.id] = newChId
                    val adjustedEnd = if (ch.endSeq != null && ch.endSeq > target.seq) null else ch.endSeq
                    val adjustedStatus = if (ch.endSeq != null && ch.endSeq > target.seq) "writing" else ch.status
                    ch.copy(
                        id = newChId,
                        conversationId = newConvId,
                        order = idx + 1,
                        status = adjustedStatus,
                        endSeq = adjustedEnd,
                        updatedAt = now,
                    )
                }
            val clonedChapters = NovelEngine.recalculateChapterWordCounts(clonedChaptersRaw, copiedMsgs)

            val nextNovel = conv.novel?.let { n ->
                val remappedActive = n.activeChapterId?.let { chapterIdMap[it] } ?: clonedChapters.lastOrNull()?.id
                n.copy(activeChapterId = remappedActive)
            }

            val existingEpisodes = chatDao.getMemoryEpisodesForConversation(conv.id).map { it.toDomain() }
                .filter { !it.stale && it.toSeq <= target.seq }
            val forkedSummarizedUntil = if (conv.summarizedUntilSeq > target.seq) {
                existingEpisodes.maxOfOrNull { it.toSeq } ?: 0
            } else {
                conv.summarizedUntilSeq
            }
            val forkedArchivedSummary = if (forkedSummarizedUntil == 0 && conv.summarizedUntilSeq > target.seq) {
                ""
            } else {
                conv.archivedSummary
            }
            val forkedParams = if (conv.params.activeWindowStartSeq != null && conv.params.activeWindowStartSeq > target.seq) {
                conv.params.copy(activeWindowStartSeq = null)
            } else {
                conv.params
            }

            val forkedConv = conv.copy(
                id = newConvId,
                title = "${conv.title} (分支)",
                params = forkedParams,
                novel = nextNovel,
                archivedSummary = forkedArchivedSummary,
                summarizedUntilSeq = forkedSummarizedUntil,
                createdAt = now,
                updatedAt = now,
                pinned = false,
            )
            chatDao.upsertConversation(forkedConv.toEntity())
            if (copiedMsgs.isNotEmpty()) chatDao.upsertMessages(copiedMsgs.map { it.toEntity() })
            if (clonedChapters.isNotEmpty()) chatDao.upsertNovelChapters(clonedChapters.map { it.toEntity() })

            val existingLore = chatDao.getNovelLoreForConversation(conv.id).map { it.toDomain() }
                .filter { !it.stale && it.validFromSeq <= target.seq }
            if (existingLore.isNotEmpty()) {
                chatDao.upsertNovelLoreList(existingLore.map { it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId).toEntity() })
            }
            val existingLedger = chatDao.getMemoryLedgerForConversation(conv.id).map { it.toDomain() }
                .filter { !it.stale && it.validFromSeq <= target.seq }
            if (existingLedger.isNotEmpty()) {
                chatDao.upsertMemoryLedgerList(existingLedger.map { it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId).toEntity() })
            }
            if (existingEpisodes.isNotEmpty()) {
                chatDao.upsertMemoryEpisodes(existingEpisodes.map { it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId).toEntity() })
            }
            val mountedRefs = assetDao.getMountedWorldbookRefs(conv.id)
            if (mountedRefs.isNotEmpty()) {
                assetDao.replaceMountedWorldbooks(newConvId, mountedRefs.map { it.copy(conversationId = newConvId) })
            }

            _activeConvId.value = newConvId
            val toast = if (conv.mode == ConversationMode.novel) {
                strings().novel.branchCreated.fmt("title" to forkedConv.title)
            } else {
                "Branch created"
            }
            container.toastController.show(toast)
        }
    }

    fun switchMessageVariant(msgId: String, variantIndex: Int) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            if (chatStreamManager.isStreaming(conv.id)) return@launch
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val msg = msgs.firstOrNull { it.id == msgId } ?: return@launch
            val variant = msg.variants.getOrNull(variantIndex) ?: return@launch
            val updatedMsg = msg.copy(
                content = variant.content,
                reasoning = variant.reasoning,
                usage = variant.usage,
                rpChoices = variant.rpChoices,
                novelChoices = variant.novelChoices,
                activeVariantIndex = variantIndex,
                tokenEstimate = ContextBuilder.estimateMessageTokens(variant.content),
            )
            chatDao.upsertMessage(updatedMsg.toEntity())
            if (conv.mode == ConversationMode.rp && conv.rp != null &&
                (variant.rpStateSnapshot != null || variant.rpCastSnapshot != null || variant.rpChoices.isNotEmpty())
            ) {
                val updatedRp = conv.rp.copy(
                    state = variant.rpStateSnapshot ?: conv.rp.state,
                    cast = variant.rpCastSnapshot ?: conv.rp.cast,
                    pendingChoices = variant.rpChoices,
                )
                chatDao.upsertConversation(conv.copy(rp = updatedRp, updatedAt = System.currentTimeMillis()).toEntity())
            } else if (conv.mode == ConversationMode.novel && conv.novel != null) {
                val updatedNovel = conv.novel.copy(
                    currentStatus = variant.novelStatusSnapshot ?: conv.novel.currentStatus,
                    pendingChoices = variant.novelChoices.ifEmpty { conv.novel.pendingChoices },
                )
                chatDao.upsertConversation(conv.copy(novel = updatedNovel, updatedAt = System.currentTimeMillis()).toEntity())
                syncNovelChapterWordCounts(conv.id)
            }
        }
    }

    private fun launchCompletionForConversation(
        conv: ChatConversation,
        existingMessageForVariant: ChatMessage? = null,
        continuePrefixText: String? = null,
        overrideMessages: List<WireChatMessageDto>? = null,
    ) {
        val groupId = conv.groupId ?: _ui.value.selectedGroupId ?: return
        val modelId = conv.model ?: _ui.value.selectedModelId ?: return
        val modelMeta = _ui.value.models.firstOrNull { it.id == modelId }
        chatStreamManager.startStream(
            StreamLaunchRequest(
                conversation = conv,
                groupId = groupId,
                modelId = modelId,
                existingMessageForVariant = existingMessageForVariant,
                continuePrefixText = continuePrefixText,
                overrideMessages = overrideMessages,
                draftText = _ui.value.draftText,
                mutedRecallKeys = _ui.value.mutedRecallKeys,
                modelMetaOverride = modelMeta,
            ),
        )
    }

    // ==================== Memory CRUD & Actions ====================

    fun extractUserMemoriesNow() {
        val conv = _ui.value.activeConversation ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId ?: return
        val model = conv.model ?: _ui.value.selectedModelId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isExtractingMemory = true) }
            try {
                val msgs = TimelineReconciler.activeMessages(_ui.value.messages)
                val existing = _ui.value.userMemories
                val prompt = MemoryExtractor.buildMemoryExtractionMessages(existing, msgs)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.2, max_tokens = 600),
                )
                val extracted = MemoryExtractor.parseExtractedMemories(res.content)
                if (extracted.isEmpty()) {
                    val isZh = container.settingsState.value.language == AppLanguage.ZH
                    container.toastController.show(if (isZh) "未提炼出新的长期记忆" else "No new long-term memories extracted")
                } else {
                    val (merged, added) = MemoryExtractor.mergeExtractedMemories(existing, extracted)
                    userMemoryDao.upsertAll(merged.map { it.toEntity() })
                    if (added > 0) scheduleActiveConversationIndexing(conv.id)
                    val isZh = container.settingsState.value.language == AppLanguage.ZH
                    container.toastController.show(if (isZh) "已新增 $added 条长期记忆" else "Added $added memories")
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Extraction failed", isError = true)
            } finally {
                _ui.update { it.copy(isExtractingMemory = false) }
            }
        }
    }

    fun addUserMemory(category: MemoryCategory, content: String, pinned: Boolean = false) {
        val clean = content.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val item = UserMemory(
                id = UUID.randomUUID().toString(),
                category = category,
                content = clean,
                pinned = pinned,
                enabled = true,
                createdAt = now,
                updatedAt = now,
                estimatedTokens = MemoryExtractor.estimateUserMemoryTokens(clean),
            )
            userMemoryDao.upsert(item.toEntity())
            scheduleActiveConversationIndexing()
        }
    }

    fun toggleUserMemoryPin(id: String) {
        viewModelScope.launch {
            val m = _ui.value.userMemories.firstOrNull { it.id == id } ?: return@launch
            userMemoryDao.upsert(m.copy(pinned = !m.pinned, updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun toggleUserMemoryEnabled(id: String) {
        viewModelScope.launch {
            val m = _ui.value.userMemories.firstOrNull { it.id == id } ?: return@launch
            userMemoryDao.upsert(m.copy(enabled = !m.enabled, updatedAt = System.currentTimeMillis()).toEntity())
            if (!m.enabled) scheduleActiveConversationIndexing()
        }
    }

    fun deleteUserMemory(id: String) {
        viewModelScope.launch {
            userMemoryDao.deleteById(id)
            container.retrievalCoordinator.deleteOwnerVector(RetrievalCoordinator.OWNER_USER_MEMORY, id, conversationId = "")
        }
    }

    fun addLedgerItem(kind: String, title: String, content: String) {
        val conv = _ui.value.activeConversation ?: return
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val tip = TimelineReconciler.findActiveTipSeq(_ui.value.messages)
            val item = MemoryLedgerItem(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                kind = kind,
                title = title.trim(),
                content = content.trim(),
                validFromSeq = tip,
                origin = "user",
                createdAt = now,
                updatedAt = now,
                estimatedTokens = ContextBuilder.estimateTextTokens("$title: $content") + 8,
            )
            chatDao.upsertMemoryLedger(item.toEntity())
            scheduleActiveConversationIndexing(conv.id)
        }
    }

    fun toggleLedgerPin(id: String) {
        viewModelScope.launch {
            val item = _ui.value.memoryLedger.firstOrNull { it.id == id } ?: return@launch
            chatDao.upsertMemoryLedger(item.copy(pinned = !item.pinned, updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun deleteLedgerItem(id: String) {
        val convId = _ui.value.activeConversation?.id
        viewModelScope.launch {
            chatDao.deleteMemoryLedger(id)
            container.retrievalCoordinator.deleteOwnerVector(RetrievalCoordinator.OWNER_RP_LEDGER, id, conversationId = convId)
        }
    }

    // ==================== Recall Inspector Quick Controls & Vector Reindex ====================

    fun toggleMuteRecallKey(key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) return
        _ui.update { st ->
            val next = if (clean in st.mutedRecallKeys) st.mutedRecallKeys - clean else st.mutedRecallKeys + clean
            st.copy(mutedRecallKeys = next)
        }
        scheduleWaterlineRecompute(debounceMs = 0L)
    }

    fun togglePinRecallItem(source: String, id: String) {
        when (source) {
            "user_memory_archival" -> toggleUserMemoryPin(id)
            "rp_ledger" -> toggleLedgerPin(id)
            "novel_lore" -> toggleNovelLoreAlwaysActive(id)
        }
    }

    fun disableRecallItem(source: String, id: String) {
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        viewModelScope.launch {
            when (source) {
                "user_memory_archival" -> {
                    val m = _ui.value.userMemories.firstOrNull { it.id == id } ?: return@launch
                    if (m.enabled) {
                        userMemoryDao.upsert(m.copy(enabled = false, updatedAt = System.currentTimeMillis()).toEntity())
                    }
                }
                "rp_ledger" -> {
                    val item = _ui.value.memoryLedger.firstOrNull { it.id == id } ?: return@launch
                    if (item.enabled) {
                        chatDao.upsertMemoryLedger(item.copy(enabled = false, updatedAt = System.currentTimeMillis()).toEntity())
                    }
                }
                "novel_lore" -> {
                    val entry = _ui.value.novelLore.firstOrNull { it.id == id } ?: return@launch
                    if (entry.enabled) {
                        chatDao.upsertNovelLore(entry.copy(enabled = false, updatedAt = System.currentTimeMillis()).toEntity())
                    }
                }
                else -> return@launch
            }
            scheduleWaterlineRecompute(debounceMs = 0L)
            container.toastController.show(if (isZh) "已停用该记忆条目" else "Disabled memory item")
        }
    }

    fun reindexActiveConversationVectors() {
        val convId = _ui.value.activeConversation?.id ?: return
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        val settings = container.settingsState.value.retrievalSettings
        if (settings.mode == RetrievalMode.LEXICAL_ONLY) {
            container.toastController.show(
                if (isZh) "当前为纯词法检索模式，请先在设置中启用向量检索" else "Switch retrieval mode to Local or Cloud Vector in settings first",
            )
            return
        }
        if (container.retrievalCoordinator.resolveActiveEmbeddingEngine(settings) == null) {
            container.toastController.show(
                if (isZh) "向量模型尚未就绪，请检查下载或接口配置" else "Vector model is not ready yet; check download or API settings",
                isError = true,
            )
            return
        }
        scheduleActiveConversationIndexing(convId)
    }

    fun triggerManualCompaction() {
        val conv = _ui.value.activeConversation ?: return
        val s = strings().chat
        viewModelScope.launch {
            _ui.update { it.copy(isCompacting = true, compactingProgressText = s.compactingProgress) }
            try {
                val out = ContextCompactor.compactNow(
                    chatApi = container.onlineChatApi,
                    conversation = conv,
                    messages = _ui.value.messages,
                    fallbackGroupId = _ui.value.selectedGroupId,
                    fallbackModel = _ui.value.selectedModelId,
                    onChunkProgress = { completed, total ->
                        _ui.update {
                            it.copy(
                                compactingProgressText = s.compactingChunkProgress.fmt(
                                    "current" to completed,
                                    "total" to total,
                                ),
                            )
                        }
                    },
                )
                if (out == null) {
                    container.toastController.show(s.compactNotEnough)
                } else {
                    chatDao.upsertConversation(
                        conv.copy(
                            archivedSummary = out.archivedSummary,
                            summarizedUntilSeq = out.summarizedUntilSeq,
                            updatedAt = System.currentTimeMillis(),
                        ).toEntity(),
                    )
                    if (out.newEpisodes.isNotEmpty()) {
                        chatDao.upsertMemoryEpisodes(out.newEpisodes.map { it.toEntity() })
                    } else {
                        out.newEpisode?.let { chatDao.upsertMemoryEpisode(it.toEntity()) }
                    }
                    scheduleActiveConversationIndexing(conv.id)
                    scheduleWaterlineRecompute(debounceMs = 0L)
                    container.toastController.show(s.compactSuccess)
                }
            } catch (e: Throwable) {
                val reason = e.message?.takeIf { it.isNotBlank() } ?: strings().common.unknownError
                container.toastController.show(s.compactFailedFallback.fmt("reason" to reason), isError = true)
            } finally {
                _ui.update { it.copy(isCompacting = false, compactingProgressText = null) }
            }
        }
    }

    // ==================== Novel Studio Actions ====================

    private suspend fun syncNovelChapterWordCounts(convId: String) {
        val chapters = chatDao.getNovelChaptersForConversation(convId).map { it.toDomain() }
        if (chapters.isEmpty()) return
        val msgs = chatDao.getMessagesForConversation(convId).map { it.toDomain() }
        val recalculated = NovelEngine.recalculateChapterWordCounts(chapters, msgs)
        val changed = recalculated.filterIndexed { idx, ch -> ch != chapters[idx] }
        if (changed.isNotEmpty()) {
            val now = System.currentTimeMillis()
            chatDao.upsertNovelChapters(changed.map { it.copy(updatedAt = now).toEntity() })
        }
    }

    fun createNovelSession(
        presetId: String,
        title: String,
        customIdea: String,
        pov: String? = null,
        targetWords: Int? = null,
        playStyle: String = "hybrid",
        antiAiRules: Boolean = true,
    ) {
        viewModelScope.launch {
            val preset = NovelPresets.findById(presetId)
            val now = System.currentTimeMillis()
            val convId = UUID.randomUUID().toString()
            val ch1Id = UUID.randomUUID().toString()
            val bookTitle = title.trim().ifBlank { preset.name }
            val resolvedPlayStyle = playStyle.takeIf { it in setOf("hybrid", "author", "interactive") } ?: "hybrid"
            val cfg = NovelConfig(
                title = bookTitle,
                synopsis = customIdea.trim().ifBlank { preset.synopsis },
                genre = preset.genre,
                style = preset.style,
                pov = pov?.takeIf { it in setOf("first", "third_limited", "third_omniscient") } ?: preset.pov,
                playStyle = resolvedPlayStyle,
                antiAiRules = antiAiRules,
                worldview = preset.worldview,
                mainOutline = preset.mainOutline,
                authorNote = preset.authorNote,
                targetWordsPerChapter = (targetWords ?: preset.targetWordsPerChapter).coerceIn(500, 10000),
                enableChoices = resolvedPlayStyle != "author",
                activeChapterId = ch1Id,
            )
            val conv = ChatConversation(
                id = convId,
                title = "《$bookTitle》",
                mode = ConversationMode.novel,
                groupId = _ui.value.selectedGroupId,
                model = _ui.value.selectedModelId,
                novel = cfg,
                createdAt = now,
                updatedAt = now,
            )
            val ch1 = NovelChapter(
                id = ch1Id,
                conversationId = convId,
                order = 1,
                title = preset.firstChapterTitle,
                outline = preset.firstChapterOutline,
                status = "writing",
                startSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            val lore = preset.starterLore.map { (cat, lTitle, lContent) ->
                val item = NovelLoreEntry(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    category = cat,
                    title = lTitle,
                    content = lContent,
                    keywords = listOf(lTitle),
                    alwaysActive = cat == "rule",
                    validFromSeq = 0,
                    createdAt = now,
                    updatedAt = now,
                )
                item.copy(estimatedTokens = NovelEngine.estimateLoreTokens(item))
            }
            chatDao.upsertConversation(conv.toEntity())
            chatDao.upsertNovelChapter(ch1.toEntity())
            if (lore.isNotEmpty()) chatDao.upsertNovelLoreList(lore.map { it.toEntity() })
            _ui.update { it.copy(draftDiceRoll = null) }
            _activeConvId.value = convId
            container.toastController.show(strings().novel.wizardCreated.fmt("title" to bookTitle))
        }
    }

    fun createNovelSessionWithAi(
        presetId: String,
        title: String,
        customIdea: String,
        pov: String? = null,
        targetWords: Int? = null,
        playStyle: String = "hybrid",
        antiAiRules: Boolean = true,
        onCompleted: () -> Unit = {},
    ) {
        if (_ui.value.isCreatingNovelWithAi) return
        val gid = _ui.value.selectedGroupId
        val model = _ui.value.selectedModelId
        if (gid == null || model.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isCreatingNovelWithAi = true) }
            try {
                val preset = NovelPresets.findById(presetId)
                val resolvedPov = pov?.takeIf { it in setOf("first", "third_limited", "third_omniscient") } ?: preset.pov
                val resolvedTarget = (targetWords ?: preset.targetWordsPerChapter).coerceIn(500, 10000)
                val resolvedPlayStyle = playStyle.takeIf { it in setOf("hybrid", "author", "interactive") } ?: "hybrid"
                val convId = UUID.randomUUID().toString()
                val ch1Id = UUID.randomUUID().toString()

                val prompt = NovelEngine.buildWizardGenesisMessages(
                    preset = preset,
                    title = title,
                    idea = customIdea,
                    pov = resolvedPov,
                    targetWordsPerChapter = resolvedTarget,
                )
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(
                        group_id = gid,
                        model = model,
                        messages = prompt,
                        stream = false,
                        temperature = 0.8,
                        max_tokens = 2400,
                    ),
                )
                val genesis = NovelEngine.parseWizardGenesisResponse(
                    raw = res.content,
                    preset = preset,
                    fallbackTitle = title,
                    conversationId = convId,
                )
                val now = System.currentTimeMillis()
                val bookTitle = genesis?.title ?: title.trim().ifBlank { preset.name }
                val openingProse = genesis?.openingProse.orEmpty().trim()
                val openingWords = NovelExport.countProseWords(openingProse)

                val cfg = NovelConfig(
                    title = bookTitle,
                    synopsis = genesis?.synopsis ?: customIdea.trim().ifBlank { preset.synopsis },
                    genre = preset.genre,
                    style = genesis?.style ?: preset.style,
                    pov = resolvedPov,
                    playStyle = resolvedPlayStyle,
                    antiAiRules = antiAiRules,
                    worldview = genesis?.worldview ?: preset.worldview,
                    mainOutline = genesis?.mainOutline ?: preset.mainOutline,
                    authorNote = genesis?.authorNote ?: preset.authorNote,
                    targetWordsPerChapter = resolvedTarget,
                    enableChoices = resolvedPlayStyle != "author",
                    activeChapterId = ch1Id,
                )
                val conv = ChatConversation(
                    id = convId,
                    title = "《$bookTitle》",
                    mode = ConversationMode.novel,
                    groupId = gid,
                    model = model,
                    novel = cfg,
                    createdAt = now,
                    updatedAt = now,
                )
                val ch1 = NovelChapter(
                    id = ch1Id,
                    conversationId = convId,
                    order = 1,
                    title = genesis?.firstChapterTitle ?: preset.firstChapterTitle,
                    outline = genesis?.firstChapterOutline ?: preset.firstChapterOutline,
                    status = "writing",
                    wordCount = openingWords,
                    startSeq = 0,
                    createdAt = now,
                    updatedAt = now,
                )
                val lore = genesis?.starterLore?.ifEmpty { null } ?: preset.starterLore.map { (cat, lTitle, lContent) ->
                    val item = NovelLoreEntry(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        category = cat,
                        title = lTitle,
                        content = lContent,
                        keywords = listOf(lTitle),
                        alwaysActive = cat == "rule",
                        validFromSeq = 0,
                        createdAt = now,
                        updatedAt = now,
                    )
                    item.copy(estimatedTokens = NovelEngine.estimateLoreTokens(item))
                }
                chatDao.upsertConversation(conv.toEntity())
                chatDao.upsertNovelChapter(ch1.toEntity())
                if (lore.isNotEmpty()) chatDao.upsertNovelLoreList(lore.map { it.toEntity() })
                if (openingProse.isNotBlank()) {
                    val openingMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = openingProse,
                        model = model,
                        createdAt = now,
                        seq = 1,
                        status = "done",
                        tokenEstimate = ContextBuilder.estimateMessageTokens(openingProse),
                        variants = listOf(
                            MessageVariant(
                                id = UUID.randomUUID().toString(),
                                content = openingProse,
                                createdAt = now,
                                model = model,
                            ),
                        ),
                    )
                    chatDao.upsertMessage(openingMsg.toEntity())
                }
                _ui.update { it.copy(draftDiceRoll = null) }
                _activeConvId.value = convId
                container.toastController.show(strings().novel.wizardCreated.fmt("title" to bookTitle))
                onCompleted()
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isCreatingNovelWithAi = false) }
            }
        }
    }

    fun updateNovelConfig(transform: (NovelConfig) -> NovelConfig) {
        viewModelScope.launch {
            val activeId = _ui.value.activeConversation?.id ?: return@launch
            val conv = chatDao.getConversationById(activeId)?.toDomain() ?: _ui.value.activeConversation ?: return@launch
            val cur = conv.novel ?: return@launch
            val updated = transform(cur)
            if (updated == cur) return@launch
            val nextConvTitle = if (updated.title.trim() != cur.title.trim() && updated.title.isNotBlank()) {
                "《${updated.title.trim()}》"
            } else {
                conv.title
            }
            chatDao.upsertConversation(
                conv.copy(
                    title = nextConvTitle,
                    novel = updated,
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            recomputeWaterline()
        }
    }

    fun addOrSelectNovelChapter(
        title: String,
        outline: String,
        markPreviousDone: Boolean = false,
        activateImmediately: Boolean = true,
    ) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val chapters = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
            val nextOrder = (chapters.maxOfOrNull { it.order } ?: 0) + 1
            val tip = TimelineReconciler.findActiveTipSeq(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })

            val activeCh = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.maxByOrNull { it.order }
            if (activeCh != null && (markPreviousDone || activateImmediately)) {
                val closedEnd = if (tip >= activeCh.startSeq) tip else activeCh.endSeq
                chatDao.upsertNovelChapter(
                    activeCh.copy(
                        status = if (markPreviousDone) "done" else activeCh.status,
                        endSeq = closedEnd,
                        updatedAt = now,
                    ).toEntity(),
                )
            }

            val ch = NovelChapter(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                order = nextOrder,
                title = title.trim().ifBlank { "第${nextOrder}章" },
                outline = outline.trim(),
                status = if (activateImmediately) "writing" else "draft",
                startSeq = tip + 1,
                createdAt = now,
                updatedAt = now,
            )
            chatDao.upsertNovelChapter(ch.toEntity())
            if (activateImmediately) {
                chatDao.upsertConversation(conv.copy(novel = novel.copy(activeChapterId = ch.id), updatedAt = now).toEntity())
            }
            syncNovelChapterWordCounts(conv.id)
        }
    }

    fun updateNovelChapter(chapter: NovelChapter) {
        val conv = _ui.value.activeConversation ?: return
        viewModelScope.launch {
            val normalized = chapter.copy(
                title = chapter.title.trim().ifBlank { "第${chapter.order}章" },
                outline = chapter.outline.trim(),
                summary = chapter.summary.trim(),
                status = chapter.status.takeIf { it in setOf("draft", "writing", "done") } ?: "writing",
                updatedAt = System.currentTimeMillis(),
            )
            chatDao.upsertNovelChapter(normalized.toEntity())
            syncNovelChapterWordCounts(conv.id)
            recomputeWaterline()
        }
    }

    fun deleteNovelChapter(chapterId: String) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        viewModelScope.launch {
            val chapters = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
            if (chapters.size <= 1) return@launch
            chatDao.deleteNovelChapter(chapterId)
            val remaining = chapters.filterNot { it.id == chapterId }.sortedBy { it.order }
            val now = System.currentTimeMillis()
            val reindexed = remaining.mapIndexed { idx, ch ->
                ch.copy(order = idx + 1, updatedAt = now)
            }
            if (reindexed.isNotEmpty()) {
                chatDao.upsertNovelChapters(reindexed.map { it.toEntity() })
            }
            if (novel.activeChapterId == chapterId) {
                val nextActive = reindexed.lastOrNull()?.id
                chatDao.upsertConversation(conv.copy(novel = novel.copy(activeChapterId = nextActive), updatedAt = now).toEntity())
            }
            syncNovelChapterWordCounts(conv.id)
        }
    }

    fun finishActiveChapterAndAdvance(
        nextTitle: String = "",
        nextOutline: String = "",
        autoRecap: Boolean = true,
    ) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val currentChapters = _ui.value.novelChapters.sortedBy { it.order }
        val activeCh = currentChapters.firstOrNull { it.id == novel.activeChapterId } ?: currentChapters.lastOrNull()
        val currentActiveId = activeCh?.id
        val nextPlannedDraft = if (nextTitle.isBlank() && nextOutline.isBlank() && activeCh != null) {
            currentChapters.firstOrNull { it.order > activeCh.order && it.status == "draft" && it.wordCount == 0 }
        } else {
            null
        }
        if (nextPlannedDraft != null && activeCh != null) {
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val tip = TimelineReconciler.findActiveTipSeq(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })
                chatDao.upsertNovelChapter(
                    activeCh.copy(
                        status = "done",
                        endSeq = if (tip >= activeCh.startSeq) tip else activeCh.endSeq,
                        updatedAt = now,
                    ).toEntity(),
                )
                chatDao.upsertNovelChapter(
                    nextPlannedDraft.copy(
                        status = "writing",
                        startSeq = tip + 1,
                        endSeq = null,
                        updatedAt = now,
                    ).toEntity(),
                )
                chatDao.upsertConversation(
                    conv.copy(novel = novel.copy(activeChapterId = nextPlannedDraft.id), updatedAt = now).toEntity(),
                )
                syncNovelChapterWordCounts(conv.id)
                if (autoRecap) generateChapterRecap(activeCh.id)
            }
        } else {
            addOrSelectNovelChapter(
                title = nextTitle,
                outline = nextOutline,
                markPreviousDone = true,
                activateImmediately = true,
            )
            if (autoRecap && currentActiveId != null) {
                generateChapterRecap(currentActiveId)
            }
        }
    }

    fun generateChapterRecap(chapterId: String) {
        if (_ui.value.isGeneratingRecap) return
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId ?: return
        val model = conv.model ?: _ui.value.selectedModelId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingRecap = true) }
            try {
                val chapters = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
                val target = chapters.firstOrNull { it.id == chapterId } ?: return@launch
                val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
                val section = NovelExport.groupMessagesByChapter(chapters, msgs).firstOrNull { it.chapter?.id == chapterId }
                if (section == null || section.messages.isEmpty()) {
                    container.toastController.show(strings().novel.recapEmpty)
                    return@launch
                }
                val prompt = NovelEngine.buildChapterRecapMessages(novel, target, section.messages)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.4, max_tokens = 400),
                )
                val summary = res.content.trim()
                if (summary.isNotEmpty()) {
                    chatDao.upsertNovelChapter(target.copy(summary = summary, updatedAt = System.currentTimeMillis()).toEntity())
                    container.toastController.show(strings().novel.recapGenerated)
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingRecap = false) }
            }
        }
    }

    fun selectActiveChapter(chapterId: String) {
        viewModelScope.launch {
            val activeId = _ui.value.activeConversation?.id ?: return@launch
            val conv = chatDao.getConversationById(activeId)?.toDomain() ?: return@launch
            val novel = conv.novel ?: return@launch
            val chapters = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
            val target = chapters.firstOrNull { it.id == chapterId } ?: return@launch
            val now = System.currentTimeMillis()
            if (target.status == "draft" && target.wordCount == 0) {
                val tip = TimelineReconciler.findActiveTipSeq(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })
                val prevActive = chapters.firstOrNull { it.id == novel.activeChapterId && it.id != target.id }
                if (prevActive != null && prevActive.endSeq == null && tip >= prevActive.startSeq) {
                    chatDao.upsertNovelChapter(prevActive.copy(endSeq = tip, updatedAt = now).toEntity())
                }
                chatDao.upsertNovelChapter(
                    target.copy(
                        status = "writing",
                        startSeq = tip + 1,
                        endSeq = null,
                        updatedAt = now,
                    ).toEntity(),
                )
            }
            chatDao.upsertConversation(
                conv.copy(novel = novel.copy(activeChapterId = chapterId), updatedAt = now).toEntity(),
            )
            syncNovelChapterWordCounts(conv.id)
            recomputeWaterline()
        }
    }

    fun upsertNovelLore(category: String, title: String, keywords: String, content: String, alwaysActive: Boolean) {
        val conv = _ui.value.activeConversation ?: return
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val kws = keywords.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
            val entry = NovelLoreEntry(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                category = category,
                title = title.trim(),
                content = content.trim(),
                keywords = kws.ifEmpty { listOf(title.trim()) },
                alwaysActive = alwaysActive,
                validFromSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            chatDao.upsertNovelLore(entry.copy(estimatedTokens = NovelEngine.estimateLoreTokens(entry)).toEntity())
            scheduleActiveConversationIndexing(conv.id)
            recomputeWaterline()
        }
    }

    fun saveForeshadowToLore(foreshadowText: String) {
        val clean = foreshadowText.trim()
        if (clean.isEmpty()) return
        val title = clean.take(16)
        upsertNovelLore(
            category = "foreshadow",
            title = title,
            keywords = title,
            content = clean,
            alwaysActive = false,
        )
        container.toastController.show(strings().novel.loreAddBtn)
    }

    fun extractNovelLoreNow() {
        if (_ui.value.isExtractingNovelLore) return
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId
        val model = conv.model ?: _ui.value.selectedModelId
        if (gid == null || model.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isExtractingNovelLore = true) }
            try {
                val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
                val activeMsgs = TimelineReconciler.activeMessages(msgs)
                if (activeMsgs.none { it.role == "assistant" && NovelExport.stripInternalTags(it.content).isNotBlank() }) {
                    container.toastController.show(strings().novel.loreExtractNoNew)
                    return@launch
                }
                val existing = chatDao.getNovelLoreForConversation(conv.id).map { it.toDomain() }
                val tipSeq = activeMsgs.maxOfOrNull { it.seq } ?: 0
                val prompt = NovelEngine.buildLoreExtractMessages(novel, activeMsgs, existing)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.3, max_tokens = 1200),
                )
                val extracted = NovelEngine.parseLoreExtractResponse(res.content, conv.id, tipSeq, existing)
                if (extracted.isEmpty()) {
                    container.toastController.show(strings().novel.loreExtractNoNew)
                } else {
                    chatDao.upsertNovelLoreList(extracted.map { it.toEntity() })
                    scheduleActiveConversationIndexing(conv.id)
                    recomputeWaterline()
                    val isZh = container.settingsState.value.language == AppLanguage.ZH
                    container.toastController.show(
                        if (isZh) "已提炼并收录 ${extracted.size} 条设定/伏笔" else "Extracted ${extracted.size} lore entries",
                    )
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isExtractingNovelLore = false) }
            }
        }
    }

    fun deleteNovelLore(id: String) {
        val convId = _ui.value.activeConversation?.id
        viewModelScope.launch {
            chatDao.deleteNovelLore(id)
            container.retrievalCoordinator.deleteOwnerVector(RetrievalCoordinator.OWNER_NOVEL_LORE, id, conversationId = convId)
            recomputeWaterline()
        }
    }

    fun toggleNovelLoreEnabled(id: String) {
        viewModelScope.launch {
            val existing = _ui.value.novelLore.firstOrNull { it.id == id } ?: return@launch
            chatDao.upsertNovelLore(
                existing.copy(
                    enabled = !existing.enabled,
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            if (!existing.enabled) scheduleActiveConversationIndexing(existing.conversationId)
            recomputeWaterline()
        }
    }

    fun toggleNovelLoreAlwaysActive(id: String) {
        viewModelScope.launch {
            val existing = _ui.value.novelLore.firstOrNull { it.id == id } ?: return@launch
            chatDao.upsertNovelLore(
                existing.copy(
                    alwaysActive = !existing.alwaysActive,
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            recomputeWaterline()
        }
    }

    fun updateNovelLore(entry: NovelLoreEntry) {
        if (entry.title.isBlank() || entry.content.isBlank()) return
        viewModelScope.launch {
            val cleanTitle = entry.title.trim()
            val cleanContent = entry.content.trim()
            val cleanKeywords = entry.keywords.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(cleanTitle) }
            val cleanAliases = entry.aliases.map { it.trim() }.filter { it.isNotEmpty() }
            val normalized = entry.copy(
                title = cleanTitle,
                content = cleanContent,
                keywords = cleanKeywords,
                aliases = cleanAliases,
                updatedAt = System.currentTimeMillis(),
            )
            chatDao.upsertNovelLore(
                normalized.copy(estimatedTokens = NovelEngine.estimateLoreTokens(normalized)).toEntity(),
            )
            scheduleActiveConversationIndexing(normalized.conversationId)
            recomputeWaterline()
        }
    }

    fun generateThreePathDrafts(hint: String) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId
        val model = conv.model ?: _ui.value.selectedModelId
        if (gid == null || model.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingDrafts = true) }
            try {
                val activeCh = _ui.value.novelChapters.firstOrNull { it.id == novel.activeChapterId }
                val prompt = NovelEngine.buildThreePathDraftMessages(novel, activeCh, _ui.value.messages, hint)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.85, max_tokens = 1500),
                )
                val options = NovelEngine.parseThreePathDrafts(res.content)
                _ui.update { it.copy(threePathDrafts = options) }
                if (options.isEmpty()) {
                    container.toastController.show(strings().novel.threePathsFailed, isError = true)
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Draft generation failed", isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingDrafts = false) }
            }
        }
    }

    fun adoptThreePathDraft(option: NovelDraftOption, sendImmediately: Boolean) {
        val directive = buildString {
            append("按走向「${option.title}」推进：${option.summary}")
            if (option.opening.isNotBlank()) {
                append("\n参考开篇语感：${option.opening}")
            }
        }
        if (sendImmediately) {
            setDraftNovelKind(NovelInputKind.branch)
            sendMessage(overrideText = directive)
        } else {
            updateDraftText(option.opening.ifBlank { directive })
        }
    }

    fun runOutlineChat(instruction: String) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId
        val model = conv.model ?: _ui.value.selectedModelId
        if (gid == null || model.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        if (instruction.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingDrafts = true) }
            try {
                val prompt = NovelEngine.buildOutlineChatMessages(novel, _ui.value.novelChapters, instruction)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.7, max_tokens = 1500),
                )
                _ui.update { it.copy(outlineChatReply = res.content.trim()) }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Outline chat failed", isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingDrafts = false) }
            }
        }
    }

    fun applyOutlineToBook(outlineText: String) {
        val clean = outlineText.trim()
        if (clean.isEmpty()) return
        updateNovelConfig { it.copy(mainOutline = clean) }
        container.toastController.show(strings().novel.outlineChatApplied)
    }

    fun syncChaptersFromOutline(outlineDraft: String) {
        if (_ui.value.isSyncingOutlineChapters || outlineDraft.isBlank()) return
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId
        val model = conv.model ?: _ui.value.selectedModelId
        if (gid == null || model.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isSyncingOutlineChapters = true) }
            try {
                val existing = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
                val prompt = NovelEngine.buildOutlineChapterSyncMessages(novel, existing, outlineDraft)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.4, max_tokens = 1200),
                )
                val parsed = NovelEngine.parseSuggestedChapters(res.content)
                if (parsed.isEmpty()) {
                    container.toastController.show(strings().common.unknownError, isError = true)
                    return@launch
                }
                val now = System.currentTimeMillis()
                var nextOrder = (existing.maxOfOrNull { it.order } ?: 0) + 1
                val tip = TimelineReconciler.findActiveTipSeq(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })
                val newChapters = parsed.map { spec ->
                    val ch = NovelChapter(
                        id = UUID.randomUUID().toString(),
                        conversationId = conv.id,
                        order = nextOrder,
                        title = spec.title,
                        outline = spec.outline,
                        status = "draft",
                        startSeq = tip + nextOrder * 1000,
                        createdAt = now,
                        updatedAt = now,
                    )
                    nextOrder++
                    ch
                }
                chatDao.upsertNovelChapters(newChapters.map { it.toEntity() })
                container.toastController.show(strings().novel.outlineChatApplyChapters.fmt("count" to newChapters.size))
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isSyncingOutlineChapters = false) }
            }
        }
    }

    fun runPolish(prose: String, instruction: String, presetMode: String? = null) {
        val conv = _ui.value.activeConversation ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId
        val model = conv.model ?: _ui.value.selectedModelId
        if (gid == null || model.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        if (prose.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingDrafts = true) }
            try {
                val prompt = NovelEngine.buildPolishMessages(prose, conv.novel?.style.orEmpty(), instruction, presetMode)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.65, max_tokens = 2200),
                )
                _ui.update { it.copy(polishResult = NovelExport.stripInternalTags(res.content)) }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Polish failed", isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingDrafts = false) }
            }
        }
    }

    fun applyPolishAsVariant(polishedText: String) {
        val clean = NovelExport.stripInternalTags(polishedText)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val lastAssistant = TimelineReconciler.activeMessages(msgs).lastOrNull { it.role == "assistant" } ?: return@launch
            val now = System.currentTimeMillis()
            val baseVariants = if (lastAssistant.variants.isNotEmpty()) {
                lastAssistant.variants
            } else {
                listOf(
                    MessageVariant(
                        id = UUID.randomUUID().toString(),
                        content = lastAssistant.content,
                        reasoning = lastAssistant.reasoning,
                        createdAt = lastAssistant.createdAt,
                        usage = lastAssistant.usage,
                        model = lastAssistant.model,
                        novelStatusSnapshot = conv.novel?.currentStatus,
                        novelChoices = lastAssistant.novelChoices,
                    ),
                )
            }
            val polishedVariant = MessageVariant(
                id = UUID.randomUUID().toString(),
                content = clean,
                createdAt = now,
                model = lastAssistant.model,
                novelStatusSnapshot = conv.novel?.currentStatus,
                novelChoices = lastAssistant.novelChoices,
            )
            val nextVariants = baseVariants + polishedVariant
            val updatedMsg = lastAssistant.copy(
                content = clean,
                editedAt = now,
                tokenEstimate = ContextBuilder.estimateMessageTokens(clean),
                variants = nextVariants,
                activeVariantIndex = nextVariants.lastIndex,
            )
            chatDao.upsertMessage(updatedMsg.toEntity())
            syncNovelChapterWordCounts(conv.id)
            recomputeWaterline()
            container.toastController.show(strings().novel.polishDone)
        }
    }

    fun refreshNovelChoices() {
        if (_ui.value.isStreaming || _ui.value.isRefreshingChoices) return
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId
        val model = conv.model ?: _ui.value.selectedModelId
        if (gid == null || model.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshingChoices = true) }
            try {
                val activeCh = _ui.value.novelChapters.firstOrNull { it.id == novel.activeChapterId }
                val prompt = NovelEngine.buildRefreshChoicesMessages(novel, activeCh, _ui.value.messages)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.85, max_tokens = 500),
                )
                val newChoices = NovelEngine.parseChoicesOnly(res.content)
                if (newChoices.isEmpty()) {
                    container.toastController.show(strings().common.unknownError, isError = true)
                    return@launch
                }
                val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
                val lastAssistant = TimelineReconciler.activeMessages(msgs).lastOrNull { it.role == "assistant" }
                if (lastAssistant != null) {
                    val updatedVariants = if (lastAssistant.variants.isNotEmpty()) {
                        val activeIdx = lastAssistant.activeVariantIndex.coerceIn(0, lastAssistant.variants.lastIndex)
                        lastAssistant.variants.mapIndexed { idx, v ->
                            if (idx == activeIdx) v.copy(novelChoices = newChoices) else v
                        }
                    } else {
                        lastAssistant.variants
                    }
                    chatDao.upsertMessage(
                        lastAssistant.copy(novelChoices = newChoices, variants = updatedVariants).toEntity(),
                    )
                }
                val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                val freshNovel = freshConv.novel ?: novel
                chatDao.upsertConversation(
                    freshConv.copy(
                        novel = freshNovel.copy(pendingChoices = newChoices),
                        updatedAt = System.currentTimeMillis(),
                    ).toEntity(),
                )
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isRefreshingChoices = false) }
            }
        }
    }

    fun setPureReadingMode(enabled: Boolean) {
        _ui.update { it.copy(pureReadingMode = enabled) }
    }

    fun updateReaderPrefs(
        fontSize: Int? = null,
        paper: String? = null,
        lineHeight: Float? = null,
        serif: Boolean? = null,
        indent: Boolean? = null,
    ) {
        _ui.update {
            it.copy(
                readerFontSizeSp = fontSize?.coerceIn(13, 24) ?: it.readerFontSizeSp,
                readerPaper = paper ?: it.readerPaper,
                readerLineHeight = lineHeight?.coerceIn(1.4f, 2.2f) ?: it.readerLineHeight,
                readerSerif = serif ?: it.readerSerif,
                readerIndent = indent ?: it.readerIndent,
            )
        }
        viewModelScope.launch {
            container.settingsStore.setReaderPrefs(
                paper = paper,
                fontSizeSp = fontSize,
                lineHeight = lineHeight,
                serif = serif,
                indent = indent,
            )
        }
    }

    fun exportNovelToUri(
        uri: Uri,
        contentResolver: android.content.ContentResolver,
        markdown: Boolean,
        includeLore: Boolean = false,
    ) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        viewModelScope.launch {
            try {
                val text = if (markdown) {
                    NovelExport.formatAsMarkdown(
                        novel = novel,
                        chapters = _ui.value.novelChapters,
                        messages = _ui.value.messages,
                        lore = _ui.value.novelLore,
                        includeLoreAppendix = includeLore,
                    )
                } else {
                    NovelExport.formatAsTxt(
                        novel = novel,
                        chapters = _ui.value.novelChapters,
                        messages = _ui.value.messages,
                        lore = _ui.value.novelLore,
                        includeLoreAppendix = includeLore,
                    )
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                container.toastController.show(strings().novel.exportDone)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    // ==================== RP Studio Actions ====================

    fun createRpSessionFromPreset(
        presetId: String,
        customTitle: String? = null,
        playStyle: RpPlayStyle? = null,
        playerName: String? = null,
        playerPersona: String? = null,
    ) {
        viewModelScope.launch {
            val preset = RpPresets.findById(presetId)
            val resolvedStyle = playStyle ?: preset.config.playStyle
            val rp = preset.config.copy(
                title = customTitle?.trim()?.ifBlank { null } ?: preset.config.title,
                playStyle = resolvedStyle,
                theaterMode = resolvedStyle == RpPlayStyle.chengguang,
                playerName = playerName?.trim()?.ifBlank { null } ?: preset.config.playerName,
                playerPersona = playerPersona?.trim()?.ifBlank { null } ?: preset.config.playerPersona,
            )
            val now = System.currentTimeMillis()
            val starterLore = preset.starterLore.map { s ->
                val entry = NovelLoreEntry(
                    id = UUID.randomUUID().toString(),
                    conversationId = "pending",
                    category = s.category,
                    title = s.title,
                    content = s.content,
                    keywords = s.keywords.ifEmpty { listOf(s.title) },
                    alwaysActive = s.alwaysActive,
                    validFromSeq = 0,
                    createdAt = now,
                    updatedAt = now,
                )
                entry.copy(estimatedTokens = NovelEngine.estimateLoreTokens(entry))
            }
            insertRpConversation(rp, starterLore)
        }
    }

    fun importSillyTavernCardUri(uri: Uri, contentResolver: android.content.ContentResolver) {
        val isZh = strings().language == AppLanguage.ZH
        viewModelScope.launch {
            try {
                when (val imported = container.assetImportCoordinator.importUri(uri, contentResolver)) {
                    is AssetImportResult.UuGameSaveDetected -> {
                        val rootElem = runCatching { AppJson.parseToJsonElement(imported.rawJson) }.getOrNull()
                            as? kotlinx.serialization.json.JsonObject ?: error("Invalid UUGame save")
                        val bundle = withContext(Dispatchers.Default) {
                            UuSaveCodec.parseJsonElement(rootElem, imported.displayName)
                        }
                        val defaults = defaultChatParams()
                        val prepared = bundle.items.map { item ->
                            val ic = item.conversation
                            val mergedParams = ic.params.copy(
                                maxTokens = ic.params.maxTokens ?: defaults.maxTokens,
                                contextWindowOverride = ic.params.contextWindowOverride ?: defaults.contextWindowOverride,
                                maxHistoryMessages = ic.params.maxHistoryMessages ?: defaults.maxHistoryMessages,
                                activeWindowStartSeq = ic.params.activeWindowStartSeq,
                            )
                            item.copy(
                                conversation = ic.copy(
                                    groupId = ic.groupId ?: _ui.value.selectedGroupId,
                                    model = ic.model?.takeIf { it.isNotBlank() } ?: _ui.value.selectedModelId,
                                    params = mergedParams,
                                ),
                            )
                        }
                        val res = ExternalImportService.persistSelected(container.database, prepared)
                        val convIds = prepared.map { it.conversation.id }
                        container.scope.launch {
                            ExternalImportService.cacheRemoteRpAvatars(
                                appContext = container.appContext,
                                httpClient = container.apiClient.standardClient,
                                db = container.database,
                                conversationIds = convIds,
                            )
                        }
                        res.firstConversationId?.let { cid ->
                            _ui.update { it.copy(draftDiceRoll = null) }
                            _activeConvId.value = cid
                        }
                        val title = prepared.firstOrNull()?.conversation?.title.orEmpty()
                        container.toastController.show(strings().rp.importSuccess.fmt("name" to title))
                    }

                    is AssetImportResult.CharacterCardImported -> {
                        insertRpConversation(
                            rp = imported.rpConfig,
                            importedLore = emptyList(),
                            characterCardId = imported.card.id,
                            mountedWorldbookId = imported.worldbook?.id,
                        )
                        container.toastController.show(strings().rp.importSuccess.fmt("name" to imported.card.name))
                    }

                    is AssetImportResult.WorldbookImported -> {
                        container.toastController.show(
                            if (isZh) "已导入世界书「${imported.worldbook.name}」到「创作 → 设定」"
                            else "Imported worldbook \"${imported.worldbook.name}\" to library",
                        )
                    }

                    is AssetImportResult.PromptPresetImported -> {
                        container.toastController.show(
                            if (isZh) "已导入预设「${imported.preset.name}」到「创作 → 设定」"
                            else "Imported preset \"${imported.preset.name}\" to library",
                        )
                    }

                    else -> error(if (isZh) "未识别到可导入的角色卡" else "Unrecognized character card")
                }
            } catch (e: Throwable) {
                container.toastController.show(
                    strings().rp.importFailed.fmt("reason" to (e.message ?: "Unknown")),
                    isError = true,
                )
            }
        }
    }

    fun importLorebookOrPresetUri(uri: Uri, contentResolver: ContentResolver) {
        val conv = _ui.value.activeConversation ?: return
        val isZh = strings().language == AppLanguage.ZH
        viewModelScope.launch {
            try {
                fun mergeRegexIntoRp(scripts: List<RpRegexScript>): Int {
                    if (scripts.isEmpty() || conv.rp == null) return 0
                    var added = 0
                    updateRpConfig { cfg ->
                        val existingKeys = cfg.regexScripts
                            .map { "${it.findRegex}|${it.replaceString}" }
                            .toHashSet()
                        val toAdd = scripts.filter { existingKeys.add("${it.findRegex}|${it.replaceString}") }
                        added = toAdd.size
                        if (toAdd.isEmpty()) cfg else cfg.copy(regexScripts = cfg.regexScripts + toAdd)
                    }
                    return added
                }

                when (val imported = container.assetImportCoordinator.importUri(uri, contentResolver)) {
                    is AssetImportResult.WorldbookImported -> {
                        mountWorldbook(imported.worldbook.id, convId = conv.id)
                        container.toastController.show(
                            strings().rp.importLorebookSuccess.fmt(
                                "name" to imported.worldbook.name,
                                "count" to imported.entries.size,
                            ),
                        )
                    }

                    is AssetImportResult.PromptPresetImported -> {
                        val addedRegex = mergeRegexIntoRp(imported.regexScripts)
                        val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                        chatDao.upsertConversation(
                            fresh.copy(
                                presetId = imported.preset.id,
                                updatedAt = System.currentTimeMillis(),
                            ).toEntity(),
                        )
                        val baseMsg = strings().rp.importPresetSuccess.fmt("name" to imported.preset.name)
                        val extras = if (addedRegex > 0) {
                            if (isZh) "+$addedRegex 条正则" else "+$addedRegex regex"
                        } else {
                            ""
                        }
                        container.toastController.show(if (extras.isEmpty()) baseMsg else "$baseMsg（$extras）")
                    }

                    is AssetImportResult.QuickRepliesImported -> {
                        val qr = imported.quickReplies
                        if (conv.rp != null) {
                            updateRpConfig { cfg ->
                                cfg.copy(quickActions = (cfg.quickActions + qr.actions).distinct())
                            }
                            container.toastController.show(
                                if (isZh) "已导入快速回复「${qr.name}」（${qr.actions.size} 条）" else "Imported quick replies \"${qr.name}\" (${qr.actions.size})",
                            )
                        } else {
                            container.toastController.show(
                                if (isZh) "快速回复仅适用于文游会话" else "Quick replies apply to RP sessions only",
                                isError = true,
                            )
                        }
                    }

                    is AssetImportResult.RegexScriptsImported -> {
                        val addedRegex = mergeRegexIntoRp(imported.scripts)
                        if (conv.rp != null) {
                            container.toastController.show(
                                if (isZh) "已导入 $addedRegex 条正则脚本" else "Imported $addedRegex regex scripts",
                            )
                        } else {
                            container.toastController.show(
                                if (isZh) "正则脚本仅适用于文游会话" else "Regex scripts apply to RP sessions only",
                                isError = true,
                            )
                        }
                    }

                    is AssetImportResult.SettingsImported -> {
                        val addedRegex = mergeRegexIntoRp(imported.regexScripts)
                        if (conv.rp != null && imported.authorNote.isNotBlank()) {
                            updateRpConfig { cfg ->
                                cfg.copy(authorNote = mergeTextSection(cfg.authorNote, imported.authorNote))
                            }
                        }
                        container.toastController.show(
                            if (isZh) "已从酒馆设置导入（+$addedRegex 条正则）" else "Imported Tavern settings (+$addedRegex regex)",
                        )
                    }

                    is AssetImportResult.CharacterCardImported -> {
                        val wb = imported.worldbook
                        if (wb != null) {
                            mountWorldbook(wb.id, convId = conv.id)
                        }
                        val addedRegex = mergeRegexIntoRp(imported.rpConfig.regexScripts)
                        container.toastController.show(
                            if (isZh) {
                                "已从角色卡「${imported.card.name}」挂载 ${imported.entryCount} 条世界书" +
                                    if (addedRegex > 0) " · $addedRegex 条正则" else ""
                            } else {
                                "Mounted ${imported.entryCount} lore entries" +
                                    (if (addedRegex > 0) " · $addedRegex regex" else "") +
                                    " from \"${imported.card.name}\""
                            },
                        )
                    }

                    else -> error(
                        if (isZh) "未识别到世界书、酒馆预设或正则 JSON"
                        else "Unrecognized lorebook, Tavern preset, or regex JSON",
                    )
                }
            } catch (e: Throwable) {
                container.toastController.show(
                    strings().rp.importFailed.fmt("reason" to (e.message ?: "Unknown")),
                    isError = true,
                )
            }
        }
    }

    fun importRpRegexJsonUri(uri: Uri, contentResolver: ContentResolver) {
        val conv = _ui.value.activeConversation ?: return
        if (conv.rp == null) return
        val isZh = strings().language == AppLanguage.ZH
        viewModelScope.launch {
            try {
                val scripts = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error(if (isZh) "文件为空" else "Empty file")
                    val text = if (RpCardCodec.isPngBytes(bytes)) {
                        RpCardCodec.extractCardJsonFromPng(bytes)
                            ?: error(if (isZh) "PNG 中未发现角色卡元数据" else "No card metadata in PNG")
                    } else {
                        RpCardCodec.extractCardFromZipBytes(bytes)?.cardJson
                            ?: bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                    }
                    val root = AppJson.parseToJsonElement(text.trim())
                    TavernAssetCodec.parseRegexScriptsJson(root)
                }
                if (scripts.isEmpty()) {
                    error(
                        if (isZh) {
                            "文件中未找到酒馆正则脚本（支持单条/数组/预设/角色卡/settings.json）"
                        } else {
                            "No Tavern regex scripts found (supports single, array, preset, card, or settings.json)"
                        },
                    )
                }
                var addedCount = 0
                updateRpConfig { cfg ->
                    val existingKeys = cfg.regexScripts
                        .map { "${it.findRegex}|${it.replaceString}" }
                        .toHashSet()
                    val fresh = scripts.filter { existingKeys.add("${it.findRegex}|${it.replaceString}") }
                    addedCount = fresh.size
                    if (fresh.isEmpty()) cfg else cfg.copy(regexScripts = cfg.regexScripts + fresh)
                }
                container.toastController.show(
                    if (addedCount > 0) {
                        if (isZh) "已导入 $addedCount 条酒馆正则脚本" else "Imported $addedCount Tavern regex scripts"
                    } else {
                        if (isZh) "正则脚本均已存在（未重复添加）" else "All regex scripts already exist"
                    },
                )
            } catch (e: Throwable) {
                container.toastController.show(
                    strings().rp.importFailed.fmt("reason" to (e.message ?: "Unknown")),
                    isError = true,
                )
            }
        }
    }

    private fun mergeTextSection(existing: String, incoming: String): String {
        val cleanIncoming = incoming.trim()
        if (cleanIncoming.isEmpty()) return existing
        val cleanExisting = existing.trim()
        if (cleanExisting.isEmpty()) return cleanIncoming
        if (cleanExisting.contains(cleanIncoming)) return cleanExisting
        return "$cleanExisting\n\n$cleanIncoming"
    }

    private suspend fun insertRpConversation(
        rp: RpConfig,
        importedLore: List<NovelLoreEntry>,
        characterCardId: String? = null,
        mountedWorldbookId: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val convId = UUID.randomUUID().toString()
        val defaultPersona = assetDao.getDefaultPersona()
        val defaultRpPreset = assetDao.getDefaultRpPromptPreset()
        val effectivePlayerName = defaultPersona?.name?.trim()?.takeIf { it.isNotEmpty() } ?: rp.playerName
        val macroRp = rp.copy(playerName = effectivePlayerName)

        val expandedState = rp.state.copy(
            scene = RpEngine.replaceMacros(rp.state.scene, macroRp),
            chapterTitle = RpEngine.replaceMacros(rp.state.chapterTitle, macroRp),
            inventory = rp.state.inventory.map { RpEngine.replaceMacros(it, macroRp) },
            quests = rp.state.quests.map { RpEngine.replaceMacros(it, macroRp) },
        )
        val expandedChoices = rp.pendingChoices.map { ch ->
            ch.copy(
                label = RpEngine.replaceMacros(ch.label, macroRp),
                actionText = RpEngine.replaceMacros(ch.actionText, macroRp),
                consequenceHint = ch.consequenceHint?.let { RpEngine.replaceMacros(it, macroRp) },
            )
        }
        val normalizedRp = rp.copy(state = expandedState, pendingChoices = expandedChoices)
        val conv = ChatConversation(
            id = convId,
            title = normalizedRp.title,
            mode = ConversationMode.rp,
            groupId = _ui.value.selectedGroupId,
            model = _ui.value.selectedModelId,
            rp = normalizedRp,
            characterCardId = characterCardId,
            personaId = defaultPersona?.id,
            presetId = defaultRpPreset?.id,
            createdAt = now,
            updatedAt = now,
        )
        chatDao.upsertConversation(conv.toEntity())
        if (!mountedWorldbookId.isNullOrBlank()) {
            assetDao.replaceMountedWorldbooks(
                convId,
                listOf(
                    ConversationWorldbookCrossRef(
                        conversationId = convId,
                        worldbookId = mountedWorldbookId,
                        sortOrder = 0,
                        enabled = true,
                        updatedAt = now,
                    ),
                ),
            )
        }
        val rawGreetings = (listOf(normalizedRp.greeting) + normalizedRp.alternateGreetings)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val leadSpeaker = normalizedRp.cast.firstOrNull { !it.isPlayer }?.name?.takeIf { it.isNotBlank() }
        if (rawGreetings.isNotEmpty()) {
            val openingVariants = rawGreetings.map { raw ->
                val rendered = RpEngine.replaceMacros(raw, macroRp)
                MessageVariant(
                    id = UUID.randomUUID().toString(),
                    content = rendered,
                    createdAt = now,
                    rpStateSnapshot = normalizedRp.state,
                    rpCastSnapshot = normalizedRp.cast,
                    rpChoices = normalizedRp.pendingChoices,
                    rpSpeakerName = leadSpeaker,
                )
            }
            val firstContent = openingVariants.first().content
            val greetingMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "assistant",
                content = firstContent,
                createdAt = now,
                seq = 1,
                rpChoices = normalizedRp.pendingChoices,
                status = "done",
                tokenEstimate = ContextBuilder.estimateMessageTokens(firstContent),
                variants = openingVariants,
                activeVariantIndex = 0,
            )
            chatDao.upsertMessage(greetingMsg.toEntity())
        }
        if (importedLore.isNotEmpty()) {
            val normalizedLore = importedLore.map { it.copy(conversationId = convId) }
            chatDao.upsertNovelLoreList(normalizedLore.map { it.toEntity() })
        }
        _ui.update { it.copy(draftDiceRoll = null) }
        _activeConvId.value = convId
    }

    fun bindRpCharacterAvatar(charId: String, uri: Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Empty image")
                    val useCase = container.studioImageGenerationUseCase
                    if (RpCardCodec.isPngBytes(bytes)) {
                        useCase.saveMediaBytesSync(bytes, "avatar", "png")
                    } else {
                        val pngBytes = useCase.transcodeToPngBytes(bytes)
                        if (pngBytes != null) {
                            useCase.saveMediaBytesSync(pngBytes, "avatar", "png")
                        } else {
                            val ext = useCase.inferImageExt(contentResolver.getType(uri))
                            useCase.saveMediaBytesSync(bytes, "avatar", ext)
                        }
                    }
                }
                updateRpConfig { cfg ->
                    cfg.copy(
                        cast = cfg.cast.map { c ->
                            if (c.id == charId) c.copy(avatarPath = savedPath) else c
                        },
                    )
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun bindRpCharacterSprite(
        charId: String,
        expressionKey: String?,
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        viewModelScope.launch {
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Empty image")
                    val useCase = container.studioImageGenerationUseCase
                    val ext = if (RpCardCodec.isPngBytes(bytes)) "png" else useCase.inferImageExt(contentResolver.getType(uri))
                    useCase.saveMediaBytesSync(bytes, "sprite", ext)
                }
                val exprKey = expressionKey?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                updateRpConfig { cfg ->
                    cfg.copy(
                        cast = cfg.cast.map { c ->
                            if (c.id != charId) {
                                c
                            } else if (exprKey == null) {
                                c.copy(
                                    spritePath = savedPath,
                                    avatarPath = c.avatarPath?.takeIf { it.isNotBlank() } ?: savedPath,
                                )
                            } else {
                                c.copy(
                                    expressionSprites = c.expressionSprites + (exprKey to savedPath),
                                    spritePath = if (exprKey == "neutral" && c.spritePath.isNullOrBlank()) savedPath else c.spritePath,
                                    avatarPath = if (exprKey == "neutral" && c.avatarPath.isNullOrBlank()) savedPath else c.avatarPath,
                                )
                            }
                        },
                    )
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun clearRpCharacterSprite(charId: String, expressionKey: String?) {
        val exprKey = expressionKey?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        updateRpConfig { cfg ->
            cfg.copy(
                cast = cfg.cast.map { c ->
                    if (c.id != charId) {
                        c
                    } else if (exprKey == null) {
                        c.copy(spritePath = null)
                    } else {
                        c.copy(
                            expressionSprites = c.expressionSprites.filterKeys { !it.equals(exprKey, ignoreCase = true) },
                        )
                    }
                },
            )
        }
    }

    fun bindRpSceneImage(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Empty image")
                    val useCase = container.studioImageGenerationUseCase
                    val ext = if (RpCardCodec.isPngBytes(bytes)) "png" else useCase.inferImageExt(contentResolver.getType(uri))
                    useCase.saveMediaBytesSync(bytes, "scene", ext)
                }
                updateRpState { it.copy(sceneImagePath = savedPath, activeCgId = null) }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun clearRpSceneImage() {
        updateRpState { it.copy(sceneImagePath = null, activeCgId = null) }
    }

    // ==================== RP 3.0 CG Gallery & AI Art Generation ====================

    fun addRpCgFromUri(
        title: String,
        caption: String,
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        viewModelScope.launch {
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Empty image")
                    val useCase = container.studioImageGenerationUseCase
                    val ext = if (RpCardCodec.isPngBytes(bytes)) "png" else useCase.inferImageExt(contentResolver.getType(uri))
                    useCase.saveMediaBytesSync(bytes, "cg", ext)
                }
                val tipSeq = TimelineReconciler.findActiveTipSeq(_ui.value.messages)
                updateRpConfig { cfg ->
                    val resolvedTitle = title.trim().ifBlank {
                        cfg.state.scene.ifBlank { "CG #${cfg.state.unlockedCgs.size + 1}" }
                    }
                    val record = RpCgRecord(
                        id = UUID.randomUUID().toString(),
                        title = resolvedTitle,
                        caption = caption.trim(),
                        imagePath = savedPath,
                        scene = cfg.state.scene,
                        characterNames = cfg.cast.filter { !it.isPlayer && it.inScene }.map { it.name },
                        unlockedAt = System.currentTimeMillis(),
                        turnSeq = tipSeq,
                    )
                    cfg.copy(state = cfg.state.copy(unlockedCgs = cfg.state.unlockedCgs + record))
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun saveCurrentSceneAsCg(title: String) {
        val tipSeq = TimelineReconciler.findActiveTipSeq(_ui.value.messages)
        updateRpConfig { cfg ->
            val scenePath = cfg.state.sceneImagePath?.trim()?.takeIf { it.isNotEmpty() } ?: return@updateRpConfig cfg
            if (cfg.state.unlockedCgs.any { it.imagePath == scenePath }) {
                return@updateRpConfig cfg
            }
            val resolvedTitle = title.trim().ifBlank {
                cfg.state.scene.ifBlank { "CG #${cfg.state.unlockedCgs.size + 1}" }
            }
            val caption = listOf(cfg.state.time, cfg.state.weather, cfg.state.mood)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            val record = RpCgRecord(
                id = UUID.randomUUID().toString(),
                title = resolvedTitle,
                caption = caption,
                imagePath = scenePath,
                scene = cfg.state.scene,
                characterNames = cfg.cast.filter { !it.isPlayer && it.inScene }.map { it.name },
                unlockedAt = System.currentTimeMillis(),
                turnSeq = tipSeq,
            )
            cfg.copy(
                state = cfg.state.copy(
                    unlockedCgs = cfg.state.unlockedCgs + record,
                    activeCgId = record.id,
                ),
            )
        }
    }

    fun applyCgAsScene(cg: RpCgRecord) {
        updateRpState { st ->
            st.copy(
                sceneImagePath = cg.imagePath,
                activeCgId = cg.id,
            )
        }
    }

    fun deleteRpCg(cgId: String) {
        updateRpState { st ->
            st.copy(
                unlockedCgs = st.unlockedCgs.filterNot { it.id == cgId },
                activeCgId = if (st.activeCgId == cgId) null else st.activeCgId,
            )
        }
    }

    fun generateRpCharacterSprite(
        charId: String,
        expressionKey: String = "neutral",
        customHint: String = "",
    ) {
        if (_ui.value.isGeneratingRpArt) return
        val conv = _ui.value.activeConversation ?: return
        val rp = conv.rp ?: return
        val character = rp.cast.firstOrNull { it.id == charId } ?: return
        val exprKey = expressionKey.trim().lowercase().ifEmpty { "neutral" }
        val exprLabel = strings().rp.expressionLabels[exprKey] ?: exprKey
        val statusLabel = "${strings().rp.aiGeneratingArt} (${character.name} · $exprLabel)"

        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingRpArt = true, rpArtStatusLabel = statusLabel) }
            try {
                val prompt = RpEngine.buildPortraitImagePrompt(
                    rp = rp,
                    character = character,
                    expressionKey = exprKey,
                    customHint = customHint,
                )
                val refPath = if (exprKey != "neutral") {
                    character.expressionSprites["neutral"]?.takeIf { it.isNotBlank() }
                        ?: character.spritePath?.takeIf { it.isNotBlank() }
                        ?: character.avatarPath?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                val generated = container.studioImageGenerationUseCase.generateAndSaveRpMedia(
                    prompt = prompt,
                    aspectRatio = "3:4",
                    referenceFilePath = refPath,
                    noChannelMessage = strings().rp.aiGenArtNoChannel,
                )

                val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                val freshRp = freshConv.rp ?: return@launch
                val updatedCast = freshRp.cast.map { c ->
                    if (c.id != charId) {
                        c
                    } else {
                        c.copy(
                            expressionSprites = c.expressionSprites + (exprKey to generated.relativePath),
                            spritePath = c.spritePath?.takeIf { it.isNotBlank() } ?: generated.relativePath,
                            avatarPath = c.avatarPath?.takeIf { it.isNotBlank() } ?: generated.relativePath,
                        )
                    }
                }
                chatDao.upsertConversation(
                    freshConv.copy(
                        rp = freshRp.copy(cast = updatedCast),
                        updatedAt = System.currentTimeMillis(),
                    ).toEntity(),
                )
                container.toastController.show(strings().rp.aiGenArtDone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingRpArt = false, rpArtStatusLabel = "") }
            }
        }
    }

    fun generateRpSceneCg(
        customHint: String = "",
        saveToGallery: Boolean = true,
    ) {
        if (_ui.value.isGeneratingRpArt) return
        val conv = _ui.value.activeConversation ?: return
        val rp = conv.rp ?: return
        val sceneName = rp.state.scene.ifBlank { rp.title }
        val statusLabel = "${strings().rp.aiGeneratingArt} ($sceneName)"

        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingRpArt = true, rpArtStatusLabel = statusLabel) }
            try {
                val prompt = RpEngine.buildSceneImagePrompt(rp = rp, customHint = customHint)
                val generated = container.studioImageGenerationUseCase.generateAndSaveRpMedia(
                    prompt = prompt,
                    aspectRatio = "9:16",
                    referenceFilePath = null,
                    noChannelMessage = strings().rp.aiGenArtNoChannel,
                )

                val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                val freshRp = freshConv.rp ?: return@launch
                val tipSeq = TimelineReconciler.findActiveTipSeq(_ui.value.messages)
                val cgRecord = if (saveToGallery) {
                    val cgTitle = customHint.trim().take(24).ifBlank {
                        freshRp.state.scene.ifBlank { "${freshRp.title} CG #${freshRp.state.unlockedCgs.size + 1}" }
                    }
                    val caption = customHint.trim().ifBlank {
                        listOf(freshRp.state.time, freshRp.state.weather, freshRp.state.mood)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    }
                    RpCgRecord(
                        id = UUID.randomUUID().toString(),
                        title = cgTitle,
                        caption = caption,
                        imagePath = generated.relativePath,
                        scene = freshRp.state.scene,
                        characterNames = freshRp.cast.filter { !it.isPlayer && it.inScene }.map { it.name },
                        unlockedAt = System.currentTimeMillis(),
                        turnSeq = tipSeq,
                    )
                } else {
                    null
                }
                val nextState = freshRp.state.copy(
                    sceneImagePath = generated.relativePath,
                    unlockedCgs = if (cgRecord != null) freshRp.state.unlockedCgs + cgRecord else freshRp.state.unlockedCgs,
                    activeCgId = cgRecord?.id ?: freshRp.state.activeCgId,
                )
                chatDao.upsertConversation(
                    freshConv.copy(
                        rp = freshRp.copy(state = nextState),
                        updatedAt = System.currentTimeMillis(),
                    ).toEntity(),
                )
                container.toastController.show(strings().rp.aiGenArtDone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingRpArt = false, rpArtStatusLabel = "") }
            }
        }
    }

    fun toggleRpTheaterMode() {
        updateRpConfig { it.copy(theaterMode = !it.theaterMode) }
    }

    fun setRpTheaterMode(enabled: Boolean) {
        updateRpConfig { it.copy(theaterMode = enabled) }
    }

    fun setRpPlayStyle(style: RpPlayStyle) {
        updateRpConfig { it.copy(playStyle = style) }
    }

    fun continueLastAssistantMessage(instruction: String = "") {
        val conv = _ui.value.activeConversation ?: return
        if (chatStreamManager.isStreaming(conv.id)) return
        val groupId = conv.groupId ?: _ui.value.selectedGroupId
        val modelId = conv.model ?: _ui.value.selectedModelId
        if (groupId == null || modelId.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        val rp = conv.rp
        val novel = conv.novel
        if (conv.mode == ConversationMode.rp && rp != null) {
            viewModelScope.launch {
                val allMsgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
                val activeMsgs = TimelineReconciler.activeMessages(allMsgs)
                val lastAssistant = activeMsgs.lastOrNull { it.role == "assistant" && it.status != "error" }
                val loreMatch = NovelEngine.matchNovelLore(
                    lore = _ui.value.novelLore,
                    recentMessages = activeMsgs,
                    draftText = instruction,
                    chapterOutline = "",
                    loreTokenBudget = rp.loreTokenBudget,
                    activeTipSeq = activeMsgs.maxOfOrNull { it.seq } ?: 0,
                    presentEntities = rp.cast.filter { it.inScene }.map { it.name },
                    sceneTitle = rp.state.scene,
                )
                val wireDtos = RpEngine.buildContinueMessages(
                    rp = rp,
                    recentMessages = activeMsgs,
                    instruction = instruction,
                    constantLore = loreMatch.constant,
                    triggeredLore = loreMatch.triggered,
                )
                val readyConv = conv.copy(groupId = groupId, model = modelId)
                launchCompletionForConversation(
                    conv = readyConv,
                    existingMessageForVariant = lastAssistant,
                    continuePrefixText = lastAssistant?.content,
                    overrideMessages = wireDtos,
                )
            }
        } else if (conv.mode == ConversationMode.novel && novel != null) {
            viewModelScope.launch {
                val allMsgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
                val activeMsgs = TimelineReconciler.activeMessages(allMsgs)
                val lastAssistant = activeMsgs.lastOrNull { it.role == "assistant" && it.status != "error" }
                val chapters = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
                val activeChapter = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.maxByOrNull { it.order }
                val loreList = _ui.value.novelLore
                val loreMatch = NovelEngine.matchNovelLore(
                    lore = loreList,
                    recentMessages = activeMsgs,
                    draftText = instruction,
                    chapterOutline = activeChapter?.outline.orEmpty(),
                    loreTokenBudget = novel.loreTokenBudget,
                    activeTipSeq = activeMsgs.maxOfOrNull { it.seq } ?: 0,
                    presentEntities = novel.currentStatus?.present.orEmpty(),
                    sceneTitle = novel.currentStatus?.location,
                )
                val wireDtos = NovelEngine.buildContinueMessages(
                    novel = novel,
                    activeChapter = activeChapter,
                    recentMessages = activeMsgs,
                    constantLore = loreMatch.constant,
                    triggeredLore = loreMatch.triggered,
                    existingLoreTitles = loreList.map { it.title },
                    previousChapters = chapters,
                    instruction = instruction,
                )
                val readyConv = conv.copy(groupId = groupId, model = modelId)
                launchCompletionForConversation(
                    conv = readyConv,
                    existingMessageForVariant = lastAssistant,
                    continuePrefixText = lastAssistant?.content?.let { NovelExport.stripInternalTags(it) },
                    overrideMessages = wireDtos,
                )
            }
        }
    }

    fun impersonatePlayerDraft() {
        if (_ui.value.isStreaming || _ui.value.isImpersonating) return
        val conv = _ui.value.activeConversation ?: return
        val rp = conv.rp ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId
        val model = conv.model ?: _ui.value.selectedModelId
        if (gid == null || model.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isImpersonating = true) }
            try {
                val prompt = RpEngine.buildImpersonateMessages(rp, _ui.value.messages)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(
                        group_id = gid,
                        model = model,
                        messages = prompt,
                        stream = false,
                        temperature = conv.params.temperature.coerceIn(0.6, 1.0),
                        max_tokens = 320,
                    ),
                )
                val draft = RpEngine.stripRpInternalTags(res.content).trim()
                if (draft.isNotEmpty()) {
                    _ui.update { it.copy(draftText = draft) }
                    recomputeWaterline()
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isImpersonating = false) }
            }
        }
    }

    fun refreshRpChoices() {
        if (_ui.value.isStreaming || _ui.value.isRefreshingChoices) return
        val conv = _ui.value.activeConversation ?: return
        val rp = conv.rp ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId
        val model = conv.model ?: _ui.value.selectedModelId
        if (gid == null || model.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshingChoices = true) }
            try {
                val prompt = RpEngine.buildRefreshChoicesMessages(rp, _ui.value.messages)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(
                        group_id = gid,
                        model = model,
                        messages = prompt,
                        stream = false,
                        temperature = 0.85,
                        max_tokens = 700,
                    ),
                )
                val newChoices = RpEngine.parseChoicesOnly(res.content)
                if (newChoices.isEmpty()) {
                    container.toastController.show(strings().common.unknownError, isError = true)
                    return@launch
                }
                val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
                val lastAssistant = TimelineReconciler.activeMessages(msgs).lastOrNull { it.role == "assistant" }
                if (lastAssistant != null) {
                    val updatedVariants = if (lastAssistant.variants.isNotEmpty()) {
                        val activeIdx = lastAssistant.activeVariantIndex.coerceIn(0, lastAssistant.variants.lastIndex)
                        lastAssistant.variants.mapIndexed { idx, v ->
                            if (idx == activeIdx) v.copy(rpChoices = newChoices) else v
                        }
                    } else {
                        lastAssistant.variants
                    }
                    chatDao.upsertMessage(
                        lastAssistant.copy(rpChoices = newChoices, variants = updatedVariants).toEntity(),
                    )
                }
                val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                val freshRp = freshConv.rp ?: rp
                chatDao.upsertConversation(
                    freshConv.copy(
                        rp = freshRp.copy(pendingChoices = newChoices),
                        updatedAt = System.currentTimeMillis(),
                    ).toEntity(),
                )
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isRefreshingChoices = false) }
            }
        }
    }

    fun rollDiceForDraft(expression: String, skill: String?, difficulty: Int?, mode: String) {
        val rp = _ui.value.activeConversation?.rp
        val rollUnder = expression.contains("d100", ignoreCase = true) ||
            (rp?.systemRules?.contains("d100", ignoreCase = true) == true)
        val roll = RpEngine.rollDice(
            expression = expression,
            difficulty = difficulty,
            skill = skill?.takeIf { it.isNotBlank() },
            mode = mode,
            rollUnder = rollUnder,
        )
        _ui.update { it.copy(draftDiceRoll = roll) }
    }

    fun clearDraftDiceRoll() {
        _ui.update { it.copy(draftDiceRoll = null) }
    }

    fun triggerRpChoice(choice: RpChoice, bypassLock: Boolean = false) {
        if (_ui.value.isStreaming) return
        val groupId = _ui.value.selectedGroupId
        val modelId = _ui.value.selectedModelId
        if (groupId == null || modelId.isNullOrBlank()) {
            container.toastController.show(strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        val conv = _ui.value.activeConversation
        val rp = conv?.rp
        if (rp != null) {
            val eval = RpEngine.evaluateChoiceCondition(choice, rp)
            if (!eval.unlocked && !bypassLock) {
                val isEn = container.settingsState.value.language == AppLanguage.EN
                val reason = (if (isEn) eval.reasonEn else eval.reasonZh).orEmpty()
                val msg = if (reason.isNotBlank()) "${strings().rp.choiceLockedLabel}：$reason" else strings().rp.choiceLockedLabel
                container.toastController.show(msg, isError = true)
                return
            }
        }

        val roll = if (!choice.skillCheck.isNullOrBlank() && rp != null) {
            RpEngine.rollForChoice(choice, rp)
        } else {
            null
        }

        val cost = choice.costGold ?: 0
        if (conv != null && rp != null && cost > 0 && rp.state.gold >= cost) {
            viewModelScope.launch {
                val updatedRp = rp.copy(
                    state = rp.state.copy(gold = (rp.state.gold - cost).coerceAtLeast(0)),
                )
                val updatedConv = conv.copy(rp = updatedRp, updatedAt = System.currentTimeMillis())
                chatDao.upsertConversation(updatedConv.toEntity())
                _ui.update {
                    it.copy(
                        activeConversation = updatedConv,
                        draftRpKind = choice.inputKind,
                        draftDiceRoll = roll,
                    )
                }
                sendMessage(overrideText = choice.actionText)
            }
        } else {
            _ui.update { it.copy(draftRpKind = choice.inputKind, draftDiceRoll = roll) }
            sendMessage(overrideText = choice.actionText)
        }
    }

    fun updateRpConfig(transform: (RpConfig) -> RpConfig) {
        viewModelScope.launch {
            val activeId = _ui.value.activeConversation?.id ?: return@launch
            val conv = chatDao.getConversationById(activeId)?.toDomain() ?: _ui.value.activeConversation ?: return@launch
            val rp = conv.rp ?: return@launch
            val updated = transform(rp)
            if (updated == rp) return@launch
            chatDao.upsertConversation(conv.copy(rp = updated, updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun updateRpState(transform: (RpGameState) -> RpGameState) {
        updateRpConfig { cfg ->
            cfg.copy(state = transform(cfg.state))
        }
    }

    // ==================== RP 3.0 Visual Theme, Regex & Frontend Bridge ====================

    fun updateRpVisualTheme(transform: (RpVisualTheme) -> RpVisualTheme) {
        updateRpConfig { cfg ->
            cfg.copy(visualTheme = transform(cfg.visualTheme))
        }
    }

    fun resetRpVisualTheme() {
        updateRpConfig { cfg ->
            cfg.copy(visualTheme = RpVisualTheme())
        }
    }

    fun upsertRpRegexScript(script: RpRegexScript) {
        val cleanName = script.name.trim()
        if (cleanName.isEmpty() || script.findRegex.isBlank()) return
        val normalized = script.copy(
            id = script.id.ifBlank { UUID.randomUUID().toString() },
            name = cleanName,
            flags = script.flags.trim().ifEmpty { "gms" },
            placement = script.placement.trim().lowercase().ifEmpty { "ai_output" },
        )
        updateRpConfig { cfg ->
            val exists = cfg.regexScripts.any { it.id == normalized.id }
            val nextScripts = if (exists) {
                cfg.regexScripts.map { if (it.id == normalized.id) normalized else it }
            } else {
                cfg.regexScripts + normalized
            }
            cfg.copy(regexScripts = nextScripts)
        }
    }

    fun toggleRpRegexScript(scriptId: String) {
        updateRpConfig { cfg ->
            cfg.copy(
                regexScripts = cfg.regexScripts.map { s ->
                    if (s.id == scriptId) s.copy(enabled = !s.enabled) else s
                },
            )
        }
    }

    fun deleteRpRegexScript(scriptId: String) {
        updateRpConfig { cfg ->
            cfg.copy(regexScripts = cfg.regexScripts.filterNot { it.id == scriptId })
        }
    }

    fun applyRpRegexPreset(presetId: String) {
        val preset = RpRegexEngine.findRegexPreset(presetId) ?: return
        updateRpConfig { cfg ->
            val dup = cfg.regexScripts.any {
                it.id == preset.script.id ||
                    it.name.equals(preset.script.name, ignoreCase = true) ||
                    it.name.equals(preset.enName, ignoreCase = true)
            }
            if (dup) {
                cfg.copy(
                    regexScripts = cfg.regexScripts.map { s ->
                        if (s.id == preset.script.id ||
                            s.name.equals(preset.script.name, ignoreCase = true) ||
                            s.name.equals(preset.enName, ignoreCase = true)
                        ) {
                            s.copy(enabled = true)
                        } else {
                            s
                        }
                    },
                )
            } else {
                cfg.copy(regexScripts = cfg.regexScripts + preset.script)
            }
        }
    }

    fun applyRpStatusWidgetTemplate(templateId: String) {
        val tpl = RpRegexEngine.findStatusWidgetTemplateOrNull(templateId) ?: return
        updateRpVisualTheme { theme ->
            theme.copy(
                customStatusHtml = tpl.html,
                showCustomStatusWidget = true,
            )
        }
    }

    fun handleRpFrontendAction(actionText: String) {
        val clean = actionText.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            sendMessage(overrideText = clean)
        }
    }

    fun handleRpFrontendFillInput(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        updateDraftText(clean)
    }

    fun handleRpFrontendSetFlag(key: String, value: String) {
        val k = key.trim()
        if (k.isEmpty()) return
        val v = value.trim()
        updateRpState { st ->
            val nextFlags = if (v.isEmpty()) st.flags - k else st.flags + (k to v)
            st.copy(flags = nextFlags)
        }
    }

    fun handleRpFrontendAdjustAffinity(npcName: String, delta: Int) {
        val target = npcName.trim()
        if (target.isEmpty() || delta == 0) return
        updateRpConfig { cfg ->
            val matched = cfg.cast.firstOrNull { c ->
                c.name.trim().equals(target, ignoreCase = true) ||
                    c.aliases.any { it.trim().equals(target, ignoreCase = true) }
            } ?: cfg.cast.firstOrNull { c ->
                c.name.contains(target, ignoreCase = true) || target.contains(c.name, ignoreCase = true)
            }
            if (matched == null) {
                cfg
            } else {
                cfg.copy(
                    cast = cfg.cast.map { c ->
                        if (c.id == matched.id) {
                            c.copy(affinity = (c.affinity + delta).coerceIn(-100, 100))
                        } else {
                            c
                        }
                    },
                )
            }
        }
    }

    fun updateRpCharacterAffinity(charId: String, delta: Int) {
        updateRpConfig { cfg ->
            cfg.copy(
                cast = cfg.cast.map { c ->
                    if (c.id == charId) c.copy(affinity = (c.affinity + delta).coerceIn(-100, 100)) else c
                },
            )
        }
    }

    fun upsertRpNpc(character: RpCharacter) {
        val cleanName = character.name.trim()
        if (cleanName.isEmpty()) return
        val normalized = character.copy(
            id = character.id.ifBlank { UUID.randomUUID().toString() },
            name = cleanName,
        )
        updateRpConfig { cfg ->
            val exists = cfg.cast.any { it.id == normalized.id }
            val nextCast = if (exists) {
                cfg.cast.map { if (it.id == normalized.id) normalized else it }
            } else {
                cfg.cast + normalized
            }
            cfg.copy(cast = nextCast)
        }
    }

    fun deleteRpNpc(charId: String) {
        updateRpConfig { cfg ->
            cfg.copy(cast = cfg.cast.filterNot { it.id == charId })
        }
    }

    fun createRpSaveSlot(name: String, note: String = "") {
        val conv = _ui.value.activeConversation ?: return
        val rp = conv.rp ?: return
        viewModelScope.launch {
            val slot = RpEngine.createSaveSlot(
                conversationId = conv.id,
                name = name,
                note = note.trim(),
                rp = rp,
                messages = _ui.value.messages,
                ledger = _ui.value.memoryLedger,
                episodes = _ui.value.memoryEpisodes,
            )
            chatDao.upsertRpSaveSlot(slot.toEntity())
            container.toastController.show(strings().rp.quickSaved.fmt("name" to slot.name))
        }
    }

    fun overwriteRpSaveSlot(slotId: String) {
        val conv = _ui.value.activeConversation ?: return
        val rp = conv.rp ?: return
        val existing = _ui.value.rpSaveSlots.firstOrNull { it.id == slotId } ?: return
        viewModelScope.launch {
            val fresh = RpEngine.createSaveSlot(
                conversationId = conv.id,
                name = existing.name,
                note = existing.note,
                rp = rp,
                messages = _ui.value.messages,
                ledger = _ui.value.memoryLedger,
                episodes = _ui.value.memoryEpisodes,
            ).copy(id = existing.id)
            chatDao.upsertRpSaveSlot(fresh.toEntity())
            container.toastController.show(strings().rp.saveOverwrittenToast.fmt("name" to existing.name))
        }
    }

    fun deleteRpSaveSlot(slotId: String) {
        viewModelScope.launch {
            chatDao.deleteRpSaveSlot(slotId)
        }
    }

    fun loadRpSaveSlotRewind(slot: RpSaveSlot) {
        val conv = _ui.value.activeConversation ?: return
        viewModelScope.launch {
            chatDao.deleteMessagesForConversation(conv.id)
            chatDao.deleteMemoryLedgerForConversation(conv.id)
            chatDao.deleteMemoryEpisodesForConversation(conv.id)
            chatDao.upsertMessages(slot.activeMessagesSnapshot.map { it.copy(conversationId = conv.id).toEntity() })
            if (slot.ledgerItems.isNotEmpty()) {
                chatDao.upsertMemoryLedgerList(slot.ledgerItems.map { it.copy(conversationId = conv.id).toEntity() })
            }
            if (slot.episodes.isNotEmpty()) {
                chatDao.upsertMemoryEpisodes(slot.episodes.map { it.copy(conversationId = conv.id).toEntity() })
            }
            chatDao.upsertConversation(conv.copy(rp = slot.rpConfig, updatedAt = System.currentTimeMillis()).toEntity())
            container.toastController.show(strings().rp.loadedRewindToast.fmt("name" to slot.name))
        }
    }

    fun branchFromRpSaveSlot(slot: RpSaveSlot) {
        val conv = _ui.value.activeConversation ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newConvId = UUID.randomUUID().toString()
            val forkedConv = conv.copy(
                id = newConvId,
                title = "${conv.title} · ${slot.name}",
                rp = slot.rpConfig,
                createdAt = now,
                updatedAt = now,
                pinned = false,
            )
            chatDao.upsertConversation(forkedConv.toEntity())
            val clonedMsgs = slot.activeMessagesSnapshot.map {
                it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId)
            }
            if (clonedMsgs.isNotEmpty()) {
                chatDao.upsertMessages(clonedMsgs.map { it.toEntity() })
            }
            val clonedLedger = slot.ledgerItems.map {
                it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId)
            }
            if (clonedLedger.isNotEmpty()) {
                chatDao.upsertMemoryLedgerList(clonedLedger.map { it.toEntity() })
            }
            val clonedEpisodes = slot.episodes.map {
                it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId)
            }
            if (clonedEpisodes.isNotEmpty()) {
                chatDao.upsertMemoryEpisodes(clonedEpisodes.map { it.toEntity() })
            }
            val currentLore = chatDao.getNovelLoreForConversation(conv.id).map { it.toDomain() }
            val clonedLore = currentLore.map {
                it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId)
            }
            if (clonedLore.isNotEmpty()) {
                chatDao.upsertNovelLoreList(clonedLore.map { it.toEntity() })
            }
            val mountedRefs = assetDao.getMountedWorldbookRefs(conv.id)
            if (mountedRefs.isNotEmpty()) {
                assetDao.replaceMountedWorldbooks(newConvId, mountedRefs.map { it.copy(conversationId = newConvId) })
            }
            _ui.update { it.copy(draftDiceRoll = null) }
            _activeConvId.value = newConvId
            container.toastController.show(strings().rp.saveBranchedToast.fmt("name" to slot.name))
        }
    }

    fun exportRpCardJsonToUri(uri: Uri, contentResolver: android.content.ContentResolver) {
        val rp = _ui.value.activeConversation?.rp ?: return
        viewModelScope.launch {
            try {
                val json = RpCardCodec.exportToV3Json(rp, _ui.value.novelLore)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("Failed to open output stream")
                }
                container.toastController.show(strings().rp.exportDone)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    fun exportRpCardPngToUri(uri: Uri, contentResolver: android.content.ContentResolver) {
        val conv = _ui.value.activeConversation ?: return
        val rp = conv.rp ?: return
        val lore = _ui.value.novelLore
        val boundCard = _ui.value.boundCharacterCard
        viewModelScope.launch {
            try {
                container.assetImportCoordinator.exportCharacterCardToPngUri(
                    rp = rp,
                    lore = lore,
                    cardEntity = boundCard,
                    uri = uri,
                    contentResolver = contentResolver,
                )
                container.toastController.show(strings().rp.exportDone)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    // ==================== v0.4 Assets, Greeting Switch, Novel Cover & EPUB ====================

    suspend fun importTavernAssetFromBytes(bytes: ByteArray, fileName: String?): Boolean {
        val isZh = strings().language == AppLanguage.ZH
        return when (val imported = container.assetImportCoordinator.importBytes(bytes = bytes, fileName = fileName)) {
            is AssetImportResult.CharacterCardImported -> {
                insertRpConversation(
                    rp = imported.rpConfig,
                    importedLore = emptyList(),
                    characterCardId = imported.card.id,
                    mountedWorldbookId = imported.worldbook?.id,
                )
                container.toastController.show(strings().rp.importSuccess.fmt("name" to imported.card.name))
                true
            }
            is AssetImportResult.WorldbookImported -> {
                container.toastController.show(
                    if (isZh) "已导入世界书「${imported.worldbook.name}」（${imported.entries.size} 条）到「创作 → 设定」"
                    else "Imported worldbook \"${imported.worldbook.name}\" (${imported.entries.size} entries)",
                )
                true
            }
            is AssetImportResult.PromptPresetImported -> {
                container.toastController.show(
                    if (isZh) "已导入预设「${imported.preset.name}」到「创作 → 设定」"
                    else "Imported preset \"${imported.preset.name}\" to library",
                )
                true
            }
            else -> false
        }
    }

    fun resolveLocalMediaFile(storedPath: String?): java.io.File? =
        LocalMediaPathResolver.resolveFile(container.appContext.filesDir.absolutePath, storedPath)?.takeIf { it.isFile }

    fun createRpSessionFromCharacterCard(cardId: String) {
        viewModelScope.launch {
            val card = assetDao.getCharacterCardById(cardId) ?: return@launch
            val playStyle = runCatching { RpPlayStyle.valueOf(card.playStyle) }.getOrDefault(RpPlayStyle.chengguang)
            val cast = card.decodedCast()
            val altGreetings = card.decodedAltGreetings()
            val regexScripts = card.decodedRegexScripts()
            val visualTheme = card.decodedVisualTheme()
            val leadChar = cast.firstOrNull { !it.isPlayer } ?: RpCharacter(
                id = UUID.randomUUID().toString(),
                name = card.name,
                role = "protagonist",
                appearance = card.description.take(600),
                personality = card.personality,
                background = card.description,
                avatarPath = card.avatarPath,
                spritePath = card.spritePath,
                inScene = true,
            )
            val effectiveCast = if (cast.isNotEmpty()) cast else listOf(leadChar)
            val rp = RpConfig(
                title = card.name.ifBlank { "角色剧场" },
                worldview = card.scenario,
                systemRules = card.systemPrompt,
                narratorStyle = card.creatorNotes,
                authorNote = card.depthPromptText,
                authorNoteDepth = card.depthPromptDepth,
                cast = effectiveCast,
                state = RpGameState(scene = card.scenario.take(40).ifBlank { "初次相遇" }),
                greeting = card.firstMes,
                cardSource = "sillytavern_${card.sourceSpec}",
                playStyle = playStyle,
                theaterMode = playStyle == RpPlayStyle.chengguang,
                alternateGreetings = altGreetings,
                mesExample = card.mesExample,
                postHistoryInstructions = card.postHistoryInstructions,
                regexScripts = regexScripts,
                visualTheme = visualTheme,
            )
            insertRpConversation(
                rp = rp,
                importedLore = emptyList(),
                characterCardId = card.id,
                mountedWorldbookId = card.primaryWorldbookId,
            )
        }
    }

    fun selectGreetingVariant(index: Int) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            val card = conv.characterCardId?.let { assetDao.getCharacterCardById(it) }
            val persona = conv.personaId?.let { assetDao.getPersonaById(it) }
            refreshOpeningGreetingMessage(
                conv = conv,
                card = card,
                persona = persona,
                selectedVariantIndex = index,
            )
        }
    }

    fun selectConversationPersona(personaId: String?) {
        viewModelScope.launch {
            val activeId = _activeConvId.value ?: _ui.value.activeConversation?.id ?: return@launch
            val fresh = chatDao.getConversationById(activeId)?.toDomain() ?: return@launch
            val normalizedId = personaId?.trim()?.takeIf { it.isNotEmpty() }
            val updated = fresh.copy(personaId = normalizedId, updatedAt = System.currentTimeMillis())
            chatDao.upsertConversation(updated.toEntity())
            val card = updated.characterCardId?.let { assetDao.getCharacterCardById(it) }
            val persona = normalizedId?.let { assetDao.getPersonaById(it) }
            refreshOpeningGreetingMessage(
                conv = updated,
                card = card,
                persona = persona,
                selectedVariantIndex = null,
            )
            recomputeWaterline()
        }
    }

    fun selectConversationPreset(presetId: String?) {
        viewModelScope.launch {
            val activeId = _activeConvId.value ?: _ui.value.activeConversation?.id ?: return@launch
            val fresh = chatDao.getConversationById(activeId)?.toDomain() ?: return@launch
            val normalizedId = presetId?.trim()?.takeIf { it.isNotEmpty() }
            chatDao.upsertConversation(
                fresh.copy(presetId = normalizedId, updatedAt = System.currentTimeMillis()).toEntity(),
            )
            recomputeWaterline()
        }
    }

    fun togglePromptPresetBlockEnabled(presetId: String, identifier: String, index: Int) {
        viewModelScope.launch {
            val preset = assetDao.getPromptPresetById(presetId) ?: return@launch
            val blocks = runCatching {
                AppJson.decodeFromString(ListSerializer(PromptPresetBlock.serializer()), preset.blocksJson)
            }.getOrDefault(emptyList())
            if (blocks.isEmpty()) return@launch
            val updated = blocks.mapIndexed { idx, block ->
                if (idx == index && (identifier.isBlank() || block.identifier == identifier)) {
                    block.copy(enabled = !block.enabled)
                } else {
                    block
                }
            }
            val encoded = AppJson.encodeToString(ListSerializer(PromptPresetBlock.serializer()), updated)
            assetDao.upsertPromptPreset(
                preset.copy(blocksJson = encoded, updatedAt = System.currentTimeMillis()),
            )
            recomputeWaterline()
        }
    }

    fun mountWorldbook(worldbookId: String, convId: String? = null) {
        val targetId = convId ?: _activeConvId.value ?: _ui.value.activeConversation?.id ?: return
        viewModelScope.launch {
            val existing = assetDao.getMountedWorldbookRefs(targetId)
            if (existing.any { it.worldbookId == worldbookId }) {
                val updated = existing.map {
                    if (it.worldbookId == worldbookId) it.copy(enabled = true, updatedAt = System.currentTimeMillis()) else it
                }
                assetDao.replaceMountedWorldbooks(targetId, updated)
            } else {
                val nextSort = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
                val added = ConversationWorldbookCrossRef(
                    conversationId = targetId,
                    worldbookId = worldbookId,
                    sortOrder = nextSort,
                    enabled = true,
                    updatedAt = System.currentTimeMillis(),
                )
                assetDao.replaceMountedWorldbooks(targetId, existing + added)
            }
            recomputeWaterline()
        }
    }

    fun unmountWorldbook(worldbookId: String) {
        val targetId = _activeConvId.value ?: _ui.value.activeConversation?.id ?: return
        viewModelScope.launch {
            val remaining = assetDao.getMountedWorldbookRefs(targetId)
                .filterNot { it.worldbookId == worldbookId }
                .mapIndexed { idx, ref -> ref.copy(sortOrder = idx) }
            assetDao.replaceMountedWorldbooks(targetId, remaining)
            recomputeWaterline()
        }
    }

    fun toggleMountedWorldbookEnabled(worldbookId: String) {
        val targetId = _activeConvId.value ?: _ui.value.activeConversation?.id ?: return
        viewModelScope.launch {
            val updated = assetDao.getMountedWorldbookRefs(targetId).map { ref ->
                if (ref.worldbookId == worldbookId) {
                    ref.copy(enabled = !ref.enabled, updatedAt = System.currentTimeMillis())
                } else {
                    ref
                }
            }
            assetDao.replaceMountedWorldbooks(targetId, updated)
            recomputeWaterline()
        }
    }

    fun saveV2LoreEntryForActiveConversation(
        existingLegacyId: String?,
        category: String,
        title: String,
        keywordsRaw: String,
        secondaryKeysRaw: String,
        selectiveLogic: Int,
        useDepthInjection: Boolean,
        depth: Int,
        sticky: Int,
        cooldown: Int,
        delay: Int,
        content: String,
        alwaysActive: Boolean,
    ) {
        val conv = _ui.value.activeConversation ?: return
        val hasV2Triggers = useDepthInjection ||
            secondaryKeysRaw.trim().isNotEmpty() ||
            sticky > 0 ||
            cooldown > 0 ||
            delay > 0
        if (!hasV2Triggers) {
            if (existingLegacyId != null) {
                val existing = _ui.value.novelLore.firstOrNull { it.id == existingLegacyId }
                if (existing != null) {
                    val parsedKeys = keywordsRaw.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
                    updateNovelLore(
                        existing.copy(
                            category = category,
                            title = title.trim(),
                            keywords = parsedKeys.ifEmpty { listOf(title.trim()) },
                            content = content.trim(),
                            alwaysActive = alwaysActive,
                        ),
                    )
                    return
                }
            }
            upsertNovelLore(category, title, keywordsRaw, content, alwaysActive)
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val wbId = "conv_v2_wb_${conv.id}"
            val primaryKeys = keywordsRaw.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { if (alwaysActive) emptyList() else listOf(title.trim()) }
            val secondaryKeys = secondaryKeysRaw.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
            val entryId = existingLegacyId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

            val existingWb = assetDao.getWorldbookById(wbId)
            val wb = existingWb ?: WorldbookEntity(
                id = wbId,
                name = "${conv.title.take(18)} · 高级词条",
                description = "Session V2 worldbook triggers",
                sourceFormat = "gugachat_v2",
                globalEnabled = false,
                createdAt = now,
                updatedAt = now,
            )
            if (existingWb == null) {
                assetDao.upsertWorldbook(wb)
            }
            val entry = WorldbookEntryEntity(
                id = entryId,
                worldbookId = wbId,
                uid = entryId.hashCode(),
                title = title.trim(),
                content = content.trim(),
                category = category,
                keysJson = AppJson.encodeToString(ListSerializer(String.serializer()), primaryKeys),
                secondaryKeysJson = AppJson.encodeToString(ListSerializer(String.serializer()), secondaryKeys),
                selectiveLogic = selectiveLogic.coerceIn(0, 3),
                constant = alwaysActive,
                enabled = true,
                position = if (useDepthInjection) WorldbookEngine.POSITION_AT_DEPTH else WorldbookEngine.POSITION_BEFORE_CHAR,
                depth = depth.coerceIn(0, 16),
                sticky = sticky.coerceAtLeast(0),
                cooldown = cooldown.coerceAtLeast(0),
                delay = delay.coerceAtLeast(0),
                estimatedTokens = TokenEstimator.estimateItem(title.trim(), content.trim(), overhead = 8, minTokens = 8),
                createdAt = now,
                updatedAt = now,
            )
            assetDao.upsertWorldbookEntry(entry)
            val wbEntries = assetDao.getWorldbookEntries(wbId)
            assetDao.upsertWorldbook(
                wb.copy(
                    entryCount = wbEntries.size,
                    totalEstimatedTokens = wbEntries.sumOf { it.estimatedTokens },
                    updatedAt = now,
                ),
            )
            val refs = assetDao.getMountedWorldbookRefs(conv.id)
            if (refs.none { it.worldbookId == wbId }) {
                assetDao.replaceMountedWorldbooks(
                    conv.id,
                    refs + ConversationWorldbookCrossRef(
                        conversationId = conv.id,
                        worldbookId = wbId,
                        sortOrder = (refs.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                        enabled = true,
                        updatedAt = now,
                    ),
                )
            }
            if (existingLegacyId != null) {
                chatDao.deleteNovelLore(existingLegacyId)
            }
            container.retrievalCoordinator.scheduleWorldbookIndexing(viewModelScope, wbId, wbEntries)
            recomputeWaterline()
        }
    }

    fun syncBoundCharacterCardFields() {
        val isZh = strings().language == AppLanguage.ZH
        viewModelScope.launch {
            val activeId = _activeConvId.value ?: _ui.value.activeConversation?.id ?: return@launch
            val fresh = chatDao.getConversationById(activeId)?.toDomain() ?: return@launch
            val rp = fresh.rp ?: return@launch
            val card = fresh.characterCardId?.let { assetDao.getCharacterCardById(it) } ?: return@launch
            val cardCast = card.decodedCast()
            val cardExpr = card.decodedExpressionSprites()
            val cardRegex = card.decodedRegexScripts()
            val altGreetings = card.decodedAltGreetings()

            val leadCardChar = cardCast.firstOrNull { !it.isPlayer }
            val updatedCast = rp.cast.mapIndexed { idx, ch ->
                val src = cardCast.firstOrNull { it.id == ch.id }
                    ?: cardCast.firstOrNull { it.name.equals(ch.name, ignoreCase = true) }
                    ?: if (!ch.isPlayer && idx == 0) leadCardChar else null
                if (src != null) {
                    ch.copy(
                        appearance = src.appearance.ifBlank { ch.appearance },
                        personality = src.personality.ifBlank { ch.personality },
                        background = src.background.ifBlank { ch.background },
                        avatarPath = ch.avatarPath?.takeIf { it.isNotBlank() } ?: src.avatarPath ?: card.avatarPath,
                        spritePath = ch.spritePath?.takeIf { it.isNotBlank() } ?: src.spritePath ?: card.spritePath,
                        expressionSprites = ch.expressionSprites.ifEmpty {
                            src.expressionSprites.ifEmpty { cardExpr }
                        },
                    )
                } else {
                    ch
                }
            }
            val syncedRp = rp.copy(
                worldview = card.scenario.ifBlank { rp.worldview },
                systemRules = card.systemPrompt.ifBlank { rp.systemRules },
                narratorStyle = card.creatorNotes.ifBlank { rp.narratorStyle },
                authorNote = card.depthPromptText.ifBlank { rp.authorNote },
                authorNoteDepth = card.depthPromptDepth,
                postHistoryInstructions = card.postHistoryInstructions.ifBlank { rp.postHistoryInstructions },
                mesExample = card.mesExample.ifBlank { rp.mesExample },
                greeting = card.firstMes.ifBlank { rp.greeting },
                alternateGreetings = altGreetings.ifEmpty { rp.alternateGreetings },
                regexScripts = cardRegex.ifEmpty { rp.regexScripts },
                cast = updatedCast,
            )
            chatDao.upsertConversation(
                fresh.copy(rp = syncedRp, updatedAt = System.currentTimeMillis()).toEntity(),
            )
            val persona = fresh.personaId?.let { assetDao.getPersonaById(it) }
            refreshOpeningGreetingMessage(
                conv = fresh.copy(rp = syncedRp),
                card = card,
                persona = persona,
                selectedVariantIndex = null,
            )
            container.toastController.show(
                if (isZh) "已同步角色卡「${card.name}」最新设定" else "Synced latest fields from \"${card.name}\"",
            )
        }
    }

    fun bindNovelCoverImage(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val relPath = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Empty image")
                    val useCase = container.studioImageGenerationUseCase
                    val ext = if (RpCardCodec.isPngBytes(bytes)) "png" else useCase.inferImageExt(contentResolver.getType(uri))
                    useCase.saveMediaBytesSync(bytes, "novel_cover", ext)
                }
                updateNovelConfig { it.copy(coverImagePath = relPath) }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun clearNovelCoverImage() {
        updateNovelConfig { it.copy(coverImagePath = null) }
    }

    fun generateNovelCoverArt() {
        if (_ui.value.isGeneratingRpArt) return
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val isZh = strings().language == AppLanguage.ZH
        val statusLabel = if (isZh) "正在绘制书封 (${novel.title})" else "Generating cover (${novel.title})"
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingRpArt = true, rpArtStatusLabel = statusLabel) }
            try {
                val prompt = buildString {
                    append("Vertical book cover illustration, high quality literary artwork, no text, no watermark. ")
                    append("Title mood: ").append(novel.title).append(". ")
                    if (novel.genre.isNotBlank()) append("Genre: ").append(novel.genre).append(". ")
                    if (novel.synopsis.isNotBlank()) append("Premise: ").append(novel.synopsis.take(260)).append(". ")
                    if (novel.worldview.isNotBlank()) append("Worldview: ").append(novel.worldview.take(200))
                }
                val generated = container.studioImageGenerationUseCase.generateAndSaveRpMedia(
                    prompt = prompt,
                    aspectRatio = "3:4",
                    prefix = "novel_cover",
                    noChannelMessage = strings().rp.aiGenArtNoChannel,
                )
                updateNovelConfig { it.copy(coverImagePath = generated.relativePath) }
                container.toastController.show(if (isZh) "书封已生成并保存" else "Book cover generated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: strings().common.unknownError, isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingRpArt = false, rpArtStatusLabel = "") }
            }
        }
    }

    fun exportNovelEpubToUri(
        uri: Uri,
        contentResolver: ContentResolver,
        includeLore: Boolean = false,
    ) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        viewModelScope.launch {
            try {
                val coverFile = resolveLocalMediaFile(novel.coverImagePath)
                val epubBytes = withContext(Dispatchers.IO) {
                    val coverBytes = coverFile?.takeIf { it.isFile }?.readBytes()
                    val mime = when (coverFile?.extension?.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "webp" -> "image/webp"
                        else -> "image/png"
                    }
                    NovelExport.exportEpubBytes(
                        conv = conv,
                        chapters = _ui.value.novelChapters,
                        messages = _ui.value.messages,
                        lore = if (includeLore) _ui.value.novelLore else emptyList(),
                        coverImageBytes = coverBytes,
                        coverMimeType = mime,
                    )
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(epubBytes) }
                        ?: error("Failed to open output stream")
                }
                container.toastController.show(strings().novel.exportDone)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    private suspend fun refreshOpeningGreetingMessage(
        conv: ChatConversation,
        card: CharacterCardEntity?,
        persona: PersonaEntity?,
        selectedVariantIndex: Int? = null,
    ) {
        val allMsgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
        val active = TimelineReconciler.activeMessages(allMsgs)
        if (active.any { it.role == "user" }) return
        val greetingMsg = active.firstOrNull { it.role == "assistant" && it.seq == 1 } ?: return

        val rawGreetings = if (card != null) {
            val alts = card.decodedAltGreetings()
            (listOf(card.firstMes) + alts).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            val rp = conv.rp
            if (rp != null) {
                (listOf(rp.greeting) + rp.alternateGreetings).map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        }
        if (rawGreetings.isEmpty()) return

        val charName = card?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: conv.rp?.cast?.firstOrNull { !it.isPlayer }?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: conv.title.trim().ifBlank { "角色" }
        val userName = persona?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: conv.rp?.playerName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "旅人"

        val baseRp = conv.rp
        val leadSpeaker = baseRp?.cast?.firstOrNull { !it.isPlayer }?.name?.takeIf { it.isNotBlank() } ?: charName
        val now = System.currentTimeMillis()
        val variants = rawGreetings.mapIndexed { idx, raw ->
            val withRpMacros = if (baseRp != null) {
                val effectiveCast = if (baseRp.cast.any { !it.isPlayer }) {
                    baseRp.cast
                } else {
                    listOf(RpCharacter(id = "lead_macro", name = charName))
                }
                RpEngine.replaceMacros(raw, baseRp.copy(playerName = userName, cast = effectiveCast))
            } else {
                raw
            }
            val rendered = ContextBuilder.expandStaticMacros(withRpMacros, charName, userName)
            val prev = greetingMsg.variants.getOrNull(idx)
            MessageVariant(
                id = prev?.id ?: UUID.randomUUID().toString(),
                content = rendered,
                createdAt = prev?.createdAt ?: now,
                rpStateSnapshot = prev?.rpStateSnapshot ?: baseRp?.state,
                rpCastSnapshot = prev?.rpCastSnapshot ?: baseRp?.cast,
                rpChoices = prev?.rpChoices ?: baseRp?.pendingChoices.orEmpty(),
                rpSpeakerName = prev?.rpSpeakerName ?: leadSpeaker,
            )
        }
        val activeIdx = (selectedVariantIndex ?: greetingMsg.activeVariantIndex).coerceIn(0, variants.lastIndex)
        val selectedContent = variants[activeIdx].content
        chatDao.upsertMessage(
            greetingMsg.copy(
                content = selectedContent,
                variants = variants,
                activeVariantIndex = activeIdx,
                tokenEstimate = ContextBuilder.estimateMessageTokens(selectedContent),
            ).toEntity(),
        )
    }

    private fun enrichConversationWithBoundCardMedia(
        conv: ChatConversation,
        card: CharacterCardEntity?,
    ): ChatConversation {
        val rp = conv.rp ?: return conv
        if (card == null) return conv
        val cardCast = card.decodedCast()
        val cardExpr = card.decodedExpressionSprites()
        val leadCardChar = cardCast.firstOrNull { !it.isPlayer }
        var changed = false
        val enrichedCast = rp.cast.mapIndexed { idx, ch ->
            if (ch.isPlayer) return@mapIndexed ch
            val fallbackSrc = cardCast.firstOrNull { it.id == ch.id }
                ?: cardCast.firstOrNull { it.name.equals(ch.name, ignoreCase = true) }
                ?: if (idx == 0 || ch.id == rp.cast.firstOrNull { !it.isPlayer }?.id) leadCardChar else null
            val fallbackAvatar = fallbackSrc?.avatarPath?.takeIf { it.isNotBlank() } ?: card.avatarPath?.takeIf { it.isNotBlank() }
            val fallbackSprite = fallbackSrc?.spritePath?.takeIf { it.isNotBlank() } ?: card.spritePath?.takeIf { it.isNotBlank() }
            val fallbackExpr = (fallbackSrc?.expressionSprites ?: emptyMap()).ifEmpty { cardExpr }
            val nextAvatar = ch.avatarPath?.takeIf { it.isNotBlank() } ?: fallbackAvatar
            val nextSprite = ch.spritePath?.takeIf { it.isNotBlank() } ?: fallbackSprite
            val nextExpr = if (ch.expressionSprites.isNotEmpty()) ch.expressionSprites else fallbackExpr
            if (nextAvatar != ch.avatarPath || nextSprite != ch.spritePath || nextExpr != ch.expressionSprites) {
                changed = true
                ch.copy(avatarPath = nextAvatar, spritePath = nextSprite, expressionSprites = nextExpr)
            } else {
                ch
            }
        }
        return if (changed) conv.copy(rp = rp.copy(cast = enrichedCast)) else conv
    }

    // ==================== Helpers ====================

    private fun strings() =
        if (container.settingsState.value.language == AppLanguage.EN) EnStrings else ZhStrings

    private suspend fun ensureActiveConversation(): ChatConversation {
        _ui.value.activeConversation?.let { return it }
        val now = System.currentTimeMillis()
        val conv = ChatConversation(
            id = UUID.randomUUID().toString(),
            title = "新对话",
            mode = ConversationMode.chat,
            groupId = _ui.value.selectedGroupId,
            model = _ui.value.selectedModelId,
            params = defaultChatParams(),
            createdAt = now,
            updatedAt = now,
        )
        chatDao.upsertConversation(conv.toEntity())
        _activeConvId.value = conv.id
        return conv
    }

    private suspend fun persistReconciliation(res: com.gugachat.app.domain.chat.TimelineReconciliationResult) {
        chatDao.upsertMessages(res.messages.map { it.toEntity() })
        if (res.ledgerItems.isNotEmpty()) chatDao.upsertMemoryLedgerList(res.ledgerItems.map { it.toEntity() })
        if (res.loreEntries.isNotEmpty()) chatDao.upsertNovelLoreList(res.loreEntries.map { it.toEntity() })
        if (res.episodes.isNotEmpty()) chatDao.upsertMemoryEpisodes(res.episodes.map { it.toEntity() })
        if (res.chapters.isNotEmpty()) chatDao.upsertNovelChapters(res.chapters.map { it.toEntity() })
    }

    private fun recomputeWaterline() {
        scheduleWaterlineRecompute(debounceMs = 0L)
    }

    private fun scheduleWaterlineRecompute(debounceMs: Long = 0L) {
        waterlineJob?.cancel()
        waterlineJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val st = _ui.value
            val conv = st.activeConversation ?: return@launch
            val settings = container.settingsState.value
            val assembler = container.chatContextAssembler
            val budget = assembler.parseMemoryBudget(settings.memoryBudget)
            val effectiveModelId = conv.model?.takeIf { it.isNotBlank() } ?: st.selectedModelId
            val effectiveGroupId = conv.groupId ?: st.selectedGroupId
            val modelWin = st.models.firstOrNull { it.id == effectiveModelId }?.context_window
                ?: effectiveModelId?.let { DirectProtocolCodec.inferChatModelMeta(it).context_window }
                ?: 128000
            val extCal = assembler.lookupExternalCalibration(settings, effectiveGroupId, effectiveModelId)
            val v4Assets = assembler.loadV4Assets(conv)
            val input = ContextBuildInput(
                conversation = conv,
                messages = st.messages,
                memories = st.userMemories,
                memoryBudget = budget,
                modelContextWindow = modelWin,
                novelChapters = st.novelChapters,
                novelLore = st.novelLore,
                memoryLedger = st.memoryLedger,
                memoryEpisodes = st.memoryEpisodes,
                draftText = st.draftText,
                draftImageCount = st.draftAttachments.count { it.isImage },
                draftAttachments = st.draftAttachments,
                attachmentsById = st.attachmentsById + st.draftAttachments.associateBy { it.id },
                mutedRecallKeys = st.mutedRecallKeys,
                modelId = effectiveModelId,
                externalCalibration = extCal,
                minSemanticCosine = settings.retrievalSettings.minCosineOverride ?: RetrievalCoordinator.DEFAULT_MIN_COSINE_FLOOR,
                characterCard = v4Assets.characterCard,
                persona = v4Assets.persona,
                promptPreset = v4Assets.promptPreset,
                mountedWorldbooks = v4Assets.mountedWorldbooks,
                loreTimedStates = v4Assets.loreTimedStates,
            )
            val wl = withContext(Dispatchers.Default) {
                ContextBuilder.computeContextWaterline(input)
            }
            _ui.update { it.copy(waterline = wl) }
        }
    }

    private fun scheduleActiveConversationIndexing(convIdOverride: String? = null) {
        val convId = convIdOverride ?: _ui.value.activeConversation?.id ?: _activeConvId.value ?: return
        val settings = container.settingsState.value.retrievalSettings
        if (settings.mode == RetrievalMode.LEXICAL_ONLY) return
        viewModelScope.launch(Dispatchers.IO) {
            val lore = chatDao.getNovelLoreForConversation(convId).map { it.toDomain() }
            val ledger = chatDao.getMemoryLedgerForConversation(convId).map { it.toDomain() }
            val episodes = chatDao.getMemoryEpisodesForConversation(convId).map { it.toDomain() }
            val userMems = userMemoryDao.getAll().map { it.toDomain() }
            container.retrievalCoordinator.scheduleConversationIndexing(
                scope = viewModelScope,
                conversationId = convId,
                lore = lore,
                ledger = ledger,
                episodes = episodes,
                userMemories = userMems,
            )
        }
    }
}
