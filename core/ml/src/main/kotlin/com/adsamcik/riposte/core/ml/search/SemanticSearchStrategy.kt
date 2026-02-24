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
 * Search strategy that uses AI embeddings for semantic similarity search.
 *
 * Available only when the embedding model has been initialized and there
 * are memes with generated embeddings in the database. Falls back gracefully
 * — the SearchOrchestrator simply skips this strategy if [isAvailable]
 * returns false.
 */
class SemanticSearchStrategy @Inject constructor(
    private val semanticSearchEngine: SemanticSearchEngine,
    private val memeEmbeddingDao: MemeEmbeddingDao,
) : SearchStrategy {

    override val name = "semantic"
    override val priority = PRIORITY

    override fun isAvailable(): Boolean = true

    override suspend fun search(query: String, limit: Int): List<SearchResult> {
        if (!semanticSearchEngine.isReady()) {
            Timber.d("Semantic search engine not ready, skipping")
            return emptyList()
        }

        val memesWithEmbeddings = memeEmbeddingDao.getMemesWithEmbeddings()
        if (memesWithEmbeddings.isEmpty()) return emptyList()

        val candidates = buildCandidates(memesWithEmbeddings)
        if (candidates.isEmpty()) return emptyList()

        return semanticSearchEngine.findSimilarMultiVector(
            query = query,
            candidates = candidates,
            limit = limit,
        )
    }

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
            )

            val embeddingsByType = memeRows
                .filter { it.embedding != null && it.embeddingType != null }
                .mapNotNull { row ->
                    val decoded = decodeEmbedding(row.embedding!!)
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
