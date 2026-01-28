package com.mememymood.feature.gallery.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.model.Meme
import com.mememymood.feature.gallery.R
import com.mememymood.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetAllMemeIdsUseCase
import com.mememymood.feature.gallery.domain.usecase.GetFavoritesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemesByEmojiUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetPagedMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getMemesUseCase: GetMemesUseCase,
    private val getPagedMemesUseCase: GetPagedMemesUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val getMemesByEmojiUseCase: GetMemesByEmojiUseCase,
    private val deleteMemeUseCase: DeleteMemesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val getAllMemeIdsUseCase: GetAllMemeIdsUseCase,
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
    
    /** Job for the current memes loading flow, canceled when filter changes. */
    private var memesJob: Job? = null

    init {
        loadPreferences()
        loadMemes()
    }

    fun onIntent(intent: GalleryIntent) {
        when (intent) {
            is GalleryIntent.LoadMemes -> loadMemes()
            is GalleryIntent.OpenMeme -> openMeme(intent.memeId)
            is GalleryIntent.ToggleSelection -> toggleSelection(intent.memeId)
            is GalleryIntent.StartSelection -> startSelection(intent.memeId)
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
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            preferencesDataStore.appPreferences.collectLatest { prefs ->
                _uiState.update { it.copy(gridColumns = prefs.gridColumns) }
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
                _effects.send(GalleryEffect.ShowError(error.message ?: "Failed to update favorite"))
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
        // Navigate to share screen instead of inline sharing
        // This removes the feature:gallery -> feature:share dependency
        viewModelScope.launch {
            _effects.send(GalleryEffect.NavigateToShare(memeId))
        }
    }
}
