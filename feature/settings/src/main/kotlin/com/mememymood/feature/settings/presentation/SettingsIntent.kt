package com.mememymood.feature.settings.presentation

import com.mememymood.core.model.DarkMode
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.UserDensityPreference

sealed interface SettingsIntent {
    // Appearance
    data class SetDarkMode(val mode: DarkMode) : SettingsIntent
    data class SetLanguage(val languageCode: String?) : SettingsIntent
    data class SetDynamicColors(val enabled: Boolean) : SettingsIntent

    // Display
    data class SetGridDensity(val preference: UserDensityPreference) : SettingsIntent

    // Sharing
    data class SetDefaultFormat(val format: ImageFormat) : SettingsIntent
    data class SetDefaultQuality(val quality: Int) : SettingsIntent
    data class SetDefaultMaxDimension(val dimension: Int) : SettingsIntent

    // Search
    data class SetEnableSemanticSearch(val enabled: Boolean) : SettingsIntent
    data class SetSaveSearchHistory(val save: Boolean) : SettingsIntent

    // Storage
    data object CalculateCacheSize : SettingsIntent
    data object ShowClearCacheDialog : SettingsIntent
    data object DismissDialog : SettingsIntent
    data object ConfirmClearCache : SettingsIntent

    // Export
    data object ShowExportOptionsDialog : SettingsIntent
    data object DismissExportOptionsDialog : SettingsIntent
    data class SetExportSettings(val include: Boolean) : SettingsIntent
    data class SetExportImages(val include: Boolean) : SettingsIntent
    data class SetExportTags(val include: Boolean) : SettingsIntent
    data object ConfirmExport : SettingsIntent
    data class ExportToUri(val uri: android.net.Uri) : SettingsIntent

    // Import
    data object ImportData : SettingsIntent
    data class ImportFromUri(val uri: android.net.Uri) : SettingsIntent
    data object ConfirmImport : SettingsIntent
    data object DismissImportConfirmDialog : SettingsIntent

    // About
    data object OpenLicenses : SettingsIntent
    data object OpenPrivacyPolicy : SettingsIntent
}
