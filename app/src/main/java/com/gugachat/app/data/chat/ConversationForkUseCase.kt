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
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpSaveSlot
import java.util.UUID

/**
 * Forks an existing conversation either from an arbitrary message cutoff ([branchFromMessage])
 * or from an RP [RpSaveSlot] ([branchFromSaveSlot]), cloning scoped messages, lore, ledger,
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
    ): ChatConversation? {
        val activeMsgs = com.gugachat.app.domain.chat.TimelineReconciler.activeMessages(
            chatDao.getMessagesForConversation(sourceConv.id).map { it.toDomain() },
        )
        val target = activeMsgs.firstOrNull { it.id == targetMsgId } ?: return null
        val newConvId = UUID.randomUUID().toString()

        val copiedMsgs = cloneMessages(activeMsgs.filter { it.seq <= target.seq }, newConvId)
        val existingChapters = chatDao.getNovelChaptersForConversation(sourceConv.id).map { it.toDomain() }
        val (clonedChapters, nextNovel) = buildBranchedChaptersAndNovel(
            sourceConv = sourceConv,
            existingChapters = existingChapters,
            copiedMsgs = copiedMsgs,
            cutoffSeq = target.seq,
            newConvId = newConvId,
            now = now,
        )

        val filteredEpisodes = chatDao.getMemoryEpisodesForConversation(sourceConv.id).map { it.toDomain() }
            .filter { !it.stale && it.toSeq <= target.seq }
        val filteredLore = chatDao.getNovelLoreForConversation(sourceConv.id).map { it.toDomain() }
            .filter { !it.stale && it.validFromSeq <= target.seq }
        val filteredLedger = chatDao.getMemoryLedgerForConversation(sourceConv.id).map { it.toDomain() }
            .filter { !it.stale && it.validFromSeq <= target.seq }

        val (forkedSummarizedUntil, forkedArchivedSummary) = computeForkedCompactionBounds(
            sourceConv = sourceConv,
            filteredEpisodes = filteredEpisodes,
            cutoffSeq = target.seq,
        )
        val forkedParams = if (sourceConv.params.activeWindowStartSeq != null &&
            sourceConv.params.activeWindowStartSeq > target.seq
        ) {
            sourceConv.params.copy(activeWindowStartSeq = null)
        } else {
            sourceConv.params
        }

        val forkedConv = sourceConv.copy(
            id = newConvId,
            title = "${sourceConv.title}$titleSuffix",
            params = forkedParams,
            novel = nextNovel,
            archivedSummary = forkedArchivedSummary,
            summarizedUntilSeq = forkedSummarizedUntil,
            createdAt = now,
            updatedAt = now,
            pinned = false,
        )

        persistForkedConversation(
            forkedConv = forkedConv,
            sourceConvId = sourceConv.id,
            msgs = copiedMsgs,
            chapters = clonedChapters,
            lore = cloneLore(filteredLore, newConvId),
            ledger = cloneLedger(filteredLedger, newConvId),
            episodes = cloneEpisodes(filteredEpisodes, newConvId),
        )
        return forkedConv
    }

    suspend fun branchFromRpSaveSlot(
        sourceConv: ChatConversation,
        slot: RpSaveSlot,
        now: Long = System.currentTimeMillis(),
    ): ChatConversation {
        val newConvId = UUID.randomUUID().toString()
        val forkedConv = sourceConv.copy(
            id = newConvId,
            title = "${sourceConv.title} · ${slot.name}",
            rp = slot.rpConfig,
            createdAt = now,
            updatedAt = now,
            pinned = false,
        )
        val currentLore = chatDao.getNovelLoreForConversation(sourceConv.id).map { it.toDomain() }
        persistForkedConversation(
            forkedConv = forkedConv,
            sourceConvId = sourceConv.id,
            msgs = cloneMessages(slot.activeMessagesSnapshot, newConvId),
            chapters = emptyList(),
            lore = cloneLore(currentLore, newConvId),
            ledger = cloneLedger(slot.ledgerItems, newConvId),
            episodes = cloneEpisodes(slot.episodes, newConvId),
        )
        return forkedConv
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

        internal fun buildBranchedChaptersAndNovel(
            sourceConv: ChatConversation,
            existingChapters: List<NovelChapter>,
            copiedMsgs: List<ChatMessage>,
            cutoffSeq: Int,
            newConvId: String,
            now: Long,
        ): Pair<List<NovelChapter>, com.gugachat.app.domain.chat.NovelConfig?> {
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
