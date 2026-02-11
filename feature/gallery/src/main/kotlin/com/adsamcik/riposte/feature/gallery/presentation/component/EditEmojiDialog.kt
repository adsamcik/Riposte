package com.adsamcik.riposte.feature.gallery.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.riposte.feature.gallery.R

/**
 * Common emojis for quick selection.
 * TODO: Replace with emojis from the user's meme collection (query from DB) plus freeform input.
 */
private val commonEmojis =
    listOf(
        "😀", "😂", "🤣", "😊", "😍", "🥺", "😭", "😤", "😡", "🤔",
        "😏", "😴", "🤯", "🥳", "😎", "🤡", "👀", "💀", "🔥", "💯",
        "❤️", "💔", "👍", "👎", "👏", "🙏", "💪", "🎉", "✨", "🌟",
    )

/**
 * Dialog for editing emoji tags on a meme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditEmojiDialog(
    selectedEmojis: List<String>,
    onAddEmoji: (String) -> Unit,
    onRemoveEmoji: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredEmojis =
        remember(searchQuery) {
            if (searchQuery.isBlank()) {
                commonEmojis
            } else {
                commonEmojis.filter { emoji ->
                    emoji.contains(searchQuery)
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_emoji_dialog_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.gallery_emoji_search_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.gallery_cd_clear),
                                modifier = Modifier.clickable { searchQuery = "" },
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Selected emojis section
                if (selectedEmojis.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.gallery_emoji_section_selected),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedEmojis.forEach { emoji ->
                            EmojiChip(
                                emoji = emoji,
                                isSelected = true,
                                showRemove = true,
                                onClick = { onRemoveEmoji(emoji) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Available emojis section
                Text(
                    text = stringResource(R.string.gallery_emoji_section_available),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filteredEmojis.forEach { emoji ->
                        val isSelected = selectedEmojis.contains(emoji)
                        EmojiChip(
                            emoji = emoji,
                            isSelected = isSelected,
                            showRemove = false,
                            onClick = {
                                if (isSelected) {
                                    onRemoveEmoji(emoji)
                                } else {
                                    onAddEmoji(emoji)
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.gallery_button_done))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun EmojiChip(
    emoji: String,
    isSelected: Boolean,
    showRemove: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .border(
                    width = 1.dp,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    shape = MaterialTheme.shapes.medium,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .sizeIn(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = emoji,
            fontSize = 20.sp,
        )

        if (isSelected && showRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.gallery_cd_remove),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        } else if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.gallery_cd_selected),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
