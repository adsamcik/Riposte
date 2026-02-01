package com.mememymood.feature.share.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mememymood.feature.share.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.ui.component.EmptyState
import com.mememymood.core.ui.component.LoadingScreen
import com.mememymood.core.ui.modifier.animatedPressScale
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Hoist string resources for use in LaunchedEffect
    val shareChooserTitle = stringResource(R.string.share_chooser_title)
    val savedToGalleryMessage = stringResource(R.string.share_message_saved_to_gallery)

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ShareEffect.LaunchShareIntent -> {
                    context.startActivity(android.content.Intent.createChooser(effect.intent, shareChooserTitle))
                }
                is ShareEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is ShareEffect.SavedToGallery -> {
                    snackbarHostState.showSnackbar(savedToGalleryMessage)
                }
                is ShareEffect.NavigateBack -> {
                    onNavigateBack()
                }
                is ShareEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.share_navigation_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                LoadingScreen()
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = "⚠️",
                        title = stringResource(R.string.share_error_title),
                        message = uiState.errorMessage ?: stringResource(R.string.share_error_generic),
                    )
                }
            }

            else -> {
                ShareContent(
                    uiState = uiState,
                    onIntent = viewModel::onIntent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun ShareContent(
    uiState: ShareUiState,
    onIntent: (ShareIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Preview section
        PreviewSection(
            originalBitmap = uiState.originalPreviewBitmap,
            processedBitmap = uiState.processedPreviewBitmap,
            isProcessing = uiState.isProcessing,
        )

        Spacer(Modifier.height(16.dp))

        // File size info
        FileSizeInfo(
            originalSize = uiState.formattedOriginalSize,
            estimatedSize = uiState.formattedEstimatedSize,
            compressionRatio = uiState.compressionRatio,
        )

        Spacer(Modifier.height(24.dp))

        // Format selector
        FormatSelector(
            selectedFormat = uiState.config.format,
            onFormatChange = { onIntent(ShareIntent.SetFormat(it)) },
        )

        Spacer(Modifier.height(16.dp))

        // Quality slider
        QualitySlider(
            quality = uiState.config.quality,
            onQualityChange = { onIntent(ShareIntent.SetQuality(it)) },
        )

        Spacer(Modifier.height(16.dp))

        // Size presets
        SizePresets(
            selectedDimension = uiState.config.maxWidth ?: 1080,
            onDimensionChange = { onIntent(ShareIntent.SetMaxDimension(it)) },
        )

        Spacer(Modifier.height(16.dp))

        // Toggles
        SettingToggle(
            title = stringResource(R.string.share_setting_keep_metadata_title),
            description = stringResource(R.string.share_setting_keep_metadata_description),
            checked = !uiState.config.stripMetadata,
            onCheckedChange = { onIntent(ShareIntent.SetStripMetadata(!it)) },
        )

        Spacer(Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = { onIntent(ShareIntent.SaveToGallery) },
                enabled = !uiState.isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .animatedPressScale(),
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_button_save))
            }

            Button(
                onClick = { onIntent(ShareIntent.Share) },
                enabled = !uiState.isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .animatedPressScale(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_button_share))
            }
        }
    }
}

@Composable
private fun PreviewSection(
    originalBitmap: Bitmap?,
    processedBitmap: Bitmap?,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Original preview
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.share_preview_original),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    originalBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.share_content_description_original_image),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Processed preview
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.share_preview_processed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .then(
                            if (isProcessing) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp),
                                )
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        processedBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.share_content_description_processed_image),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSizeInfo(
    originalSize: String,
    estimatedSize: String,
    compressionRatio: Float,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.share_size_estimated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = estimatedSize,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.share_size_original, originalSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                if (compressionRatio > 0 && compressionRatio < 1) {
                    Text(
                        text = stringResource(R.string.share_size_smaller, ((1 - compressionRatio) * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatSelector(
    selectedFormat: ImageFormat,
    onFormatChange: (ImageFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.share_format_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val formats = listOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP)
            formats.forEachIndexed { index, format ->
                SegmentedButton(
                    selected = selectedFormat == format,
                    onClick = { onFormatChange(format) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                ) {
                    Text(format.name)
                }
            }
        }
    }
}

@Composable
private fun QualitySlider(
    quality: Int,
    onQualityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.share_quality_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.share_quality_percentage, quality),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = quality.toFloat(),
            onValueChange = { onQualityChange(it.toInt()) },
            valueRange = 10f..100f,
            steps = 8,
        )
    }
}

@Composable
private fun SizePresets(
    selectedDimension: Int,
    onDimensionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.share_size_max_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val presets = listOf(
                480 to stringResource(R.string.share_size_preset_small),
                720 to stringResource(R.string.share_size_preset_medium),
                1080 to stringResource(R.string.share_size_preset_large),
                2048 to stringResource(R.string.share_size_preset_original),
            )
            presets.forEach { (dimension, label) ->
                FilterChip(
                    selected = selectedDimension == dimension,
                    onClick = { onDimensionChange(dimension) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateText = if (checked) "On" else "Off"
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = stateText
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
