package com.adsamcik.riposte.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.ui.R
import com.adsamcik.riposte.core.ui.theme.RiposteShapes
import java.io.File

/**
 * Card component for displaying a meme in the gallery.
 */
@Composable
fun MemeCard(
    meme: Meme,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    showEmojis: Boolean = true,
) {
    val favoritedText = stringResource(R.string.ui_state_favorited)
    val notFavoritedText = stringResource(R.string.ui_state_not_favorited)
    val memeDescription =
        buildString {
            append(meme.title ?: meme.fileName)
            if (meme.emojiTags.isNotEmpty()) {
                append(", tags: ")
                append(meme.emojiTags.take(5).joinToString(", ") { it.name })
            }
            if (meme.isFavorite) {
                append(", $favoritedText")
            }
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = memeDescription
                },
        shape = RiposteShapes.MemeCard,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Image
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
            ) {
                AsyncImage(
                    model = File(meme.filePath),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clearAndSetSemantics { },
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = onFavoriteClick,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .semantics {
                                role = Role.Button
                                stateDescription = if (meme.isFavorite) favoritedText else notFavoritedText
                            },
                ) {
                    Icon(
                        imageVector =
                            if (meme.isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                        contentDescription =
                            if (meme.isFavorite) {
                                stringResource(R.string.ui_meme_card_remove_from_favorites)
                            } else {
                                stringResource(R.string.ui_meme_card_add_to_favorites)
                            },
                        tint =
                            if (meme.isFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }

            // Content
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                // Emojis
                if (showEmojis && meme.emojiTags.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(meme.emojiTags.take(5)) { emojiTag ->
                            EmojiChip(
                                emojiTag = emojiTag,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                        if (meme.emojiTags.size > 5) {
                            item {
                                Text(
                                    text = "+${meme.emojiTags.size - 5}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Title
                val title = meme.title
                if (showTitle && !title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Compact meme card for grid layouts.
 *
 * @param onClick Click handler. When null, no click modifier is applied —
 *   useful when a parent composable provides its own [combinedClickable].
 */
@Composable
fun MemeCardCompact(
    meme: Meme,
    modifier: Modifier = Modifier,
    showEmojis: Boolean = true,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val favoritedText = stringResource(R.string.ui_state_favorited)
    val memeDescription =
        buildString {
            append(meme.title ?: meme.fileName)
            if (meme.emojiTags.isNotEmpty()) {
                append(", ")
                append(meme.emojiTags.take(3).joinToString(" ") { it.emoji })
            }
            if (meme.isFavorite) {
                append(", $favoritedText")
            }
        }

    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = if (interactionSource != null) ripple() else null,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RiposteShapes.MemeCard)
                .then(clickModifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = memeDescription
                },
    ) {
        AsyncImage(
            model = File(meme.filePath),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clearAndSetSemantics { },
            contentScale = ContentScale.Crop,
        )
        MemeCardCompactOverlays(meme = meme, showEmojis = showEmojis)
    }
}

@Composable
private fun BoxScope.MemeCardCompactOverlays(
    meme: Meme,
    showEmojis: Boolean,
) {
    if (showEmojis && meme.emojiTags.isNotEmpty()) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RiposteShapes.EmojiChipDefault,
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = meme.emojiTags.take(3).joinToString("") { it.emoji },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (meme.isFavorite) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = stringResource(R.string.ui_state_favorited),
            tint = MaterialTheme.colorScheme.error,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp),
        )
    }
}
