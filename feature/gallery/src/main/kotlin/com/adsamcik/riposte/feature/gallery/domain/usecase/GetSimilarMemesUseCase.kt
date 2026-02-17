package com.adsamcik.riposte.feature.gallery.domain.usecase

import com.adsamcik.riposte.core.common.di.DefaultDispatcher
import com.adsamcik.riposte.core.ml.EmbeddingManager
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Finds memes that are semantically similar to a given meme using embedding cosine similarity.
 */
class GetSimilarMemesUseCase
    @Inject
    constructor(
        private val embeddingManager: EmbeddingManager,
        private val galleryRepository: GalleryRepository,
        private val semanticSearchEngine: SemanticSearchEngine,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        /**
         * @param memeId The meme to find similar memes for.
         * @param limit Maximum number of similar memes to return.
         * @return [SimilarMemesStatus] indicating the result or reason for no results.
         */
        suspend operator fun invoke(
            memeId: Long,
            limit: Int = 10,
        ): SimilarMemesStatus =
            withContext(defaultDispatcher) {
                try {
                    val currentEmbedding =
                        embeddingManager.getEmbedding(memeId)
                            ?: return@withContext SimilarMemesStatus.NoEmbeddingForMeme

                    val candidates = galleryRepository.getEmbeddingsExcluding(memeId)

                    if (candidates.isEmpty()) {
                        return@withContext SimilarMemesStatus.NoCandidates
                    }

                    val scored =
                        candidates.mapNotNull { candidate ->
                            val embedding = candidate.embedding ?: return@mapNotNull null
                            val floats = decodeEmbedding(embedding)
                            if (floats.size != currentEmbedding.size) {
                                return@mapNotNull null
                            }
                            val score = semanticSearchEngine.cosineSimilarity(currentEmbedding, floats)
                            candidate.memeId to score
                        }
                            .filter { it.second >= SIMILARITY_THRESHOLD }
                            .sortedByDescending { it.second }
                            .take(limit)

                    if (scored.isEmpty()) {
                        return@withContext SimilarMemesStatus.NoSimilarFound
                    }

                    val memes =
                        scored.mapNotNull { (id, _) ->
                            galleryRepository.getMemeById(id)
                        }

                    SimilarMemesStatus.Found(memes)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to find similar memes for meme %d", memeId)
                    SimilarMemesStatus.Error(e.message ?: "Unknown error")
                }
            }

        // Mirrors EmbeddingManager.decodeEmbedding — kept here to avoid N+1 queries
        // when batch-decoding candidate embeddings from the DAO result.
        private fun decodeEmbedding(bytes: ByteArray): FloatArray {
            val floatArray = FloatArray(bytes.size / 4)
            ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()
                .get(floatArray)
            return floatArray
        }

        private companion object {
            const val SIMILARITY_THRESHOLD = 0.3f
        }
    }
