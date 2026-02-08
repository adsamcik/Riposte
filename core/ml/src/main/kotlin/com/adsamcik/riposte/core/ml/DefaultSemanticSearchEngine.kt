package com.adsamcik.riposte.core.ml

import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.SearchResult
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

    private val queryEmbeddingCache: MutableMap<String, FloatArray> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, FloatArray>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>): Boolean {
                return size > MAX_CACHE_ENTRIES
            }
        }
    )

    override suspend fun findSimilar(
        query: String,
        candidates: List<MemeWithEmbedding>,
        limit: Int,
        threshold: Float
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()

        // Generate query embedding (cache to avoid regenerating for repeated queries)
        val queryEmbedding = try {
            queryEmbeddingCache[query]
                ?: embeddingGenerator.generateFromText(query).also {
                    queryEmbeddingCache[query] = it
                }
        } catch (e: UnsatisfiedLinkError) {
            return@withContext emptyList()
        } catch (e: ExceptionInInitializerError) {
            return@withContext emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }

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

    /**
     * Clears the query embedding cache.
     * Call when the embedding model changes.
     */
    fun clearCache() {
        queryEmbeddingCache.clear()
    }

    override fun close() {
        embeddingGenerator.close()
    }

    private companion object {
        const val MAX_CACHE_ENTRIES = 50
    }
}
