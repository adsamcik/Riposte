package com.mememymood.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememymood.feature.search.domain.usecase.SearchUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchUseCases: SearchUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        loadRecentSearches()
        loadEmojiCounts()
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
                    _uiState.update { it.copy(recentSearches = searches) }
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
            performSearchInternal(query)
            viewModelScope.launch {
                searchUseCases.addRecentSearch(query)
            }
        }
    }

    private fun performSearchInternal(query: String) {
        viewModelScope.launch {
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
                    }
                    SearchMode.HYBRID -> {
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
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        hasSearched = true,
                        errorMessage = e.message ?: "Search failed",
                    )
                }
                _effects.send(SearchEffect.ShowError(e.message ?: "Search failed"))
            }
        }
    }

    private fun applyFilters(results: List<com.mememymood.core.model.SearchResult>): List<com.mememymood.core.model.SearchResult> {
        var filtered = results
        
        // Apply emoji filters
        val emojiFilters = _uiState.value.selectedEmojiFilters
        if (emojiFilters.isNotEmpty()) {
            filtered = filtered.filter { result ->
                val memeEmojis = result.meme.emojiTags.map { it.emoji }
                emojiFilters.any { it in memeEmojis }
            }
        }
        
        // Apply quick filter emoji if present
        val quickFilter = _uiState.value.selectedQuickFilter
        if (quickFilter?.emojiFilter != null) {
            filtered = filtered.filter { result ->
                result.meme.emojiTags.any { it.emoji == quickFilter.emojiFilter }
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

        return results.filter { result ->
            val memeEmojis = result.meme.emojiTags.map { it.emoji }
            filters.any { it in memeEmojis }
        }
    }

    private fun clearQuery() {
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                suggestions = emptyList(),
                hasSearched = false,
            )
        }
        queryFlow.value = ""
    }

    private fun toggleEmojiFilter(emoji: String) {
        _uiState.update { state ->
            val currentFilters = state.selectedEmojiFilters
            val newFilters = if (emoji in currentFilters) {
                currentFilters - emoji
            } else {
                currentFilters + emoji
            }
            state.copy(selectedEmojiFilters = newFilters)
        }

        // Re-apply filters
        val currentQuery = _uiState.value.query
        if (currentQuery.isNotBlank() && _uiState.value.hasSearched) {
            performSearchInternal(currentQuery)
        }
    }

    private fun clearEmojiFilters() {
        _uiState.update { it.copy(selectedEmojiFilters = emptyList()) }

        // Re-apply filters
        val currentQuery = _uiState.value.query
        if (currentQuery.isNotBlank() && _uiState.value.hasSearched) {
            performSearchInternal(currentQuery)
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
        
        // If filter has a query, use it
        if (filter.query != null) {
            updateQuery(filter.query)
            performSearch()
        } else if (_uiState.value.query.isNotBlank()) {
            performSearchInternal(_uiState.value.query)
        }
    }
    
    private fun clearQuickFilter() {
        _uiState.update { it.copy(selectedQuickFilter = null) }
        if (_uiState.value.query.isNotBlank() && _uiState.value.hasSearched) {
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

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
