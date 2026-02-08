package com.adsamcik.riposte.feature.gallery.presentation

import com.adsamcik.riposte.core.common.util.normalizeEmoji
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.search.domain.usecase.SearchUseCases
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Delegate handling inline search logic within the gallery.
 * Plain class (not a ViewModel) — the coordinator VM owns the scope.
 *
 * Always uses hybrid search (FTS results instant, semantic appended in background).
 */
class SearchDelegate @Inject constructor(
    private val searchUseCases: SearchUseCases,
) {
    private val _state = MutableStateFlow(SearchSliceState())
    val state: StateFlow<SearchSliceState> = _state.asStateFlow()

    private val _effects = Channel<GalleryEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val queryFlow = MutableStateFlow("")
    private var initScope: CoroutineScope? = null

    /**
     * Initialize reactive flows. Must be called once from the coordinator's viewModelScope.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun init(scope: CoroutineScope) {
        initScope = scope

        // Observe debounced query changes
        scope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        performSearch(query, activeEmojiFilters = emptySet(), scope = scope)
                    } else {
                        _state.update {
                            it.copy(
                                results = emptyList(),
                                suggestions = emptyList(),
                                hasSearched = false,
                                isSearching = false,
                                errorMessage = null,
                            )
                        }
                    }
                }
        }

        // Load recent searches reactively
        scope.launch {
            searchUseCases.getRecentSearches()
                .map { searches -> searches.filterNot { it.isInternalQuerySyntax() } }
                .collectLatest { filtered ->
                    _state.update { it.copy(recentSearches = filtered) }
                }
        }
    }

    fun onIntent(
        intent: GalleryIntent,
        scope: CoroutineScope,
        activeEmojiFilters: Set<String>,
    ) {
        when (intent) {
            is GalleryIntent.UpdateSearchQuery -> updateQuery(intent.query)
            is GalleryIntent.ClearSearch -> clearSearch()
            is GalleryIntent.SelectRecentSearch -> selectRecentSearch(intent.query, scope, activeEmojiFilters)
            is GalleryIntent.DeleteRecentSearch -> deleteRecentSearch(intent.query, scope)
            is GalleryIntent.ClearRecentSearches -> clearRecentSearches(scope)
            else -> {} // Not a search intent
        }
    }

    /**
     * Re-run the current search with updated emoji filters.
     * Called by the coordinator when emoji filters change while searching.
     */
    fun refilter(scope: CoroutineScope, activeEmojiFilters: Set<String>) {
        val query = _state.value.query
        if (query.isNotBlank()) {
            performSearch(query, activeEmojiFilters, scope = scope)
        }
    }

    private fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
        queryFlow.value = query
    }

    private fun clearSearch() {
        _state.update {
            SearchSliceState(recentSearches = it.recentSearches)
        }
        queryFlow.value = ""
    }

    private fun selectRecentSearch(query: String, scope: CoroutineScope, emojiFilters: Set<String>) {
        updateQuery(query)
        scope.launch {
            searchUseCases.addRecentSearch(query)
        }
        performSearch(query, emojiFilters, scope = scope)
    }

    private fun deleteRecentSearch(query: String, scope: CoroutineScope) {
        scope.launch {
            searchUseCases.deleteRecentSearch(query)
            _state.update { state ->
                state.copy(recentSearches = state.recentSearches - query)
            }
        }
    }

    private fun clearRecentSearches(scope: CoroutineScope) {
        scope.launch {
            searchUseCases.clearRecentSearches()
            _state.update { it.copy(recentSearches = emptyList()) }
        }
    }

    private fun performSearch(
        query: String,
        activeEmojiFilters: Set<String>,
        scope: CoroutineScope? = null,
    ) {
        val searchScope = scope ?: return
        searchScope.launch {
            _state.update { it.copy(isSearching = true, errorMessage = null) }
            val startTime = System.currentTimeMillis()

            try {
                val results = searchUseCases.hybridSearch(query)
                val filtered = applyEmojiFilters(results, activeEmojiFilters)
                val endTime = System.currentTimeMillis()

                _state.update {
                    it.copy(
                        results = filtered,
                        totalResultCount = filtered.size,
                        searchDurationMs = endTime - startTime,
                        isSearching = false,
                        hasSearched = true,
                    )
                }

                searchUseCases.addRecentSearch(query)
            } catch (@Suppress("SwallowedException") e: UnsatisfiedLinkError) {
                fallbackToTextSearch(query, startTime, activeEmojiFilters, searchScope)
            } catch (@Suppress("SwallowedException") e: ExceptionInInitializerError) {
                fallbackToTextSearch(query, startTime, activeEmojiFilters, searchScope)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        hasSearched = true,
                        errorMessage = e.message ?: "Search failed",
                    )
                }
            }
        }
    }

    private suspend fun fallbackToTextSearch(
        query: String,
        startTime: Long,
        activeEmojiFilters: Set<String>,
        scope: CoroutineScope,
    ) {
        try {
            searchUseCases.search(query).collectLatest { results ->
                val filtered = applyEmojiFilters(results, activeEmojiFilters)
                val endTime = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        results = filtered,
                        totalResultCount = filtered.size,
                        searchDurationMs = endTime - startTime,
                        isSearching = false,
                        hasSearched = true,
                    )
                }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    errorMessage = e.message ?: "Search failed",
                )
            }
        }
    }

    private fun applyEmojiFilters(
        results: List<SearchResult>,
        activeEmojiFilters: Set<String>,
    ): List<SearchResult> {
        if (activeEmojiFilters.isEmpty()) return results
        val normalizedFilters = activeEmojiFilters.map { normalizeEmoji(it) }.toSet()
        return results.filter { result ->
            val normalizedMemeEmojis = result.meme.emojiTags.map { normalizeEmoji(it.emoji) }
            normalizedFilters.any { it in normalizedMemeEmojis }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L

        private val INTERNAL_QUERY_REGEX = Regex("^(is|type):", RegexOption.IGNORE_CASE)

        fun String.isInternalQuerySyntax(): Boolean =
            INTERNAL_QUERY_REGEX.containsMatchIn(this.trim())
    }
}
