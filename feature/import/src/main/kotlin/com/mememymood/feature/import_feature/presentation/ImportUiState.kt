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
    val showEmojiPicker: Boolean = false,

    /**
     * Indices of images detected as duplicates pending user decision.
     */
    val duplicateIndices: Set<Int> = emptySet(),

    /**
     * Whether to show the duplicate confirmation dialog.
     */
    val showDuplicateDialog: Boolean = false,

    /**
     * Result of the import operation, shown in summary screen.
     */
    val importResult: ImportResult? = null,
) {
    val hasImages: Boolean get() = selectedImages.isNotEmpty()
    val canImport: Boolean get() = hasImages && !isImporting
    val editingImage: ImportImage? get() = editingImageIndex?.let { selectedImages.getOrNull(it) }

    /** True when progress is indeterminate (streaming ZIP import). */
    val isProgressIndeterminate: Boolean get() = importProgress < 0f
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

/**
 * Result of the import operation.
 */
data class ImportResult(
    val successCount: Int,
    val failureCount: Int,
    val failedImages: List<ImportImage> = emptyList(),
)
