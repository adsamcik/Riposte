package com.mememymood.feature.gallery.domain.usecase

import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.dao.MemeEmbeddingDao
import com.mememymood.core.database.mapper.MemeMapper.toDomain
import com.mememymood.core.ml.EmbeddingManager
import com.mememymood.core.ml.SemanticSearchEngine
import com.mememymood.core.model.Meme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Finds memes that are semantically similar to a given meme using embedding cosine similarity.
 */
class GetSimilarMemesUseCase @Inject constructor(
    private val embeddingManager: EmbeddingManager,
    private val memeEmbeddingDao: MemeEmbeddingDao,
    private val memeDao: MemeDao,
    private val semanticSearchEngine: SemanticSearchEngine,
) {
    /**
     * @param memeId The meme to find similar memes for.
     * @param limit Maximum number of similar memes to return.
     * @return List of similar memes sorted by relevance, or empty list if no embedding exists.
     */
    suspend operator fun invoke(memeId: Long, limit: Int = 10): List<Meme> =
        withContext(Dispatchers.Default) {
            val currentEmbedding = embeddingManager.getEmbedding(memeId)
                ?: return@withContext emptyList()

            val candidates = memeEmbeddingDao.getMemesWithEmbeddings()
                .filter { it.memeId != memeId }

            if (candidates.isEmpty()) return@withContext emptyList()

            val scored = candidates.mapNotNull { candidate ->
                val embedding = candidate.embedding ?: return@mapNotNull null
                val floats = decodeEmbedding(embedding)
                val score = semanticSearchEngine.cosineSimilarity(currentEmbedding, floats)
                candidate.memeId to score
            }
                .filter { it.second >= SIMILARITY_THRESHOLD }
                .sortedByDescending { it.second }
                .take(limit)

            // Load full meme data for top results
            scored.mapNotNull { (id, _) ->
                memeDao.getMemeById(id)?.toDomain()
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
