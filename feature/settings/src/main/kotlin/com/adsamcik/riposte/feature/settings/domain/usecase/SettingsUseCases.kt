package com.adsamcik.riposte.feature.settings.domain.usecase

import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.SharingPreferences
import com.adsamcik.riposte.core.model.UserDensityPreference
import com.adsamcik.riposte.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for observing app preferences.
 */
class GetAppPreferencesUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<AppPreferences> = repository.appPreferences
}

/**
 * Use case for observing sharing preferences.
 */
class GetSharingPreferencesUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<SharingPreferences> = repository.sharingPreferences
}

/**
 * Use case for updating dark mode setting.
 */
class SetDarkModeUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(mode: DarkMode) = repository.setDarkMode(mode)
}

/**
 * Use case for updating dynamic colors setting.
 */
class SetDynamicColorsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setDynamicColors(enabled)
}

/**
 * Use case for updating semantic search enabled setting.
 */
class SetEnableSemanticSearchUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setEnableSemanticSearch(enabled)
}

/**
 * Use case for updating save search history setting.
 */
class SetSaveSearchHistoryUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(save: Boolean) = repository.setSaveSearchHistory(save)
}

/**
 * Use case for updating hold-to-share delay setting.
 */
class SetHoldToShareDelayUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(delayMs: Long) = repository.setHoldToShareDelay(delayMs)
}

/**
 * Use case for updating use native share dialog setting.
 */
class SetUseNativeShareDialogUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setUseNativeShareDialog(enabled)
}

/**
 * Use case for updating default sharing format.
 */
class SetDefaultFormatUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(format: ImageFormat) = repository.setDefaultFormat(format)
}

/**
 * Use case for updating default sharing quality.
 */
class SetDefaultQualityUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(quality: Int) = repository.setDefaultQuality(quality)
}

/**
 * Use case for updating default max dimension for sharing.
 */
class SetDefaultMaxDimensionUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(dimension: Int) = repository.setDefaultMaxDimension(dimension)
}

/**
 * Use case for updating strip metadata setting for sharing.
 */
class SetStripMetadataUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(strip: Boolean) = repository.setStripMetadata(strip)
}

/**
 * Use case for updating grid density preference.
 */
class SetGridDensityUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(preference: UserDensityPreference) = repository.setGridDensity(preference)
}

/**
 * Use case for exporting preferences to JSON.
 */
class ExportPreferencesUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(): String = repository.exportPreferences()
}

/**
 * Use case for importing preferences from JSON.
 */
class ImportPreferencesUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(json: String): Result<Unit> = repository.importPreferences(json)
}
