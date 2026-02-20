package com.adsamcik.riposte.feature.gallery.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.riposte.core.common.review.UserActionTracker
import com.adsamcik.riposte.core.common.share.ShareMemeUseCase
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.feature.gallery.R
import com.adsamcik.riposte.feature.gallery.domain.usecase.MemeDetailUseCases
import com.adsamcik.riposte.feature.gallery.domain.usecase.SimilarMemesStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MemeDetailViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val useCases: MemeDetailUseCases,
        private val shareMemeUseCase: ShareMemeUseCase,
        private val userActionTracker: UserActionTracker,
    ) : ViewModel() {
        private var currentMemeId: Long = savedStateHandle.get<Long>("memeId") ?: -1L

        private val _uiState = MutableStateFlow(MemeDetailUiState())
        val uiState: StateFlow<MemeDetailUiState> = _uiState.asStateFlow()

        private val _effects = Channel<MemeDetailEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        init {
            loadMeme()
            loadAllMemeIds()
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
                is MemeDetailIntent.SaveChanges -> saveChanges()
                is MemeDetailIntent.DiscardChanges -> discardChanges()
                is MemeDetailIntent.ShowEmojiPicker -> showEmojiPicker()
                is MemeDetailIntent.DismissEmojiPicker -> dismissEmojiPicker()
                is MemeDetailIntent.Dismiss -> dismiss()
                is MemeDetailIntent.LoadSimilarMemes -> loadSimilarMemes()
                is MemeDetailIntent.NavigateToSimilarMeme -> navigateToMeme(intent.memeId)
                is MemeDetailIntent.ChangeMeme -> changeMeme(intent.memeId)
                is MemeDetailIntent.SearchByEmoji -> searchByEmoji(intent.emoji)
            }
        }

        private fun share() {
            viewModelScope.launch {
                _uiState.update { it.copy(isSharing = true) }
                shareMemeUseCase(currentMemeId)
                    .onSuccess { intent ->
                        _uiState.update { it.copy(isSharing = false) }
                        _effects.send(MemeDetailEffect.LaunchShareIntent(intent))
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isSharing = false) }
                        _effects.send(
                            MemeDetailEffect.ShowError(
                                error.message ?: context.getString(R.string.gallery_error_default),
                            ),
                        )
                    }
            }
        }

        private fun searchByEmoji(emoji: String) {
            viewModelScope.launch {
                _effects.send(MemeDetailEffect.NavigateToGalleryWithEmoji(emoji))
            }
        }

        private fun loadMeme() {
            if (currentMemeId == -1L) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = context.getString(R.string.gallery_error_invalid_meme_id),
                    )
                }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                try {
                    val meme = useCases.getMemeById(currentMemeId)
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
                        // Record view (fire-and-forget, don't block UI)
                        launch {
                            try {
                                useCases.recordMemeView(currentMemeId)
                            } catch (
                                @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                                e: Exception,
                            ) {
                                Timber.d(e, "Failed to record meme view")
                            }
                        }
                        // Load similar memes in background
                        launch { loadSimilarMemes() }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = context.getString(R.string.gallery_error_meme_not_found),
                            )
                        }
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                    e: Exception,
                ) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            }
        }

        private fun toggleEditMode() {
            val currentState = _uiState.value
            if (currentState.isEditMode && currentState.hasUnsavedChanges) {
                // Ask to save changes before exiting edit mode
                viewModelScope.launch {
                    _effects.send(
                        MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_save_or_discard)),
                    )
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
                useCases.toggleFavorite(currentMemeId)
                    .onSuccess {
                        // Reload to get updated state
                        val meme = useCases.getMemeById(currentMemeId)
                        if (meme != null) {
                            _uiState.update { it.copy(meme = meme) }
                            if (meme.isFavorite) userActionTracker.trackPositiveAction()
                            _effects.send(
                                MemeDetailEffect.ShowSnackbar(
                                    if (meme.isFavorite) {
                                        context.getString(R.string.gallery_snackbar_added_to_favorites)
                                    } else {
                                        context.getString(R.string.gallery_snackbar_removed_from_favorites)
                                    },
                                ),
                            )
                        }
                    }
                    .onFailure {
                        _effects.send(
                            MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_favorite_failed)),
                        )
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

                useCases.deleteMemes(currentMemeId)
                    .onSuccess {
                        _effects.send(
                            MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_meme_deleted)),
                        )
                        _effects.send(MemeDetailEffect.NavigateBack)
                    }
                    .onFailure {
                        _uiState.update { it.copy(isLoading = false) }
                        _effects.send(
                            MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_delete_failed)),
                        )
                    }
            }
        }

        private fun saveChanges() {
            val currentState = _uiState.value
            val meme = currentState.meme ?: return

            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true) }

                // Convert emoji strings to EmojiTag objects
                val emojiTags =
                    currentState.editedEmojis.map { emoji ->
                        EmojiTag.fromEmoji(emoji)
                    }

                // Update meme
                val updatedMeme =
                    meme.copy(
                        title = currentState.editedTitle.takeIf { it.isNotBlank() },
                        description = currentState.editedDescription.takeIf { it.isNotBlank() },
                        emojiTags = emojiTags,
                    )

                useCases.updateMeme(updatedMeme)
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                meme = updatedMeme,
                                isEditMode = false,
                                isSaving = false,
                            )
                        }
                        _effects.send(
                            MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_changes_saved)),
                        )
                    }
                    .onFailure {
                        _uiState.update { it.copy(isSaving = false) }
                        _effects.send(
                            MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_save_failed)),
                        )
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
                    _effects.send(
                        MemeDetailEffect.ShowSnackbar(context.getString(R.string.gallery_snackbar_unsaved_changes)),
                    )
                } else {
                    _effects.send(MemeDetailEffect.NavigateBack)
                }
            }
        }

        private fun loadSimilarMemes() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingSimilar = true) }
                try {
                    val status = useCases.getSimilarMemes(currentMemeId)
                    _uiState.update { it.copy(similarMemesStatus = status, isLoadingSimilar = false) }
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to load similar memes")
                    _uiState.update {
                        it.copy(
                            similarMemesStatus =
                                SimilarMemesStatus.Error(
                                    e.message ?: "Unknown error",
                                ),
                            isLoadingSimilar = false,
                        )
                    }
                }
            }
        }

        private fun navigateToMeme(memeId: Long) {
            viewModelScope.launch {
                _effects.send(MemeDetailEffect.NavigateToMeme(memeId))
            }
        }

        private fun changeMeme(memeId: Long) {
            if (memeId == currentMemeId) return
            currentMemeId = memeId
            // Reset similar memes and loading state for new meme
            _uiState.update {
                it.copy(
                    similarMemesStatus = null,
                    isLoadingSimilar = false,
                )
            }
            loadMeme()
        }

        private fun loadAllMemeIds() {
            viewModelScope.launch {
                try {
                    val ids = useCases.getAllMemeIds()
                    _uiState.update { it.copy(allMemeIds = ids) }
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Catches all to show error state
                    e: Exception,
                ) {
                    Timber.d(e, "Failed to load meme IDs for pager")
                }
            }
        }
    }
