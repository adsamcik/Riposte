package com.adsamcik.riposte.feature.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.feature.settings.R
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SettingsDialogs(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    if (uiState.showClearCacheDialog) {
        ClearCacheDialog(uiState = uiState, onIntent = onIntent)
    }

    if (uiState.showExportOptionsDialog) {
        ExportOptionsDialog(uiState = uiState, onIntent = onIntent)
    }

    if (uiState.showImportConfirmDialog) {
        ImportConfirmDialog(uiState = uiState, onIntent = onIntent)
    }
}

@Composable
private fun ClearCacheDialog(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onIntent(SettingsIntent.DismissDialog) },
        title = { Text(stringResource(R.string.settings_clear_cache_dialog_title)) },
        text = { Text(stringResource(R.string.settings_clear_cache_dialog_message, uiState.cacheSize)) },
        confirmButton = {
            TextButton(
                onClick = { onIntent(SettingsIntent.ConfirmClearCache) },
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(text = stringResource(R.string.settings_clear_cache_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(SettingsIntent.DismissDialog) }) {
                Text(stringResource(R.string.settings_clear_cache_dialog_cancel))
            }
        },
    )
}

@Composable
private fun ExportOptionsDialog(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
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
                    label = stringResource(R.string.settings_export_option_images_coming_soon),
                    checked = false,
                    onCheckedChange = { },
                    enabled = false,
                )
                ExportOptionRow(
                    label = stringResource(R.string.settings_export_option_tags_coming_soon),
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

@Composable
private fun ImportConfirmDialog(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
) {
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

@Composable
internal fun ExportOptionRow(
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
