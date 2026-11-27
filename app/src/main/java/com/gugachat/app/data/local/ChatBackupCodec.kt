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
import kotlinx.serialization.json.JsonObject

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
    fun decodePayload(rawJson: String): GuGuChatBackupPayload {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        val rootObj = runCatching { AppJson.parseToJsonElement(cleaned) as? JsonObject }.getOrNull()
            ?: throw IllegalArgumentException("无效的 GuGuChat 本地备份 JSON 格式")
        val hasKnownSection = listOf(
            "conversations",
            "messages",
            "userMemories",
            "novelChapters",
            "novelLore",
            "memoryLedger",
            "memoryEpisodes",
            "rpSaveSlots",
        ).any { rootObj.containsKey(it) }
        if (!hasKnownSection) {
            throw IllegalArgumentException("该文件不是 GuGuChat 本地备份（外部软件导出请使用「导入外部对话 / 剧本」）")
        }
        return AppJson.decodeFromString(GuGuChatBackupPayload.serializer(), cleaned)
    }

    internal suspend fun restorePayloadInTransaction(
        chatDao: ChatDao,
        memDao: UserMemoryDao,
        payload: GuGuChatBackupPayload,
        replaceExisting: Boolean,
    ) {
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

    suspend fun importJson(
        db: AppDatabase,
        rawJson: String,
        replaceExisting: Boolean = false,
    ): BackupImportResult {
        val payload = decodePayload(rawJson)
        val chatDao = db.chatDao()
        val memDao = db.userMemoryDao()

        db.withTransaction {
            restorePayloadInTransaction(chatDao, memDao, payload, replaceExisting)
        }

        return BackupImportResult(
            conversationsCount = payload.conversations.size,
            messagesCount = payload.messages.size,
            memoriesCount = payload.userMemories.size,
        )
    }
}
