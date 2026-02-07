package com.mememymood.feature.search.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememymood.core.common.di.DefaultDispatcher
import com.mememymood.core.common.suggestion.GetSuggestionsUseCase
import com.mememymood.core.common.suggestion.Surface
import com.mememymood.core.common.suggestion.SuggestionContext
import com.mememymood.core.common.util.normalizeEmoji
import com.mememymood.core.model.MatchType
import com.mememymood.core.model.SearchResult
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.feature.search.R
import com.mememymood.feature.search.domain.usecase.SearchUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val searchUseCases: SearchUseCases,
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val preferencesDataStore: PreferencesDataStore,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        loadRecentSearches()
        loadEmojiCounts()
        loadSuggestedMemes()
        observeQueryChanges()
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> updateQuery(intent.query)
            is SearchIntent.SetSearchMode -> setSearchMode(intent.mode)
            is SearchIntent.Search -> performSearch()
            is SearchIntent.ClearQuery -> clearQuery()
            is SearchIntent.ToggleEmojiFilter -> toggleEmojiFilter(intent.emoji)
            is SearchIntent.ClearEmojiFilters -> clearEmojiFilters()
            is SearchIntent.SelectRecentSearch -> selectRecentSearch(intent.query)
            is SearchIntent.ClearRecentSearches -> clearRecentSearches()
            is SearchIntent.SelectSuggestion -> selectSuggestion(intent.suggestion)
            is SearchIntent.MemeClicked -> navigateToMeme(intent.meme)
            // UX enhancement intents
            is SearchIntent.SelectQuickFilter -> selectQuickFilter(intent.filter)
            is SearchIntent.ClearQuickFilter -> clearQuickFilter()
            is SearchIntent.ResetSearch -> resetSearch()
            is SearchIntent.SetSortOrder -> setSortOrder(intent.order)
            is SearchIntent.SetViewMode -> setViewMode(intent.mode)
            is SearchIntent.StartVoiceSearch -> startVoiceSearch()
            is SearchIntent.StopVoiceSearch -> stopVoiceSearch()
            is SearchIntent.VoiceSearchResult -> handleVoiceResult(intent.text)
            is SearchIntent.DeleteRecentSearch -> deleteRecentSearch(intent.query)
        }
    }

    private fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    private fun observeQueryChanges() {
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        loadSuggestions(query)
                        performSearchInternal(query)
                    } else {
                        _uiState.update {
                            it.copy(
                                results = emptyList(),
                                suggestions = emptyList(),
                                hasSearched = false,
                            )
                        }
                    }
                }
        }
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            searchUseCases.getRecentSearches()
                .collectLatest { searches ->
                    val filtered = searches.filterNot { it.isInternalQuerySyntax() }
                    _uiState.update { it.copy(recentSearches = filtered) }
                }
        }
    }

    private fun loadEmojiCounts() {
        viewModelScope.launch {
            searchUseCases.getEmojiCounts()
                .collectLatest { counts ->
                    _uiState.update { it.copy(emojiCounts = counts) }
                }
        }
    }

    private fun loadSuggestedMemes() {
        viewModelScope.launch {
            searchUseCases.getAllMemes()
                .collectLatest { allMemes ->
                    val suggestions = withContext(defaultDispatcher) {
                        val recentSearches = _uiState.value.recentSearches
                        val ctx = SuggestionContext(
                            surface = Surface.SEARCH,
                            recentSearches = recentSearches,
                        )
                        getSuggestionsUseCase(allMemes, ctx)
                    }
                    _uiState.update { it.copy(suggestedMemes = suggestions) }
                }
        }
    }

    private fun loadSuggestions(query: String) {
        viewModelScope.launch {
            try {
                val suggestions = searchUseCases.getSearchSuggestions(query)
                _uiState.update { it.copy(suggestions = suggestions) }
            } catch (e: Exception) {
                // Ignore suggestion errors
            }
        }
    }

    private fun setSearchMode(mode: SearchMode) {
        _uiState.update { it.copy(searchMode = mode) }
        // Re-search with new mode
        val currentQuery = _uiState.value.query
        if (currentQuery.isNotBlank()) {
            performSearchInternal(currentQuery)
        }
    }

    private fun performSearch() {
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            _uiState.update { it.copy(hasSearched = true, isSearching = true) }
            performSearchInternal(query)
            viewModelScope.launch {
                // Don't save quick filter queries (e.g., "is:favorite") to recent searches
                if (_uiState.value.selectedQuickFilter == null) {
                    searchUseCases.addRecentSearch(query)
                }
                // Show smart search tip once after first search
                if (!preferencesDataStore.hasShownSearchTip.first()) {
                    preferencesDataStore.setSearchTipShown()
                    _effects.send(
                        SearchEffect.ShowSnackbar(
                            context.getString(R.string.search_tip_smart_search),
                        ),
                    )
                }
            }
        }
    }

    private fun performSearchInternal(query: String) {
        viewModelScope.launch {
            // Intercept quick filter queries before FTS sanitization
            val quickFilterResult = tryQuickFilterQuery(query)
            if (quickFilterResult != null) {
                quickFilterResult.collectLatest { results ->
                    val filteredResults = applyFilters(results)
                    val sortedResults = applySorting(filteredResults)
                    _uiState.update {
                        it.copy(
                            results = sortedResults,
                            totalResultCount = filteredResults.size,
                            searchDurationMs = 0L,
                            isSearching = false,
                            hasSearched = true,
                        )
                    }
                }
                return@launch
            }

            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            val startTime = System.currentTimeMillis()

            try {
                when (_uiState.value.searchMode) {
                    SearchMode.TEXT -> {
                        searchUseCases.search(query).collectLatest { results ->
                            val filteredResults = applyFilters(results)
                            val sortedResults = applySorting(filteredResults)
                            val endTime = System.currentTimeMillis()
                            _uiState.update {
                                it.copy(
                                    results = sortedResults,
                                    totalResultCount = filteredResults.size,
                                    searchDurationMs = endTime - startTime,
                                    isSearching = false,
                                    hasSearched = true,
                                )
                            }
                        }
                    }
                    SearchMode.SEMANTIC -> {
                        try {
                            val results = searchUseCases.semanticSearch(query)
                            val filteredResults = applyFilters(results)
                            val sortedResults = applySorting(filteredResults)
                            val endTime = System.currentTimeMillis()
                            _uiState.update {
                                it.copy(
                                    results = sortedResults,
                                    totalResultCount = filteredResults.size,
                                    searchDurationMs = endTime - startTime,
                                    isSearching = false,
                                    hasSearched = true,
                                )
                            }
                        } catch (@Suppress("SwallowedException") e: UnsatisfiedLinkError) {
                            fallbackToTextSearch(query, startTime)
                        } catch (@Suppress("SwallowedException") e: ExceptionInInitializerError) {
                            fallbackToTextSearch(query, startTime)
                        }
                    }
                    SearchMode.HYBRID -> {
                        try {
                            val results = searchUseCases.hybridSearch(query)
                            val filteredResults = applyFilters(results)
                            val sortedResults = applySorting(filteredResults)
                            val endTime = System.currentTimeMillis()
                            _uiState.update {
                                it.copy(
                                    results = sortedResults,
                                    totalResultCount = filteredResults.size,
                                    searchDurationMs = endTime - startTime,
                                    isSearching = false,
                                    hasSearched = true,
                                )
                            }
                        } catch (@Suppress("SwallowedException") e: UnsatisfiedLinkError) {
                            fallbackToTextSearch(query, startTime)
                        } catch (@Suppress("SwallowedException") e: ExceptionInInitializerError) {
                            fallbackToTextSearch(query, startTime)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        hasSearched = true,
                        errorMessage = e.message ?: context.getString(R.string.search_error_failed),
                    )
                }
                _effects.send(SearchEffect.ShowError(e.message ?: context.getString(R.string.search_error_failed)))
            }
        }
    }

    private fun applyFilters(results: List<com.mememymood.core.model.SearchResult>): List<com.mememymood.core.model.SearchResult> {
        var filtered = results
        
        // Apply emoji filters with normalization for variation selector tolerance
        val emojiFilters = _uiState.value.selectedEmojiFilters
        if (emojiFilters.isNotEmpty()) {
            val normalizedFilters = emojiFilters.map { normalizeEmoji(it) }.toSet()
            filtered = filtered.filter { result ->
                val normalizedMemeEmojis = result.meme.emojiTags.map { normalizeEmoji(it.emoji) }
                normalizedFilters.any { it in normalizedMemeEmojis }
            }
        }
        
        // Apply quick filter emoji if present
        val quickFilter = _uiState.value.selectedQuickFilter
        if (quickFilter?.emojiFilter != null) {
            val normalizedFilterEmoji = normalizeEmoji(quickFilter.emojiFilter)
            filtered = filtered.filter { result ->
                result.meme.emojiTags.any { normalizeEmoji(it.emoji) == normalizedFilterEmoji }
            }
        }
        
        return filtered
    }
    
    private fun applySorting(results: List<com.mememymood.core.model.SearchResult>): List<com.mememymood.core.model.SearchResult> {
        return when (_uiState.value.sortOrder) {
            SearchSortOrder.RELEVANCE -> results.sortedByDescending { it.relevanceScore }
            SearchSortOrder.NEWEST -> results.sortedByDescending { it.meme.createdAt }
            SearchSortOrder.OLDEST -> results.sortedBy { it.meme.createdAt }
            SearchSortOrder.MOST_USED -> results.sortedByDescending { it.meme.useCount }
        }
    }

    private fun applyEmojiFilters(results: List<com.mememymood.core.model.SearchResult>): List<com.mememymood.core.model.SearchResult> {
        val filters = _uiState.value.selectedEmojiFilters
        if (filters.isEmpty()) return results

        val normalizedFilters = filters.map { normalizeEmoji(it) }.toSet()
        return results.filter { result ->
            val normalizedMemeEmojis = result.meme.emojiTags.map { normalizeEmoji(it.emoji) }
            normalizedFilters.any { it in normalizedMemeEmojis }
        }
    }

    private suspend fun fallbackToTextSearch(query: String, startTime: Long) {
        try {
            searchUseCases.search(query).collectLatest { results ->
                val filteredResults = applyFilters(results)
                val sortedResults = applySorting(filteredResults)
                val endTime = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        results = sortedResults,
                        totalResultCount = filteredResults.size,
                        searchDurationMs = endTime - startTime,
                        isSearching = false,
                        hasSearched = true,
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    errorMessage = e.message ?: context.getString(R.string.search_error_failed),
                )
            }
        }
    }

    private fun clearQuery() {
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                suggestions = emptyList(),
                hasSearched = false,
                selectedQuickFilter = null,
                selectedEmojiFilters = emptyList(),
            )
        }
        queryFlow.value = ""
    }

    /**
     * Atomically resets all search state — query, quick filters, emoji filters, results.
     * Unlike calling clearQuery/clearEmojiFilters/clearQuickFilter separately,
     * this avoids intermediate states that re-trigger searches.
     */
    private fun resetSearch() {
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                suggestions = emptyList(),
                hasSearched = false,
                selectedQuickFilter = null,
                selectedEmojiFilters = emptyList(),
            )
        }
        queryFlow.value = ""
    }

    private fun toggleEmojiFilter(emoji: String) {
        val normalized = normalizeEmoji(emoji)
        _uiState.update { state ->
            val currentFilters = state.selectedEmojiFilters
            val newFilters = if (normalized in currentFilters) {
                currentFilters - normalized
            } else {
                currentFilters + normalized
            }
            state.copy(selectedEmojiFilters = newFilters)
        }
        // Always perform search when emoji filters change — emoji filtering IS searching
        performEmojiFilterSearch()
    }

    private fun clearEmojiFilters() {
        _uiState.update { it.copy(selectedEmojiFilters = emptyList()) }
        // Re-evaluate search state after clearing filters
        performEmojiFilterSearch()
    }

    private fun performEmojiFilterSearch() {
        val currentQuery = _uiState.value.query
        val emojiFilters = _uiState.value.selectedEmojiFilters
        when {
            currentQuery.isNotBlank() -> performSearchInternal(currentQuery)
            emojiFilters.isNotEmpty() -> {
                viewModelScope.launch {
                    searchUseCases.getAllMemes()
                        .map { memes ->
                            memes.map { meme ->
                                SearchResult(
                                    meme = meme,
                                    relevanceScore = 1.0f,
                                    matchType = MatchType.EMOJI,
                                )
                            }
                        }
                        .collectLatest { allResults ->
                            val filteredResults = applyFilters(allResults)
                            val sortedResults = applySorting(filteredResults)
                            _uiState.update {
                                it.copy(
                                    results = sortedResults,
                                    totalResultCount = filteredResults.size,
                                    searchDurationMs = 0L,
                                    isSearching = false,
                                    hasSearched = true,
                                )
                            }
                        }
                }
            }
            else -> {
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        totalResultCount = 0,
                        hasSearched = false,
                    )
                }
            }
        }
    }

    private fun selectRecentSearch(query: String) {
        updateQuery(query)
        performSearch()
    }

    private fun clearRecentSearches() {
        viewModelScope.launch {
            searchUseCases.clearRecentSearches()
            _uiState.update { it.copy(recentSearches = emptyList()) }
        }
    }

    private fun selectSuggestion(suggestion: String) {
        val currentQuery = _uiState.value.query
        val words = currentQuery.split(" ").dropLast(1)
        val newQuery = (words + suggestion).joinToString(" ")
        updateQuery(newQuery)
        performSearch()
    }

    private fun navigateToMeme(meme: com.mememymood.core.model.Meme) {
        viewModelScope.launch {
            _effects.send(SearchEffect.NavigateToMeme(meme.id))
        }
    }

    // UX enhancement functions
    private fun selectQuickFilter(filter: QuickFilter) {
        viewModelScope.launch {
            _effects.send(SearchEffect.TriggerHapticFeedback)
        }
        _uiState.update { it.copy(selectedQuickFilter = filter) }
        
        // Apply filter's emoji if present
        if (filter.emojiFilter != null) {
            val currentFilters = _uiState.value.selectedEmojiFilters
            if (filter.emojiFilter !in currentFilters) {
                _uiState.update { 
                    it.copy(selectedEmojiFilters = currentFilters + filter.emojiFilter) 
                }
            }
        }
        
        // If filter has a query, search directly without updating the display query
        if (filter.query != null) {
            performSearchInternal(filter.query)
        } else if (_uiState.value.query.isNotBlank()) {
            performSearchInternal(_uiState.value.query)
        }
    }
    
    private fun clearQuickFilter() {
        _uiState.update {
            it.copy(
                selectedQuickFilter = null,
                results = emptyList(),
                hasSearched = false,
            )
        }
        // Re-search with existing user query if present
        if (_uiState.value.query.isNotBlank()) {
            performSearchInternal(_uiState.value.query)
        }
    }
    
    private fun setSortOrder(order: SearchSortOrder) {
        viewModelScope.launch {
            _effects.send(SearchEffect.TriggerHapticFeedback)
        }
        _uiState.update { it.copy(sortOrder = order) }
        
        // Re-sort current results
        if (_uiState.value.hasSearched && _uiState.value.results.isNotEmpty()) {
            val sortedResults = applySorting(_uiState.value.results)
            _uiState.update { it.copy(results = sortedResults) }
        }
    }
    
    private fun setViewMode(mode: SearchViewMode) {
        viewModelScope.launch {
            _effects.send(SearchEffect.TriggerHapticFeedback)
        }
        _uiState.update { it.copy(viewMode = mode) }
    }
    
    private fun startVoiceSearch() {
        _uiState.update { it.copy(isVoiceSearchActive = true) }
        viewModelScope.launch {
            _effects.send(SearchEffect.StartVoiceRecognition)
        }
    }
    
    private fun stopVoiceSearch() {
        _uiState.update { it.copy(isVoiceSearchActive = false) }
    }
    
    private fun handleVoiceResult(text: String) {
        _uiState.update { it.copy(isVoiceSearchActive = false) }
        if (text.isNotBlank()) {
            updateQuery(text)
            performSearch()
        }
    }
    
    private fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            searchUseCases.deleteRecentSearch(query)
            _uiState.update { state ->
                state.copy(recentSearches = state.recentSearches - query)
            }
        }
    }

    /**
     * Intercept quick filter queries (e.g., "is:favorite", "is:recent") before FTS sanitization.
     * Returns a Flow of results if the query matches a known filter, or null for regular search.
     */
    private fun tryQuickFilterQuery(query: String): kotlinx.coroutines.flow.Flow<List<com.mememymood.core.model.SearchResult>>? {
        return when (query.trim().lowercase()) {
            "is:favorite" -> searchUseCases.getFavoriteMemes()
            "is:recent" -> searchUseCases.getRecentMemes()
            "type:reaction" -> searchUseCases.search("reaction")
            "type:gif" -> searchUseCases.search("gif")
            else -> null
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L

        /** Matches internal query syntax that should never be shown to users. */
        private val INTERNAL_QUERY_REGEX = Regex("^(is|type):", RegexOption.IGNORE_CASE)

        fun String.isInternalQuerySyntax(): Boolean =
            INTERNAL_QUERY_REGEX.containsMatchIn(this.trim())
    }
}
