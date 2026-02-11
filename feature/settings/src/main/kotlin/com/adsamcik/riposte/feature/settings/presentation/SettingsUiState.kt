package com.adsamcik.riposte.feature.settings.presentation

import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.UserDensityPreference

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
 * State of the embedding search index.
 */
data class EmbeddingSearchState(
    val modelName: String,
    val modelVersion: String,
    val dimension: Int,
    val indexedCount: Int,
    val totalCount: Int,
    val pendingCount: Int,
    val regenerationCount: Int,
    val modelError: String? = null,
) {
    val isFullyIndexed: Boolean
        get() = pendingCount == 0 && regenerationCount == 0
}

/**
 * UI state for the Settings screen.
 */
data class SettingsUiState(
    // Appearance
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val currentLanguage: String? = null,
    val availableLanguages: List<AppLanguage> = emptyList(),
    val dynamicColorsEnabled: Boolean = true,
    // Display
    val gridDensityPreference: UserDensityPreference = UserDensityPreference.AUTO,
    // Sharing
    val useNativeShareDialog: Boolean = false,
    // Sharing defaults
    val defaultFormat: ImageFormat = ImageFormat.WEBP,
    val defaultQuality: Int = 85,
    val defaultMaxDimension: Int = 1080,
    val stripMetadata: Boolean = true,
    // Search
    val enableSemanticSearch: Boolean = true,
    val saveSearchHistory: Boolean = true,
    val embeddingSearchState: EmbeddingSearchState? = null,
    // Storage
    val cacheSize: String = "0 B",
    val showClearCacheDialog: Boolean = false,
    // Export options dialog
    val showExportOptionsDialog: Boolean = false,
    val exportSettings: Boolean = true,
    val exportImages: Boolean = true,
    val exportTags: Boolean = true,
    // Import confirmation dialog
    val showImportConfirmDialog: Boolean = false,
    val pendingImportJson: String? = null,
    val importBackupTimestamp: Long? = null,
    // About
    val appVersion: String = "1.0.0",
    // Crash Logs
    val crashLogCount: Int = 0,
    val isLoading: Boolean = true,
)
