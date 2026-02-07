package com.mememymood.feature.gallery.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.mememymood.core.common.di.DefaultDispatcher
import com.mememymood.core.common.suggestion.GetSuggestionsUseCase
import com.mememymood.core.common.suggestion.Surface
import com.mememymood.core.common.suggestion.SuggestionContext
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.model.Meme
import com.mememymood.feature.gallery.R
import com.mememymood.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetAllMemeIdsUseCase
import com.mememymood.feature.gallery.domain.usecase.GetFavoritesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemeByIdUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemesByEmojiUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetPagedMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getMemesUseCase: GetMemesUseCase,
    private val getPagedMemesUseCase: GetPagedMemesUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val getMemesByEmojiUseCase: GetMemesByEmojiUseCase,
    private val getMemeByIdUseCase: GetMemeByIdUseCase,
    private val deleteMemeUseCase: DeleteMemesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val getAllMemeIdsUseCase: GetAllMemeIdsUseCase,
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val shareTargetRepository: com.mememymood.feature.gallery.domain.repository.ShareTargetRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val preferencesDataStore: PreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    /**
     * Paged memes flow for the "All" filter.
     * Only active when usePaging is true in UI state.
     */
    val pagedMemes: Flow<PagingData<Meme>> = getPagedMemesUseCase(viewModelScope)

    private val _effects = Channel<GalleryEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

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
            is GalleryIntent.SetSortOption -> setSortOption(intent.option)
            is GalleryIntent.SelectShareTarget -> selectShareTarget(intent.target)
            is GalleryIntent.QuickShareMore -> quickShareMore()
            is GalleryIntent.DismissQuickShare -> dismissQuickShare()
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
            getMemesUseCase().collectLatest { allMemes ->
                val suggestions = withContext(defaultDispatcher) {
                    val ctx = SuggestionContext(
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
            getMemesUseCase().collectLatest { allMemes ->
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
                        error = null
                    ) 
                }
            }
            is GalleryFilter.Favorites, is GalleryFilter.ByEmoji -> {
                // For filtered views, use regular list (typically smaller datasets)
                memesJob = viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, usePaging = false, error = null) }

                    val flow = when (filter) {
                        is GalleryFilter.Favorites -> getFavoritesUseCase()
                        is GalleryFilter.ByEmoji -> getMemesByEmojiUseCase(filter.emoji)
                        else -> return@launch // Should not happen
                    }

                    flow.collectLatest { memes ->
                        _uiState.update {
                            it.copy(
                                memes = memes,
                                isLoading = false,
                                error = null
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
            val newSelection = if (memeId in state.selectedMemeIds) {
                state.selectedMemeIds - memeId
            } else {
                state.selectedMemeIds + memeId
            }
            state.copy(
                selectedMemeIds = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
    }

    private fun startSelection(memeId: Long) {
        _uiState.update {
            it.copy(
                selectedMemeIds = setOf(memeId),
                isSelectionMode = true
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
                isSelectionMode = false
            )
        }
    }

    private fun selectAll() {
        viewModelScope.launch {
            val allIds = if (_uiState.value.usePaging) {
                // For paged data, fetch all IDs from database
                getAllMemeIdsUseCase().toSet()
            } else {
                // For list data, use the in-memory list
                _uiState.value.memes.map { it.id }.toSet()
            }
            _uiState.update { state ->
                state.copy(
                    selectedMemeIds = allIds,
                    isSelectionMode = true
                )
            }
        }
    }

    private fun toggleFavorite(memeId: Long) {
        viewModelScope.launch {
            toggleFavoriteUseCase(memeId).onFailure { error ->
                _effects.send(GalleryEffect.ShowError(error.message ?: context.getString(R.string.gallery_snackbar_favorite_failed)))
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
            deleteMemeUseCase(pendingDeleteIds)
                .onSuccess {
                    _effects.send(GalleryEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_deleted, pendingDeleteIds.size)))
                    clearSelection()
                }
                .onFailure { error ->
                    _effects.send(GalleryEffect.ShowError(error.message ?: context.getString(R.string.gallery_snackbar_delete_failed)))
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
        viewModelScope.launch {
            _effects.send(GalleryEffect.OpenShareSheet(_uiState.value.selectedMemeIds.toList()))
        }
    }

    private fun navigateToImport() {
        viewModelScope.launch {
            _effects.send(GalleryEffect.NavigateToImport)
        }
    }

    private fun quickShare(memeId: Long) {
        viewModelScope.launch {
            // Find the meme to show in the bottom sheet
            val meme = _uiState.value.memes.find { it.id == memeId }
                ?: _uiState.value.suggestions.find { it.id == memeId }
                ?: getMemeByIdUseCase(memeId)
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

    private fun selectShareTarget(target: com.mememymood.core.model.ShareTarget) {
        val meme = _uiState.value.quickShareMeme ?: return
        viewModelScope.launch {
            shareTargetRepository.recordShare(target)
            _uiState.update { it.copy(quickShareMeme = null, quickShareTargets = emptyList()) }
            _effects.send(GalleryEffect.LaunchQuickShare(meme, target))
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

    private fun toggleEmojiFilter(emoji: String) {
        _uiState.update { state ->
            val newFilters = if (emoji in state.activeEmojiFilters) {
                state.activeEmojiFilters - emoji
            } else {
                state.activeEmojiFilters + emoji
            }
            state.copy(activeEmojiFilters = newFilters)
        }
        recomputeDerivedState()
    }

    private fun clearEmojiFilters() {
        _uiState.update { it.copy(activeEmojiFilters = emptySet()) }
        recomputeDerivedState()
    }

    private fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
        recomputeDerivedState()
    }

    /**
     * Recomputes derived state (uniqueEmojis, filteredMemes) from the current memes list.
     * Called whenever memes, emoji filters, or sort option change.
     */
    private fun recomputeDerivedState() {
        val state = _uiState.value
        val memes = state.memes

        val uniqueEmojis = memes
            .flatMap { meme -> meme.emojiTags.map { it.emoji } }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

        val filtered = if (state.activeEmojiFilters.isEmpty()) {
            memes
        } else {
            memes.filter { meme ->
                meme.emojiTags.any { it.emoji in state.activeEmojiFilters }
            }
        }

        val sorted = applySortOption(filtered, state.sortOption)

        _uiState.update {
            it.copy(
                uniqueEmojis = uniqueEmojis,
                filteredMemes = sorted,
            )
        }
    }

    private fun applySortOption(memes: List<Meme>, sortOption: SortOption): List<Meme> =
        when (sortOption) {
            SortOption.Recent -> memes.sortedByDescending { it.importedAt }
            SortOption.MostUsed -> memes.sortedByDescending { it.useCount }
            SortOption.EmojiGroup -> memes.sortedWith(
                compareBy<Meme> { meme ->
                    meme.emojiTags.firstOrNull()?.emoji ?: "zzz"
                }.thenByDescending { it.importedAt },
            )
        }
}
