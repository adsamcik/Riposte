package com.adsamcik.riposte.core.search.domain.repository

import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for search operations.
 */
interface SearchRepository {

    /**
     * Combined search on memes (alias for searchByText).
     * 
     * @param query The search query.
     * @return Flow of search results.
     */
    fun searchMemes(query: String): Flow<List<SearchResult>>

    /**
     * Perform full-text search on memes.
     * 
     * @param query The search query.
     * @return Flow of search results.
     */
    fun searchByText(query: String): Flow<List<SearchResult>>

    /**
     * Perform semantic search using AI embeddings.
     * 
     * @param query The search query.
     * @param limit Maximum number of results.
     * @return List of search results sorted by relevance.
     */
    suspend fun searchSemantic(query: String, limit: Int = 20): List<SearchResult>

    /**
     * Perform hybrid search (text + semantic).
     * 
     * @param query The search query.
     * @param limit Maximum number of results.
     * @return List of combined and deduplicated search results.
     */
    suspend fun searchHybrid(query: String, limit: Int = 20): List<SearchResult>

    /**
     * Search by emoji.
     * 
     * @param emoji The emoji character to search for.
     * @return Flow of search results.
     */
    fun searchByEmoji(emoji: String): Flow<List<SearchResult>>

    /**
     * Get search suggestions based on query prefix.
     * 
     * @param prefix The query prefix.
     * @return List of suggested search terms.
     */
    suspend fun getSearchSuggestions(prefix: String): List<String>

    /**
     * Get recent searches.
     */
    fun getRecentSearches(): Flow<List<String>>

    /**
     * Add a search to recent searches.
     */
    suspend fun addRecentSearch(query: String)

    /**
     * Delete a specific recent search.
     */
    suspend fun deleteRecentSearch(query: String)

    /**
     * Clear recent searches.
     */
    suspend fun clearRecentSearches()

    /**
     * Get all unique emojis with their usage counts.
     *
     * @return Flow of emoji-count pairs sorted by frequency descending.
     */
    fun getEmojiCounts(): Flow<List<Pair<String, Int>>>

    /**
     * Get all memes as a flow (for suggestion engine).
     *
     * @return Flow of all memes.
     */
    fun getAllMemes(): Flow<List<Meme>>

    /**
     * Get favorite memes as search results.
     *
     * @return Flow of favorite memes wrapped as search results.
     */
    fun getFavoriteMemes(): Flow<List<SearchResult>>

    /**
     * Get recently viewed memes as search results.
     *
     * @return Flow of recently viewed memes wrapped as search results.
     */
    fun getRecentMemes(): Flow<List<SearchResult>>
}
