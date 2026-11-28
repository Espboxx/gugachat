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

    fun resolveFileRetentionModes(
        allActiveMessages: List<ChatMessage>,
        activeWindowMessages: List<ChatMessage>,
        attachmentsById: Map<String, ChatAttachmentMeta>,
        effectiveLimitTokens: Int,
    ): Map<String, FileRetentionTier> {
        if (allActiveMessages.isEmpty() || attachmentsById.isEmpty()) return emptyMap()
        val activeMsgIds = activeWindowMessages.map { it.id }.toSet()
        val fullInlineThreshold = FileContextBudgeter.computeFullInlineThreshold(effectiveLimitTokens)
        val hardFullCap = min(
            FileContextBudgeter.HARD_MAX_FILE_TURN_TOKENS,
            (max(4096, effectiveLimitTokens) * FileContextBudgeter.MAX_FILE_WINDOW_SHARE).toInt(),
        )

        val retention = LinkedHashMap<String, FileRetentionTier>()
        for (msg in allActiveMessages) {
            if (msg.role != "user" || msg.attachmentIds.isEmpty()) continue
            val inActiveWindow = msg.id in activeMsgIds
            for (attId in msg.attachmentIds) {
                val meta = attachmentsById[attId]?.takeIf { it.isFile } ?: continue
                val tier = if (!inActiveWindow) {
                    // Compacted or slid-out upload turn -> keep dynamically recallable in Tier 2
                    FileRetentionTier.INDEX_RECALL
                } else {
                    when (meta.injectMode) {
                        FileInjectMode.retrieve, FileInjectMode.head_tail -> FileRetentionTier.INDEX_RECALL
                        FileInjectMode.full -> {
                            if (meta.estimatedTokens <= hardFullCap) {
                                FileRetentionTier.FULL
                            } else {
                                FileRetentionTier.INDEX_RECALL
                            }
                        }
                        FileInjectMode.auto -> {
                            if (meta.estimatedTokens <= fullInlineThreshold) {
                                FileRetentionTier.FULL
                            } else {
                                FileRetentionTier.INDEX_RECALL
                            }
                        }
                    }
                }
                retention[attId] = tier
            }
        }
        return retention
    }

    fun sliceActiveWindow(
        conversation: ChatConversation,
        messages: List<ChatMessage>,
        novelChapters: List<NovelChapter>,
        attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
        effectiveLimitTokens: Int = 100_000,
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

        val fileRetention = resolveFileRetentionModes(
            allActiveMessages = allActive,
            activeWindowMessages = active,
            attachmentsById = attachmentsById,
            effectiveLimitTokens = effectiveLimitTokens,
        )

        val decayedFiles = active
            .filter { msg ->
                msg.role == "user" && msg.attachmentIds.any { id ->
                    attachmentsById[id]?.isFile == true && fileRetention[id] == FileRetentionTier.INDEX_RECALL
                }
            }
            .map { it.id }
            .toSet()

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
            fileRetentionByAttachmentId = fileRetention,
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
        fileRetentionByAttachmentId: Map<String, FileRetentionTier> = emptyMap(),
    ): Int {
        if (msg.role != "user" || msg.attachmentIds.isEmpty()) return 0
        val files = msg.attachmentIds.mapNotNull { attachmentsById[it]?.takeIf { a -> a.isFile } }
        if (files.isEmpty()) return 0
        var fullRawSum = 0
        var anchorSum = 0
        for (f in files) {
            val tier = fileRetentionByAttachmentId[f.id]
                ?: if (fileDecayed) FileRetentionTier.INDEX_RECALL else FileRetentionTier.FULL
            if (tier == FileRetentionTier.INDEX_RECALL) {
                anchorSum += FileContextBudgeter.INDEX_ANCHOR_ESTIMATED_TOKENS
            } else {
                fullRawSum += max(32, f.estimatedTokens) + 48
            }
        }
        return min(fullRawSum, perTurnFileBudget) + anchorSum
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
