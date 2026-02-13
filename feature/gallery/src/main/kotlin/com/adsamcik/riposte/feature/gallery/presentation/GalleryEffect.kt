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
     * Launch a share intent directly via the native Android share sheet.
     */
    data class LaunchShareIntent(val intent: android.content.Intent) : GalleryEffect

    /**
     * Trigger haptic feedback for UI interactions.
     */
    data object TriggerHapticFeedback : GalleryEffect
}
