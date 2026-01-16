package com.mememymood.feature.search.presentation

import com.mememymood.core.model.Meme
import com.mememymood.core.model.SearchResult

data class SearchUiState(
    val query: String = "",
    val searchMode: SearchMode = SearchMode.HYBRID,
    val results: List<SearchResult> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val selectedEmojiFilters: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

enum class SearchMode {
    TEXT,
    SEMANTIC,
    HYBRID,
}
