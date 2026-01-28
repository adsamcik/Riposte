package com.mememymood.feature.share.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.ShareConfig
import com.mememymood.feature.share.R
import com.mememymood.feature.share.data.ImageProcessor
import com.mememymood.feature.share.domain.BitmapLoader
import com.mememymood.feature.share.domain.usecase.ShareUseCases
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
class ShareViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val shareUseCases: ShareUseCases,
    private val imageProcessor: ImageProcessor,
    private val bitmapLoader: BitmapLoader,
) : ViewModel() {

    private val memeId: Long = savedStateHandle.get<Long>("memeId") ?: -1L

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ShareEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadMeme()
    }

    fun onIntent(intent: ShareIntent) {
        when (intent) {
            is ShareIntent.SetFormat -> updateConfig { it.copy(format = intent.format) }
            is ShareIntent.SetQuality -> updateConfig { it.copy(quality = intent.quality) }
            is ShareIntent.SetMaxDimension -> updateConfig { 
                it.copy(maxWidth = intent.dimension, maxHeight = intent.dimension) 
            }
            is ShareIntent.SetStripMetadata -> updateConfig { it.copy(stripMetadata = intent.strip) }
            is ShareIntent.Share -> share()
            is ShareIntent.SaveToGallery -> saveToGallery()
            is ShareIntent.RefreshPreview -> updatePreview()
            is ShareIntent.NavigateBack -> navigateBack()
        }
    }

    private fun loadMeme() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val meme = shareUseCases.getMeme(memeId)
                val defaultConfig = shareUseCases.getDefaultConfig()

                if (meme == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = context.getString(R.string.share_error_meme_not_found)) }
                    return@launch
                }

                // Load original bitmap for preview
                val originalBitmap = bitmapLoader.loadBitmap(meme.filePath)
                val originalFileSize = bitmapLoader.getFileSize(meme.filePath)

                _uiState.update {
                    it.copy(
                        meme = meme,
                        config = defaultConfig,
                        originalPreviewBitmap = originalBitmap,
                        originalFileSize = originalFileSize,
                        isLoading = false,
                    )
                }

                updatePreview()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message)
                }
            }
        }
    }

    private fun updateConfig(transform: (ShareConfig) -> ShareConfig) {
        _uiState.update { state ->
            state.copy(config = transform(state.config))
        }
        updateEstimatedSize()
    }

    private fun updatePreview() {
        viewModelScope.launch {
            val meme = _uiState.value.meme ?: return@launch
            val config = _uiState.value.config

            _uiState.update { it.copy(isProcessing = true) }

            try {
                val original = bitmapLoader.loadBitmap(meme.filePath) ?: run {
                    _uiState.update { it.copy(isProcessing = false) }
                    return@launch
                }
                val maxWidth = config.maxWidth ?: original.width
                val maxHeight = config.maxHeight ?: original.height
                val processedBitmap = imageProcessor.resizeBitmap(original, maxWidth, maxHeight)
                
                if (processedBitmap != original) original.recycle()

                _uiState.update {
                    it.copy(
                        processedPreviewBitmap = processedBitmap,
                        isProcessing = false,
                    )
                }

                updateEstimatedSize()
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private fun updateEstimatedSize() {
        viewModelScope.launch {
            val meme = _uiState.value.meme ?: return@launch
            val config = _uiState.value.config

            try {
                val estimatedSize = shareUseCases.estimateFileSize(meme, config)
                _uiState.update { it.copy(estimatedFileSize = estimatedSize) }
            } catch (e: Exception) {
                // Ignore estimation errors
            }
        }
    }

    private fun share() {
        viewModelScope.launch {
            val meme = _uiState.value.meme ?: return@launch
            val config = _uiState.value.config

            _uiState.update { it.copy(isProcessing = true) }

            try {
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
                        _effects.send(ShareEffect.LaunchShareIntent(intent))
                    },
                    onFailure = { error ->
                        _effects.send(ShareEffect.ShowError(error.message ?: "Share failed"))
                    },
                )
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private fun saveToGallery() {
        viewModelScope.launch {
            val meme = _uiState.value.meme ?: return@launch
            val config = _uiState.value.config

            _uiState.update { it.copy(isProcessing = true) }

            try {
                val result = shareUseCases.saveToGallery(meme, config)
                result.fold(
                    onSuccess = { uri ->
                        _effects.send(ShareEffect.SavedToGallery(uri))
                        _effects.send(ShareEffect.ShowSnackbar(context.getString(R.string.share_snackbar_saved_to_gallery)))
                    },
                    onFailure = { error ->
                        _effects.send(ShareEffect.ShowError(error.message ?: context.getString(R.string.share_snackbar_save_failed)))
                    },
                )
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _effects.send(ShareEffect.NavigateBack)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up bitmaps
        _uiState.value.originalPreviewBitmap?.recycle()
        _uiState.value.processedPreviewBitmap?.recycle()
    }
}
