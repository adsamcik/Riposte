package com.adsamcik.riposte.feature.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.core.ml.EmbeddingGemmaGenerator
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.UserDensityPreference
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.presentation.component.DialogSettingItem
import com.adsamcik.riposte.feature.settings.presentation.component.SettingsSection
import com.adsamcik.riposte.feature.settings.presentation.component.ClickableSettingItem
import com.adsamcik.riposte.feature.settings.presentation.component.SwitchSettingItem

internal fun LazyListScope.appearanceSection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    item(key = "appearance") {
        SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
            ThemeSettingItem(uiState = uiState, onIntent = onIntent)
            LanguageSettingItem(uiState = uiState, onIntent = onIntent)
            SwitchSettingItem(
                title = stringResource(R.string.settings_dynamic_colors_title),
                subtitle = stringResource(R.string.settings_dynamic_colors_subtitle),
                checked = uiState.dynamicColorsEnabled,
                onCheckedChange = { onIntent(SettingsIntent.SetDynamicColors(it)) },
                icon = Icons.Default.ColorLens,
            )
            GridDensitySettingItem(uiState = uiState, onIntent = onIntent)
        }
    }
}

@Composable
private fun ThemeSettingItem(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
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
}

@Composable
private fun LanguageSettingItem(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
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
}

@Composable
private fun GridDensitySettingItem(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
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
                UserDensityPreference.COMPACT -> stringResource(R.string.settings_grid_density_compact)
                UserDensityPreference.STANDARD -> stringResource(R.string.settings_grid_density_standard)
                UserDensityPreference.DENSE -> stringResource(R.string.settings_grid_density_dense)
            }
        },
    )
}

internal fun LazyListScope.searchSection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    item(key = "search") {
        SettingsSection(title = stringResource(R.string.settings_section_search)) {
            SwitchSettingItem(
                title = stringResource(R.string.settings_semantic_search_title),
                subtitle = stringResource(R.string.settings_semantic_search_subtitle),
                checked = uiState.enableSemanticSearch,
                onCheckedChange = { onIntent(SettingsIntent.SetEnableSemanticSearch(it)) },
                icon = Icons.Default.Psychology,
            )

            if (uiState.enableSemanticSearch) {
                EmbeddingSearchSettings(uiState = uiState)
            }

            SwitchSettingItem(
                title = stringResource(R.string.settings_search_history_title),
                subtitle = stringResource(R.string.settings_search_history_subtitle),
                checked = uiState.saveSearchHistory,
                onCheckedChange = { onIntent(SettingsIntent.SetSaveSearchHistory(it)) },
                icon = Icons.Default.History,
            )

            SwitchSettingItem(
                title = stringResource(R.string.settings_sort_emojis_by_usage_title),
                subtitle = stringResource(R.string.settings_sort_emojis_by_usage_subtitle),
                checked = uiState.sortEmojisByUsage,
                onCheckedChange = { onIntent(SettingsIntent.SetSortEmojisByUsage(it)) },
                icon = Icons.Default.TrendingUp,
            )
        }
    }
}

@Composable
private fun EmbeddingSearchSettings(uiState: SettingsUiState) {
    val embeddingState = uiState.embeddingSearchState ?: return

    if (embeddingState.modelError != null) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_search_index_title)) },
            supportingContent = {
                Text(
                    text = embeddingErrorMessage(embeddingState.modelError),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingContent = { Icon(imageVector = Icons.Default.Storage, contentDescription = null) },
        )
        return
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_search_index_title)) },
        supportingContent = {
            Column {
                Text(embeddingIndexSubtitle(embeddingState))
                if (!embeddingState.isFullyIndexed) {
                    Spacer(Modifier.size(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (embeddingState.totalCount > 0) {
                                embeddingState.indexedCount.toFloat() / embeddingState.totalCount
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
            }
        },
        leadingContent = { Icon(imageVector = Icons.Default.Storage, contentDescription = null) },
    )
}

@Composable
private fun embeddingIndexSubtitle(embeddingState: EmbeddingSearchState): String =
    when {
        embeddingState.isFullyIndexed ->
            stringResource(
                R.string.settings_search_index_complete,
                embeddingState.indexedCount,
            )
        embeddingState.regenerationCount > 0 ->
            stringResource(
                R.string.settings_search_index_regenerating,
                embeddingState.indexedCount,
                embeddingState.totalCount,
                embeddingState.regenerationCount,
            )
        else ->
            stringResource(
                R.string.settings_search_index_progress,
                embeddingState.indexedCount,
                embeddingState.totalCount,
            )
    }

@Composable
private fun embeddingErrorMessage(modelError: String): String =
    when (modelError) {
        EmbeddingGemmaGenerator.ERROR_NOT_COMPATIBLE ->
            stringResource(R.string.settings_search_index_error_not_supported)
        EmbeddingGemmaGenerator.ERROR_FILES_NOT_FOUND ->
            stringResource(R.string.settings_search_index_error_not_included)
        else ->
            stringResource(R.string.settings_search_index_error_failed)
    }

internal fun LazyListScope.funStatsSummarySection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    if (uiState.totalMemeCount == 0) return

    item(key = "fun_stats_summary") {
        SettingsSection(title = stringResource(R.string.settings_section_fun_stats)) {
            ClickableSettingItem(
                title = stringResource(R.string.settings_fun_stats_summary_title),
                subtitle = stringResource(R.string.settings_fun_stats_summary_subtitle),
                onClick = { onIntent(SettingsIntent.OpenFunStats) },
                icon = Icons.Default.TrendingUp,
            )
        }
    }
}
