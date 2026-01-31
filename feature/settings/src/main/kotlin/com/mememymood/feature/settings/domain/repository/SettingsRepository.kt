package com.mememymood.feature.settings.domain.repository

import com.mememymood.core.model.AppPreferences
import com.mememymood.core.model.DarkMode
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.SharingPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for settings and preferences operations.
 */
interface SettingsRepository {

    /**
     * Flow of app preferences (dark mode, dynamic colors, search settings, etc.).
     */
    val appPreferences: Flow<AppPreferences>

    /**
     * Flow of sharing preferences (format, quality, dimensions, etc.).
     */
    val sharingPreferences: Flow<SharingPreferences>

    /**
     * Updates the dark mode setting.
     */
    suspend fun setDarkMode(mode: DarkMode)

    /**
     * Updates the dynamic colors setting.
     */
    suspend fun setDynamicColors(enabled: Boolean)

    /**
     * Updates the semantic search enabled setting.
     */
    suspend fun setEnableSemanticSearch(enabled: Boolean)

    /**
     * Updates the save search history setting.
     */
    suspend fun setSaveSearchHistory(save: Boolean)

    /**
     * Updates the hold-to-share delay setting.
     */
    suspend fun setHoldToShareDelay(delayMs: Long)

    /**
     * Updates the default sharing format.
     */
    suspend fun setDefaultFormat(format: ImageFormat)

    /**
     * Updates the default sharing quality.
     */
    suspend fun setDefaultQuality(quality: Int)

    /**
     * Updates the default max dimension for sharing.
     */
    suspend fun setDefaultMaxDimension(dimension: Int)

    /**
     * Updates the keep metadata setting.
     */
    suspend fun setKeepMetadata(keep: Boolean)

    /**
     * Exports preferences to a JSON string.
     */
    suspend fun exportPreferences(): String

    /**
     * Imports preferences from a JSON string.
     *
     * @param json The JSON string containing preferences to import
     * @return Result indicating success or failure with error details
     */
    suspend fun importPreferences(json: String): Result<Unit>
}
