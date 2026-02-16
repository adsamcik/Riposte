package com.adsamcik.riposte.feature.gallery.presentation

import com.adsamcik.riposte.core.common.util.normalizeEmoji
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
class SearchDelegate
    @Inject
    constructor(
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
                            performSearch(query, activeEmojiFilter = null, scope = scope)
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

            // Fetch autocomplete suggestions with a shorter debounce
            scope.launch {
                queryFlow
                    .debounce(SUGGESTION_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collectLatest { query ->
                        if (query.length >= MIN_SUGGESTION_LENGTH) {
                            try {
                                val suggestions = searchUseCases.getSearchSuggestions(query)
                                _state.update { it.copy(suggestions = suggestions) }
                            } catch (_: Exception) {
                                // Suggestions are best-effort; don't show errors
                            }
                        } else {
                            _state.update { it.copy(suggestions = emptyList()) }
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
            activeEmojiFilter: String?,
        ) {
            when (intent) {
                is GalleryIntent.UpdateSearchQuery -> updateQuery(intent.query)
                is GalleryIntent.ClearSearch -> clearSearch()
                is GalleryIntent.SelectRecentSearch -> selectRecentSearch(intent.query, scope, activeEmojiFilter)
                is GalleryIntent.SelectSuggestion -> selectSuggestion(intent.suggestion, scope, activeEmojiFilter)
                is GalleryIntent.DeleteRecentSearch -> deleteRecentSearch(intent.query, scope)
                is GalleryIntent.ClearRecentSearches -> clearRecentSearches(scope)
                else -> {} // Not a search intent
            }
        }

        /**
         * Re-run the current search with updated emoji filters.
         * Called by the coordinator when emoji filters change while searching.
         */
        fun refilter(
            scope: CoroutineScope,
            activeEmojiFilter: String?,
        ) {
            val query = _state.value.query
            if (query.isNotBlank()) {
                performSearch(query, activeEmojiFilter, scope = scope)
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

        private fun selectRecentSearch(
            query: String,
            scope: CoroutineScope,
            emojiFilter: String?,
        ) {
            updateQuery(query)
            scope.launch {
                searchUseCases.addRecentSearch(query)
            }
            performSearch(query, emojiFilter, scope = scope)
        }

        private fun selectSuggestion(
            suggestion: String,
            scope: CoroutineScope,
            emojiFilter: String?,
        ) {
            _state.update { it.copy(suggestions = emptyList()) }
            updateQuery(suggestion)
            scope.launch {
                searchUseCases.addRecentSearch(suggestion)
            }
            performSearch(suggestion, emojiFilter, scope = scope)
        }

        private fun deleteRecentSearch(
            query: String,
            scope: CoroutineScope,
        ) {
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
            activeEmojiFilter: String?,
            scope: CoroutineScope? = null,
        ) {
            val searchScope = scope ?: return
            searchScope.launch {
                _state.update { it.copy(isSearching = true, errorMessage = null) }
                val startTime = System.currentTimeMillis()

                try {
                    val results = searchUseCases.hybridSearch(query)
                    val filtered = applyEmojiFilter(results, activeEmojiFilter)
                    val endTime = System.currentTimeMillis()

                    _state.update {
                        it.copy(
                            results = filtered,
                            totalResultCount = filtered.size,
                            searchDurationMs = endTime - startTime,
                            isSearching = false,
                            hasSearched = true,
                            suggestions = emptyList(),
                        )
                    }

                    searchUseCases.addRecentSearch(query)
                } catch (
                    @Suppress("SwallowedException") e: UnsatisfiedLinkError,
                ) {
                    fallbackToTextSearch(query, startTime, activeEmojiFilter, searchScope)
                } catch (
                    @Suppress("SwallowedException") e: ExceptionInInitializerError,
                ) {
                    fallbackToTextSearch(query, startTime, activeEmojiFilter, searchScope)
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
            activeEmojiFilter: String?,
            scope: CoroutineScope,
        ) {
            try {
                searchUseCases.search(query).collectLatest { results ->
                    val filtered = applyEmojiFilter(results, activeEmojiFilter)
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

        private fun applyEmojiFilter(
            results: List<SearchResult>,
            activeEmojiFilter: String?,
        ): List<SearchResult> {
            if (activeEmojiFilter == null) return results
            val normalizedFilter = normalizeEmoji(activeEmojiFilter)
            return results.filter { result ->
                val normalizedMemeEmojis = result.meme.emojiTags.map { normalizeEmoji(it.emoji) }
                normalizedFilter in normalizedMemeEmojis
            }
        }

        companion object {
            private const val SEARCH_DEBOUNCE_MS = 300L
            private const val SUGGESTION_DEBOUNCE_MS = 150L
            private const val MIN_SUGGESTION_LENGTH = 2

            private val INTERNAL_QUERY_REGEX = Regex("^(is|type):", RegexOption.IGNORE_CASE)

            fun String.isInternalQuerySyntax(): Boolean = INTERNAL_QUERY_REGEX.containsMatchIn(this.trim())
        }
    }
