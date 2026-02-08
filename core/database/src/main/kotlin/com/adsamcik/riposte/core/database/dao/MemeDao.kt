package com.adsamcik.riposte.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.adsamcik.riposte.core.database.entity.MemeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for meme operations.
 */
@Dao
interface MemeDao {

    /**
     * Get all memes ordered by import date (newest first).
     */
    @Query("SELECT * FROM memes ORDER BY importedAt DESC")
    fun getAllMemes(): Flow<List<MemeEntity>>

    /**
     * Get all favorite memes ordered by import date.
     */
    @Query("SELECT * FROM memes WHERE isFavorite = 1 ORDER BY importedAt DESC")
    fun getFavoriteMemes(): Flow<List<MemeEntity>>

    /**
     * Get a single meme by ID.
     */
    @Query("SELECT * FROM memes WHERE id = :id")
    suspend fun getMemeById(id: Long): MemeEntity?

    /**
     * Get multiple memes by IDs in a single query.
     */
    @Query("SELECT * FROM memes WHERE id IN (:ids)")
    suspend fun getMemesByIds(ids: List<Long>): List<MemeEntity>

    /**
     * Get a single meme by ID as a Flow for observation.
     */
    @Query("SELECT * FROM memes WHERE id = :id")
    fun observeMemeById(id: Long): Flow<MemeEntity?>

    /**
     * Insert a new meme and return the generated ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeme(meme: MemeEntity): Long

    /**
     * Insert multiple memes.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemes(memes: List<MemeEntity>): List<Long>

    /**
     * Update an existing meme.
     */
    @Update
    suspend fun updateMeme(meme: MemeEntity)

    /**
     * Delete a meme.
     */
    @Delete
    suspend fun deleteMeme(meme: MemeEntity)

    /**
     * Delete a meme by ID.
     */
    @Query("DELETE FROM memes WHERE id = :id")
    suspend fun deleteMemeById(id: Long)

    /**
     * Delete multiple memes by IDs.
     */
    @Query("DELETE FROM memes WHERE id IN (:ids)")
    suspend fun deleteMemesByIds(ids: List<Long>)

    /**
     * Toggle favorite status for a meme.
     */
    @Query("UPDATE memes SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    /**
     * Set favorite status for a meme.
     */
    @Query("UPDATE memes SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    /**
     * Update the embedding for a meme (for semantic search).
     */
    @Query("UPDATE memes SET embedding = :embedding WHERE id = :id")
    suspend fun updateEmbedding(id: Long, embedding: ByteArray)

    /**
     * Get memes that don't have embeddings yet (for batch processing).
     */
    @Query("SELECT * FROM memes WHERE embedding IS NULL LIMIT :limit")
    suspend fun getMemesWithoutEmbeddings(limit: Int = 50): List<MemeEntity>

    /**
     * Get the total count of memes.
     */
    @Query("SELECT COUNT(*) FROM memes")
    suspend fun getMemeCount(): Int

    /**
     * Observe the total count of memes as a Flow.
     */
    @Query("SELECT COUNT(*) FROM memes")
    fun observeMemeCount(): Flow<Int>

    /**
     * Get the count of favorite memes.
     */
    @Query("SELECT COUNT(*) FROM memes WHERE isFavorite = 1")
    suspend fun getFavoriteCount(): Int

    /**
     * Observe the count of favorite memes as a Flow.
     */
    @Query("SELECT COUNT(*) FROM memes WHERE isFavorite = 1")
    fun observeFavoriteCount(): Flow<Int>

    /**
     * Get memes by emoji tag.
     */
    @Query("""
        SELECT m.* FROM memes m
        INNER JOIN emoji_tags e ON m.id = e.memeId
        WHERE e.emoji = :emoji
        ORDER BY m.importedAt DESC
    """)
    fun getMemesByEmoji(emoji: String): Flow<List<MemeEntity>>

    /**
     * Check if a file already exists in the database.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM memes WHERE filePath = :filePath)")
    suspend fun memeExistsByPath(filePath: String): Boolean

    /**
     * Check if a meme with the given file hash already exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM memes WHERE fileHash = :hash)")
    suspend fun memeExistsByHash(hash: String): Boolean

    /**
     * Get all memes as a PagingSource for efficient pagination.
     * Used for large collections (1000+ memes).
     */
    @Query("SELECT * FROM memes ORDER BY importedAt DESC")
    fun getAllMemesPaged(): PagingSource<Int, MemeEntity>

    /**
     * Get all memes as a PagingSource sorted by most used first.
     */
    @Query("SELECT * FROM memes ORDER BY useCount DESC, importedAt DESC")
    fun getAllMemesPagedByMostUsed(): PagingSource<Int, MemeEntity>

    /**
     * Get all memes as a PagingSource sorted by primary emoji tag.
     */
    @Query(
        """
        SELECT m.* FROM memes m
        LEFT JOIN (SELECT memeId, MIN(emoji) as primaryEmoji FROM emoji_tags GROUP BY memeId) e ON m.id = e.memeId
        ORDER BY COALESCE(e.primaryEmoji, 'zzz') ASC, m.importedAt DESC
        """,
    )
    fun getAllMemesPagedByEmoji(): PagingSource<Int, MemeEntity>

    /**
     * Get all meme IDs for bulk operations (e.g., select all).
     */
    @Query("SELECT id FROM memes ORDER BY importedAt DESC")
    suspend fun getAllMemeIds(): List<Long>

    /**
     * Increment the view count and update last viewed timestamp.
     */
    @Query("UPDATE memes SET viewCount = viewCount + 1, lastViewedAt = :timestamp WHERE id = :id")
    suspend fun recordView(id: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Get recently viewed memes ordered by last view time (most recent first).
     * Only includes memes that have been viewed at least once.
     */
    @Query("SELECT * FROM memes WHERE lastViewedAt IS NOT NULL ORDER BY lastViewedAt DESC LIMIT :limit")
    fun getRecentlyViewedMemes(limit: Int = 20): Flow<List<MemeEntity>>
}
