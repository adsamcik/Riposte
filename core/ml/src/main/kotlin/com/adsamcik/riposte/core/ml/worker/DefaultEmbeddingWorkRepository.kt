package com.adsamcik.riposte.core.ml.worker

import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of EmbeddingWorkRepository.
 *
 * This connects the WorkManager worker to the database layer for
 * embedding generation operations.
 */
@Singleton
class DefaultEmbeddingWorkRepository
    @Inject
    constructor(
        private val memeDao: MemeDao,
        private val memeEmbeddingDao: MemeEmbeddingDao,
    ) : EmbeddingWorkRepository {
        override suspend fun getMemesNeedingEmbeddings(limit: Int): List<MemeDataForEmbedding> {
            // Get memes without embeddings
            val memesWithoutEmbeddings = memeEmbeddingDao.getMemeIdsWithoutEmbeddings(limit / 2)

            // Get memes needing regeneration
            val memesNeedingRegeneration = memeEmbeddingDao.getMemeIdsNeedingRegeneration(limit / 2)

            // Combine and get meme data
            val allMemeIds = (memesWithoutEmbeddings + memesNeedingRegeneration).distinct().take(limit)

            return allMemeIds.mapNotNull { memeId ->
                memeDao.getMemeById(memeId)?.let { entity ->
                    MemeDataForEmbedding(
                        id = entity.id,
                        filePath = entity.filePath,
                        title = entity.title,
                        description = entity.description,
                        textContent = entity.textContent,
                        searchPhrases = entity.searchPhrasesJson,
                    )
                }
            }
        }

        override suspend fun saveEmbedding(
            memeId: Long,
            embedding: ByteArray,
            dimension: Int,
            modelVersion: String,
            sourceTextHash: String?,
            embeddingType: String,
        ) {
            val embeddingEntity =
                MemeEmbeddingEntity(
                    memeId = memeId,
                    embeddingType = embeddingType,
                    embedding = embedding,
                    dimension = dimension,
                    modelVersion = modelVersion,
                    generatedAt = System.currentTimeMillis(),
                    sourceTextHash = sourceTextHash,
                    needsRegeneration = false,
                )

            memeEmbeddingDao.insertEmbedding(embeddingEntity)
        }

        override suspend fun countMemesNeedingEmbeddings(): Int {
            return memeEmbeddingDao.countMemesWithoutEmbeddings() +
                memeEmbeddingDao.countEmbeddingsNeedingRegeneration()
        }

        override suspend fun markOutdatedEmbeddings(currentVersion: String) {
            memeEmbeddingDao.markOutdatedForRegeneration(currentVersion)
        }
    }
