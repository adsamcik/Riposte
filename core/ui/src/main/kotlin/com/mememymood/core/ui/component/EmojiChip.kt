package com.mememymood.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.ui.theme.EmojiCardBackgrounds
import com.mememymood.core.ui.theme.MoodShapes

/**
 * Chip component for displaying an emoji tag.
 */
@Composable
fun EmojiChip(
    emojiTag: EmojiTag,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showName: Boolean = false,
    backgroundColor: Color? = null
) {
    val bgColor = backgroundColor ?: getEmojiBackgroundColor(emojiTag.emoji)

    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = MoodShapes.EmojiChip,
        color = bgColor
    ) {
        if (showName) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${emojiTag.emoji} ${emojiTag.name.replace("_", " ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emojiTag.emoji,
                    fontSize = 20.sp
                )
            }
        }
    }
}

/**
 * Large emoji display for detail views.
 */
@Composable
fun EmojiLarge(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(
                color = getEmojiBackgroundColor(emoji),
                shape = MoodShapes.EmojiChip
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = (size * 0.6f).sp
        )
    }
}

/**
 * Gets a consistent background color for an emoji based on its hash.
 */
private fun getEmojiBackgroundColor(emoji: String): Color {
    val index = emoji.hashCode().mod(EmojiCardBackgrounds.size).let {
        if (it < 0) it + EmojiCardBackgrounds.size else it
    }
    return EmojiCardBackgrounds[index]
}
