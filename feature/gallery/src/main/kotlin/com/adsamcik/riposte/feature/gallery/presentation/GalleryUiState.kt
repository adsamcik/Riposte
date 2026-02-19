package com.adsamcik.riposte.feature.gallery.presentation

import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.model.UserDensityPreference

/**
 * Screen mode: browsing (default) or searching (query active).
 */
enum class ScreenMode {
    Browsing,
    Searching,
}

/**
 * Typed search error — allows the UI layer to localize messages and
 * control retry behaviour without coupling to hardcoded English strings.
 *
 * @see SearchDelegate.performSearch
 */
sealed interface SearchError {
    /** Device permanently cannot run the ML model (UnsatisfiedLinkError). */
    data object NotSupported : SearchError

    /** Model static initialiser failed (ExceptionInInitializerError). */
    data object IndexFailed : SearchError

    /** Any other search error. */
    data class Generic(val message: String) : SearchError

    /** Whether this error is permanent (retry will never succeed). */
    val isRetryable: Boolean
        get() = this is Generic
}

/**
 * Search-specific state slice, owned by [SearchDelegate].
 */
data class SearchSliceState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchDurationMs: Long = 0L,
    val totalResultCount: Int = 0,
    val searchError: SearchError? = null,
    val isTextOnly: Boolean = false,
)

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
     * Unique emojis with counts, derived from the current meme list.
     * Computed in the ViewModel to avoid expensive recomposition in Compose.
     */
    val uniqueEmojis: List<Pair<String, Int>> = emptyList(),
    /**
     * Current screen mode: browsing or searching.
     */
    val screenMode: ScreenMode = ScreenMode.Browsing,
    /**
     * Search-specific state slice.
     */
    val searchState: SearchSliceState = SearchSliceState(),
    /**
     * Status of a background import, if any.
     * Observed via WorkManager so gallery can show progress without
     * depending on the import feature module.
     */
    val importStatus: ImportWorkStatus = ImportWorkStatus.Idle,
    /**
     * Number of favorited memes. Used to conditionally show the Favorites chip
     * in the search-mode emoji filter rail.
     */
    val favoritesCount: Int = 0,
    /**
     * Active one-shot notification to display in the notification banner.
     */
    val notification: GalleryNotification? = null,
    /**
     * Whether the search field is currently focused.
     * Used to show the emoji filter rail even before a query is typed.
     */
    val isSearchFocused: Boolean = false,
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
}

/**
 * Status of background import work observed via WorkManager.
 * Only tracks active import progress; completion/failure are promoted
 * to [GalleryNotification] for the unified notification banner.
 */
sealed interface ImportWorkStatus {
    data object Idle : ImportWorkStatus

    data class InProgress(val completed: Int, val total: Int) : ImportWorkStatus
}

/**
 * One-shot notification displayed in the banner below the search bar.
 * Dismissed automatically after a timeout or by user interaction.
 */
sealed interface GalleryNotification {
    data class ImportComplete(val count: Int, val failed: Int = 0) : GalleryNotification

    data class ImportFailed(val message: String? = null) : GalleryNotification

    data class IndexingComplete(val count: Int) : GalleryNotification
}
