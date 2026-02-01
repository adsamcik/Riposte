package com.mememymood.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mememymood.core.ui.theme.MemeMyMoodTheme

/**
 * Circular progress indicator for hold-to-share gesture feedback.
 *
 * Per VISUAL_DESIGN_SPEC.md Section 9, shows visual progress
 * during a hold gesture. The indicator fills linearly over
 * the specified duration.
 *
 * @param progress Current progress value from 0f to 1f.
 * @param onComplete Callback invoked when progress reaches 1f (called exactly once).
 * @param modifier Modifier to be applied to the component.
 */
@Composable
fun HoldToShareProgress(
    progress: Float,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnComplete by rememberUpdatedState(onComplete)
    var hasCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (progress >= 1f && !hasCompleted) {
            hasCompleted = true
            currentOnComplete()
        } else if (progress < 1f) {
            hasCompleted = false
        }
    }

    CircularProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.size(48.dp),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Preview(showBackground = true)
@Composable
private fun HoldToShareProgressPreview() {
    MemeMyMoodTheme {
        HoldToShareProgress(
            progress = 0.6f,
            onComplete = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty Progress")
@Composable
private fun HoldToShareProgressEmptyPreview() {
    MemeMyMoodTheme {
        HoldToShareProgress(
            progress = 0f,
            onComplete = {},
        )
    }
}

@Preview(showBackground = true, name = "Full Progress")
@Composable
private fun HoldToShareProgressFullPreview() {
    MemeMyMoodTheme {
        HoldToShareProgress(
            progress = 1f,
            onComplete = {},
        )
    }
}
