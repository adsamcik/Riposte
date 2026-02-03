package com.mememymood.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.core.ui.theme.MemeMoodTheme
import com.mememymood.core.ui.theme.ThumbnailSizes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Duration in milliseconds for the hold-to-share gesture.
 * The progress indicator fills over this duration.
 */
private const val HOLD_TO_SHARE_DURATION_MS = 600L

/**
 * Delay before starting the hold progress animation.
 * Allows distinguishing between tap and hold gestures.
 */
private const val HOLD_START_DELAY_MS = 150L

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
    val itemDescription = "${meme.title ?: meme.fileName}, sticker. Tap to share, hold for instant share"
    val scope = rememberCoroutineScope()

    val holdProgress = remember { Animatable(0f) }
    var isHolding by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier
            .height(ThumbnailSizes.QUICK_ACCESS_HEIGHT)
            .widthIn(max = ThumbnailSizes.QUICK_ACCESS_MAX_WIDTH)
            .semantics { contentDescription = itemDescription },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(ThumbnailSizes.THUMBNAIL_CORNER_RADIUS),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            AsyncImage(
                model = File(meme.filePath),
                contentDescription = null, // Handled by parent semantics
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()

                            // Start hold detection
                            holdJob = scope.launch {
                                delay(HOLD_START_DELAY_MS)
                                isHolding = true
                                holdProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = HOLD_TO_SHARE_DURATION_MS.toInt(),
                                        easing = LinearEasing,
                                    ),
                                )
                            }

                            val up = waitForUpOrCancellation()

                            // Cancel or complete
                            holdJob?.cancel()
                            holdJob = null

                            if (up != null) {
                                up.consume()
                                if (holdProgress.value >= 1f) {
                                    // Hold completed - trigger share
                                    onHoldComplete()
                                } else if (!isHolding) {
                                    // Released before hold started - treat as tap
                                    onTap()
                                }
                                // If holding but not complete, just cancel (do nothing)
                            }

                            // Reset state
                            isHolding = false
                            scope.launch {
                                holdProgress.snapTo(0f)
                            }
                        }
                    },
            )
        }

        // Hold-to-share progress overlay
        AnimatedVisibility(
            visible = isHolding,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            HoldToShareProgress(
                progress = holdProgress.value,
                onComplete = { /* Handled in gesture detection */ },
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun QuickAccessSectionPreview() {
    MemeMoodTheme {
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
    MemeMoodTheme {
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
