package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ContextBuildInput(
    val conversation: ChatConversation,
    val messages: List<ChatMessage>,
    val memories: List<UserMemory> = emptyList(),
    val memoryBudget: MemoryBudget = MemoryBudget.standard,
    val modelContextWindow: Int = 128000,
    val novelChapters: List<NovelChapter> = emptyList(),
    val novelLore: List<NovelLoreEntry> = emptyList(),
    val memoryLedger: List<MemoryLedgerItem> = emptyList(),
    val memoryEpisodes: List<MemoryEpisode> = emptyList(),
    val draftText: String = "",
    val draftImageCount: Int = 0,
    val draftAttachments: List<ChatAttachmentMeta> = emptyList(),
    val attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
    val draftInputKind: NovelInputKind? = null,
    val draftRpInputKind: RpInputKind? = null,
    val semanticScorer: SemanticScorer? = null,
    val resolveAttachmentDataUrl: (suspend (String) -> String?)? = null,
    val resolveAttachmentText: (suspend (String) -> String?)? = null,
    val precomputedSemanticScores: Map<String, Double>? = null,
    val mutedRecallKeys: Set<String> = emptySet(),
    val modelId: String? = null,
    val externalCalibration: Double? = null,
    val minSemanticCosine: Double = 0.50,
    val characterCard: CharacterCardEntity? = null,
    val persona: PersonaEntity? = null,
    val promptPreset: PromptPresetEntity? = null,
    val mountedWorldbooks: List<WorldbookMountedBook> = emptyList(),
    val worldbookEvalOverride: WorldbookEvalResult? = null,
    val loreTimedStates: Map<String, LoreTimedEntryState> = emptyMap(),
    val assistantPrefill: String = "",
)

data class BuiltChatContextV4(
    val built: BuiltChatContext,
    val worldbookResult: WorldbookEvalResult? = null,
    val resolvedAssistantPrefill: String? = null,
)

data class CoreMemoryBlockResult(
    val blockText: String,
    val usedTokens: Int,
    val archivalMemories: List<UserMemory>,
)

data class SystemPrefixResult(
    val systemText: String,
    val systemTokens: Int,
    val memoryTokens: Int,
    val summaryTokens: Int,
    val novelLoreTokens: Int,
    val archivalUserMemories: List<UserMemory>,
    val overflowEpisodes: List<MemoryEpisode>,
    val baseSystemTokens: Int = 0,
)

data class TailRecallBundle(
    val chatTailRecallBlock: String = "",
    val chatRecallTokens: Int = 0,
    val rpActiveLedgerLines: List<String> = emptyList(),
    val rpRecalledMemoryLines: List<String> = emptyList(),
    val rpArchivedEpisodeLines: List<String> = emptyList(),
    val novelArchivedEpisodeLines: List<String> = emptyList(),
    val tailRecallTokens: Int = 0,
    val retrievedMemoryCount: Int = 0,
    val recalledItems: List<RecalledMemoryInspectItem> = emptyList(),
    val recallBudgetTokens: Int = 0,
    val droppedRecallCount: Int = 0,
    val retrievalModeUsed: String = "lexical",
)

object ContextBuilder {
    const val SLIDE_STRIDE = 4

    private val STATIC_MACRO_RE = Regex("\\{\\{\\s*(char|user)\\s*\\}\\}", RegexOption.IGNORE_CASE)

    private val CATEGORY_TAG = mapOf(
        MemoryCategory.preference to "偏好",
        MemoryCategory.profile to "个人档案",
        MemoryCategory.project to "长期项目",
        MemoryCategory.rule to "规则要求",
        MemoryCategory.other to "备忘",
    )

    private const val MAX_SUMMARY_CAP_TOKENS = 8000

    fun estimateTextTokens(text: String, modelId: String? = null): Int =
        TokenEstimator.estimateText(text, modelId)

    fun estimateMessageTokens(text: String, imageCount: Int = 0, modelId: String? = null): Int =
        TokenEstimator.estimateMessage(text, imageCount, modelId)

    fun coreMemoryLimit(budget: MemoryBudget): Int = when (budget) {
        MemoryBudget.low -> 400
        MemoryBudget.standard -> 800
        MemoryBudget.high -> 1500
    }

    fun totalUserMemoryLimit(budget: MemoryBudget): Int = when (budget) {
        MemoryBudget.low -> 700
        MemoryBudget.standard -> 1600
        MemoryBudget.high -> 3000
    }

    fun tailRecallShare(budget: MemoryBudget): Double = when (budget) {
        MemoryBudget.low -> 0.08
        MemoryBudget.standard -> 0.14
        MemoryBudget.high -> 0.22
    }

    fun tailRecallPresetCap(budget: MemoryBudget): Int = when (budget) {
        MemoryBudget.low -> 2000
        MemoryBudget.standard -> 4000
        MemoryBudget.high -> 8000
    }

    fun topKRecallCap(budget: MemoryBudget): Int = when (budget) {
        MemoryBudget.low -> 4
        MemoryBudget.standard -> 8
        MemoryBudget.high -> 12
    }

    fun computeTailBudget(effectiveLimitTokens: Int, budget: MemoryBudget): Int =
        min(floor(effectiveLimitTokens * tailRecallShare(budget)).toInt(), tailRecallPresetCap(budget))

    fun computeSummaryCap(effectiveLimitTokens: Int): Int =
        min(max(400, (effectiveLimitTokens * 0.16).toInt()), MAX_SUMMARY_CAP_TOKENS)

    private fun mutedIdsForSource(mutedKeys: Set<String>, source: String): Set<String> {
        if (mutedKeys.isEmpty()) return emptySet()
        val prefix = "$source:"
        val out = HashSet<String>()
        for (k in mutedKeys) {
            if (k.startsWith(prefix)) {
                out.add(k.removePrefix(prefix))
            } else if (!k.contains(':')) {
                out.add(k)
            }
        }
        return out
    }

    fun buildCoreUserMemoryBlock(
        memories: List<UserMemory>,
        budget: MemoryBudget,
        modelId: String? = null,
    ): CoreMemoryBlockResult {
        val enabled = memories.filter { it.enabled && it.content.isNotBlank() }
        if (enabled.isEmpty()) return CoreMemoryBlockResult("", 0, emptyList())

        val cap = coreMemoryLimit(budget)
        val sorted = enabled.sortedWith(
            compareByDescending<UserMemory> { it.pinned }
                .thenByDescending { it.category == MemoryCategory.rule || it.category == MemoryCategory.profile }
                .thenByDescending { it.updatedAt },
        )

        val coreLines = ArrayList<String>()
        val archival = ArrayList<UserMemory>()
        var used = 16

        for (mem in sorted) {
            val tok = if (modelId == null && mem.estimatedTokens > 0) {
                mem.estimatedTokens
            } else {
                MemoryExtractor.estimateUserMemoryTokens(mem.content, modelId)
            }
            val isCoreCandidate = mem.pinned || mem.category == MemoryCategory.rule || mem.category == MemoryCategory.profile
            if (isCoreCandidate && used + tok <= cap) {
                val tag = CATEGORY_TAG[mem.category] ?: "其他"
                coreLines.add("- [$tag] ${mem.content.trim()}")
                used += tok
            } else {
                archival.add(mem)
            }
        }

        val block = if (coreLines.isNotEmpty()) {
            "<user_core_memory>\n以下是用户的核心长期记忆与偏好（请始终遵守）：\n${coreLines.joinToString("\n")}\n</user_core_memory>"
        } else {
            ""
        }
        val actualTokens = if (block.isNotEmpty()) estimateTextTokens(block, modelId) + 8 else 0
        return CoreMemoryBlockResult(block, actualTokens, archival)
    }

    data class ActiveWindowSlice(
        val active: List<ChatMessage>,
        val effectiveTipSeq: Int,
        val decayedImageMessageIds: Set<String>,
        val decayedFileMessageIds: Set<String>,
        val activeChapter: NovelChapter?,
        val allActiveMessages: List<ChatMessage> = active,
        val uncompactedExcludedCount: Int = 0,
    )

    fun sliceActiveWindow(
        conversation: ChatConversation,
        messages: List<ChatMessage>,
        novelChapters: List<NovelChapter>,
        attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
    ): ActiveWindowSlice {
        val allActive = TimelineReconciler.activeMessages(messages)
        val tipSeq = allActive.maxOfOrNull { it.seq } ?: 0

        // C1 fix: always exclude compacted turns (seq <= summarizedUntilSeq) from the raw active window,
        // and honor Stage-0 / hysteresis activeWindowStartSeq if set beyond summarizedUntilSeq.
        val compactedCutoff = conversation.summarizedUntilSeq.coerceAtLeast(0)
        val uncompacted = allActive.filter { it.seq > compactedCutoff }
        val windowStart = conversation.params.activeWindowStartSeq?.takeIf { it > compactedCutoff }
        val active = if (windowStart != null) {
            val windowed = uncompacted.filter { it.seq >= windowStart }
            if (windowed.isNotEmpty()) windowed else uncompacted.takeLast(1)
        } else {
            uncompacted
        }
        val uncompactedExcludedCount = max(0, uncompacted.size - active.size)

        val maxImgTurns = max(0, conversation.params.maxImageTurns)
        val maxFileTurns = max(0, conversation.params.maxFileTurns)

        val imgMsgIds = active
            .filter { msg ->
                msg.role == "user" && msg.attachmentIds.any { id -> attachmentsById[id]?.isFile != true }
            }
            .map { it.id }

        val decayedImages = if (imgMsgIds.size > maxImgTurns) {
            imgMsgIds.dropLast(maxImgTurns).toSet()
        } else {
            emptySet()
        }

        val recentUserIdsForFiles = if (maxFileTurns > 0) {
            active.filter { it.role == "user" }.takeLast(maxFileTurns).map { it.id }.toSet()
        } else {
            emptySet()
        }

        val fileMsgIds = active
            .filter { msg ->
                msg.role == "user" && msg.attachmentIds.any { id -> attachmentsById[id]?.isFile == true }
            }
            .map { it.id }

        val decayedFiles = fileMsgIds.filter { it !in recentUserIdsForFiles }.toSet()

        val activeChapter = if (conversation.mode == ConversationMode.novel && conversation.novel != null) {
            val id = conversation.novel.activeChapterId
            novelChapters.firstOrNull { it.id == id } ?: novelChapters.maxByOrNull { it.order }
        } else {
            null
        }

        return ActiveWindowSlice(
            active = active,
            effectiveTipSeq = tipSeq,
            decayedImageMessageIds = decayedImages,
            decayedFileMessageIds = decayedFiles,
            activeChapter = activeChapter,
            allActiveMessages = allActive,
            uncompactedExcludedCount = uncompactedExcludedCount,
        )
    }

    fun buildSystemPrefix(
        conversation: ChatConversation,
        memories: List<UserMemory>,
        memoryBudget: MemoryBudget,
        novelChapters: List<NovelChapter>,
        constantLore: List<NovelLoreEntry>,
        episodes: List<MemoryEpisode>,
        effectiveTipSeq: Int,
        effectiveLimitTokens: Int,
        modelId: String? = null,
    ): SystemPrefixResult {
        val isNovel = conversation.mode == ConversationMode.novel && conversation.novel != null
        val isRp = conversation.mode == ConversationMode.rp && conversation.rp != null

        val activeEpisodes = episodes
            .filter { !it.stale && it.toSeq <= effectiveTipSeq }
            .sortedWith(compareByDescending<MemoryEpisode> { it.level }.thenBy { it.fromSeq })

        val summaryCap = computeSummaryCap(effectiveLimitTokens)
        val tier1Episodes = ArrayList<MemoryEpisode>()
        val overflowEpisodes = ArrayList<MemoryEpisode>()
        var sumUsed = 0

        for (ep in activeEpisodes) {
            val tok = if (modelId == null && ep.estimatedTokens > 0) {
                ep.estimatedTokens
            } else {
                EpisodeConsolidator.estimateEpisodeTokens(ep.title, ep.summary, modelId)
            }
            if (sumUsed + tok <= summaryCap) {
                tier1Episodes.add(ep)
                sumUsed += tok
            } else {
                overflowEpisodes.add(ep)
            }
        }

        val chronicleText = buildString {
            val frozenLines = tier1Episodes
                .sortedBy { it.fromSeq }
                .map { "- [Seq ${it.fromSeq}~${it.toSeq} ${it.title}] ${it.summary}" }
            if (frozenLines.isNotEmpty()) {
                append(frozenLines.joinToString("\n"))
            } else if (conversation.archivedSummary.isNotBlank()) {
                append(conversation.archivedSummary.trim())
            }
        }

        // H5 fix: honor conversation.params.enableAutoMemory for personal UserMemory injection
        val effectiveMemories = if (conversation.params.enableAutoMemory) memories else emptyList()
        val coreMem = buildCoreUserMemoryBlock(effectiveMemories, memoryBudget, modelId)

        val activeChapter = if (isNovel) {
            val cid = conversation.novel!!.activeChapterId
            novelChapters.firstOrNull { it.id == cid } ?: novelChapters.maxByOrNull { it.order }
        } else {
            null
        }

        val baseSystem = when {
            isNovel -> NovelEngine.buildNovelSystemPrompt(
                novel = conversation.novel!!,
                activeChapter = activeChapter,
                constantLore = constantLore,
                chronicleSummary = chronicleText,
                previousChapters = novelChapters,
            )
            isRp -> RpEngine.buildRpSystemPrompt(
                rp = conversation.rp!!,
                constantLore = constantLore,
                chronicleSummary = chronicleText,
            )
            else -> buildString {
                if (conversation.systemPrompt.isNotBlank()) {
                    appendLine(conversation.systemPrompt.trim())
                }
                if (chronicleText.isNotBlank()) {
                    if (isNotEmpty()) appendLine()
                    appendLine("<archived_memory_chronicle>")
                    appendLine(chronicleText)
                    appendLine("</archived_memory_chronicle>")
                }
            }.trim()
        }

        val fullSystem = buildString {
            if (baseSystem.isNotBlank()) appendLine(baseSystem)
            if (coreMem.blockText.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(coreMem.blockText)
            }
        }.trim()

        val loreTok = constantLore.sumOf { NovelEngine.estimateLoreTokens(it, modelId) }
        val chronicleTok = if (chronicleText.isNotBlank()) estimateTextTokens(chronicleText, modelId) else 0
        val sysTok = if (fullSystem.isNotBlank()) estimateTextTokens(fullSystem, modelId) + 12 else 0
        val baseSystemTok = max(0, sysTok - coreMem.usedTokens - chronicleTok - loreTok)

        return SystemPrefixResult(
            systemText = fullSystem,
            systemTokens = sysTok,
            memoryTokens = coreMem.usedTokens,
            summaryTokens = chronicleTok,
            novelLoreTokens = loreTok,
            archivalUserMemories = coreMem.archivalMemories,
            overflowEpisodes = overflowEpisodes,
            baseSystemTokens = baseSystemTok,
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

        val tailBudget = computeTailBudget(effectiveLimitTokens, memoryBudget)
        val topKCap = topKRecallCap(memoryBudget)
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
        val mutedLedger = mutedIdsForSource(mutedRecallKeys, "rp_ledger")
        val mutedEpisodes = mutedIdsForSource(mutedRecallKeys, "episode_archived")
        val mutedUserMems = mutedIdsForSource(mutedRecallKeys, "user_memory_archival")

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
                    options = ScoreMemoryOptions(
                        activeTipSeq = effectiveTipSeq,
                        maxTokens = ledgerCap,
                        semanticScores = semanticScores,
                        semanticScorer = if (semanticScores == null) semanticScorer else null,
                        minSemanticCosine = minSemanticCosine,
                        maxResults = topKCap,
                        modelId = modelId,
                        mutedIds = mutedLedger,
                    ),
                )
            } else {
                ScoreMemoryResult(emptyList(), emptyList(), 0)
            }

            val epCap = max(0, tailBudget - recalledLedger.usedTokens)
            val recalledEps = if (overflowEpisodes.isNotEmpty() && epCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = overflowEpisodes.map { CjkSearch.episodeToSearchCandidate(it) },
                    query = query,
                    options = ScoreMemoryOptions(
                        activeTipSeq = effectiveTipSeq,
                        maxTokens = epCap,
                        semanticScores = semanticScores,
                        semanticScorer = if (semanticScores == null) semanticScorer else null,
                        minSemanticCosine = minSemanticCosine,
                        maxResults = topKCap,
                        modelId = modelId,
                        mutedIds = mutedEpisodes,
                    ),
                )
            } else {
                ScoreMemoryResult(emptyList(), emptyList(), 0)
            }

            val activeLines = ArrayList<String>(activeLedger.size)
            val activeInspect = ArrayList<RecalledMemoryInspectItem>(activeLedger.size)
            var activeTok = 0
            for (item in activeLedger) {
                val line = "- [${item.kind}] ${item.title}: ${item.content}"
                val tok = estimateTextTokens(line, modelId)
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
                    add(
                        RecalledMemoryInspectItem(
                            id = s.item.id,
                            title = s.item.title ?: "RP 台账",
                            contentPreview = (s.item.content ?: "").take(80),
                            category = s.item.kind ?: "fact",
                            source = "rp_ledger",
                            score = (s.score * 1000).roundToInt() / 1000.0,
                            estimatedTokens = s.estimatedTokens,
                            matchedTerms = s.matchedTerms,
                            triggerKind = s.triggerKind,
                        ),
                    )
                }
                recalledEps.selected.forEach { s ->
                    add(
                        RecalledMemoryInspectItem(
                            id = s.item.id,
                            title = s.item.title ?: "历史幕次",
                            contentPreview = (s.item.summary ?: s.item.content ?: "").take(80),
                            category = "episode",
                            source = "episode_archived",
                            score = (s.score * 1000).roundToInt() / 1000.0,
                            estimatedTokens = s.estimatedTokens,
                            matchedTerms = s.matchedTerms,
                            triggerKind = s.triggerKind,
                        ),
                    )
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
                    options = ScoreMemoryOptions(
                        activeTipSeq = effectiveTipSeq,
                        maxTokens = epCap,
                        semanticScores = semanticScores,
                        semanticScorer = if (semanticScores == null) semanticScorer else null,
                        minSemanticCosine = minSemanticCosine,
                        maxResults = topKCap,
                        modelId = modelId,
                        mutedIds = mutedEpisodes,
                    ),
                )
            } else {
                ScoreMemoryResult(emptyList(), emptyList(), 0)
            }
            val epLines = epRes.selected.map { "- [${it.item.title ?: "前文片段"}] ${(it.item.summary ?: it.item.content).orEmpty()}" }
            val epInspect = epRes.selected.map { s ->
                RecalledMemoryInspectItem(
                    id = s.item.id,
                    title = s.item.title ?: "前文片段",
                    contentPreview = (s.item.summary ?: s.item.content ?: "").take(80),
                    category = "episode",
                    source = "episode_archived",
                    score = (s.score * 1000).roundToInt() / 1000.0,
                    estimatedTokens = s.estimatedTokens,
                    matchedTerms = s.matchedTerms,
                    triggerKind = s.triggerKind,
                )
            }

            val crossModeCap = max(0, min(tailBudget - epRes.usedTokens, totalUserMemoryLimit(memoryBudget) - coreMemoryTokens))
            val archRes = if (effectiveArchivalMemories.isNotEmpty() && crossModeCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = effectiveArchivalMemories.map { CjkSearch.chatMemoryToSearchCandidate(it) },
                    query = query,
                    options = ScoreMemoryOptions(
                        activeTipSeq = Int.MAX_VALUE,
                        maxTokens = crossModeCap,
                        semanticScores = semanticScores,
                        semanticScorer = if (semanticScores == null) semanticScorer else null,
                        minSemanticCosine = minSemanticCosine,
                        maxResults = topKCap,
                        modelId = modelId,
                        mutedIds = mutedUserMems,
                    ),
                )
            } else {
                ScoreMemoryResult(emptyList(), emptyList(), 0)
            }

            val crossBlock = if (archRes.selected.isNotEmpty()) {
                val lines = archRes.selected.map { s -> "- [${s.item.category ?: "other"}] ${s.item.content.orEmpty()}" }
                "<recalled_user_memory>\n${lines.joinToString("\n")}\n</recalled_user_memory>"
            } else {
                ""
            }
            val crossInspect = archRes.selected.map { s ->
                RecalledMemoryInspectItem(
                    id = s.item.id,
                    title = "用户偏好",
                    contentPreview = (s.item.content ?: "").take(80),
                    category = s.item.category ?: "other",
                    source = "user_memory_archival",
                    score = (s.score * 1000).roundToInt() / 1000.0,
                    estimatedTokens = s.estimatedTokens,
                    matchedTerms = s.matchedTerms,
                    triggerKind = s.triggerKind,
                )
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
            val remMemLimit = max(0, totalUserMemoryLimit(memoryBudget) - coreMemoryTokens)
            val memCap = min(max(0, tailBudget - epReserve), remMemLimit)
            val archRes = if (memCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = effectiveArchivalMemories.map { CjkSearch.chatMemoryToSearchCandidate(it) },
                    query = query,
                    options = ScoreMemoryOptions(
                        activeTipSeq = Int.MAX_VALUE,
                        maxTokens = memCap,
                        semanticScores = semanticScores,
                        semanticScorer = if (semanticScores == null) semanticScorer else null,
                        minSemanticCosine = minSemanticCosine,
                        maxResults = topKCap,
                        modelId = modelId,
                        mutedIds = mutedUserMems,
                    ),
                )
            } else {
                ScoreMemoryResult(emptyList(), emptyList(), 0)
            }
            dropped += archRes.droppedCount
            if (archRes.retrievalModeUsed == "hybrid") usedHybrid = true
            if (archRes.selected.isNotEmpty()) {
                val lines = archRes.selected.map { s ->
                    val catName = s.item.category ?: "other"
                    val cat = MemoryCategory.entries.firstOrNull { it.name == catName } ?: MemoryCategory.other
                    val tag = CATEGORY_TAG[cat] ?: "其他"
                    inspected.add(
                        RecalledMemoryInspectItem(
                            id = s.item.id,
                            title = tag,
                            contentPreview = (s.item.content ?: "").take(80),
                            category = catName,
                            source = "user_memory_archival",
                            score = (s.score * 1000).roundToInt() / 1000.0,
                            estimatedTokens = s.estimatedTokens,
                            matchedTerms = s.matchedTerms,
                            triggerKind = s.triggerKind,
                        ),
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
                options = ScoreMemoryOptions(
                    activeTipSeq = effectiveTipSeq,
                    maxTokens = remForEps,
                    semanticScores = semanticScores,
                    semanticScorer = if (semanticScores == null) semanticScorer else null,
                    maxResults = topKCap,
                    modelId = modelId,
                    mutedIds = mutedEpisodes,
                ),
            )
            dropped += epRes.droppedCount
            if (epRes.retrievalModeUsed == "hybrid") usedHybrid = true
            if (epRes.selected.isNotEmpty()) {
                val lines = epRes.selected.map { s ->
                    inspected.add(
                        RecalledMemoryInspectItem(
                            id = s.item.id,
                            title = s.item.title ?: "历史片段",
                            contentPreview = (s.item.summary ?: s.item.content ?: "").take(80),
                            category = "episode",
                            source = "episode_archived",
                            score = (s.score * 1000).roundToInt() / 1000.0,
                            estimatedTokens = s.estimatedTokens,
                            matchedTerms = s.matchedTerms,
                            triggerKind = s.triggerKind,
                        ),
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

    private data class DepthInjectionItem(
        val depth: Int,
        val role: String,
        val text: String,
        val sortBucket: Int,
        val subOrder: Int,
    )

    private fun isV4AssetMode(input: ContextBuildInput): Boolean =
        input.characterCard != null ||
            input.persona != null ||
            input.promptPreset != null ||
            input.mountedWorldbooks.isNotEmpty() ||
            input.worldbookEvalOverride != null

    private fun isDepthInjectionPosition(position: Int): Boolean =
        position == 1 || position == WorldbookEngine.POSITION_AT_DEPTH

    private fun normalizeWireRole(raw: String): String = when (raw.trim().lowercase()) {
        "user" -> "user"
        "assistant", "model" -> "assistant"
        else -> "system"
    }

    private fun decodeStringList(rawJson: String): List<String> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
        return runCatching {
            val arr = AppJson.parseToJsonElement(trimmed) as? kotlinx.serialization.json.JsonArray
                ?: return@runCatching emptyList()
            arr.mapNotNull { el ->
                val prim = el as? kotlinx.serialization.json.JsonPrimitive
                if (prim == null || prim is kotlinx.serialization.json.JsonNull) null else prim.content
            }
        }.getOrDefault(emptyList())
    }

    private fun parsePresetBlocks(preset: PromptPresetEntity?): List<PromptPresetBlock> {
        val raw = preset?.blocksJson?.trim().orEmpty()
        if (raw.isEmpty() || raw == "[]") return emptyList()
        return runCatching {
            AppJson.decodeFromString(ListSerializer(PromptPresetBlock.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun parseCardCast(card: CharacterCardEntity?): List<RpCharacter> {
        val raw = card?.castJson?.trim().orEmpty()
        if (raw.isEmpty() || raw == "[]") return emptyList()
        return runCatching {
            AppJson.decodeFromString(ListSerializer(RpCharacter.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun resolveStaticCharName(conv: ChatConversation, card: CharacterCardEntity?): String {
        card?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val rp = conv.rp
        if (rp != null) {
            rp.cast.firstOrNull { !it.isPlayer }?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            if (rp.title.isNotBlank()) return rp.title.trim()
        }
        return conv.title.trim().ifBlank { "角色" }
    }

    private fun resolveStaticUserName(conv: ChatConversation, persona: PersonaEntity?): String {
        persona?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        conv.rp?.playerName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return "旅人"
    }

    internal fun expandStaticMacros(text: String, charName: String, userName: String): String {
        if (text.isEmpty() || !text.contains("{{")) return text
        return STATIC_MACRO_RE.replace(text) { m ->
            when (m.groupValues[1].lowercase()) {
                "char" -> charName
                "user" -> userName
                else -> m.value
            }
        }
    }

    private fun expandDynamicV4Macros(
        text: String,
        conv: ChatConversation,
        card: CharacterCardEntity?,
        persona: PersonaEntity?,
    ): String {
        if (text.isEmpty() || !text.contains("{{")) return text
        val charName = resolveStaticCharName(conv, card)
        val userName = resolveStaticUserName(conv, persona)
        val rp = conv.rp
        if (rp != null) {
            val effectiveCast = if (rp.cast.any { !it.isPlayer }) {
                rp.cast
            } else {
                listOf(RpCharacter(id = "lead_macro", name = charName))
            }
            return RpEngine.replaceMacros(
                text = text,
                rp = rp.copy(playerName = userName, cast = effectiveCast),
            )
        }
        return expandStaticMacros(text, charName, userName)
    }

    internal fun computeSlidingWindowKeepCount(
        activeSize: Int,
        rawKeptCount: Int,
        maxHistoryLimit: Int?,
    ): Int {
        if (activeSize <= 0) return 0
        val clampedRaw = rawKeptCount.coerceIn(1, activeSize)
        if (clampedRaw >= activeSize) return activeSize
        if (maxHistoryLimit != null && clampedRaw >= min(activeSize, maxHistoryLimit)) {
            return min(activeSize, maxHistoryLimit)
        }
        val minDrop = activeSize - clampedRaw
        val snappedDrop = ((minDrop + SLIDE_STRIDE - 1) / SLIDE_STRIDE) * SLIDE_STRIDE
        val boundedDrop = min(snappedDrop, activeSize - 1)
        return (activeSize - boundedDrop).coerceAtLeast(1)
    }

    private fun buildV4Zone0SystemPrefix(
        input: ContextBuildInput,
        episodes: List<MemoryEpisode>,
        novelChapters: List<NovelChapter>,
        worldbookResult: WorldbookEvalResult,
        presetBlocks: List<PromptPresetBlock>,
        effectiveTipSeq: Int,
        effectiveLimitTokens: Int,
        modelId: String?,
    ): SystemPrefixResult {
        val conv = input.conversation
        val isNovel = conv.mode == ConversationMode.novel && conv.novel != null
        val isRp = conv.mode == ConversationMode.rp && conv.rp != null
        val charName = resolveStaticCharName(conv, input.characterCard)
        val userName = resolveStaticUserName(conv, input.persona)

        val activeEpisodes = episodes
            .filter { !it.stale && it.toSeq <= effectiveTipSeq }
            .sortedWith(compareByDescending<MemoryEpisode> { it.level }.thenBy { it.fromSeq })

        val summaryCap = computeSummaryCap(effectiveLimitTokens)
        val tier1Episodes = ArrayList<MemoryEpisode>()
        val overflowEpisodes = ArrayList<MemoryEpisode>()
        var sumUsed = 0

        for (ep in activeEpisodes) {
            val tok = if (modelId == null && ep.estimatedTokens > 0) {
                ep.estimatedTokens
            } else {
                EpisodeConsolidator.estimateEpisodeTokens(ep.title, ep.summary, modelId)
            }
            if (sumUsed + tok <= summaryCap) {
                tier1Episodes.add(ep)
                sumUsed += tok
            } else {
                overflowEpisodes.add(ep)
            }
        }

        val chronicleText = buildString {
            val frozenLines = tier1Episodes
                .sortedBy { it.fromSeq }
                .map { "- [Seq ${it.fromSeq}~${it.toSeq} ${it.title}] ${it.summary}" }
            if (frozenLines.isNotEmpty()) {
                append(frozenLines.joinToString("\n"))
            } else if (conv.archivedSummary.isNotBlank()) {
                append(conv.archivedSummary.trim())
            }
        }

        val effectiveMemories = if (conv.params.enableAutoMemory) input.memories else emptyList()
        val coreMem = buildCoreUserMemoryBlock(effectiveMemories, input.memoryBudget, modelId)

        val activeChapter = if (isNovel) {
            val cid = conv.novel!!.activeChapterId
            novelChapters.firstOrNull { it.id == cid } ?: novelChapters.maxByOrNull { it.order }
        } else {
            null
        }

        val staticPresetText = presetBlocks
            .filter { it.enabled && !it.marker && it.content.isNotBlank() && !isDepthInjectionPosition(it.injectionPosition) }
            .map { expandStaticMacros(it.content.trim(), charName, userName) }
            .joinToString("\n\n")

        val beforeCharConstant = worldbookResult.zone0ConstantEntries
            .filter { it.position == WorldbookEngine.POSITION_BEFORE_CHAR }
        val afterCharConstant = worldbookResult.zone0ConstantEntries
            .filter { it.position != WorldbookEngine.POSITION_BEFORE_CHAR }

        val card = input.characterCard
        val baseStaticSection = when {
            isNovel -> expandStaticMacros(
                NovelEngine.buildNovelSystemPrompt(
                    novel = conv.novel!!,
                    activeChapter = activeChapter,
                    constantLore = emptyList(),
                    chronicleSummary = "",
                    previousChapters = novelChapters,
                ),
                charName,
                userName,
            )
            card != null -> buildString {
                if (card.systemPrompt.isNotBlank()) {
                    appendLine(card.systemPrompt.trim())
                }
                if (isRp && conv.rp != null) {
                    val styleTitle = when (conv.rp.playStyle) {
                        RpPlayStyle.chengguang -> "沉浸式互动剧场 / 视觉小说（橙光风格）"
                        RpPlayStyle.tavern -> "深度角色沉浸互动（SillyTavern 酒馆风格）"
                        RpPlayStyle.trpg -> "桌面角色扮演跑团（TRPG / GM 主持风格）"
                    }
                    if (isNotEmpty()) appendLine()
                    appendLine("你正在主持$styleTitle《${conv.rp.title.ifBlank { charName }}》。")
                    val personaHint = input.persona?.description?.trim()?.takeIf { it.isNotEmpty() }
                        ?: conv.rp.playerPersona.trim()
                    appendLine("玩家扮演角色：【$userName】${if (personaHint.isNotBlank() && input.persona == null) "—— $personaHint" else ""}")
                } else {
                    if (isNotEmpty()) appendLine()
                    appendLine("角色档案：$charName")
                }

                val desc = card.description.trim()
                if (desc.isNotEmpty()) appendLine("\n【角色设定与背景】\n$desc")
                val pers = card.personality.trim()
                if (pers.isNotEmpty()) appendLine("\n【性格特征】\n$pers")
                val scen = card.scenario.trim().ifBlank { conv.rp?.worldview?.trim().orEmpty() }
                if (scen.isNotEmpty()) appendLine("\n【世界观设定】\n$scen")

                val rules = conv.rp?.systemRules?.trim().orEmpty()
                if (rules.isNotEmpty() && rules != card.systemPrompt.trim()) {
                    appendLine("\n【游戏与检定规则】\n$rules")
                }
                val narrator = conv.rp?.narratorStyle?.trim().orEmpty().ifBlank { card.creatorNotes.trim() }
                if (narrator.isNotEmpty()) appendLine("\n【叙述风格】\n$narrator")

                val example = card.mesExample.trim().ifBlank { conv.rp?.mesExample?.trim().orEmpty() }
                if (example.isNotEmpty()) appendLine("\n【角色对话风格示例 (Example Dialogue)】\n$example")

                val staticCast = parseCardCast(card).ifEmpty { conv.rp?.cast.orEmpty() }
                if (staticCast.isNotEmpty()) {
                    appendLine("\n【主要登场人物表（静态人设）】")
                    staticCast.forEach { c ->
                        append("- ${c.name}（${c.role}）")
                        if (c.personality.isNotBlank()) append("；性格：${c.personality}")
                        if (c.speakingStyle.isNotBlank()) append("；口吻：${c.speakingStyle}")
                        if (c.appearance.isNotBlank()) append("；外貌：${c.appearance}")
                        if (c.relationship.isNotBlank()) append("；关系：${c.relationship}")
                        if (c.secret.isNotBlank()) append("；暗线秘密（仅GM掌握，切勿直白泄密）：${c.secret}")
                        appendLine()
                    }
                }
            }.trim().let { expandStaticMacros(it, charName, userName) }
            isRp -> {
                val rp = conv.rp!!
                buildString {
                    val styleTitle = when (rp.playStyle) {
                        RpPlayStyle.chengguang -> "沉浸式互动剧场 / 视觉小说（橙光风格）"
                        RpPlayStyle.tavern -> "深度角色沉浸互动（SillyTavern 酒馆风格）"
                        RpPlayStyle.trpg -> "桌面角色扮演跑团（TRPG / GM 主持风格）"
                    }
                    appendLine("你正在主持$styleTitle《${rp.title}》。")
                    appendLine("玩家扮演角色：【$userName】${if (rp.playerPersona.isNotBlank() && input.persona == null) "—— ${rp.playerPersona}" else ""}")
                    if (rp.worldview.isNotBlank()) appendLine("\n【世界观设定】\n${rp.worldview.trim()}")
                    if (rp.systemRules.isNotBlank()) appendLine("\n【游戏与检定规则】\n${rp.systemRules.trim()}")
                    if (rp.narratorStyle.isNotBlank()) appendLine("\n【叙述风格】\n${rp.narratorStyle.trim()}")
                    if (rp.mesExample.isNotBlank()) appendLine("\n【角色对话风格示例 (Example Dialogue)】\n${rp.mesExample.trim()}")
                    if (rp.cast.isNotEmpty()) {
                        appendLine("\n【主要登场人物表（静态人设）】")
                        rp.cast.forEach { c ->
                            append("- ${c.name}（${c.role}）")
                            if (c.personality.isNotBlank()) append("；性格：${c.personality}")
                            if (c.speakingStyle.isNotBlank()) append("；口吻：${c.speakingStyle}")
                            if (c.appearance.isNotBlank()) append("；外貌：${c.appearance}")
                            if (c.relationship.isNotBlank()) append("；关系：${c.relationship}")
                            if (c.secret.isNotBlank()) append("；暗线秘密（仅GM掌握，切勿直白泄密）：${c.secret}")
                            appendLine()
                        }
                    }
                    appendLine("\n【演出与主持原则】")
                    appendLine("1. 严格尊重玩家主权：禁止代替【$userName】说出对白、决定内心想法或擅自代做重大抉择。")
                    appendLine("2. 若本回合包含骰子检定结果，必须忠实依据成败与大成功/大失败演绎客观后果。")
                }.trim().let { expandStaticMacros(it, charName, userName) }
            }
            else -> expandStaticMacros(conv.systemPrompt.trim(), charName, userName)
        }

        val personaStaticSection = input.persona
            ?.takeIf { it.description.isNotBlank() && !isDepthInjectionPosition(it.injectionPosition) }
            ?.let { p ->
                val expanded = expandStaticMacros(p.description.trim(), charName, userName)
                "<user_persona name=\"$userName\">\n$expanded\n</user_persona>"
            }.orEmpty()

        fun formatWorldbookSection(tag: String, entries: List<EvaluatedWorldbookEntry>): String {
            if (entries.isEmpty()) return ""
            val body = entries.joinToString("\n") { e ->
                "- [${e.category}] ${e.title}：${expandStaticMacros(e.content.trim(), charName, userName)}"
            }
            return "<$tag>\n$body\n</$tag>"
        }

        val sections = buildList {
            if (staticPresetText.isNotBlank()) add(staticPresetText)
            formatWorldbookSection("worldbook_before_char", beforeCharConstant).takeIf { it.isNotEmpty() }?.let { add(it) }
            if (baseStaticSection.isNotBlank()) add(baseStaticSection)
            if (personaStaticSection.isNotBlank()) add(personaStaticSection)
            formatWorldbookSection("worldbook_constant", afterCharConstant).takeIf { it.isNotEmpty() }?.let { add(it) }
            if (chronicleText.isNotBlank()) add("<archived_memory_chronicle>\n$chronicleText\n</archived_memory_chronicle>")
            if (coreMem.blockText.isNotBlank()) add(expandStaticMacros(coreMem.blockText, charName, userName))
        }

        val fullSystem = sections.joinToString("\n\n").trim()
        val loreTok = worldbookResult.constantTokens
        val chronicleTok = if (chronicleText.isNotBlank()) estimateTextTokens(chronicleText, modelId) else 0
        val sysTok = if (fullSystem.isNotBlank()) estimateTextTokens(fullSystem, modelId) + 12 else 0
        val baseSystemTok = max(0, sysTok - coreMem.usedTokens - chronicleTok - loreTok)

        return SystemPrefixResult(
            systemText = fullSystem,
            systemTokens = sysTok,
            memoryTokens = coreMem.usedTokens,
            summaryTokens = chronicleTok,
            novelLoreTokens = loreTok,
            archivalUserMemories = coreMem.archivalMemories,
            overflowEpisodes = overflowEpisodes,
            baseSystemTokens = baseSystemTok,
        )
    }

    private data class ComputedPipeline(
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
        val dynamicOverhead: Int,
        val waterline: ContextWaterlineSummary,
    )

    private fun precomputeSinglePassSemanticScores(
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
                                keywords = decodeStringList(entry.keysJson),
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
        }
        if (allCandidates.isEmpty()) return emptyMap()
        return scorer.score(budgetedQuery.rawQueryText, allCandidates)
    }

    private fun runSharedPipeline(input: ContextBuildInput, allowScorer: Boolean): ComputedPipeline {
        val conv = input.conversation
        val slice = sliceActiveWindow(conv, input.messages, input.novelChapters, input.attachmentsById)
        val requestedWindow = conv.params.contextWindowOverride?.takeIf { it >= 4096 } ?: input.modelContextWindow
        val windowLimit = max(4096, requestedWindow)
        val budgetTokens = max(2048, (windowLimit * 0.80).toInt())
        val maxReserve = max(512, min(32768, budgetTokens / 2))
        val outputReserve = (conv.params.maxTokens ?: 2048).coerceIn(512, maxReserve)
        val effectiveLimitTokens = max(1024, budgetTokens - outputReserve)

        val effectiveModelId = input.modelId?.takeIf { it.isNotBlank() }
            ?: conv.model?.takeIf { it.isNotBlank() }
        val calibration = when {
            input.externalCalibration != null && input.externalCalibration in 0.4..2.5 -> input.externalCalibration
            conv.tokenCalibration in 0.4..2.5 -> conv.tokenCalibration
            else -> 1.0
        }

        val isNovel = conv.mode == ConversationMode.novel && conv.novel != null
        val isRp = conv.mode == ConversationMode.rp && conv.rp != null
        val isV4Mode = isV4AssetMode(input)
        val parsedPresetBlocks = if (isV4Mode) parsePresetBlocks(input.promptPreset) else emptyList()

        // Single-pass semantic scoring: use precomputedSemanticScores if given (including on synchronous
        // waterline preview), otherwise invoke semanticScorer at most ONCE when allowScorer == true.
        val resolvedSemanticScores: Map<String, Double>? = when {
            input.precomputedSemanticScores != null -> input.precomputedSemanticScores
            allowScorer && input.semanticScorer != null -> precomputeSinglePassSemanticScores(input, slice, isNovel, isRp)
            else -> null
        }

        val mutedLoreIds = mutedIdsForSource(input.mutedRecallKeys, "novel_lore") +
            mutedIdsForSource(input.mutedRecallKeys, "worldbook_entry")
        val topKCap = topKRecallCap(input.memoryBudget)

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
            prefix = buildV4Zone0SystemPrefix(
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
            prefix = buildSystemPrefix(
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
                .filter { it.enabled && !it.marker && it.content.isNotBlank() && isDepthInjectionPosition(it.injectionPosition) }
                .sumOf { estimateTextTokens(it.content, effectiveModelId) + 6 }
            val cardDepthTok = input.characterCard?.depthPromptText?.takeIf { it.isNotBlank() }?.let {
                estimateTextTokens(it, effectiveModelId) + 6
            } ?: 0
            val cardPostTok = input.characterCard?.postHistoryInstructions?.takeIf {
                it.isNotBlank() && (!isRp || conv.rp?.postHistoryInstructions.isNullOrBlank())
            }?.let { estimateTextTokens(it, effectiveModelId) + 8 } ?: 0
            val personaDepthTok = input.persona?.takeIf {
                it.description.isNotBlank() && isDepthInjectionPosition(it.injectionPosition)
            }?.let { estimateTextTokens(it.description, effectiveModelId) + 6 } ?: 0
            wbDynamic + presetDepthTok + cardDepthTok + cardPostTok + personaDepthTok
        } else {
            0
        }

        val dynamicOverhead = when {
            isNovel -> {
                val novelCfg = conv.novel!!
                (if (isV4Mode) v4ExtraDynamicTokens else loreMatch.triggeredTokens) +
                    tailBundle.tailRecallTokens +
                    estimateTextTokens(novelCfg.authorNote, effectiveModelId) +
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
                    estimateTextTokens(rpCfg.authorNote, effectiveModelId) +
                    estimateTextTokens(rpCfg.postHistoryInstructions, effectiveModelId) +
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
                            isDepthInjectionPosition(it.injectionPosition) &&
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
                ?.let { expandDynamicV4Macros(it, conv, input.characterCard, input.persona) }
        }

        val effectiveDraftImages = if (input.draftAttachments.isNotEmpty()) {
            input.draftAttachments.count { it.isImage }
        } else {
            input.draftImageCount
        }
        val effectiveDraftFiles = input.draftAttachments.filter { it.isFile }
        val fileTurnBudget = FileContextBudgeter.computeFileTurnBudget(
            effectiveLimitTokens = effectiveLimitTokens,
            reservedFixedTokens = prefix.systemTokens + dynamicOverhead,
        )

        val msgCosts = slice.active.map { msg ->
            resolveMsgTokenCost(
                msg = msg,
                imageDecayed = msg.id in slice.decayedImageMessageIds,
                fileDecayed = msg.id in slice.decayedFileMessageIds,
                attachmentsById = input.attachmentsById,
                perTurnFileBudget = fileTurnBudget,
                modelId = effectiveModelId,
                conversation = conv,
            )
        }
        val msgFileCosts = slice.active.map { msg ->
            resolveMsgFileTokens(
                msg = msg,
                fileDecayed = msg.id in slice.decayedFileMessageIds,
                attachmentsById = input.attachmentsById,
                perTurnFileBudget = fileTurnBudget,
            )
        }
        val draftFileTokens = FileContextBudgeter.estimateDraftFilesInjectedTokens(
            files = effectiveDraftFiles,
            effectiveLimitTokens = effectiveLimitTokens,
            reservedFixedTokens = prefix.systemTokens + dynamicOverhead,
        )
        val draftTokens = if (input.draftText.isNotBlank() || effectiveDraftImages > 0 || effectiveDraftFiles.isNotEmpty()) {
            estimateMessageTokens(input.draftText, effectiveDraftImages, effectiveModelId) + draftFileTokens
        } else {
            0
        }

        val maxHistoryLimit = conv.params.maxHistoryMessages?.takeIf { it > 0 }
        var running = ((prefix.systemTokens + draftTokens + dynamicOverhead) * calibration).roundToInt()
        var rawKept = 0
        for (i in slice.active.indices.reversed()) {
            if (maxHistoryLimit != null && rawKept >= maxHistoryLimit) break
            val msgTok = (msgCosts[i] * calibration).roundToInt()
            if (rawKept > 0 && running + msgTok > effectiveLimitTokens) break
            running += msgTok
            rawKept++
        }

        val kept = computeSlidingWindowKeepCount(
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

        // Dual-Metric accounting:
        // - sentTokens = actual kept payload + prefix + overhead + draft (drives bar/warning color)
        // - archiveTotalTokens = full untrimmed non-deleted history + prefix + overhead + draft (informational)
        val sentRaw = prefix.systemTokens + draftTokens + dynamicOverhead + keptHistoryRaw
        val sentTokens = (sentRaw * calibration).roundToInt()
        val sentPct = min(100, ((sentTokens.toDouble() / max(1, effectiveLimitTokens)) * 100.0).roundToInt())

        val allArchiveHistoryRaw = slice.allActiveMessages.sumOf { msg ->
            resolveMsgTokenCost(
                msg = msg,
                imageDecayed = msg.id in slice.decayedImageMessageIds,
                fileDecayed = msg.id in slice.decayedFileMessageIds,
                attachmentsById = input.attachmentsById,
                perTurnFileBudget = fileTurnBudget,
                modelId = effectiveModelId,
                conversation = conv,
            )
        }
        val archiveTotalTokens = ((prefix.systemTokens + draftTokens + dynamicOverhead + allArchiveHistoryRaw) * calibration).roundToInt()

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
        val allRecalled = loreInspect + tailBundle.recalledItems
        val recalledTotalTokens = allRecalled.sumOf { it.estimatedTokens }

        val recallBudgetTokens = when {
            isNovel -> conv.novel!!.loreTokenBudget + tailBundle.recallBudgetTokens
            isRp -> conv.rp!!.loreTokenBudget + tailBundle.recallBudgetTokens
            worldbookResult != null -> (input.mountedWorldbooks.firstOrNull()?.worldbook?.tokenBudget ?: 1600) + tailBundle.recallBudgetTokens
            else -> tailBundle.recallBudgetTokens
        }
        val loreHybrid = worldbookResult?.retrievalModeUsed == "hybrid" || loreMatch.retrievalModeUsed == "hybrid"
        val retrievalModeUsed = if (loreHybrid || tailBundle.retrievalModeUsed == "hybrid") "hybrid" else "lexical"

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
            decayedFileMessageIds = slice.decayedFileMessageIds,
            fileTokens = cal(keptFileTokensRaw + draftFileTokens),
            memoryTokens = cal(prefix.memoryTokens),
            summaryTokens = cal(prefix.summaryTokens),
            triggeredLoreIds = triggeredIds,
            triggeredLoreTitles = triggeredTitles,
            novelLoreTokens = cal(totalLoreTokensRaw),
            retrievedMemoryCount = allRecalled.size,
            recalledMemoryTokens = cal(tailBundle.tailRecallTokens),
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
            droppedRecallCount = droppedLoreCount + tailBundle.droppedRecallCount,
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
            dynamicOverhead = dynamicOverhead,
            waterline = waterline,
        )
    }

    private fun resolveMsgFileTokens(
        msg: ChatMessage,
        fileDecayed: Boolean,
        attachmentsById: Map<String, ChatAttachmentMeta>,
        perTurnFileBudget: Int,
    ): Int {
        if (msg.role != "user" || msg.attachmentIds.isEmpty()) return 0
        val files = msg.attachmentIds.mapNotNull { attachmentsById[it]?.takeIf { a -> a.isFile } }
        if (files.isEmpty()) return 0
        if (fileDecayed) return files.size * FileContextBudgeter.DECAYED_STUB_TOKENS
        val rawSum = files.sumOf { max(32, it.estimatedTokens) + 48 }
        return min(rawSum, perTurnFileBudget)
    }

    internal fun cleanAssistantHistoryText(msg: ChatMessage, conversation: ChatConversation): String {
        if (msg.role != "assistant") return msg.content
        return when {
            conversation.mode == ConversationMode.novel && conversation.novel != null ->
                NovelExport.stripInternalTags(msg.content)
            conversation.mode == ConversationMode.rp && conversation.rp != null -> {
                val stripped = RpEngine.stripRpInternalTags(msg.content)
                RpRegexEngine.applyScripts(
                    text = stripped,
                    scripts = conversation.rp.regexScripts,
                    placement = "ai_output",
                    forDisplay = false,
                    rp = conversation.rp,
                )
            }
            else -> msg.content
        }
    }

    private fun resolveMsgTokenCost(
        msg: ChatMessage,
        imageDecayed: Boolean,
        fileDecayed: Boolean,
        attachmentsById: Map<String, ChatAttachmentMeta>,
        perTurnFileBudget: Int,
        modelId: String? = null,
        conversation: ChatConversation? = null,
    ): Int {
        val imageIds = if (msg.role == "user") {
            msg.attachmentIds.filter { id -> attachmentsById[id]?.isFile != true }
        } else {
            emptyList()
        }
        val imgCount = if (!imageDecayed) imageIds.size else 0
        val fileTok = resolveMsgFileTokens(msg, fileDecayed, attachmentsById, perTurnFileBudget)
        val effectiveText = if (conversation != null) cleanAssistantHistoryText(msg, conversation) else msg.content
        val base = estimateMessageTokens(effectiveText, imgCount, modelId) + fileTok
        return if (imageDecayed && imageIds.isNotEmpty()) base + 12 else base
    }

    fun computeContextWaterline(input: ContextBuildInput): ContextWaterlineSummary =
        runSharedPipeline(input, allowScorer = false).waterline

    suspend fun buildChatContext(input: ContextBuildInput): BuiltChatContext =
        buildChatContextV4(input).built

    suspend fun buildChatContextV4(input: ContextBuildInput): BuiltChatContextV4 {
        val pipe = runSharedPipeline(input, allowScorer = true)
        val conv = input.conversation
        val active = pipe.active
        val decayedImages = pipe.waterline.decayedImageMessageIds.toMutableSet()
        val decayedFiles = pipe.waterline.decayedFileMessageIds.toMutableSet()
        val fileTurnBudget = FileContextBudgeter.computeFileTurnBudget(
            effectiveLimitTokens = pipe.effectiveLimitTokens,
            reservedFixedTokens = pipe.prefix.systemTokens + pipe.dynamicOverhead,
        )

        val maxHistoryLimit = conv.params.maxHistoryMessages?.takeIf { it > 0 }
        val candidateWindow = if (maxHistoryLimit != null && active.size > maxHistoryLimit) {
            active.takeLast(maxHistoryLimit)
        } else {
            active
        }

        fun calcTotal(): Int {
            var tok = pipe.prefix.systemTokens + pipe.dynamicOverhead
            for (msg in candidateWindow) {
                tok += resolveMsgTokenCost(
                    msg = msg,
                    imageDecayed = msg.id in decayedImages,
                    fileDecayed = msg.id in decayedFiles,
                    attachmentsById = input.attachmentsById,
                    perTurnFileBudget = fileTurnBudget,
                    modelId = pipe.effectiveModelId,
                    conversation = conv,
                )
            }
            return (tok * pipe.calibration).roundToInt()
        }

        if (calcTotal() > pipe.effectiveLimitTokens) {
            for (i in 0 until max(0, candidateWindow.size - 1)) {
                val msg = candidateWindow[i]
                val hasFiles = msg.attachmentIds.any { input.attachmentsById[it]?.isFile == true }
                val hasImgs = msg.attachmentIds.any { input.attachmentsById[it]?.isFile != true }
                if (hasFiles && msg.id !in decayedFiles) {
                    decayedFiles.add(msg.id)
                    if (calcTotal() <= pipe.effectiveLimitTokens) break
                }
                if (hasImgs && msg.id !in decayedImages) {
                    decayedImages.add(msg.id)
                    if (calcTotal() <= pipe.effectiveLimitTokens) break
                }
            }
        }

        var used = ((pipe.prefix.systemTokens + pipe.dynamicOverhead) * pipe.calibration).roundToInt()
        var rawSelectedCount = 0
        for (i in active.indices.reversed()) {
            if (maxHistoryLimit != null && rawSelectedCount >= maxHistoryLimit) break
            val msg = active[i]
            val rawCost = resolveMsgTokenCost(
                msg = msg,
                imageDecayed = msg.id in decayedImages,
                fileDecayed = msg.id in decayedFiles,
                attachmentsById = input.attachmentsById,
                perTurnFileBudget = fileTurnBudget,
                modelId = pipe.effectiveModelId,
                conversation = conv,
            )
            val msgTok = (rawCost * pipe.calibration).roundToInt()
            if (rawSelectedCount > 0 && used + msgTok > pipe.effectiveLimitTokens) break
            used += msgTok
            rawSelectedCount++
        }

        val selectedCount = computeSlidingWindowKeepCount(
            activeSize = active.size,
            rawKeptCount = rawSelectedCount,
            maxHistoryLimit = maxHistoryLimit,
        )
        val selected = if (selectedCount > 0) active.takeLast(selectedCount) else emptyList()
        var selectedRawHistory = 0
        var selectedRawFiles = 0
        for (msg in selected) {
            selectedRawHistory += resolveMsgTokenCost(
                msg = msg,
                imageDecayed = msg.id in decayedImages,
                fileDecayed = msg.id in decayedFiles,
                attachmentsById = input.attachmentsById,
                perTurnFileBudget = fileTurnBudget,
                modelId = pipe.effectiveModelId,
                conversation = conv,
            )
            selectedRawFiles += resolveMsgFileTokens(
                msg = msg,
                fileDecayed = msg.id in decayedFiles,
                attachmentsById = input.attachmentsById,
                perTurnFileBudget = fileTurnBudget,
            )
        }

        val wireMessages = ArrayList<WireChatMessage>()
        if (pipe.prefix.systemText.isNotBlank()) {
            wireMessages.add(WireChatMessage(role = "system", textContent = pipe.prefix.systemText))
        }

        val wbResult = pipe.worldbookResult
        val zone3StandardLore: List<NovelLoreEntry> = if (pipe.isV4Mode && wbResult != null) {
            wbResult.zone3TriggeredEntries
                .filter { !(it.position == WorldbookEngine.POSITION_AT_DEPTH && it.depth == 0) }
                .map { entry ->
                    val expanded = expandDynamicV4Macros(entry.content.trim(), conv, input.characterCard, input.persona)
                    NovelLoreEntry(
                        id = entry.id,
                        conversationId = conv.id,
                        title = entry.title,
                        category = entry.category,
                        content = expanded,
                        alwaysActive = entry.constant,
                        createdAt = 0L,
                        updatedAt = 0L,
                        estimatedTokens = entry.estimatedTokens,
                    )
                }
        } else {
            pipe.loreMatch.triggered
        }

        val zone3TrailingSuffix = if (pipe.isV4Mode) {
            val parts = ArrayList<String>()
            val depth0Lore = wbResult?.zone3TriggeredEntries.orEmpty()
                .filter { it.position == WorldbookEngine.POSITION_AT_DEPTH && it.depth == 0 }
            if (depth0Lore.isNotEmpty()) {
                val body = depth0Lore.joinToString("\n") { e ->
                    val txt = expandDynamicV4Macros(e.content.trim(), conv, input.characterCard, input.persona)
                    "- [${e.category}] ${e.title}: $txt"
                }
                parts.add("<depth_instructions>\n$body\n</depth_instructions>")
            }
            val depth0PresetBlocks = pipe.parsedPresetBlocks
                .filter {
                    it.enabled && !it.marker &&
                        isDepthInjectionPosition(it.injectionPosition) &&
                        it.injectionDepth == 0 &&
                        !it.role.equals("assistant", ignoreCase = true) &&
                        it.content.isNotBlank()
                }
            for (b in depth0PresetBlocks) {
                parts.add(expandDynamicV4Macros(b.content.trim(), conv, input.characterCard, input.persona))
            }
            val personaDepth0 = input.persona
                ?.takeIf { it.description.isNotBlank() && isDepthInjectionPosition(it.injectionPosition) && it.injectionDepth == 0 }
            if (personaDepth0 != null) {
                val userName = resolveStaticUserName(conv, input.persona)
                val body = expandDynamicV4Macros(personaDepth0.description.trim(), conv, input.characterCard, input.persona)
                parts.add("【用户人设 ($userName)】\n$body")
            }
            val cardDepth0 = input.characterCard
                ?.takeIf { it.depthPromptText.isNotBlank() && it.depthPromptDepth == 0 }
            if (cardDepth0 != null) {
                parts.add(expandDynamicV4Macros(cardDepth0.depthPromptText.trim(), conv, input.characterCard, input.persona))
            }
            val cardPost = input.characterCard?.postHistoryInstructions?.trim().orEmpty()
            val rpPost = conv.rp?.postHistoryInstructions?.trim().orEmpty()
            if (cardPost.isNotEmpty() && (!pipe.isRp || rpPost != cardPost)) {
                val expandedPost = expandDynamicV4Macros(cardPost, conv, input.characterCard, input.persona)
                parts.add("【后置核心指令 (Post-History Instructions)】\n$expandedPost")
            }
            parts.joinToString("\n\n").trim()
        } else {
            ""
        }

        val chatWorldbookTriggeredBlock = if (pipe.isV4Mode && !pipe.isRp && !pipe.isNovel && zone3StandardLore.isNotEmpty()) {
            val lines = zone3StandardLore.joinToString("\n") { entry ->
                "- [${entry.category}] ${entry.title}: ${entry.content.trim()}"
            }
            "<worldbook_triggered>\n$lines\n</worldbook_triggered>"
        } else {
            ""
        }

        val lastUserIdx = selected.indexOfLast { it.role == "user" }
        val latestUserQuery = if (lastUserIdx >= 0) selected[lastUserIdx].content else input.draftText
        val existingLoreTitles = if (pipe.isNovel) pipe.novelLore.map { it.title } else emptyList()

        // Cross-turn follow-up: if the latest user turn explicitly mentions a decayed earlier file, re-retrieve it on demand
        val reRecalledDecayedFiles = if (lastUserIdx >= 0 && latestUserQuery.isNotBlank()) {
            val latestOwnIds = selected[lastUserIdx].attachmentIds.toSet()
            pipe.allActiveMessages
                .filter { it.role == "user" && it.id in decayedFiles }
                .flatMap { it.attachmentIds }
                .filter { it !in latestOwnIds }
                .mapNotNull { input.attachmentsById[it]?.takeIf { m -> m.isFile } }
                .filter { meta -> FileContextBudgeter.messageMentionsFile(latestUserQuery, meta) }
                .distinctBy { it.id }
                .take(2)
        } else {
            emptyList()
        }

        var reRecalledInjectedRawTokens = 0

        fun appendTrailingZone3(baseText: String): String {
            return buildList {
                if (baseText.isNotBlank()) add(baseText.trim())
                if (zone3TrailingSuffix.isNotBlank()) add(zone3TrailingSuffix)
            }.joinToString("\n\n")
        }

        for (idx in selected.indices) {
            val msg = selected[idx]
            val rawContent = cleanAssistantHistoryText(msg, conv)

            val isFinalNovelUser = pipe.isNovel && idx == lastUserIdx
            val isFinalRpUser = pipe.isRp && idx == lastUserIdx
            val isFinalChatUser = !pipe.isNovel && !pipe.isRp && idx == lastUserIdx

            val finalText = when {
                isFinalNovelUser -> {
                    val wrapped = NovelEngine.wrapNovelTurnUserMessage(
                        rawContent = rawContent,
                        inputKind = msg.inputKind ?: input.draftInputKind ?: NovelInputKind.director,
                        novel = conv.novel!!,
                        activeChapter = pipe.activeChapter,
                        triggeredLore = zone3StandardLore,
                        existingLoreTitles = existingLoreTitles,
                        archivedEpisodeLines = pipe.tailBundle.novelArchivedEpisodeLines,
                    )
                    val withRecall = if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) {
                        "${pipe.tailBundle.chatTailRecallBlock}\n\n$wrapped"
                    } else {
                        wrapped
                    }
                    appendTrailingZone3(withRecall)
                }
                isFinalRpUser -> {
                    val wrappedRp = RpEngine.wrapRpTurnUserMessage(
                        rawContent = rawContent,
                        inputKind = msg.rpInputKind ?: input.draftRpInputKind ?: RpInputKind.action,
                        rp = conv.rp!!,
                        diceRoll = msg.rpDiceRoll,
                        activeLedgerLines = pipe.tailBundle.rpActiveLedgerLines,
                        recalledMemoryLines = pipe.tailBundle.rpRecalledMemoryLines,
                        archivedEpisodeLines = pipe.tailBundle.rpArchivedEpisodeLines,
                        triggeredLore = zone3StandardLore,
                        includeAuthorNote = conv.rp.authorNoteDepth <= 0,
                    )
                    appendTrailingZone3(wrappedRp)
                }
                isFinalChatUser -> {
                    val combinedChat = buildList {
                        if (chatWorldbookTriggeredBlock.isNotBlank()) add(chatWorldbookTriggeredBlock)
                        if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) add(pipe.tailBundle.chatTailRecallBlock)
                        if (rawContent.isNotBlank()) add(rawContent)
                        if (zone3TrailingSuffix.isNotBlank()) add(zone3TrailingSuffix)
                    }.joinToString("\n\n")
                    combinedChat
                }
                else -> rawContent
            }

            val attIds = msg.attachmentIds
            val fileMetas = if (msg.role == "user") {
                attIds.mapNotNull { input.attachmentsById[it]?.takeIf { a -> a.isFile } }
            } else {
                emptyList()
            }
            val imageIds = if (msg.role == "user") {
                attIds.filter { id -> input.attachmentsById[id]?.isFile != true }
            } else {
                emptyList()
            }

            val extraFilesForTurn = if (idx == lastUserIdx) reRecalledDecayedFiles else emptyList()
            val fileBlocksText = buildString {
                if (fileMetas.isNotEmpty()) {
                    if (msg.id in decayedFiles || input.resolveAttachmentText == null) {
                        fileMetas.forEach { fm ->
                            appendLine(FileContextBudgeter.buildDecayedFileStub(fm))
                        }
                    } else {
                        val allTurnFiles = fileMetas + extraFilesForTurn
                        val allocations = FileContextBudgeter.allocateFileBudgets(allTurnFiles, fileTurnBudget)
                        val queryForTurn = if (idx == lastUserIdx) latestUserQuery else rawContent
                        for (fm in fileMetas) {
                            val rawFileText = input.resolveAttachmentText.invoke(fm.id)
                            if (!rawFileText.isNullOrBlank()) {
                                val fitted = FileContextBudgeter.fitFileToBudget(
                                    meta = fm,
                                    fullText = rawFileText,
                                    queryText = queryForTurn,
                                    budgetTokens = allocations[fm.id] ?: fileTurnBudget,
                                )
                                appendLine(fitted.formattedBlock)
                                appendLine()
                            } else {
                                appendLine(FileContextBudgeter.buildDecayedFileStub(fm))
                            }
                        }
                    }
                }
                if (extraFilesForTurn.isNotEmpty() && input.resolveAttachmentText != null) {
                    val allocations = FileContextBudgeter.allocateFileBudgets(
                        fileMetas + extraFilesForTurn,
                        fileTurnBudget,
                    )
                    for (fm in extraFilesForTurn) {
                        val rawFileText = input.resolveAttachmentText.invoke(fm.id)
                        if (!rawFileText.isNullOrBlank()) {
                            val fitted = FileContextBudgeter.fitFileToBudget(
                                meta = fm,
                                fullText = rawFileText,
                                queryText = latestUserQuery,
                                budgetTokens = allocations[fm.id] ?: (fileTurnBudget / 2),
                                modeOverride = FileInjectMode.retrieve,
                            )
                            reRecalledInjectedRawTokens += fitted.injectedTokens
                            appendLine(fitted.formattedBlock)
                            appendLine()
                        }
                    }
                }
            }.trim()

            val mergedText = buildString {
                if (fileBlocksText.isNotBlank()) {
                    append(fileBlocksText)
                    if (finalText.isNotBlank()) append("\n\n")
                }
                append(finalText)
            }.trim()

            if (imageIds.isNotEmpty() && msg.role == "user") {
                if (msg.id in decayedImages || input.resolveAttachmentDataUrl == null) {
                    val notice = "[${imageIds.size} 张历史图片已省略]"
                    wireMessages.add(
                        WireChatMessage(
                            role = "user",
                            textContent = if (mergedText.isNotBlank()) "$notice\n$mergedText" else notice,
                        ),
                    )
                } else {
                    val parts = ArrayList<WireChatContentPart>()
                    if (mergedText.isNotBlank()) {
                        parts.add(WireChatContentPart(type = "text", text = mergedText))
                    }
                    for (id in imageIds) {
                        val url = input.resolveAttachmentDataUrl.invoke(id)
                        if (!url.isNullOrBlank()) {
                            parts.add(WireChatContentPart(type = "image_url", imageUrl = url))
                        }
                    }
                    if (parts.isEmpty()) {
                        wireMessages.add(WireChatMessage(role = "user", textContent = mergedText.ifBlank { "[图片]" }))
                    } else {
                        wireMessages.add(WireChatMessage(role = "user", parts = parts))
                    }
                }
            } else {
                wireMessages.add(WireChatMessage(role = msg.role, textContent = mergedText))
            }
        }

        if (lastUserIdx == -1) {
            when {
                pipe.isNovel -> {
                    val openingPrompt = NovelEngine.wrapNovelTurnUserMessage(
                        rawContent = if (!pipe.activeChapter?.outline.isNullOrBlank()) {
                            "请按本章细纲重新撰写精彩开篇正文：${pipe.activeChapter!!.outline.trim()}"
                        } else {
                            "请结合书名、简介、大纲与世界观，重新撰写本章精彩开篇正文。"
                        },
                        inputKind = NovelInputKind.director,
                        novel = conv.novel!!,
                        activeChapter = pipe.activeChapter,
                        triggeredLore = zone3StandardLore,
                        existingLoreTitles = existingLoreTitles,
                        archivedEpisodeLines = pipe.tailBundle.novelArchivedEpisodeLines,
                    )
                    val content = if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) {
                        "${pipe.tailBundle.chatTailRecallBlock}\n\n$openingPrompt"
                    } else {
                        openingPrompt
                    }
                    wireMessages.add(WireChatMessage(role = "user", textContent = appendTrailingZone3(content)))
                }
                pipe.isRp -> {
                    val openingRp = RpEngine.wrapRpTurnUserMessage(
                        rawContent = "请结合世界观设定、角色关系与当前场景状态，继续演绎本幕剧情。",
                        inputKind = input.draftRpInputKind ?: RpInputKind.action,
                        rp = conv.rp!!,
                        activeLedgerLines = pipe.tailBundle.rpActiveLedgerLines,
                        recalledMemoryLines = pipe.tailBundle.rpRecalledMemoryLines,
                        archivedEpisodeLines = pipe.tailBundle.rpArchivedEpisodeLines,
                        triggeredLore = zone3StandardLore,
                        includeAuthorNote = conv.rp.authorNoteDepth <= 0,
                    )
                    wireMessages.add(WireChatMessage(role = "user", textContent = appendTrailingZone3(openingRp)))
                }
                else -> {
                    val syntheticChat = buildList {
                        if (chatWorldbookTriggeredBlock.isNotBlank()) add(chatWorldbookTriggeredBlock)
                        if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) add(pipe.tailBundle.chatTailRecallBlock)
                        if (zone3TrailingSuffix.isNotBlank()) add(zone3TrailingSuffix)
                    }.joinToString("\n\n")
                    if (syntheticChat.isNotBlank()) {
                        wireMessages.add(WireChatMessage(role = "user", textContent = syntheticChat))
                    }
                }
            }
        }

        // Zone 2 (@D >= 1) Shallow Depth Band injection with isDepthInjection = true
        if (!pipe.isV4Mode) {
            if (pipe.isRp && conv.rp != null && conv.rp.authorNote.isNotBlank() && conv.rp.authorNoteDepth >= 1) {
                val firstNonSystemIdx = wireMessages.indexOfFirst { it.role != "system" }.let {
                    if (it < 0) wireMessages.size else it
                }
                val insertIdx = (wireMessages.size - conv.rp.authorNoteDepth).coerceIn(firstNonSystemIdx, wireMessages.size)
                val noteText = RpEngine.replaceMacros("【OOC 作者备注】\n${conv.rp.authorNote.trim()}", conv.rp)
                wireMessages.add(insertIdx, WireChatMessage(role = "system", textContent = noteText, isDepthInjection = true))
            }
        } else {
            val depthItems = ArrayList<DepthInjectionItem>()
            wbResult?.zone2DepthEntries.orEmpty().forEachIndexed { idx, entry ->
                val expanded = expandDynamicV4Macros(entry.content.trim(), conv, input.characterCard, input.persona)
                if (expanded.isNotBlank()) {
                    depthItems.add(
                        DepthInjectionItem(
                            depth = entry.depth.coerceAtLeast(1),
                            role = normalizeWireRole(entry.outletRole),
                            text = "- [${entry.category}] ${entry.title}: $expanded",
                            sortBucket = 0,
                            subOrder = idx,
                        ),
                    )
                }
            }
            pipe.parsedPresetBlocks
                .filter { it.enabled && !it.marker && isDepthInjectionPosition(it.injectionPosition) && it.injectionDepth >= 1 && it.content.isNotBlank() }
                .forEachIndexed { idx, block ->
                    val expanded = expandDynamicV4Macros(block.content.trim(), conv, input.characterCard, input.persona)
                    if (expanded.isNotBlank()) {
                        depthItems.add(
                            DepthInjectionItem(
                                depth = block.injectionDepth,
                                role = normalizeWireRole(block.role),
                                text = expanded,
                                sortBucket = 1,
                                subOrder = idx,
                            ),
                        )
                    }
                }
            input.persona
                ?.takeIf { it.description.isNotBlank() && isDepthInjectionPosition(it.injectionPosition) && it.injectionDepth >= 1 }
                ?.let { persona ->
                    val userName = resolveStaticUserName(conv, persona)
                    val expanded = expandDynamicV4Macros(persona.description.trim(), conv, input.characterCard, input.persona)
                    depthItems.add(
                        DepthInjectionItem(
                            depth = persona.injectionDepth,
                            role = "system",
                            text = "【用户人设 ($userName)】\n$expanded",
                            sortBucket = 2,
                            subOrder = 0,
                        ),
                    )
                }
            input.characterCard
                ?.takeIf {
                    it.depthPromptText.isNotBlank() &&
                        it.depthPromptDepth >= 1 &&
                        (!pipe.isRp || conv.rp == null || it.depthPromptText.trim() != conv.rp.authorNote.trim())
                }
                ?.let { card ->
                    val expanded = expandDynamicV4Macros(card.depthPromptText.trim(), conv, input.characterCard, input.persona)
                    depthItems.add(
                        DepthInjectionItem(
                            depth = card.depthPromptDepth,
                            role = normalizeWireRole(card.depthPromptRole),
                            text = expanded,
                            sortBucket = 3,
                            subOrder = 0,
                        ),
                    )
                }
            if (pipe.isRp && conv.rp != null && conv.rp.authorNote.isNotBlank() && conv.rp.authorNoteDepth >= 1) {
                val noteText = expandDynamicV4Macros("【OOC 作者备注】\n${conv.rp.authorNote.trim()}", conv, input.characterCard, input.persona)
                depthItems.add(
                    DepthInjectionItem(
                        depth = conv.rp.authorNoteDepth,
                        role = "system",
                        text = noteText,
                        sortBucket = 4,
                        subOrder = 0,
                    ),
                )
            }

            if (depthItems.isNotEmpty()) {
                val baseMessages = wireMessages.toList()
                val firstNonSystemIdx = baseMessages.indexOfFirst { it.role != "system" }.let {
                    if (it < 0) baseMessages.size else it
                }
                val buckets = HashMap<Int, ArrayList<DepthInjectionItem>>()
                for (item in depthItems) {
                    val anchor = (baseMessages.size - item.depth).coerceIn(firstNonSystemIdx, baseMessages.size)
                    buckets.getOrPut(anchor) { ArrayList() }.add(item)
                }
                wireMessages.clear()
                for (pos in 0..baseMessages.size) {
                    val bucket = buckets[pos]
                    if (!bucket.isNullOrEmpty()) {
                        val sortedBucket = bucket.sortedWith(
                            compareByDescending<DepthInjectionItem> { it.depth }
                                .thenBy { it.sortBucket }
                                .thenBy { it.subOrder },
                        )
                        var currentRole: String? = null
                        val currentLines = ArrayList<String>()
                        fun flushRoleGroup() {
                            val r = currentRole ?: return
                            if (currentLines.isNotEmpty()) {
                                wireMessages.add(
                                    WireChatMessage(
                                        role = r,
                                        textContent = currentLines.joinToString("\n\n"),
                                        isDepthInjection = true,
                                    ),
                                )
                            }
                            currentLines.clear()
                            currentRole = null
                        }
                        for (item in sortedBucket) {
                            if (currentRole == null || currentRole == item.role) {
                                currentRole = item.role
                                currentLines.add(item.text)
                            } else {
                                flushRoleGroup()
                                currentRole = item.role
                                currentLines.add(item.text)
                            }
                        }
                        flushRoleGroup()
                    }
                    if (pos < baseMessages.size) {
                        wireMessages.add(baseMessages[pos])
                    }
                }
            }
        }

        // C2 fix: recompute sentTokens / sentPercentage / fileTokens / hardOverflow for actual `selected` payload
        val actualSentRaw = pipe.prefix.systemTokens + pipe.dynamicOverhead + selectedRawHistory + reRecalledInjectedRawTokens
        val actualSentTokens = (actualSentRaw * pipe.calibration).roundToInt()
        val actualSentPct = min(100, ((actualSentTokens.toDouble() / max(1, pipe.effectiveLimitTokens)) * 100.0).roundToInt())
        val actualFileTokens = ((selectedRawFiles + reRecalledInjectedRawTokens) * pipe.calibration).roundToInt()
        val actualTrimmedCount = pipe.uncompactedExcludedCount + max(0, active.size - selected.size)
        val actualHardOverflow = actualSentTokens > pipe.windowLimit
        val actualIsSliding = actualTrimmedCount > 0

        val built = BuiltChatContext(
            wireMessages = wireMessages,
            summary = pipe.waterline.copy(
                usedTokens = actualSentTokens,
                percentage = actualSentPct,
                exceedsModelWindow = actualHardOverflow,
                decayedImageMessageIds = decayedImages,
                decayedFileMessageIds = decayedFiles,
                trimmedMessageCount = actualTrimmedCount,
                fileTokens = actualFileTokens,
                sentTokens = actualSentTokens,
                sentPercentage = actualSentPct,
                isSliding = actualIsSliding,
                hardOverflow = actualHardOverflow,
                uncompactedOverflowCount = actualTrimmedCount,
            ),
        )
        return BuiltChatContextV4(
            built = built,
            worldbookResult = pipe.worldbookResult,
            resolvedAssistantPrefill = pipe.resolvedAssistantPrefill,
        )
    }
}
