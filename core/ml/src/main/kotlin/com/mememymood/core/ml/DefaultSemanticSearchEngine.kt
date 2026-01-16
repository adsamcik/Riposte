package com.mememymood.core.ml

import com.mememymood.core.model.MatchType
import com.mememymood.core.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Default implementation of semantic search using cosine similarity.
 */
@Singleton
class DefaultSemanticSearchEngine @Inject constructor(
    private val embeddingGenerator: EmbeddingGenerator
) : SemanticSearchEngine {

    override suspend fun findSimilar(
        query: String,
        candidates: List<MemeWithEmbedding>,
        limit: Int,
        threshold: Float
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // Generate query embedding
        val queryEmbedding = embeddingGenerator.generateFromText(query)

        // Calculate similarities and filter
        candidates
            .map { candidate ->
                val similarity = cosineSimilarity(queryEmbedding, candidate.embedding)
                SearchResult(
                    meme = candidate.meme,
                    relevanceScore = similarity,
                    matchType = MatchType.SEMANTIC
                )
            }
            .filter { it.relevanceScore >= threshold }
            .sortedByDescending { it.relevanceScore }
            .take(limit)
    }

    override fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) {
            "Embedding dimensions must match: ${embedding1.size} vs ${embedding2.size}"
        }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    override suspend fun isReady(): Boolean = embeddingGenerator.isReady()

    override suspend fun initialize() {
        embeddingGenerator.initialize()
    }

    override fun close() {
        embeddingGenerator.close()
    }
}
