package com.adsamcik.riposte.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent cache for query embedding vectors.
 * Avoids re-computing embeddings for repeated search queries.
 *
 * Cache entries are keyed by (queryHash + modelVersion) and automatically
 * invalidated when the model version changes.
 */
@Entity(
    tableName = "query_embedding_cache",
    indices = [
        Index(value = ["modelVersion"]),
        Index(value = ["accessedAt"]),
    ],
)
data class QueryEmbeddingCacheEntity(
    /** SHA-256 hash of the normalized (trimmed, lowercased) query text. */
    @PrimaryKey
    val queryHash: String,
    /** Original query text (for debugging/inspection). */
    val query: String,
    /** Model version that produced this embedding. */
    val modelVersion: String,
    /** Embedding vector as little-endian float32 ByteArray. */
    val embedding: ByteArray,
    /** Dimension of the embedding vector. */
    val dimension: Int,
    /** Epoch millis when this cache entry was created. */
    val createdAt: Long,
    /** Epoch millis when this entry was last accessed (for LRU eviction). */
    val accessedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as QueryEmbeddingCacheEntity
        return queryHash == other.queryHash &&
            modelVersion == other.modelVersion &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = queryHash.hashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
