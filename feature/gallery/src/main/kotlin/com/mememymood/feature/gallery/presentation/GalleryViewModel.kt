package com.mememymood.feature.gallery.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.model.ImageFormat
import com.mememymood.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetFavoritesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemesByEmojiUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import com.mememymood.feature.share.domain.usecase.ShareUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
    private val getMemesUseCase: GetMemesUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val getMemesByEmojiUseCase: GetMemesByEmojiUseCase,
    private val deleteMemeUseCase: DeleteMemesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val preferencesDataStore: PreferencesDataStore,
    private val shareUseCases: ShareUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

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
        memesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val flow = when (val filter = _uiState.value.filter) {
                is GalleryFilter.All -> getMemesUseCase()
                is GalleryFilter.Favorites -> getFavoritesUseCase()
                is GalleryFilter.ByEmoji -> getMemesByEmojiUseCase(filter.emoji)
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
        _uiState.update { state ->
            state.copy(
                selectedMemeIds = state.memes.map { it.id }.toSet(),
                isSelectionMode = true
            )
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
                    _effects.send(GalleryEffect.ShowSnackbar("${pendingDeleteIds.size} meme(s) deleted"))
                    clearSelection()
                }
                .onFailure { error ->
                    _effects.send(GalleryEffect.ShowError(error.message ?: "Failed to delete"))
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
            try {
                val meme = shareUseCases.getMeme(memeId)
                if (meme == null) {
                    _effects.send(GalleryEffect.ShowError("Meme not found"))
                    return@launch
                }

                val config = shareUseCases.getDefaultConfig()
                val result = shareUseCases.prepareForSharing(meme, config)

                result.fold(
                    onSuccess = { uri ->
                        val mimeType = when (config.format) {
                            ImageFormat.JPEG -> "image/jpeg"
                            ImageFormat.PNG -> "image/png"
                            ImageFormat.WEBP -> "image/webp"
                            ImageFormat.GIF -> "image/gif"
                        }
                        val intent = shareUseCases.createShareIntent(uri, mimeType)
                        _effects.send(GalleryEffect.LaunchShareIntent(intent))
                    },
                    onFailure = { error ->
                        _effects.send(GalleryEffect.ShowError(error.message ?: "Share failed"))
                    }
                )
            } catch (e: Exception) {
                _effects.send(GalleryEffect.ShowError(e.message ?: "Share failed"))
            }
        }
    }
}
