package com.mememymood.feature.gallery.presentation

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
     * Open share sheet with selected memes.
     */
    data class OpenShareSheet(val memeIds: List<Long>) : GalleryEffect

    /**
     * Show error message.
     */
    data class ShowError(val message: String) : GalleryEffect

    /**
     * Navigate to share screen for a specific meme.
     */
    data class NavigateToShare(val memeId: Long) : GalleryEffect

    /**
     * Launch share intent directly.
     */
    data class LaunchShareIntent(val intent: android.content.Intent) : GalleryEffect

    /**
     * Launch a quick share to a specific app target.
     */
    data class LaunchQuickShare(
        val meme: com.mememymood.core.model.Meme,
        val target: com.mememymood.core.model.ShareTarget,
    ) : GalleryEffect
}
