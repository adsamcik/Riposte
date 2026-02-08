package com.adsamcik.riposte.feature.share.presentation

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.adsamcik.riposte.feature.share.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.ui.component.EmptyState
import com.adsamcik.riposte.core.ui.component.LoadingScreen
import com.adsamcik.riposte.core.ui.modifier.animatedPressScale
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

/**
 * Stateless ShareScreen for previews and testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareScreen(
    uiState: ShareUiState,
    onIntent: (ShareIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
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
        contentWindowInsets = WindowInsets.safeDrawing,
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
                    onIntent = onIntent,
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
    var settingsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Single centered preview
        PreviewSection(
            processedBitmap = uiState.processedPreviewBitmap,
            originalBitmap = uiState.originalPreviewBitmap,
            isProcessing = uiState.isProcessing,
        )

        Spacer(Modifier.height(12.dp))

        // Simplified file size banner
        FileSizeBanner(estimatedFileSize = uiState.estimatedFileSize)

        Spacer(Modifier.height(16.dp))

        // Expandable settings header
        SettingsHeader(
            expanded = settingsExpanded,
            onToggle = { settingsExpanded = !settingsExpanded },
        )

        // Collapsible settings section
        AnimatedVisibility(
            visible = settingsExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))

                FormatSelector(
                    selectedFormat = uiState.config.format,
                    onFormatChange = { onIntent(ShareIntent.SetFormat(it)) },
                )

                AnimatedVisibility(
                    visible = uiState.config.format.isLossy,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                    modifier = Modifier.testTag("quality_slider_section"),
                ) {
                    Column {
                        Spacer(Modifier.height(16.dp))

                        QualitySlider(
                            quality = uiState.config.quality,
                            onQualityChange = { onIntent(ShareIntent.SetQuality(it)) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                SizePresets(
                    selectedDimension = uiState.config.maxWidth ?: 1080,
                    onDimensionChange = { onIntent(ShareIntent.SetMaxDimension(it)) },
                )

                Spacer(Modifier.height(16.dp))

                SettingToggle(
                    title = stringResource(R.string.share_setting_keep_metadata_title),
                    description = stringResource(R.string.share_setting_keep_metadata_description),
                    checked = !uiState.config.stripMetadata,
                    onCheckedChange = { onIntent(ShareIntent.SetStripMetadata(!it)) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Hero share button
        val shareInteractionSource = remember { MutableInteractionSource() }
        Button(
            onClick = { onIntent(ShareIntent.Share) },
            enabled = !uiState.isProcessing,
            interactionSource = shareInteractionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .animatedPressScale(shareInteractionSource),
        ) {
            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_button_share))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.share_button_share),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Demoted save link
        TextButton(
            onClick = { onIntent(ShareIntent.SaveToGallery) },
            enabled = !uiState.isProcessing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.share_button_save_to_gallery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewSection(
    processedBitmap: Bitmap?,
    originalBitmap: Bitmap?,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val maxPreviewHeight = screenHeightDp * 0.4f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxPreviewHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val displayBitmap = processedBitmap ?: originalBitmap
        if (isProcessing) {
            // Show original while processing, with a loading indicator overlay
            displayBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_preview_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        } else {
            displayBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_preview_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun FileSizeBanner(
    estimatedFileSize: Long,
    modifier: Modifier = Modifier,
) {
    val estimatedKb = estimatedFileSize / 1024f

    val (message, messageColor) = when {
        estimatedFileSize <= 0 -> null to MaterialTheme.colorScheme.onSurfaceVariant
        estimatedKb < 100 -> stringResource(R.string.share_size_quick_to_send) to
                MaterialTheme.colorScheme.tertiary
        estimatedKb <= 500 -> stringResource(R.string.share_size_good_quality) to
                MaterialTheme.colorScheme.onSurfaceVariant
        else -> stringResource(R.string.share_size_large_file) to
                MaterialTheme.colorScheme.error
    }

    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = messageColor,
            modifier = modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SettingsHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleDescription = if (expanded) {
        stringResource(R.string.share_settings_collapse)
    } else {
        stringResource(R.string.share_settings_expand)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .semantics { contentDescription = toggleDescription },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.share_settings_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            val formats = listOf(
                ImageFormat.JPEG to stringResource(R.string.share_format_jpeg_description),
                ImageFormat.PNG to stringResource(R.string.share_format_png_description),
                ImageFormat.WEBP to stringResource(R.string.share_format_webp_description),
            )
            formats.forEachIndexed { index, (format, label) ->
                SegmentedButton(
                    selected = selectedFormat == format,
                    onClick = { onFormatChange(format) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                ) {
                    Text(label)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.share_quality_smaller_file),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.share_quality_better_quality),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    val stateText = if (checked) {
        stringResource(com.adsamcik.riposte.core.ui.R.string.ui_toggle_state_on)
    } else {
        stringResource(com.adsamcik.riposte.core.ui.R.string.ui_toggle_state_off)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
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

// region Previews

private val sharePreviewMeme = com.adsamcik.riposte.core.model.Meme(
    id = 1L,
    filePath = "/preview/meme.jpg",
    fileName = "meme.jpg",
    mimeType = "image/jpeg",
    width = 1024,
    height = 768,
    fileSizeBytes = 256_000L,
    importedAt = System.currentTimeMillis(),
    emojiTags = listOf(com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("😂")),
    title = "Sample meme",
)

@Preview(name = "Loading", showBackground = true)
@Composable
private fun ShareScreenLoadingPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        ShareScreen(
            uiState = ShareUiState(isLoading = true),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Content", showBackground = true)
@Preview(name = "Content Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ShareScreenContentPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        ShareScreen(
            uiState = ShareUiState(
                meme = sharePreviewMeme,
                isLoading = false,
                originalFileSize = 256_000L,
                estimatedFileSize = 68_000L,
            ),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Content - Large File", showBackground = true)
@Composable
private fun ShareScreenLargeFilePreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        ShareScreen(
            uiState = ShareUiState(
                meme = sharePreviewMeme,
                isLoading = false,
                originalFileSize = 2_048_000L,
                estimatedFileSize = 1_024_000L,
            ),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun ShareScreenErrorPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        ShareScreen(
            uiState = ShareUiState(
                isLoading = false,
                errorMessage = stringResource(R.string.share_error_load_failed),
            ),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

// endregion
