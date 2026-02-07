package com.mememymood.feature.search.presentation

import com.mememymood.core.model.Meme
import com.mememymood.core.model.SearchResult
import com.mememymood.feature.search.R

data class SearchUiState(
    val query: String = "",
    val searchMode: SearchMode = SearchMode.HYBRID,
    val results: List<SearchResult> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val suggestedMemes: List<Meme> = emptyList(),
    val selectedEmojiFilters: List<String> = emptyList(),
    val emojiCounts: List<Pair<String, Int>> = emptyList(),
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

    val activeFilterCount: Int
        get() {
            var count = 0
            if (selectedEmojiFilters.isNotEmpty()) count += selectedEmojiFilters.size
            if (selectedQuickFilter != null) count++
            if (searchMode != SearchMode.HYBRID) count++
            if (sortOrder != SearchSortOrder.RELEVANCE) count++
            if (viewMode != SearchViewMode.GRID) count++
            return count
        }

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
    val labelResId: Int,
    val emoji: String,
    val query: String? = null,
    val emojiFilter: String? = null,
) {
    companion object {
        fun defaultFilters() = listOf(
            QuickFilter("favorites", R.string.search_quick_filter_favorites, "⭐", query = "is:favorite"),
            QuickFilter("recent", R.string.search_quick_filter_recent, "🕐", query = "is:recent"),
            QuickFilter("funny", R.string.search_quick_filter_funny, "😂", emojiFilter = "😂"),
            QuickFilter("love", R.string.search_quick_filter_love, "❤️", emojiFilter = "❤️"),
            QuickFilter("reactions", R.string.search_quick_filter_reactions, "😮", query = "type:reaction"),
        )
    }
}
