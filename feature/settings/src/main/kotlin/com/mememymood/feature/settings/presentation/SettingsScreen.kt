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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mememymood.core.model.ImageFormat
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
                    snackbarHostState.showSnackbar("Exported to ${effect.path}")
                }
                is SettingsEffect.ImportComplete -> {
                    snackbarHostState.showSnackbar("Imported ${effect.count} memes")
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
            title = { Text("Clear Cache") },
            text = { Text("This will delete ${uiState.cacheSize} of cached data. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(SettingsIntent.ConfirmClearCache) }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(SettingsIntent.DismissDialog) }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                SettingsSection(title = "Appearance") {
                    DialogSettingItem(
                        title = "Theme",
                        selectedValue = uiState.darkMode,
                        values = DarkMode.entries,
                        onValueChange = { viewModel.onIntent(SettingsIntent.SetDarkMode(it)) },
                        icon = Icons.Default.Brightness4,
                        valueLabel = { mode ->
                            when (mode) {
                                DarkMode.SYSTEM -> "System default"
                                DarkMode.LIGHT -> "Light"
                                DarkMode.DARK -> "Dark"
                            }
                        },
                    )

                    SwitchSettingItem(
                        title = "Dynamic Colors",
                        subtitle = "Use colors from your wallpaper",
                        checked = uiState.dynamicColorsEnabled,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetDynamicColors(it)) },
                        icon = Icons.Default.ColorLens,
                    )
                }
            }

            // Default Sharing Section
            item {
                SettingsSection(title = "Default Sharing") {
                    DialogSettingItem(
                        title = "Format",
                        selectedValue = uiState.defaultFormat,
                        values = listOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP),
                        onValueChange = { viewModel.onIntent(SettingsIntent.SetDefaultFormat(it)) },
                        icon = Icons.Default.Image,
                        valueLabel = { it.name },
                    )

                    SliderSettingItem(
                        title = "Quality",
                        value = uiState.defaultQuality.toFloat(),
                        onValueChange = { viewModel.onIntent(SettingsIntent.SetDefaultQuality(it.toInt())) },
                        icon = Icons.Default.HighQuality,
                        valueRange = 10f..100f,
                        steps = 8,
                        valueLabel = { "${it.toInt()}%" },
                    )

                    DialogSettingItem(
                        title = "Max Size",
                        selectedValue = uiState.defaultMaxDimension,
                        values = listOf(480, 720, 1080, 2048),
                        onValueChange = { viewModel.onIntent(SettingsIntent.SetDefaultMaxDimension(it)) },
                        icon = Icons.Default.PhotoSizeSelectLarge,
                        valueLabel = { dimension ->
                            when (dimension) {
                                480 -> "Small (480p)"
                                720 -> "Medium (720p)"
                                1080 -> "Large (1080p)"
                                2048 -> "Original"
                                else -> "${dimension}p"
                            }
                        },
                    )

                    SwitchSettingItem(
                        title = "Keep Metadata",
                        subtitle = "Preserve emoji tags when sharing",
                        checked = uiState.keepMetadata,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetKeepMetadata(it)) },
                        icon = Icons.Default.Description,
                    )
                }
            }

            // Search Section
            item {
                SettingsSection(title = "Search") {
                    SwitchSettingItem(
                        title = "Semantic Search",
                        subtitle = "AI-powered search for similar memes",
                        checked = uiState.enableSemanticSearch,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetEnableSemanticSearch(it)) },
                        icon = Icons.Default.Psychology,
                    )

                    SwitchSettingItem(
                        title = "Search History",
                        subtitle = "Save recent searches",
                        checked = uiState.saveSearchHistory,
                        onCheckedChange = { viewModel.onIntent(SettingsIntent.SetSaveSearchHistory(it)) },
                        icon = Icons.Default.History,
                    )
                }
            }

            // Storage Section
            item {
                SettingsSection(title = "Storage") {
                    ClickableSettingItem(
                        title = "Clear Cache",
                        subtitle = "Free up ${uiState.cacheSize}",
                        onClick = { viewModel.onIntent(SettingsIntent.ShowClearCacheDialog) },
                        icon = Icons.Default.CleaningServices,
                        showChevron = false,
                    )

                    ClickableSettingItem(
                        title = "Export Data",
                        subtitle = "Backup your memes and settings",
                        onClick = { viewModel.onIntent(SettingsIntent.ExportData) },
                        icon = Icons.Default.Upload,
                    )

                    ClickableSettingItem(
                        title = "Import Data",
                        subtitle = "Restore from backup",
                        onClick = { viewModel.onIntent(SettingsIntent.ImportData) },
                        icon = Icons.Default.Download,
                    )
                }
            }

            // About Section
            item {
                SettingsSection(title = "About") {
                    ClickableSettingItem(
                        title = "Version",
                        subtitle = null,
                        onClick = { },
                        icon = Icons.Default.Info,
                        showChevron = false,
                        trailingText = uiState.appVersion,
                    )

                    ClickableSettingItem(
                        title = "Open Source Licenses",
                        onClick = { viewModel.onIntent(SettingsIntent.OpenLicenses) },
                        icon = Icons.Default.Description,
                    )

                    ClickableSettingItem(
                        title = "Privacy Policy",
                        onClick = { viewModel.onIntent(SettingsIntent.OpenPrivacyPolicy) },
                        icon = Icons.Default.Policy,
                    )
                }
            }
        }
    }
}
