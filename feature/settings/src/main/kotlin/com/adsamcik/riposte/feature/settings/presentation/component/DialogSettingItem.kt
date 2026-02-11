package com.adsamcik.riposte.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.feature.settings.R

@Composable
fun <T> DialogSettingItem(
    title: String,
    selectedValue: T,
    values: List<T>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    dialogTitle: String = title,
    valueLabel: @Composable (T) -> String,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(subtitle ?: valueLabel(selectedValue))
        },
        leadingContent =
            icon?.let {
                { Icon(imageVector = it, contentDescription = null) }
            },
        modifier = modifier.clickable(enabled = enabled) { showDialog = true },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(dialogTitle) },
            text = {
                Column {
                    values.forEach { value ->
                        val label = valueLabel(value)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = value == selectedValue,
                                        onClick = {
                                            onValueChange(value)
                                            showDialog = false
                                        },
                                        role = Role.RadioButton,
                                    )
                                    .padding(vertical = 12.dp),
                        ) {
                            RadioButton(
                                selected = value == selectedValue,
                                onClick = null,
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.settings_clear_cache_dialog_cancel))
                }
            },
        )
    }
}
