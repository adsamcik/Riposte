package com.adsamcik.riposte.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.ui.R
import com.adsamcik.riposte.core.ui.theme.EmojiCardBackgrounds
import com.adsamcik.riposte.core.ui.theme.RiposteMotionScheme
import com.adsamcik.riposte.core.ui.theme.RiposteShapes
import com.adsamcik.riposte.core.ui.theme.RiposteTheme

private val EmojiChipShape = RiposteShapes.EmojiChipDefault

/**
 * Chip component for displaying an emoji tag.
 *
 * @param emojiTag The emoji tag to display.
 * @param modifier Modifier to be applied to the chip.
 * @param onClick Optional click handler. When provided, chip becomes clickable with scale animation.
 * @param isSelected Whether the chip is in selected state.
 * @param showName Whether to show the emoji name alongside the emoji.
 * @param backgroundColor Optional custom background color. When null, uses selection-based colors.
 */
@Composable
fun EmojiChip(
    emojiTag: EmojiTag,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    showName: Boolean = false,
    backgroundColor: Color? = null,
) {
    val bgColor =
        backgroundColor ?: if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue =
            if (isPressed) {
                0.92f
            } else if (isSelected) {
                1.05f
            } else {
                1.0f
            },
        animationSpec = RiposteMotionScheme.FastSpatial,
        label = "EmojiChipScale",
    )

    val chipDescription =
        if (isSelected) {
            stringResource(R.string.ui_emoji_chip_filter_active, emojiTag.emoji)
        } else {
            stringResource(R.string.ui_emoji_chip_filter_inactive, emojiTag.emoji)
        }

    val borderModifier =
        if (isSelected) {
            Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RiposteShapes.EmojiChipSelected,
            )
        } else {
            Modifier
        }

    Surface(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .then(borderModifier)
                .semantics {
                    contentDescription = chipDescription
                }
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .sizeIn(minWidth = 36.dp)
                .scale(scale),
        shape = if (isSelected) RiposteShapes.EmojiChipSelected else EmojiChipShape,
        color = bgColor,
    ) {
        if (showName) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${emojiTag.emoji} ${emojiTag.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emojiTag.emoji,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Preview(name = "EmojiChip - Unselected", showBackground = true)
@Composable
private fun EmojiChipUnselectedPreview() {
    RiposteTheme {
        EmojiChip(
            emojiTag = EmojiTag(emoji = "😂", name = "joy"),
            onClick = {},
            isSelected = false,
        )
    }
}

@Preview(name = "EmojiChip - Selected", showBackground = true)
@Composable
private fun EmojiChipSelectedPreview() {
    RiposteTheme {
        EmojiChip(
            emojiTag = EmojiTag(emoji = "🔥", name = "fire"),
            onClick = {},
            isSelected = true,
        )
    }
}

@Preview(name = "EmojiChip - With Name", showBackground = true)
@Composable
private fun EmojiChipWithNamePreview() {
    RiposteTheme {
        EmojiChip(
            emojiTag = EmojiTag(emoji = "💀", name = "skull"),
            onClick = {},
            isSelected = true,
            showName = true,
        )
    }
}

/**
 * Large emoji display for detail views.
 */
@Composable
fun EmojiLarge(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Int = 48,
) {
    Box(
        modifier =
            modifier
                .size(size.dp)
                .background(
                    color = getEmojiBackgroundColor(emoji),
                    shape = RiposteShapes.EmojiChipDefault,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = (size * 0.6f).sp,
        )
    }
}

/**
 * Gets a consistent background color for an emoji based on its hash.
 */
private fun getEmojiBackgroundColor(emoji: String): Color {
    val index =
        emoji.hashCode().mod(EmojiCardBackgrounds.size).let {
            if (it < 0) it + EmojiCardBackgrounds.size else it
        }
    return EmojiCardBackgrounds[index]
}
