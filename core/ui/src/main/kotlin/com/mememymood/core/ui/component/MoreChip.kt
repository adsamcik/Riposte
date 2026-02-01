package com.mememymood.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mememymood.core.ui.theme.MemeMyMoodTheme

private val MoreChipShape = RoundedCornerShape(18.dp)

/**
 * Chip component that displays a count of additional items.
 *
 * Used in emoji filter rails to show how many more emojis are available
 * beyond the visible ones. Displays as "+N" format.
 *
 * @param count The number of additional items to display.
 * @param onClick Callback invoked when the chip is clicked.
 * @param modifier Modifier to be applied to the chip.
 */
@Composable
fun MoreChip(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        shape = MoreChipShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "MoreChip - Small Count", showBackground = true)
@Composable
private fun MoreChipSmallCountPreview() {
    MemeMyMoodTheme {
        MoreChip(
            count = 5,
            onClick = {},
        )
    }
}

@Preview(name = "MoreChip - Large Count", showBackground = true)
@Composable
private fun MoreChipLargeCountPreview() {
    MemeMyMoodTheme {
        MoreChip(
            count = 42,
            onClick = {},
        )
    }
}
