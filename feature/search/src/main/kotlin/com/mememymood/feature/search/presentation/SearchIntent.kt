package com.mememymood.feature.search.presentation

sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data class SetSearchMode(val mode: SearchMode) : SearchIntent
    data object Search : SearchIntent
    data object ClearQuery : SearchIntent
    data class ToggleEmojiFilter(val emoji: String) : SearchIntent
    data object ClearEmojiFilters : SearchIntent
    data class SelectRecentSearch(val query: String) : SearchIntent
    data object ClearRecentSearches : SearchIntent
    data class SelectSuggestion(val suggestion: String) : SearchIntent
    data class MemeClicked(val meme: com.mememymood.core.model.Meme) : SearchIntent
}
