package com.adsamcik.riposte.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.database.entity.PotentialDuplicateEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for duplicate detection operations.
 * Handles perceptual hash storage, potential duplicate tracking, and merge operations.
 */
@Dao
interface DuplicateDetectionDao {

    // region Perceptual Hash Operations

    /**
     * Get all memes that don't have a perceptual hash yet.
     */
    @Query("SELECT * FROM memes WHERE perceptualHash IS NULL")
    suspend fun getMemesWithoutPerceptualHash(): List<MemeEntity>

    /**
     * Get count of memes without a perceptual hash.
     */
    @Query("SELECT COUNT(*) FROM memes WHERE perceptualHash IS NULL")
    suspend fun getMemesWithoutPerceptualHashCount(): Int

    /**
     * Update the perceptual hash for a meme.
     */
    @Query("UPDATE memes SET perceptualHash = :hash WHERE id = :memeId")
    suspend fun updatePerceptualHash(memeId: Long, hash: Long)

    /**
     * Get all memes with their perceptual hashes (only those that have one).
     */
    @Query("SELECT * FROM memes WHERE perceptualHash IS NOT NULL")
    suspend fun getMemesWithPerceptualHash(): List<MemeEntity>

    /**
     * Get total meme count for progress tracking.
     */
    @Query("SELECT COUNT(*) FROM memes")
    suspend fun getTotalMemeCount(): Int

    // endregion

    // region Potential Duplicate Operations

    /**
     * Insert a potential duplicate pair. Ignores if the pair already exists.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPotentialDuplicate(duplicate: PotentialDuplicateEntity)

    /**
     * Insert multiple potential duplicate pairs.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPotentialDuplicates(duplicates: List<PotentialDuplicateEntity>)

    /**
     * Get all pending potential duplicates as a Flow for reactive UI updates.
     */
    @Query("SELECT * FROM potential_duplicates WHERE status = 'pending' ORDER BY hammingDistance ASC")
    fun getPendingDuplicates(): Flow<List<PotentialDuplicateEntity>>

    /**
     * Get count of pending potential duplicates.
     */
    @Query("SELECT COUNT(*) FROM potential_duplicates WHERE status = 'pending'")
    fun getPendingDuplicateCount(): Flow<Int>

    /**
     * Dismiss a duplicate pair so it won't be re-flagged.
     */
    @Query("UPDATE potential_duplicates SET status = 'dismissed' WHERE id = :id")
    suspend fun dismissDuplicate(id: Long)

    /**
     * Mark a duplicate pair as merged.
     */
    @Query("UPDATE potential_duplicates SET status = 'merged' WHERE id = :id")
    suspend fun markAsMerged(id: Long)

    /**
     * Check if a pair is already tracked (in any status).
     */
    @Query(
        """SELECT EXISTS(
            SELECT 1 FROM potential_duplicates 
            WHERE memeId1 = :memeId1 AND memeId2 = :memeId2
        )""",
    )
    suspend fun pairExists(memeId1: Long, memeId2: Long): Boolean

    /**
     * Delete all potential duplicates that reference a specific meme
     * (used after merge/delete operations).
     */
    @Query("DELETE FROM potential_duplicates WHERE memeId1 = :memeId OR memeId2 = :memeId")
    suspend fun deleteDuplicatesForMeme(memeId: Long)

    /**
     * Clear all pending duplicates (for re-scan).
     */
    @Query("DELETE FROM potential_duplicates WHERE status = 'pending'")
    suspend fun clearPendingDuplicates()

    /**
     * Get a specific potential duplicate by ID.
     */
    @Query("SELECT * FROM potential_duplicates WHERE id = :id")
    suspend fun getPendingDuplicateById(id: Long): PotentialDuplicateEntity?

    /**
     * Dismiss all pending duplicates at once.
     */
    @Query("UPDATE potential_duplicates SET status = 'dismissed' WHERE status = 'pending'")
    suspend fun dismissAllPending()

    /**
     * Get all pending duplicates as a snapshot list (non-reactive).
     */
    @Query("SELECT * FROM potential_duplicates WHERE status = 'pending' ORDER BY hammingDistance ASC")
    suspend fun getPendingDuplicatesList(): List<PotentialDuplicateEntity>

    // endregion

    // region Merge Support

    /**
     * Get a meme by ID for merge comparison.
     */
    @Query("SELECT * FROM memes WHERE id = :id")
    suspend fun getMemeById(id: Long): MemeEntity?

    /**
     * Delete a meme by ID (the "loser" in a merge).
     */
    @Query("DELETE FROM memes WHERE id = :id")
    suspend fun deleteMemeById(id: Long)

    /**
     * Update meme fields after merge (winner absorbs loser's best metadata).
     */
    @Suppress("LongParameterList")
    @Query(
        """UPDATE memes SET 
            emojiTagsJson = :emojiTagsJson,
            title = :title,
            description = :description,
            textContent = :textContent,
            searchPhrasesJson = :searchPhrasesJson,
            useCount = :useCount,
            viewCount = :viewCount,
            isFavorite = :isFavorite
        WHERE id = :id""",
    )
    suspend fun updateMergedMeme(
        id: Long,
        emojiTagsJson: String,
        title: String?,
        description: String?,
        textContent: String?,
        searchPhrasesJson: String?,
        useCount: Int,
        viewCount: Int,
        isFavorite: Boolean,
    )

    /**
     * Reassign embeddings from loser meme to winner meme.
     * Only reassigns types that the winner doesn't already have.
     */
    @Query(
        """UPDATE meme_embeddings 
        SET memeId = :winnerId 
        WHERE memeId = :loserId 
        AND embeddingType NOT IN (
            SELECT embeddingType FROM meme_embeddings WHERE memeId = :winnerId
        )""",
    )
    suspend fun reassignEmbeddings(loserId: Long, winnerId: Long)

    /**
     * Merge operation: update winner, reassign embeddings, delete loser, mark as merged.
     */
    @Suppress("LongParameterList")
    @Transaction
    suspend fun performMerge(
        winnerId: Long,
        loserId: Long,
        duplicateId: Long,
        emojiTagsJson: String,
        title: String?,
        description: String?,
        textContent: String?,
        searchPhrasesJson: String?,
        useCount: Int,
        viewCount: Int,
        isFavorite: Boolean,
    ) {
        updateMergedMeme(
            id = winnerId,
            emojiTagsJson = emojiTagsJson,
            title = title,
            description = description,
            textContent = textContent,
            searchPhrasesJson = searchPhrasesJson,
            useCount = useCount,
            viewCount = viewCount,
            isFavorite = isFavorite,
        )
        reassignEmbeddings(loserId = loserId, winnerId = winnerId)
        deleteMemeById(loserId)
        markAsMerged(duplicateId)
    }

    // endregion
}
