package com.mememymood.feature.import_feature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememymood.core.model.MemeMetadata
import com.mememymood.feature.import_feature.domain.usecase.ExtractTextUseCase
import com.mememymood.feature.import_feature.domain.usecase.ImportImageUseCase
import com.mememymood.feature.import_feature.domain.usecase.SuggestEmojisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importImageUseCase: ImportImageUseCase,
    private val suggestEmojisUseCase: SuggestEmojisUseCase,
    private val extractTextUseCase: ExtractTextUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ImportEffect>()
    val effects = _effects.receiveAsFlow()

    private var importJob: Job? = null

    fun onIntent(intent: ImportIntent) {
        when (intent) {
            is ImportIntent.ImagesSelected -> handleImagesSelected(intent)
            is ImportIntent.RemoveImage -> removeImage(intent.index)
            is ImportIntent.EditImage -> editImage(intent.index)
            is ImportIntent.CloseEditor -> closeEditor()
            is ImportIntent.UpdateTitle -> updateTitle(intent.title)
            is ImportIntent.UpdateDescription -> updateDescription(intent.description)
            is ImportIntent.AddEmoji -> addEmoji(intent.emoji)
            is ImportIntent.RemoveEmoji -> removeEmoji(intent.emoji)
            is ImportIntent.ShowEmojiPicker -> showEmojiPicker()
            is ImportIntent.HideEmojiPicker -> hideEmojiPicker()
            is ImportIntent.ApplySuggestedEmojis -> applySuggestedEmojis()
            is ImportIntent.StartImport -> startImport()
            is ImportIntent.CancelImport -> cancelImport()
            is ImportIntent.ClearAll -> clearAll()
            is ImportIntent.PickMoreImages -> pickMoreImages()
        }
    }

    private fun handleImagesSelected(intent: ImportIntent.ImagesSelected) {
        viewModelScope.launch {
            val newImages = intent.uris.map { uri ->
                val fileName = uri.lastPathSegment ?: "image_${System.currentTimeMillis()}"
                ImportImage(
                    uri = uri,
                    fileName = fileName,
                    isProcessing = true
                )
            }

            _uiState.update { state ->
                state.copy(selectedImages = state.selectedImages + newImages)
            }

            // Process each image for suggestions
            newImages.forEachIndexed { index, image ->
                val actualIndex = _uiState.value.selectedImages.size - newImages.size + index
                processImage(actualIndex, image)
            }
        }
    }

    private suspend fun processImage(index: Int, image: ImportImage) {
        try {
            // Suggest emojis
            val suggestedEmojis = suggestEmojisUseCase(image.uri)

            // Extract text
            val extractedText = extractTextUseCase(image.uri)

            _uiState.update { state ->
                val updatedImages = state.selectedImages.toMutableList()
                if (index < updatedImages.size) {
                    updatedImages[index] = updatedImages[index].copy(
                        suggestedEmojis = suggestedEmojis,
                        extractedText = extractedText,
                        isProcessing = false
                    )
                }
                state.copy(selectedImages = updatedImages)
            }
        } catch (e: Exception) {
            _uiState.update { state ->
                val updatedImages = state.selectedImages.toMutableList()
                if (index < updatedImages.size) {
                    updatedImages[index] = updatedImages[index].copy(
                        isProcessing = false,
                        error = e.message
                    )
                }
                state.copy(selectedImages = updatedImages)
            }
        }
    }

    private fun removeImage(index: Int) {
        _uiState.update { state ->
            val updatedImages = state.selectedImages.toMutableList()
            if (index < updatedImages.size) {
                updatedImages.removeAt(index)
            }
            state.copy(
                selectedImages = updatedImages,
                editingImageIndex = if (state.editingImageIndex == index) null else state.editingImageIndex
            )
        }
    }

    private fun editImage(index: Int) {
        _uiState.update { it.copy(editingImageIndex = index) }
    }

    private fun closeEditor() {
        _uiState.update { it.copy(editingImageIndex = null, showEmojiPicker = false) }
    }

    private fun updateTitle(title: String) {
        updateEditingImage { it.copy(title = title) }
    }

    private fun updateDescription(description: String) {
        updateEditingImage { it.copy(description = description) }
    }

    private fun addEmoji(emoji: com.mememymood.core.model.EmojiTag) {
        updateEditingImage { image ->
            if (emoji !in image.emojis) {
                image.copy(emojis = image.emojis + emoji)
            } else {
                image
            }
        }
    }

    private fun removeEmoji(emoji: com.mememymood.core.model.EmojiTag) {
        updateEditingImage { image ->
            image.copy(emojis = image.emojis - emoji)
        }
    }

    private fun showEmojiPicker() {
        _uiState.update { it.copy(showEmojiPicker = true) }
    }

    private fun hideEmojiPicker() {
        _uiState.update { it.copy(showEmojiPicker = false) }
    }

    private fun applySuggestedEmojis() {
        updateEditingImage { image ->
            image.copy(emojis = image.suggestedEmojis.take(5))
        }
    }

    private fun updateEditingImage(transform: (ImportImage) -> ImportImage) {
        _uiState.update { state ->
            val index = state.editingImageIndex ?: return@update state
            val updatedImages = state.selectedImages.toMutableList()
            if (index < updatedImages.size) {
                updatedImages[index] = transform(updatedImages[index])
            }
            state.copy(selectedImages = updatedImages)
        }
    }

    private fun startImport() {
        importJob = viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = 0f) }

            val images = _uiState.value.selectedImages
            var successCount = 0

            images.forEachIndexed { index, image ->
                // Only create metadata if there are emojis (required by MemeMetadata)
                val metadata = if (image.emojis.isNotEmpty()) {
                    MemeMetadata(
                        emojis = image.emojis.map { it.emoji },
                        title = image.title,
                        description = image.description
                    )
                } else {
                    null
                }

                val result = importImageUseCase(image.uri, metadata)
                if (result.isSuccess) {
                    successCount++
                }

                _uiState.update {
                    it.copy(importProgress = (index + 1).toFloat() / images.size)
                }
            }

            _uiState.update { it.copy(isImporting = false) }

            if (successCount > 0) {
                _effects.send(ImportEffect.ImportComplete(successCount))
                _effects.send(ImportEffect.NavigateToGallery)
            } else {
                _effects.send(ImportEffect.ShowError("Failed to import images"))
            }
        }
    }

    private fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _uiState.update { it.copy(isImporting = false, importProgress = 0f) }
    }

    private fun clearAll() {
        _uiState.update { ImportUiState() }
    }

    private fun pickMoreImages() {
        viewModelScope.launch {
            _effects.send(ImportEffect.OpenImagePicker)
        }
    }
}
