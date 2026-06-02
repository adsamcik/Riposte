package com.adsamcik.riposte.core.ml.search

import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.mapper.MemeMapper
import com.adsamcik.riposte.core.ml.MemeWithEmbeddings
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.model.SearchStrategy
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Search strategy that uses Mindlayer-backed AI embeddings for semantic
 * similarity search.
 *
 * Runs brute-force cosine similarity against all candidate memes. The previous
 * two-stage Matryoshka retrieval (truncated-ANN first pass, full re-rank) used
 * a Rust-native USearch index that was removed as part of the Mindlayer
 * migration. For the meme corpus sizes Riposte targets, the brute-force
 * implementation is well within budget, and the on-device service handles all
 * embedding inference.
 */
class SemanticSearchStrategy @Inject constructor(
    private val semanticSearchEngine: SemanticSearchEngine,
    private val memeEmbeddingDao: MemeEmbeddingDao,
) : SearchStrategy {

    override val name = "semantic"
    override val priority = PRIORITY

    /** Cached decoded candidates to avoid re-querying Room on every search. */
    @Volatile
    private var cachedCandidates: List<MemeWithEmbeddings>? = null

    override fun isAvailable(): Boolean = true

    override suspend fun search(query: String, limit: Int): List<SearchResult> {
        if (!semanticSearchEngine.isReady()) {
            Timber.d("Semantic search engine not ready, skipping")
            return emptyList()
        }

        val candidates = getCachedOrLoadCandidates()
        if (candidates.isEmpty()) {
            Timber.d("No valid candidates")
            return emptyList()
        }

        Timber.d("Semantic search: %d candidates", candidates.size)

        val results = semanticSearchEngine.findSimilarMultiVector(
            query = query,
            candidates = candidates,
            limit = limit,
        )

        Timber.d("Semantic search returned %d results for query: %s", results.size, query)
        return results
    }

    /** Invalidate cached candidates when embeddings change. */
    fun invalidateCandidateCache() {
        cachedCandidates = null
    }

    override fun invalidateCache() {
        invalidateCandidateCache()
    }

    private suspend fun getCachedOrLoadCandidates(): List<MemeWithEmbeddings> =
        cachedCandidates ?: memeEmbeddingDao.getMemesWithEmbeddings()
            .takeIf { it.isNotEmpty() }
            ?.let { rows ->
                buildCandidates(rows).also { cachedCandidates = it }
            }
            .orEmpty()

    @Suppress("NestedBlockDepth")
    private fun buildCandidates(
        rows: List<com.adsamcik.riposte.core.database.entity.MemeWithEmbeddingData>,
    ): List<MemeWithEmbeddings> {
        val groupedByMeme = rows
            .filter { it.embedding != null }
            .groupBy { it.memeId }

        return groupedByMeme.map { (_, memeRows) ->
            val first = memeRows.first()
            val meme = Meme(
                id = first.memeId,
                filePath = first.filePath,
                fileName = first.fileName,
                mimeType = "image/jpeg",
                width = 0,
                height = 0,
                fileSizeBytes = 0,
                importedAt = 0,
                emojiTags = MemeMapper.parseEmojiTagsJson(first.emojiTagsJson),
                title = first.title,
                description = first.description,
                textContent = first.textContent,
                useCount = first.useCount,
                viewCount = first.viewCount,
                isFavorite = first.isFavorite,
            )

            val embeddingsByType = memeRows
                .filter { it.embedding != null && it.embeddingType != null }
                .mapNotNull { row ->
                    val embedding = row.embedding ?: return@mapNotNull null
                    val decoded = decodeEmbedding(embedding)
                    if (decoded.size < 2) {
                        Timber.w("Skipping embedding with invalid dimensions: %d", decoded.size)
                        return@mapNotNull null
                    }
                    (row.embeddingType ?: "content") to decoded
                }
                .toMap()

            MemeWithEmbeddings(meme = meme, embeddings = embeddingsByType)
        }
    }

    private fun decodeEmbedding(bytes: ByteArray): FloatArray {
        val floatArray = FloatArray(bytes.size / BYTES_PER_FLOAT)
        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(floatArray)
        return floatArray
    }

    companion object {
        const val PRIORITY = 200
        private const val BYTES_PER_FLOAT = 4
    }
}
