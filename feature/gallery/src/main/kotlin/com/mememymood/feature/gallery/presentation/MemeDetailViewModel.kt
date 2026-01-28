package com.mememymood.feature.gallery.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememymood.core.model.EmojiTag
import com.mememymood.feature.gallery.R
import com.mememymood.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemeByIdUseCase
import com.mememymood.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import com.mememymood.feature.gallery.domain.usecase.UpdateMemeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemeDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val getMemeByIdUseCase: GetMemeByIdUseCase,
    private val updateMemeUseCase: UpdateMemeUseCase,
    private val deleteMemeUseCase: DeleteMemesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
) : ViewModel() {

    private val memeId: Long = savedStateHandle.get<Long>("memeId") ?: -1L

    private val _uiState = MutableStateFlow(MemeDetailUiState())
    val uiState: StateFlow<MemeDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<MemeDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadMeme()
    }

    fun onIntent(intent: MemeDetailIntent) {
        when (intent) {
            is MemeDetailIntent.LoadMeme -> loadMeme()
            is MemeDetailIntent.ToggleEditMode -> toggleEditMode()
            is MemeDetailIntent.UpdateTitle -> updateTitle(intent.title)
            is MemeDetailIntent.UpdateDescription -> updateDescription(intent.description)
            is MemeDetailIntent.AddEmoji -> addEmoji(intent.emoji)
            is MemeDetailIntent.RemoveEmoji -> removeEmoji(intent.emoji)
            is MemeDetailIntent.ToggleFavorite -> toggleFavorite()
            is MemeDetailIntent.ShowDeleteDialog -> showDeleteDialog()
            is MemeDetailIntent.DismissDeleteDialog -> dismissDeleteDialog()
            is MemeDetailIntent.ConfirmDelete -> confirmDelete()
            is MemeDetailIntent.Share -> share()
            is MemeDetailIntent.OpenShareScreen -> openShareScreen()
            is MemeDetailIntent.SaveChanges -> saveChanges()
            is MemeDetailIntent.DiscardChanges -> discardChanges()
            is MemeDetailIntent.ShowEmojiPicker -> showEmojiPicker()
            is MemeDetailIntent.DismissEmojiPicker -> dismissEmojiPicker()
            is MemeDetailIntent.Dismiss -> dismiss()
        }
    }

    private fun openShareScreen() {
        viewModelScope.launch {
            _effects.send(MemeDetailEffect.NavigateToShare(memeId))
        }
    }

    private fun loadMeme() {
        if (memeId == -1L) {
            _uiState.update { it.copy(isLoading = false, errorMessage = context.getString(R.string.gallery_error_invalid_meme_id)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val meme = getMemeByIdUseCase(memeId)
                if (meme != null) {
                    _uiState.update {
                        it.copy(
                            meme = meme,
                            editedTitle = meme.title ?: "",
                            editedDescription = meme.description ?: "",
                            editedEmojis = meme.emojiTags.map { tag -> tag.emoji },
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = context.getString(R.string.gallery_error_meme_not_found)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun toggleEditMode() {
        val currentState = _uiState.value
        if (currentState.isEditMode && currentState.hasUnsavedChanges) {
            // Ask to save changes before exiting edit mode
            viewModelScope.launch {
                _effects.send(MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_save_or_discard)))
            }
        } else {
            _uiState.update {
                it.copy(
                    isEditMode = !it.isEditMode,
                    editedTitle = it.meme?.title ?: "",
                    editedDescription = it.meme?.description ?: "",
                    editedEmojis = it.meme?.emojiTags?.map { tag -> tag.emoji } ?: emptyList(),
                )
            }
        }
    }

    private fun updateTitle(title: String) {
        _uiState.update { it.copy(editedTitle = title) }
    }

    private fun updateDescription(description: String) {
        _uiState.update { it.copy(editedDescription = description) }
    }

    private fun addEmoji(emoji: String) {
        val currentEmojis = _uiState.value.editedEmojis
        if (!currentEmojis.contains(emoji)) {
            _uiState.update {
                it.copy(
                    editedEmojis = currentEmojis + emoji,
                    showEmojiPicker = false,
                )
            }
        }
    }

    private fun removeEmoji(emoji: String) {
        _uiState.update {
            it.copy(editedEmojis = it.editedEmojis.filter { e -> e != emoji })
        }
    }

    private fun toggleFavorite() {
        viewModelScope.launch {
            toggleFavoriteUseCase(memeId)
                .onSuccess {
                    // Reload to get updated state
                    val meme = getMemeByIdUseCase(memeId)
                    if (meme != null) {
                        _uiState.update { it.copy(meme = meme) }
                        _effects.send(
                            MemeDetailEffect.ShowSnackbar(
                                if (meme.isFavorite) context.getString(R.string.gallery_snackbar_added_to_favorites) 
                                else context.getString(R.string.gallery_snackbar_removed_from_favorites)
                            )
                        )
                    }
                }
                .onFailure {
                    _effects.send(MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_favorite_failed)))
                }
        }
    }

    private fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    private fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    private fun confirmDelete() {
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteDialog = false, isLoading = true) }

            deleteMemeUseCase(memeId)
                .onSuccess {
                    _effects.send(MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_meme_deleted)))
                    _effects.send(MemeDetailEffect.NavigateBack)
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                    _effects.send(MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_delete_failed)))
                }
        }
    }

    private fun share() {
        // Delegate to share screen for consistent sharing experience
        // This removes the feature:gallery -> feature:share dependency
        openShareScreen()
    }

    private fun saveChanges() {
        val currentState = _uiState.value
        val meme = currentState.meme ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // Convert emoji strings to EmojiTag objects
            val emojiTags = currentState.editedEmojis.map { emoji ->
                EmojiTag.fromEmoji(emoji)
            }

            // Update meme
            val updatedMeme = meme.copy(
                title = currentState.editedTitle.takeIf { it.isNotBlank() },
                description = currentState.editedDescription.takeIf { it.isNotBlank() },
                emojiTags = emojiTags,
            )

            updateMemeUseCase(updatedMeme)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            meme = updatedMeme,
                            isEditMode = false,
                            isSaving = false,
                        )
                    }
                    _effects.send(MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_changes_saved)))
                }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false) }
                    _effects.send(MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_save_failed)))
                }
        }
    }

    private fun discardChanges() {
        val meme = _uiState.value.meme
        _uiState.update {
            it.copy(
                isEditMode = false,
                editedTitle = meme?.title ?: "",
                editedDescription = meme?.description ?: "",
                editedEmojis = meme?.emojiTags?.map { tag -> tag.emoji } ?: emptyList(),
            )
        }
    }

    private fun showEmojiPicker() {
        _uiState.update { it.copy(showEmojiPicker = true) }
    }

    private fun dismissEmojiPicker() {
        _uiState.update { it.copy(showEmojiPicker = false) }
    }

    private fun dismiss() {
        viewModelScope.launch {
            if (_uiState.value.hasUnsavedChanges) {
                _effects.send(MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_unsaved_changes)))
            } else {
                _effects.send(MemeDetailEffect.NavigateBack)
            }
        }
    }
}
