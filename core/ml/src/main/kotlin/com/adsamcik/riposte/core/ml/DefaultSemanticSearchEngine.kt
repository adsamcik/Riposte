package com.adsamcik.riposte.core.ml

import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of semantic search using cosine similarity.
 */
@Singleton
class DefaultSemanticSearchEngine
    @Inject
    constructor(
        private val embeddingGenerator: EmbeddingGenerator,
        private val persistentCache: PersistentQueryEmbeddingCache,
    ) : SemanticSearchEngine {
        private val queryEmbeddingCache: MutableMap<String, FloatArray> =
            java.util.Collections.synchronizedMap(
                object : LinkedHashMap<String, FloatArray>(MAX_CACHE_ENTRIES, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>): Boolean {
                        return size > MAX_CACHE_ENTRIES
                    }
                },
            )

        override suspend fun findSimilar(
            query: String,
            candidates: List<MemeWithEmbedding>,
            limit: Int,
            threshold: Float,
        ): List<SearchResult> =
            withContext(Dispatchers.Default) {
                if (candidates.isEmpty()) return@withContext emptyList()

                val queryEmbedding =
                    try {
                        queryEmbeddingCache[query]
                            ?: persistentCache.get(query)?.also { queryEmbeddingCache[query] = it }
                            ?: embeddingGenerator.generateFromQuery(query).also {
                                queryEmbeddingCache[query] = it
                                persistentCache.put(query, it)
                            }
                    } catch (
                        @Suppress("TooGenericExceptionCaught") // ML libraries throw unpredictable exceptions
                        e: Exception,
                    ) {
                        Timber.w(e, "Failed to generate query embedding")
                        throw e
                    }

                // Calculate similarities and filter
                candidates
                    .map { candidate ->
                        val similarity = cosineSimilarity(queryEmbedding, candidate.embedding)
                        SearchResult(
                            meme = candidate.meme,
                            relevanceScore = similarity,
                            matchType = MatchType.SEMANTIC,
                        )
                    }
                    .filter { it.relevanceScore >= threshold }
                    .sortedByDescending { it.relevanceScore }
                    .take(limit)
            }

        override suspend fun findSimilarMultiVector(
            query: String,
            candidates: List<MemeWithEmbeddings>,
            limit: Int,
            threshold: Float,
        ): List<SearchResult> =
            withContext(Dispatchers.Default) {
                if (candidates.isEmpty()) return@withContext emptyList()

                val queryEmbedding =
                    try {
                        queryEmbeddingCache[query]
                            ?: persistentCache.get(query)?.also { queryEmbeddingCache[query] = it }
                            ?: embeddingGenerator.generateFromQuery(query).also {
                                queryEmbeddingCache[query] = it
                                persistentCache.put(query, it)
                            }
                    } catch (
                        @Suppress("TooGenericExceptionCaught") // ML libraries throw unpredictable exceptions
                        e: Exception,
                    ) {
                        Timber.w(e, "Failed to generate query embedding for multi-vector search")
                        throw e
                    }

                Timber.d(
                    "Query embedding: dim=%d",
                    queryEmbedding.size,
                )

                val scored = candidates
                    .map { candidate ->
                        // Max-pool: take the highest similarity across all embedding slots
                        val maxSimilarity =
                            candidate.embeddings.values
                                .filter { it.size == queryEmbedding.size }
                                .maxOfOrNull { cosineSimilarity(queryEmbedding, it) }
                                ?: 0f
                        SearchResult(
                            meme = candidate.meme,
                            relevanceScore = maxSimilarity,
                            matchType = MatchType.SEMANTIC,
                        )
                    }

                val topScores = scored.sortedByDescending { it.relevanceScore }.take(5)
                Timber.d(
                    "Top 5 similarities (threshold=%.2f): %s",
                    threshold,
                    topScores.joinToString { "%.4f".format(it.relevanceScore) },
                )

                scored
                    .filter { it.relevanceScore >= threshold }
                    .sortedByDescending { it.relevanceScore }
                    .take(limit)
            }

        override fun cosineSimilarity(
            embedding1: FloatArray,
            embedding2: FloatArray,
        ): Float = EmbeddingUtils.cosineSimilarity(embedding1, embedding2)

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
