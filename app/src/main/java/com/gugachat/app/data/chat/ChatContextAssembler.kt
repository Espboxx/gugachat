package com.gugachat.app.data.chat

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.ApiProviderStore
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.data.api.WireChatContentPartDto
import com.gugachat.app.data.api.WireChatMessageDto
import com.gugachat.app.data.api.WireImageUrlDto
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.AttachmentFileStore
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.domain.chat.BuiltChatContext
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ContextBuildInput
import com.gugachat.app.domain.chat.ContextBuilder
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.LoreTimedEntryState
import com.gugachat.app.domain.chat.MemoryBudget
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.TimelineReconciler
import com.gugachat.app.domain.chat.TokenEstimator
import com.gugachat.app.domain.chat.UserMemory
import com.gugachat.app.domain.chat.WireChatMessage
import com.gugachat.app.domain.chat.WorldbookEvalResult
import com.gugachat.app.domain.chat.WorldbookMountedBook
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.math.roundToInt

data class V4ConversationAssets(
    val characterCard: CharacterCardEntity? = null,
    val persona: PersonaEntity? = null,
    val promptPreset: PromptPresetEntity? = null,
    val mountedWorldbooks: List<WorldbookMountedBook> = emptyList(),
    val loreTimedStates: Map<String, LoreTimedEntryState> = emptyMap(),
)

data class SentPayloadTracking(
    val calibrationKey: String,
    val protocol: ApiProtocol,
    val estimatedRawTokens: Int,
    val hasUndecayedImages: Boolean,
)

data class AssembledChatPayload(
    val request: OnlineChatCompletionRequestDto,
    val builtContext: BuiltChatContext,
    val worldbookResult: WorldbookEvalResult?,
    val resolvedAssistantPrefill: String?,
    val tracking: SentPayloadTracking,
    val v4Assets: V4ConversationAssets,
)

/**
 * Loads conversation-scoped v0.4 assets (character card, persona, prompt preset, mounted + global
 * worldbooks, timed states, and semantic retrieval scores) directly from Room and executes
 * [ContextBuilder.buildChatContextV4] + optional rerank with zero dependency on UI state.
 *
 * Legacy conversations where card, persona, preset, and worldbooks are all absent/empty preserve
 * 100% legacy prompt assembly via [ContextBuilder].
 */
class ChatContextAssembler(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val apiProviderStore: ApiProviderStore,
    private val attachmentStore: AttachmentFileStore,
    private val retrievalCoordinator: RetrievalCoordinator,
) {
    private val chatDao = database.chatDao()
    private val userMemoryDao = database.userMemoryDao()
    private val assetDao = database.assetDao()

    suspend fun loadV4Assets(conv: ChatConversation): V4ConversationAssets {
        val characterCard = conv.characterCardId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            assetDao.getCharacterCardById(it)
        }
        val persona = conv.personaId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            assetDao.getPersonaById(it)
        }
        val promptPreset = conv.presetId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            assetDao.getPromptPresetById(it)
        }

        val mountedRefs = assetDao.getMountedWorldbookRefs(conv.id)
        val mountedOrderById = mountedRefs.filter { it.enabled }.associate { it.worldbookId to it.sortOrder }
        val mountedBooks = if (mountedOrderById.isNotEmpty()) {
            assetDao.getMountedWorldbooks(conv.id)
        } else {
            emptyList()
        }
        val globalBooks = assetDao.getGlobalEnabledWorldbooks()

        val orderedPairs = ArrayList<Pair<WorldbookEntity, Boolean>>(mountedBooks.size + globalBooks.size)
        val seenIds = HashSet<String>()
        for (wb in mountedBooks) {
            if (seenIds.add(wb.id)) {
                orderedPairs.add(wb to wb.globalEnabled)
            }
        }
        for (wb in globalBooks) {
            if (seenIds.add(wb.id)) {
                orderedPairs.add(wb to true)
            }
        }

        val mountedWorldbooks = if (orderedPairs.isNotEmpty()) {
            val allEntries = assetDao.getEnabledEntriesForWorldbooks(orderedPairs.map { it.first.id })
            val entriesByBook = allEntries.groupBy { it.worldbookId }
            val fallbackStartSort = (mountedOrderById.values.maxOrNull() ?: -1) + 1
            var globalIdx = 0
            orderedPairs.map { (wb, isGlobal) ->
                val sortOrder = mountedOrderById[wb.id] ?: (fallbackStartSort + globalIdx++)
                WorldbookMountedBook(
                    worldbook = wb,
                    entries = entriesByBook[wb.id].orEmpty(),
                    sortOrder = sortOrder,
                    isGlobal = isGlobal,
                )
            }
        } else {
            emptyList()
        }

        return V4ConversationAssets(
            characterCard = characterCard,
            persona = persona,
            promptPreset = promptPreset,
            mountedWorldbooks = mountedWorldbooks,
            loreTimedStates = parseLoreTimedStates(conv.loreTimedStatesJson),
        )
    }

    suspend fun assembleContext(
        conv: ChatConversation,
        msgs: List<ChatMessage>,
        groupId: Long,
        modelId: String,
        modelMetaOverride: OnlineChatModelDto? = null,
        episodesOverride: List<MemoryEpisode>? = null,
        assistantPrefill: String = "",
        draftText: String = "",
        mutedRecallKeys: Set<String> = emptySet(),
    ): AssembledChatPayload {
        val settings = settingsStore.currentSettings()
        val budget = parseMemoryBudget(settings.memoryBudget)
        val modelMeta = modelMetaOverride ?: DirectProtocolCodec.inferChatModelMeta(modelId)

        val allAttIds = msgs.asSequence().flatMap { it.attachmentIds.asSequence() }.distinct().toList()
        val attMap: Map<String, ChatAttachmentMeta> = if (allAttIds.isNotEmpty()) {
            attachmentStore.loadAttachmentsMap(allAttIds)
        } else {
            emptyMap()
        }

        val loreList = chatDao.getNovelLoreForConversation(conv.id).map { it.toDomain() }
        val ledgerList = chatDao.getMemoryLedgerForConversation(conv.id).map { it.toDomain() }
        val episodeList = episodesOverride ?: chatDao.getMemoryEpisodesForConversation(conv.id).map { it.toDomain() }
        val userMemList = userMemoryDao.getAll().map { it.toDomain() }
        val chapterList = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
        val v4Assets = loadV4Assets(conv)

        val queryText = buildSemanticQueryText(conv = conv, msgs = msgs, draftText = draftText)
        val candidateIds = collectSemanticCandidateIds(
            conv = conv,
            lore = loreList,
            ledger = ledgerList,
            episodes = episodeList,
            userMemories = userMemList,
            mountedWorldbooks = v4Assets.mountedWorldbooks,
        )
        val mountedWbIds = v4Assets.mountedWorldbooks.map { it.worldbook.id }.distinct()
        val (semanticScores, minCosineFloor) = retrievalCoordinator.prepareSendSemanticScores(
            conversationId = conv.id,
            queryText = queryText,
            candidateIds = candidateIds,
            mountedWorldbookIds = mountedWbIds,
        )

        val extCal = lookupExternalCalibration(settings, groupId, modelId)
        val effectiveCal = when {
            extCal != null && extCal in 0.4..2.5 -> extCal
            conv.tokenCalibration in 0.4..2.5 -> conv.tokenCalibration
            else -> 1.0
        }

        val baseInput = ContextBuildInput(
            conversation = conv,
            messages = msgs,
            memories = userMemList,
            memoryBudget = budget,
            modelContextWindow = modelMeta.context_window,
            novelChapters = chapterList,
            novelLore = loreList,
            memoryLedger = ledgerList,
            memoryEpisodes = episodeList,
            attachmentsById = attMap,
            resolveAttachmentDataUrl = { attId -> attachmentStore.resolveDataUrl(attId) },
            resolveAttachmentText = { attId -> attachmentStore.readExtractedText(attId) },
            precomputedSemanticScores = semanticScores.ifEmpty { null },
            mutedRecallKeys = mutedRecallKeys,
            modelId = modelId,
            externalCalibration = extCal,
            minSemanticCosine = minCosineFloor,
            characterCard = v4Assets.characterCard,
            persona = v4Assets.persona,
            promptPreset = v4Assets.promptPreset,
            mountedWorldbooks = v4Assets.mountedWorldbooks,
            loreTimedStates = v4Assets.loreTimedStates,
            assistantPrefill = assistantPrefill,
        )

        var builtV4 = ContextBuilder.buildChatContextV4(baseInput)

        if (retrievalCoordinator.isRerankEnabled(settings.retrievalSettings)) {
            val dynamicItems = builtV4.built.summary.recalledItems.filter {
                it.triggerKind != "constant" && it.triggerKind != "pinned_ledger"
            }
            if (dynamicItems.size > 2) {
                val loreById = loreList.associateBy { it.id }
                val wbEntryById = v4Assets.mountedWorldbooks
                    .asSequence()
                    .flatMap { it.entries.asSequence() }
                    .associateBy { it.id }
                val ledgerById = ledgerList.associateBy { it.id }
                val epById = episodeList.associateBy { it.id }
                val memById = userMemList.associateBy { it.id }
                val rerankPairs = dynamicItems.map { item ->
                    val key = "${item.source}:${item.id}"
                    val docText = when (item.source) {
                        "novel_lore" -> loreById[item.id]?.let { "${it.title}: ${it.content}" }
                        RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY -> wbEntryById[item.id]?.let { "${it.title}: ${it.content}" }
                        "rp_ledger" -> ledgerById[item.id]?.let { "${it.title}: ${it.content}" }
                        "episode_archived" -> epById[item.id]?.let { "${it.title}: ${it.summary}" }
                        "user_memory_archival" -> memById[item.id]?.content
                        else -> null
                    }?.takeIf { it.isNotBlank() } ?: "${item.title}: ${item.contentPreview}"
                    key to docText
                }
                val rerankScores = retrievalCoordinator.rerankCandidatesIfEnabled(queryText, rerankPairs)
                if (rerankScores.isNotEmpty()) {
                    val droppedNoiseKeys = HashSet<String>()
                    val adjustedScores = HashMap<String, Double>(semanticScores)
                    val floorBase = maxOf(minCosineFloor, 0.50)
                    for (item in dynamicItems) {
                        val key = "${item.source}:${item.id}"
                        val rScore = rerankScores[key] ?: continue
                        if (rScore < 0.15) {
                            droppedNoiseKeys.add(key)
                            adjustedScores.remove(item.id)
                        } else {
                            adjustedScores[item.id] = (floorBase + (1.0 - floorBase) * rScore).coerceIn(floorBase, 1.0)
                        }
                    }
                    val refinedV4 = ContextBuilder.buildChatContextV4(
                        baseInput.copy(
                            precomputedSemanticScores = adjustedScores.ifEmpty { null },
                            mutedRecallKeys = mutedRecallKeys + droppedNoiseKeys,
                        ),
                    )
                    builtV4 = refinedV4.copy(
                        built = refinedV4.built.copy(
                            summary = refinedV4.built.summary.copy(
                                retrievalModeUsed = RetrievalCoordinator.MODE_REMOTE_VECTOR_RERANK,
                                droppedRecallCount = refinedV4.built.summary.droppedRecallCount + droppedNoiseKeys.size,
                            ),
                        ),
                    )
                }
            }
        }

        val protocol = resolveProtocolForGroup(groupId)
        val rawResolvedPrefill = builtV4.resolvedAssistantPrefill?.takeIf { it.isNotBlank() }
        val guardedPrefill = if (rawResolvedPrefill != null) {
            val anthropicThinking = protocol == ApiProtocol.ANTHROPIC &&
                conv.params.enableReasoning &&
                !conv.params.reasoningEffort.isNullOrBlank()
            val officialOpenAi = protocol == ApiProtocol.OPENAI_CHAT &&
                DirectProtocolCodec.isOfficialOpenAiHost(apiProviderStore.findBySyntheticId(groupId)?.baseUrl.orEmpty())
            if (anthropicThinking || officialOpenAi || protocol == ApiProtocol.OPENAI_RESPONSES) null else rawResolvedPrefill
        } else {
            null
        }

        val built = builtV4.built
        val hasUndecayedImages = built.wireMessages.any { msg ->
            msg.parts?.any { it.type == "image_url" && !it.imageUrl.isNullOrBlank() } == true
        }
        val estimatedRawTokens = (built.summary.sentTokens.toDouble() / effectiveCal).roundToInt().coerceAtLeast(0)
        val tracking = SentPayloadTracking(
            calibrationKey = calibrationKey(groupId, modelId),
            protocol = protocol,
            estimatedRawTokens = estimatedRawTokens,
            hasUndecayedImages = hasUndecayedImages,
        )
        val request = OnlineChatCompletionRequestDto(
            group_id = groupId,
            model = modelId,
            messages = built.wireMessages.map(::toWireDto),
            stream = true,
            temperature = conv.params.temperature,
            top_p = conv.params.topP,
            max_tokens = conv.params.maxTokens,
            reasoning_effort = if (conv.params.enableReasoning) conv.params.reasoningEffort else null,
            frequency_penalty = conv.params.frequencyPenalty,
            presence_penalty = conv.params.presencePenalty,
            repetition_penalty = conv.params.repetitionPenalty,
            top_k = conv.params.topK,
            min_p = conv.params.minP,
            stop = conv.params.stopSequences.map { it.trim() }.filter { it.isNotEmpty() }.take(4).ifEmpty { null },
            enablePromptCaching = conv.params.enablePromptCaching,
            promptCacheKey = if (conv.params.enablePromptCaching) "gugachat_${conv.id.take(24)}" else null,
            assistantPrefill = guardedPrefill,
        )

        return AssembledChatPayload(
            request = request,
            builtContext = built,
            worldbookResult = builtV4.worldbookResult,
            resolvedAssistantPrefill = guardedPrefill,
            tracking = tracking,
            v4Assets = v4Assets,
        )
    }

    fun calibrationKey(groupId: Long?, modelId: String?): String {
        val providerKey = when {
            groupId == null -> "default"
            ApiProviderConfig.isCustomProviderId(groupId) ->
                apiProviderStore.findBySyntheticId(groupId)?.id ?: "custom_$groupId"
            else -> "server_$groupId"
        }
        val family = TokenEstimator.modelFamilyKey(modelId)
        return "$providerKey|$family"
    }

    fun lookupExternalCalibration(
        settings: AppSettings,
        groupId: Long?,
        modelId: String?,
    ): Double? {
        if (settings.tokenCalibrationMap.isEmpty() || modelId.isNullOrBlank()) return null
        val key = calibrationKey(groupId, modelId)
        return settings.tokenCalibrationMap[key]?.takeIf { it in 0.60..1.60 }
    }

    fun resolveProtocolForGroup(groupId: Long): ApiProtocol {
        if (ApiProviderConfig.isCustomProviderId(groupId)) {
            return apiProviderStore.findBySyntheticId(groupId)?.protocol ?: ApiProtocol.OPENAI_CHAT
        }
        return ApiProtocol.OPENAI_CHAT
    }

    fun parseMemoryBudget(raw: String): MemoryBudget =
        when (raw.trim().lowercase()) {
            "low", "2k" -> MemoryBudget.low
            "high", "8k" -> MemoryBudget.high
            else -> MemoryBudget.standard
        }

    fun collectSemanticCandidateIds(
        conv: ChatConversation,
        lore: List<NovelLoreEntry>,
        ledger: List<MemoryLedgerItem>,
        episodes: List<MemoryEpisode>,
        userMemories: List<UserMemory>,
        mountedWorldbooks: List<WorldbookMountedBook> = emptyList(),
    ): Set<String> {
        val ids = LinkedHashSet<String>()
        if (conv.mode == ConversationMode.novel || conv.mode == ConversationMode.rp) {
            for (entry in lore) {
                if (!entry.alwaysActive && entry.enabled && !entry.stale) {
                    ids.add(entry.id)
                }
            }
        }
        for (book in mountedWorldbooks) {
            for (entry in book.entries) {
                if (entry.enabled && !entry.constant) {
                    ids.add(entry.id)
                }
            }
        }
        if (conv.mode == ConversationMode.rp) {
            for (item in ledger) {
                if (item.enabled && !item.stale) {
                    ids.add(item.id)
                }
            }
        }
        for (ep in episodes) {
            if (!ep.stale) {
                ids.add(ep.id)
            }
        }
        if (conv.params.enableAutoMemory) {
            for (mem in userMemories) {
                if (mem.enabled && mem.content.isNotBlank()) {
                    ids.add(mem.id)
                }
            }
        }
        return ids
    }

    fun buildSemanticQueryText(
        conv: ChatConversation,
        msgs: List<ChatMessage>,
        draftText: String,
    ): String {
        val active = TimelineReconciler.activeMessages(msgs)
        val presentNames = when (conv.mode) {
            ConversationMode.novel -> conv.novel?.currentStatus?.present.orEmpty()
            ConversationMode.rp -> conv.rp?.cast?.filter { it.inScene }?.map { it.name }.orEmpty()
            ConversationMode.chat -> emptyList()
        }
        val recentTail = active.takeLast(3)
            .mapNotNull { m -> m.content.trim().takeIf { it.isNotEmpty() } }
            .joinToString("\n")
            .takeLast(500)
        return buildList {
            draftText.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            recentTail.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (presentNames.isNotEmpty()) add(presentNames.joinToString(" "))
        }.joinToString("\n").trim()
    }

    companion object {
        private val TIMED_STATES_SERIALIZER = MapSerializer(String.serializer(), LoreTimedEntryState.serializer())

        fun parseLoreTimedStates(rawJson: String?): Map<String, LoreTimedEntryState> {
            val clean = rawJson?.trim().orEmpty()
            if (clean.isEmpty() || clean == "{}") return emptyMap()
            return runCatching {
                AppJson.decodeFromString(TIMED_STATES_SERIALIZER, clean)
            }.getOrDefault(emptyMap())
        }

        fun encodeLoreTimedStates(states: Map<String, LoreTimedEntryState>): String {
            if (states.isEmpty()) return "{}"
            return runCatching {
                AppJson.encodeToString(TIMED_STATES_SERIALIZER, states)
            }.getOrDefault("{}")
        }

        fun toWireDto(msg: WireChatMessage): WireChatMessageDto {
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
}
