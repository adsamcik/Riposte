package com.adsamcik.riposte.core.model

import kotlinx.serialization.Serializable

/**
 * Search result containing a meme and its relevance score.
 */
@Serializable
data class SearchResult(
    val meme: Meme,
    /**
     * Relevance score from 0.0 to 1.0, where 1.0 is most relevant.
     */
    val relevanceScore: Float,
    /**
     * The type of match that produced this result.
     */
    val matchType: MatchType,
)

/**
 * Types of search matches.
 */
@Serializable
enum class MatchType {
    /**
     * Matched via full-text search on title, description, or text content.
     */
    TEXT,

    /**
     * Matched via emoji tag.
     */
    EMOJI,

    /**
     * Matched via semantic similarity (AI-powered).
     */
    SEMANTIC,

    /**
     * Combined match from multiple sources.
     */
    HYBRID,
}
