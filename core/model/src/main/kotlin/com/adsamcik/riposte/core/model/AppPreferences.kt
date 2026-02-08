package com.adsamcik.riposte.core.model

import kotlinx.serialization.Serializable

/**
 * App-level preferences model.
 */
@Serializable
data class AppPreferences(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColors: Boolean = true,
    val gridColumns: Int = 2,
    val showEmojiNames: Boolean = false,
    val enableSemanticSearch: Boolean = true,
    val autoExtractText: Boolean = true,
    val saveSearchHistory: Boolean = true,
    val userDensityPreference: UserDensityPreference = UserDensityPreference.AUTO,
    val holdToShareDelayMs: Long = 600L,
)

/**
 * User preference for grid density override.
 * AUTO uses adaptive columns based on screen width.
 */
@Serializable
enum class UserDensityPreference {
    /** Adaptive columns based on screen width */
    AUTO,

    /** 3 columns (larger thumbnails) */
    COMPACT,

    /** 4 columns (balanced) */
    STANDARD,

    /** 5 columns (smaller thumbnails) */
    DENSE,
}

/**
 * Dark mode options for the app theme.
 */
@Serializable
enum class DarkMode {
    SYSTEM,
    LIGHT,
    DARK,
}
