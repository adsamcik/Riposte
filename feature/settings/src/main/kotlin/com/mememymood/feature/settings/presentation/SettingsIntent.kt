package com.mememymood.feature.settings.presentation

sealed interface SettingsIntent {
    // Appearance
    data class SetDarkMode(val mode: DarkMode) : SettingsIntent
    data class SetDynamicColors(val enabled: Boolean) : SettingsIntent

    // Sharing
    data class SetDefaultFormat(val format: com.mememymood.core.model.ImageFormat) : SettingsIntent
    data class SetDefaultQuality(val quality: Int) : SettingsIntent
    data class SetDefaultMaxDimension(val dimension: Int) : SettingsIntent
    data class SetKeepMetadata(val keep: Boolean) : SettingsIntent
    data class SetAddWatermark(val add: Boolean) : SettingsIntent

    // Search
    data class SetEnableSemanticSearch(val enabled: Boolean) : SettingsIntent
    data class SetSaveSearchHistory(val save: Boolean) : SettingsIntent

    // Storage
    data object CalculateCacheSize : SettingsIntent
    data object ShowClearCacheDialog : SettingsIntent
    data object DismissDialog : SettingsIntent
    data object ConfirmClearCache : SettingsIntent
    data object ExportData : SettingsIntent
    data object ImportData : SettingsIntent

    // About
    data object OpenLicenses : SettingsIntent
    data object OpenPrivacyPolicy : SettingsIntent
}

enum class DarkMode {
    SYSTEM,
    LIGHT,
    DARK,
}
