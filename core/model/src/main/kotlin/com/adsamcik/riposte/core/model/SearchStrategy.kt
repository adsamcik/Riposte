package com.adsamcik.riposte.core.model

import com.adsamcik.riposte.core.model.SearchResult

/**
 * A pluggable search strategy that can find memes matching a query.
 *
 * Strategies are registered via Hilt multibinding (`@IntoSet`) and
 * orchestrated by [SearchOrchestrator]. Each strategy declares a
 * [priority] that controls result fusion ranking — higher priority
 * strategies produce results that are weighted more heavily.
 *
 * Implementations must be safe to call concurrently from multiple
 * coroutines — the orchestrator runs available strategies in parallel.
 */
interface SearchStrategy {

    /** Human-readable name for logging and debugging (e.g., "fts", "semantic"). */
    val name: String

    /**
     * Priority for result fusion. Higher values are weighted more heavily.
     * Convention: FTS=100, Semantic=200, future strategies 300+.
     */
    val priority: Int

    /**
     * Whether this strategy is currently available.
     * For example, semantic search is unavailable until the ML model loads.
     */
    fun isAvailable(): Boolean

    /**
     * Execute the search and return ranked results.
     *
     * @param query User search query (already trimmed, never blank).
     * @param limit Maximum number of results to return.
     * @return List of [SearchResult] sorted by relevance (best first).
     */
    suspend fun search(query: String, limit: Int): List<SearchResult>
}
