package com.gugachat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryEmbeddingDao {
    @Query(
        "SELECT * FROM memory_embeddings " +
            "WHERE conversationId = :conversationId AND profileKey = :profileKey",
    )
    suspend fun getByConversationAndProfile(
        conversationId: String,
        profileKey: String,
    ): List<MemoryEmbeddingEntity>

    @Query(
        "SELECT * FROM memory_embeddings " +
            "WHERE conversationId = '' AND profileKey = :profileKey",
    )
    suspend fun getGlobalByProfile(profileKey: String): List<MemoryEmbeddingEntity>

    @Query(
        "SELECT ownerType, ownerId, contentHash FROM memory_embeddings " +
            "WHERE conversationId = :conversationId AND profileKey = :profileKey",
    )
    suspend fun getHashesForProfile(
        conversationId: String,
        profileKey: String,
    ): List<MemoryEmbeddingHashRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MemoryEmbeddingEntity>)

    @Query("DELETE FROM memory_embeddings WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteByOwner(ownerType: String, ownerId: String)

    @Query("DELETE FROM memory_embeddings WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Query("DELETE FROM memory_embeddings WHERE profileKey != :activeProfileKey")
    suspend fun deleteStaleProfiles(activeProfileKey: String)

    @Query("SELECT COUNT(*) FROM memory_embeddings WHERE profileKey = :profileKey")
    suspend fun countByProfile(profileKey: String): Int

    @Query("DELETE FROM memory_embeddings")
    suspend fun deleteAll()
}
