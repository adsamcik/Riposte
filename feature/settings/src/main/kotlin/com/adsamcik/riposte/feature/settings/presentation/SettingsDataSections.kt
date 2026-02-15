@file:Suppress("MagicNumber")

package com.adsamcik.riposte.feature.settings.presentation

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.presentation.component.ClickableSettingItem
import com.adsamcik.riposte.feature.settings.presentation.component.DialogSettingItem
import com.adsamcik.riposte.feature.settings.presentation.component.SettingsSection
import com.adsamcik.riposte.feature.settings.presentation.component.SliderSettingItem
import com.adsamcik.riposte.feature.settings.presentation.component.SwitchSettingItem

internal fun LazyListScope.sharingSection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    item(key = "sharing") {
        SettingsSection(title = stringResource(R.string.settings_section_sharing)) {
            ShareFormatSettingItem(uiState = uiState, onIntent = onIntent)
            ShareQualitySettingItem(uiState = uiState, onIntent = onIntent)
            ShareMaxSizeSettingItem(uiState = uiState, onIntent = onIntent)

            SwitchSettingItem(
                title = stringResource(R.string.settings_share_strip_metadata_title),
                subtitle = stringResource(R.string.settings_share_strip_metadata_subtitle),
                checked = !uiState.stripMetadata,
                onCheckedChange = { onIntent(SettingsIntent.SetStripMetadata(!it)) },
                icon = Icons.Default.NoPhotography,
            )
        }
    }
}

@Composable
private fun ShareFormatSettingItem(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    DialogSettingItem(
        title = stringResource(R.string.settings_share_format_title),
        icon = Icons.Default.Image,
        selectedValue = uiState.defaultFormat,
        values = listOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP),
        onValueChange = { onIntent(SettingsIntent.SetDefaultFormat(it)) },
        valueLabel = { format ->
            when (format) {
                ImageFormat.JPEG -> stringResource(R.string.settings_share_format_jpeg)
                ImageFormat.PNG -> stringResource(R.string.settings_share_format_png)
                ImageFormat.WEBP -> stringResource(R.string.settings_share_format_webp)
                ImageFormat.GIF -> "GIF"
            }
        },
    )
}

@Composable
private fun ShareQualitySettingItem(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    if (uiState.defaultFormat.isLossy) {
        var localQuality by remember { mutableFloatStateOf(uiState.defaultQuality.toFloat()) }
        LaunchedEffect(uiState.defaultQuality) {
            localQuality = uiState.defaultQuality.toFloat()
        }
        SliderSettingItem(
            title = stringResource(R.string.settings_share_quality_title),
            subtitle = stringResource(R.string.settings_share_quality_subtitle),
            icon = Icons.Default.HighQuality,
            value = localQuality,
            onValueChange = { localQuality = it },
            onValueChangeFinished = {
                onIntent(
                    SettingsIntent.SetDefaultQuality(localQuality.toInt()),
                )
            },
            valueRange = 10f..100f,
            steps = 8,
            valueLabel = { stringResource(R.string.settings_share_quality_value, it.toInt()) },
        )
    }
}

@Composable
private fun ShareMaxSizeSettingItem(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    DialogSettingItem(
        title = stringResource(R.string.settings_share_max_size_title),
        subtitle = stringResource(R.string.settings_share_max_size_subtitle),
        icon = Icons.Default.PhotoSizeSelectLarge,
        selectedValue = uiState.defaultMaxDimension,
        values = listOf(480, 720, 1080, 2048),
        onValueChange = { onIntent(SettingsIntent.SetDefaultMaxDimension(it)) },
        valueLabel = { dimension ->
            when (dimension) {
                480 -> stringResource(R.string.settings_share_size_small)
                720 -> stringResource(R.string.settings_share_size_medium)
                1080 -> stringResource(R.string.settings_share_size_large)
                2048 -> stringResource(R.string.settings_share_size_original)
                else -> "${dimension}px"
            }
        },
    )
}

internal fun LazyListScope.librarySection(uiState: SettingsUiState) {
    item(key = "library") {
        SettingsSection(title = stringResource(R.string.settings_section_library)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_library_total_memes_title)) },
                leadingContent = { Icon(imageVector = Icons.Default.Collections, contentDescription = null) },
                trailingContent = { Text(uiState.totalMemeCount.toString()) },
            )

            if (uiState.favoriteMemeCount > 0) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_library_favorites_title)) },
                    leadingContent = { Icon(imageVector = Icons.Default.Favorite, contentDescription = null) },
                    trailingContent = { Text(uiState.favoriteMemeCount.toString()) },
                )
            }
        }
    }
}

internal fun LazyListScope.storageSection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    item(key = "storage") {
        SettingsSection(title = stringResource(R.string.settings_section_storage)) {
            val isCacheEmpty = uiState.cacheSize == "0 B"
            if (!isCacheEmpty) {
                ClickableSettingItem(
                    title = stringResource(R.string.settings_clear_cache_title),
                    subtitle = stringResource(R.string.settings_clear_cache_subtitle, uiState.cacheSize),
                    onClick = { onIntent(SettingsIntent.ShowClearCacheDialog) },
                    icon = Icons.Default.CleaningServices,
                    showChevron = false,
                )
            }

            ClickableSettingItem(
                title = stringResource(R.string.settings_export_data_title),
                subtitle = stringResource(R.string.settings_export_data_subtitle),
                onClick = { onIntent(SettingsIntent.ShowExportOptionsDialog) },
                icon = Icons.Default.Upload,
            )

            ClickableSettingItem(
                title = stringResource(R.string.settings_import_data_title),
                subtitle = stringResource(R.string.settings_import_data_subtitle),
                onClick = { onIntent(SettingsIntent.ImportData) },
                icon = Icons.Default.Download,
            )
        }
    }
}

internal fun LazyListScope.diagnosticsSection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    item(key = "diagnostics") {
        SettingsSection(title = stringResource(R.string.settings_section_diagnostics)) {
            ClickableSettingItem(
                title = stringResource(R.string.settings_crash_share_title),
                subtitle =
                    if (uiState.crashLogCount > 0) {
                        pluralStringResource(
                            R.plurals.settings_crash_report_count,
                            uiState.crashLogCount,
                            uiState.crashLogCount,
                        )
                    } else {
                        stringResource(R.string.settings_crash_share_subtitle_empty)
                    },
                onClick = { onIntent(SettingsIntent.ShareCrashLogs) },
                icon = Icons.Default.BugReport,
                enabled = uiState.crashLogCount > 0,
            )

            if (uiState.crashLogCount > 0) {
                ClickableSettingItem(
                    title = stringResource(R.string.settings_crash_clear_title),
                    onClick = { onIntent(SettingsIntent.ClearCrashLogs) },
                    icon = Icons.Default.Delete,
                    showChevron = false,
                )
            }
        }
    }
}

internal fun LazyListScope.aboutSection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    item(key = "about") {
        SettingsSection(title = stringResource(R.string.settings_section_about)) {
            ClickableSettingItem(
                title = stringResource(R.string.settings_version_title),
                subtitle = null,
                onClick = { },
                icon = Icons.Default.Info,
                showChevron = false,
                trailingText = uiState.appVersion,
            )

            ClickableSettingItem(
                title = stringResource(R.string.settings_open_source_licenses_title),
                onClick = { onIntent(SettingsIntent.OpenLicenses) },
                icon = Icons.Default.Description,
            )

            ClickableSettingItem(
                title = stringResource(R.string.settings_privacy_policy_title),
                onClick = { onIntent(SettingsIntent.OpenPrivacyPolicy) },
                icon = Icons.Default.Policy,
            )
        }
    }
}
