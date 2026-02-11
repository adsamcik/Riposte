package com.adsamcik.riposte.feature.settings.presentation

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.UserDensityPreference
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.presentation.component.ClickableSettingItem
import com.adsamcik.riposte.feature.settings.presentation.component.DialogSettingItem
import com.adsamcik.riposte.feature.settings.presentation.component.SettingsSection
import com.adsamcik.riposte.feature.settings.presentation.component.SliderSettingItem
import com.adsamcik.riposte.feature.settings.presentation.component.SwitchSettingItem
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat
import java.util.Date

/**
 * Stateful SettingsScreen that manages ViewModel and side effects.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // Export file picker launcher
    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.onIntent(SettingsIntent.ExportToUri(uri))
            }
        }

    // Import file picker launcher
    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.onIntent(SettingsIntent.ImportFromUri(uri))
            }
        }

    LaunchedEffect(Unit) {
        viewModel.onIntent(SettingsIntent.CalculateCacheSize)

        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SettingsEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is SettingsEffect.NavigateToLicenses -> {
                    // Navigate to licenses screen
                }
                is SettingsEffect.OpenUrl -> {
                    uriHandler.openUri(effect.url)
                }
                is SettingsEffect.LaunchExportPicker -> {
                    exportLauncher.launch("riposte_backup_${System.currentTimeMillis()}.zip")
                }
                is SettingsEffect.LaunchImportPicker -> {
                    importLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
                }
                is SettingsEffect.ExportComplete -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.settings_export_complete, effect.path))
                }
                is SettingsEffect.ImportComplete -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.settings_import_complete, effect.count))
                }
                is SettingsEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is SettingsEffect.ShareText -> {
                    val sendIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, effect.text)
                            putExtra(Intent.EXTRA_SUBJECT, effect.title)
                        }
                    context.startActivity(
                        Intent.createChooser(sendIntent, effect.title),
                    )
                }
            }
        }
    }

    SettingsScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless SettingsScreen for testing and previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // Clear cache confirmation dialog
    if (uiState.showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DismissDialog) },
            title = { Text(stringResource(R.string.settings_clear_cache_dialog_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_dialog_message, uiState.cacheSize)) },
            confirmButton = {
                TextButton(onClick = { onIntent(SettingsIntent.ConfirmClearCache) }) {
                    Text(
                        text = stringResource(R.string.settings_clear_cache_dialog_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.DismissDialog) }) {
                    Text(stringResource(R.string.settings_clear_cache_dialog_cancel))
                }
            },
        )
    }

    // Export options dialog
    if (uiState.showExportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DismissExportOptionsDialog) },
            title = { Text(stringResource(R.string.settings_export_options_title)) },
            text = {
                Column {
                    ExportOptionRow(
                        label = stringResource(R.string.settings_export_option_settings),
                        checked = uiState.exportSettings,
                        onCheckedChange = { onIntent(SettingsIntent.SetExportSettings(it)) },
                    )
                    ExportOptionRow(
                        label = stringResource(R.string.settings_export_option_images) + " (coming soon)",
                        checked = false,
                        onCheckedChange = { },
                        enabled = false,
                    )
                    ExportOptionRow(
                        label = stringResource(R.string.settings_export_option_tags) + " (coming soon)",
                        checked = false,
                        onCheckedChange = { },
                        enabled = false,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onIntent(SettingsIntent.ConfirmExport) },
                    enabled = uiState.exportSettings || uiState.exportImages || uiState.exportTags,
                ) {
                    Text(stringResource(R.string.settings_export_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.DismissExportOptionsDialog) }) {
                    Text(stringResource(R.string.settings_dialog_cancel))
                }
            },
        )
    }

    // Import confirmation dialog
    if (uiState.showImportConfirmDialog) {
        val dateText =
            uiState.importBackupTimestamp?.let { timestamp ->
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
            }
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DismissImportConfirmDialog) },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = {
                Text(
                    if (dateText != null) {
                        stringResource(R.string.settings_import_confirm_message, dateText)
                    } else {
                        stringResource(R.string.settings_import_confirm_message_unknown)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(SettingsIntent.ConfirmImport) }) {
                    Text(stringResource(R.string.settings_import_confirm_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.DismissImportConfirmDialog) }) {
                    Text(stringResource(R.string.settings_dialog_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                // Appearance Section
                item {
                    SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                        DialogSettingItem(
                            title = stringResource(R.string.settings_theme_title),
                            selectedValue = uiState.darkMode,
                            values = DarkMode.entries,
                            onValueChange = { onIntent(SettingsIntent.SetDarkMode(it)) },
                            icon = Icons.Default.Brightness4,
                            valueLabel = { mode ->
                                when (mode) {
                                    DarkMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                    DarkMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                    DarkMode.DARK -> stringResource(R.string.settings_theme_dark)
                                }
                            },
                        )

                        if (uiState.availableLanguages.isNotEmpty()) {
                            val defaultLanguage = uiState.availableLanguages.first()
                            DialogSettingItem(
                                title = stringResource(R.string.settings_language_title),
                                subtitle = stringResource(R.string.settings_language_subtitle),
                                selectedValue =
                                    uiState.availableLanguages.find { it.code == uiState.currentLanguage }
                                        ?: defaultLanguage,
                                values = uiState.availableLanguages,
                                onValueChange = { onIntent(SettingsIntent.SetLanguage(it.code)) },
                                icon = Icons.Default.Language,
                                valueLabel = { language ->
                                    when (language.code) {
                                        null -> stringResource(R.string.settings_language_system)
                                        "en" -> stringResource(R.string.settings_language_en)
                                        "cs" -> stringResource(R.string.settings_language_cs)
                                        "de" -> stringResource(R.string.settings_language_de)
                                        "es" -> stringResource(R.string.settings_language_es)
                                        "pt" -> stringResource(R.string.settings_language_pt)
                                        else -> language.nativeName
                                    }
                                },
                            )
                        }

                        SwitchSettingItem(
                            title = stringResource(R.string.settings_dynamic_colors_title),
                            subtitle = stringResource(R.string.settings_dynamic_colors_subtitle),
                            checked = uiState.dynamicColorsEnabled,
                            onCheckedChange = { onIntent(SettingsIntent.SetDynamicColors(it)) },
                            icon = Icons.Default.ColorLens,
                        )

                        DialogSettingItem(
                            title = stringResource(R.string.settings_grid_density_title),
                            subtitle = stringResource(R.string.settings_grid_density_subtitle),
                            selectedValue = uiState.gridDensityPreference,
                            values = UserDensityPreference.entries,
                            onValueChange = { onIntent(SettingsIntent.SetGridDensity(it)) },
                            icon = Icons.Default.GridView,
                            valueLabel = { preference ->
                                when (preference) {
                                    UserDensityPreference.AUTO -> stringResource(R.string.settings_grid_density_auto)
                                    UserDensityPreference.COMPACT ->
                                        stringResource(
                                            R.string.settings_grid_density_compact,
                                        )
                                    UserDensityPreference.STANDARD ->
                                        stringResource(
                                            R.string.settings_grid_density_standard,
                                        )
                                    UserDensityPreference.DENSE -> stringResource(R.string.settings_grid_density_dense)
                                }
                            },
                        )
                    }
                }

                // Search Section
                item {
                    SettingsSection(title = stringResource(R.string.settings_section_search)) {
                        SwitchSettingItem(
                            title = stringResource(R.string.settings_semantic_search_title),
                            subtitle = stringResource(R.string.settings_semantic_search_subtitle),
                            checked = uiState.enableSemanticSearch,
                            onCheckedChange = { onIntent(SettingsIntent.SetEnableSemanticSearch(it)) },
                            icon = Icons.Default.Psychology,
                        )

                        if (uiState.enableSemanticSearch) {
                            val embeddingState = uiState.embeddingSearchState
                            if (embeddingState != null) {
                                ClickableSettingItem(
                                    title = stringResource(R.string.settings_embedding_model_title),
                                    subtitle =
                                        stringResource(
                                            R.string.settings_embedding_model_subtitle,
                                            embeddingState.dimension,
                                        ),
                                    onClick = { },
                                    icon = Icons.Default.Hub,
                                    showChevron = false,
                                    trailingText =
                                        stringResource(
                                            R.string.settings_embedding_model_version,
                                            embeddingState.modelName,
                                            embeddingState.modelVersion,
                                        ),
                                )

                                ClickableSettingItem(
                                    title = stringResource(R.string.settings_search_index_title),
                                    subtitle =
                                        if (embeddingState.isFullyIndexed) {
                                            stringResource(
                                                R.string.settings_search_index_complete,
                                                embeddingState.indexedCount,
                                            )
                                        } else if (embeddingState.regenerationCount > 0) {
                                            stringResource(
                                                R.string.settings_search_index_regenerating,
                                                embeddingState.indexedCount,
                                                embeddingState.totalCount,
                                                embeddingState.regenerationCount,
                                            )
                                        } else {
                                            stringResource(
                                                R.string.settings_search_index_progress,
                                                embeddingState.indexedCount,
                                                embeddingState.totalCount,
                                            )
                                        },
                                    onClick = { },
                                    icon = Icons.Default.Storage,
                                    showChevron = false,
                                )
                            }
                        }

                        SwitchSettingItem(
                            title = stringResource(R.string.settings_search_history_title),
                            subtitle = stringResource(R.string.settings_search_history_subtitle),
                            checked = uiState.saveSearchHistory,
                            onCheckedChange = { onIntent(SettingsIntent.SetSaveSearchHistory(it)) },
                            icon = Icons.Default.History,
                        )
                    }
                }

                // Sharing Section
                item {
                    SettingsSection(title = stringResource(R.string.settings_section_sharing)) {
                        DialogSettingItem(
                            title = stringResource(R.string.settings_share_format_title),
                            subtitle = stringResource(R.string.settings_share_format_subtitle),
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

                        if (uiState.defaultFormat.isLossy) {
                            var localQuality by remember { mutableFloatStateOf(uiState.defaultQuality.toFloat()) }
                            LaunchedEffect(uiState.defaultQuality) {
                                localQuality = uiState.defaultQuality.toFloat()
                            }
                            SliderSettingItem(
                                title = stringResource(R.string.settings_share_quality_title),
                                subtitle = stringResource(R.string.settings_share_quality_subtitle),
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

                        DialogSettingItem(
                            title = stringResource(R.string.settings_share_max_size_title),
                            subtitle = stringResource(R.string.settings_share_max_size_subtitle),
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

                        SwitchSettingItem(
                            title = stringResource(R.string.settings_share_strip_metadata_title),
                            subtitle = stringResource(R.string.settings_share_strip_metadata_subtitle),
                            checked = !uiState.stripMetadata,
                            onCheckedChange = { onIntent(SettingsIntent.SetStripMetadata(!it)) },
                        )

                        SwitchSettingItem(
                            title = stringResource(R.string.settings_use_native_share_title),
                            subtitle = stringResource(R.string.settings_use_native_share_subtitle),
                            checked = uiState.useNativeShareDialog,
                            onCheckedChange = { onIntent(SettingsIntent.SetUseNativeShareDialog(it)) },
                        )
                    }
                }

                // Storage Section
                item {
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

                // Diagnostics Section
                item {
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

                // About Section
                item {
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
        } // end else
    }
}

@Composable
private fun ExportOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                    enabled = enabled,
                )
                .padding(vertical = 12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

// region Previews

@Preview(name = "Settings", showBackground = true)
@Preview(name = "Settings Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        SettingsScreen(
            uiState =
                SettingsUiState(
                    cacheSize = "24.5 MB",
                    appVersion = "1.0.0 (42)",
                    embeddingSearchState =
                        EmbeddingSearchState(
                            modelName = "embeddinggemma",
                            modelVersion = "1.0.0",
                            dimension = 768,
                            indexedCount = 42,
                            totalCount = 50,
                            pendingCount = 8,
                            regenerationCount = 0,
                        ),
                ),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

// endregion
