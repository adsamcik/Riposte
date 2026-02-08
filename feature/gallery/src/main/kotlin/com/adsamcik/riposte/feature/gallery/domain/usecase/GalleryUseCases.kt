package com.adsamcik.riposte.feature.gallery.domain.usecase

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.adsamcik.riposte.core.database.LibraryStatsProvider
import com.adsamcik.riposte.core.database.LibraryStatistics
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting all memes.
 */
class GetMemesUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    operator fun invoke(): Flow<List<Meme>> = repository.getMemes()
}

/**
 * Use case for getting paged memes for large collections.
 */
class GetPagedMemesUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    /**
     * Returns a Flow of PagingData for efficient pagination.
     * @param scope CoroutineScope to cache the paging data in (typically viewModelScope)
     * @param sortBy Sort key: "recent" (default), "most_used", or "emoji".
     */
    operator fun invoke(scope: CoroutineScope, sortBy: String = "recent"): Flow<PagingData<Meme>> =
        repository.getPagedMemes(sortBy).cachedIn(scope)
}

/**
 * Use case for getting favorite memes.
 */
class GetFavoritesUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    operator fun invoke(): Flow<List<Meme>> = repository.getFavorites()
}

/**
 * Use case for getting a meme by ID.
 */
class GetMemeByIdUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    suspend operator fun invoke(id: Long): Meme? = repository.getMemeById(id)
}

/**
 * Use case for deleting memes.
 */
class DeleteMemesUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    suspend operator fun invoke(ids: Set<Long>): Result<Unit> = repository.deleteMemes(ids)
    
    suspend operator fun invoke(id: Long): Result<Unit> = repository.deleteMeme(id)
}

/**
 * Use case for toggling favorite status.
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> = repository.toggleFavorite(id)
}

/**
 * Use case for updating a meme with its emoji tags.
 */
class UpdateMemeUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    suspend operator fun invoke(meme: Meme): Result<Unit> = repository.updateMemeWithEmojis(meme)
}

/**
 * Use case for filtering memes by emoji.
 */
class GetMemesByEmojiUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    operator fun invoke(emoji: String): Flow<List<Meme>> = repository.getMemesByEmoji(emoji)
}

/**
 * Use case for getting all meme IDs (for bulk operations like select all).
 */
class GetAllMemeIdsUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    suspend operator fun invoke(): List<Long> = repository.getAllMemeIds()
}

/**
 * Use case for recording a meme view.
 */
class RecordMemeViewUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    suspend operator fun invoke(id: Long) = repository.recordMemeView(id)
}

/**
 * Use case for getting recently viewed memes.
 */
class GetRecentlyViewedMemesUseCase @Inject constructor(
    private val repository: GalleryRepository
) {
    operator fun invoke(limit: Int = 20): Flow<List<Meme>> = repository.getRecentlyViewed(limit)
}

/**
 * Use case for observing library statistics (total count, favorites, indexed).
 */
class GetLibraryStatsUseCase @Inject constructor(
    private val statsProvider: LibraryStatsProvider
) {
    operator fun invoke(): Flow<LibraryStatistics> = statsProvider.observeStatistics()
}
