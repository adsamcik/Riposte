package com.adsamcik.riposte.feature.gallery.domain.usecase

import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.mapper.MemeMapper.toDomain
import com.adsamcik.riposte.core.ml.EmbeddingManager
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import android.util.Log
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
     * @return [SimilarMemesStatus] indicating the result or reason for no results.
     */
    suspend operator fun invoke(memeId: Long, limit: Int = 10): SimilarMemesStatus =
        withContext(Dispatchers.Default) {
            try {
                val currentEmbedding = embeddingManager.getEmbedding(memeId)
                if (currentEmbedding == null) {
                    Log.d(TAG, "No embedding for meme $memeId")
                    return@withContext SimilarMemesStatus.NoEmbeddingForMeme
                }

                val candidates = memeEmbeddingDao.getMemesWithEmbeddings()
                    .filter { it.memeId != memeId }

                if (candidates.isEmpty()) {
                    Log.d(TAG, "No candidate embeddings available")
                    return@withContext SimilarMemesStatus.NoCandidates
                }

                val scored = candidates.mapNotNull { candidate ->
                    val embedding = candidate.embedding ?: return@mapNotNull null
                    val floats = decodeEmbedding(embedding)
                    if (floats.size != currentEmbedding.size) {
                        Log.d(TAG, "Dimension mismatch for meme ${candidate.memeId}: ${floats.size} vs ${currentEmbedding.size}")
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

                val memes = scored.mapNotNull { (id, _) ->
                    memeDao.getMemeById(id)?.toDomain()
                }

                SimilarMemesStatus.Found(memes)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compute similar memes", e)
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
        const val TAG = "GetSimilarMemesUseCase"
        const val SIMILARITY_THRESHOLD = 0.3f
    }
}
