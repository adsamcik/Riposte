package com.adsamcik.riposte.feature.gallery.presentation

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.adsamcik.riposte.core.common.di.DefaultDispatcher
import com.adsamcik.riposte.core.common.share.ShareMemeUseCase
import com.adsamcik.riposte.core.common.share.ShareRepository
import com.adsamcik.riposte.core.common.suggestion.GetSuggestionsUseCase
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.events.EmbeddingsReady
import com.adsamcik.riposte.core.events.EventBus
import com.adsamcik.riposte.core.events.MemeImported
import com.adsamcik.riposte.core.events.MemeShared
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.feature.gallery.R
import com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
import com.adsamcik.riposte.feature.gallery.domain.usecase.GalleryViewModelUseCases
import com.adsamcik.riposte.feature.gallery.domain.usecase.RecoverStaleImportsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class GalleryViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val useCases: GalleryViewModelUseCases,
        private val getSuggestionsUseCase: GetSuggestionsUseCase,
        private val shareMemeUseCase: ShareMemeUseCase,
        private val shareRepository: ShareRepository,
        private val galleryRepository: GalleryRepository,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
        private val preferencesDataStore: PreferencesDataStore,
        private val eventBus: EventBus,
        private val recoverStaleImportsUseCase: RecoverStaleImportsUseCase,
        val searchDelegate: SearchDelegate,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(GalleryUiState())
        val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

        /**
         * Paged memes flow for the "All" filter.
         * During import, sorts by newest-first so new memes appear at the top
         * without disrupting scroll position. Reverts to emoji sort when idle.
         */
        val pagedMemes: Flow<PagingData<Meme>> =
            _uiState
                .map { it.importStatus is ImportWorkStatus.InProgress }
                .distinctUntilChanged()
                .flatMapLatest { isImporting ->
                    val sortBy = if (isImporting) "recent" else "emoji"
                    galleryRepository.getPagedMemes(sortBy)
                }
                .cachedIn(viewModelScope)

        private val _effects = Channel<GalleryEffect>(Channel.BUFFERED)
        val effects = merge(_effects.receiveAsFlow(), searchDelegate.effects)

        private val workObserver = GalleryWorkObserver(context, viewModelScope, _uiState, ::dismissNotification)
        private val suggestionCoordinator = GallerySuggestionCoordinator(
            useCases = useCases,
            getSuggestionsUseCase = getSuggestionsUseCase,
            preferencesDataStore = preferencesDataStore,
            defaultDispatcher = defaultDispatcher,
            uiState = _uiState,
            effects = _effects,
        )

        /** Job for the current memes loading flow, canceled when filter changes. */
        private var memesJob: Job? = null

        /** Job for the delayed deletion, canceled when user triggers undo. */
        private var deleteJob: Job? = null

        init {
            loadPreferences()
            loadMemes()
            suggestionCoordinator.loadSuggestions(viewModelScope)
            suggestionCoordinator.checkShareTip(viewModelScope)
            searchDelegate.init(viewModelScope)
            observeSearchState()
            workObserver.observeImportWork()
            workObserver.observeEmbeddingWork()
            observeUniqueEmojis()
            observeFavoritesCount()
            observeEvents()
            recoverStaleImports()
        }

        fun onIntent(intent: GalleryIntent) {
            when (intent) {
                is GalleryIntent.LoadMemes -> loadMemes()
                is GalleryIntent.OpenMeme -> openMeme(intent.memeId)
                is GalleryIntent.ToggleSelection -> toggleSelection(intent.memeId)
                is GalleryIntent.StartSelection -> startSelection(intent.memeId)
                is GalleryIntent.EnterSelectionMode -> enterSelectionMode()
                is GalleryIntent.ClearSelection -> clearSelection()
                is GalleryIntent.SelectAll -> selectAll()
                is GalleryIntent.DeselectAll -> deselectAll()
                is GalleryIntent.ToggleFavorite -> toggleFavorite(intent.memeId)
                is GalleryIntent.DeleteSelected -> deleteSelected()
                is GalleryIntent.ConfirmDelete -> confirmDelete()
                is GalleryIntent.CancelDelete -> cancelDelete()
                is GalleryIntent.UndoDelete -> undoDelete()
                is GalleryIntent.SetFilter -> setFilter(intent.filter)
                is GalleryIntent.SetGridColumns -> setGridColumns(intent.columns)
                is GalleryIntent.ShareSelected -> shareSelected()
                is GalleryIntent.NavigateToImport -> navigateToImport()
                is GalleryIntent.QuickShare -> quickShare(intent.memeId)
                is GalleryIntent.DismissNotification -> dismissNotification()
                is GalleryIntent.SearchFieldFocusChanged -> setSearchFocused(intent.isFocused)
                // Search intents — delegate
                is GalleryIntent.UpdateSearchQuery,
                is GalleryIntent.SelectRecentSearch,
                is GalleryIntent.DeleteRecentSearch,
                is GalleryIntent.ClearRecentSearches,
                is GalleryIntent.ClearSearch,
                is GalleryIntent.SubmitSearch,
                -> searchDelegate.onIntent(intent, viewModelScope)
            }
        }

        /** Observe search delegate state and merge it into the unified UI state. */
        private fun observeSearchState() {
            viewModelScope.launch {
                searchDelegate.state.collectLatest { searchState ->
                    _uiState.update { state ->
                        val mode =
                            if (searchState.query.isNotBlank() || searchState.hasSearched) {
                                ScreenMode.Searching
                            } else {
                                ScreenMode.Browsing
                            }
                        state.copy(searchState = searchState, screenMode = mode)
                    }
                }
            }
        }

        /** Observe unique emojis from the database for the emoji filter rail. */
        private fun observeUniqueEmojis() {
            viewModelScope.launch {
                // React to the sortEmojisByUsage preference
                preferencesDataStore.appPreferences
                    .map { it.sortEmojisByUsage }
                    .distinctUntilChanged()
                    .collectLatest { sortByUsage ->
                        val flow = if (sortByUsage) {
                            useCases.getAllEmojisWithCounts()
                        } else {
                            useCases.getAllEmojisWithTagCounts()
                        }
                        flow.collectLatest { emojiCounts ->
                            _uiState.update { it.copy(uniqueEmojis = emojiCounts) }
                        }
                    }
            }
        }

        /** Observe favorites count for the conditional Favorites chip in search mode. */
        private fun observeFavoritesCount() {
            viewModelScope.launch {
                useCases.getLibraryStats()
                    .map { it.favoriteMemes }
                    .distinctUntilChanged()
                    .collectLatest { count ->
                        var shouldReloadMemes = false
                        _uiState.update { state ->
                            // Auto-clear Favorites filter when no favorites remain
                            if (count == 0 && state.filter is GalleryFilter.Favorites) {
                                shouldReloadMemes = true
                                state.copy(favoritesCount = count, filter = GalleryFilter.All)
                            } else {
                                state.copy(favoritesCount = count)
                            }
                        }
                        if (shouldReloadMemes) {
                            loadMemes()
                        }
                    }
            }
        }

        /**
         * React to domain events for cross-feature coordination.
         * Refreshes suggestions when memes are shared, imported, or newly searchable.
         */
        private fun observeEvents() {
            viewModelScope.launch {
                merge(
                    eventBus.on<MemeShared>(),
                    eventBus.on<MemeImported>(),
                    eventBus.on<EmbeddingsReady>(),
                ).collect { event ->
                    suggestionCoordinator.refreshSuggestions(viewModelScope)
                    if (event is EmbeddingsReady) {
                        searchDelegate.invalidateSearchCaches()
                    }
                }
            }
        }

        /**
         * On startup, detect import requests stuck in IN_PROGRESS for >30 minutes
         * with no active WorkManager work. Mark them as failed so they don't block
         * future imports. The user can re-import if needed.
         */
        private fun recoverStaleImports() {
            viewModelScope.launch {
                try {
                    val recovered = recoverStaleImportsUseCase(STALE_IMPORT_THRESHOLD_MS)
                    for (entry in recovered) {
                        _uiState.update {
                            it.copy(
                                notification = GalleryNotification.ImportFailed(
                                    context.getString(
                                        R.string.gallery_import_stalled,
                                        entry.completedCount,
                                        entry.imageCount,
                                    ),
                                ),
                            )
                        }
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Timber.w(e, "Failed to recover stale imports")
                }
            }
        }

        private fun loadPreferences() {
            viewModelScope.launch {
                preferencesDataStore.appPreferences.collectLatest { prefs ->
                    _uiState.update { it.copy(densityPreference = prefs.userDensityPreference) }
                }
            }
        }

        private fun loadMemes() {
            // Cancel any previous memes loading job to prevent concurrent collections
            memesJob?.cancel()

            val filter = _uiState.value.filter
            Timber.d("Loading memes with filter: %s", filter)

            // Use paging for "All" filter, regular list for filtered views
            when (filter) {
                is GalleryFilter.All -> {
                    // For All filter, use paging - the UI will collect from pagedMemes flow
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            usePaging = true,
                            memes = emptyList(),
                            error = null,
                        )
                    }
                }
                is GalleryFilter.Favorites -> {
                    // For filtered views, use regular list (typically smaller datasets)
                    memesJob =
                        viewModelScope.launch {
                            _uiState.update { it.copy(isLoading = true, usePaging = false, error = null) }

                            useCases.getFavorites().collectLatest { memes ->
                                _uiState.update {
                                    it.copy(
                                        memes = memes,
                                        isLoading = false,
                                        error = null,
                                    )
                                }
                            }
                        }
                }
            }
        }

        private fun openMeme(memeId: Long) {
            if (_uiState.value.isSelectionMode) {
                toggleSelection(memeId)
            } else {
                _uiState.update { it.copy(isSearchFocused = false) }
                viewModelScope.launch {
                    _effects.send(GalleryEffect.NavigateToMeme(memeId))
                }
            }
        }

        private fun toggleSelection(memeId: Long) {
            _uiState.update { state ->
                val newSelection =
                    if (memeId in state.selectedMemeIds) {
                        state.selectedMemeIds - memeId
                    } else {
                        state.selectedMemeIds + memeId
                    }
                state.copy(
                    selectedMemeIds = newSelection,
                    isSelectionMode = newSelection.isNotEmpty(),
                )
            }
        }

        private fun startSelection(memeId: Long) {
            _uiState.update {
                it.copy(
                    selectedMemeIds = setOf(memeId),
                    isSelectionMode = true,
                )
            }
        }

        private fun enterSelectionMode() {
            searchDelegate.onIntent(GalleryIntent.ClearSearch, viewModelScope)
            _uiState.update {
                it.copy(
                    isSelectionMode = true,
                    isSearchFocused = false,
                )
            }
        }

        private fun clearSelection() {
            _uiState.update {
                it.copy(
                    selectedMemeIds = emptySet(),
                    isSelectionMode = false,
                )
            }
        }

        private fun selectAll() {
            viewModelScope.launch {
                val allIds =
                    if (_uiState.value.usePaging) {
                        // For paged data, fetch all IDs from database
                        useCases.getAllMemeIds().toSet()
                    } else {
                        // For list data, use the in-memory list
                        _uiState.value.memes.map { it.id }.toSet()
                    }
                _uiState.update { state ->
                    state.copy(
                        selectedMemeIds = allIds,
                        isSelectionMode = true,
                    )
                }
            }
        }

        private fun deselectAll() {
            _uiState.update { state ->
                state.copy(selectedMemeIds = emptySet())
            }
        }

        private fun toggleFavorite(memeId: Long) {
            viewModelScope.launch {
                useCases.toggleFavorite(memeId).onSuccess {
                    _effects.send(GalleryEffect.TriggerHapticFeedback)
                }.onFailure { error ->
                    _effects.send(
                        GalleryEffect.ShowError(
                            error.message ?: context.getString(R.string.gallery_snackbar_favorite_failed),
                        ),
                    )
                }
            }
        }

        private fun deleteSelected() {
            _uiState.update { it.copy(pendingDeleteIds = it.selectedMemeIds) }
            viewModelScope.launch {
                _effects.send(GalleryEffect.ShowDeleteConfirmation(_uiState.value.pendingDeleteIds.size))
            }
        }

        private fun confirmDelete() {
            val deleteIds = _uiState.value.pendingDeleteIds
            val count = deleteIds.size
            clearSelection()

            // Start delayed deletion — gives user time to undo
            deleteJob?.cancel()
            deleteJob = viewModelScope.launch {
                _effects.send(
                    GalleryEffect.ShowUndoDeleteSnackbar(
                        message = context.getString(R.string.gallery_snackbar_deleted, count),
                        count = count,
                    ),
                )
                delay(UNDO_TIMEOUT_MS)
                // If we reach here, user didn't undo — perform actual deletion
                performDelete(deleteIds)
            }
        }

        private fun undoDelete() {
            deleteJob?.cancel()
            deleteJob = null
            _uiState.update { it.copy(pendingDeleteIds = emptySet()) }
        }

        private suspend fun performDelete(deleteIds: Set<Long>) {
            useCases.deleteMemes(deleteIds)
                .onFailure { error ->
                    Timber.e(error, "Failed to delete %d memes", deleteIds.size)
                    _effects.send(
                        GalleryEffect.ShowError(
                            error.message ?: context.getString(R.string.gallery_snackbar_delete_failed),
                        ),
                    )
                }
            _uiState.update { it.copy(pendingDeleteIds = emptySet()) }
        }

        private fun cancelDelete() {
            _uiState.update { it.copy(pendingDeleteIds = emptySet()) }
        }

        private fun setFilter(filter: GalleryFilter) {
            _uiState.update { it.copy(filter = filter) }
            loadMemes()
        }

        private fun setGridColumns(columns: Int) {
            viewModelScope.launch {
                preferencesDataStore.setGridColumns(columns)
            }
        }

        private fun shareSelected() {
            val selectedIds = _uiState.value.selectedMemeIds.toList()
            if (selectedIds.isEmpty()) return

            if (selectedIds.size == 1) {
                // Single meme: use preference-aware share path
                clearSelection()
                quickShare(selectedIds.first())
                return
            }

            // Multiple memes: use repository's MediaStore-backed batch share path
            // (FileProvider-based sharing crashes Discord's ShareActivity on re-grant —
            // MediaStore URIs are owned by the system MediaProvider so receivers can
            // grant them via their own READ_MEDIA_IMAGES permission).
            viewModelScope.launch {
                val memes =
                    selectedIds.mapNotNull { id ->
                        _uiState.value.memes.find { it.id == id }
                            ?: useCases.getMemeById(id)
                    }
                if (memes.isEmpty()) return@launch

                val config = shareRepository.getDefaultShareConfig()
                shareRepository.prepareMultipleForSharing(memes, config)
                    .onSuccess { uris ->
                        val intent = shareRepository.createMultipleShareIntent(uris, "image/*")
                        clearSelection()
                        _effects.send(GalleryEffect.LaunchShareIntent(intent))
                    }
                    .onFailure { error ->
                        Timber.e(error, "Multi-share failed for %d memes", memes.size)
                        _effects.send(
                            GalleryEffect.ShowError(
                                error.message ?: context.getString(R.string.gallery_error_default),
                            ),
                        )
                    }
            }
        }

        private fun navigateToImport() {
            viewModelScope.launch {
                _effects.send(GalleryEffect.NavigateToImport)
            }
        }

        private fun quickShare(memeId: Long) {
            viewModelScope.launch {
                shareMemeUseCase(memeId)
                    .onSuccess { intent ->
                        _effects.send(GalleryEffect.LaunchShareIntent(intent))
                    }
                    .onFailure { error ->
                        Timber.e(error, "Quick share failed for meme %d", memeId)
                        _effects.send(
                            GalleryEffect.ShowError(
                                error.message ?: context.getString(R.string.gallery_error_default),
                            ),
                        )
                    }
            }
        }

        private fun dismissNotification() {
            _uiState.update { it.copy(notification = null) }
        }

        private fun setSearchFocused(isFocused: Boolean) {
            _uiState.update { it.copy(isSearchFocused = isFocused) }
        }

        companion object {
            /** How long to wait before performing actual deletion, giving the user time to undo. */
            private const val UNDO_TIMEOUT_MS = 5_000L

            /** Imports stuck in IN_PROGRESS for longer than this are considered stale. */
            private const val STALE_IMPORT_THRESHOLD_MS = 30L * 60 * 1000
        }
    }
