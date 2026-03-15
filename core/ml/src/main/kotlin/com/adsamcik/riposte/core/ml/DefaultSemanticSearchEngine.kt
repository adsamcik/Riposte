package com.adsamcik.riposte.core.ml

import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

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

        /** Model version when cache entries were created; cleared on mismatch. */
        private var cachedModelVersion: String = ""

        /**
         * Returns a cached query embedding if the model version hasn't changed,
         * otherwise invalidates the cache and returns null.
         */
        private fun getCachedQueryEmbedding(query: String): FloatArray? {
            val currentVersion = embeddingGenerator.modelVersion
            if (currentVersion.isNotEmpty() && currentVersion != cachedModelVersion) {
                queryEmbeddingCache.clear()
                cachedModelVersion = currentVersion
                return null
            }
            return queryEmbeddingCache[query]
        }

        private fun putCachedQueryEmbedding(query: String, embedding: FloatArray) {
            val currentVersion = embeddingGenerator.modelVersion
            if (currentVersion.isNotEmpty()) {
                cachedModelVersion = currentVersion
            }
            queryEmbeddingCache[query] = embedding
        }

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
                        getCachedQueryEmbedding(query)
                            ?: persistentCache.get(query)?.also {
                                putCachedQueryEmbedding(query, it)
                            }
                            ?: embeddingGenerator.generateFromQuery(query).also {
                                putCachedQueryEmbedding(query, it)
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
                    .filter { it.embedding.size == queryEmbedding.size }
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
                        getCachedQueryEmbedding(query)
                            ?: persistentCache.get(query)?.also {
                                putCachedQueryEmbedding(query, it)
                            }
                            ?: embeddingGenerator.generateFromQuery(query).also {
                                putCachedQueryEmbedding(query, it)
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
                        val relevance = computeWeightedSimilarity(queryEmbedding, candidate)
                        SearchResult(
                            meme = candidate.meme,
                            relevanceScore = relevance,
                            matchType = MatchType.SEMANTIC,
                        )
                    }

                val topScores = scored.sortedByDescending { it.relevanceScore }
                if (Timber.treeCount > 0) {
                    Timber.d(
                        "Top 5 scores: %s",
                        topScores.take(5).joinToString {
                            "${it.meme.title?.take(15)}=%.4f".format(it.relevanceScore)
                        },
                    )
                }

                applyDynamicThreshold(scored, limit, maxOf(threshold, ABSOLUTE_SIMILARITY_FLOOR))
            }

        override fun cosineSimilarity(
            embedding1: FloatArray,
            embedding2: FloatArray,
        ): Float = EmbeddingUtils.cosineSimilarity(embedding1, embedding2)

        /**
         * Compute similarity using only signal-bearing slots (intent, content).
         * Emoji and differentiator embeddings are too generic to discriminate.
         */
        private fun computeWeightedSimilarity(
            queryEmbedding: FloatArray,
            candidate: MemeWithEmbeddings,
        ): Float {
            val sims = candidate.embeddings
                .filter { (type, vec) ->
                    vec.size == queryEmbedding.size && type in SCORING_SLOTS
                }
                .map { (type, vec) ->
                    val weight = EMBEDDING_WEIGHTS[type] ?: DEFAULT_EMBEDDING_WEIGHT
                    val sim = cosineSimilarity(queryEmbedding, vec)
                    weight to sim
                }

            if (sims.isEmpty()) return 0f
            if (sims.size == 1) return sims.first().second

            val totalWeight = sims.sumOf { it.first.toDouble() }.toFloat()
            return sims.sumOf { (w, s) -> (w * s).toDouble() }.toFloat() / totalWeight
        }

        /**
         * Dynamic threshold using z-score normalization and gap detection.
         * Returns only results significantly above the mean similarity,
         * with a minimum floor to always show some results.
         *
         * @param absoluteFloor Hard minimum similarity — results below this are always excluded
         *                      (except when needed to reach [MIN_RESULTS]).
         */
        private fun applyDynamicThreshold(
            scored: List<SearchResult>,
            limit: Int,
            absoluteFloor: Float = 0f,
        ): List<SearchResult> {
            if (scored.isEmpty()) return emptyList()

            val sorted = scored.sortedByDescending { it.relevanceScore }

            // Hard floor: never return fewer than MIN_RESULTS (if enough candidates)
            val minResults = MIN_RESULTS.coerceAtMost(sorted.size)

            if (sorted.size <= minResults) return sorted.take(limit)

            val scores = sorted.map { it.relevanceScore }
            val mean = scores.average().toFloat()
            val stddev = sqrt(scores.map { (it - mean) * (it - mean) }.average()).toFloat()

            // Z-score cutoff: keep results above (mean + Z_CUTOFF * stddev)
            val zCutoff = if (stddev > STDDEV_FLOOR) {
                mean + Z_CUTOFF * stddev
            } else {
                // Scores are tightly clustered — use gap detection
                findGapCutoff(scores) ?: scores.last()
            }

            // Relative cutoff: only keep results within TOP_SCORE_RATIO of the best score
            val topScore = scores.first()
            val relativeCutoff = topScore * TOP_SCORE_RATIO

            // Effective cutoff is the strictest of all thresholds
            val effectiveCutoff = maxOf(zCutoff, absoluteFloor, relativeCutoff)

            val filtered = sorted.filter { it.relevanceScore >= effectiveCutoff }

            // Ensure minimum results (from candidates above absolute floor if possible)
            val result = if (filtered.size >= minResults) {
                filtered
            } else {
                // Respect absolute floor even when padding to minResults
                val aboveFloor = sorted.filter { it.relevanceScore >= absoluteFloor }
                if (aboveFloor.size >= minResults) {
                    aboveFloor.take(minResults)
                } else {
                    aboveFloor
                }
            }

            Timber.d(
                "Dynamic threshold: mean=%.4f, stddev=%.4f, zCutoff=%.4f, " +
                    "relativeCutoff=%.4f, floor=%.4f, %d/%d kept",
                mean, stddev, zCutoff, relativeCutoff, absoluteFloor, result.size, sorted.size,
            )

            return result.take(limit)
        }

        /**
         * Find the largest gap between consecutive sorted scores (descending).
         * Returns the score at which to cut, or null if no significant gap found.
         */
        private fun findGapCutoff(sortedScores: List<Float>): Float? {
            if (sortedScores.size < 3) return null

            val gaps = sortedScores.zipWithNext { a, b -> a - b }
            val meanGap = gaps.average().toFloat()

            var maxGapIdx = -1
            var maxGapValue = 0f
            gaps.forEachIndexed { idx, gap ->
                // Only consider gaps after minimum results
                if (idx >= MIN_RESULTS - 1 && gap > maxGapValue) {
                    maxGapValue = gap
                    maxGapIdx = idx
                }
            }

            // Gap must be significantly larger than average to be meaningful
            return if (maxGapIdx >= 0 && maxGapValue > meanGap * GAP_MULTIPLIER) {
                sortedScores[maxGapIdx + 1] // Cut at the score below the gap
            } else {
                null
            }
        }

        override suspend fun isReady(): Boolean = embeddingGenerator.isReady()

        override suspend fun initialize() {
            embeddingGenerator.initialize()
        }

        /**
         * Clears the query embedding cache.
         * Call when the embedding model changes.
         */
        override fun clearCache() {
            queryEmbeddingCache.clear()
        }

        override fun close() {
            embeddingGenerator.close()
        }

        private companion object {
            const val MAX_CACHE_ENTRIES = 50

            /** Minimum results to always return (prevents empty results). */
            const val MIN_RESULTS = 2

            /** Z-score cutoff: results must be this many stddevs above mean. */
            const val Z_CUTOFF = 0.5f

            /** Minimum stddev before falling back to gap detection. */
            const val STDDEV_FLOOR = 0.01f

            /** Gap must be this many times the mean gap to be significant. */
            const val GAP_MULTIPLIER = 2.0f

            /** Hard minimum cosine similarity — results below this are never returned. */
            const val ABSOLUTE_SIMILARITY_FLOOR = 0.10f

            /** Results must score at least this fraction of the top score. */
            const val TOP_SCORE_RATIO = 0.88f

            /** Default weight for unknown embedding types. */
            const val DEFAULT_EMBEDDING_WEIGHT = 0.5f

            /** Weights per embedding type for weighted fusion. */
            val EMBEDDING_WEIGHTS = mapOf(
                "content" to 0.25f,
                "intent" to 0.35f,
                "emotion" to 0.30f,
                "emoji" to 0.08f,
                "differentiator" to 0.02f,
            )

            /** Only these embedding slots are used for query→meme scoring. */
            val SCORING_SLOTS = setOf("intent", "content", "emotion", "emoji")
        }
    }
