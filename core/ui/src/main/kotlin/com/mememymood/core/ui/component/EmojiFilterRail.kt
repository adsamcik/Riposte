package com.mememymood.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.ui.theme.MemeMyMoodTheme

/**
 * A horizontal rail displaying emoji filter chips with overflow support.
 *
 * Shows the first [maxVisible] emojis as chips, with a "more" chip if additional
 * emojis exist. Tapping the more chip expands to show [EmojiGridOverlay].
 *
 * @param emojis List of emoji-count pairs, sorted by frequency.
 * @param activeFilters Set of currently selected emoji filters.
 * @param onEmojiToggle Callback when an emoji filter is toggled.
 * @param maxVisible Maximum number of emojis to show before overflow.
 * @param modifier Modifier to be applied to the component.
 */
@Composable
fun EmojiFilterRail(
    emojis: List<Pair<String, Int>>,
    activeFilters: Set<String>,
    onEmojiToggle: (String) -> Unit,
    maxVisible: Int = 7,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    val visibleEmojis = emojis.take(maxVisible)
    val hiddenCount = (emojis.size - maxVisible).coerceAtLeast(0)

    Column(modifier = modifier) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(
                items = visibleEmojis,
                key = { it.first },
            ) { (emoji, _) ->
                EmojiChip(
                    emojiTag = EmojiTag.fromEmoji(emoji),
                    onClick = { onEmojiToggle(emoji) },
                    isSelected = emoji in activeFilters,
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
                emojis = emojis,
                activeFilters = activeFilters,
                onEmojiToggle = onEmojiToggle,
                onDismiss = { isExpanded = false },
            )
        }
    }
}

@Preview(name = "EmojiFilterRail - Default", showBackground = true)
@Composable
private fun EmojiFilterRailPreview() {
    MemeMyMoodTheme {
        EmojiFilterRail(
            emojis = listOf(
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
            activeFilters = setOf("😂", "🔥"),
            onEmojiToggle = {},
        )
    }
}

@Preview(name = "EmojiFilterRail - Few Emojis", showBackground = true)
@Composable
private fun EmojiFilterRailFewEmojisPreview() {
    MemeMyMoodTheme {
        EmojiFilterRail(
            emojis = listOf(
                "😂" to 10,
                "🔥" to 5,
                "💀" to 3,
            ),
            activeFilters = emptySet(),
            onEmojiToggle = {},
        )
    }
}

@Preview(name = "EmojiFilterRail - None Selected", showBackground = true)
@Composable
private fun EmojiFilterRailNoneSelectedPreview() {
    MemeMyMoodTheme {
        EmojiFilterRail(
            emojis = listOf(
                "😂" to 42,
                "🔥" to 35,
                "💀" to 28,
                "😭" to 22,
                "🥺" to 18,
                "😤" to 15,
                "🤔" to 12,
            ),
            activeFilters = emptySet(),
            onEmojiToggle = {},
        )
    }
}
