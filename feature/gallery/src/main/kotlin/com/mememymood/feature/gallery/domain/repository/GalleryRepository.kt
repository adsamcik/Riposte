package com.mememymood.feature.gallery.domain.repository

import com.mememymood.core.model.Meme
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
}
