package com.mememymood.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mememymood.core.ui.R

/**
 * Full-screen loading indicator.
 */
@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            if (message != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Error state view.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    EmptyState(
        icon = "😵",
        title = stringResource(R.string.ui_loading_error_title),
        message = message,
        actionLabel = if (onRetry != null) stringResource(R.string.ui_loading_error_retry) else null,
        onAction = onRetry,
        modifier = modifier,
    )
}

/**
 * No search results view.
 */
@Composable
fun NoSearchResults(
    query: String,
    modifier: Modifier = Modifier,
    onClearSearch: (() -> Unit)? = null
) {
    EmptyState(
        icon = "🔍",
        title = stringResource(R.string.ui_loading_no_results_title),
        message = stringResource(R.string.ui_loading_no_results_message, query),
        actionLabel = if (onClearSearch != null) stringResource(R.string.ui_loading_no_results_clear) else null,
        onAction = onClearSearch,
        modifier = modifier,
    )
}

/**
 * Shimmer loading placeholder for content.
 */
@Composable
fun ShimmerPlaceholder(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.3f else 1f,
        label = "shimmer_alpha"
    )

    Box(modifier = modifier.alpha(alpha)) {
        content()
    }
}
