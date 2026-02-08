package com.adsamcik.riposte.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import com.adsamcik.riposte.core.database.entity.MemeWithEmbeddingData
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for meme embedding operations.
 * 
 * This DAO handles all operations related to semantic embeddings,
 * including storage, retrieval, and batch processing for search.
 */
@Dao
interface MemeEmbeddingDao {

    // ============ Insert Operations ============

    /**
     * Insert a new embedding.
     * If an embedding for the meme already exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: MemeEmbeddingEntity): Long

    /**
     * Insert multiple embeddings in a batch.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<MemeEmbeddingEntity>): List<Long>

    // ============ Query Operations ============

    /**
     * Get embedding for a specific meme.
     */
    @Query("SELECT * FROM meme_embeddings WHERE memeId = :memeId")
    suspend fun getEmbeddingByMemeId(memeId: Long): MemeEmbeddingEntity?

    /**
     * Get embeddings for multiple memes.
     */
    @Query("SELECT * FROM meme_embeddings WHERE memeId IN (:memeIds)")
    suspend fun getEmbeddingsByMemeIds(memeIds: List<Long>): List<MemeEmbeddingEntity>

    /**
     * Get all embeddings for semantic search.
     */
    @Query("SELECT * FROM meme_embeddings WHERE needsRegeneration = 0")
    suspend fun getAllValidEmbeddings(): List<MemeEmbeddingEntity>

    /**
     * Get all embeddings as a Flow for observation.
     */
    @Query("SELECT * FROM meme_embeddings WHERE needsRegeneration = 0")
    fun observeAllValidEmbeddings(): Flow<List<MemeEmbeddingEntity>>

    /**
     * Get memes with their embeddings for search operations.
     * Joins meme data with embeddings for efficient search.
     */
    @Transaction
    @Query("""
        SELECT 
            m.id as memeId,
            m.filePath,
            m.fileName,
            m.title,
            m.description,
            m.textContent,
            m.emojiTagsJson,
            e.embedding,
            e.dimension,
            e.modelVersion
        FROM memes m
        LEFT JOIN meme_embeddings e ON m.id = e.memeId
        WHERE e.embedding IS NOT NULL AND e.needsRegeneration = 0
    """)
    suspend fun getMemesWithEmbeddings(): List<MemeWithEmbeddingData>

    /**
     * Get memes with their embeddings as a Flow.
     */
    @Transaction
    @Query("""
        SELECT 
            m.id as memeId,
            m.filePath,
            m.fileName,
            m.title,
            m.description,
            m.textContent,
            m.emojiTagsJson,
            e.embedding,
            e.dimension,
            e.modelVersion
        FROM memes m
        LEFT JOIN meme_embeddings e ON m.id = e.memeId
        WHERE e.embedding IS NOT NULL AND e.needsRegeneration = 0
    """)
    fun observeMemesWithEmbeddings(): Flow<List<MemeWithEmbeddingData>>

    // ============ Update Operations ============

    /**
     * Update an existing embedding.
     */
    @Update
    suspend fun updateEmbedding(embedding: MemeEmbeddingEntity)

    /**
     * Mark an embedding as needing regeneration.
     */
    @Query("UPDATE meme_embeddings SET needsRegeneration = 1 WHERE memeId = :memeId")
    suspend fun markForRegeneration(memeId: Long)

    /**
     * Mark all embeddings with a specific model version for regeneration.
     * Used when the model is upgraded.
     */
    @Query("UPDATE meme_embeddings SET needsRegeneration = 1 WHERE modelVersion != :currentVersion")
    suspend fun markOutdatedForRegeneration(currentVersion: String)

    /**
     * Mark all embeddings for regeneration.
     */
    @Query("UPDATE meme_embeddings SET needsRegeneration = 1")
    suspend fun markAllForRegeneration()

    // ============ Delete Operations ============

    /**
     * Delete embedding for a specific meme.
     */
    @Query("DELETE FROM meme_embeddings WHERE memeId = :memeId")
    suspend fun deleteEmbeddingByMemeId(memeId: Long)

    /**
     * Delete embeddings for multiple memes.
     */
    @Query("DELETE FROM meme_embeddings WHERE memeId IN (:memeIds)")
    suspend fun deleteEmbeddingsByMemeIds(memeIds: List<Long>)

    /**
     * Delete all embeddings (for testing or reset).
     */
    @Query("DELETE FROM meme_embeddings")
    suspend fun deleteAllEmbeddings()

    // ============ Batch Processing Queries ============

    /**
     * Get meme IDs that don't have embeddings yet.
     */
    @Query("""
        SELECT m.id FROM memes m
        LEFT JOIN meme_embeddings e ON m.id = e.memeId
        WHERE e.id IS NULL
        LIMIT :limit
    """)
    suspend fun getMemeIdsWithoutEmbeddings(limit: Int = 50): List<Long>

    /**
     * Get meme IDs that need embedding regeneration.
     */
    @Query("""
        SELECT memeId FROM meme_embeddings
        WHERE needsRegeneration = 1
        LIMIT :limit
    """)
    suspend fun getMemeIdsNeedingRegeneration(limit: Int = 50): List<Long>

    /**
     * Get total count of memes without embeddings.
     */
    @Query("""
        SELECT COUNT(*) FROM memes m
        LEFT JOIN meme_embeddings e ON m.id = e.memeId
        WHERE e.id IS NULL
    """)
    suspend fun countMemesWithoutEmbeddings(): Int

    /**
     * Get total count of embeddings needing regeneration.
     */
    @Query("SELECT COUNT(*) FROM meme_embeddings WHERE needsRegeneration = 1")
    suspend fun countEmbeddingsNeedingRegeneration(): Int

    /**
     * Get total count of valid embeddings.
     */
    @Query("SELECT COUNT(*) FROM meme_embeddings WHERE needsRegeneration = 0")
    suspend fun countValidEmbeddings(): Int

    /**
     * Observe the count of valid embeddings as a Flow.
     */
    @Query("SELECT COUNT(*) FROM meme_embeddings WHERE needsRegeneration = 0")
    fun observeValidEmbeddingsCount(): Flow<Int>

    /**
     * Observe the count of memes without embeddings as a Flow.
     */
    @Query("""
        SELECT COUNT(*) FROM memes m
        LEFT JOIN meme_embeddings e ON m.id = e.memeId
        WHERE e.id IS NULL
    """)
    fun observeMemesWithoutEmbeddingsCount(): Flow<Int>

    // ============ Statistics ============

    /**
     * Get embedding statistics by model version.
     */
    @Query("""
        SELECT modelVersion, COUNT(*) as count 
        FROM meme_embeddings 
        GROUP BY modelVersion
    """)
    suspend fun getEmbeddingCountByModelVersion(): List<EmbeddingVersionCount>

    /**
     * Check if a meme has a valid embedding.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM meme_embeddings 
            WHERE memeId = :memeId AND needsRegeneration = 0
        )
    """)
    suspend fun hasValidEmbedding(memeId: Long): Boolean
}

/**
 * Data class for embedding count per model version.
 */
data class EmbeddingVersionCount(
    val modelVersion: String,
    val count: Int
)
