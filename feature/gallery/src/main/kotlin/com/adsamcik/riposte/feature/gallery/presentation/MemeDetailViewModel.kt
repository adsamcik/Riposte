package com.adsamcik.riposte.feature.gallery.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.riposte.core.common.review.UserActionTracker
import com.adsamcik.riposte.core.database.repository.ShareTargetRepository
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.feature.gallery.R
import com.adsamcik.riposte.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetMemeByIdUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetSimilarMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.RecordMemeViewUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.SimilarMemesStatus
import com.adsamcik.riposte.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.UpdateMemeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemeDetailViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val getMemeByIdUseCase: GetMemeByIdUseCase,
        private val updateMemeUseCase: UpdateMemeUseCase,
        private val deleteMemeUseCase: DeleteMemesUseCase,
        private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
        private val recordMemeViewUseCase: RecordMemeViewUseCase,
        private val getSimilarMemesUseCase: GetSimilarMemesUseCase,
        private val userActionTracker: UserActionTracker,
        private val preferencesDataStore: PreferencesDataStore,
        private val shareTargetRepository: ShareTargetRepository,
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
                is MemeDetailIntent.LoadSimilarMemes -> loadSimilarMemes()
                is MemeDetailIntent.NavigateToSimilarMeme -> navigateToMeme(intent.memeId)
                is MemeDetailIntent.SelectShareTarget -> selectShareTarget(intent.target)
                is MemeDetailIntent.QuickShareMore -> quickShareMore()
                is MemeDetailIntent.DismissQuickShare -> dismissQuickShare()
                is MemeDetailIntent.CopyToClipboard -> copyToClipboard()
            }
        }

        private fun openShareScreen() {
            viewModelScope.launch {
                _effects.send(MemeDetailEffect.NavigateToShare(memeId))
            }
        }

        private fun loadMeme() {
            if (memeId == -1L) {
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
                        // Record view (fire-and-forget, don't block UI)
                        launch { recordMemeViewUseCase(memeId) }
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
                toggleFavoriteUseCase(memeId)
                    .onSuccess {
                        // Reload to get updated state
                        val meme = getMemeByIdUseCase(memeId)
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

                deleteMemeUseCase(memeId)
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

        private fun share() {
            viewModelScope.launch {
                val useNative = preferencesDataStore.sharingPreferences.first().useNativeShareDialog
                if (useNative) {
                    openShareScreen()
                    return@launch
                }
                val meme =
                    _uiState.value.meme ?: run {
                        openShareScreen()
                        return@launch
                    }
                val targets = shareTargetRepository.getTopShareTargets(limit = 6)
                _uiState.update {
                    it.copy(quickShareMeme = meme, quickShareTargets = targets)
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

                updateMemeUseCase(updatedMeme)
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
                    val status = getSimilarMemesUseCase(memeId)
                    _uiState.update { it.copy(similarMemesStatus = status, isLoadingSimilar = false) }
                } catch (e: Exception) {
                    android.util.Log.e("MemeDetailViewModel", "Failed to load similar memes", e)
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
                _effects.send(MemeDetailEffect.LaunchQuickShare(intent))
            }
        }

        private fun quickShareMore() {
            _uiState.update { it.copy(quickShareMeme = null, quickShareTargets = emptyList()) }
            openShareScreen()
        }

        private fun dismissQuickShare() {
            _uiState.update { it.copy(quickShareMeme = null, quickShareTargets = emptyList()) }
        }

        private fun copyToClipboard() {
            val meme = _uiState.value.quickShareMeme ?: _uiState.value.meme ?: return
            _uiState.update { it.copy(quickShareMeme = null, quickShareTargets = emptyList()) }
            viewModelScope.launch {
                _effects.send(MemeDetailEffect.CopyToClipboard(meme.id))
            }
        }
    }
