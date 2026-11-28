package com.gugachat.app.data.chat

import androidx.room.withTransaction
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.TimelineReconciler
import com.gugachat.app.domain.chat.activeVariant
import java.util.UUID

data class ConversationForkResult(
    val conversation: ChatConversation,
    val prefillUserMessage: ChatMessage? = null,
)

internal data class PlannedConversationFork(
    val conversation: ChatConversation,
    val messages: List<ChatMessage>,
    val chapters: List<NovelChapter>,
    val lore: List<NovelLoreEntry>,
    val ledger: List<MemoryLedgerItem>,
    val episodes: List<MemoryEpisode>,
    val prefillUserMessage: ChatMessage? = null,
)

/**
 * Forks an existing conversation either from an arbitrary message cutoff ([branchFromMessage])
 * or from an RP [RpSaveSlot] ([branchFromRpSaveSlot]), cloning scoped messages, lore, ledger,
 * episodes, chapters, and mounted worldbooks inside a single Room transaction.
 */
class ConversationForkUseCase(
    private val database: AppDatabase,
) {
    private val chatDao = database.chatDao()
    private val assetDao = database.assetDao()

    suspend fun branchFromMessage(
        sourceConv: ChatConversation,
        targetMsgId: String,
        titleSuffix: String = " (分支)",
        now: Long = System.currentTimeMillis(),
    ): ConversationForkResult? {
        val freshSource = chatDao.getConversationById(sourceConv.id)?.toDomain() ?: sourceConv
        val plan = planBranchFromMessage(
            sourceConv = freshSource,
            targetMsgId = targetMsgId,
            allMessages = chatDao.getMessagesForConversation(freshSource.id).map { it.toDomain() },
            existingChapters = chatDao.getNovelChaptersForConversation(freshSource.id).map { it.toDomain() },
            existingEpisodes = chatDao.getMemoryEpisodesForConversation(freshSource.id).map { it.toDomain() },
            existingLore = chatDao.getNovelLoreForConversation(freshSource.id).map { it.toDomain() },
            existingLedger = chatDao.getMemoryLedgerForConversation(freshSource.id).map { it.toDomain() },
            titleSuffix = titleSuffix,
            now = now,
        ) ?: return null

        persistForkedConversation(
            forkedConv = plan.conversation,
            sourceConvId = freshSource.id,
            msgs = plan.messages,
            chapters = plan.chapters,
            lore = plan.lore,
            ledger = plan.ledger,
            episodes = plan.episodes,
        )
        return ConversationForkResult(
            conversation = plan.conversation,
            prefillUserMessage = plan.prefillUserMessage,
        )
    }

    suspend fun branchFromRpSaveSlot(
        sourceConv: ChatConversation,
        slot: RpSaveSlot,
        now: Long = System.currentTimeMillis(),
    ): ChatConversation {
        val freshSource = chatDao.getConversationById(sourceConv.id)?.toDomain() ?: sourceConv
        val currentLore = chatDao.getNovelLoreForConversation(freshSource.id).map { it.toDomain() }
        val plan = planBranchFromRpSaveSlot(
            sourceConv = freshSource,
            slot = slot,
            existingLore = currentLore,
            now = now,
        )
        persistForkedConversation(
            forkedConv = plan.conversation,
            sourceConvId = freshSource.id,
            msgs = plan.messages,
            chapters = plan.chapters,
            lore = plan.lore,
            ledger = plan.ledger,
            episodes = plan.episodes,
        )
        return plan.conversation
    }

    private suspend fun persistForkedConversation(
        forkedConv: ChatConversation,
        sourceConvId: String,
        msgs: List<ChatMessage>,
        chapters: List<NovelChapter>,
        lore: List<NovelLoreEntry>,
        ledger: List<MemoryLedgerItem>,
        episodes: List<MemoryEpisode>,
    ) {
        val mountedRefs = assetDao.getMountedWorldbookRefs(sourceConvId)
        database.withTransaction {
            chatDao.upsertConversation(forkedConv.toEntity())
            if (msgs.isNotEmpty()) {
                chatDao.upsertMessages(msgs.map { it.toEntity() })
            }
            if (chapters.isNotEmpty()) {
                chatDao.upsertNovelChapters(chapters.map { it.toEntity() })
            }
            if (lore.isNotEmpty()) {
                chatDao.upsertNovelLoreList(lore.map { it.toEntity() })
            }
            if (ledger.isNotEmpty()) {
                chatDao.upsertMemoryLedgerList(ledger.map { it.toEntity() })
            }
            if (episodes.isNotEmpty()) {
                chatDao.upsertMemoryEpisodes(episodes.map { it.toEntity() })
            }
            if (mountedRefs.isNotEmpty()) {
                assetDao.replaceMountedWorldbooks(
                    forkedConv.id,
                    mountedRefs.map { it.copy(conversationId = forkedConv.id) },
                )
            }
        }
    }

    companion object {
        internal fun cloneMessages(msgs: List<ChatMessage>, newConvId: String): List<ChatMessage> =
            msgs.map { it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId) }

        internal fun cloneLore(entries: List<NovelLoreEntry>, newConvId: String): List<NovelLoreEntry> =
            entries.map { it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId) }

        internal fun cloneLedger(items: List<MemoryLedgerItem>, newConvId: String): List<MemoryLedgerItem> =
            items.map { it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId) }

        internal fun cloneEpisodes(episodes: List<MemoryEpisode>, newConvId: String): List<MemoryEpisode> =
            episodes.map { it.copy(id = UUID.randomUUID().toString(), conversationId = newConvId) }

        internal fun restoreModeStateAtCutoff(
            sourceConv: ChatConversation,
            keptMsgs: List<ChatMessage>,
            nextNovel: NovelConfig? = sourceConv.novel,
        ): Pair<RpConfig?, NovelConfig?> {
            val lastAssistant = keptMsgs.lastOrNull { it.role == "assistant" }
            val activeVar = lastAssistant?.activeVariant()

            val restoredRp = sourceConv.rp?.let { rp ->
                val restoredChoices = if (lastAssistant != null) {
                    activeVar?.rpChoices?.ifEmpty { lastAssistant.rpChoices } ?: lastAssistant.rpChoices
                } else {
                    emptyList()
                }
                rp.copy(
                    state = activeVar?.rpStateSnapshot ?: rp.state,
                    cast = activeVar?.rpCastSnapshot ?: rp.cast,
                    pendingChoices = restoredChoices,
                )
            }

            val restoredNovel = nextNovel?.let { novel ->
                val restoredChoices = if (lastAssistant != null) {
                    activeVar?.novelChoices?.ifEmpty { lastAssistant.novelChoices } ?: lastAssistant.novelChoices
                } else {
                    emptyList()
                }
                novel.copy(
                    currentStatus = activeVar?.novelStatusSnapshot ?: novel.currentStatus,
                    pendingChoices = restoredChoices,
                )
            }

            return restoredRp to restoredNovel
        }

        internal fun planBranchFromMessage(
            sourceConv: ChatConversation,
            targetMsgId: String,
            allMessages: List<ChatMessage>,
            existingChapters: List<NovelChapter> = emptyList(),
            existingEpisodes: List<MemoryEpisode> = emptyList(),
            existingLore: List<NovelLoreEntry> = emptyList(),
            existingLedger: List<MemoryLedgerItem> = emptyList(),
            titleSuffix: String = " (分支)",
            now: Long = System.currentTimeMillis(),
            newConvId: String = UUID.randomUUID().toString(),
        ): PlannedConversationFork? {
            val activeMsgs = TimelineReconciler.activeMessages(allMessages)
                .filter { it.status != "streaming" }
            val target = activeMsgs.firstOrNull { it.id == targetMsgId } ?: return null

            val isUserTarget = target.role == "user"
            val keptMsgs = if (isUserTarget) {
                activeMsgs.filter { it.seq < target.seq }
            } else {
                activeMsgs.filter { it.seq <= target.seq }
            }
            val cutoffSeq = keptMsgs.maxOfOrNull { it.seq } ?: 0
            val maxActiveSeq = activeMsgs.maxOfOrNull { it.seq } ?: 0

            val copiedMsgs = cloneMessages(keptMsgs, newConvId)
            val (clonedChapters, branchedNovel) = buildBranchedChaptersAndNovel(
                sourceConv = sourceConv,
                existingChapters = existingChapters,
                copiedMsgs = copiedMsgs,
                cutoffSeq = cutoffSeq,
                newConvId = newConvId,
                now = now,
            )

            val filteredEpisodes = existingEpisodes.filter { !it.stale && it.toSeq <= cutoffSeq }
            val filteredLore = existingLore.filter { !it.stale && it.validFromSeq <= cutoffSeq }
            val filteredLedger = existingLedger.filter { !it.stale && it.validFromSeq <= cutoffSeq }

            val (forkedSummarizedUntil, forkedArchivedSummary) = computeForkedCompactionBounds(
                sourceConv = sourceConv,
                filteredEpisodes = filteredEpisodes,
                cutoffSeq = cutoffSeq,
            )
            val forkedParams = if (sourceConv.params.activeWindowStartSeq != null &&
                sourceConv.params.activeWindowStartSeq > cutoffSeq
            ) {
                sourceConv.params.copy(activeWindowStartSeq = null)
            } else {
                sourceConv.params
            }
            val (restoredRp, restoredNovel) = restoreModeStateAtCutoff(
                sourceConv = sourceConv,
                keptMsgs = keptMsgs,
                nextNovel = branchedNovel,
            )
            val forkedLoreTimedStates = if (cutoffSeq < maxActiveSeq) "{}" else sourceConv.loreTimedStatesJson

            val forkedConv = sourceConv.copy(
                id = newConvId,
                title = "${sourceConv.title}$titleSuffix",
                params = forkedParams,
                rp = restoredRp,
                novel = restoredNovel,
                archivedSummary = forkedArchivedSummary,
                summarizedUntilSeq = forkedSummarizedUntil,
                loreTimedStatesJson = forkedLoreTimedStates,
                createdAt = now,
                updatedAt = now,
                pinned = false,
            )

            return PlannedConversationFork(
                conversation = forkedConv,
                messages = copiedMsgs,
                chapters = clonedChapters,
                lore = cloneLore(filteredLore, newConvId),
                ledger = cloneLedger(filteredLedger, newConvId),
                episodes = cloneEpisodes(filteredEpisodes, newConvId),
                prefillUserMessage = if (isUserTarget) target else null,
            )
        }

        internal fun planBranchFromRpSaveSlot(
            sourceConv: ChatConversation,
            slot: RpSaveSlot,
            existingLore: List<NovelLoreEntry> = emptyList(),
            now: Long = System.currentTimeMillis(),
            newConvId: String = UUID.randomUUID().toString(),
        ): PlannedConversationFork {
            val cutoffSeq = slot.messageSeq
            val cleanMessages = TimelineReconciler.activeMessages(slot.activeMessagesSnapshot)
                .filter { it.status != "streaming" }
            val cleanLedger = slot.ledgerItems.filter { !it.stale }
            val cleanEpisodes = slot.episodes.filter { !it.stale }
            val filteredLore = existingLore.filter { !it.stale && it.validFromSeq <= cutoffSeq }

            val (forkedSummarizedUntil, forkedArchivedSummary) = computeForkedCompactionBounds(
                sourceConv = sourceConv,
                filteredEpisodes = cleanEpisodes,
                cutoffSeq = cutoffSeq,
            )
            val forkedParams = if (sourceConv.params.activeWindowStartSeq != null &&
                sourceConv.params.activeWindowStartSeq > cutoffSeq
            ) {
                sourceConv.params.copy(activeWindowStartSeq = null)
            } else {
                sourceConv.params
            }

            val forkedConv = sourceConv.copy(
                id = newConvId,
                title = "${sourceConv.title} · ${slot.name}",
                params = forkedParams,
                rp = slot.rpConfig,
                archivedSummary = forkedArchivedSummary,
                summarizedUntilSeq = forkedSummarizedUntil,
                loreTimedStatesJson = "{}",
                createdAt = now,
                updatedAt = now,
                pinned = false,
            )

            return PlannedConversationFork(
                conversation = forkedConv,
                messages = cloneMessages(cleanMessages, newConvId),
                chapters = emptyList(),
                lore = cloneLore(filteredLore, newConvId),
                ledger = cloneLedger(cleanLedger, newConvId),
                episodes = cloneEpisodes(cleanEpisodes, newConvId),
                prefillUserMessage = null,
            )
        }

        internal fun buildBranchedChaptersAndNovel(
            sourceConv: ChatConversation,
            existingChapters: List<NovelChapter>,
            copiedMsgs: List<ChatMessage>,
            cutoffSeq: Int,
            newConvId: String,
            now: Long,
        ): Pair<List<NovelChapter>, NovelConfig?> {
            val chapterIdMap = HashMap<String, String>()
            val clonedRaw = existingChapters
                .filter { it.startSeq <= cutoffSeq || it.order == 1 }
                .sortedBy { it.order }
                .mapIndexed { idx, ch ->
                    val newChId = UUID.randomUUID().toString()
                    chapterIdMap[ch.id] = newChId
                    val truncatedAfterCutoff = ch.endSeq != null && ch.endSeq > cutoffSeq
                    ch.copy(
                        id = newChId,
                        conversationId = newConvId,
                        order = idx + 1,
                        status = if (truncatedAfterCutoff) "writing" else ch.status,
                        endSeq = if (truncatedAfterCutoff) null else ch.endSeq,
                        updatedAt = now,
                    )
                }
            val recalculated = NovelEngine.recalculateChapterWordCounts(clonedRaw, copiedMsgs)
            val nextNovel = sourceConv.novel?.let { n ->
                val remappedActive = n.activeChapterId?.let { chapterIdMap[it] } ?: recalculated.lastOrNull()?.id
                n.copy(activeChapterId = remappedActive)
            }
            return recalculated to nextNovel
        }

        internal fun computeForkedCompactionBounds(
            sourceConv: ChatConversation,
            filteredEpisodes: List<MemoryEpisode>,
            cutoffSeq: Int,
        ): Pair<Int, String> {
            val forkedSummarizedUntil = if (sourceConv.summarizedUntilSeq > cutoffSeq) {
                filteredEpisodes.maxOfOrNull { it.toSeq } ?: 0
            } else {
                sourceConv.summarizedUntilSeq
            }
            val forkedArchivedSummary = if (forkedSummarizedUntil == 0 && sourceConv.summarizedUntilSeq > cutoffSeq) {
                ""
            } else {
                sourceConv.archivedSummary
            }
            return forkedSummarizedUntil to forkedArchivedSummary
        }
    }
}
