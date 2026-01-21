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
    // UX enhancements
    val searchDurationMs: Long = 0L,
    val totalResultCount: Int = 0,
    val isVoiceSearchActive: Boolean = false,
    val quickFilters: List<QuickFilter> = QuickFilter.defaultFilters(),
    val selectedQuickFilter: QuickFilter? = null,
    val sortOrder: SearchSortOrder = SearchSortOrder.RELEVANCE,
    val viewMode: SearchViewMode = SearchViewMode.GRID,
) {
    val hasActiveFilters: Boolean
        get() = selectedEmojiFilters.isNotEmpty() || selectedQuickFilter != null

    val resultSummary: String
        get() = when {
            !hasSearched -> ""
            isSearching -> "Searching..."
            results.isEmpty() -> "No results found"
            else -> "$totalResultCount result${if (totalResultCount != 1) "s" else ""} in ${searchDurationMs}ms"
        }
}

enum class SearchMode {
    TEXT,
    SEMANTIC,
    HYBRID,
}

enum class SearchSortOrder(val label: String) {
    RELEVANCE("Relevance"),
    NEWEST("Newest"),
    OLDEST("Oldest"),
    MOST_USED("Most Used"),
}

enum class SearchViewMode {
    GRID,
    LIST,
}

data class QuickFilter(
    val id: String,
    val label: String,
    val emoji: String,
    val query: String? = null,
    val emojiFilter: String? = null,
) {
    companion object {
        fun defaultFilters() = listOf(
            QuickFilter("favorites", "Favorites", "⭐", query = "is:favorite"),
            QuickFilter("recent", "Recent", "🕐", query = "is:recent"),
            QuickFilter("trending", "Trending", "🔥"),
            QuickFilter("funny", "Funny", "😂", emojiFilter = "😂"),
            QuickFilter("love", "Love", "❤️", emojiFilter = "❤️"),
            QuickFilter("reactions", "Reactions", "😮", query = "type:reaction"),
        )
    }
}
