package com.adsamcik.riposte.feature.settings.presentation.duplicatedetection

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.domain.model.DuplicateGroup
import com.adsamcik.riposte.feature.settings.domain.model.ScanProgress
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import kotlin.math.roundToInt

/**
 * Stateful duplicate detection screen with ViewModel.
 */
@Composable
fun DuplicateDetectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: DuplicateDetectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is DuplicateDetectionEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    DuplicateDetectionScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless duplicate detection screen for testing and previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateDetectionScreen(
    uiState: DuplicateDetectionUiState,
    onIntent: (DuplicateDetectionIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.duplicate_detection_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Sensitivity slider
            item(key = "sensitivity") {
                SensitivitySection(
                    sensitivity = uiState.sensitivity,
                    onSensitivityChange = { onIntent(DuplicateDetectionIntent.SetSensitivity(it)) },
                    enabled = !uiState.isScanning,
                )
            }

            // Scan button
            item(key = "scan_button") {
                ScanSection(
                    isScanning = uiState.isScanning,
                    scanProgress = uiState.scanProgress,
                    onStartScan = { onIntent(DuplicateDetectionIntent.StartScan) },
                )
            }

            // Results header with bulk actions
            if (uiState.duplicateGroups.isNotEmpty()) {
                item(key = "results_header") {
                    ResultsHeader(
                        count = uiState.duplicateGroups.size,
                        onMergeAll = { onIntent(DuplicateDetectionIntent.MergeAll) },
                        onDismissAll = { onIntent(DuplicateDetectionIntent.DismissAll) },
                        enabled = !uiState.isScanning,
                    )
                }
            }

            // Duplicate groups
            items(
                items = uiState.duplicateGroups,
                key = { it.duplicateId },
            ) { group ->
                DuplicateGroupCard(
                    group = group,
                    onMerge = { onIntent(DuplicateDetectionIntent.MergeDuplicate(group.duplicateId)) },
                    onDismiss = { onIntent(DuplicateDetectionIntent.DismissDuplicate(group.duplicateId)) },
                )
            }

            // Empty state
            if (uiState.hasScanned && uiState.duplicateGroups.isEmpty() && !uiState.isScanning) {
                item(key = "empty") {
                    EmptyState()
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SensitivitySection(
    sensitivity: Int,
    onSensitivityChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.duplicate_detection_sensitivity),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = sensitivity.toFloat(),
                onValueChange = { onSensitivityChange(it.roundToInt()) },
                valueRange = DuplicateDetectionUiState.MIN_SENSITIVITY.toFloat()..
                    DuplicateDetectionUiState.MAX_SENSITIVITY.toFloat(),
                steps = DuplicateDetectionUiState.MAX_SENSITIVITY - 1,
                enabled = enabled,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.duplicate_detection_sensitivity_exact),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.duplicate_detection_sensitivity_similar),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.duplicate_detection_sensitivity_loose),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScanSection(
    isScanning: Boolean,
    scanProgress: ScanProgress?,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (isScanning && scanProgress != null) {
            val total = scanProgress.totalToHash.coerceAtLeast(1)
            val progress = scanProgress.hashedCount.toFloat() / total

            Text(
                text = if (scanProgress.hashedCount < scanProgress.totalToHash) {
                    stringResource(
                        R.string.duplicate_detection_hashing_progress,
                        scanProgress.hashedCount,
                        scanProgress.totalToHash,
                    )
                } else {
                    stringResource(R.string.duplicate_detection_comparing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Button(
                onClick = onStartScan,
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.duplicate_detection_scan_button))
            }
        }
    }
}

@Composable
private fun ResultsHeader(
    count: Int,
    onMergeAll: () -> Unit,
    onDismissAll: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.duplicate_detection_found, count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = onMergeAll,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.duplicate_detection_merge_all))
            }
            OutlinedButton(
                onClick = onDismissAll,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.duplicate_detection_dismiss_all))
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Detection method badge
            Text(
                text = if (group.detectionMethod == "exact") {
                    stringResource(R.string.duplicate_detection_exact_match)
                } else {
                    stringResource(R.string.duplicate_detection_perceptual_match, group.hammingDistance)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (group.detectionMethod == "exact") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Side-by-side thumbnails
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MemeThumb(meme = group.meme1, modifier = Modifier.weight(1f))
                MemeThumb(meme = group.meme2, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onMerge,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.duplicate_detection_merge),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.duplicate_detection_dismiss),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemeThumb(
    meme: MemeEntity,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = File(meme.filePath),
            contentDescription = meme.title ?: meme.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.duplicate_detection_resolution, meme.width, meme.height),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = meme.fileName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.duplicate_detection_no_duplicates),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

// region Previews

@Preview(name = "Empty", showBackground = true)
@Preview(name = "Empty Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DuplicateDetectionEmptyPreview() {
    RiposteTheme {
        DuplicateDetectionScreen(
            uiState = DuplicateDetectionUiState(hasScanned = true),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Scanning", showBackground = true)
@Composable
private fun DuplicateDetectionScanningPreview() {
    RiposteTheme {
        DuplicateDetectionScreen(
            uiState = DuplicateDetectionUiState(
                isScanning = true,
                scanProgress = ScanProgress(hashedCount = 15, totalToHash = 33, duplicatesFound = 0),
            ),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

// endregion
