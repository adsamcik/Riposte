package com.adsamcik.riposte.feature.gallery.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.adsamcik.riposte.core.common.di.DefaultDispatcher
import com.adsamcik.riposte.core.common.suggestion.GetSuggestionsUseCase
import com.adsamcik.riposte.core.common.suggestion.SuggestionContext
import com.adsamcik.riposte.core.common.suggestion.Surface
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.feature.gallery.R
import com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
import com.adsamcik.riposte.feature.gallery.domain.usecase.GalleryViewModelUseCases
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val useCases: GalleryViewModelUseCases,
        private val getSuggestionsUseCase: GetSuggestionsUseCase,
        private val shareTargetRepository: com.adsamcik.riposte.core.common.repository.ShareTargetRepository,
        private val galleryRepository: GalleryRepository,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
        private val preferencesDataStore: PreferencesDataStore,
        val searchDelegate: SearchDelegate,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(GalleryUiState())
        val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

        /**
         * Paged memes flow for the "All" filter.
         * Reacts to emoji filter changes — when filters are active, a filtered
         * PagingSource is used so filtering happens at the SQL level instead of
         * O(n) iteration during Compose recomposition.
         */
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val pagedMemes: Flow<PagingData<Meme>> =
            _uiState
                .map { it.activeEmojiFilters }
                .distinctUntilChanged()
                .flatMapLatest { emojiFilters ->
                    if (emojiFilters.isEmpty()) {
                        galleryRepository.getPagedMemes("emoji")
                    } else {
                        galleryRepository.getPagedMemesByEmojis(emojiFilters)
                    }
                }
                .cachedIn(viewModelScope)

        private val _effects = Channel<GalleryEffect>(Channel.BUFFERED)
        val effects = merge(_effects.receiveAsFlow(), searchDelegate.effects)

        private var pendingDeleteIds: Set<Long> = emptySet()

        /** IDs of suggestions shown in the previous session (for staleness rotation). */
        private var lastSessionSuggestionIds: Set<Long> = emptySet()

        /** Job for the current memes loading flow, canceled when filter changes. */
        private var memesJob: Job? = null

        init {
            loadPreferences()
            loadMemes()
            loadSuggestions()
            checkShareTip()
            searchDelegate.init(viewModelScope)
            observeSearchState()
            observeImportWork()
            observeUniqueEmojis()
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
                is GalleryIntent.ToggleFavorite -> toggleFavorite(intent.memeId)
                is GalleryIntent.DeleteSelected -> deleteSelected()
                is GalleryIntent.ConfirmDelete -> confirmDelete()
                is GalleryIntent.CancelDelete -> cancelDelete()
                is GalleryIntent.SetFilter -> setFilter(intent.filter)
                is GalleryIntent.SetGridColumns -> setGridColumns(intent.columns)
                is GalleryIntent.ShareSelected -> shareSelected()
                is GalleryIntent.NavigateToImport -> navigateToImport()
                is GalleryIntent.QuickShare -> quickShare(intent.memeId)
                is GalleryIntent.ToggleEmojiFilter -> toggleEmojiFilter(intent.emoji)
                is GalleryIntent.ClearEmojiFilters -> clearEmojiFilters()
                is GalleryIntent.SelectShareTarget -> selectShareTarget(intent.target)
                is GalleryIntent.QuickShareMore -> quickShareMore()
                is GalleryIntent.DismissQuickShare -> dismissQuickShare()
                is GalleryIntent.CopyToClipboard -> copyToClipboard()
                is GalleryIntent.DismissImportStatus -> dismissImportStatus()
                // Search intents — delegate
                is GalleryIntent.UpdateSearchQuery,
                is GalleryIntent.ClearSearch,
                is GalleryIntent.SelectRecentSearch,
                is GalleryIntent.DeleteRecentSearch,
                is GalleryIntent.ClearRecentSearches,
                -> searchDelegate.onIntent(intent, viewModelScope, _uiState.value.activeEmojiFilters)
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
                useCases.getAllEmojisWithCounts().collectLatest { emojiCounts ->
                    _uiState.update { it.copy(uniqueEmojis = emojiCounts) }
                }
            }
        }

        /** Observe WorkManager for active import work and update UI state. */
        private fun observeImportWork() {
            viewModelScope.launch {
                try {
                    androidx.work.WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWorkFlow(com.adsamcik.riposte.core.common.AppConstants.IMPORT_WORK_NAME)
                        .collectLatest { workInfos ->
                            val workInfo = workInfos.firstOrNull()
                            val status =
                                when (workInfo?.state) {
                                    androidx.work.WorkInfo.State.RUNNING -> {
                                        val completed = workInfo.progress.getInt("completed", 0)
                                        val total = workInfo.progress.getInt("total", 0)
                                        if (total > 0) {
                                            ImportWorkStatus.InProgress(completed, total)
                                        } else {
                                            ImportWorkStatus.InProgress(0, 0)
                                        }
                                    }
                                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                                        val completed = workInfo.outputData.getInt("completed", 0)
                                        val failed = workInfo.outputData.getInt("failed", 0)
                                        ImportWorkStatus.Completed(completed, failed)
                                    }
                                    androidx.work.WorkInfo.State.FAILED -> {
                                        ImportWorkStatus.Failed()
                                    }
                                    else -> ImportWorkStatus.Idle
                                }
                            _uiState.update { it.copy(importStatus = status) }
                            if (status is ImportWorkStatus.Completed) {
                                delay(5000L)
                                dismissImportStatus()
                            }
                        }
                } catch (_: IllegalStateException) {
                    // WorkManager not initialized — safe to ignore in tests
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

        private fun loadSuggestions() {
            viewModelScope.launch {
                // Load persisted staleness IDs from DataStore
                preferencesDataStore.lastSessionSuggestionIds.collectLatest { persistedIds ->
                    lastSessionSuggestionIds = persistedIds
                }
            }
            viewModelScope.launch {
                useCases.getMemes().collectLatest { allMemes ->
                    val suggestions =
                        withContext(defaultDispatcher) {
                            val ctx =
                                SuggestionContext(
                                    surface = Surface.GALLERY,
                                    lastSessionSuggestionIds = lastSessionSuggestionIds,
                                )
                            getSuggestionsUseCase(allMemes, ctx)
                        }
                    lastSessionSuggestionIds = suggestions.map { it.id }.toSet()
                    preferencesDataStore.updateLastSessionSuggestionIds(lastSessionSuggestionIds)
                    _uiState.update { it.copy(suggestions = suggestions) }
                }
            }
        }

        private fun checkShareTip() {
            viewModelScope.launch {
                // Wait for memes to be available, then show share tip once
                useCases.getMemes().collectLatest { allMemes ->
                    if (allMemes.isNotEmpty() && !preferencesDataStore.hasShownShareTip.first()) {
                        preferencesDataStore.setShareTipShown()
                        _effects.send(
                            GalleryEffect.ShowSnackbar(
                                "\uD83D\uDCA1 Tip: Long-press any meme to quickly share it!",
                            ),
                        )
                    }
                }
            }
        }

        private fun loadMemes() {
            // Cancel any previous memes loading job to prevent concurrent collections
            memesJob?.cancel()

            val filter = _uiState.value.filter

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
                is GalleryFilter.Favorites, is GalleryFilter.ByEmoji -> {
                    // For filtered views, use regular list (typically smaller datasets)
                    memesJob =
                        viewModelScope.launch {
                            _uiState.update { it.copy(isLoading = true, usePaging = false, error = null) }

                            val flow =
                                when (filter) {
                                    is GalleryFilter.Favorites -> useCases.getFavorites()
                                    is GalleryFilter.ByEmoji -> useCases.getMemesByEmoji(filter.emoji)
                                    else -> return@launch // Should not happen
                                }

                            flow.collectLatest { memes ->
                                _uiState.update {
                                    it.copy(
                                        memes = memes,
                                        isLoading = false,
                                        error = null,
                                    )
                                }
                                recomputeDerivedState()
                            }
                        }
                }
            }
        }

        private fun openMeme(memeId: Long) {
            if (_uiState.value.isSelectionMode) {
                toggleSelection(memeId)
            } else {
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
            _uiState.update {
                it.copy(isSelectionMode = true)
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

        private fun toggleFavorite(memeId: Long) {
            viewModelScope.launch {
                useCases.toggleFavorite(memeId).onFailure { error ->
                    _effects.send(
                        GalleryEffect.ShowError(
                            error.message ?: context.getString(R.string.gallery_snackbar_favorite_failed),
                        ),
                    )
                }
            }
        }

        private fun deleteSelected() {
            pendingDeleteIds = _uiState.value.selectedMemeIds
            viewModelScope.launch {
                _effects.send(GalleryEffect.ShowDeleteConfirmation(pendingDeleteIds.size))
            }
        }

        private fun confirmDelete() {
            viewModelScope.launch {
                useCases.deleteMemes(pendingDeleteIds)
                    .onSuccess {
                        _effects.send(
                            GalleryEffect.ShowSnackbar(
                                context.getString(R.string.gallery_snackbar_deleted, pendingDeleteIds.size),
                            ),
                        )
                        clearSelection()
                    }
                    .onFailure { error ->
                        _effects.send(
                            GalleryEffect.ShowError(
                                error.message ?: context.getString(R.string.gallery_snackbar_delete_failed),
                            ),
                        )
                    }
                pendingDeleteIds = emptySet()
            }
        }

        private fun cancelDelete() {
            pendingDeleteIds = emptySet()
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
                // Single meme: use preference-aware quick share path
                clearSelection()
                quickShare(selectedIds.first())
                return
            }

            // Multiple memes: build ACTION_SEND_MULTIPLE intent with system chooser
            viewModelScope.launch {
                val memes =
                    selectedIds.mapNotNull { id ->
                        _uiState.value.memes.find { it.id == id }
                            ?: useCases.getMemeById(id)
                    }
                if (memes.isEmpty()) return@launch

                val uris =
                    ArrayList(
                        memes.map { meme ->
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                java.io.File(meme.filePath),
                            )
                        },
                    )
                val intent =
                    android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/*"
                        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                clearSelection()
                _effects.send(
                    GalleryEffect.LaunchQuickShare(android.content.Intent.createChooser(intent, null)),
                )
            }
        }

        private fun navigateToImport() {
            viewModelScope.launch {
                _effects.send(GalleryEffect.NavigateToImport)
            }
        }

        private fun quickShare(memeId: Long) {
            viewModelScope.launch {
                // Check if user prefers native share dialog
                val useNative = preferencesDataStore.sharingPreferences.first().useNativeShareDialog
                if (useNative) {
                    _effects.send(GalleryEffect.NavigateToShare(memeId))
                    return@launch
                }

                val meme =
                    _uiState.value.memes.find { it.id == memeId }
                        ?: _uiState.value.suggestions.find { it.id == memeId }
                        ?: _uiState.value.searchState.results.find { it.meme.id == memeId }?.meme
                        ?: useCases.getMemeById(memeId)
                if (meme == null) {
                    _effects.send(GalleryEffect.NavigateToShare(memeId))
                    return@launch
                }
                val targets = shareTargetRepository.getTopShareTargets(limit = 6)
                _uiState.update {
                    it.copy(quickShareMeme = meme, quickShareTargets = targets)
                }
            }
        }

        private fun selectShareTarget(target: com.adsamcik.riposte.core.model.ShareTarget) {
            val meme = _uiState.value.quickShareMeme ?: return
            viewModelScope.launch {
                shareTargetRepository.recordShare(target)
                _uiState.update { it.copy(quickShareMeme = null, quickShareTargets = emptyList()) }
                val intent =
                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = meme.mimeType
                        val uri =
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                java.io.File(meme.filePath),
                            )
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setClassName(target.packageName, target.activityName)
                    }
                _effects.send(GalleryEffect.LaunchQuickShare(intent))
            }
        }

        private fun quickShareMore() {
            val meme = _uiState.value.quickShareMeme ?: return
            _uiState.update { it.copy(quickShareMeme = null, quickShareTargets = emptyList()) }
            viewModelScope.launch {
                _effects.send(GalleryEffect.NavigateToShare(meme.id))
            }
        }

        private fun dismissQuickShare() {
            _uiState.update { it.copy(quickShareMeme = null, quickShareTargets = emptyList()) }
        }

        private fun copyToClipboard() {
            val meme = _uiState.value.quickShareMeme ?: return
            _uiState.update { it.copy(quickShareMeme = null, quickShareTargets = emptyList()) }
            viewModelScope.launch {
                _effects.send(GalleryEffect.CopyToClipboard(meme.id))
            }
        }

        private fun dismissImportStatus() {
            _uiState.update { it.copy(importStatus = ImportWorkStatus.Idle) }
        }

        private fun toggleEmojiFilter(emoji: String) {
            _uiState.update { state ->
                val newFilters =
                    if (emoji in state.activeEmojiFilters) {
                        state.activeEmojiFilters - emoji
                    } else {
                        state.activeEmojiFilters + emoji
                    }
                state.copy(activeEmojiFilters = newFilters)
            }
            recomputeDerivedState()
            if (_uiState.value.screenMode == ScreenMode.Searching) {
                searchDelegate.refilter(viewModelScope, _uiState.value.activeEmojiFilters)
            }
        }

        private fun clearEmojiFilters() {
            _uiState.update { it.copy(activeEmojiFilters = emptySet()) }
            recomputeDerivedState()
            if (_uiState.value.screenMode == ScreenMode.Searching) {
                searchDelegate.refilter(viewModelScope, emptySet())
            }
        }

        /**
         * Recomputes derived state (filteredMemes) from the current memes list.
         * Called whenever memes or emoji filters change.
         */
        private fun recomputeDerivedState() {
            val state = _uiState.value
            val memes = state.memes

            val filtered =
                if (state.activeEmojiFilters.isEmpty()) {
                    memes
                } else {
                    memes.filter { meme ->
                        meme.emojiTags.any { it.emoji in state.activeEmojiFilters }
                    }
                }

            _uiState.update {
                it.copy(
                    filteredMemes = sortByEmojiGroup(filtered),
                )
            }
        }

        /**
         * Groups memes by primary emoji, orders groups by aggregate engagement score
         * (most-used groups first), and sorts memes within each group by individual
         * engagement score. Uncategorized memes (no emoji tags) go last.
         */
        private fun sortByEmojiGroup(memes: List<Meme>): List<Meme> {
            val grouped =
                memes.groupBy { meme ->
                    meme.emojiTags.firstOrNull()?.emoji ?: UNCATEGORIZED_GROUP
                }
            return grouped.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, List<Meme>>> { (emoji, _) ->
                        if (emoji == UNCATEGORIZED_GROUP) -1.0 else 0.0
                    }.thenByDescending { (_, groupMemes) ->
                        groupMemes.sumOf { memeEngagementScore(it) }
                    },
                )
                .flatMap { (_, groupMemes) ->
                    groupMemes.sortedByDescending { memeEngagementScore(it) }
                }
        }

        companion object {
            private const val UNCATEGORIZED_GROUP = "❓"

            /** Lightweight engagement score matching TriSignalScorer formula. */
            private fun memeEngagementScore(meme: Meme): Double =
                (meme.useCount * 3.0) + (meme.viewCount * 0.5) + (if (meme.isFavorite) 5.0 else 0.0)
        }
    }
