package com.mememymood.core.model

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
    val saveSearchHistory: Boolean = true
)

/**
 * Dark mode options for the app theme.
 */
@Serializable
enum class DarkMode {
    SYSTEM,
    LIGHT,
    DARK,
}
