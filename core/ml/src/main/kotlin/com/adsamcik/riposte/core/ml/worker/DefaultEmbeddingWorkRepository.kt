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
            // Split budget across three sources
            val third = (limit / 3).coerceAtLeast(1)

            // Get memes without any embeddings
            val memesWithoutEmbeddings = memeEmbeddingDao.getMemeIdsWithoutEmbeddings(third)

            // Get memes needing regeneration (version change)
            val memesNeedingRegeneration = memeEmbeddingDao.getMemeIdsNeedingRegeneration(third)

            // Get memes with incomplete type coverage (partial failures)
            val memesIncomplete = memeEmbeddingDao.getMemeIdsWithIncompleteEmbeddings(
                expectedTypeCount = EXPECTED_EMBEDDING_TYPES,
                currentVersion = EmbeddingGenerationWorker.CURRENT_MODEL_VERSION,
                limit = third,
            )

            // Combine and get meme data
            val allMemeIds = (memesWithoutEmbeddings + memesNeedingRegeneration + memesIncomplete)
                .distinct()
                .take(limit)

            return allMemeIds.mapNotNull { memeId ->
                memeDao.getMemeById(memeId)?.let { entity ->
                    MemeDataForEmbedding(
                        id = entity.id,
                        filePath = entity.filePath,
                        title = entity.title,
                        description = entity.description,
                        textContent = entity.textContent,
                        searchPhrases = entity.searchPhrasesJson,
                        emojiTagsJson = entity.emojiTagsJson,
                        basedOn = entity.basedOn,
                        emotionsJson = entity.emotionsJson,
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
                memeEmbeddingDao.countEmbeddingsNeedingRegeneration() +
                memeEmbeddingDao.countMemesWithIncompleteEmbeddings(
                    expectedTypeCount = EXPECTED_EMBEDDING_TYPES,
                    currentVersion = EmbeddingGenerationWorker.CURRENT_MODEL_VERSION,
                )
        }

        override suspend fun deleteOutdatedEmbeddings(currentVersion: String) {
            memeEmbeddingDao.deleteOutdatedEmbeddings(currentVersion)
        }

        companion object {
            /** Number of embedding types the generator produces per meme. */
            private const val EXPECTED_EMBEDDING_TYPES = 4
        }
    }
