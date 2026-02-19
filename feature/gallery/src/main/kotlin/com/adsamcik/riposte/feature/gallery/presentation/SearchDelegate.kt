package com.adsamcik.riposte.feature.gallery.presentation

import com.adsamcik.riposte.core.model.MatchType
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
import timber.log.Timber
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
                            performSearch(query, scope = scope)
                        } else {
                            _state.update {
                                it.copy(
                                    results = emptyList(),
                                    hasSearched = false,
                                    isSearching = false,
                                    searchError = null,
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
        ) {
            when (intent) {
                is GalleryIntent.UpdateSearchQuery -> updateQuery(intent.query)
                is GalleryIntent.ClearSearch -> clearSearch()
                is GalleryIntent.SelectRecentSearch -> selectRecentSearch(intent.query, scope)
                is GalleryIntent.DeleteRecentSearch -> deleteRecentSearch(intent.query, scope)
                is GalleryIntent.ClearRecentSearches -> clearRecentSearches(scope)
                else -> {} // Not a search intent
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
        ) {
            updateQuery(query)
            scope.launch {
                searchUseCases.addRecentSearch(query)
            }
            performSearch(query, scope = scope)
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
            scope: CoroutineScope? = null,
        ) {
            val searchScope = scope ?: return
            searchScope.launch {
                _state.update { it.copy(isSearching = true, searchError = null) }
                val startTime = System.currentTimeMillis()

                try {
                    val results = searchUseCases.hybridSearch(query)
                    val endTime = System.currentTimeMillis()
                    val hasSemanticResults = results.any {
                        it.matchType == MatchType.SEMANTIC || it.matchType == MatchType.HYBRID
                    }

                    _state.update {
                        it.copy(
                            results = results,
                            totalResultCount = results.size,
                            searchDurationMs = endTime - startTime,
                            isSearching = false,
                            hasSearched = true,
                            isTextOnly = !hasSemanticResults,
                        )
                    }

                    searchUseCases.addRecentSearch(query)
                } catch (e: UnsatisfiedLinkError) {
                    Timber.e(e, "Native library not available for semantic search")
                    _state.update {
                        it.copy(
                            isSearching = false,
                            hasSearched = true,
                            searchError = SearchError.NotSupported,
                        )
                    }
                } catch (e: ExceptionInInitializerError) {
                    Timber.e(e, "Embedding model initialization failed")
                    _state.update {
                        it.copy(
                            isSearching = false,
                            hasSearched = true,
                            searchError = SearchError.IndexFailed,
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Search failed")
                    _state.update {
                        it.copy(
                            isSearching = false,
                            hasSearched = true,
                            searchError = SearchError.Generic(e.message ?: "Search failed"),
                        )
                    }
                }
            }
        }

        companion object {
            private const val SEARCH_DEBOUNCE_MS = 300L
                private val INTERNAL_QUERY_REGEX= Regex("^(is|type):", RegexOption.IGNORE_CASE)

            fun String.isInternalQuerySyntax(): Boolean = INTERNAL_QUERY_REGEX.containsMatchIn(this.trim())
        }
    }
