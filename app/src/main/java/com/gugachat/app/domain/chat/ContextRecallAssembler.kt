package com.gugachat.app.domain.chat

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class MemoizedMessageCost(
    val cleanedText: String,
    val baseTextTokens: Int,
)

internal data class ComputedPipeline(
    val active: List<ChatMessage>,
    val allActiveMessages: List<ChatMessage>,
    val uncompactedExcludedCount: Int,
    val activeChapter: NovelChapter?,
    val novelLore: List<NovelLoreEntry>,
    val windowLimit: Int,
    val budgetTokens: Int,
    val effectiveLimitTokens: Int,
    val calibration: Double,
    val effectiveModelId: String?,
    val isNovel: Boolean,
    val isRp: Boolean,
    val isV4Mode: Boolean,
    val loreMatch: LoreMatchResult,
    val worldbookResult: WorldbookEvalResult?,
    val parsedPresetBlocks: List<PromptPresetBlock>,
    val resolvedAssistantPrefill: String?,
    val prefix: SystemPrefixResult,
    val tailBundle: TailRecallBundle,
    val fileRecallBundle: FileRecallBundle = FileRecallBundle(),
    val fileRetentionByAttachmentId: Map<String, FileRetentionTier> = emptyMap(),
    val resolvedSemanticScores: Map<String, Double>? = null,
    val dynamicOverhead: Int,
    val waterline: ContextWaterlineSummary,
    val messageCacheById: Map<String, MemoizedMessageCost> = emptyMap(),
)

internal object ContextRecallAssembler {
    fun memoizedMessageCost(
        msg: ChatMessage,
        conversation: ChatConversation,
        modelId: String?,
        cache: MutableMap<String, MemoizedMessageCost>,
    ): MemoizedMessageCost = cache.getOrPut(msg.id) {
        val cleaned = ContextBudgetPolicy.cleanAssistantHistoryText(msg, conversation)
        val baseTextTokens = ContextBudgetPolicy.estimateTextTokens(cleaned, modelId)
        MemoizedMessageCost(cleanedText = cleaned, baseTextTokens = baseTextTokens)
    }

    fun resolveMemoizedMsgTokenCost(
        msg: ChatMessage,
        imageDecayed: Boolean,
        fileDecayed: Boolean,
        attachmentsById: Map<String, ChatAttachmentMeta>,
        perTurnFileBudget: Int,
        modelId: String?,
        conversation: ChatConversation,
        cache: Map<String, MemoizedMessageCost>,
        fileRetentionByAttachmentId: Map<String, FileRetentionTier> = emptyMap(),
    ): Int {
        val baseTextTokens = cache[msg.id]?.baseTextTokens ?: run {
            val cleaned = ContextBudgetPolicy.cleanAssistantHistoryText(msg, conversation)
            ContextBudgetPolicy.estimateTextTokens(cleaned, modelId)
        }
        val imageIds = if (msg.role == "user") {
            msg.attachmentIds.filter { id -> attachmentsById[id]?.isFile != true }
        } else {
            emptyList()
        }
        val imgCount = if (!imageDecayed) imageIds.size else 0
        val fileTok = ContextBudgetPolicy.resolveMsgFileTokens(
            msg = msg,
            fileDecayed = fileDecayed,
            attachmentsById = attachmentsById,
            perTurnFileBudget = perTurnFileBudget,
            fileRetentionByAttachmentId = fileRetentionByAttachmentId,
        )
        val base = baseTextTokens + max(0, imgCount) * 765 + 8 + fileTok
        return if (imageDecayed && imageIds.isNotEmpty()) base + 12 else base
    }
    private fun emptyScoreResult(): ScoreMemoryResult = ScoreMemoryResult(emptyList(), emptyList(), 0)

    private fun toInspectItem(
        s: ScoredMemoryCandidate,
        source: String,
        defaultTitle: String,
        categoryOverride: String? = null,
        titleOverride: String? = null,
        useSummaryPreview: Boolean = false,
    ): RecalledMemoryInspectItem {
        val rawBody = if (useSummaryPreview) {
            (s.item.summary ?: s.item.content).orEmpty()
        } else {
            s.item.content.orEmpty()
        }
        return RecalledMemoryInspectItem(
            id = s.item.id,
            title = titleOverride ?: s.item.title ?: defaultTitle,
            contentPreview = rawBody.take(80),
            category = categoryOverride ?: s.item.category ?: s.item.kind ?: "other",
            source = source,
            score = (s.score * 1000).roundToInt() / 1000.0,
            estimatedTokens = s.estimatedTokens,
            matchedTerms = s.matchedTerms,
            triggerKind = s.triggerKind,
        )
    }

    fun buildTailRecallBundle(
        conversation: ChatConversation,
        active: List<ChatMessage>,
        draftText: String,
        memoryBudget: MemoryBudget,
        coreMemoryTokens: Int,
        effectiveLimitTokens: Int,
        effectiveTipSeq: Int,
        archivalUserMemories: List<UserMemory>,
        memoryLedger: List<MemoryLedgerItem>,
        overflowEpisodes: List<MemoryEpisode>,
        activeChapter: NovelChapter?,
        semanticScorer: SemanticScorer? = null,
        semanticScores: Map<String, Double>? = null,
        modelId: String? = null,
        mutedRecallKeys: Set<String> = emptySet(),
        minSemanticCosine: Double = 0.50,
    ): TailRecallBundle {
        val isNovel = conversation.mode == ConversationMode.novel && conversation.novel != null
        val isRp = conversation.mode == ConversationMode.rp && conversation.rp != null

        val tailBudget = ContextBudgetPolicy.computeTailBudget(effectiveLimitTokens, memoryBudget)
        val topKCap = ContextBudgetPolicy.topKRecallCap(memoryBudget)
        val presentNames = when {
            isNovel -> conversation.novel?.currentStatus?.present.orEmpty()
            isRp -> conversation.rp?.cast?.filter { it.inScene }?.map { it.name }.orEmpty()
            else -> emptyList()
        }
        val querySeed = buildList {
            if (draftText.isNotBlank()) add(draftText)
            if (isNovel && !activeChapter?.outline.isNullOrBlank()) add(activeChapter!!.outline)
            if (isRp && !conversation.rp?.state?.scene.isNullOrBlank()) add(conversation.rp!!.state.scene)
        }.joinToString("\n")
        val query = CjkSearch.buildMemoryQuery(querySeed, active, presentNames)

        val effectiveArchivalMemories = if (conversation.params.enableAutoMemory) archivalUserMemories else emptyList()
        val mutedLedger = ContextBudgetPolicy.mutedIdsForSource(mutedRecallKeys, "rp_ledger")
        val mutedEpisodes = ContextBudgetPolicy.mutedIdsForSource(mutedRecallKeys, "episode_archived")
        val mutedUserMems = ContextBudgetPolicy.mutedIdsForSource(mutedRecallKeys, "user_memory_archival")

        fun scoreOptions(
            tipSeq: Int,
            maxTokens: Int,
            mutedIds: Set<String>,
        ) = ScoreMemoryOptions(
            activeTipSeq = tipSeq,
            maxTokens = maxTokens,
            semanticScores = semanticScores,
            semanticScorer = if (semanticScores == null) semanticScorer else null,
            minSemanticCosine = minSemanticCosine,
            maxResults = topKCap,
            modelId = modelId,
            mutedIds = mutedIds,
        )

        if (isRp) {
            val validLedger = memoryLedger.filter {
                it.id !in mutedLedger &&
                    TimelineReconciler.isItemValidAtTip(it.enabled, it.stale, it.validFromSeq, it.supersededAtSeq, effectiveTipSeq)
            }
            val activeLedger = validLedger
                .filter { it.pinned || it.locked || (!it.resolved && (it.kind == "promise" || it.kind == "quest")) }
                .take(6)
            val activeIds = activeLedger.map { it.id }.toSet()
            val restLedger = validLedger.filter { it.id !in activeIds }
            val ledgerCap = max(0, (tailBudget * 0.55).toInt())
            val recalledLedger = if (restLedger.isNotEmpty() && ledgerCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = restLedger.map { CjkSearch.ledgerItemToSearchCandidate(it) },
                    query = query,
                    options = scoreOptions(effectiveTipSeq, ledgerCap, mutedLedger),
                )
            } else {
                emptyScoreResult()
            }

            val epCap = max(0, tailBudget - recalledLedger.usedTokens)
            val recalledEps = if (overflowEpisodes.isNotEmpty() && epCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = overflowEpisodes.map { CjkSearch.episodeToSearchCandidate(it) },
                    query = query,
                    options = scoreOptions(effectiveTipSeq, epCap, mutedEpisodes),
                )
            } else {
                emptyScoreResult()
            }

            val activeLines = ArrayList<String>(activeLedger.size)
            val activeInspect = ArrayList<RecalledMemoryInspectItem>(activeLedger.size)
            var activeTok = 0
            for (item in activeLedger) {
                val line = "- [${item.kind}] ${item.title}: ${item.content}"
                val tok = ContextBudgetPolicy.estimateTextTokens(line, modelId)
                activeLines.add(line)
                activeTok += tok
                activeInspect.add(
                    RecalledMemoryInspectItem(
                        id = item.id,
                        title = item.title,
                        contentPreview = item.content.take(80),
                        category = item.kind,
                        source = "rp_ledger",
                        score = null,
                        estimatedTokens = tok,
                        matchedTerms = emptyList(),
                        triggerKind = "pinned_ledger",
                    ),
                )
            }

            val inspect = buildList {
                addAll(activeInspect)
                recalledLedger.selected.forEach { s ->
                    add(toInspectItem(s, source = "rp_ledger", defaultTitle = "RP 台账", categoryOverride = s.item.kind ?: "fact"))
                }
                recalledEps.selected.forEach { s ->
                    add(toInspectItem(s, source = "episode_archived", defaultTitle = "历史幕次", categoryOverride = "episode", useSummaryPreview = true))
                }
            }
            val recalledLines = recalledLedger.selected.map { "- [${it.item.kind ?: "fact"}] ${it.item.title.orEmpty()}: ${it.item.content.orEmpty()}" }
            val epLines = recalledEps.selected.map { "- [${it.item.title.orEmpty()}] ${(it.item.summary ?: it.item.content).orEmpty()}" }
            val totalTok = activeTok + recalledLedger.usedTokens + recalledEps.usedTokens
            val modeUsed = if (recalledLedger.retrievalModeUsed == "hybrid" || recalledEps.retrievalModeUsed == "hybrid") "hybrid" else "lexical"
            return TailRecallBundle(
                rpActiveLedgerLines = activeLines,
                rpRecalledMemoryLines = recalledLines,
                rpArchivedEpisodeLines = epLines,
                tailRecallTokens = totalTok,
                retrievedMemoryCount = inspect.size,
                recalledItems = inspect,
                recallBudgetTokens = tailBudget,
                droppedRecallCount = recalledLedger.droppedCount + recalledEps.droppedCount,
                retrievalModeUsed = modeUsed,
            )
        }

        if (isNovel) {
            val epCap = max(0, (tailBudget * 0.65).toInt())
            val epRes = if (overflowEpisodes.isNotEmpty() && epCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = overflowEpisodes.map { CjkSearch.episodeToSearchCandidate(it) },
                    query = query,
                    options = scoreOptions(effectiveTipSeq, epCap, mutedEpisodes),
                )
            } else {
                emptyScoreResult()
            }
            val epLines = epRes.selected.map { "- [${it.item.title ?: "前文片段"}] ${(it.item.summary ?: it.item.content).orEmpty()}" }
            val epInspect = epRes.selected.map { s ->
                toInspectItem(s, source = "episode_archived", defaultTitle = "前文片段", categoryOverride = "episode", useSummaryPreview = true)
            }

            val crossModeCap = max(0, min(tailBudget - epRes.usedTokens, ContextBudgetPolicy.totalUserMemoryLimit(memoryBudget) - coreMemoryTokens))
            val archRes = if (effectiveArchivalMemories.isNotEmpty() && crossModeCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = effectiveArchivalMemories.map { CjkSearch.chatMemoryToSearchCandidate(it) },
                    query = query,
                    options = scoreOptions(Int.MAX_VALUE, crossModeCap, mutedUserMems),
                )
            } else {
                emptyScoreResult()
            }

            val crossBlock = if (archRes.selected.isNotEmpty()) {
                val lines = archRes.selected.map { s -> "- [${s.item.category ?: "other"}] ${s.item.content.orEmpty()}" }
                "<recalled_user_memory>\n${lines.joinToString("\n")}\n</recalled_user_memory>"
            } else {
                ""
            }
            val crossInspect = archRes.selected.map { s ->
                toInspectItem(s, source = "user_memory_archival", defaultTitle = "用户偏好", titleOverride = "用户偏好", categoryOverride = s.item.category ?: "other")
            }
            val crossTok = if (crossBlock.isNotEmpty()) archRes.usedTokens + 16 else 0
            val modeUsed = if (epRes.retrievalModeUsed == "hybrid" || archRes.retrievalModeUsed == "hybrid") "hybrid" else "lexical"
            return TailRecallBundle(
                chatTailRecallBlock = crossBlock,
                chatRecallTokens = crossTok,
                novelArchivedEpisodeLines = epLines,
                tailRecallTokens = epRes.usedTokens + crossTok,
                retrievedMemoryCount = epInspect.size + crossInspect.size,
                recalledItems = epInspect + crossInspect,
                recallBudgetTokens = tailBudget,
                droppedRecallCount = epRes.droppedCount + archRes.droppedCount,
                retrievalModeUsed = modeUsed,
            )
        }

        // Standard chat mode
        val sections = ArrayList<String>()
        val inspected = ArrayList<RecalledMemoryInspectItem>()
        var chatTokens = 0
        var dropped = 0
        var usedHybrid = false

        if (effectiveArchivalMemories.isNotEmpty() && tailBudget > 0) {
            val epReserve = if (overflowEpisodes.isNotEmpty()) floor(tailBudget * 0.25).toInt() else 0
            val remMemLimit = max(0, ContextBudgetPolicy.totalUserMemoryLimit(memoryBudget) - coreMemoryTokens)
            val memCap = min(max(0, tailBudget - epReserve), remMemLimit)
            val archRes = if (memCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = effectiveArchivalMemories.map { CjkSearch.chatMemoryToSearchCandidate(it) },
                    query = query,
                    options = scoreOptions(Int.MAX_VALUE, memCap, mutedUserMems),
                )
            } else {
                emptyScoreResult()
            }
            dropped += archRes.droppedCount
            if (archRes.retrievalModeUsed == "hybrid") usedHybrid = true
            if (archRes.selected.isNotEmpty()) {
                val lines = archRes.selected.map { s ->
                    val catName = s.item.category ?: "other"
                    val cat = MemoryCategory.entries.firstOrNull { it.name == catName } ?: MemoryCategory.other
                    val tag = ContextZonePrefixBuilder.CATEGORY_TAG[cat] ?: "其他"
                    inspected.add(
                        toInspectItem(s, source = "user_memory_archival", defaultTitle = tag, titleOverride = tag, categoryOverride = catName),
                    )
                    "- [$tag] ${(s.item.content ?: "").trim()}"
                }
                sections.add("<recalled_user_memory>\n以下是与当前话题相关的用户历史项目与备忘（请按需参考）：\n${lines.joinToString("\n")}\n</recalled_user_memory>")
                chatTokens += archRes.usedTokens + 24
            }
        }

        val remForEps = max(0, tailBudget - chatTokens)
        if (overflowEpisodes.isNotEmpty() && remForEps > 0) {
            val epRes = CjkSearch.scoreMemoryCandidates(
                candidates = overflowEpisodes.map { CjkSearch.episodeToSearchCandidate(it) },
                query = query,
                options = scoreOptions(effectiveTipSeq, remForEps, mutedEpisodes),
            )
            dropped += epRes.droppedCount
            if (epRes.retrievalModeUsed == "hybrid") usedHybrid = true
            if (epRes.selected.isNotEmpty()) {
                val lines = epRes.selected.map { s ->
                    inspected.add(
                        toInspectItem(s, source = "episode_archived", defaultTitle = "历史片段", categoryOverride = "episode", useSummaryPreview = true),
                    )
                    "- [${s.item.title ?: "历史片段"}] ${(s.item.summary ?: s.item.content ?: "").trim()}"
                }
                sections.add("<archived_episodes>\n${lines.joinToString("\n")}\n</archived_episodes>")
                chatTokens += epRes.usedTokens + 16
            }
        }

        return TailRecallBundle(
            chatTailRecallBlock = sections.joinToString("\n\n"),
            chatRecallTokens = chatTokens,
            tailRecallTokens = chatTokens,
            retrievedMemoryCount = inspected.size,
            recalledItems = inspected,
            recallBudgetTokens = tailBudget,
            droppedRecallCount = dropped,
            retrievalModeUsed = if (usedHybrid) "hybrid" else "lexical",
        )
    }

    fun precomputeSinglePassSemanticScores(
        input: ContextBuildInput,
        slice: ActiveWindowSlice,
        isNovel: Boolean,
        isRp: Boolean,
    ): Map<String, Double>? {
        if (input.precomputedSemanticScores != null) return input.precomputedSemanticScores
        val scorer = input.semanticScorer ?: return null

        val conv = input.conversation
        val presentNames = when {
            isNovel -> conv.novel?.currentStatus?.present.orEmpty()
            isRp -> conv.rp?.cast?.filter { it.inScene }?.map { it.name }.orEmpty()
            else -> emptyList()
        }
        val seed = buildList {
            if (input.draftText.isNotBlank()) add(input.draftText)
            if (isNovel && !slice.activeChapter?.outline.isNullOrBlank()) add(slice.activeChapter!!.outline)
            if (isRp && !conv.rp?.state?.scene.isNullOrBlank()) add(conv.rp!!.state.scene)
        }.joinToString("\n")
        val budgetedQuery = CjkSearch.buildMemoryQuery(seed, slice.active, presentNames)
        if (budgetedQuery.rawQueryText.isBlank()) return emptyMap()

        val allCandidates = buildList {
            if (isNovel || isRp) {
                input.novelLore
                    .filter { !it.alwaysActive && it.enabled && !it.stale }
                    .forEach { add(CjkSearch.novelLoreToSearchCandidate(it)) }
            }
            for (book in input.mountedWorldbooks) {
                for (entry in book.entries) {
                    if (entry.enabled && !entry.constant && entry.content.isNotBlank()) {
                        add(
                            MemorySearchCandidate(
                                id = entry.id,
                                title = entry.title,
                                content = entry.content,
                                category = entry.category,
                                keywords = ContextZonePrefixBuilder.decodeStringList(entry.keysJson),
                                enabled = true,
                                estimatedTokens = entry.estimatedTokens.takeIf { it > 0 },
                            ),
                        )
                    }
                }
            }
            if (isRp) {
                input.memoryLedger
                    .filter { it.enabled && !it.stale }
                    .forEach { add(CjkSearch.ledgerItemToSearchCandidate(it)) }
            }
            input.memoryEpisodes
                .filter { !it.stale && it.toSeq <= slice.effectiveTipSeq }
                .forEach { add(CjkSearch.episodeToSearchCandidate(it)) }
            if (conv.params.enableAutoMemory) {
                input.memories
                    .filter { it.enabled && it.content.isNotBlank() }
                    .forEach { add(CjkSearch.chatMemoryToSearchCandidate(it)) }
            }
            for ((attId, doc) in input.preloadedFileChunks) {
                for (ch in doc.chunks) {
                    add(
                        MemorySearchCandidate(
                            id = FileContextBudgeter.chunkOwnerId(attId, ch.index),
                            title = "${doc.fileName} · ${ch.label}",
                            content = ch.text,
                            estimatedTokens = ch.estimatedTokens,
                            enabled = true,
                        ),
                    )
                }
            }
        }
        if (allCandidates.isEmpty()) return emptyMap()
        return scorer.score(budgetedQuery.rawQueryText, allCandidates)
    }

    fun runSharedPipeline(input: ContextBuildInput, allowScorer: Boolean): ComputedPipeline {
        val conv = input.conversation
        val requestedWindow = conv.params.contextWindowOverride?.takeIf { it >= 4096 } ?: input.modelContextWindow
        val windowLimit = max(4096, requestedWindow)
        val effectiveModelId = input.modelId?.takeIf { it.isNotBlank() }
            ?: conv.model?.takeIf { it.isNotBlank() }
        val budgetTokens = max(2048, (windowLimit * 0.80).toInt())
        val maxReserve = max(512, min(32768, budgetTokens / 2))
        val minLenFloor = OutputLengthPolicy.computeRequiredMaxTokensFloor(
            params = conv.params,
            mode = conv.mode,
            modelId = effectiveModelId,
            windowLimit = windowLimit,
        )
        val outputReserve = maxOf(conv.params.maxTokens ?: 2048, minLenFloor ?: 0).coerceIn(512, maxReserve)
        val effectiveLimitTokens = max(1024, budgetTokens - outputReserve)
        val slice = ContextBudgetPolicy.sliceActiveWindow(
            conversation = conv,
            messages = input.messages,
            novelChapters = input.novelChapters,
            attachmentsById = input.attachmentsById,
            effectiveLimitTokens = effectiveLimitTokens,
        )
        val calibration = when {
            input.externalCalibration != null && input.externalCalibration in 0.4..2.5 -> input.externalCalibration
            conv.tokenCalibration in 0.4..2.5 -> conv.tokenCalibration
            else -> 1.0
        }

        val isNovel = conv.mode == ConversationMode.novel && conv.novel != null
        val isRp = conv.mode == ConversationMode.rp && conv.rp != null
        val isV4Mode = ContextZonePrefixBuilder.isV4AssetMode(input)
        val parsedPresetBlocks = if (isV4Mode) ContextZonePrefixBuilder.parsePresetBlocks(input.promptPreset) else emptyList()

        val resolvedSemanticScores: Map<String, Double>? = when {
            input.precomputedSemanticScores != null -> input.precomputedSemanticScores
            allowScorer && input.semanticScorer != null -> precomputeSinglePassSemanticScores(input, slice, isNovel, isRp)
            else -> null
        }

        val mutedLoreIds = ContextBudgetPolicy.mutedIdsForSource(input.mutedRecallKeys, "novel_lore") +
            ContextBudgetPolicy.mutedIdsForSource(input.mutedRecallKeys, "worldbook_entry")
        val topKCap = ContextBudgetPolicy.topKRecallCap(input.memoryBudget)

        val worldbookResult: WorldbookEvalResult?
        val loreMatch: LoreMatchResult
        val prefix: SystemPrefixResult

        if (isV4Mode) {
            val loreBudget = when {
                isNovel -> conv.novel!!.loreTokenBudget
                isRp -> conv.rp!!.loreTokenBudget
                else -> input.mountedWorldbooks.firstOrNull()?.worldbook?.tokenBudget ?: 1600
            }
            val defaultScan = input.mountedWorldbooks.firstOrNull()?.worldbook?.scanDepth ?: 4
            val recursiveScan = input.mountedWorldbooks.all { it.worldbook.recursiveScanning }
            val extraScans = buildList {
                if (isNovel && !slice.activeChapter?.outline.isNullOrBlank()) add(slice.activeChapter!!.outline)
                if (isRp && !conv.rp?.state?.scene.isNullOrBlank()) add(conv.rp!!.state.scene)
                if (isRp && !conv.rp?.state?.chapterTitle.isNullOrBlank()) add(conv.rp!!.state.chapterTitle)
            }
            val evaluated = input.worldbookEvalOverride ?: WorldbookEngine.evaluate(
                conversationId = conv.id,
                books = input.mountedWorldbooks,
                sessionLore = if (isNovel || isRp) input.novelLore else emptyList(),
                recentMessages = slice.active,
                draftText = input.draftText,
                extraScanTexts = extraScans,
                timedStates = input.loreTimedStates,
                totalBudget = loreBudget,
                defaultScanDepth = defaultScan,
                recursiveScanning = recursiveScan,
                activeTipSeq = slice.effectiveTipSeq,
                mutedIds = mutedLoreIds,
                semanticScores = resolvedSemanticScores,
                minSemanticCosine = input.minSemanticCosine,
                modelId = effectiveModelId,
            )
            worldbookResult = evaluated
            loreMatch = LoreMatchResult(emptyList(), emptyList(), evaluated.constantTokens, evaluated.triggeredTokens)
            prefix = ContextZonePrefixBuilder.buildV4Zone0SystemPrefix(
                input = input,
                episodes = input.memoryEpisodes,
                novelChapters = input.novelChapters,
                worldbookResult = evaluated,
                presetBlocks = parsedPresetBlocks,
                effectiveTipSeq = slice.effectiveTipSeq,
                effectiveLimitTokens = effectiveLimitTokens,
                modelId = effectiveModelId,
            )
        } else {
            worldbookResult = null
            loreMatch = when {
                isNovel -> NovelEngine.matchNovelLore(
                    lore = input.novelLore,
                    recentMessages = slice.active,
                    draftText = input.draftText,
                    chapterOutline = slice.activeChapter?.outline.orEmpty(),
                    loreTokenBudget = conv.novel!!.loreTokenBudget,
                    activeTipSeq = slice.effectiveTipSeq,
                    presentEntities = conv.novel.currentStatus?.present.orEmpty(),
                    sceneTitle = conv.novel.currentStatus?.location,
                    semanticScorer = null,
                    semanticScores = resolvedSemanticScores,
                    modelId = effectiveModelId,
                    mutedIds = mutedLoreIds,
                    maxTriggered = topKCap,
                    minSemanticCosine = input.minSemanticCosine,
                )
                isRp -> {
                    val rpCfg = conv.rp!!
                    NovelEngine.matchNovelLore(
                        lore = input.novelLore,
                        recentMessages = slice.active,
                        draftText = input.draftText,
                        chapterOutline = rpCfg.state.chapterTitle,
                        loreTokenBudget = rpCfg.loreTokenBudget,
                        activeTipSeq = slice.effectiveTipSeq,
                        presentEntities = rpCfg.cast.filter { it.inScene }.map { it.name },
                        sceneTitle = rpCfg.state.scene,
                        semanticScorer = null,
                        semanticScores = resolvedSemanticScores,
                        modelId = effectiveModelId,
                        mutedIds = mutedLoreIds,
                        maxTriggered = topKCap,
                        minSemanticCosine = input.minSemanticCosine,
                    )
                }
                else -> LoreMatchResult(emptyList(), emptyList(), 0, 0)
            }
            prefix = ContextZonePrefixBuilder.buildSystemPrefix(
                conversation = conv,
                memories = input.memories,
                memoryBudget = input.memoryBudget,
                novelChapters = input.novelChapters,
                constantLore = loreMatch.constant,
                episodes = input.memoryEpisodes,
                effectiveTipSeq = slice.effectiveTipSeq,
                effectiveLimitTokens = effectiveLimitTokens,
                modelId = effectiveModelId,
            )
        }

        val tailBundle = buildTailRecallBundle(
            conversation = conv,
            active = slice.active,
            draftText = input.draftText,
            memoryBudget = input.memoryBudget,
            coreMemoryTokens = prefix.memoryTokens,
            effectiveLimitTokens = effectiveLimitTokens,
            effectiveTipSeq = slice.effectiveTipSeq,
            archivalUserMemories = prefix.archivalUserMemories,
            memoryLedger = input.memoryLedger,
            overflowEpisodes = prefix.overflowEpisodes,
            activeChapter = slice.activeChapter,
            semanticScorer = null,
            semanticScores = resolvedSemanticScores,
            modelId = effectiveModelId,
            mutedRecallKeys = input.mutedRecallKeys,
            minSemanticCosine = input.minSemanticCosine,
        )

        val v4ExtraDynamicTokens = if (isV4Mode) {
            val wbDynamic = (worldbookResult?.depthTokens ?: 0) + (worldbookResult?.triggeredTokens ?: 0)
            val presetDepthTok = parsedPresetBlocks
                .filter {
                    it.enabled && !it.marker && it.content.isNotBlank() &&
                        ContextZonePrefixBuilder.isDepthInjectionPosition(it.injectionPosition)
                }
                .sumOf { ContextBudgetPolicy.estimateTextTokens(it.content, effectiveModelId) + 6 }
            val cardDepthTok = input.characterCard?.depthPromptText?.takeIf { it.isNotBlank() }?.let {
                ContextBudgetPolicy.estimateTextTokens(it, effectiveModelId) + 6
            } ?: 0
            val cardPostTok = input.characterCard?.postHistoryInstructions?.takeIf {
                it.isNotBlank() && (!isRp || conv.rp?.postHistoryInstructions.isNullOrBlank())
            }?.let { ContextBudgetPolicy.estimateTextTokens(it, effectiveModelId) + 8 } ?: 0
            val personaDepthTok = input.persona?.takeIf {
                it.description.isNotBlank() && ContextZonePrefixBuilder.isDepthInjectionPosition(it.injectionPosition)
            }?.let { ContextBudgetPolicy.estimateTextTokens(it.description, effectiveModelId) + 6 } ?: 0
            wbDynamic + presetDepthTok + cardDepthTok + cardPostTok + personaDepthTok
        } else {
            0
        }

        val lastUserTextForLength = if (input.draftText.isNotBlank()) {
            input.draftText
        } else {
            slice.active.lastOrNull { it.role == "user" }?.content.orEmpty()
        }
        val lengthDirectiveText = OutputLengthPolicy.buildLengthDirective(
            mode = conv.mode,
            minOutputChars = conv.params.minOutputChars,
            novelConfig = conv.novel,
            activeChapter = slice.activeChapter,
            isEnglish = OutputLengthPolicy.isEnglishPreference(lastUserTextForLength),
        )
        val lengthDirectiveTokens = if (lengthDirectiveText.isNotBlank()) {
            TokenEstimator.estimateText(lengthDirectiveText, effectiveModelId)
        } else {
            0
        }

        val dynamicOverhead = lengthDirectiveTokens + when {
            isNovel -> {
                val novelCfg = conv.novel!!
                (if (isV4Mode) v4ExtraDynamicTokens else loreMatch.triggeredTokens) +
                    tailBundle.tailRecallTokens +
                    ContextBudgetPolicy.estimateTextTokens(novelCfg.authorNote, effectiveModelId) +
                    (if (novelCfg.enableStatusHud) 90 else 0) +
                    (if (novelCfg.autoSyncLore) 110 else 0) +
                    (if (novelCfg.autoAdvanceChapter) 85 else 0) +
                    (if (novelCfg.enableChoices && novelCfg.playStyle != "author") 90 else 0)
            }
            isRp -> {
                val rpCfg = conv.rp!!
                val hudOverhead = 160 + min(
                    360,
                    rpCfg.state.inventory.size * 12 +
                        rpCfg.state.quests.size * 16 +
                        rpCfg.state.flags.size * 10 +
                        rpCfg.state.stats.size * 8 +
                        rpCfg.cast.size * 20,
                )
                val suffixOverhead = (if (rpCfg.enableStateHud) 160 else 0) +
                    (if (rpCfg.enableLedger) 65 else 0) +
                    (if (rpCfg.enableChoices) 120 else 0)
                hudOverhead + suffixOverhead +
                    (if (isV4Mode) v4ExtraDynamicTokens else loreMatch.triggeredTokens) +
                    ContextBudgetPolicy.estimateTextTokens(rpCfg.authorNote, effectiveModelId) +
                    ContextBudgetPolicy.estimateTextTokens(rpCfg.postHistoryInstructions, effectiveModelId) +
                    tailBundle.tailRecallTokens
            }
            else -> v4ExtraDynamicTokens + tailBundle.tailRecallTokens
        }

        val resolvedPrefill = run {
            val presetPrefill = if (isV4Mode) {
                parsedPresetBlocks
                    .lastOrNull {
                        it.enabled && !it.marker &&
                            it.role.equals("assistant", ignoreCase = true) &&
                            ContextZonePrefixBuilder.isDepthInjectionPosition(it.injectionPosition) &&
                            it.injectionDepth == 0 &&
                            it.content.isNotBlank()
                    }
                    ?.content
                    ?.trim()
                    .orEmpty()
            } else {
                ""
            }
            input.assistantPrefill.trim()
                .ifBlank { presetPrefill }
                .takeIf { it.isNotEmpty() }
                ?.let { ContextZonePrefixBuilder.expandDynamicV4Macros(it, conv, input.characterCard, input.persona) }
        }

        val effectiveDraftImages = if (input.draftAttachments.isNotEmpty()) {
            input.draftAttachments.count { it.isImage }
        } else {
            input.draftImageCount
        }
        val effectiveDraftFiles = input.draftAttachments.filter { it.isFile }
        val baseDynamicOverhead = dynamicOverhead
        val fileTurnBudget = FileContextBudgeter.computeFileTurnBudget(
            effectiveLimitTokens = effectiveLimitTokens,
            reservedFixedTokens = prefix.systemTokens + baseDynamicOverhead,
        )

        val messageCacheById = HashMap<String, MemoizedMessageCost>(slice.allActiveMessages.size * 2)
        for (msg in slice.allActiveMessages) {
            memoizedMessageCost(msg, conv, effectiveModelId, messageCacheById)
        }

        val retentionByAttId = slice.fileRetentionByAttachmentId.toMutableMap()
        val fullInlineThreshold = FileContextBudgeter.computeFullInlineThreshold(effectiveLimitTokens)
        val hardFullCap = min(
            FileContextBudgeter.HARD_MAX_FILE_TURN_TOKENS,
            (max(4096, effectiveLimitTokens) * FileContextBudgeter.MAX_FILE_WINDOW_SHARE).toInt(),
        )
        for (df in effectiveDraftFiles) {
            retentionByAttId[df.id] = when (df.injectMode) {
                FileInjectMode.retrieve, FileInjectMode.head_tail -> FileRetentionTier.INDEX_RECALL
                FileInjectMode.full -> if (df.estimatedTokens <= hardFullCap) FileRetentionTier.FULL else FileRetentionTier.INDEX_RECALL
                FileInjectMode.auto -> if (df.estimatedTokens <= fullInlineThreshold) FileRetentionTier.FULL else FileRetentionTier.INDEX_RECALL
            }
        }

        val draftFileTokens = FileContextBudgeter.estimateDraftFilesInjectedTokens(
            files = effectiveDraftFiles,
            effectiveLimitTokens = effectiveLimitTokens,
            reservedFixedTokens = prefix.systemTokens + baseDynamicOverhead,
        )
        val draftTokens = if (input.draftText.isNotBlank() || effectiveDraftImages > 0 || effectiveDraftFiles.isNotEmpty()) {
            ContextBudgetPolicy.estimateMessageTokens(input.draftText, effectiveDraftImages, effectiveModelId) + draftFileTokens
        } else {
            0
        }

        val maxHistoryLimit = conv.params.maxHistoryMessages?.takeIf { it > 0 }
        val pressureWindow = if (maxHistoryLimit != null && slice.active.size > maxHistoryLimit) {
            slice.active.takeLast(maxHistoryLimit)
        } else {
            slice.active
        }

        fun computeActiveCost(msg: ChatMessage): Int = resolveMemoizedMsgTokenCost(
            msg = msg,
            imageDecayed = msg.id in slice.decayedImageMessageIds,
            fileDecayed = false,
            attachmentsById = input.attachmentsById,
            perTurnFileBudget = fileTurnBudget,
            modelId = effectiveModelId,
            conversation = conv,
            cache = messageCacheById,
            fileRetentionByAttachmentId = retentionByAttId,
        )

        // Under context pressure, demote oldest `auto` Tier-1 files to Tier-2 INDEX_RECALL before sliding messages
        if (pressureWindow.isNotEmpty()) {
            val pressureCosts = IntArray(pressureWindow.size) { computeActiveCost(pressureWindow[it]) }
            var totalPressureRaw = prefix.systemTokens + draftTokens + baseDynamicOverhead + pressureCosts.sum()
            if ((totalPressureRaw * calibration).roundToInt() > effectiveLimitTokens) {
                for (i in pressureWindow.indices) {
                    val msg = pressureWindow[i]
                    if (msg.role != "user" || msg.attachmentIds.isEmpty()) continue
                    var demotedThisMsg = false
                    for (attId in msg.attachmentIds) {
                        val meta = input.attachmentsById[attId]?.takeIf { it.isFile } ?: continue
                        if (meta.injectMode == FileInjectMode.auto && retentionByAttId[attId] == FileRetentionTier.FULL) {
                            retentionByAttId[attId] = FileRetentionTier.INDEX_RECALL
                            demotedThisMsg = true
                        }
                    }
                    if (demotedThisMsg) {
                        val updated = computeActiveCost(msg)
                        totalPressureRaw += updated - pressureCosts[i]
                        pressureCosts[i] = updated
                        if ((totalPressureRaw * calibration).roundToInt() <= effectiveLimitTokens) break
                    }
                }
            }
        }

        val msgCosts = slice.active.map { computeActiveCost(it) }
        val msgFileCosts = slice.active.map { msg ->
            ContextBudgetPolicy.resolveMsgFileTokens(
                msg = msg,
                fileDecayed = false,
                attachmentsById = input.attachmentsById,
                perTurnFileBudget = fileTurnBudget,
                fileRetentionByAttachmentId = retentionByAttId,
            )
        }

        // First sliding pass to identify any slid-out file upload turns so they stay recallable in Tier 2
        var preRunning = ((prefix.systemTokens + draftTokens + baseDynamicOverhead) * calibration).roundToInt()
        var preRawKept = 0
        for (i in slice.active.indices.reversed()) {
            if (maxHistoryLimit != null && preRawKept >= maxHistoryLimit) break
            val msgTok = (msgCosts[i] * calibration).roundToInt()
            if (preRawKept > 0 && preRunning + msgTok > effectiveLimitTokens) break
            preRunning += msgTok
            preRawKept++
        }
        val preKept = ContextBudgetPolicy.computeSlidingWindowKeepCount(
            activeSize = slice.active.size,
            rawKeptCount = preRawKept,
            maxHistoryLimit = maxHistoryLimit,
        )
        val preStartIdx = max(0, slice.active.size - preKept)
        for (i in 0 until preStartIdx) {
            val slidMsg = slice.active[i]
            if (slidMsg.role != "user") continue
            for (attId in slidMsg.attachmentIds) {
                if (input.attachmentsById[attId]?.isFile == true) {
                    retentionByAttId[attId] = FileRetentionTier.INDEX_RECALL
                }
            }
        }

        // Discover all Tier-2 files across allActiveMessages (including compacted/slid-out turns, excluding soft-deleted)
        val allFileMetasById = LinkedHashMap<String, ChatAttachmentMeta>(input.attachmentsById)
        for (df in effectiveDraftFiles) {
            allFileMetasById[df.id] = df
        }
        val orderedTier2Ids = LinkedHashSet<String>()
        for (msg in slice.allActiveMessages) {
            if (msg.role != "user" || msg.attachmentIds.isEmpty()) continue
            for (attId in msg.attachmentIds) {
                if (allFileMetasById[attId]?.isFile == true && retentionByAttId[attId] == FileRetentionTier.INDEX_RECALL) {
                    orderedTier2Ids.add(attId)
                }
            }
        }
        for (df in effectiveDraftFiles) {
            if (retentionByAttId[df.id] == FileRetentionTier.INDEX_RECALL) {
                orderedTier2Ids.add(df.id)
            }
        }

        val lastActiveUserMsg = slice.active.lastOrNull { it.role == "user" }
        val latestFileQuery = if (input.draftText.isNotBlank()) input.draftText else lastActiveUserMsg?.content.orEmpty()
        val uploadTurnFileIds = if (effectiveDraftFiles.isNotEmpty()) {
            effectiveDraftFiles.map { it.id }.toSet()
        } else {
            lastActiveUserMsg?.attachmentIds
                ?.filter { allFileMetasById[it]?.isFile == true }
                ?.toSet()
                .orEmpty()
        }
        val recentForFileRecall = if (input.draftText.isNotBlank() || lastActiveUserMsg == null) {
            slice.active
        } else {
            slice.active.filter { it.id != lastActiveUserMsg.id }
        }
        val tier2Docs = orderedTier2Ids.mapNotNull { input.preloadedFileChunks[it] }
        val remainingAfterSystem = max(0, effectiveLimitTokens - prefix.systemTokens - baseDynamicOverhead)
        val fileRecallBudget = if (orderedTier2Ids.isNotEmpty()) {
            FileContextBudgeter.computeFileRecallBudget(
                remainingAfterSystem = remainingAfterSystem,
                memoryBudget = input.memoryBudget,
                isUploadTurn = uploadTurnFileIds.any { it in orderedTier2Ids },
            )
        } else {
            0
        }
        val fileRecallBundle = if (tier2Docs.isNotEmpty() && fileRecallBudget > 0) {
            FileRecallEngine.recall(
                tier2Docs = tier2Docs,
                fileMetasById = allFileMetasById,
                uploadTurnFileIds = uploadTurnFileIds,
                conversation = conv,
                recentMessages = recentForFileRecall,
                latestUserQuery = latestFileQuery,
                activeChapter = slice.activeChapter,
                budgetTokens = fileRecallBudget,
                semanticScores = resolvedSemanticScores,
                semanticScorer = null,
                minSemanticCosine = input.minSemanticCosine,
                mutedRecallKeys = input.mutedRecallKeys,
                modelId = effectiveModelId,
            )
        } else {
            FileRecallBundle(budgetTokens = fileRecallBudget, tier2FileIds = orderedTier2Ids)
        }

        val totalDynamicOverhead = baseDynamicOverhead + fileRecallBundle.recalledTokens

        var running = ((prefix.systemTokens + draftTokens + totalDynamicOverhead) * calibration).roundToInt()
        var rawKept = 0
        for (i in slice.active.indices.reversed()) {
            if (maxHistoryLimit != null && rawKept >= maxHistoryLimit) break
            val msgTok = (msgCosts[i] * calibration).roundToInt()
            if (rawKept > 0 && running + msgTok > effectiveLimitTokens) break
            running += msgTok
            rawKept++
        }

        val kept = ContextBudgetPolicy.computeSlidingWindowKeepCount(
            activeSize = slice.active.size,
            rawKeptCount = rawKept,
            maxHistoryLimit = maxHistoryLimit,
        )
        val startIdx = max(0, slice.active.size - kept)
        var keptHistoryRaw = 0
        var keptFileTokensRaw = 0
        for (i in startIdx until slice.active.size) {
            keptHistoryRaw += msgCosts[i]
            keptFileTokensRaw += msgFileCosts[i]
        }

        val effectiveDecayedFiles = slice.active
            .filter { msg ->
                msg.role == "user" && msg.attachmentIds.any { id ->
                    allFileMetasById[id]?.isFile == true && retentionByAttId[id] == FileRetentionTier.INDEX_RECALL
                }
            }
            .map { it.id }
            .toSet()

        val sentRaw = prefix.systemTokens + draftTokens + totalDynamicOverhead + keptHistoryRaw
        val sentTokens = (sentRaw * calibration).roundToInt()
        val sentPct = min(100, ((sentTokens.toDouble() / max(1, effectiveLimitTokens)) * 100.0).roundToInt())

        val allArchiveHistoryRaw = slice.allActiveMessages.sumOf { msg ->
            resolveMemoizedMsgTokenCost(
                msg = msg,
                imageDecayed = msg.id in slice.decayedImageMessageIds,
                fileDecayed = msg.id in effectiveDecayedFiles,
                attachmentsById = input.attachmentsById,
                perTurnFileBudget = fileTurnBudget,
                modelId = effectiveModelId,
                conversation = conv,
                cache = messageCacheById,
                fileRetentionByAttachmentId = retentionByAttId,
            )
        }
        val archiveTotalTokens = ((prefix.systemTokens + draftTokens + totalDynamicOverhead + allArchiveHistoryRaw) * calibration).roundToInt()

        val trimmedCount = slice.uncompactedExcludedCount + max(0, slice.active.size - kept)
        val isSliding = trimmedCount > 0
        val hardOverflow = sentTokens > windowLimit

        val loreInspect = if (worldbookResult != null) {
            worldbookResult.inspectItems
        } else {
            val constantLoreInspect = loreMatch.constant.map { entry ->
                RecalledMemoryInspectItem(
                    id = entry.id,
                    title = entry.title,
                    contentPreview = entry.content.take(80),
                    category = entry.category,
                    source = "novel_lore",
                    score = null,
                    estimatedTokens = NovelEngine.estimateLoreTokens(entry, effectiveModelId),
                    matchedTerms = emptyList(),
                    triggerKind = "constant",
                )
            }
            val triggeredLoreInspect = loreMatch.triggered.map { entry ->
                RecalledMemoryInspectItem(
                    id = entry.id,
                    title = entry.title,
                    contentPreview = entry.content.take(80),
                    category = entry.category,
                    source = "novel_lore",
                    score = loreMatch.triggeredScores[entry.id]?.let { (it * 1000).roundToInt() / 1000.0 },
                    estimatedTokens = NovelEngine.estimateLoreTokens(entry, effectiveModelId),
                    matchedTerms = loreMatch.triggeredMatchedTerms[entry.id].orEmpty(),
                    triggerKind = loreMatch.triggeredKinds[entry.id] ?: "lexical",
                )
            }
            constantLoreInspect + triggeredLoreInspect
        }
        val allRecalled = loreInspect + tailBundle.recalledItems + fileRecallBundle.recalledItems
        val recalledTotalTokens = allRecalled.sumOf { it.estimatedTokens }

        val baseRecallBudgetTokens = when {
            isNovel -> conv.novel!!.loreTokenBudget + tailBundle.recallBudgetTokens
            isRp -> conv.rp!!.loreTokenBudget + tailBundle.recallBudgetTokens
            worldbookResult != null -> (input.mountedWorldbooks.firstOrNull()?.worldbook?.tokenBudget ?: 1600) + tailBundle.recallBudgetTokens
            else -> tailBundle.recallBudgetTokens
        }
        val recallBudgetTokens = baseRecallBudgetTokens + if (tier2Docs.isNotEmpty()) fileRecallBundle.budgetTokens else 0
        val loreHybrid = worldbookResult?.retrievalModeUsed == "hybrid" || loreMatch.retrievalModeUsed == "hybrid"
        val retrievalModeUsed = if (
            loreHybrid ||
            tailBundle.retrievalModeUsed == "hybrid" ||
            fileRecallBundle.retrievalModeUsed == "hybrid"
        ) {
            "hybrid"
        } else {
            "lexical"
        }

        fun cal(raw: Int): Int = (raw * calibration).roundToInt()

        val triggeredIds = worldbookResult?.allTriggeredIds ?: loreMatch.triggered.map { it.id }
        val triggeredTitles = worldbookResult?.allTriggeredTitles ?: loreMatch.triggered.map { it.title }
        val totalLoreTokensRaw = if (worldbookResult != null) {
            prefix.novelLoreTokens + worldbookResult.depthTokens + worldbookResult.triggeredTokens
        } else {
            prefix.novelLoreTokens + loreMatch.triggeredTokens
        }
        val droppedLoreCount = worldbookResult?.droppedCount ?: loreMatch.droppedCount

        val waterline = ContextWaterlineSummary(
            usedTokens = sentTokens,
            budgetTokens = budgetTokens,
            effectiveLimitTokens = effectiveLimitTokens,
            modelContextWindow = windowLimit,
            percentage = sentPct,
            exceedsModelWindow = hardOverflow,
            activeMessageCount = slice.allActiveMessages.size,
            trimmedMessageCount = trimmedCount,
            decayedImageMessageIds = slice.decayedImageMessageIds,
            decayedFileMessageIds = effectiveDecayedFiles,
            fileTokens = cal(keptFileTokensRaw + draftFileTokens + fileRecallBundle.recalledTokens),
            memoryTokens = cal(prefix.memoryTokens),
            summaryTokens = cal(prefix.summaryTokens),
            triggeredLoreIds = triggeredIds,
            triggeredLoreTitles = triggeredTitles,
            novelLoreTokens = cal(totalLoreTokensRaw),
            retrievedMemoryCount = allRecalled.size,
            recalledMemoryTokens = cal(tailBundle.tailRecallTokens + fileRecallBundle.recalledTokens),
            recalledItems = allRecalled,
            sentTokens = sentTokens,
            sentPercentage = sentPct,
            archiveTotalTokens = archiveTotalTokens,
            isSliding = isSliding,
            hardOverflow = hardOverflow,
            uncompactedOverflowCount = trimmedCount,
            baseSystemTokens = cal(prefix.baseSystemTokens),
            recallBudgetTokens = recallBudgetTokens,
            recalledTotalTokens = recalledTotalTokens,
            retrievalModeUsed = retrievalModeUsed,
            droppedRecallCount = droppedLoreCount + tailBundle.droppedRecallCount + fileRecallBundle.droppedCount,
        )

        return ComputedPipeline(
            active = slice.active,
            allActiveMessages = slice.allActiveMessages,
            uncompactedExcludedCount = slice.uncompactedExcludedCount,
            activeChapter = slice.activeChapter,
            novelLore = input.novelLore,
            windowLimit = windowLimit,
            budgetTokens = budgetTokens,
            effectiveLimitTokens = effectiveLimitTokens,
            calibration = calibration,
            effectiveModelId = effectiveModelId,
            isNovel = isNovel,
            isRp = isRp,
            isV4Mode = isV4Mode,
            loreMatch = loreMatch,
            worldbookResult = worldbookResult,
            parsedPresetBlocks = parsedPresetBlocks,
            resolvedAssistantPrefill = resolvedPrefill,
            prefix = prefix,
            tailBundle = tailBundle,
            fileRecallBundle = fileRecallBundle,
            fileRetentionByAttachmentId = retentionByAttId,
            resolvedSemanticScores = resolvedSemanticScores,
            dynamicOverhead = totalDynamicOverhead,
            waterline = waterline,
            messageCacheById = messageCacheById,
        )
    }
}
