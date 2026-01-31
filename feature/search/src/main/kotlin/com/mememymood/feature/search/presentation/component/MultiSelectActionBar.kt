package com.mememymood.feature.search.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mememymood.feature.search.R

/**
 * Bottom action bar that appears when in multi-select mode.
 *
 * Provides actions for the current selection:
 * - Share: Share selected memes
 * - Favorite: Toggle favorite status for selected memes
 * - Delete: Delete selected memes
 *
 * Uses slide-in/slide-out animation from the bottom.
 *
 * @param visible Whether the action bar should be visible.
 * @param selectedCount The number of currently selected items.
 * @param onClose Called when the user wants to exit selection mode.
 * @param onSelectAll Called when the user wants to select all items.
 * @param onShare Called when the user wants to share selected items.
 * @param onFavorite Called when the user wants to toggle favorite on selected items.
 * @param onDelete Called when the user wants to delete selected items.
 * @param modifier Modifier to apply to this composable.
 */
@Composable
fun MultiSelectActionBar(
    visible: Boolean,
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        ),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        ),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left side: Close button and selection count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val closeDescription = stringResource(R.string.search_selection_close)
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.semantics { contentDescription = closeDescription },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(R.string.search_selection_count, selectedCount),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Right side: Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Select All button
                    val selectAllDescription = stringResource(R.string.search_selection_select_all)
                    IconButton(
                        onClick = onSelectAll,
                        modifier = Modifier.semantics { contentDescription = selectAllDescription },
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Share button
                    MultiSelectActionButton(
                        onClick = onShare,
                        icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = stringResource(R.string.search_selection_share),
                    )

                    // Favorite button
                    MultiSelectActionButton(
                        onClick = onFavorite,
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = stringResource(R.string.search_selection_favorite),
                    )

                    // Delete button
                    MultiSelectActionButton(
                        onClick = onDelete,
                        icon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = stringResource(R.string.search_selection_delete),
                        isDestructive = true,
                    )
                }
            }
        }
    }
}

/**
 * A compact action button for the multi-select action bar.
 *
 * @param onClick Called when the button is clicked.
 * @param icon The icon to display.
 * @param label The text label for the button.
 * @param modifier Modifier to apply to this composable.
 * @param isDestructive Whether this is a destructive action (uses error colors).
 */
@Composable
private fun MultiSelectActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}
