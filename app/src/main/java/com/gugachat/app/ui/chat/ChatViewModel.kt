package com.gugachat.app.ui.chat

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gugachat.app.AppContainer
import com.gugachat.app.AuthSessionState
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.AppStrings
import com.gugachat.app.core.i18n.EnStrings
import com.gugachat.app.core.i18n.ZhStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.OnlineChatGroupDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.data.api.WireChatMessageDto
import com.gugachat.app.data.chat.RetrievalCoordinator
import com.gugachat.app.data.chat.StreamLaunchRequest
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.ConversationWorldbookCrossRef
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.decodedAltGreetings
import com.gugachat.app.data.local.decodedCast
import com.gugachat.app.data.local.decodedExpressionSprites
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextBuildInput
import com.gugachat.app.domain.chat.ContextBuilder
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.FileInjectMode
import com.gugachat.app.domain.chat.FreeQuotaUtils
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.RpCgRecord
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpDiceRoll
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpRegexEngine
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.RpVisualTheme
import com.gugachat.app.domain.chat.TimelineReconciler
import com.gugachat.app.domain.chat.UserMemory
import com.gugachat.app.domain.chat.VectorIndexProgress
import com.gugachat.app.domain.chat.activeVariant
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
import java.util.UUID

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

    private val delegateHost: ChatViewModelHost = object : ChatViewModelHost {
        override val container: AppContainer get() = this@ChatViewModel.container
        override val scope get() = viewModelScope
        override val uiFlow: MutableStateFlow<ChatUiState> get() = _ui
        override val activeConvIdFlow: MutableStateFlow<String?> get() = _activeConvId
        override val oneShotRunner: ChatOneShotAiRunner get() = this@ChatViewModel.oneShotRunner

        override fun strings(): AppStrings = this@ChatViewModel.strings()
        override fun updateDraftText(text: String) = this@ChatViewModel.updateDraftText(text)
        override fun setDraftNovelKind(kind: NovelInputKind) = this@ChatViewModel.setDraftNovelKind(kind)
        override fun sendMessage(overrideText: String?) = this@ChatViewModel.sendMessage(overrideText)
        override fun launchCompletionForConversation(
            conv: ChatConversation,
            existingMessageForVariant: ChatMessage?,
            continuePrefixText: String?,
            overrideMessages: List<WireChatMessageDto>?,
        ) = this@ChatViewModel.launchCompletionForConversation(conv, existingMessageForVariant, continuePrefixText, overrideMessages)

        override fun scheduleWaterline(debounceMs: Long) = scheduleWaterlineRecompute(debounceMs)
        override fun scheduleIndexing(convIdOverride: String?) = scheduleActiveConversationIndexing(convIdOverride)
        override suspend fun syncNovelChapterWordCounts(convId: String) = novelStudioDelegate.syncNovelChapterWordCounts(convId)
        override fun upsertNovelLore(category: String, title: String, keywords: String, content: String, alwaysActive: Boolean) =
            novelStudioDelegate.upsertNovelLore(category, title, keywords, content, alwaysActive)

        override fun toggleNovelLoreAlwaysActive(id: String) = novelStudioDelegate.toggleNovelLoreAlwaysActive(id)
        override fun updateNovelLore(entry: NovelLoreEntry) = novelStudioDelegate.updateNovelLore(entry)
        override fun mountWorldbook(worldbookId: String, convId: String?) =
            assetsAndMemoryDelegate.mountWorldbook(worldbookId, convId)

        override fun updateRpConfig(transform: (RpConfig) -> RpConfig) = rpStudioDelegate.updateRpConfig(transform)
        override suspend fun refreshOpeningGreetingSuspend(
            conv: ChatConversation,
            card: CharacterCardEntity?,
            persona: PersonaEntity?,
            selectedVariantIndex: Int?,
        ) = assetsAndMemoryDelegate.refreshOpeningGreetingMessage(conv, card, persona, selectedVariantIndex)
    }

    private val oneShotRunner = ChatOneShotAiRunner(delegateHost)
    private val attachmentDelegate = ChatAttachmentDelegate(delegateHost)
    private val novelStudioDelegate = NovelStudioDelegate(delegateHost)
    private val assetsAndMemoryDelegate = ChatAssetsAndMemoryDelegate(delegateHost)
    private val rpStudioDelegate = RpStudioDelegate(delegateHost)

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
            chatStreamManager.turnCommittedEvents.collect { committedConvId ->
                if (committedConvId == (_activeConvId.value ?: _ui.value.activeConversation?.id)) {
                    _ui.update { it.copy(mutedRecallKeys = emptySet()) }
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
            container.settingsState.collect { s ->
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

    // ==================== Model / Channel Capabilities ====================

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

    // ==================== Draft & Attachment Actions ====================

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

    fun addImageUris(uris: List<Uri>) = attachmentDelegate.addImageUris(uris)
    fun addFileUris(uris: List<Uri>) = attachmentDelegate.addFileUris(uris)
    fun updateDraftAttachmentInjectMode(id: String, mode: FileInjectMode) =
        attachmentDelegate.updateDraftAttachmentInjectMode(id, mode)

    fun dismissPendingScannedPdf() = attachmentDelegate.dismissPendingScannedPdf()
    fun convertPendingScannedPdfToImages(maxPages: Int = 4) =
        attachmentDelegate.convertPendingScannedPdfToImages(maxPages)

    suspend fun readAttachmentText(id: String): String? = attachmentDelegate.readAttachmentText(id)
    fun removeDraftAttachment(id: String) = attachmentDelegate.removeDraftAttachment(id)

    // ==================== Core Conversation & Messaging ====================

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
                container.toastController.show(strings().attachments.visionSendBlocked, isError = true)
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
                novelStudioDelegate.syncNovelChapterWordCounts(rolledConv.id)
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
                novelStudioDelegate.syncNovelChapterWordCounts(conv.id)
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
                novelStudioDelegate.syncNovelChapterWordCounts(conv.id)
            }
        }
    }

    fun branchFromMessage(msgId: String) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            val forkedConv = container.conversationForkUseCase.branchFromMessage(conv, msgId) ?: return@launch
            _activeConvId.value = forkedConv.id
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
                novelStudioDelegate.syncNovelChapterWordCounts(conv.id)
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

    // ==================== Memory, Recall & V4 Asset Actions (Delegated) ====================

    fun extractUserMemoriesNow() = assetsAndMemoryDelegate.extractUserMemoriesNow()
    fun addUserMemory(category: MemoryCategory, content: String, pinned: Boolean = false) =
        assetsAndMemoryDelegate.addUserMemory(category, content, pinned)

    fun toggleUserMemoryPin(id: String) = assetsAndMemoryDelegate.toggleUserMemoryPin(id)
    fun toggleUserMemoryEnabled(id: String) = assetsAndMemoryDelegate.toggleUserMemoryEnabled(id)
    fun deleteUserMemory(id: String) = assetsAndMemoryDelegate.deleteUserMemory(id)
    fun addLedgerItem(kind: String, title: String, content: String) =
        assetsAndMemoryDelegate.addLedgerItem(kind, title, content)

    fun toggleLedgerPin(id: String) = assetsAndMemoryDelegate.toggleLedgerPin(id)
    fun deleteLedgerItem(id: String) = assetsAndMemoryDelegate.deleteLedgerItem(id)
    fun toggleMuteRecallKey(key: String) = assetsAndMemoryDelegate.toggleMuteRecallKey(key)
    fun togglePinRecallItem(source: String, id: String) = assetsAndMemoryDelegate.togglePinRecallItem(source, id)
    fun disableRecallItem(source: String, id: String) = assetsAndMemoryDelegate.disableRecallItem(source, id)
    fun reindexActiveConversationVectors() = assetsAndMemoryDelegate.reindexActiveConversationVectors()
    fun triggerManualCompaction() = assetsAndMemoryDelegate.triggerManualCompaction()

    // ==================== Novel Studio Actions (Delegated) ====================

    fun createNovelSession(
        presetId: String,
        title: String,
        customIdea: String,
        pov: String? = null,
        targetWords: Int? = null,
        playStyle: String = "hybrid",
        antiAiRules: Boolean = true,
    ) = novelStudioDelegate.createNovelSession(presetId, title, customIdea, pov, targetWords, playStyle, antiAiRules)

    fun createNovelSessionWithAi(
        presetId: String,
        title: String,
        customIdea: String,
        pov: String? = null,
        targetWords: Int? = null,
        playStyle: String = "hybrid",
        antiAiRules: Boolean = true,
        onCompleted: () -> Unit = {},
    ) = novelStudioDelegate.createNovelSessionWithAi(
        presetId, title, customIdea, pov, targetWords, playStyle, antiAiRules, onCompleted,
    )

    fun updateNovelConfig(transform: (NovelConfig) -> NovelConfig) = novelStudioDelegate.updateNovelConfig(transform)
    fun addOrSelectNovelChapter(
        title: String,
        outline: String,
        markPreviousDone: Boolean = false,
        activateImmediately: Boolean = true,
    ) = novelStudioDelegate.addOrSelectNovelChapter(title, outline, markPreviousDone, activateImmediately)

    fun updateNovelChapter(chapter: NovelChapter) = novelStudioDelegate.updateNovelChapter(chapter)
    fun deleteNovelChapter(chapterId: String) = novelStudioDelegate.deleteNovelChapter(chapterId)
    fun finishActiveChapterAndAdvance(
        nextTitle: String = "",
        nextOutline: String = "",
        autoRecap: Boolean = true,
    ) = novelStudioDelegate.finishActiveChapterAndAdvance(nextTitle, nextOutline, autoRecap)

    fun generateChapterRecap(chapterId: String) = novelStudioDelegate.generateChapterRecap(chapterId)
    fun selectActiveChapter(chapterId: String) = novelStudioDelegate.selectActiveChapter(chapterId)
    fun upsertNovelLore(category: String, title: String, keywords: String, content: String, alwaysActive: Boolean) =
        novelStudioDelegate.upsertNovelLore(category, title, keywords, content, alwaysActive)

    fun saveForeshadowToLore(foreshadowText: String) = novelStudioDelegate.saveForeshadowToLore(foreshadowText)
    fun extractNovelLoreNow() = novelStudioDelegate.extractNovelLoreNow()
    fun deleteNovelLore(id: String) = novelStudioDelegate.deleteNovelLore(id)
    fun toggleNovelLoreEnabled(id: String) = novelStudioDelegate.toggleNovelLoreEnabled(id)
    fun toggleNovelLoreAlwaysActive(id: String) = novelStudioDelegate.toggleNovelLoreAlwaysActive(id)
    fun updateNovelLore(entry: NovelLoreEntry) = novelStudioDelegate.updateNovelLore(entry)
    fun generateThreePathDrafts(hint: String) = novelStudioDelegate.generateThreePathDrafts(hint)
    fun adoptThreePathDraft(option: NovelDraftOption, sendImmediately: Boolean) =
        novelStudioDelegate.adoptThreePathDraft(option, sendImmediately)

    fun runOutlineChat(instruction: String) = novelStudioDelegate.runOutlineChat(instruction)
    fun applyOutlineToBook(outlineText: String) = novelStudioDelegate.applyOutlineToBook(outlineText)
    fun syncChaptersFromOutline(outlineDraft: String) = novelStudioDelegate.syncChaptersFromOutline(outlineDraft)
    fun runPolish(prose: String, instruction: String, presetMode: String? = null) =
        novelStudioDelegate.runPolish(prose, instruction, presetMode)

    fun applyPolishAsVariant(polishedText: String) = novelStudioDelegate.applyPolishAsVariant(polishedText)
    fun refreshNovelChoices() = novelStudioDelegate.refreshNovelChoices()
    fun setPureReadingMode(enabled: Boolean) = novelStudioDelegate.setPureReadingMode(enabled)
    fun updateReaderPrefs(
        fontSize: Int? = null,
        paper: String? = null,
        lineHeight: Float? = null,
        serif: Boolean? = null,
        indent: Boolean? = null,
    ) = novelStudioDelegate.updateReaderPrefs(fontSize, paper, lineHeight, serif, indent)

    fun exportNovelToUri(
        uri: Uri,
        contentResolver: ContentResolver,
        markdown: Boolean,
        includeLore: Boolean = false,
    ) = novelStudioDelegate.exportNovelToUri(uri, contentResolver, markdown, includeLore)

    fun exportNovelEpubToUri(
        uri: Uri,
        contentResolver: ContentResolver,
        includeLore: Boolean = false,
    ) = novelStudioDelegate.exportNovelEpubToUri(uri, contentResolver, includeLore)

    // ==================== RP Studio & Tavern Actions (Delegated) ====================

    fun createRpSessionFromPreset(
        presetId: String,
        customTitle: String? = null,
        playStyle: RpPlayStyle? = null,
        playerName: String? = null,
        playerPersona: String? = null,
    ) = rpStudioDelegate.createRpSessionFromPreset(presetId, customTitle, playStyle, playerName, playerPersona)

    fun createRpSessionFromCharacterCard(cardId: String) = rpStudioDelegate.createRpSessionFromCharacterCard(cardId)
    fun importSillyTavernCardUri(uri: Uri, contentResolver: ContentResolver) =
        assetsAndMemoryDelegate.importSillyTavernCardUri(uri, contentResolver)

    fun importLorebookOrPresetUri(uri: Uri, contentResolver: ContentResolver) =
        assetsAndMemoryDelegate.importLorebookOrPresetUri(uri, contentResolver)

    fun importRpRegexJsonUri(uri: Uri, contentResolver: ContentResolver) =
        assetsAndMemoryDelegate.importRpRegexJsonUri(uri, contentResolver)

    suspend fun importTavernAssetFromBytes(bytes: ByteArray, fileName: String?): Boolean =
        assetsAndMemoryDelegate.importTavernAssetFromBytes(bytes, fileName)

    fun resolveLocalMediaFile(storedPath: String?): java.io.File? =
        assetsAndMemoryDelegate.resolveLocalMediaFile(storedPath)

    fun bindRpCharacterAvatar(charId: String, uri: Uri, contentResolver: ContentResolver) =
        assetsAndMemoryDelegate.bindRpCharacterAvatar(charId, uri, contentResolver)

    fun bindRpCharacterSprite(
        charId: String,
        expressionKey: String?,
        uri: Uri,
        contentResolver: ContentResolver,
    ) = assetsAndMemoryDelegate.bindRpCharacterSprite(charId, expressionKey, uri, contentResolver)

    fun clearRpCharacterSprite(charId: String, expressionKey: String?) =
        assetsAndMemoryDelegate.clearRpCharacterSprite(charId, expressionKey)

    fun bindRpSceneImage(uri: Uri, contentResolver: ContentResolver) =
        assetsAndMemoryDelegate.bindRpSceneImage(uri, contentResolver)

    fun clearRpSceneImage() = assetsAndMemoryDelegate.clearRpSceneImage()
    fun addRpCgFromUri(title: String, caption: String, uri: Uri, contentResolver: ContentResolver) =
        assetsAndMemoryDelegate.addRpCgFromUri(title, caption, uri, contentResolver)

    fun saveCurrentSceneAsCg(title: String) = assetsAndMemoryDelegate.saveCurrentSceneAsCg(title)
    fun applyCgAsScene(cg: RpCgRecord) = assetsAndMemoryDelegate.applyCgAsScene(cg)
    fun deleteRpCg(cgId: String) = assetsAndMemoryDelegate.deleteRpCg(cgId)
    fun generateRpCharacterSprite(charId: String, expressionKey: String = "neutral", customHint: String = "") =
        assetsAndMemoryDelegate.generateRpCharacterSprite(charId, expressionKey, customHint)

    fun generateRpSceneCg(customHint: String = "", saveToGallery: Boolean = true) =
        assetsAndMemoryDelegate.generateRpSceneCg(customHint, saveToGallery)

    fun toggleRpTheaterMode() = rpStudioDelegate.toggleRpTheaterMode()
    fun setRpTheaterMode(enabled: Boolean) = rpStudioDelegate.setRpTheaterMode(enabled)
    fun setRpPlayStyle(style: RpPlayStyle) = rpStudioDelegate.setRpPlayStyle(style)
    fun continueLastAssistantMessage(instruction: String = "") =
        rpStudioDelegate.continueLastAssistantMessage(instruction)

    fun impersonatePlayerDraft() = rpStudioDelegate.impersonatePlayerDraft()
    fun refreshRpChoices() = rpStudioDelegate.refreshRpChoices()
    fun rollDiceForDraft(expression: String, skill: String?, difficulty: Int?, mode: String) =
        rpStudioDelegate.rollDiceForDraft(expression, skill, difficulty, mode)

    fun clearDraftDiceRoll() = rpStudioDelegate.clearDraftDiceRoll()
    fun triggerRpChoice(choice: RpChoice, bypassLock: Boolean = false) =
        rpStudioDelegate.triggerRpChoice(choice, bypassLock)

    fun updateRpConfig(transform: (RpConfig) -> RpConfig) = rpStudioDelegate.updateRpConfig(transform)
    fun updateRpState(transform: (RpGameState) -> RpGameState) = rpStudioDelegate.updateRpState(transform)
    fun updateRpVisualTheme(transform: (RpVisualTheme) -> RpVisualTheme) =
        rpStudioDelegate.updateRpVisualTheme(transform)

    fun resetRpVisualTheme() = rpStudioDelegate.resetRpVisualTheme()
    fun upsertRpRegexScript(script: RpRegexScript) = rpStudioDelegate.upsertRpRegexScript(script)
    fun toggleRpRegexScript(scriptId: String) = rpStudioDelegate.toggleRpRegexScript(scriptId)
    fun deleteRpRegexScript(scriptId: String) = rpStudioDelegate.deleteRpRegexScript(scriptId)
    fun applyRpRegexPreset(presetId: String) = rpStudioDelegate.applyRpRegexPreset(presetId)
    fun applyRpStatusWidgetTemplate(templateId: String) = rpStudioDelegate.applyRpStatusWidgetTemplate(templateId)
    fun handleRpFrontendAction(actionText: String) = rpStudioDelegate.handleRpFrontendAction(actionText)
    fun handleRpFrontendFillInput(text: String) = rpStudioDelegate.handleRpFrontendFillInput(text)
    fun handleRpFrontendSetFlag(key: String, value: String) = rpStudioDelegate.handleRpFrontendSetFlag(key, value)
    fun handleRpFrontendAdjustAffinity(npcName: String, delta: Int) =
        rpStudioDelegate.handleRpFrontendAdjustAffinity(npcName, delta)

    fun updateRpCharacterAffinity(charId: String, delta: Int) =
        rpStudioDelegate.updateRpCharacterAffinity(charId, delta)

    fun upsertRpNpc(character: RpCharacter) = rpStudioDelegate.upsertRpNpc(character)
    fun deleteRpNpc(charId: String) = rpStudioDelegate.deleteRpNpc(charId)
    fun createRpSaveSlot(name: String, note: String = "") = rpStudioDelegate.createRpSaveSlot(name, note)
    fun overwriteRpSaveSlot(slotId: String) = rpStudioDelegate.overwriteRpSaveSlot(slotId)
    fun deleteRpSaveSlot(slotId: String) = rpStudioDelegate.deleteRpSaveSlot(slotId)
    fun loadRpSaveSlotRewind(slot: RpSaveSlot) = rpStudioDelegate.loadRpSaveSlotRewind(slot)
    fun branchFromRpSaveSlot(slot: RpSaveSlot) = rpStudioDelegate.branchFromRpSaveSlot(slot)
    fun exportRpCardJsonToUri(uri: Uri, contentResolver: ContentResolver) =
        assetsAndMemoryDelegate.exportRpCardJsonToUri(uri, contentResolver)

    fun exportRpCardPngToUri(uri: Uri, contentResolver: ContentResolver) =
        assetsAndMemoryDelegate.exportRpCardPngToUri(uri, contentResolver)

    fun selectGreetingVariant(index: Int) = assetsAndMemoryDelegate.selectGreetingVariant(index)
    fun selectConversationPersona(personaId: String?) = assetsAndMemoryDelegate.selectConversationPersona(personaId)
    fun selectConversationPreset(presetId: String?) = assetsAndMemoryDelegate.selectConversationPreset(presetId)
    fun togglePromptPresetBlockEnabled(presetId: String, identifier: String, index: Int) =
        assetsAndMemoryDelegate.togglePromptPresetBlockEnabled(presetId, identifier, index)

    fun mountWorldbook(worldbookId: String, convId: String? = null) =
        assetsAndMemoryDelegate.mountWorldbook(worldbookId, convId)

    fun unmountWorldbook(worldbookId: String) = assetsAndMemoryDelegate.unmountWorldbook(worldbookId)
    fun toggleMountedWorldbookEnabled(worldbookId: String) =
        assetsAndMemoryDelegate.toggleMountedWorldbookEnabled(worldbookId)

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
    ) = assetsAndMemoryDelegate.saveV2LoreEntryForActiveConversation(
        existingLegacyId = existingLegacyId,
        category = category,
        title = title,
        keywordsRaw = keywordsRaw,
        secondaryKeysRaw = secondaryKeysRaw,
        selectiveLogic = selectiveLogic,
        useDepthInjection = useDepthInjection,
        depth = depth,
        sticky = sticky,
        cooldown = cooldown,
        delay = delay,
        content = content,
        alwaysActive = alwaysActive,
    )

    fun syncBoundCharacterCardFields() = assetsAndMemoryDelegate.syncBoundCharacterCardFields()
    fun bindNovelCoverImage(uri: Uri, contentResolver: ContentResolver) =
        assetsAndMemoryDelegate.bindNovelCoverImage(uri, contentResolver)

    fun clearNovelCoverImage() = assetsAndMemoryDelegate.clearNovelCoverImage()
    fun generateNovelCoverArt() = assetsAndMemoryDelegate.generateNovelCoverArt()

    // ==================== Private Infrastructure Helpers ====================

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

    private fun strings(): AppStrings =
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
        scheduleWaterlineRecompute(debounceMs = 80L)
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
        container.retrievalCoordinator.scheduleConversationIndexingFromDb(
            scope = viewModelScope,
            database = container.database,
            conversationId = convId,
        )
    }
}
