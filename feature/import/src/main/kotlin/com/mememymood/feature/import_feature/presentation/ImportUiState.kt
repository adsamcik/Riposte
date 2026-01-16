package com.mememymood.feature.import_feature.presentation

import android.net.Uri
import com.mememymood.core.model.EmojiTag

/**
 * UI state for the Import screen.
 */
data class ImportUiState(
    /**
     * Selected images to import.
     */
    val selectedImages: List<ImportImage> = emptyList(),

    /**
     * Whether import is in progress.
     */
    val isImporting: Boolean = false,

    /**
     * Import progress (0.0 to 1.0).
     */
    val importProgress: Float = 0f,

    /**
     * Current status message.
     */
    val statusMessage: String? = null,

    /**
     * Error message if any.
     */
    val error: String? = null,

    /**
     * Index of currently editing image.
     */
    val editingImageIndex: Int? = null,

    /**
     * Whether in emoji picker mode.
     */
    val showEmojiPicker: Boolean = false
) {
    val hasImages: Boolean get() = selectedImages.isNotEmpty()
    val canImport: Boolean get() = hasImages && !isImporting && selectedImages.all { it.emojis.isNotEmpty() }
    val editingImage: ImportImage? get() = editingImageIndex?.let { selectedImages.getOrNull(it) }
}

/**
 * Represents an image being imported with its metadata.
 */
data class ImportImage(
    val uri: Uri,
    val fileName: String,
    val emojis: List<EmojiTag> = emptyList(),
    val title: String? = null,
    val description: String? = null,
    val extractedText: String? = null,
    val suggestedEmojis: List<EmojiTag> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null
)
