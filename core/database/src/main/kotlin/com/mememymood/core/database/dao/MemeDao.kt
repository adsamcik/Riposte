package com.mememymood.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mememymood.core.database.entity.MemeEntity
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
     * Get all memes as a PagingSource for efficient pagination.
     * Used for large collections (1000+ memes).
     */
    @Query("SELECT * FROM memes ORDER BY importedAt DESC")
    fun getAllMemesPaged(): PagingSource<Int, MemeEntity>

    /**
     * Get all meme IDs for bulk operations (e.g., select all).
     */
    @Query("SELECT id FROM memes ORDER BY importedAt DESC")
    suspend fun getAllMemeIds(): List<Long>
}
