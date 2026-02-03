package com.mememymood.feature.settings.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mememymood.feature.settings.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mememymood.core.model.DarkMode
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.UserDensityPreference
import com.mememymood.feature.settings.presentation.component.ClickableSettingItem
import com.mememymood.feature.settings.presentation.component.DialogSettingItem
import com.mememymood.feature.settings.presentation.component.SettingsSection
import com.mememymood.feature.settings.presentation.component.SliderSettingItem
import com.mememymood.feature.settings.presentation.component.SwitchSettingItem
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    // State to trigger file picker launches
    var launchExportPicker by remember { mutableStateOf(false) }
    var launchImportPicker by remember { mutableStateOf(false) }
    
    // Export file picker launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            // Trigger export to this location
            viewModel.onIntent(SettingsIntent.ExportData)
        }
    }
    
    // Import file picker launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFromFile(uri)
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
                    exportLauncher.launch("meme_my_mood_backup_${System.currentTimeMillis()}.json")
                }
                is SettingsEffect.LaunchImportPicker -> {
                    importLauncher.launch(arrayOf("application/json"))
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
            }
        }
    }

    // Clear cache confirmation dialog
    if (uiState.showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(SettingsIntent.DismissDialog) },
            title = { Text(stringResource(R.string.settings_clear_cache_dialog_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_dialog_message, uiState.cacheSize)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(SettingsIntent.ConfirmClearCache) }) {
                    Text(stringResource(R.string.settings_clear_cache_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(SettingsIntent.DismissDialog) }) {
                    Text(stringResource(R.string.settings_clear_cache_dialog_cancel))
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
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
                        onValueChange = { viewModel.onIntent(SettingsIntent.SetDarkMode(it)) },
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
                            selectedValue = uiState.availableLanguages.find { it.code == uiState.currentLanguage }
                                ?: defaultLanguage,
                            values = uiState.availableLanguages,
                            onValueChange = { viewModel.onIntent(SettingsIntent.SetLanguage(it.code)) },
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
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetDynamicColors(it)) },
                        icon = Icons.Default.ColorLens,
                    )
                }
            }

            // Display Section
            item {
                SettingsSection(title = stringResource(R.string.settings_section_display)) {
                    DialogSettingItem(
                        title = stringResource(R.string.settings_grid_density_title),
                        subtitle = stringResource(R.string.settings_grid_density_subtitle),
                        selectedValue = uiState.gridDensityPreference,
                        values = UserDensityPreference.entries,
                        onValueChange = { viewModel.onIntent(SettingsIntent.SetGridDensity(it)) },
                        icon = Icons.Default.GridView,
                        valueLabel = { preference ->
                            when (preference) {
                                UserDensityPreference.AUTO -> stringResource(R.string.settings_grid_density_auto)
                                UserDensityPreference.COMPACT -> stringResource(R.string.settings_grid_density_compact)
                                UserDensityPreference.STANDARD -> stringResource(R.string.settings_grid_density_standard)
                                UserDensityPreference.DENSE -> stringResource(R.string.settings_grid_density_dense)
                            }
                        },
                    )
                }
            }

            // Default Sharing Section
            item {
                SettingsSection(title = stringResource(R.string.settings_section_default_sharing)) {
                    DialogSettingItem(
                        title = stringResource(R.string.settings_format_title),
                        selectedValue = uiState.defaultFormat,
                        values = listOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP),
                        onValueChange = { viewModel.onIntent(SettingsIntent.SetDefaultFormat(it)) },
                        icon = Icons.Default.Image,
                        valueLabel = { it.name },
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.settings_quality_title),
                        value = uiState.defaultQuality.toFloat(),
                        onValueChange = { viewModel.onIntent(SettingsIntent.SetDefaultQuality(it.toInt())) },
                        icon = Icons.Default.HighQuality,
                        valueRange = 10f..100f,
                        steps = 8,
                        valueLabel = { stringResource(R.string.settings_quality_value, it.toInt()) },
                    )

                    DialogSettingItem(
                        title = stringResource(R.string.settings_max_size_title),
                        selectedValue = uiState.defaultMaxDimension,
                        values = listOf(480, 720, 1080, 2048),
                        onValueChange = { viewModel.onIntent(SettingsIntent.SetDefaultMaxDimension(it)) },
                        icon = Icons.Default.PhotoSizeSelectLarge,
                        valueLabel = { dimension ->
                            when (dimension) {
                                480 -> stringResource(R.string.settings_max_size_small)
                                720 -> stringResource(R.string.settings_max_size_medium)
                                1080 -> stringResource(R.string.settings_max_size_large)
                                2048 -> stringResource(R.string.settings_max_size_original)
                                else -> stringResource(R.string.settings_max_size_custom, dimension)
                            }
                        },
                    )

                    SwitchSettingItem(
                        title = stringResource(R.string.settings_keep_metadata_title),
                        subtitle = stringResource(R.string.settings_keep_metadata_subtitle),
                        checked = uiState.keepMetadata,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetKeepMetadata(it)) },
                        icon = Icons.Default.Description,
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
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetEnableSemanticSearch(it)) },
                        icon = Icons.Default.Psychology,
                    )

                    SwitchSettingItem(
                        title = stringResource(R.string.settings_search_history_title),
                        subtitle = stringResource(R.string.settings_search_history_subtitle),
                        checked = uiState.saveSearchHistory,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetSaveSearchHistory(it)) },
                        icon = Icons.Default.History,
                    )
                }
            }

            // Storage Section
            item {
                SettingsSection(title = stringResource(R.string.settings_section_storage)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.settings_clear_cache_title),
                        subtitle = stringResource(R.string.settings_clear_cache_subtitle, uiState.cacheSize),
                        onClick = { viewModel.onIntent(SettingsIntent.ShowClearCacheDialog) },
                        icon = Icons.Default.CleaningServices,
                        showChevron = false,
                    )

                    ClickableSettingItem(
                        title = stringResource(R.string.settings_export_data_title),
                        subtitle = stringResource(R.string.settings_export_data_subtitle),
                        onClick = { viewModel.onIntent(SettingsIntent.ExportData) },
                        icon = Icons.Default.Upload,
                    )

                    ClickableSettingItem(
                        title = stringResource(R.string.settings_import_data_title),
                        subtitle = stringResource(R.string.settings_import_data_subtitle),
                        onClick = { viewModel.onIntent(SettingsIntent.ImportData) },
                        icon = Icons.Default.Download,
                    )
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
                        onClick = { viewModel.onIntent(SettingsIntent.OpenLicenses) },
                        icon = Icons.Default.Description,
                    )

                    ClickableSettingItem(
                        title = stringResource(R.string.settings_privacy_policy_title),
                        onClick = { viewModel.onIntent(SettingsIntent.OpenPrivacyPolicy) },
                        icon = Icons.Default.Policy,
                    )
                }
            }
        }
    }
}
