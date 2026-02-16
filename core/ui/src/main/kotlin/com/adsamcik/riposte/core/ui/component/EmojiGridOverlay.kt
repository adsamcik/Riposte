package com.adsamcik.riposte.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.ui.R
import com.adsamcik.riposte.core.ui.theme.RiposteTheme

/**
 * A modal bottom sheet overlay displaying all emojis in a grid layout.
 *
 * Used when the user taps the "more" chip in [EmojiFilterRail] to show
 * all available emoji filters in a 4-column grid.
 *
 * @param emojis List of emoji-count pairs.
 * @param activeFilter Currently selected emoji filter, or null if none.
 * @param onEmojiSelected Callback when an emoji filter is selected.
 * @param onDismiss Callback when the overlay is dismissed.
 * @param modifier Modifier to be applied to the component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiGridOverlay(
    emojis: List<Pair<String, Int>>,
    activeFilter: String?,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column {
            // Header row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ui_emoji_grid_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.ui_emoji_grid_close),
                    )
                }
            }

            // Emoji grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = emojis,
                    key = { it.first },
                ) { (emoji, _) ->
                    EmojiChip(
                        emojiTag = EmojiTag.fromEmoji(emoji),
                        onClick = { onEmojiSelected(emoji) },
                        isSelected = emoji == activeFilter,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "EmojiGridOverlay", showBackground = true)
@Composable
private fun EmojiGridOverlayPreview() {
    RiposteTheme {
        EmojiGridOverlay(
            emojis =
                listOf(
                    "😂" to 42,
                    "🔥" to 35,
                    "💀" to 28,
                    "😭" to 22,
                    "🥺" to 18,
                    "😤" to 15,
                    "🤔" to 12,
                    "😏" to 10,
                    "🤣" to 8,
                    "😅" to 5,
                    "🙃" to 4,
                    "😊" to 3,
                    "🥹" to 2,
                    "😩" to 1,
                ),
            activeFilter = "😂",
            onEmojiSelected = {},
            onDismiss = {},
        )
    }
}
