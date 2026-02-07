package com.mememymood.feature.gallery.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.mememymood.core.common.di.IoDispatcher
import com.mememymood.core.database.dao.EmojiTagDao
import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.entity.EmojiTagEntity
import com.mememymood.core.database.mapper.MemeMapper.toDomain
import com.mememymood.core.database.mapper.MemeMapper.toEntity
import com.mememymood.core.model.Meme
import com.mememymood.feature.gallery.domain.repository.GalleryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Implementation of GalleryRepository using Room database.
 */
class GalleryRepositoryImpl @Inject constructor(
    private val memeDao: MemeDao,
    private val emojiTagDao: EmojiTagDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
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
            config = PagingConfig(
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

    override fun getFavorites(): Flow<List<Meme>> {
        return memeDao.getFavoriteMemes()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getMemeById(id: Long): Meme? = withContext(ioDispatcher) {
        memeDao.getMemeById(id)?.toDomain()
    }
    
    override fun observeMeme(id: Long): Flow<Meme?> {
        return memeDao.observeMemeById(id)
            .map { entity -> entity?.toDomain() }
            .flowOn(ioDispatcher)
    }
    
    override suspend fun updateMeme(meme: Meme): Result<Unit> = withContext(ioDispatcher) {
        try {
            memeDao.updateMeme(meme.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMemeWithEmojis(meme: Meme): Result<Unit> = withContext(ioDispatcher) {
        try {
            // Update the meme entity
            memeDao.updateMeme(meme.toEntity())
            
            // Delete existing emoji tags and insert new ones
            emojiTagDao.deleteEmojiTagsForMeme(meme.id)
            if (meme.emojiTags.isNotEmpty()) {
                val tagEntities = meme.emojiTags.map { tag ->
                    EmojiTagEntity(
                        memeId = meme.id,
                        emoji = tag.emoji,
                        emojiName = tag.name,
                    )
                }
                emojiTagDao.insertEmojiTags(tagEntities)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMeme(id: Long): Result<Unit> = withContext(ioDispatcher) {
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMemes(ids: Set<Long>): Result<Unit> = withContext(ioDispatcher) {
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleFavorite(id: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            memeDao.toggleFavorite(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getMemesByEmoji(emoji: String): Flow<List<Meme>> {
        return memeDao.getMemesByEmoji(emoji)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getAllMemeIds(): List<Long> = withContext(ioDispatcher) {
        memeDao.getAllMemeIds()
    }

    override suspend fun recordMemeView(id: Long) = withContext(ioDispatcher) {
        memeDao.recordView(id)
    }

    override fun getRecentlyViewed(limit: Int): Flow<List<Meme>> {
        return memeDao.getRecentlyViewedMemes(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }
}
