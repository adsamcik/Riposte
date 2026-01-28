package com.mememymood.feature.gallery.presentation

import com.mememymood.core.model.Meme

/**
 * UI state for the Gallery screen.
 */
data class GalleryUiState(
    /**
     * List of memes to display.
     */
    val memes: List<Meme> = emptyList(),

    /**
     * Whether the gallery is loading.
     */
    val isLoading: Boolean = true,

    /**
     * Error message if any.
     */
    val error: String? = null,

    /**
     * Currently selected meme IDs (for multi-select mode).
     */
    val selectedMemeIds: Set<Long> = emptySet(),

    /**
     * Whether in selection mode.
     */
    val isSelectionMode: Boolean = false,

    /**
     * Current filter: "all", "favorites", or an emoji string.
     */
    val filter: GalleryFilter = GalleryFilter.All,

    /**
     * Number of grid columns.
     */
    val gridColumns: Int = 2,

    /**
     * Whether using paging for this view.
     * True for "All" filter with large datasets, false for filtered views.
     */
    val usePaging: Boolean = true
) {
    /**
     * Whether any memes are selected.
     */
    val hasSelection: Boolean get() = selectedMemeIds.isNotEmpty()

    /**
     * Number of selected memes.
     */
    val selectionCount: Int get() = selectedMemeIds.size

    /**
     * Whether the gallery is empty (no memes).
     * Note: For paged data, emptiness is determined in the UI layer via LazyPagingItems.
     */
    val isEmpty: Boolean get() = !usePaging && memes.isEmpty() && !isLoading
}

/**
 * Filter options for the gallery.
 */
sealed interface GalleryFilter {
    data object All : GalleryFilter
    data object Favorites : GalleryFilter
    data class ByEmoji(val emoji: String) : GalleryFilter
}
