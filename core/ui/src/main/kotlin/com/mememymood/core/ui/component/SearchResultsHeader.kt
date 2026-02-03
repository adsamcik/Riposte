package com.mememymood.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mememymood.core.ui.theme.MemeMoodTheme

/**
 * Header component for search results display.
 *
 * Shows the result count and optionally the search query.
 * Per VISUAL_DESIGN_SPEC.md Section 5, provides search context
 * above the results grid.
 *
 * @param query The search query text. If empty, only result count is shown.
 * @param resultCount The number of results found.
 * @param modifier Modifier to be applied to the component.
 */
@Composable
fun SearchResultsHeader(
    query: String,
    resultCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "$resultCount results",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (query.isNotEmpty()) {
            Text(
                text = "for \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchResultsHeaderPreview() {
    MemeMoodTheme {
        SearchResultsHeader(
            query = "funny cat",
            resultCount = 42,
        )
    }
}

@Preview(showBackground = true, name = "Empty Query")
@Composable
private fun SearchResultsHeaderEmptyQueryPreview() {
    MemeMoodTheme {
        SearchResultsHeader(
            query = "",
            resultCount = 100,
        )
    }
}

@Preview(showBackground = true, name = "Zero Results")
@Composable
private fun SearchResultsHeaderZeroResultsPreview() {
    MemeMoodTheme {
        SearchResultsHeader(
            query = "nonexistent",
            resultCount = 0,
        )
    }
}
