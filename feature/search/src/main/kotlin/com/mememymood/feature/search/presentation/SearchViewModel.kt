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

            try {
                when (_uiState.value.searchMode) {
                    SearchMode.TEXT -> {
                        searchUseCases.search(query).collectLatest { results ->
                            val filteredResults = applyEmojiFilters(results)
                            _uiState.update {
                                it.copy(
                                    results = filteredResults,
                                    isSearching = false,
                                    hasSearched = true,
                                )
                            }
                        }
                    }
                    SearchMode.SEMANTIC -> {
                        val results = searchUseCases.semanticSearch(query)
                        val filteredResults = applyEmojiFilters(results)
                        _uiState.update {
                            it.copy(
                                results = filteredResults,
                                isSearching = false,
                                hasSearched = true,
                            )
                        }
                    }
                    SearchMode.HYBRID -> {
                        val results = searchUseCases.hybridSearch(query)
                        val filteredResults = applyEmojiFilters(results)
                        _uiState.update {
                            it.copy(
                                results = filteredResults,
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

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
