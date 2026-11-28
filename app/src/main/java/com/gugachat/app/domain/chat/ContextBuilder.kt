package com.gugachat.app.domain.chat

import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity

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
    val preloadedFileChunks: Map<String, IndexedDocument> = emptyMap(),
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

data class ActiveWindowSlice(
    val active: List<ChatMessage>,
    val effectiveTipSeq: Int,
    val decayedImageMessageIds: Set<String>,
    val decayedFileMessageIds: Set<String>,
    val activeChapter: NovelChapter?,
    val allActiveMessages: List<ChatMessage> = active,
    val uncompactedExcludedCount: Int = 0,
    val fileRetentionByAttachmentId: Map<String, FileRetentionTier> = emptyMap(),
)

object ContextBuilder {
    const val SLIDE_STRIDE = ContextBudgetPolicy.SLIDE_STRIDE

    fun estimateTextTokens(text: String, modelId: String? = null): Int =
        ContextBudgetPolicy.estimateTextTokens(text, modelId)

    fun estimateMessageTokens(text: String, imageCount: Int = 0, modelId: String? = null): Int =
        ContextBudgetPolicy.estimateMessageTokens(text, imageCount, modelId)

    fun coreMemoryLimit(budget: MemoryBudget): Int =
        ContextBudgetPolicy.coreMemoryLimit(budget)

    fun totalUserMemoryLimit(budget: MemoryBudget): Int =
        ContextBudgetPolicy.totalUserMemoryLimit(budget)

    fun tailRecallShare(budget: MemoryBudget): Double =
        ContextBudgetPolicy.tailRecallShare(budget)

    fun tailRecallPresetCap(budget: MemoryBudget): Int =
        ContextBudgetPolicy.tailRecallPresetCap(budget)

    fun topKRecallCap(budget: MemoryBudget): Int =
        ContextBudgetPolicy.topKRecallCap(budget)

    fun computeTailBudget(effectiveLimitTokens: Int, budget: MemoryBudget): Int =
        ContextBudgetPolicy.computeTailBudget(effectiveLimitTokens, budget)

    fun computeSummaryCap(effectiveLimitTokens: Int): Int =
        ContextBudgetPolicy.computeSummaryCap(effectiveLimitTokens)

    fun buildCoreUserMemoryBlock(
        memories: List<UserMemory>,
        budget: MemoryBudget,
        modelId: String? = null,
    ): CoreMemoryBlockResult =
        ContextZonePrefixBuilder.buildCoreUserMemoryBlock(memories, budget, modelId)

    fun sliceActiveWindow(
        conversation: ChatConversation,
        messages: List<ChatMessage>,
        novelChapters: List<NovelChapter>,
        attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
        effectiveLimitTokens: Int = 100_000,
    ): ActiveWindowSlice =
        ContextBudgetPolicy.sliceActiveWindow(
            conversation = conversation,
            messages = messages,
            novelChapters = novelChapters,
            attachmentsById = attachmentsById,
            effectiveLimitTokens = effectiveLimitTokens,
        )

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
    ): SystemPrefixResult = ContextZonePrefixBuilder.buildSystemPrefix(
        conversation = conversation,
        memories = memories,
        memoryBudget = memoryBudget,
        novelChapters = novelChapters,
        constantLore = constantLore,
        episodes = episodes,
        effectiveTipSeq = effectiveTipSeq,
        effectiveLimitTokens = effectiveLimitTokens,
        modelId = modelId,
    )

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
    ): TailRecallBundle = ContextRecallAssembler.buildTailRecallBundle(
        conversation = conversation,
        active = active,
        draftText = draftText,
        memoryBudget = memoryBudget,
        coreMemoryTokens = coreMemoryTokens,
        effectiveLimitTokens = effectiveLimitTokens,
        effectiveTipSeq = effectiveTipSeq,
        archivalUserMemories = archivalUserMemories,
        memoryLedger = memoryLedger,
        overflowEpisodes = overflowEpisodes,
        activeChapter = activeChapter,
        semanticScorer = semanticScorer,
        semanticScores = semanticScores,
        modelId = modelId,
        mutedRecallKeys = mutedRecallKeys,
        minSemanticCosine = minSemanticCosine,
    )

    internal fun expandStaticMacros(text: String, charName: String, userName: String): String =
        ContextZonePrefixBuilder.expandStaticMacros(text, charName, userName)

    internal fun computeSlidingWindowKeepCount(
        activeSize: Int,
        rawKeptCount: Int,
        maxHistoryLimit: Int?,
    ): Int = ContextBudgetPolicy.computeSlidingWindowKeepCount(activeSize, rawKeptCount, maxHistoryLimit)

    internal fun cleanAssistantHistoryText(msg: ChatMessage, conversation: ChatConversation): String =
        ContextBudgetPolicy.cleanAssistantHistoryText(msg, conversation)

    fun computeContextWaterline(input: ContextBuildInput): ContextWaterlineSummary =
        ContextRecallAssembler.runSharedPipeline(input, allowScorer = false).waterline

    suspend fun buildChatContext(input: ContextBuildInput): BuiltChatContext =
        buildChatContextV4(input).built

    suspend fun buildChatContextV4(input: ContextBuildInput): BuiltChatContextV4 =
        ContextDepthInjector.buildChatContextV4(input)
}
