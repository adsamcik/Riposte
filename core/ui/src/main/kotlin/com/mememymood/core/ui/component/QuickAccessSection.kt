package com.mememymood.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.core.ui.theme.MemeMyMoodTheme
import com.mememymood.core.ui.theme.ThumbnailSizes
import java.io.File

/**
 * Quick Access section displaying frequently used memes in a horizontal row.
 *
 * This is the most prominent section in the gallery, designed for instant access
 * to the user's most-used stickers. Single tap triggers immediate sharing.
 *
 * @param memes List of memes to display in the quick access row.
 * @param onQuickShare Callback invoked when a meme is tapped for quick sharing.
 * @param onLongPress Callback invoked when a meme is long-pressed for additional options.
 * @param onSettingsClick Callback invoked when the settings icon is clicked.
 * @param modifier Modifier for the section container.
 */
@Composable
fun QuickAccessSection(
    memes: List<Meme>,
    onQuickShare: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🔥",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quick Access",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Quick Access settings",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Meme row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = memes,
                    key = { it.id },
                ) { meme ->
                    QuickAccessItem(
                        meme = meme,
                        onClick = { onQuickShare(meme.id) },
                        onLongClick = { onLongPress(meme.id) },
                    )
                }
            }
        }
    }
}

/**
 * Individual meme item in the Quick Access row.
 *
 * @param meme The meme to display.
 * @param onClick Callback for single tap (quick share).
 * @param onLongClick Callback for long press (additional options).
 * @param modifier Modifier for the item.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAccessItem(
    meme: Meme,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemDescription = "${meme.title ?: meme.fileName}, sticker. Double-tap to share, hold for options"

    Surface(
        modifier = modifier
            .height(ThumbnailSizes.QUICK_ACCESS_HEIGHT)
            .widthIn(max = ThumbnailSizes.QUICK_ACCESS_MAX_WIDTH)
            .semantics { contentDescription = itemDescription },
        shape = RoundedCornerShape(ThumbnailSizes.THUMBNAIL_CORNER_RADIUS),
        shadowElevation = 2.dp,
    ) {
        AsyncImage(
            model = File(meme.filePath),
            contentDescription = null, // Handled by parent semantics
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        )
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun QuickAccessSectionPreview() {
    MemeMyMoodTheme {
        QuickAccessSection(
            memes = sampleMemes,
            onQuickShare = {},
            onLongPress = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun QuickAccessSectionEmptyPreview() {
    MemeMyMoodTheme {
        QuickAccessSection(
            memes = emptyList(),
            onQuickShare = {},
            onLongPress = {},
            onSettingsClick = {},
        )
    }
}

private val sampleMemes = listOf(
    Meme(
        id = 1L,
        filePath = "/storage/emulated/0/Memes/meme1.jpg",
        fileName = "meme1.jpg",
        mimeType = "image/jpeg",
        width = 500,
        height = 500,
        fileSizeBytes = 50_000L,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag(emoji = "😂", name = "joy")),
        title = "Funny reaction",
        useCount = 42,
    ),
    Meme(
        id = 2L,
        filePath = "/storage/emulated/0/Memes/meme2.png",
        fileName = "meme2.png",
        mimeType = "image/png",
        width = 400,
        height = 600,
        fileSizeBytes = 75_000L,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag(emoji = "🔥", name = "fire")),
        title = "Hot take",
        useCount = 28,
    ),
    Meme(
        id = 3L,
        filePath = "/storage/emulated/0/Memes/meme3.webp",
        fileName = "meme3.webp",
        mimeType = "image/webp",
        width = 600,
        height = 400,
        fileSizeBytes = 60_000L,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag(emoji = "💀", name = "skull")),
        title = "Dead",
        useCount = 15,
    ),
    Meme(
        id = 4L,
        filePath = "/storage/emulated/0/Memes/meme4.jpg",
        fileName = "meme4.jpg",
        mimeType = "image/jpeg",
        width = 512,
        height = 512,
        fileSizeBytes = 45_000L,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag(emoji = "😭", name = "crying")),
        title = "Crying laughing",
        useCount = 33,
    ),
    Meme(
        id = 5L,
        filePath = "/storage/emulated/0/Memes/meme5.gif",
        fileName = "meme5.gif",
        mimeType = "image/gif",
        width = 300,
        height = 300,
        fileSizeBytes = 120_000L,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag(emoji = "🥺", name = "pleading")),
        title = "Please",
        useCount = 21,
    ),
)

// endregion
