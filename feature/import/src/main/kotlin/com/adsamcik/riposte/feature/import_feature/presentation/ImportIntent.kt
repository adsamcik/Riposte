package com.adsamcik.riposte.feature.import_feature.presentation

import android.net.Uri
import com.adsamcik.riposte.core.model.EmojiTag

/**
 * User intents for the Import screen.
 */
sealed interface ImportIntent {
    /**
     * User selected images from picker.
     */
    data class ImagesSelected(val uris: List<Uri>) : ImportIntent

    /**
     * User selected a .meme.zip bundle file.
     */
    data class ZipSelected(val uri: Uri) : ImportIntent

    /**
     * Remove an image from the import list.
     */
    data class RemoveImage(val index: Int) : ImportIntent

    /**
     * Start editing an image's metadata.
     */
    data class EditImage(val index: Int) : ImportIntent

    /**
     * Stop editing.
     */
    data object CloseEditor : ImportIntent

    /**
     * Update title for current editing image.
     */
    data class UpdateTitle(val title: String) : ImportIntent

    /**
     * Update description for current editing image.
     */
    data class UpdateDescription(val description: String) : ImportIntent

    /**
     * Add an emoji to current editing image.
     */
    data class AddEmoji(val emoji: EmojiTag) : ImportIntent

    /**
     * Remove an emoji from current editing image.
     */
    data class RemoveEmoji(val emoji: EmojiTag) : ImportIntent

    /**
     * Show emoji picker.
     */
    data object ShowEmojiPicker : ImportIntent

    /**
     * Hide emoji picker.
     */
    data object HideEmojiPicker : ImportIntent

    /**
     * Apply suggested emojis to current image.
     */
    data object ApplySuggestedEmojis : ImportIntent

    /**
     * Start the import process.
     */
    data object StartImport : ImportIntent

    /**
     * Cancel the import process.
     */
    data object CancelImport : ImportIntent

    /**
     * Clear all selected images.
     */
    data object ClearAll : ImportIntent

    /**
     * Pick more images.
     */
    data object PickMoreImages : ImportIntent

    /**
     * Pick a .meme.zip bundle file to import.
     */
    data object PickZipBundle : ImportIntent

    /**
     * Import anyway despite duplicates.
     */
    data object ImportDuplicatesAnyway : ImportIntent

    /**
     * Skip all duplicate images.
     */
    data object SkipDuplicates : ImportIntent

    /**
     * Dismiss the duplicate dialog.
     */
    data object DismissDuplicateDialog : ImportIntent

    /**
     * Retry failed imports from the result summary.
     */
    data object RetryFailedImports : ImportIntent

    /**
     * Dismiss the import result summary.
     */
    data object DismissImportResult : ImportIntent
}
