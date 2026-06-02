package com.adsamcik.riposte.feature.gallery.presentation

import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.search.domain.usecase.SearchUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
        private var currentSearchJob: Job? = null

        /**
         * Initialize reactive flows. Must be called once from the coordinator's viewModelScope.
         */
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        fun init(scope: CoroutineScope) {
            if (initScope != null) return
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
                            currentSearchJob?.cancel()
                            currentSearchJob = null
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
                    .catch { e ->
                        Timber.e(e, "Failed to observe recent searches")
                    }
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
                is GalleryIntent.SubmitSearch -> submitSearch(scope)
                else -> {} // Not a search intent
            }
        }

        private fun updateQuery(query: String) {
            currentSearchJob?.cancel()
            currentSearchJob = null
            _state.update { it.copy(query = query) }
            queryFlow.value = query
        }

        private fun clearSearch() {
            currentSearchJob?.cancel()
            currentSearchJob = null
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
                try {
                    searchUseCases.addRecentSearch(query)
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to add recent search")
                }
            }
            // Search is triggered immediately rather than waiting for debounce
            performSearch(query, scope = scope)
        }

        private fun submitSearch(scope: CoroutineScope) {
            val query = _state.value.query.trim()
            if (query.isBlank()) {
                Timber.d("Ignoring blank search submit")
                return
            }
            performSearch(query, scope = scope)
        }

        private fun deleteRecentSearch(
            query: String,
            scope: CoroutineScope,
        ) {
            scope.launch {
                try {
                    searchUseCases.deleteRecentSearch(query)
                    _state.update { state ->
                        state.copy(recentSearches = state.recentSearches - query)
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to delete recent search")
                }
            }
        }

        private fun clearRecentSearches(scope: CoroutineScope) {
            scope.launch {
                try {
                    searchUseCases.clearRecentSearches()
                    _state.update { it.copy(recentSearches = emptyList()) }
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to clear recent searches")
                }
            }
        }

        private fun performSearch(
            query: String,
            scope: CoroutineScope? = null,
        ) {
            val searchScope = scope ?: return
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank()) return
            currentSearchJob?.cancel()
            currentSearchJob = searchScope.launch {
                _state.update { it.copy(isSearching = true, searchError = null) }
                val startTime = System.currentTimeMillis()

                try {
                    val results = searchUseCases.hybridSearch(normalizedQuery)
                    updateSearchSuccess(results, System.currentTimeMillis() - startTime)
                    searchUseCases.addRecentSearch(normalizedQuery)
                } catch (e: UnsatisfiedLinkError) {
                    handleSearchFailure(e, SearchError.NotSupported, "Native library not available for semantic search")
                } catch (e: ExceptionInInitializerError) {
                    handleSearchFailure(e, SearchError.IndexFailed, "Embedding model initialization failed")
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                    e: Exception,
                ) {
                    handleSearchFailure(e, SearchError.Generic(e.message ?: "Search failed"), "Search failed")
                }
            }
        }

        private fun updateSearchSuccess(
            results: List<com.adsamcik.riposte.core.model.SearchResult>,
            durationMs: Long,
        ) {
            val hasSemanticResults = results.any {
                it.matchType == MatchType.SEMANTIC || it.matchType == MatchType.HYBRID
            }
            _state.update {
                it.copy(
                    results = results,
                    totalResultCount = results.size,
                    searchDurationMs = durationMs,
                    isSearching = false,
                    hasSearched = true,
                    isTextOnly = !hasSemanticResults,
                )
            }
        }

        private fun handleSearchFailure(
            throwable: Throwable,
            searchError: SearchError,
            logMessage: String,
        ) {
            Timber.e(throwable, logMessage)
            _state.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    searchError = searchError,
                )
            }
        }

        /**
         * Invalidate cached search data (e.g., embedding candidate caches).
         * Call when new embeddings are generated.
         */
        fun invalidateSearchCaches() {
            searchUseCases.invalidateSearchCaches()
        }

        companion object {
            private const val SEARCH_DEBOUNCE_MS = 200L
                private val INTERNAL_QUERY_REGEX= Regex("^(is|type):", RegexOption.IGNORE_CASE)

            fun String.isInternalQuerySyntax(): Boolean = INTERNAL_QUERY_REGEX.containsMatchIn(this.trim())
        }
    }
