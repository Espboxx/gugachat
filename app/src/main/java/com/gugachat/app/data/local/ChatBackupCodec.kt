package com.gugachat.app.data.local

import androidx.room.withTransaction
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.UserMemory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class GuGuChatBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val conversations: List<ChatConversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val userMemories: List<UserMemory> = emptyList(),
    val novelChapters: List<NovelChapter> = emptyList(),
    val novelLore: List<NovelLoreEntry> = emptyList(),
    val memoryLedger: List<MemoryLedgerItem> = emptyList(),
    val memoryEpisodes: List<MemoryEpisode> = emptyList(),
    val rpSaveSlots: List<RpSaveSlot> = emptyList(),
)

data class BackupImportResult(
    val conversationsCount: Int,
    val messagesCount: Int,
    val memoriesCount: Int,
)

object ChatBackupCodec {
    suspend fun exportAll(db: AppDatabase): String {
        val chatDao = db.chatDao()
        val memDao = db.userMemoryDao()
        val payload = GuGuChatBackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            conversations = chatDao.getAllConversations().map { it.toDomain() },
            messages = chatDao.getAllMessages().map { it.toDomain() },
            userMemories = memDao.getAll().map { it.toDomain() },
            novelChapters = chatDao.getAllNovelChapters().map { it.toDomain() },
            novelLore = chatDao.getAllNovelLore().map { it.toDomain() },
            memoryLedger = chatDao.getAllMemoryLedger().map { it.toDomain() },
            memoryEpisodes = chatDao.getAllMemoryEpisodes().map { it.toDomain() },
            rpSaveSlots = chatDao.getAllRpSaveSlots().map { it.toDomain() },
        )
        return AppJson.encodeToString(payload)
    }

    suspend fun importJson(
        db: AppDatabase,
        rawJson: String,
        replaceExisting: Boolean = false,
    ): BackupImportResult {
        val payload = AppJson.decodeFromString(GuGuChatBackupPayload.serializer(), rawJson.trim())
        val chatDao = db.chatDao()
        val memDao = db.userMemoryDao()

        db.withTransaction {
            if (replaceExisting) {
                chatDao.clearAllRpSaveSlots()
                chatDao.clearAllMemoryEpisodes()
                chatDao.clearAllMemoryLedger()
                chatDao.clearAllNovelLore()
                chatDao.clearAllNovelChapters()
                chatDao.clearAllMessages()
                chatDao.clearAllConversations()
                memDao.clearAll()
            }
            if (payload.conversations.isNotEmpty()) {
                chatDao.upsertConversations(payload.conversations.map { it.toEntity() })
            }
            if (payload.messages.isNotEmpty()) {
                chatDao.upsertMessages(payload.messages.map { it.toEntity() })
            }
            if (payload.userMemories.isNotEmpty()) {
                memDao.upsertAll(payload.userMemories.map { it.toEntity() })
            }
            if (payload.novelChapters.isNotEmpty()) {
                chatDao.upsertNovelChapters(payload.novelChapters.map { it.toEntity() })
            }
            if (payload.novelLore.isNotEmpty()) {
                chatDao.upsertNovelLoreList(payload.novelLore.map { it.toEntity() })
            }
            if (payload.memoryLedger.isNotEmpty()) {
                chatDao.upsertMemoryLedgerList(payload.memoryLedger.map { it.toEntity() })
            }
            if (payload.memoryEpisodes.isNotEmpty()) {
                chatDao.upsertMemoryEpisodes(payload.memoryEpisodes.map { it.toEntity() })
            }
            if (payload.rpSaveSlots.isNotEmpty()) {
                chatDao.upsertRpSaveSlots(payload.rpSaveSlots.map { it.toEntity() })
            }
        }

        return BackupImportResult(
            conversationsCount = payload.conversations.size,
            messagesCount = payload.messages.size,
            memoriesCount = payload.userMemories.size,
        )
    }
}
