package com.mememymood.feature.search.domain.repository

import com.mememymood.core.model.SearchResult
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
     * Clear recent searches.
     */
    suspend fun clearRecentSearches()
}
