package com.adsamcik.riposte.feature.gallery.domain.repository

import androidx.paging.PagingData
import com.adsamcik.riposte.core.model.Meme
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for gallery operations.
 */
interface GalleryRepository {
    /**
     * Get all memes ordered by import date.
     */
    fun getMemes(): Flow<List<Meme>>

    /**
     * Get all memes as paged data for large collections.
     * @param sortBy Sort key: "recent" (default), "most_used", or "emoji".
     */
    fun getPagedMemes(sortBy: String = "recent"): Flow<PagingData<Meme>>

    /**
     * Get favorite memes.
     */
    fun getFavorites(): Flow<List<Meme>>

    /**
     * Get a single meme by ID.
     */
    suspend fun getMemeById(id: Long): Meme?

    /**
     * Observe a single meme by ID.
     */
    fun observeMeme(id: Long): Flow<Meme?>

    /**
     * Update a meme.
     */
    suspend fun updateMeme(meme: Meme): Result<Unit>

    /**
     * Update a meme with its emoji tags.
     */
    suspend fun updateMemeWithEmojis(meme: Meme): Result<Unit>

    /**
     * Delete a meme.
     */
    suspend fun deleteMeme(id: Long): Result<Unit>

    /**
     * Delete multiple memes.
     */
    suspend fun deleteMemes(ids: Set<Long>): Result<Unit>

    /**
     * Toggle favorite status.
     */
    suspend fun toggleFavorite(id: Long): Result<Unit>

    /**
     * Get memes by emoji filter.
     */
    fun getMemesByEmoji(emoji: String): Flow<List<Meme>>

    /**
     * Get all meme IDs for bulk operations.
     */
    suspend fun getAllMemeIds(): List<Long>

    /**
     * Record that a meme was viewed.
     */
    suspend fun recordMemeView(id: Long)

    /**
     * Get recently viewed memes.
     */
    fun getRecentlyViewed(limit: Int = 20): Flow<List<Meme>>

    /**
     * Get all unique emojis with their usage counts, sorted by count descending.
     */
    fun getAllEmojisWithCounts(): Flow<List<Pair<String, Int>>>
}
