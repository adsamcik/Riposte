package com.adsamcik.riposte.core.database

import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Statistics about the meme library.
 */
data class LibraryStatistics(
    val totalMemes: Int = 0,
    val favoriteMemes: Int = 0,
    val indexedMemes: Int = 0,
    val pendingIndexing: Int = 0,
)

/**
 * Provides observable library statistics for the settings screen.
 */
@Singleton
class LibraryStatsProvider
    @Inject
    constructor(
        private val memeDao: MemeDao,
        private val memeEmbeddingDao: MemeEmbeddingDao,
    ) {
        /**
         * Observe library statistics as a Flow.
         * Combines multiple count queries for live updates.
         */
        fun observeStatistics(): Flow<LibraryStatistics> {
            return combine(
                memeDao.observeMemeCount(),
                memeDao.observeFavoriteCount(),
                memeEmbeddingDao.observeValidEmbeddingsCount(),
                memeEmbeddingDao.observeMemesWithoutEmbeddingsCount(),
            ) { totalCount, favoriteCount, indexedCount, pendingCount ->
                LibraryStatistics(
                    totalMemes = totalCount,
                    favoriteMemes = favoriteCount,
                    indexedMemes = indexedCount,
                    pendingIndexing = pendingCount,
                )
            }
        }

        /**
         * Get current library statistics (suspend).
         */
        suspend fun getStatistics(): LibraryStatistics {
            return LibraryStatistics(
                totalMemes = memeDao.getMemeCount(),
                favoriteMemes = memeDao.getFavoriteCount(),
                indexedMemes = memeEmbeddingDao.countValidEmbeddings(),
                pendingIndexing = memeEmbeddingDao.countMemesWithoutEmbeddings(),
            )
        }
    }
