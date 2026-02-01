package com.mememymood.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mememymood.core.ui.theme.MemeMyMoodTheme

/**
 * Empty state composable for displaying when no content is available.
 *
 * Displays a centered layout with an emoji icon, title, descriptive message,
 * and an optional action button. Used for empty galleries, no search results,
 * and other empty content scenarios.
 *
 * @param icon The emoji to display as the visual indicator (e.g., "📱", "🔍")
 * @param title The main title text describing the empty state
 * @param message The descriptive message providing context or guidance
 * @param actionLabel Optional label for the action button. If null, no button is shown.
 * @param onAction Optional callback for the action button. Must be provided with [actionLabel].
 * @param modifier Modifier to be applied to the root layout
 */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.alpha(0.75f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStateNewUserPreview() {
    MemeMyMoodTheme {
        EmptyState(
            icon = "📱",
            title = "Your sticker collection is waiting!",
            message = "Import your favorite reaction images and tag them with emojis for lightning-fast searching.",
            actionLabel = "Import Stickers",
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStateNoSearchResultsPreview() {
    MemeMyMoodTheme {
        EmptyState(
            icon = "🔍",
            title = "No stickers found for \"your query\"",
            message = "Try different words or check your emoji filters.",
            actionLabel = "Clear Filters",
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStateNoEmojiTagMatchesPreview() {
    MemeMyMoodTheme {
        EmptyState(
            icon = "😶",
            title = "No 😩 stickers yet",
            message = "You can add emoji tags when viewing any sticker's details.",
            actionLabel = "Browse All",
            onAction = {},
        )
    }
}
