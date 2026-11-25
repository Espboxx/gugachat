package com.gugachat.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gugachat.app.AppContainer
import com.gugachat.app.AuthSessionState
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.OnlineChatGroupDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.data.api.WireChatContentPartDto
import com.gugachat.app.data.api.WireChatMessageDto
import com.gugachat.app.data.api.WireImageUrlDto
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.data.local.toEntity
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
import com.gugachat.app.domain.chat.FreeQuotaUtils
import com.gugachat.app.domain.chat.MemoryBudget
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryExtractor
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.MessageUsage
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.NovelPresets
import com.gugachat.app.domain.chat.RpCardCodec
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpDiceRoll
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.RpPresets
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.TimelineReconciler
import com.gugachat.app.domain.chat.UserMemory
import com.gugachat.app.domain.chat.WireChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ChatUiState(
    val enabled: Boolean = true,
    val isLoadingCapabilities: Boolean = false,
    val groups: List<OnlineChatGroupDto> = emptyList(),
    val models: List<OnlineChatModelDto> = emptyList(),
    val selectedGroupId: Long? = null,
    val selectedModelId: String? = null,
    val conversations: List<ChatConversation> = emptyList(),
    val activeConversation: ChatConversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val userMemories: List<UserMemory> = emptyList(),
    val novelChapters: List<NovelChapter> = emptyList(),
    val novelLore: List<NovelLoreEntry> = emptyList(),
    val memoryLedger: List<MemoryLedgerItem> = emptyList(),
    val memoryEpisodes: List<MemoryEpisode> = emptyList(),
    val rpSaveSlots: List<RpSaveSlot> = emptyList(),
    val draftText: String = "",
    val draftAttachments: List<ChatAttachmentMeta> = emptyList(),
    val draftNovelKind: NovelInputKind = NovelInputKind.director,
    val draftRpKind: RpInputKind = RpInputKind.action,
    val draftDiceRoll: RpDiceRoll? = null,
    val isStreaming: Boolean = false,
    val isExtractingMemory: Boolean = false,
    val isCompacting: Boolean = false,
    val threePathDrafts: List<NovelDraftOption> = emptyList(),
    val isGeneratingDrafts: Boolean = false,
    val outlineChatReply: String = "",
    val polishResult: String = "",
    val pureReadingMode: Boolean = false,
    val readerFontSizeSp: Int = 16,
    val readerPaper: String = "cream", // cream | parchment | night
    val readerSerif: Boolean = false,
    val waterline: ContextWaterlineSummary = ContextWaterlineSummary(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val container: AppContainer) : ViewModel() {
    private val chatDao = container.database.chatDao()
    private val userMemoryDao = container.database.userMemoryDao()

    private val _activeConvId = MutableStateFlow<String?>(null)
    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            combine(chatDao.observeConversations(), _activeConvId) { list, curId ->
                list.map { it.toDomain() } to curId
            }.collect { (domainList, curId) ->
                val active = domainList.firstOrNull { it.id == curId } ?: domainList.firstOrNull()
                if (active != null && active.id != curId) {
                    _activeConvId.value = active.id
                }
                _ui.update { it.copy(conversations = domainList, activeConversation = active) }
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
            _activeConvId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(Triple<String?, List<ChatMessage>, ChatMessage?>(null, emptyList(), null))
                } else {
                    val dbFlow = chatDao.observeMessages(id).map { rows -> rows.map { r -> r.toDomain() } }
                    combine(dbFlow, _streamingMessage) { dbMsgs, live ->
                        Triple(id, dbMsgs, live)
                    }
                }
            }.collect { (convId, dbMsgs, live) ->
                val merged = if (live != null && live.conversationId == convId) {
                    val dbMatch = dbMsgs.firstOrNull { it.id == live.id }
                    if (dbMatch != null && dbMatch.status != "streaming" && live.status != "streaming") {
                        _streamingMessage.compareAndSet(live, null)
                        dbMsgs
                    } else if (dbMatch != null) {
                        dbMsgs.map { if (it.id == live.id) live else it }
                    } else {
                        dbMsgs + live
                    }
                } else {
                    dbMsgs
                }
                _ui.update { it.copy(messages = merged) }
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

        loadCapabilities()
    }

    fun loadCapabilities() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoadingCapabilities = true) }
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
                        enabled = (cap?.enabled ?: false) || sortedGroups.isNotEmpty(),
                        groups = sortedGroups,
                        selectedGroupId = pickedGroupId,
                        isLoadingCapabilities = false,
                    )
                }
                if (pickedGroupId != null) {
                    loadModels(pickedGroupId)
                }
            } catch (e: Throwable) {
                _ui.update { it.copy(isLoadingCapabilities = false) }
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
        recomputeWaterline()
    }

    fun setDraftNovelKind(kind: NovelInputKind) {
        _ui.update { it.copy(draftNovelKind = kind) }
    }

    fun setDraftRpKind(kind: RpInputKind) {
        _ui.update { it.copy(draftRpKind = kind) }
    }

    fun addAttachmentUri(uri: Uri) {
        viewModelScope.launch {
            if (_ui.value.draftAttachments.size >= 4) {
                container.toastController.show("Max 4 images per message", isError = true)
                return@launch
            }
            val entity = container.attachmentStore.saveFromUri(uri)
            if (entity == null) {
                container.toastController.show("Invalid image", isError = true)
                return@launch
            }
            _ui.update { it.copy(draftAttachments = it.draftAttachments + entity.toDomain()) }
            recomputeWaterline()
        }
    }

    fun removeDraftAttachment(id: String) {
        _ui.update { it.copy(draftAttachments = it.draftAttachments.filterNot { a -> a.id == id }) }
        recomputeWaterline()
    }

    fun selectConversation(convId: String) {
        if (!_ui.value.isStreaming) _streamingMessage.value = null
        _activeConvId.value = convId
        val conv = _ui.value.conversations.firstOrNull { it.id == convId }
        _ui.update { it.copy(activeConversation = conv ?: it.activeConversation, draftDiceRoll = null) }
        if (conv != null) {
            if (conv.groupId != null && conv.groupId != _ui.value.selectedGroupId) {
                selectGroup(conv.groupId)
            } else if (!conv.model.isNullOrBlank()) {
                _ui.update { it.copy(selectedModelId = conv.model) }
            }
        }
    }

    fun createNewChatSession(title: String = "新对话") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val conv = ChatConversation(
                id = UUID.randomUUID().toString(),
                title = title,
                mode = ConversationMode.chat,
                groupId = _ui.value.selectedGroupId,
                model = _ui.value.selectedModelId,
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
            chatDao.deleteConversationCascade(convId)
            if (_activeConvId.value == convId) {
                _activeConvId.value = _ui.value.conversations.firstOrNull { it.id != convId }?.id
            }
        }
    }

    fun updateConversationParams(systemPrompt: String, params: ChatParams) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: ensureActiveConversation()
            chatDao.upsertConversation(
                conv.copy(
                    systemPrompt = systemPrompt,
                    params = params,
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            recomputeWaterline()
        }
    }

    fun toggleActiveReasoning() {
        val conv = _ui.value.activeConversation
        val params = conv?.params ?: ChatParams()
        updateConversationParams(
            systemPrompt = conv?.systemPrompt.orEmpty(),
            params = params.copy(enableReasoning = !params.enableReasoning),
        )
    }

    fun toggleActiveMemory() {
        val conv = _ui.value.activeConversation
        val params = conv?.params ?: ChatParams()
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
        if (_ui.value.isStreaming) return
        val groupId = _ui.value.selectedGroupId
        val modelId = _ui.value.selectedModelId
        if (groupId == null || modelId.isNullOrBlank()) {
            container.toastController.show("Please select a group and model first", isError = true)
            return
        }
        val text = (overrideText ?: _ui.value.draftText).trim()
        val attachments = if (overrideText == null) _ui.value.draftAttachments else emptyList()
        if (text.isEmpty() && attachments.isEmpty()) return

        viewModelScope.launch {
            val conv = ensureActiveConversation()
            val curMsgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val nextSeq = (curMsgs.maxOfOrNull { it.seq } ?: 0) + 1
            val now = System.currentTimeMillis()

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                role = "user",
                content = text,
                createdAt = now,
                seq = nextSeq,
                attachmentIds = attachments.map { it.id },
                inputKind = if (conv.mode == ConversationMode.novel) _ui.value.draftNovelKind else null,
                rpInputKind = if (conv.mode == ConversationMode.rp) _ui.value.draftRpKind else null,
                rpDiceRoll = if (conv.mode == ConversationMode.rp) _ui.value.draftDiceRoll else null,
                status = "done",
                tokenEstimate = ContextBuilder.estimateMessageTokens(text, attachments.size),
            )
            chatDao.upsertMessage(userMsg.toEntity())

            val updatedTitle = if (conv.mode == ConversationMode.chat && conv.title == "新对话" && text.isNotBlank()) {
                text.lineSequence().firstOrNull()?.take(22)?.trim().orEmpty().ifBlank { conv.title }
            } else {
                conv.title
            }
            val updatedConv = conv.copy(title = updatedTitle, groupId = groupId, model = modelId, updatedAt = now)
            chatDao.upsertConversation(updatedConv.toEntity())

            if (overrideText == null) {
                _ui.update { it.copy(draftText = "", draftAttachments = emptyList(), draftDiceRoll = null) }
            } else {
                _ui.update { it.copy(draftDiceRoll = null) }
            }
            launchCompletionForConversation(updatedConv)
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        _ui.update { it.copy(isStreaming = false) }
    }

    fun regenerateFromAssistant(assistantMsgId: String) {
        if (_ui.value.isStreaming) return
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
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
            )
            persistReconciliation(reconciled)
            launchCompletionForConversation(conv, existingMessageForVariant = if (conv.mode == ConversationMode.rp) target else null)
        }
    }

    fun editAndResendUserMessage(userMsgId: String, newText: String) {
        if (_ui.value.isStreaming || newText.isBlank()) return
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
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
            )
            persistReconciliation(reconciled)
            launchCompletionForConversation(conv)
        }
    }

    fun deleteMessage(msgId: String) {
        viewModelScope.launch {
            if (!_ui.value.isStreaming) _streamingMessage.value = null
            val conv = _ui.value.activeConversation ?: return@launch
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val updated = TimelineReconciler.softDeleteMessage(msgId, msgs)
            chatDao.upsertMessages(updated.map { it.toEntity() })
        }
    }

    fun branchFromMessage(msgId: String) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            val msgs = TimelineReconciler.activeMessages(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })
            val target = msgs.firstOrNull { it.id == msgId } ?: return@launch
            val now = System.currentTimeMillis()
            val newConvId = UUID.randomUUID().toString()
            val forkedConv = conv.copy(
                id = newConvId,
                title = "${conv.title} (分支)",
                createdAt = now,
                updatedAt = now,
            )
            chatDao.upsertConversation(forkedConv.toEntity())
            val copiedMsgs = msgs.filter { it.seq <= target.seq }.map {
                it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId)
            }
            if (copiedMsgs.isNotEmpty()) chatDao.upsertMessages(copiedMsgs.map { it.toEntity() })
            _activeConvId.value = newConvId
            container.toastController.show("Branch created")
        }
    }

    fun switchMessageVariant(msgId: String, variantIndex: Int) {
        viewModelScope.launch {
            if (!_ui.value.isStreaming) _streamingMessage.value = null
            val conv = _ui.value.activeConversation ?: return@launch
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
            )
            chatDao.upsertMessage(updatedMsg.toEntity())
            if (conv.mode == ConversationMode.rp && conv.rp != null && variant.rpStateSnapshot != null) {
                val updatedRp = conv.rp.copy(
                    state = variant.rpStateSnapshot,
                    pendingChoices = variant.rpChoices,
                )
                chatDao.upsertConversation(conv.copy(rp = updatedRp, updatedAt = System.currentTimeMillis()).toEntity())
            }
        }
    }

    private fun launchCompletionForConversation(
        conv: ChatConversation,
        existingMessageForVariant: ChatMessage? = null,
    ) {
        val groupId = conv.groupId ?: _ui.value.selectedGroupId ?: return
        val modelId = conv.model ?: _ui.value.selectedModelId ?: return

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _ui.update { it.copy(isStreaming = true) }

            val allMsgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val nextSeq = existingMessageForVariant?.seq ?: ((allMsgs.maxOfOrNull { it.seq } ?: 0) + 1)
            val assistantId = existingMessageForVariant?.id ?: UUID.randomUUID().toString()
            val assistantStart = ChatMessage(
                id = assistantId,
                conversationId = conv.id,
                role = "assistant",
                content = "",
                reasoning = "",
                model = modelId,
                createdAt = System.currentTimeMillis(),
                seq = nextSeq,
                status = "streaming",
                variants = existingMessageForVariant?.variants.orEmpty(),
            )
            _streamingMessage.value = assistantStart
            chatDao.upsertMessage(assistantStart.toEntity())

            val streamLock = Any()
            val contentBuf = StringBuilder()
            val reasoningBuf = StringBuilder()
            var latestUsage: MessageUsage? = null
            var lastUiEmitMs = 0L
            var lastDbFlushMs = System.currentTimeMillis()
            var uiDirty = false

            fun buildSnapshotLocked(status: String = "streaming", error: String? = null): ChatMessage =
                assistantStart.copy(
                    content = contentBuf.toString(),
                    reasoning = reasoningBuf.toString(),
                    usage = latestUsage,
                    status = status,
                    error = error,
                )

            fun buildSnapshot(status: String = "streaming", error: String? = null): ChatMessage =
                synchronized(streamLock) { buildSnapshotLocked(status, error) }

            fun publishStreamingSnapshot(snap: ChatMessage) {
                _streamingMessage.update { cur ->
                    if (cur == null || cur.id == assistantId) snap else cur
                }
            }

            fun onChunkAppended(deltaText: String? = null, deltaReasoning: String? = null) {
                val immediateSnap: ChatMessage? = synchronized(streamLock) {
                    if (!deltaText.isNullOrEmpty()) contentBuf.append(deltaText)
                    if (!deltaReasoning.isNullOrEmpty()) reasoningBuf.append(deltaReasoning)
                    val now = System.currentTimeMillis()
                    if (lastUiEmitMs == 0L || now - lastUiEmitMs >= 35L) {
                        lastUiEmitMs = now
                        uiDirty = false
                        buildSnapshotLocked()
                    } else {
                        uiDirty = true
                        null
                    }
                }
                if (immediateSnap != null) {
                    publishStreamingSnapshot(immediateSnap)
                }
            }

            val flushTickerJob = viewModelScope.launch {
                while (isActive) {
                    delay(35L)
                    val pendingSnap: ChatMessage? = synchronized(streamLock) {
                        if (!uiDirty) {
                            null
                        } else {
                            lastUiEmitMs = System.currentTimeMillis()
                            uiDirty = false
                            buildSnapshotLocked()
                        }
                    }
                    if (pendingSnap != null) {
                        publishStreamingSnapshot(pendingSnap)
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastDbFlushMs >= 1000L) {
                        lastDbFlushMs = now
                        val checkpoint = buildSnapshot()
                        if (checkpoint.content.isNotEmpty() || checkpoint.reasoning.isNotEmpty()) {
                            chatDao.upsertMessage(checkpoint.toEntity())
                        }
                    }
                }
            }

            try {
                var built = buildWirePayload(conv, allMsgs, groupId, modelId)
                try {
                    streamOnce(
                        request = built,
                        onTextDelta = { delta -> onChunkAppended(deltaText = delta) },
                        onReasoningDelta = { r -> onChunkAppended(deltaReasoning = r) },
                        onUsageSet = { u -> synchronized(streamLock) { latestUsage = u } },
                    )
                } catch (e: ApiException) {
                    val isBufEmpty = synchronized(streamLock) { contentBuf.isEmpty() }
                    if (e.isContextTooLong && isBufEmpty) {
                        container.toastController.show("Context too long, auto-compacting...")
                        val outcome = ContextCompactor.compactConversation(container.onlineChatApi, conv, allMsgs)
                        if (outcome != null) {
                            val updatedConv = conv.copy(
                                archivedSummary = outcome.archivedSummary,
                                summarizedUntilSeq = outcome.summarizedUntilSeq,
                            )
                            chatDao.upsertConversation(updatedConv.toEntity())
                            outcome.newEpisode?.let { chatDao.upsertMemoryEpisode(it.toEntity()) }
                            built = buildWirePayload(updatedConv, allMsgs, groupId, modelId)
                            streamOnce(
                                request = built,
                                onTextDelta = { delta -> onChunkAppended(deltaText = delta) },
                                onReasoningDelta = { r -> onChunkAppended(deltaReasoning = r) },
                                onUsageSet = { u -> synchronized(streamLock) { latestUsage = u } },
                            )
                        } else {
                            throw e
                        }
                    } else {
                        throw e
                    }
                }

                flushTickerJob.cancel()
                val (finalRawContent, finalReasoning, finalUsage) = synchronized(streamLock) {
                    Triple(contentBuf.toString(), reasoningBuf.toString(), latestUsage)
                }
                finalizeCompletedAssistantTurn(
                    conv = conv,
                    assistantTemplate = assistantStart,
                    rawContent = finalRawContent,
                    reasoning = finalReasoning,
                    usage = finalUsage,
                )
            } catch (_: CancellationException) {
                flushTickerJob.cancel()
                withContext(NonCancellable) {
                    val aborted = buildSnapshot(status = "aborted")
                    publishStreamingSnapshot(aborted)
                    chatDao.upsertMessage(aborted.toEntity())
                }
            } catch (e: Throwable) {
                flushTickerJob.cancel()
                val failed = buildSnapshot(status = "error", error = e.message ?: "Generation failed")
                publishStreamingSnapshot(failed)
                chatDao.upsertMessage(failed.toEntity())
                container.toastController.show(e.message ?: "Generation failed", isError = true)
            } finally {
                flushTickerJob.cancel()
                _ui.update { it.copy(isStreaming = false) }
            }
        }
    }

    private suspend fun buildWirePayload(
        conv: ChatConversation,
        msgs: List<ChatMessage>,
        groupId: Long,
        modelId: String,
    ): OnlineChatCompletionRequestDto {
        val settings = container.settingsStore.currentSettings()
        val budget = parseMemoryBudget(settings.memoryBudget)
        val modelMeta = _ui.value.models.firstOrNull { it.id == modelId }
        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = msgs,
                memories = _ui.value.userMemories,
                memoryBudget = budget,
                modelContextWindow = modelMeta?.context_window ?: 128000,
                novelChapters = _ui.value.novelChapters,
                novelLore = _ui.value.novelLore,
                memoryLedger = _ui.value.memoryLedger,
                memoryEpisodes = _ui.value.memoryEpisodes,
                resolveAttachmentDataUrl = { attId -> container.attachmentStore.resolveDataUrl(attId) },
            ),
        )
        return OnlineChatCompletionRequestDto(
            group_id = groupId,
            model = modelId,
            messages = built.wireMessages.map(::toWireDto),
            stream = true,
            temperature = conv.params.temperature,
            top_p = conv.params.topP,
            max_tokens = conv.params.maxTokens,
            reasoning_effort = if (conv.params.enableReasoning) conv.params.reasoningEffort else null,
        )
    }

    private suspend fun streamOnce(
        request: OnlineChatCompletionRequestDto,
        onTextDelta: (String) -> Unit,
        onReasoningDelta: (String) -> Unit,
        onUsageSet: (MessageUsage) -> Unit,
    ) {
        container.onlineChatApi.streamCompletion(
            request = request,
            onDelta = onTextDelta,
            onReasoning = onReasoningDelta,
            onUsage = { u ->
                onUsageSet(
                    MessageUsage(
                        promptTokens = u.prompt_tokens,
                        completionTokens = u.completion_tokens,
                        totalTokens = u.total_tokens,
                        cacheReadInputTokens = u.cache_read_input_tokens,
                    ),
                )
            },
        )
    }

    private suspend fun finalizeCompletedAssistantTurn(
        conv: ChatConversation,
        assistantTemplate: ChatMessage,
        rawContent: String,
        reasoning: String,
        usage: MessageUsage?,
    ) {
        var nextConv = conv
        var finalContent = rawContent
        var rpChoices: List<RpChoice> = emptyList()
        var novelChoices: List<String> = emptyList()
        var updatedVariants = assistantTemplate.variants

        if (conv.mode == ConversationMode.novel && conv.novel != null) {
            val outcome = NovelEngine.parseNovelTurnResponse(
                rawText = rawContent,
                conversationId = conv.id,
                turnSeq = assistantTemplate.seq,
                existingLore = _ui.value.novelLore,
            )
            finalContent = outcome.cleanProse
            novelChoices = outcome.choices
            if (outcome.loreUpserts.isNotEmpty()) {
                chatDao.upsertNovelLoreList(outcome.loreUpserts.map { it.toEntity() })
            }
            var novelCfg = conv.novel.copy(
                currentStatus = outcome.statusHud ?: conv.novel.currentStatus,
                pendingChoices = outcome.choices,
            )
            val activeChapter = _ui.value.novelChapters.firstOrNull { it.id == novelCfg.activeChapterId }
                ?: _ui.value.novelChapters.maxByOrNull { it.order }
            if (activeChapter != null) {
                val addedWords = finalContent.count { !it.isWhitespace() }
                if (outcome.chapterEndSummary != null && novelCfg.autoAdvanceChapter) {
                    chatDao.upsertNovelChapter(
                        activeChapter.copy(
                            status = "done",
                            summary = outcome.chapterEndSummary,
                            wordCount = activeChapter.wordCount + addedWords,
                            endSeq = assistantTemplate.seq,
                            updatedAt = System.currentTimeMillis(),
                        ).toEntity(),
                    )
                    val nextOrder = (_ui.value.novelChapters.maxOfOrNull { it.order } ?: 0) + 1
                    val nextChap = NovelChapter(
                        id = UUID.randomUUID().toString(),
                        conversationId = conv.id,
                        order = nextOrder,
                        title = outcome.suggestedNextChapterTitle ?: "第${nextOrder}章",
                        status = "writing",
                        startSeq = assistantTemplate.seq + 1,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                    chatDao.upsertNovelChapter(nextChap.toEntity())
                    novelCfg = novelCfg.copy(activeChapterId = nextChap.id)
                } else {
                    chatDao.upsertNovelChapter(
                        activeChapter.copy(
                            wordCount = activeChapter.wordCount + addedWords,
                            updatedAt = System.currentTimeMillis(),
                        ).toEntity(),
                    )
                }
            }
            nextConv = nextConv.copy(novel = novelCfg)
        } else if (conv.mode == ConversationMode.rp && conv.rp != null) {
            val outcome = RpEngine.parseRpTurnResponse(
                rawText = rawContent,
                currentState = conv.rp.state,
                currentCast = conv.rp.cast,
                conversationId = conv.id,
                turnSeq = assistantTemplate.seq,
            )
            finalContent = outcome.cleanNarrative
            rpChoices = outcome.choices
            if (outcome.ledgerUpserts.isNotEmpty()) {
                chatDao.upsertMemoryLedgerList(outcome.ledgerUpserts.map { it.toEntity() })
            }
            val nextRp = conv.rp.copy(
                state = outcome.updatedState,
                cast = outcome.updatedCast,
                pendingChoices = outcome.choices,
            )
            nextConv = nextConv.copy(rp = nextRp)
            val newVariant = MessageVariant(
                id = UUID.randomUUID().toString(),
                content = finalContent,
                reasoning = reasoning,
                createdAt = System.currentTimeMillis(),
                usage = usage,
                model = assistantTemplate.model,
                rpStateSnapshot = outcome.updatedState,
                rpChoices = rpChoices,
            )
            updatedVariants = assistantTemplate.variants + newVariant
        }

        val finalMsg = assistantTemplate.copy(
            content = finalContent,
            reasoning = reasoning,
            usage = usage,
            rpChoices = rpChoices,
            novelChoices = novelChoices,
            status = "done",
            tokenEstimate = ContextBuilder.estimateMessageTokens(finalContent),
            variants = updatedVariants,
            activeVariantIndex = if (updatedVariants.isNotEmpty()) updatedVariants.lastIndex else 0,
        )
        _streamingMessage.update { cur -> if (cur == null || cur.id == finalMsg.id) finalMsg else cur }
        chatDao.upsertMessage(finalMsg.toEntity())
        chatDao.upsertConversation(nextConv.copy(updatedAt = System.currentTimeMillis()).toEntity())

        // Background auto memory extraction & hierarchical episode consolidation
        if (nextConv.mode == ConversationMode.chat && nextConv.params.enableAutoMemory) {
            runBackgroundMemoryExtraction(nextConv)
        }
        if (nextConv.params.enableAutoConsolidate) {
            runBackgroundEpisodeConsolidation(nextConv)
        }
    }

    private fun runBackgroundMemoryExtraction(conv: ChatConversation) {
        val gid = conv.groupId ?: return
        val model = conv.model ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val msgs = TimelineReconciler.activeMessages(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })
                if (msgs.size < 4 || msgs.size % 4 != 0) return@launch
                val existing = userMemoryDao.getAll().map { it.toDomain() }
                val prompt = MemoryExtractor.buildMemoryExtractionMessages(existing, msgs)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.2, max_tokens = 500),
                )
                val extracted = MemoryExtractor.parseExtractedMemories(res.content)
                if (extracted.isNotEmpty()) {
                    val (merged, added) = MemoryExtractor.mergeExtractedMemories(existing, extracted)
                    if (added > 0) userMemoryDao.upsertAll(merged.map { it.toEntity() })
                }
            }
        }
    }

    private fun runBackgroundEpisodeConsolidation(conv: ChatConversation) {
        val gid = conv.groupId ?: return
        val model = conv.model ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val msgs = TimelineReconciler.activeMessages(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })
                val eps = chatDao.getMemoryEpisodesForConversation(conv.id).map { it.toDomain() }
                val batch = EpisodeConsolidator.findNextMessagesBatch(msgs, eps)
                if (batch != null) {
                    val prompt = EpisodeConsolidator.buildEpisodeConsolidationPrompt(conv.title, batch)
                    val res = container.onlineChatApi.completeOnce(
                        OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.25, max_tokens = 600),
                    )
                    val newEp = EpisodeConsolidator.parseConsolidationResponse(
                        raw = res.content,
                        conversationId = conv.id,
                        fromSeq = batch.fromSeq,
                        toSeq = batch.toSeq,
                        level = 1,
                    )
                    chatDao.upsertMemoryEpisode(newEp.toEntity())
                }
            }
        }
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
                    container.toastController.show("No new long-term memories extracted")
                } else {
                    val (merged, added) = MemoryExtractor.mergeExtractedMemories(existing, extracted)
                    userMemoryDao.upsertAll(merged.map { it.toEntity() })
                    container.toastController.show("Added $added memories")
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
        }
    }

    fun deleteUserMemory(id: String) {
        viewModelScope.launch { userMemoryDao.deleteById(id) }
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
        }
    }

    fun toggleLedgerPin(id: String) {
        viewModelScope.launch {
            val item = _ui.value.memoryLedger.firstOrNull { it.id == id } ?: return@launch
            chatDao.upsertMemoryLedger(item.copy(pinned = !item.pinned, updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun deleteLedgerItem(id: String) {
        viewModelScope.launch { chatDao.deleteMemoryLedger(id) }
    }

    fun triggerManualCompaction() {
        val conv = _ui.value.activeConversation ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isCompacting = true) }
            try {
                val out = ContextCompactor.compactConversation(container.onlineChatApi, conv, _ui.value.messages)
                if (out == null) {
                    container.toastController.show("Not enough history to compact")
                } else {
                    chatDao.upsertConversation(
                        conv.copy(
                            archivedSummary = out.archivedSummary,
                            summarizedUntilSeq = out.summarizedUntilSeq,
                            updatedAt = System.currentTimeMillis(),
                        ).toEntity(),
                    )
                    out.newEpisode?.let { chatDao.upsertMemoryEpisode(it.toEntity()) }
                    container.toastController.show("Context compacted")
                }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Compaction failed", isError = true)
            } finally {
                _ui.update { it.copy(isCompacting = false) }
            }
        }
    }

    // ==================== Novel Studio Actions ====================

    fun createNovelSession(presetId: String, title: String, customIdea: String) {
        viewModelScope.launch {
            val preset = NovelPresets.findById(presetId)
            val now = System.currentTimeMillis()
            val convId = UUID.randomUUID().toString()
            val ch1Id = UUID.randomUUID().toString()
            val bookTitle = title.trim().ifBlank { preset.name }
            val cfg = NovelConfig(
                title = bookTitle,
                synopsis = customIdea.trim().ifBlank { preset.synopsis },
                genre = preset.genre,
                style = preset.style,
                pov = preset.pov,
                worldview = preset.worldview,
                mainOutline = preset.mainOutline,
                authorNote = preset.authorNote,
                targetWordsPerChapter = preset.targetWordsPerChapter,
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
        }
    }

    fun updateNovelConfig(transform: (NovelConfig) -> NovelConfig) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            val cur = conv.novel ?: return@launch
            chatDao.upsertConversation(conv.copy(novel = transform(cur), updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun addOrSelectNovelChapter(title: String, outline: String) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextOrder = (_ui.value.novelChapters.maxOfOrNull { it.order } ?: 0) + 1
            val tip = TimelineReconciler.findActiveTipSeq(_ui.value.messages)
            val ch = NovelChapter(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                order = nextOrder,
                title = title.trim().ifBlank { "第${nextOrder}章" },
                outline = outline.trim(),
                status = "writing",
                startSeq = tip + 1,
                createdAt = now,
                updatedAt = now,
            )
            chatDao.upsertNovelChapter(ch.toEntity())
            chatDao.upsertConversation(conv.copy(novel = novel.copy(activeChapterId = ch.id), updatedAt = now).toEntity())
        }
    }

    fun selectActiveChapter(chapterId: String) {
        updateNovelConfig { it.copy(activeChapterId = chapterId) }
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
        }
    }

    fun deleteNovelLore(id: String) {
        viewModelScope.launch { chatDao.deleteNovelLore(id) }
    }

    fun generateThreePathDrafts(hint: String) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId ?: return
        val model = conv.model ?: _ui.value.selectedModelId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingDrafts = true) }
            try {
                val activeCh = _ui.value.novelChapters.firstOrNull { it.id == novel.activeChapterId }
                val prompt = NovelEngine.buildThreePathDraftMessages(novel, activeCh, _ui.value.messages, hint)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.8, max_tokens = 1200),
                )
                val options = NovelEngine.parseThreePathDrafts(res.content)
                _ui.update { it.copy(threePathDrafts = options) }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Draft generation failed", isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingDrafts = false) }
            }
        }
    }

    fun runOutlineChat(instruction: String) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId ?: return
        val model = conv.model ?: _ui.value.selectedModelId ?: return
        if (instruction.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingDrafts = true) }
            try {
                val prompt = NovelEngine.buildOutlineChatMessages(novel, _ui.value.novelChapters, instruction)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.7, max_tokens = 1200),
                )
                _ui.update { it.copy(outlineChatReply = res.content.trim()) }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Outline chat failed", isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingDrafts = false) }
            }
        }
    }

    fun runPolish(prose: String, instruction: String) {
        val conv = _ui.value.activeConversation ?: return
        val gid = conv.groupId ?: _ui.value.selectedGroupId ?: return
        val model = conv.model ?: _ui.value.selectedModelId ?: return
        if (prose.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingDrafts = true) }
            try {
                val prompt = NovelEngine.buildPolishMessages(prose, conv.novel?.style.orEmpty(), instruction)
                val res = container.onlineChatApi.completeOnce(
                    OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.65, max_tokens = 1800),
                )
                _ui.update { it.copy(polishResult = res.content.trim()) }
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Polish failed", isError = true)
            } finally {
                _ui.update { it.copy(isGeneratingDrafts = false) }
            }
        }
    }

    fun setPureReadingMode(enabled: Boolean) {
        _ui.update { it.copy(pureReadingMode = enabled) }
    }

    fun updateReaderPrefs(fontSize: Int? = null, paper: String? = null, serif: Boolean? = null) {
        _ui.update {
            it.copy(
                readerFontSizeSp = fontSize ?: it.readerFontSizeSp,
                readerPaper = paper ?: it.readerPaper,
                readerSerif = serif ?: it.readerSerif,
            )
        }
    }

    fun exportNovelToUri(uri: Uri, contentResolver: android.content.ContentResolver, markdown: Boolean) {
        val conv = _ui.value.activeConversation ?: return
        val novel = conv.novel ?: return
        viewModelScope.launch {
            try {
                val text = if (markdown) {
                    NovelExport.formatAsMarkdown(novel, _ui.value.novelChapters, _ui.value.messages)
                } else {
                    NovelExport.formatAsTxt(novel, _ui.value.novelChapters, _ui.value.messages)
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                container.toastController.show("Exported successfully")
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    // ==================== RP Studio Actions ====================

    fun createRpSessionFromPreset(presetId: String, customTitle: String? = null) {
        viewModelScope.launch {
            val preset = RpPresets.findById(presetId)
            val rp = preset.config.copy(title = customTitle?.trim()?.ifBlank { null } ?: preset.config.title)
            insertRpConversation(rp, emptyList())
        }
    }

    fun importSillyTavernCardUri(uri: Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Empty file")
                }
                val jsonText = if (RpCardCodec.isPngBytes(bytes)) {
                    RpCardCodec.extractCardJsonFromPng(bytes) ?: error("No ccv3/chara metadata found in PNG")
                } else {
                    bytes.toString(Charsets.UTF_8)
                }
                val imported = RpCardCodec.parseCardJson(jsonText, conversationId = "pending")
                insertRpConversation(imported.rpConfig, imported.importedLore)
                container.toastController.show("Imported SillyTavern ${imported.specVersion.uppercase()} card")
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Card import failed", isError = true)
            }
        }
    }

    private suspend fun insertRpConversation(rp: RpConfig, importedLore: List<NovelLoreEntry>) {
        val now = System.currentTimeMillis()
        val convId = UUID.randomUUID().toString()
        val conv = ChatConversation(
            id = convId,
            title = rp.title,
            mode = ConversationMode.rp,
            groupId = _ui.value.selectedGroupId,
            model = _ui.value.selectedModelId,
            rp = rp,
            createdAt = now,
            updatedAt = now,
        )
        chatDao.upsertConversation(conv.toEntity())
        if (rp.greeting.isNotBlank()) {
            val greetingMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "assistant",
                content = rp.greeting,
                createdAt = now,
                seq = 1,
                rpChoices = rp.pendingChoices,
                status = "done",
                variants = listOf(
                    MessageVariant(
                        id = UUID.randomUUID().toString(),
                        content = rp.greeting,
                        createdAt = now,
                        rpStateSnapshot = rp.state,
                        rpChoices = rp.pendingChoices,
                    ),
                ),
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

    fun rollDiceForDraft(expression: String, skill: String?, difficulty: Int?, mode: String) {
        val roll = RpEngine.rollDice(expression = expression, difficulty = difficulty, skill = skill?.takeIf { it.isNotBlank() }, mode = mode)
        _ui.update { it.copy(draftDiceRoll = roll) }
    }

    fun clearDraftDiceRoll() {
        _ui.update { it.copy(draftDiceRoll = null) }
    }

    fun triggerRpChoice(choice: RpChoice) {
        _ui.update { it.copy(draftRpKind = choice.inputKind) }
        if (!choice.skillCheck.isNullOrBlank()) {
            val expr = if (_ui.value.activeConversation?.rp?.systemRules?.contains("d100", ignoreCase = true) == true) "1d100" else "1d20"
            val roll = RpEngine.rollDice(expr, difficulty = choice.difficulty, skill = choice.skillCheck)
            _ui.update { it.copy(draftDiceRoll = roll) }
        }
        sendMessage(overrideText = choice.actionText)
    }

    fun updateRpConfig(transform: (RpConfig) -> RpConfig) {
        viewModelScope.launch {
            val conv = _ui.value.activeConversation ?: return@launch
            val rp = conv.rp ?: return@launch
            chatDao.upsertConversation(conv.copy(rp = transform(rp), updatedAt = System.currentTimeMillis()).toEntity())
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

    fun addRpNpc(name: String, role: String, personality: String) {
        if (name.isBlank()) return
        updateRpConfig { cfg ->
            cfg.copy(
                cast = cfg.cast + RpCharacter(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    role = role.ifBlank { "ally" },
                    personality = personality.trim(),
                ),
            )
        }
    }

    fun createRpSaveSlot(name: String) {
        val conv = _ui.value.activeConversation ?: return
        val rp = conv.rp ?: return
        viewModelScope.launch {
            val slot = RpEngine.createSaveSlot(
                conversationId = conv.id,
                name = name,
                rp = rp,
                messages = _ui.value.messages,
                ledger = _ui.value.memoryLedger,
                episodes = _ui.value.memoryEpisodes,
            )
            chatDao.upsertRpSaveSlot(slot.toEntity())
            container.toastController.show("Saved slot: ${slot.name}")
        }
    }

    fun loadRpSaveSlotRewind(slot: RpSaveSlot) {
        val conv = _ui.value.activeConversation ?: return
        viewModelScope.launch {
            if (!_ui.value.isStreaming) _streamingMessage.value = null
            chatDao.deleteMessagesForConversation(conv.id)
            chatDao.deleteMemoryLedgerForConversation(conv.id)
            chatDao.deleteMemoryEpisodesForConversation(conv.id)
            chatDao.upsertMessages(slot.activeMessagesSnapshot.map { it.toEntity() })
            if (slot.ledgerItems.isNotEmpty()) chatDao.upsertMemoryLedgerList(slot.ledgerItems.map { it.toEntity() })
            if (slot.episodes.isNotEmpty()) chatDao.upsertMemoryEpisodes(slot.episodes.map { it.toEntity() })
            chatDao.upsertConversation(conv.copy(rp = slot.rpConfig, updatedAt = System.currentTimeMillis()).toEntity())
            container.toastController.show("Loaded save slot: ${slot.name}")
        }
    }

    fun exportRpCardJsonToUri(uri: Uri, contentResolver: android.content.ContentResolver) {
        val rp = _ui.value.activeConversation?.rp ?: return
        viewModelScope.launch {
            try {
                val json = RpCardCodec.exportToV3Json(rp, _ui.value.novelLore)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                container.toastController.show("Exported SillyTavern V3 JSON")
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    // ==================== Helpers ====================

    private suspend fun ensureActiveConversation(): ChatConversation {
        _ui.value.activeConversation?.let { return it }
        val now = System.currentTimeMillis()
        val conv = ChatConversation(
            id = UUID.randomUUID().toString(),
            title = "新对话",
            mode = ConversationMode.chat,
            groupId = _ui.value.selectedGroupId,
            model = _ui.value.selectedModelId,
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
        val conv = _ui.value.activeConversation ?: return
        val budget = parseMemoryBudget(container.settingsState.value.memoryBudget)
        val modelWin = _ui.value.models.firstOrNull { it.id == (_ui.value.selectedModelId ?: conv.model) }?.context_window ?: 128000
        val wl = ContextBuilder.computeContextWaterline(
            ContextBuildInput(
                conversation = conv,
                messages = _ui.value.messages,
                memories = _ui.value.userMemories,
                memoryBudget = budget,
                modelContextWindow = modelWin,
                novelChapters = _ui.value.novelChapters,
                novelLore = _ui.value.novelLore,
                memoryLedger = _ui.value.memoryLedger,
                memoryEpisodes = _ui.value.memoryEpisodes,
                draftText = _ui.value.draftText,
                draftImageCount = _ui.value.draftAttachments.size,
            ),
        )
        _ui.update { it.copy(waterline = wl) }
    }

    private fun parseMemoryBudget(raw: String): MemoryBudget =
        when (raw.lowercase()) {
            "low", "2k" -> MemoryBudget.low
            "high", "8k" -> MemoryBudget.high
            else -> MemoryBudget.standard
        }

    private fun toWireDto(msg: WireChatMessage): WireChatMessageDto {
        val parts = msg.parts
        return if (!parts.isNullOrEmpty()) {
            WireChatMessageDto.parts(
                role = msg.role,
                parts = parts.map { p ->
                    if (p.type == "image_url" && !p.imageUrl.isNullOrBlank()) {
                        WireChatContentPartDto(type = "image_url", image_url = WireImageUrlDto(p.imageUrl))
                    } else {
                        WireChatContentPartDto(type = "text", text = p.text.orEmpty())
                    }
                },
            )
        } else {
            WireChatMessageDto.text(msg.role, msg.textContent.orEmpty())
        }
    }
}
