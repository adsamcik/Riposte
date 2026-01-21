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
    // UX enhancement intents
    data class SelectQuickFilter(val filter: QuickFilter) : SearchIntent
    data object ClearQuickFilter : SearchIntent
    data class SetSortOrder(val order: SearchSortOrder) : SearchIntent
    data class SetViewMode(val mode: SearchViewMode) : SearchIntent
    data object StartVoiceSearch : SearchIntent
    data object StopVoiceSearch : SearchIntent
    data class VoiceSearchResult(val text: String) : SearchIntent
    data class DeleteRecentSearch(val query: String) : SearchIntent
}
