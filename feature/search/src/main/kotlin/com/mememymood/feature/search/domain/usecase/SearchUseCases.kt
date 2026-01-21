package com.mememymood.feature.search.domain.usecase

import com.mememymood.core.model.SearchResult
import com.mememymood.feature.search.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for hybrid search (combines text and semantic search).
 */
class HybridSearchUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 20): List<SearchResult> {
        return repository.searchHybrid(query, limit)
    }
}

/**
 * Use case for full-text search.
 */
class TextSearchUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    operator fun invoke(query: String): Flow<List<SearchResult>> {
        return repository.searchByText(query)
    }
}

/**
 * Use case for semantic search.
 */
class SemanticSearchUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 20): List<SearchResult> {
        return repository.searchSemantic(query, limit)
    }
}

/**
 * Use case for emoji search.
 */
class EmojiSearchUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    operator fun invoke(emoji: String): Flow<List<SearchResult>> {
        return repository.searchByEmoji(emoji)
    }
}

/**
 * Use case for getting search suggestions.
 */
class GetSearchSuggestionsUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    suspend operator fun invoke(prefix: String): List<String> {
        return repository.getSearchSuggestions(prefix)
    }
}

/**
 * Use case for managing recent searches.
 */
class RecentSearchesUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    fun getRecentSearches(): Flow<List<String>> = repository.getRecentSearches()
    
    suspend fun addRecentSearch(query: String) = repository.addRecentSearch(query)
    
    suspend fun clearRecentSearches() = repository.clearRecentSearches()
}

/**
 * Aggregated use cases for search functionality.
 */
class SearchUseCases @Inject constructor(
    private val repository: SearchRepository,
) {
    /**
     * Full text search with FTS.
     */
    fun search(query: String): Flow<List<SearchResult>> {
        return repository.searchMemes(query)
    }

    /**
     * Semantic (vector) search.
     */
    suspend fun semanticSearch(query: String, limit: Int = 20): List<SearchResult> {
        return repository.searchSemantic(query, limit)
    }

    /**
     * Hybrid search (combines text + semantic).
     */
    suspend fun hybridSearch(query: String, limit: Int = 20): List<SearchResult> {
        return repository.searchHybrid(query, limit)
    }

    /**
     * Get recent search queries.
     */
    fun getRecentSearches(): Flow<List<String>> {
        return repository.getRecentSearches()
    }

    /**
     * Get search suggestions based on prefix.
     */
    suspend fun getSearchSuggestions(prefix: String): List<String> {
        return repository.getSearchSuggestions(prefix)
    }

    /**
     * Add a search query to recent searches.
     */
    suspend fun addRecentSearch(query: String) {
        repository.addRecentSearch(query)
    }

    /**
     * Delete a specific recent search.
     */
    suspend fun deleteRecentSearch(query: String) {
        repository.deleteRecentSearch(query)
    }

    /**
     * Clear all recent searches.
     */
    suspend fun clearRecentSearches() {
        repository.clearRecentSearches()
    }
}
