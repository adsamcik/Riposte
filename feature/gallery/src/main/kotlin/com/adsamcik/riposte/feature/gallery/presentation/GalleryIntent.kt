package com.adsamcik.riposte.feature.gallery.presentation

/**
 * User intents for the Gallery screen.
 */
sealed interface GalleryIntent {
    /**
     * Load or refresh memes.
     */
    data object LoadMemes : GalleryIntent

    /**
     * Open a meme for viewing/sharing.
     */
    data class OpenMeme(val memeId: Long) : GalleryIntent

    /**
     * Toggle selection of a meme (in selection mode).
     */
    data class ToggleSelection(val memeId: Long) : GalleryIntent

    /**
     * Enter selection mode by long-pressing a meme.
     */
    data class StartSelection(val memeId: Long) : GalleryIntent

    /**
     * Enter selection mode without pre-selecting a meme (from overflow menu).
     */
    data object EnterSelectionMode : GalleryIntent

    /**
     * Exit selection mode.
     */
    data object ClearSelection : GalleryIntent

    /**
     * Select all memes.
     */
    data object SelectAll : GalleryIntent

    /**
     * Toggle favorite status for a meme.
     */
    data class ToggleFavorite(val memeId: Long) : GalleryIntent

    /**
     * Delete selected memes.
     */
    data object DeleteSelected : GalleryIntent

    /**
     * Confirm deletion of selected memes.
     */
    data object ConfirmDelete : GalleryIntent

    /**
     * Cancel deletion.
     */
    data object CancelDelete : GalleryIntent

    /**
     * Change the filter.
     */
    data class SetFilter(val filter: GalleryFilter) : GalleryIntent

    /**
     * Change the grid columns.
     */
    data class SetGridColumns(val columns: Int) : GalleryIntent

    /**
     * Share selected memes.
     */
    data object ShareSelected : GalleryIntent

    /**
     * Navigate to import screen.
     */
    data object NavigateToImport : GalleryIntent

    /**
     * Quick share a meme (long press action).
     */
    data class QuickShare(val memeId: Long) : GalleryIntent

    // ── Search intents (inline search) ──

    /**
     * Update the search query text.
     */
    data class UpdateSearchQuery(val query: String) : GalleryIntent

    /**
     * Clear the search query and reset search state.
     */
    data object ClearSearch : GalleryIntent

    /**
     * Select a recent search and perform it.
     */
    data class SelectRecentSearch(val query: String) : GalleryIntent

    /**
     * Delete a specific recent search entry.
     */
    data class DeleteRecentSearch(val query: String) : GalleryIntent

    /**
     * Clear all recent search history.
     */
    data object ClearRecentSearches : GalleryIntent

    /**
     * Dismiss the active notification banner.
     */
    data object DismissNotification : GalleryIntent

    /**
     * Notify that the search field focus state changed.
     */
    data class SearchFieldFocusChanged(val isFocused: Boolean) : GalleryIntent
}
