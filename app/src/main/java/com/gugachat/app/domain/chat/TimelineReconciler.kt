package com.gugachat.app.domain.chat

data class TimelineReconciliationResult(
    val messages: List<ChatMessage>,
    val ledgerItems: List<MemoryLedgerItem>,
    val loreEntries: List<NovelLoreEntry>,
    val episodes: List<MemoryEpisode>,
    val chapters: List<NovelChapter>,
    val newTipSeq: Int,
    val resetSummarizedUntilSeq: Int? = null,
    val shouldClearArchivedSummary: Boolean = false,
    val resetActiveWindowStartSeq: Boolean = false,
)

object TimelineReconciler {

    fun activeMessages(messages: List<ChatMessage>): List<ChatMessage> =
        messages
            .filter { it.deletedAt == null && it.supersededAtSeq == null }
            .sortedBy { it.seq }

    fun findActiveTipSeq(messages: List<ChatMessage>): Int =
        activeMessages(messages).maxOfOrNull { it.seq } ?: 0

    fun isItemValidAtTip(
        enabled: Boolean,
        stale: Boolean,
        validFromSeq: Int,
        supersededAtSeq: Int?,
        tipSeq: Int,
    ): Boolean = enabled && !stale && validFromSeq <= tipSeq && (supersededAtSeq == null || supersededAtSeq > tipSeq)

    /**
     * Reconciles branch messages and timeline-indexed memory structures when rolling back to,
     * editing at, or regenerating from `cutoffSeq`.
     *
     * When `inclusiveOfCutoff` is true, messages with `seq >= cutoffSeq` are marked superseded;
     * when false, messages with `seq > cutoffSeq` are marked superseded while keeping `cutoffSeq`.
     *
     * When rolling back past `summarizedUntilSeq` (or `activeWindowStartSeq`), computes clean
     * reset targets so compacted summaries never orphan or hide active history.
     */
    fun reconcileOnRollback(
        cutoffSeq: Int,
        inclusiveOfCutoff: Boolean,
        messages: List<ChatMessage>,
        ledgerItems: List<MemoryLedgerItem> = emptyList(),
        loreEntries: List<NovelLoreEntry> = emptyList(),
        episodes: List<MemoryEpisode> = emptyList(),
        chapters: List<NovelChapter> = emptyList(),
        summarizedUntilSeq: Int = 0,
        activeWindowStartSeq: Int? = null,
    ): TimelineReconciliationResult {
        val now = System.currentTimeMillis()
        val updatedMessages = messages.map { msg ->
            val hits = if (inclusiveOfCutoff) msg.seq >= cutoffSeq else msg.seq > cutoffSeq
            if (hits && msg.supersededAtSeq == null) {
                msg.copy(supersededAtSeq = cutoffSeq)
            } else {
                msg
            }
        }

        val updatedLedger = ledgerItems.map { item ->
            when {
                item.validFromSeq >= cutoffSeq && !item.locked -> item.copy(
                    stale = true,
                    supersededAtSeq = cutoffSeq,
                    updatedAt = now,
                )
                item.supersededAtSeq != null && item.supersededAtSeq >= cutoffSeq -> item.copy(
                    supersededAtSeq = null,
                    stale = false,
                    updatedAt = now,
                )
                else -> item
            }
        }

        val updatedLore = loreEntries.map { entry ->
            when {
                entry.validFromSeq >= cutoffSeq && !entry.alwaysActive -> entry.copy(
                    stale = true,
                    supersededAtSeq = cutoffSeq,
                    updatedAt = now,
                )
                entry.supersededAtSeq != null && entry.supersededAtSeq >= cutoffSeq -> entry.copy(
                    supersededAtSeq = null,
                    stale = false,
                    updatedAt = now,
                )
                else -> entry
            }
        }

        val updatedEpisodes = episodes.map { ep ->
            if (!ep.locked && ep.toSeq >= cutoffSeq) {
                ep.copy(stale = true, updatedAt = now)
            } else {
                ep
            }
        }

        val updatedChapters = chapters.map { ch ->
            when {
                ch.startSeq >= cutoffSeq -> ch.copy(
                    status = if (ch.order == 1) "writing" else "draft",
                    summary = "",
                    wordCount = 0,
                    endSeq = null,
                    updatedAt = now,
                )
                ch.endSeq != null && ch.endSeq >= cutoffSeq -> ch.copy(
                    status = "writing",
                    endSeq = null,
                    updatedAt = now,
                )
                else -> ch
            }
        }

        val newTipSeq = findActiveTipSeq(updatedMessages)
        val rolledPastSummary = summarizedUntilSeq > 0 && (
            newTipSeq < summarizedUntilSeq ||
                (inclusiveOfCutoff && cutoffSeq <= summarizedUntilSeq) ||
                (!inclusiveOfCutoff && cutoffSeq < summarizedUntilSeq)
            )
        val resetSummarizedUntilSeq: Int? = if (rolledPastSummary) {
            updatedEpisodes
                .filter { !it.stale && it.toSeq <= newTipSeq }
                .maxOfOrNull { it.toSeq } ?: 0
        } else {
            null
        }
        val shouldClearArchivedSummary = rolledPastSummary && (resetSummarizedUntilSeq ?: 0) == 0
        val resetActiveWindowStartSeq = activeWindowStartSeq != null && activeWindowStartSeq > newTipSeq

        return TimelineReconciliationResult(
            messages = updatedMessages,
            ledgerItems = updatedLedger,
            loreEntries = updatedLore,
            episodes = updatedEpisodes,
            chapters = updatedChapters,
            newTipSeq = newTipSeq,
            resetSummarizedUntilSeq = resetSummarizedUntilSeq,
            shouldClearArchivedSummary = shouldClearArchivedSummary,
            resetActiveWindowStartSeq = resetActiveWindowStartSeq,
        )
    }

    /**
     * Applies compaction and sliding-window rollback resets cleanly to a [ChatConversation].
     * Works both when `summarizedUntilSeq` was passed into [reconcileOnRollback] and when called
     * directly with the reconciliation output.
     */
    fun applyRollbackToConversation(
        conversation: ChatConversation,
        result: TimelineReconciliationResult,
    ): ChatConversation {
        val nextSummarized = result.resetSummarizedUntilSeq ?: if (conversation.summarizedUntilSeq > result.newTipSeq) {
            result.episodes
                .filter { !it.stale && it.toSeq <= result.newTipSeq }
                .maxOfOrNull { it.toSeq } ?: 0
        } else {
            conversation.summarizedUntilSeq
        }
        val clearSummary = result.shouldClearArchivedSummary ||
            (conversation.summarizedUntilSeq > result.newTipSeq && nextSummarized == 0)
        val resetStartSeq = result.resetActiveWindowStartSeq ||
            (conversation.params.activeWindowStartSeq != null && conversation.params.activeWindowStartSeq > result.newTipSeq)

        val nextParams = if (resetStartSeq) {
            conversation.params.copy(activeWindowStartSeq = null)
        } else {
            conversation.params
        }
        val nextArchived = if (clearSummary) "" else conversation.archivedSummary

        if (nextSummarized == conversation.summarizedUntilSeq &&
            nextArchived == conversation.archivedSummary &&
            nextParams == conversation.params
        ) {
            return conversation
        }
        return conversation.copy(
            summarizedUntilSeq = nextSummarized,
            archivedSummary = nextArchived,
            params = nextParams,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Marks messages as deleted cleanly while keeping monotonic `seq` indices stable.
     */
    fun softDeleteMessage(messageId: String, messages: List<ChatMessage>): List<ChatMessage> {
        val now = System.currentTimeMillis()
        return messages.map { msg ->
            if (msg.id == messageId) msg.copy(deletedAt = now) else msg
        }
    }
}
