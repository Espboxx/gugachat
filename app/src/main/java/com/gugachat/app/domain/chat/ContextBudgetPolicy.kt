package com.gugachat.app.domain.chat

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object ContextBudgetPolicy {
    const val SLIDE_STRIDE = 4
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

    fun mutedIdsForSource(mutedKeys: Set<String>, source: String): Set<String> {
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

    fun sliceActiveWindow(
        conversation: ChatConversation,
        messages: List<ChatMessage>,
        novelChapters: List<NovelChapter>,
        attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
    ): ActiveWindowSlice {
        val allActive = TimelineReconciler.activeMessages(messages)
        val tipSeq = allActive.maxOfOrNull { it.seq } ?: 0

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

    fun computeSlidingWindowKeepCount(
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

    fun resolveMsgFileTokens(
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

    fun cleanAssistantHistoryText(msg: ChatMessage, conversation: ChatConversation): String {
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

    fun resolveMsgTokenCost(
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
}
