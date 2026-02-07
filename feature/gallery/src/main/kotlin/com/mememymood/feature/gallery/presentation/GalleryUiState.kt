package com.mememymood.feature.gallery.presentation

import com.mememymood.core.model.Meme
import com.mememymood.core.model.UserDensityPreference

/**
 * Sort options for the gallery.
 */
enum class SortOption {
    /** Most recently imported first. */
    Recent,
    /** Most frequently used first. */
    MostUsed,
    /** Grouped by primary emoji tag. */
    EmojiGroup,
}

/**
 * UI state for the Gallery screen.
 */
data class GalleryUiState(
    /**
     * List of memes to display.
     */
    val memes: List<Meme> = emptyList(),

    /**
     * Smart suggestions from the StickerHand algorithm.
     * Populated by [GetSuggestionsUseCase] — replaces simple useCount sorting.
     */
    val suggestions: List<Meme> = emptyList(),

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
     * User's grid density preference (from settings).
     */
    val densityPreference: UserDensityPreference = UserDensityPreference.AUTO,

    /**
     * Whether using paging for this view.
     * True for "All" filter with large datasets, false for filtered views.
     */
    val usePaging: Boolean = true,

    /**
     * Active emoji filters applied on top of the gallery filter.
     * Persisted in ViewModel so they survive navigation/recomposition.
     */
    val activeEmojiFilters: Set<String> = emptySet(),

    /**
     * Current sort option.
     */
    val sortOption: SortOption = SortOption.Recent,

    /**
     * Unique emojis with counts, derived from the current meme list.
     * Computed in the ViewModel to avoid expensive recomposition in Compose.
     */
    val uniqueEmojis: List<Pair<String, Int>> = emptyList(),

    /**
     * Memes filtered by active emoji filters (non-paged path).
     * Computed in the ViewModel as derived state.
     */
    val filteredMemes: List<Meme> = emptyList(),

    /**
     * Meme currently being quick-shared (shows the bottom sheet), or null.
     */
    val quickShareMeme: Meme? = null,

    /**
     * Frequent share targets for the quick share bottom sheet.
     */
    val quickShareTargets: List<com.mememymood.core.model.ShareTarget> = emptyList(),
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
