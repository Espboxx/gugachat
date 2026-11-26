package com.gugachat.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "memory_embeddings",
    primaryKeys = ["ownerType", "ownerId", "profileKey"],
    indices = [
        Index(value = ["conversationId", "profileKey"]),
        Index(value = ["profileKey", "contentHash"]),
    ],
)
data class MemoryEmbeddingEntity(
    val ownerType: String,
    val ownerId: String,
    val conversationId: String,
    val profileKey: String,
    val contentHash: String,
    val dim: Int,
    val quantType: String,
    val scale: Float,
    val vectorBlob: ByteArray,
    val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEmbeddingEntity) return false
        return ownerType == other.ownerType &&
            ownerId == other.ownerId &&
            conversationId == other.conversationId &&
            profileKey == other.profileKey &&
            contentHash == other.contentHash &&
            dim == other.dim &&
            quantType == other.quantType &&
            scale == other.scale &&
            vectorBlob.contentEquals(other.vectorBlob) &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = ownerType.hashCode()
        result = 31 * result + ownerId.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + profileKey.hashCode()
        result = 31 * result + contentHash.hashCode()
        result = 31 * result + dim
        result = 31 * result + quantType.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + vectorBlob.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

data class MemoryEmbeddingHashRow(
    val ownerType: String,
    val ownerId: String,
    val contentHash: String,
)
