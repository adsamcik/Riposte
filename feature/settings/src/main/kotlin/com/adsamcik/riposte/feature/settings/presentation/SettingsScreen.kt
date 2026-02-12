package com.adsamcik.riposte.feature.settings.presentation

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.riposte.feature.settings.R
import kotlinx.coroutines.flow.collectLatest

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
    modifier: Modifier = Modifier,
) {
    SettingsDialogs(uiState = uiState, onIntent = onIntent)

    Scaffold(
        modifier = modifier,
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
                appearanceSection(uiState = uiState, onIntent = onIntent)
                searchSection(uiState = uiState, onIntent = onIntent)
                sharingSection(uiState = uiState, onIntent = onIntent)
                storageSection(uiState = uiState, onIntent = onIntent)
                diagnosticsSection(uiState = uiState, onIntent = onIntent)
                aboutSection(uiState = uiState, onIntent = onIntent)
            }
        }
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
