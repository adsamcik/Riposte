package com.adsamcik.riposte.core.ui.component

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.core.ui.R
import com.adsamcik.riposte.core.ui.theme.RiposteTheme

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
    val description = stringResource(R.string.ui_more_chip_description, count)
    
    Surface(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
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
    RiposteTheme {
        MoreChip(
            count = 5,
            onClick = {},
        )
    }
}

@Preview(name = "MoreChip - Large Count", showBackground = true)
@Composable
private fun MoreChipLargeCountPreview() {
    RiposteTheme {
        MoreChip(
            count = 42,
            onClick = {},
        )
    }
}
