package com.mememymood.feature.search.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mememymood.feature.search.R

/**
 * A wrapper composable that adds selection behavior to any meme card.
 *
 * Provides:
 * - Long-press to enter selection mode
 * - Tap to toggle selection when in selection mode
 * - Checkbox overlay with animation
 * - Scale animation on selection state change
 * - Haptic feedback for interactions
 *
 * @param memeId The unique identifier of the meme.
 * @param isSelected Whether this meme is currently selected.
 * @param isSelectionMode Whether the parent is in selection mode.
 * @param onLongPress Called when the user long-presses to enter selection mode.
 * @param onToggleSelection Called when the user taps to toggle selection (only in selection mode).
 * @param onClick Called when the user taps normally (not in selection mode).
 * @param modifier Modifier to apply to this composable.
 * @param content The meme card content to wrap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableMemeCard(
    memeId: Long,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongPress: () -> Unit,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Scale animation for selection feedback
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "selectionScale",
    )

    // Overlay color animation
    val overlayColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        label = "overlayColor",
    )

    val selectedDescription = stringResource(R.string.search_selection_selected)
    val unselectedDescription = stringResource(R.string.search_selection_unselected)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .semantics {
                if (isSelectionMode) {
                    stateDescription = if (isSelected) selectedDescription else unselectedDescription
                }
            },
    ) {
        // Original content
        content()

        // Selection overlay
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor),
            )

            // Checkbox indicator
            SelectionCheckbox(
                isSelected = isSelected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}

/**
 * Animated checkbox indicator for selection state.
 *
 * Shows a filled check circle when selected, or an outlined circle when not selected.
 * Uses spring animation for smooth state transitions.
 *
 * @param isSelected Whether the item is selected.
 * @param modifier Modifier to apply to this composable.
 */
@Composable
private fun SelectionCheckbox(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val checkboxScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "checkboxScale",
    )

    val checkboxColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        },
        label = "checkboxColor",
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        },
        label = "checkboxBackground",
    )

    Box(
        modifier = modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = checkboxScale
                scaleY = checkboxScale
            }
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isSelected) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Outlined.Circle
            },
            contentDescription = null,
            tint = checkboxColor,
            modifier = Modifier.size(24.dp),
        )
    }
}
