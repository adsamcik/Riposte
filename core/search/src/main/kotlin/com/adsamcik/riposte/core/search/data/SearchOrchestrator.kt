package com.adsamcik.riposte.core.search.data

import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.SearchMode
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.model.SearchStrategy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * Orchestrates multiple [SearchStrategy] implementations and fuses their
 * results using Reciprocal Rank Fusion (RRF).
 *
 * Available strategies are run in parallel. Results are merged by meme ID
 * and scored using RRF, which is robust to incompatible score scales across
 * strategies. Higher-priority strategies get a larger weight multiplier.
 *
 * If only FTS is available (e.g., ML model still loading), the user gets
 * instant results with no visible delay. When semantic search becomes
 * available, it seamlessly enhances future queries.
 */
@Singleton
class SearchOrchestrator @Inject constructor(
    private val strategies: Set<@JvmSuppressWildcards SearchStrategy>,
) {
    /**
     * Run available strategies in parallel and fuse results.
     *
     * @param query User search query (already trimmed, never blank).
     * @param limit Maximum number of results to return.
     * @param searchMode Which strategies to run. Defaults to [SearchMode.HYBRID] (all).
     * @return Fused list of [SearchResult] sorted by combined score.
     */
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        searchMode: SearchMode = SearchMode.HYBRID,
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val available = strategies.filter { it.isAvailable() && matchesMode(it, searchMode) }
        if (available.isEmpty()) return emptyList()

        Timber.d(
            "SearchOrchestrator: running %d strategies: %s",
            available.size,
            available.joinToString { "${it.name}(p=${it.priority})" },
        )

        val resultsByStrategy = coroutineScope {
            available.map { strategy ->
                async {
                    try {
                        strategy to strategy.search(query, limit)
                    } catch (
                        @Suppress("TooGenericExceptionCaught")
                        e: Exception,
                    ) {
                        Timber.w(e, "Search strategy '%s' failed", strategy.name)
                        strategy to emptyList()
                    }
                }
            }.map { it.await() }
        }

        return applyUsageReranking(fuseResults(resultsByStrategy, limit))
    }

    /**
     * Reciprocal Rank Fusion: for each meme, sum `weight / (k + rank)` across
     * all strategies. This naturally handles different score scales.
     *
     * Weight is derived from strategy priority: `priority / 100.0`.
     */
    private fun fuseResults(
        resultsByStrategy: List<Pair<SearchStrategy, List<SearchResult>>>,
        limit: Int,
    ): List<SearchResult> {
        // memeId → (best SearchResult, accumulated RRF score)
        val scoreMap = mutableMapOf<Long, Pair<SearchResult, Float>>()

        for ((strategy, results) in resultsByStrategy) {
            val weight = strategy.priority / PRIORITY_DIVISOR

            results.forEachIndexed { rank, result ->
                val rrfScore = weight / (RRF_K + rank + 1)
                val existing = scoreMap[result.meme.id]
                if (existing != null) {
                    scoreMap[result.meme.id] = Pair(
                        existing.first.copy(
                            relevanceScore = existing.second + rrfScore,
                            matchType = MatchType.HYBRID,
                        ),
                        existing.second + rrfScore,
                    )
                } else {
                    scoreMap[result.meme.id] = Pair(
                        result.copy(relevanceScore = rrfScore),
                        rrfScore,
                    )
                }
            }
        }

        return scoreMap.values
            .map { it.first }
            .sortedByDescending { it.relevanceScore }
            .take(limit)
    }

    /**
     * Apply a small usage-based boost so frequently-used and favorited
     * memes float up when relevance scores are close.
     * The boost is capped to prevent popular memes from overriding relevance.
     */
    private fun applyUsageReranking(results: List<SearchResult>): List<SearchResult> {
        return results
            .map { result ->
                val boost = computeUsageBoost(result.meme)
                if (boost > 0f) {
                    result.copy(relevanceScore = result.relevanceScore * (1f + boost))
                } else {
                    result
                }
            }
            .sortedByDescending { it.relevanceScore }
    }

    private fun computeUsageBoost(meme: com.adsamcik.riposte.core.model.Meme): Float {
        var boost = 0f
        if (meme.isFavorite) boost += FAVORITE_BOOST
        if (meme.useCount > 0) {
            boost += (ln(1.0 + meme.useCount).toFloat() / USE_COUNT_DIVISOR)
                .coerceAtMost(USE_COUNT_MAX_BOOST)
        }
        return boost.coerceAtMost(MAX_USAGE_BOOST)
    }

    companion object {
        private const val DEFAULT_LIMIT = 20

        /** RRF constant k — controls how much rank position matters. */
        private const val RRF_K = 60f

        /** Normalize priority to a weight multiplier. */
        private const val PRIORITY_DIVISOR = 100f

        /** Strategy name used by FTS search. */
        private const val FTS_STRATEGY_NAME = "fts"

        /** Strategy name used by semantic/vector search. */
        private const val SEMANTIC_STRATEGY_NAME = "semantic"

        /** Favorite memes get this multiplicative boost. */
        private const val FAVORITE_BOOST = 0.15f

        /** Divisor for log-scaled use count boost. */
        private const val USE_COUNT_DIVISOR = 10f

        /** Maximum boost from use count alone. */
        private const val USE_COUNT_MAX_BOOST = 0.2f

        /** Maximum total usage boost (prevents popularity from overriding relevance). */
        private const val MAX_USAGE_BOOST = 0.25f

        private fun matchesMode(strategy: SearchStrategy, mode: SearchMode): Boolean =
            when (mode) {
                SearchMode.HYBRID -> true
                SearchMode.FTS_ONLY -> strategy.name == FTS_STRATEGY_NAME
                SearchMode.SEMANTIC_ONLY -> strategy.name == SEMANTIC_STRATEGY_NAME
            }
    }
}
