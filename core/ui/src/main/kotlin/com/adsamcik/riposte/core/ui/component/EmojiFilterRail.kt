package com.adsamcik.riposte.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.ui.R
import com.adsamcik.riposte.core.ui.theme.RiposteTheme

/** Maximum number of emojis to process to prevent DoS */
private const val MAX_EMOJI_COUNT = 200

/**
 * A horizontal rail displaying emoji filter chips with overflow support.
 *
 * Shows the first [maxVisible] emojis as chips, with a "more" chip if additional
 * emojis exist. Tapping the more chip expands to show [EmojiGridOverlay].
 * When a filter is active, shows a clear button to reset it.
 *
 * @param emojis List of emoji-count pairs, sorted by frequency. Capped at [MAX_EMOJI_COUNT].
 * @param activeFilter Currently selected emoji filter, or null if none.
 * @param onEmojiSelected Callback when an emoji filter is selected (tapping the active one clears it).
 * @param onClearFilter Callback to clear the active filter.
 * @param leadingContent Optional slot for additional chips rendered before the emoji chips.
 * @param maxVisible Maximum number of emojis to show before overflow.
 * @param modifier Modifier to be applied to the component.
 */
@Composable
fun EmojiFilterRail(
    emojis: List<Pair<String, Int>>,
    activeFilter: String?,
    onEmojiSelected: (String) -> Unit,
    onClearFilter: () -> Unit = {},
    leadingContent: (LazyListScope.() -> Unit)? = null,
    maxVisible: Int = 7,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Defensive limit to prevent DoS with huge emoji lists
    val limitedEmojis = remember(emojis) { emojis.take(MAX_EMOJI_COUNT) }
    val visibleEmojis = limitedEmojis.take(maxVisible)
    val hiddenCount = (limitedEmojis.size - maxVisible).coerceAtLeast(0)

    Column(modifier = modifier) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            // Leading content slot (e.g., Favorites chip)
            leadingContent?.invoke(this)

            // Clear button when a filter is active
            if (activeFilter != null) {
                item(key = "clear_all") {
                    IconButton(
                        onClick = onClearFilter,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.ui_emoji_filter_clear_all),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(
                items = visibleEmojis,
                key = { it.first },
            ) { (emoji, _) ->
                EmojiChip(
                    emojiTag = EmojiTag.fromEmoji(emoji),
                    onClick = { onEmojiSelected(emoji) },
                    isSelected = emoji == activeFilter,
                )
            }

            if (hiddenCount > 0) {
                item(key = "more_chip") {
                    MoreChip(
                        count = hiddenCount,
                        onClick = { isExpanded = true },
                    )
                }
            }
        }

        if (isExpanded) {
            EmojiGridOverlay(
                emojis = limitedEmojis,
                activeFilter = activeFilter,
                onEmojiSelected = onEmojiSelected,
                onDismiss = { isExpanded = false },
            )
        }
    }
}

@Preview(name = "EmojiFilterRail - Default", showBackground = true)
@Composable
private fun EmojiFilterRailPreview() {
    RiposteTheme {
        EmojiFilterRail(
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
                ),
            activeFilter = "😂",
            onEmojiSelected = {},
        )
    }
}

@Preview(name = "EmojiFilterRail - Few Emojis", showBackground = true)
@Composable
private fun EmojiFilterRailFewEmojisPreview() {
    RiposteTheme {
        EmojiFilterRail(
            emojis =
                listOf(
                    "😂" to 10,
                    "🔥" to 5,
                    "💀" to 3,
                ),
            activeFilter = null,
            onEmojiSelected = {},
        )
    }
}

@Preview(name = "EmojiFilterRail - None Selected", showBackground = true)
@Composable
private fun EmojiFilterRailNoneSelectedPreview() {
    RiposteTheme {
        EmojiFilterRail(
            emojis =
                listOf(
                    "😂" to 42,
                    "🔥" to 35,
                    "💀" to 28,
                    "😭" to 22,
                    "🥺" to 18,
                    "😤" to 15,
                    "🤔" to 12,
                ),
            activeFilter = null,
            onEmojiSelected = {},
        )
    }
}
