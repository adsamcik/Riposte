package com.adsamcik.riposte.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.ui.R
import com.adsamcik.riposte.core.ui.theme.RiposteShapes
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import com.adsamcik.riposte.core.ui.theme.Spacing
import com.adsamcik.riposte.core.ui.theme.ThumbnailSizes
import java.io.File

/**
 * Quick Access section displaying frequently used memes in a horizontal row.
 *
 * This is the most prominent section in the gallery, designed for instant access
 * to the user's most-used stickers. Single tap triggers immediate sharing.
 * Hold gesture shows a circular progress indicator; completing the hold also
 * triggers sharing (useful for visual feedback before sharing).
 *
 * @param memes List of memes to display in the quick access row.
 * @param onQuickShare Callback invoked when a meme is tapped or hold completes for quick sharing.
 * @param onLongPress Deprecated: No longer used. Hold gesture now triggers [onQuickShare] with visual feedback.
 * @param onSettingsClick Callback invoked when the settings icon is clicked.
 * @param modifier Modifier for the section container.
 */
@Suppress("UNUSED_PARAMETER")
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
            modifier = Modifier.padding(vertical = Spacing.md),
        ) {
            // Header row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🔥",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.ui_quick_access_title),
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
                        contentDescription = stringResource(R.string.ui_quick_access_settings),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Meme row
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(
                    items = memes,
                    key = { it.id },
                ) { meme ->
                    QuickAccessItem(
                        meme = meme,
                        onTap = { onQuickShare(meme.id) },
                        onHoldComplete = { onQuickShare(meme.id) },
                    )
                }
            }
        }
    }
}

/**
 * Individual meme item in the Quick Access row with hold-to-share gesture.
 *
 * Single tap triggers immediate sharing. Hold gesture shows a circular progress
 * indicator that fills over [HOLD_TO_SHARE_DURATION_MS]; releasing before completion
 * cancels the action.
 *
 * @param meme The meme to display.
 * @param onTap Callback for single tap (quick share).
 * @param onHoldComplete Callback when hold gesture completes (instant share with progress).
 * @param modifier Modifier for the item.
 */
@Composable
private fun QuickAccessItem(
    meme: Meme,
    onTap: () -> Unit,
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemDescription =
        stringResource(
            R.string.ui_quick_access_item_description,
            meme.title ?: meme.fileName,
        )

    HoldToShareContainer(
        onTap = onTap,
        onHoldComplete = onHoldComplete,
        modifier =
            modifier
                .height(ThumbnailSizes.QUICK_ACCESS_HEIGHT)
                .widthIn(max = ThumbnailSizes.QUICK_ACCESS_MAX_WIDTH)
                .semantics { contentDescription = itemDescription },
    ) {
        Surface(
            shape = RiposteShapes.SettingsItem,
            tonalElevation = 2.dp,
            modifier = Modifier.matchParentSize(),
        ) {
            AsyncImage(
                model = File(meme.filePath),
                // Handled by parent semantics
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun QuickAccessSectionPreview() {
    RiposteTheme {
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
    RiposteTheme {
        QuickAccessSection(
            memes = emptyList(),
            onQuickShare = {},
            onLongPress = {},
            onSettingsClick = {},
        )
    }
}

private val sampleMemes =
    listOf(
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
