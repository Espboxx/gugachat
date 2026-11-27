package com.gugachat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getConversationsPaged(limit: Int, offset: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(entity: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(entities: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY seq ASC, createdAt ASC")
    fun observeMessages(convId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY seq ASC, createdAt ASC")
    suspend fun getMessagesForConversation(convId: String): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY conversationId, seq ASC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY conversationId ASC, seq ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPaged(limit: Int, offset: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(entity: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(entities: List<MessageEntity>)

    @Query("UPDATE messages SET status = 'aborted' WHERE status = 'streaming'")
    suspend fun abortOrphanStreamingMessages()

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun deleteMessagesForConversation(convId: String)

    @Query("SELECT * FROM novel_chapters WHERE conversationId = :convId ORDER BY sortOrder ASC")
    fun observeNovelChapters(convId: String): Flow<List<NovelChapterEntity>>

    @Query("SELECT * FROM novel_chapters WHERE conversationId = :convId ORDER BY sortOrder ASC")
    suspend fun getNovelChaptersForConversation(convId: String): List<NovelChapterEntity>

    @Query("SELECT * FROM novel_chapters ORDER BY conversationId, sortOrder ASC")
    suspend fun getAllNovelChapters(): List<NovelChapterEntity>

    @Query("SELECT * FROM novel_chapters ORDER BY conversationId ASC, sortOrder ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getNovelChaptersPaged(limit: Int, offset: Int): List<NovelChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNovelChapter(entity: NovelChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNovelChapters(entities: List<NovelChapterEntity>)

    @Query("DELETE FROM novel_chapters WHERE id = :id")
    suspend fun deleteNovelChapter(id: String)

    @Query("DELETE FROM novel_chapters WHERE conversationId = :convId")
    suspend fun deleteNovelChaptersForConversation(convId: String)

    @Query("SELECT * FROM novel_lore WHERE conversationId = :convId ORDER BY priority DESC, updatedAt DESC")
    fun observeNovelLore(convId: String): Flow<List<NovelLoreEntity>>

    @Query("SELECT * FROM novel_lore WHERE conversationId = :convId ORDER BY priority DESC, updatedAt DESC")
    suspend fun getNovelLoreForConversation(convId: String): List<NovelLoreEntity>

    @Query("SELECT * FROM novel_lore")
    suspend fun getAllNovelLore(): List<NovelLoreEntity>

    @Query("SELECT * FROM novel_lore ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getNovelLorePaged(limit: Int, offset: Int): List<NovelLoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNovelLore(entity: NovelLoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNovelLoreList(entities: List<NovelLoreEntity>)

    @Query("DELETE FROM novel_lore WHERE id = :id")
    suspend fun deleteNovelLore(id: String)

    @Query("DELETE FROM novel_lore WHERE conversationId = :convId")
    suspend fun deleteNovelLoreForConversation(convId: String)

    @Query("SELECT * FROM memory_ledger WHERE conversationId = :convId ORDER BY pinned DESC, updatedAt DESC")
    fun observeMemoryLedger(convId: String): Flow<List<MemoryLedgerEntity>>

    @Query("SELECT * FROM memory_ledger WHERE conversationId = :convId ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getMemoryLedgerForConversation(convId: String): List<MemoryLedgerEntity>

    @Query("SELECT * FROM memory_ledger")
    suspend fun getAllMemoryLedger(): List<MemoryLedgerEntity>

    @Query("SELECT * FROM memory_ledger ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getMemoryLedgerPaged(limit: Int, offset: Int): List<MemoryLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemoryLedger(entity: MemoryLedgerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemoryLedgerList(entities: List<MemoryLedgerEntity>)

    @Query("DELETE FROM memory_ledger WHERE id = :id")
    suspend fun deleteMemoryLedger(id: String)

    @Query("DELETE FROM memory_ledger WHERE conversationId = :convId")
    suspend fun deleteMemoryLedgerForConversation(convId: String)

    @Query("SELECT * FROM memory_episodes WHERE conversationId = :convId ORDER BY level DESC, fromSeq ASC")
    fun observeMemoryEpisodes(convId: String): Flow<List<MemoryEpisodeEntity>>

    @Query("SELECT * FROM memory_episodes WHERE conversationId = :convId ORDER BY level DESC, fromSeq ASC")
    suspend fun getMemoryEpisodesForConversation(convId: String): List<MemoryEpisodeEntity>

    @Query("SELECT * FROM memory_episodes")
    suspend fun getAllMemoryEpisodes(): List<MemoryEpisodeEntity>

    @Query("SELECT * FROM memory_episodes ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getMemoryEpisodesPaged(limit: Int, offset: Int): List<MemoryEpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemoryEpisode(entity: MemoryEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemoryEpisodes(entities: List<MemoryEpisodeEntity>)

    @Query("DELETE FROM memory_episodes WHERE id = :id")
    suspend fun deleteMemoryEpisode(id: String)

    @Query("DELETE FROM memory_episodes WHERE conversationId = :convId")
    suspend fun deleteMemoryEpisodesForConversation(convId: String)

    @Query("SELECT * FROM rp_save_slots WHERE conversationId = :convId ORDER BY createdAt DESC")
    fun observeRpSaveSlots(convId: String): Flow<List<RpSaveSlotEntity>>

    @Query("SELECT * FROM rp_save_slots WHERE conversationId = :convId ORDER BY createdAt DESC")
    suspend fun getRpSaveSlotsForConversation(convId: String): List<RpSaveSlotEntity>

    @Query("SELECT * FROM rp_save_slots")
    suspend fun getAllRpSaveSlots(): List<RpSaveSlotEntity>

    @Query("SELECT * FROM rp_save_slots ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getRpSaveSlotsPaged(limit: Int, offset: Int): List<RpSaveSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRpSaveSlot(entity: RpSaveSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRpSaveSlots(entities: List<RpSaveSlotEntity>)

    @Query("DELETE FROM rp_save_slots WHERE id = :id")
    suspend fun deleteRpSaveSlot(id: String)

    @Query("DELETE FROM rp_save_slots WHERE conversationId = :convId")
    suspend fun deleteRpSaveSlotsForConversation(convId: String)

    @Query("DELETE FROM conversation_worldbooks WHERE conversationId = :convId")
    suspend fun deleteConversationWorldbooksForConversation(convId: String)

    @Query("DELETE FROM memory_embeddings WHERE conversationId = :convId")
    suspend fun deleteMemoryEmbeddingsForConversation(convId: String)

    @Transaction
    suspend fun deleteConversationCascade(convId: String) {
        deleteMessagesForConversation(convId)
        deleteNovelChaptersForConversation(convId)
        deleteNovelLoreForConversation(convId)
        deleteMemoryLedgerForConversation(convId)
        deleteMemoryEpisodesForConversation(convId)
        deleteRpSaveSlotsForConversation(convId)
        deleteConversationWorldbooksForConversation(convId)
        deleteMemoryEmbeddingsForConversation(convId)
        deleteConversation(convId)
    }

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM novel_chapters")
    suspend fun clearAllNovelChapters()

    @Query("DELETE FROM novel_lore")
    suspend fun clearAllNovelLore()

    @Query("DELETE FROM memory_ledger")
    suspend fun clearAllMemoryLedger()

    @Query("DELETE FROM memory_episodes")
    suspend fun clearAllMemoryEpisodes()

    @Query("DELETE FROM rp_save_slots")
    suspend fun clearAllRpSaveSlots()
}

@Dao
interface UserMemoryDao {
    @Query("SELECT * FROM user_memories ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memories ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getAll(): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<UserMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UserMemoryEntity>)

    @Query("DELETE FROM user_memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM user_memories")
    suspend fun clearAll()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachment_meta WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AttachmentMetaEntity?

    @Query("SELECT * FROM attachment_meta WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<AttachmentMetaEntity>

    @Query("SELECT * FROM attachment_meta ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<AttachmentMetaEntity>

    @Query("UPDATE attachment_meta SET injectMode = :injectMode WHERE id = :id")
    suspend fun updateInjectMode(id: String, injectMode: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AttachmentMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AttachmentMetaEntity>)

    @Query("DELETE FROM attachment_meta WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM attachment_meta")
    suspend fun clearAll()
}
