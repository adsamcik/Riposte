package com.adsamcik.riposte.feature.settings.domain.repository

import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.SharingPreferences
import com.adsamcik.riposte.core.model.UserDensityPreference
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
     * Updates the use native share dialog setting.
     */
    suspend fun setUseNativeShareDialog(enabled: Boolean)

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
     * Updates the strip metadata setting for sharing.
     */
    suspend fun setStripMetadata(strip: Boolean)

    /**
     * Updates the grid density preference.
     */
    suspend fun setGridDensity(preference: UserDensityPreference)

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
