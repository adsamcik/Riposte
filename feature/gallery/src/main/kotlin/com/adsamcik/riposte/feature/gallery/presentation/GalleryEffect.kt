package com.adsamcik.riposte.feature.gallery.presentation

/**
 * One-time side effects for the Gallery screen.
 */
sealed interface GalleryEffect {
    /**
     * Navigate to meme detail/share screen.
     */
    data class NavigateToMeme(val memeId: Long) : GalleryEffect

    /**
     * Navigate to import screen.
     */
    data object NavigateToImport : GalleryEffect

    /**
     * Show a snackbar message.
     */
    data class ShowSnackbar(val message: String) : GalleryEffect

    /**
     * Show delete confirmation dialog.
     */
    data class ShowDeleteConfirmation(val count: Int) : GalleryEffect

    /**
     * Show error message.
     */
    data class ShowError(val message: String) : GalleryEffect

    /**
     * Navigate to share screen for a specific meme.
     */
    data class NavigateToShare(val memeId: Long) : GalleryEffect

    /**
     * Launch a quick share to a specific app target.
     */
    data class LaunchQuickShare(val intent: android.content.Intent) : GalleryEffect

    /**
     * Trigger haptic feedback for UI interactions.
     */
    data object TriggerHapticFeedback : GalleryEffect

    /**
     * Copy meme image to clipboard.
     */
    data class CopyToClipboard(val memeId: Long) : GalleryEffect
}
