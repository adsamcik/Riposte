package com.adsamcik.riposte.feature.gallery.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.adsamcik.riposte.core.common.di.IoDispatcher
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.entity.EmojiTagEntity
import com.adsamcik.riposte.core.database.mapper.MemeMapper.toDomain
import com.adsamcik.riposte.core.database.mapper.MemeMapper.toEntity
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.feature.gallery.domain.model.MemeEmbeddingData
import com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Implementation of GalleryRepository using Room database.
 */
class GalleryRepositoryImpl
    @Inject
    constructor(
        private val memeDao: MemeDao,
        private val emojiTagDao: EmojiTagDao,
        private val memeEmbeddingDao: MemeEmbeddingDao,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : GalleryRepository {
        companion object {
            private const val PAGE_SIZE = 20
            private const val PREFETCH_DISTANCE = 5
        }

        override fun getMemes(): Flow<List<Meme>> {
            return memeDao.getAllMemes()
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)
        }

        override fun getPagedMemes(sortBy: String): Flow<PagingData<Meme>> {
            return Pager(
                config =
                    PagingConfig(
                        pageSize = PAGE_SIZE,
                        prefetchDistance = PREFETCH_DISTANCE,
                        enablePlaceholders = false,
                    ),
                pagingSourceFactory = {
                    when (sortBy) {
                        "most_used" -> memeDao.getAllMemesPagedByMostUsed()
                        "emoji" -> memeDao.getAllMemesPagedByEmoji()
                        else -> memeDao.getAllMemesPaged()
                    }
                },
            ).flow.map { pagingData ->
                pagingData.map { it.toDomain() }
            }.flowOn(ioDispatcher)
        }

        override fun getPagedMemesByEmojis(emojis: Set<String>): Flow<PagingData<Meme>> {
            return Pager(
                config =
                    PagingConfig(
                        pageSize = PAGE_SIZE,
                        prefetchDistance = PREFETCH_DISTANCE,
                        enablePlaceholders = false,
                    ),
                pagingSourceFactory = {
                    memeDao.getMemesByEmojisPaged(emojis.toList())
                },
            ).flow.map { pagingData ->
                pagingData.map { it.toDomain() }
            }.flowOn(ioDispatcher)
        }

        override fun getFavorites(): Flow<List<Meme>> {
            return memeDao.getFavoriteMemes()
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)
        }

        override suspend fun getMemeById(id: Long): Meme? =
            withContext(ioDispatcher) {
                memeDao.getMemeById(id)?.toDomain()
            }

        override fun observeMeme(id: Long): Flow<Meme?> {
            return memeDao.observeMemeById(id)
                .map { entity -> entity?.toDomain() }
                .flowOn(ioDispatcher)
        }

        override suspend fun updateMeme(meme: Meme): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    memeDao.updateMeme(meme.toEntity())
                    Result.success(Unit)
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to update meme")
                    Result.failure(e)
                }
            }

        override suspend fun updateMemeWithEmojis(meme: Meme): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    // Update the meme entity
                    memeDao.updateMeme(meme.toEntity())

                    // Delete existing emoji tags and insert new ones
                    emojiTagDao.deleteEmojiTagsForMeme(meme.id)
                    if (meme.emojiTags.isNotEmpty()) {
                        val tagEntities =
                            meme.emojiTags.map { tag ->
                                EmojiTagEntity(
                                    memeId = meme.id,
                                    emoji = tag.emoji,
                                    emojiName = tag.name,
                                )
                            }
                        emojiTagDao.insertEmojiTags(tagEntities)
                    }

                    Result.success(Unit)
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to delete meme with tags")
                    Result.failure(e)
                }
            }

        override suspend fun deleteMeme(id: Long): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    val meme = memeDao.getMemeById(id)
                    if (meme != null) {
                        // Delete the file
                        val file = File(meme.filePath)
                        if (file.exists()) {
                            file.delete()
                        }
                        // Delete from database
                        memeDao.deleteMemeById(id)
                    }
                    Result.success(Unit)
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to delete meme")
                    Result.failure(e)
                }
            }

        override suspend fun deleteMemes(ids: Set<Long>): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    // Delete files
                    ids.forEach { id ->
                        val meme = memeDao.getMemeById(id)
                        meme?.let {
                            val file = File(it.filePath)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                    }
                    // Delete from database
                    memeDao.deleteMemesByIds(ids.toList())
                    Result.success(Unit)
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to delete %d memes", ids.size)
                    Result.failure(e)
                }
            }

        override suspend fun toggleFavorite(id: Long): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    memeDao.toggleFavorite(id)
                    Result.success(Unit)
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to toggle favorite")
                    Result.failure(e)
                }
            }

        override fun getMemesByEmoji(emoji: String): Flow<List<Meme>> {
            return memeDao.getMemesByEmoji(emoji)
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)
        }

        override suspend fun getAllMemeIds(): List<Long> =
            withContext(ioDispatcher) {
                memeDao.getAllMemeIds()
            }

        override suspend fun recordMemeView(id: Long) =
            withContext(ioDispatcher) {
                memeDao.recordView(id)
            }

        override fun getRecentlyViewed(limit: Int): Flow<List<Meme>> {
            return memeDao.getRecentlyViewedMemes(limit)
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)
        }

        override fun getAllEmojisWithCounts(): Flow<List<Pair<String, Int>>> {
            return emojiTagDao.getEmojisOrderedByUsage()
                .map { stats -> stats.map { it.emoji to it.totalUsage } }
                .flowOn(ioDispatcher)
        }

        override fun getAllEmojisWithTagCounts(): Flow<List<Pair<String, Int>>> {
            return emojiTagDao.getAllEmojisWithCounts()
                .map { stats -> stats.map { it.emoji to it.count } }
                .flowOn(ioDispatcher)
        }

        override suspend fun getEmbeddingsExcluding(memeId: Long): List<MemeEmbeddingData> =
            withContext(ioDispatcher) {
                memeEmbeddingDao.getMemesWithEmbeddings()
                    .filter { it.memeId != memeId }
                    .map { MemeEmbeddingData(memeId = it.memeId, embedding = it.embedding) }
            }
    }
