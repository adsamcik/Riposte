package com.mememymood.feature.settings.presentation

import com.mememymood.core.model.DarkMode
import com.mememymood.core.model.ImageFormat

/**
 * Represents a selectable app language option.
 *
 * @property code Language code (e.g., "en", "cs") or null for system default
 * @property displayName English display name of the language
 * @property nativeName Language name in its native form
 */
data class AppLanguage(
    val code: String?,
    val displayName: String,
    val nativeName: String,
)

/**
 * UI state for the Settings screen.
 */
data class SettingsUiState(
    // Appearance
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val currentLanguage: String? = null,
    val availableLanguages: List<AppLanguage> = emptyList(),
    val dynamicColorsEnabled: Boolean = true,

    // Sharing defaults
    val defaultFormat: ImageFormat = ImageFormat.JPEG,
    val defaultQuality: Int = 85,
    val defaultMaxDimension: Int = 1080,
    val keepMetadata: Boolean = true,

    // Search
    val enableSemanticSearch: Boolean = true,
    val saveSearchHistory: Boolean = true,

    // Storage
    val cacheSize: String = "0 B",
    val showClearCacheDialog: Boolean = false,

    // About
    val appVersion: String = "1.0.0",

    val isLoading: Boolean = true,
)
