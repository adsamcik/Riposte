package com.adsamcik.riposte.core.common.suggestion

/**
 * Surface where suggestions are displayed.
 * Gallery favors engagement (go-to stickers), Search favors contextual scope.
 */
enum class Surface {
    GALLERY,
    SEARCH,
}

/**
 * Context for computing suggestions — includes the surface, active search state,
 * and IDs shown in the previous session (for staleness rotation).
 */
data class SuggestionContext(
    val surface: Surface,
    val currentQuery: String? = null,
    val recentSearches: List<String> = emptyList(),
    val currentEmojiFilter: String? = null,
    val lastSessionSuggestionIds: Set<Long> = emptySet(),
)
