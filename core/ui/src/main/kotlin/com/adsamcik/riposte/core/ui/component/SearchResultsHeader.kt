package com.adsamcik.riposte.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.riposte.core.ui.R
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import java.util.Locale

private const val MILLIS_PER_SECOND = 1000.0

/**
 * Header component for search results display.
 *
 * Shows the result count, optionally the search query, and search duration.
 * Per VISUAL_DESIGN_SPEC.md Section 5, provides search context
 * above the results grid.
 *
 * @param query The search query text. If empty, only result count is shown.
 * @param resultCount The number of results found.
 * @param durationMs Search duration in milliseconds. If 0, not displayed.
 * @param modifier Modifier to be applied to the component.
 */
@Suppress("detekt:UnusedParameter")
@Composable
fun SearchResultsHeader(
    query: String,
    resultCount: Int,
    durationMs: Long = 0L,
    isTextOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val countText = pluralStringResource(R.plurals.ui_search_results_count, resultCount, resultCount)
        val headerText = if (durationMs > 0) {
            val seconds = durationMs / MILLIS_PER_SECOND
            stringResource(R.string.ui_search_results_duration, countText, String.format(Locale.US, "%.1f", seconds))
        } else {
            countText
        }
        val displayText = if (isTextOnly) {
            stringResource(R.string.ui_search_text_only_suffix, headerText)
        } else {
            headerText
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchResultsHeaderPreview() {
    RiposteTheme {
        SearchResultsHeader(
            query = "funny cat",
            resultCount = 42,
            durationMs = 310L,
        )
    }
}

@Preview(showBackground = true, name = "Empty Query")
@Composable
private fun SearchResultsHeaderEmptyQueryPreview() {
    RiposteTheme {
        SearchResultsHeader(
            query = "",
            resultCount = 100,
        )
    }
}

@Preview(showBackground = true, name = "Zero Results")
@Composable
private fun SearchResultsHeaderZeroResultsPreview() {
    RiposteTheme {
        SearchResultsHeader(
            query = "nonexistent",
            resultCount = 0,
        )
    }
}

@Preview(showBackground = true, name = "Text Only")
@Composable
private fun SearchResultsHeaderTextOnlyPreview() {
    RiposteTheme {
        SearchResultsHeader(
            query = "funny cat",
            resultCount = 12,
            durationMs = 150L,
            isTextOnly = true,
        )
    }
}
