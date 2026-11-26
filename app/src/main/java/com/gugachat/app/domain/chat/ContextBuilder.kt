package com.gugachat.app.domain.chat

import kotlin.math.ceil
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
    val draftInputKind: NovelInputKind? = null,
    val draftRpInputKind: RpInputKind? = null,
    val semanticScorer: SemanticScorer? = null,
    val resolveAttachmentDataUrl: (suspend (String) -> String?)? = null,
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
)

object ContextBuilder {
    private val CATEGORY_TAG = mapOf(
        MemoryCategory.preference to "偏好",
        MemoryCategory.profile to "个人档案",
        MemoryCategory.project to "长期项目",
        MemoryCategory.rule to "规则要求",
        MemoryCategory.other to "备忘",
    )

    fun estimateTextTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        var spaces = 0
        for (ch in text) {
            when {
                CjkSearch.isHanChar(ch) || ch.code in 0x3000..0x303F || ch.code in 0xFF00..0xFFEF -> cjk++
                ch.isWhitespace() -> spaces++
                else -> other++
            }
        }
        return max(1, ceil(cjk * 1.45 + other * 0.38 + spaces * 0.20).toInt())
    }

    fun estimateMessageTokens(text: String, imageCount: Int = 0): Int =
        estimateTextTokens(text) + max(0, imageCount) * 765 + 8

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

    fun buildCoreUserMemoryBlock(
        memories: List<UserMemory>,
        budget: MemoryBudget,
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
            val tok = if (mem.estimatedTokens > 0) mem.estimatedTokens else MemoryExtractor.estimateUserMemoryTokens(mem.content)
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
        val actualTokens = if (block.isNotEmpty()) estimateTextTokens(block) + 8 else 0
        return CoreMemoryBlockResult(block, actualTokens, archival)
    }

    data class ActiveWindowSlice(
        val active: List<ChatMessage>,
        val effectiveTipSeq: Int,
        val decayedImageMessageIds: Set<String>,
        val activeChapter: NovelChapter?,
    )

    fun sliceActiveWindow(
        conversation: ChatConversation,
        messages: List<ChatMessage>,
        novelChapters: List<NovelChapter>,
    ): ActiveWindowSlice {
        val active = TimelineReconciler.activeMessages(messages)
        val tipSeq = active.maxOfOrNull { it.seq } ?: 0
        val maxImgTurns = max(0, conversation.params.maxImageTurns)

        val imgMsgIds = active
            .filter { it.role == "user" && it.attachmentIds.isNotEmpty() }
            .map { it.id }

        val decayed = if (imgMsgIds.size > maxImgTurns) {
            imgMsgIds.dropLast(maxImgTurns).toSet()
        } else {
            emptySet()
        }

        val activeChapter = if (conversation.mode == ConversationMode.novel && conversation.novel != null) {
            val id = conversation.novel.activeChapterId
            novelChapters.firstOrNull { it.id == id } ?: novelChapters.maxByOrNull { it.order }
        } else {
            null
        }

        return ActiveWindowSlice(active, tipSeq, decayed, activeChapter)
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
    ): SystemPrefixResult {
        val isNovel = conversation.mode == ConversationMode.novel && conversation.novel != null
        val isRp = conversation.mode == ConversationMode.rp && conversation.rp != null

        val activeEpisodes = episodes
            .filter { !it.stale && it.toSeq <= effectiveTipSeq }
            .sortedWith(compareByDescending<MemoryEpisode> { it.level }.thenBy { it.fromSeq })

        val summaryCap = max(400, (effectiveLimitTokens * 0.16).toInt())
        val tier1Episodes = ArrayList<MemoryEpisode>()
        val overflowEpisodes = ArrayList<MemoryEpisode>()
        var sumUsed = 0

        for (ep in activeEpisodes) {
            val tok = if (ep.estimatedTokens > 0) ep.estimatedTokens else EpisodeConsolidator.estimateEpisodeTokens(ep.title, ep.summary)
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

        val coreMem = buildCoreUserMemoryBlock(memories, memoryBudget)

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

        val loreTok = constantLore.sumOf { NovelEngine.estimateLoreTokens(it) }
        val chronicleTok = if (chronicleText.isNotBlank()) estimateTextTokens(chronicleText) else 0
        val sysTok = if (fullSystem.isNotBlank()) estimateTextTokens(fullSystem) + 12 else 0

        return SystemPrefixResult(
            systemText = fullSystem,
            systemTokens = sysTok,
            memoryTokens = coreMem.usedTokens,
            summaryTokens = chronicleTok,
            novelLoreTokens = loreTok,
            archivalUserMemories = coreMem.archivalMemories,
            overflowEpisodes = overflowEpisodes,
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
    ): TailRecallBundle {
        val isNovel = conversation.mode == ConversationMode.novel && conversation.novel != null
        val isRp = conversation.mode == ConversationMode.rp && conversation.rp != null

        val tailBudget = floor(effectiveLimitTokens * tailRecallShare(memoryBudget)).toInt()
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

        if (isRp) {
            val validLedger = memoryLedger.filter {
                TimelineReconciler.isItemValidAtTip(it.enabled, it.stale, it.validFromSeq, it.supersededAtSeq, effectiveTipSeq)
            }
            val activeLedger = validLedger.filter { it.pinned || it.locked || (!it.resolved && (it.kind == "promise" || it.kind == "quest")) }.take(6)
            val activeIds = activeLedger.map { it.id }.toSet()
            val restLedger = validLedger.filter { it.id !in activeIds }
            val ledgerCap = max(0, (tailBudget * 0.55).toInt())
            val recalledLedger = if (restLedger.isNotEmpty() && ledgerCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = restLedger.map { CjkSearch.ledgerItemToSearchCandidate(it) },
                    query = query,
                    options = ScoreMemoryOptions(effectiveTipSeq, ledgerCap, semanticScorer = semanticScorer),
                )
            } else {
                ScoreMemoryResult(emptyList(), emptyList(), 0)
            }

            val epCap = max(0, tailBudget - recalledLedger.usedTokens)
            val recalledEps = if (overflowEpisodes.isNotEmpty() && epCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = overflowEpisodes.map { CjkSearch.episodeToSearchCandidate(it) },
                    query = query,
                    options = ScoreMemoryOptions(effectiveTipSeq, epCap, semanticScorer = semanticScorer),
                )
            } else {
                ScoreMemoryResult(emptyList(), emptyList(), 0)
            }

            val inspect = buildList {
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
                        ),
                    )
                }
            }
            val activeLines = activeLedger.map { "- [${it.kind}] ${it.title}: ${it.content}" }
            val recalledLines = recalledLedger.selected.map { "- [${it.item.kind ?: "fact"}] ${it.item.title.orEmpty()}: ${it.item.content.orEmpty()}" }
            val epLines = recalledEps.selected.map { "- [${it.item.title.orEmpty()}] ${(it.item.summary ?: it.item.content).orEmpty()}" }
            val activeTok = activeLines.sumOf { estimateTextTokens(it) }
            val totalTok = activeTok + recalledLedger.usedTokens + recalledEps.usedTokens
            return TailRecallBundle(
                rpActiveLedgerLines = activeLines,
                rpRecalledMemoryLines = recalledLines,
                rpArchivedEpisodeLines = epLines,
                tailRecallTokens = totalTok,
                retrievedMemoryCount = inspect.size,
                recalledItems = inspect,
            )
        }

        if (isNovel) {
            val epCap = max(0, (tailBudget * 0.65).toInt())
            val epRes = if (overflowEpisodes.isNotEmpty() && epCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = overflowEpisodes.map { CjkSearch.episodeToSearchCandidate(it) },
                    query = query,
                    options = ScoreMemoryOptions(effectiveTipSeq, epCap, semanticScorer = semanticScorer),
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
                )
            }

            val crossModeCap = max(0, min(tailBudget - epRes.usedTokens, totalUserMemoryLimit(memoryBudget) - coreMemoryTokens))
            val archRes = if (archivalUserMemories.isNotEmpty() && crossModeCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = archivalUserMemories.map { CjkSearch.chatMemoryToSearchCandidate(it) },
                    query = query,
                    options = ScoreMemoryOptions(Int.MAX_VALUE, crossModeCap, semanticScorer = semanticScorer),
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
                )
            }
            val crossTok = if (crossBlock.isNotEmpty()) archRes.usedTokens + 16 else 0
            return TailRecallBundle(
                chatTailRecallBlock = crossBlock,
                chatRecallTokens = crossTok,
                novelArchivedEpisodeLines = epLines,
                tailRecallTokens = epRes.usedTokens + crossTok,
                retrievedMemoryCount = epInspect.size + crossInspect.size,
                recalledItems = epInspect + crossInspect,
            )
        }

        // Standard chat mode
        val sections = ArrayList<String>()
        val inspected = ArrayList<RecalledMemoryInspectItem>()
        var chatTokens = 0

        if (archivalUserMemories.isNotEmpty() && tailBudget > 0) {
            val epReserve = if (overflowEpisodes.isNotEmpty()) floor(tailBudget * 0.25).toInt() else 0
            val remMemLimit = max(0, totalUserMemoryLimit(memoryBudget) - coreMemoryTokens)
            val memCap = min(max(0, tailBudget - epReserve), remMemLimit)
            val archRes = if (memCap > 0) {
                CjkSearch.scoreMemoryCandidates(
                    candidates = archivalUserMemories.map { CjkSearch.chatMemoryToSearchCandidate(it) },
                    query = query,
                    options = ScoreMemoryOptions(Int.MAX_VALUE, memCap, semanticScorer = semanticScorer),
                )
            } else {
                ScoreMemoryResult(emptyList(), emptyList(), 0)
            }
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
                options = ScoreMemoryOptions(effectiveTipSeq, remForEps, semanticScorer = semanticScorer),
            )
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
        )
    }

    private data class ComputedPipeline(
        val active: List<ChatMessage>,
        val activeChapter: NovelChapter?,
        val novelLore: List<NovelLoreEntry>,
        val windowLimit: Int,
        val budgetTokens: Int,
        val effectiveLimitTokens: Int,
        val calibration: Double,
        val isNovel: Boolean,
        val isRp: Boolean,
        val loreMatch: LoreMatchResult,
        val prefix: SystemPrefixResult,
        val tailBundle: TailRecallBundle,
        val dynamicOverhead: Int,
        val waterline: ContextWaterlineSummary,
    )

    private fun runSharedPipeline(input: ContextBuildInput, allowScorer: Boolean): ComputedPipeline {
        val conv = input.conversation
        val slice = sliceActiveWindow(conv, input.messages, input.novelChapters)
        val windowLimit = max(4096, input.modelContextWindow)
        val budgetTokens = max(2048, (windowLimit * 0.80).toInt())
        val outputReserve = (conv.params.maxTokens ?: 2048).coerceIn(512, 8192)
        val effectiveLimitTokens = max(1024, budgetTokens - outputReserve)
        val calibration = if (conv.tokenCalibration in 0.4..2.5) conv.tokenCalibration else 1.0

        val isNovel = conv.mode == ConversationMode.novel && conv.novel != null
        val isRp = conv.mode == ConversationMode.rp && conv.rp != null
        val scorer = if (allowScorer) input.semanticScorer else null

        val loreMatch = when {
            isNovel -> NovelEngine.matchNovelLore(
                lore = input.novelLore,
                recentMessages = slice.active,
                draftText = input.draftText,
                chapterOutline = slice.activeChapter?.outline.orEmpty(),
                loreTokenBudget = conv.novel!!.loreTokenBudget,
                activeTipSeq = slice.effectiveTipSeq,
                presentEntities = conv.novel.currentStatus?.present.orEmpty(),
                sceneTitle = conv.novel.currentStatus?.location,
                semanticScorer = scorer,
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
                    semanticScorer = scorer,
                )
            }
            else -> LoreMatchResult(emptyList(), emptyList(), 0, 0)
        }

        val prefix = buildSystemPrefix(
            conversation = conv,
            memories = input.memories,
            memoryBudget = input.memoryBudget,
            novelChapters = input.novelChapters,
            constantLore = loreMatch.constant,
            episodes = input.memoryEpisodes,
            effectiveTipSeq = slice.effectiveTipSeq,
            effectiveLimitTokens = effectiveLimitTokens,
        )

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
            semanticScorer = scorer,
        )

        val dynamicOverhead = when {
            isNovel -> {
                val novelCfg = conv.novel!!
                loreMatch.triggeredTokens +
                    tailBundle.tailRecallTokens +
                    estimateTextTokens(novelCfg.authorNote) +
                    estimateTextTokens(slice.activeChapter?.outline.orEmpty()) +
                    (if (novelCfg.enableStatusHud) 90 else 0) +
                    (if (novelCfg.autoSyncLore) 110 else 0) +
                    (if (novelCfg.autoAdvanceChapter) 85 else 0) +
                    (if (novelCfg.enableChoices) 90 else 0)
            }
            isRp -> {
                val rpCfg = conv.rp!!
                180 + min(280, rpCfg.state.inventory.size * 12 + rpCfg.state.quests.size * 16 + rpCfg.cast.size * 20) +
                    loreMatch.triggeredTokens +
                    estimateTextTokens(rpCfg.authorNote) +
                    estimateTextTokens(rpCfg.postHistoryInstructions) +
                    tailBundle.tailRecallTokens
            }
            else -> tailBundle.tailRecallTokens
        }

        val msgCosts = slice.active.map { msg ->
            resolveMsgTokenCost(msg, msg.id in slice.decayedImageMessageIds)
        }
        val activeMsgsRawTotal = msgCosts.sum()
        val draftTokens = if (input.draftText.isNotBlank() || input.draftImageCount > 0) {
            estimateMessageTokens(input.draftText, input.draftImageCount)
        } else {
            0
        }
        val totalRaw = prefix.systemTokens + draftTokens + dynamicOverhead + activeMsgsRawTotal
        val calibratedTotal = (totalRaw * calibration).roundToInt()

        var running = ((prefix.systemTokens + draftTokens + dynamicOverhead) * calibration).roundToInt()
        var kept = 0
        for (i in slice.active.indices.reversed()) {
            val msgTok = (msgCosts[i] * calibration).roundToInt()
            if (kept > 0 && running + msgTok > effectiveLimitTokens) break
            running += msgTok
            kept++
        }

        val loreInspect = loreMatch.triggered.map { entry ->
            RecalledMemoryInspectItem(
                id = entry.id,
                title = entry.title,
                contentPreview = entry.content.take(80),
                category = entry.category,
                source = "novel_lore",
                score = loreMatch.triggeredScores[entry.id]?.let { (it * 1000).roundToInt() / 1000.0 },
                estimatedTokens = NovelEngine.estimateLoreTokens(entry),
                matchedTerms = loreMatch.triggeredMatchedTerms[entry.id].orEmpty(),
            )
        }
        val allRecalled = loreInspect + tailBundle.recalledItems
        val pct = min(100, ((calibratedTotal.toDouble() / max(1, effectiveLimitTokens)) * 100.0).roundToInt())

        val waterline = ContextWaterlineSummary(
            usedTokens = calibratedTotal,
            budgetTokens = budgetTokens,
            effectiveLimitTokens = effectiveLimitTokens,
            modelContextWindow = windowLimit,
            percentage = pct,
            exceedsModelWindow = calibratedTotal > windowLimit,
            activeMessageCount = slice.active.size,
            trimmedMessageCount = max(0, slice.active.size - kept),
            decayedImageMessageIds = slice.decayedImageMessageIds,
            memoryTokens = prefix.memoryTokens + tailBundle.chatRecallTokens,
            summaryTokens = prefix.summaryTokens,
            triggeredLoreIds = loreMatch.triggered.map { it.id },
            triggeredLoreTitles = loreMatch.triggered.map { it.title },
            novelLoreTokens = prefix.novelLoreTokens + loreMatch.triggeredTokens,
            retrievedMemoryCount = allRecalled.size,
            recalledMemoryTokens = tailBundle.tailRecallTokens,
            recalledItems = allRecalled,
        )

        return ComputedPipeline(
            active = slice.active,
            activeChapter = slice.activeChapter,
            novelLore = input.novelLore,
            windowLimit = windowLimit,
            budgetTokens = budgetTokens,
            effectiveLimitTokens = effectiveLimitTokens,
            calibration = calibration,
            isNovel = isNovel,
            isRp = isRp,
            loreMatch = loreMatch,
            prefix = prefix,
            tailBundle = tailBundle,
            dynamicOverhead = dynamicOverhead,
            waterline = waterline,
        )
    }

    private fun resolveMsgTokenCost(msg: ChatMessage, imageDecayed: Boolean): Int {
        val imgCount = if (msg.role == "user" && !imageDecayed) msg.attachmentIds.size else 0
        val base = estimateMessageTokens(msg.content, imgCount)
        return if (imageDecayed && msg.attachmentIds.isNotEmpty()) base + 12 else base
    }

    fun computeContextWaterline(input: ContextBuildInput): ContextWaterlineSummary =
        runSharedPipeline(input, allowScorer = false).waterline

    suspend fun buildChatContext(input: ContextBuildInput): BuiltChatContext {
        val pipe = runSharedPipeline(input, allowScorer = true)
        val conv = input.conversation
        val active = pipe.active
        val decayed = pipe.waterline.decayedImageMessageIds.toMutableSet()

        fun calcTotal(): Int {
            var tok = pipe.prefix.systemTokens + pipe.dynamicOverhead
            for (msg in active) {
                tok += resolveMsgTokenCost(msg, msg.id in decayed)
            }
            return (tok * pipe.calibration).roundToInt()
        }

        if (calcTotal() > pipe.effectiveLimitTokens) {
            for (i in 0 until max(0, active.size - 1)) {
                val msg = active[i]
                if (msg.attachmentIds.isNotEmpty() && msg.id !in decayed) {
                    decayed.add(msg.id)
                    if (calcTotal() <= pipe.effectiveLimitTokens) break
                }
            }
        }

        val selected = ArrayList<ChatMessage>()
        var used = ((pipe.prefix.systemTokens + pipe.dynamicOverhead) * pipe.calibration).roundToInt()
        for (i in active.indices.reversed()) {
            val msg = active[i]
            val msgTok = (resolveMsgTokenCost(msg, msg.id in decayed) * pipe.calibration).roundToInt()
            if (selected.isNotEmpty() && used + msgTok > pipe.effectiveLimitTokens) break
            used += msgTok
            selected.add(0, msg)
        }

        val wireMessages = ArrayList<WireChatMessage>()
        if (pipe.prefix.systemText.isNotBlank()) {
            wireMessages.add(WireChatMessage(role = "system", textContent = pipe.prefix.systemText))
        }

        val lastUserIdx = selected.indexOfLast { it.role == "user" }
        val existingLoreTitles = if (pipe.isNovel) pipe.novelLore.map { it.title } else emptyList()

        for (idx in selected.indices) {
            val msg = selected[idx]
            val rawContent = if (msg.role == "assistant") {
                when {
                    pipe.isNovel -> NovelExport.stripInternalTags(msg.content)
                    pipe.isRp -> RpEngine.stripRpInternalTags(msg.content)
                    else -> msg.content
                }
            } else {
                msg.content
            }

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
                        triggeredLore = pipe.loreMatch.triggered,
                        existingLoreTitles = existingLoreTitles,
                        archivedEpisodeLines = pipe.tailBundle.novelArchivedEpisodeLines,
                    )
                    if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) {
                        "${pipe.tailBundle.chatTailRecallBlock}\n\n$wrapped"
                    } else {
                        wrapped
                    }
                }
                isFinalRpUser -> RpEngine.wrapRpTurnUserMessage(
                    rawContent = rawContent,
                    inputKind = msg.rpInputKind ?: input.draftRpInputKind ?: RpInputKind.action,
                    rp = conv.rp!!,
                    diceRoll = msg.rpDiceRoll,
                    activeLedgerLines = pipe.tailBundle.rpActiveLedgerLines,
                    recalledMemoryLines = pipe.tailBundle.rpRecalledMemoryLines,
                    archivedEpisodeLines = pipe.tailBundle.rpArchivedEpisodeLines,
                    triggeredLore = pipe.loreMatch.triggered,
                )
                isFinalChatUser && pipe.tailBundle.chatTailRecallBlock.isNotBlank() -> {
                    if (rawContent.isNotBlank()) "${pipe.tailBundle.chatTailRecallBlock}\n\n$rawContent"
                    else pipe.tailBundle.chatTailRecallBlock
                }
                else -> rawContent
            }

            val attIds = msg.attachmentIds
            if (attIds.isNotEmpty() && msg.role == "user") {
                if (msg.id in decayed || input.resolveAttachmentDataUrl == null) {
                    val notice = "[${attIds.size} 张历史图片已省略]"
                    wireMessages.add(
                        WireChatMessage(
                            role = "user",
                            textContent = if (finalText.isNotBlank()) "$notice\n$finalText" else notice,
                        ),
                    )
                } else {
                    val parts = ArrayList<WireChatContentPart>()
                    if (finalText.isNotBlank()) {
                        parts.add(WireChatContentPart(type = "text", text = finalText))
                    }
                    for (id in attIds) {
                        val url = input.resolveAttachmentDataUrl.invoke(id)
                        if (!url.isNullOrBlank()) {
                            parts.add(WireChatContentPart(type = "image_url", imageUrl = url))
                        }
                    }
                    if (parts.isEmpty()) {
                        wireMessages.add(WireChatMessage(role = "user", textContent = finalText.ifBlank { "[图片]" }))
                    } else {
                        wireMessages.add(WireChatMessage(role = "user", parts = parts))
                    }
                }
            } else {
                wireMessages.add(WireChatMessage(role = msg.role, textContent = finalText))
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
                        triggeredLore = pipe.loreMatch.triggered,
                        existingLoreTitles = existingLoreTitles,
                        archivedEpisodeLines = pipe.tailBundle.novelArchivedEpisodeLines,
                    )
                    val content = if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) {
                        "${pipe.tailBundle.chatTailRecallBlock}\n\n$openingPrompt"
                    } else {
                        openingPrompt
                    }
                    wireMessages.add(WireChatMessage(role = "user", textContent = content))
                }
                pipe.isRp -> {
                    val openingRp = RpEngine.wrapRpTurnUserMessage(
                        rawContent = "请结合世界观设定、角色关系与当前场景状态，继续演绎本幕剧情。",
                        inputKind = input.draftRpInputKind ?: RpInputKind.action,
                        rp = conv.rp!!,
                        activeLedgerLines = pipe.tailBundle.rpActiveLedgerLines,
                        recalledMemoryLines = pipe.tailBundle.rpRecalledMemoryLines,
                        archivedEpisodeLines = pipe.tailBundle.rpArchivedEpisodeLines,
                        triggeredLore = pipe.loreMatch.triggered,
                    )
                    wireMessages.add(WireChatMessage(role = "user", textContent = openingRp))
                }
                pipe.tailBundle.chatTailRecallBlock.isNotBlank() -> {
                    wireMessages.add(WireChatMessage(role = "user", textContent = pipe.tailBundle.chatTailRecallBlock))
                }
            }
        }

        return BuiltChatContext(
            wireMessages = wireMessages,
            summary = pipe.waterline.copy(
                decayedImageMessageIds = decayed,
                trimmedMessageCount = max(0, active.size - selected.size),
            ),
        )
    }
}
