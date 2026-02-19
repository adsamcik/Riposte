package com.adsamcik.riposte.feature.settings.presentation

sealed interface SettingsEffect {
    data class ShowSnackbar(val message: String) : SettingsEffect

    data object NavigateToLicenses : SettingsEffect

    data object NavigateToFunStats : SettingsEffect

    data class OpenUrl(val url: String) : SettingsEffect

    data object LaunchExportPicker : SettingsEffect

    data object LaunchImportPicker : SettingsEffect

    data class ExportComplete(val path: String) : SettingsEffect

    data class ImportComplete(val count: Int) : SettingsEffect

    data class ShowError(val message: String) : SettingsEffect

    data class ShareText(val text: String, val title: String) : SettingsEffect

    data object NavigateToDuplicateDetection : SettingsEffect
}
