package com.mememymood.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mememymood.core.ui.theme.MemeMyMoodTheme

/**
 * Section header component for organizing content into visual groups.
 *
 * Displays a title with an optional leading icon (emoji) and optional trailing action.
 * Used throughout the app to separate sections like Quick Access, Recent, Pinned, etc.
 *
 * @param title The section title text to display.
 * @param icon Optional emoji or icon character to display before the title.
 * @param accentColor The color for the title text. Defaults to [MaterialTheme.colorScheme.onSurfaceVariant].
 * @param trailingAction Optional composable content to display on the right side (e.g., action button).
 * @param modifier Modifier to be applied to the component.
 */
@Composable
fun SectionHeader(
    title: String,
    icon: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = accentColor,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.weight(1f))
        trailingAction?.invoke()
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    MemeMyMoodTheme {
        SectionHeader(
            title = "Quick Access",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderWithIconPreview() {
    MemeMyMoodTheme {
        SectionHeader(
            title = "Quick Access",
            icon = "🔥",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderWithTrailingActionPreview() {
    MemeMyMoodTheme {
        SectionHeader(
            title = "Recent",
            icon = "🕐",
            trailingAction = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "See all",
                    )
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderPinnedPreview() {
    MemeMyMoodTheme {
        SectionHeader(
            title = "Pinned",
            icon = "📌",
            accentColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderNewImportsPreview() {
    MemeMyMoodTheme {
        SectionHeader(
            title = "New Imports",
            icon = "✨",
            accentColor = MaterialTheme.colorScheme.tertiary,
        )
    }
}
