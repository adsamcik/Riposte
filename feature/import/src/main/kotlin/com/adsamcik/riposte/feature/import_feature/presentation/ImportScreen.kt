package com.adsamcik.riposte.feature.import_feature.presentation

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.ui.component.EmojiChip
import com.adsamcik.riposte.feature.import_feature.R
import kotlinx.coroutines.flow.collectLatest

// TODO: Replace with emojis from the user's meme collection (query from DB) plus freeform input.
private val CommonEmojis =
    listOf(
        EmojiTag("😂", "😂"),
        EmojiTag("❤️", "❤️"),
        EmojiTag("🔥", "🔥"),
        EmojiTag("😍", "😍"),
        EmojiTag("🤣", "🤣"),
        EmojiTag("😊", "😊"),
        EmojiTag("🙏", "🙏"),
        EmojiTag("😭", "😭"),
        EmojiTag("😘", "😘"),
        EmojiTag("💯", "💯"),
        EmojiTag("🤔", "🤔"),
        EmojiTag("👀", "👀"),
        EmojiTag("💀", "💀"),
        EmojiTag("🎉", "🎉"),
        EmojiTag("✨", "✨"),
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val multiplePhotoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
            onResult = { uris ->
                viewModel.onIntent(ImportIntent.ImagesSelected(uris))
            },
        )

    val zipPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                uri?.let { viewModel.onIntent(ImportIntent.ZipSelected(it)) }
            },
        )

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ImportEffect.OpenImagePicker -> {
                    multiplePhotoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                is ImportEffect.OpenFilePicker -> {
                    zipPickerLauncher.launch(arrayOf("application/zip"))
                }
                is ImportEffect.ImportComplete -> {
                    // No navigation here — the ImportResultSummary UI handles it
                }
                is ImportEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is ImportEffect.NavigateToGallery -> {
                    onNavigateBack()
                }
                is ImportEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    val bottomSheetState =
        rememberStandardBottomSheetState(
            initialValue = if (uiState.editingImage != null) SheetValue.Expanded else SheetValue.Hidden,
            skipHiddenState = false,
        )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    // Update sheet state when image is selected
    LaunchedEffect(uiState.editingImage) {
        if (uiState.editingImage != null) {
            bottomSheetState.expand()
        } else {
            bottomSheetState.hide()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetContent = {
            uiState.editingImage?.let { importImage ->
                EditImageSheet(
                    imageUri = importImage.uri,
                    currentEmojis = importImage.emojis,
                    suggestedEmojis = importImage.suggestedEmojis,
                    title = importImage.title ?: "",
                    description = importImage.description ?: "",
                    onEmojiToggle = { emoji ->
                        if (importImage.emojis.contains(emoji)) {
                            viewModel.onIntent(ImportIntent.RemoveEmoji(emoji))
                        } else {
                            viewModel.onIntent(ImportIntent.AddEmoji(emoji))
                        }
                    },
                    onTitleChange = { title ->
                        viewModel.onIntent(ImportIntent.UpdateTitle(title))
                    },
                    onDescriptionChange = { description ->
                        viewModel.onIntent(ImportIntent.UpdateDescription(description))
                    },
                    onApplySuggestions = {
                        viewModel.onIntent(ImportIntent.ApplySuggestedEmojis)
                    },
                    onDone = {
                        viewModel.onIntent(ImportIntent.CloseEditor)
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.import_content_description_back),
                        )
                    }
                },
                actions = {
                    if (uiState.selectedImages.isNotEmpty()) {
                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.import_selected_count_plural,
                                    uiState.selectedImages.size,
                                    uiState.selectedImages.size,
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            val importResult = uiState.importResult
            when {
                importResult != null -> {
                    ImportResultSummary(
                        result = importResult,
                        onRetry = { viewModel.onIntent(ImportIntent.RetryFailedImports) },
                        onDone = { viewModel.onIntent(ImportIntent.DismissImportResult) },
                    )
                }

                uiState.isImporting -> {
                    ImportProgressContent(
                        progress = uiState.importProgress,
                        total = uiState.totalImportCount,
                        currentFileName = uiState.statusMessage,
                        isIndeterminate = uiState.isProgressIndeterminate,
                        onCancel = { viewModel.onIntent(ImportIntent.CancelImport) },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                uiState.selectedImages.isEmpty() -> {
                    EmptyImportContent(
                        onSelectImages = {
                            viewModel.onIntent(ImportIntent.PickMoreImages)
                        },
                        onImportBundle = {
                            viewModel.onIntent(ImportIntent.PickZipBundle)
                        },
                    )
                }

                else -> {
                    ImportGridContent(
                        images = uiState.selectedImages,
                        editingIndex = uiState.editingImageIndex,
                        onImageClick = { index ->
                            viewModel.onIntent(ImportIntent.EditImage(index))
                        },
                        onRemoveImage = { index ->
                            viewModel.onIntent(ImportIntent.RemoveImage(index))
                        },
                        onAddMore = {
                            viewModel.onIntent(ImportIntent.PickMoreImages)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Import button at bottom
                    Button(
                        onClick = { viewModel.onIntent(ImportIntent.StartImport) },
                        enabled = uiState.canImport,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(16.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.import_content_description_confirm_import),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            pluralStringResource(
                                R.plurals.import_button_import_memes,
                                uiState.selectedImages.size,
                                uiState.selectedImages.size,
                            ),
                        )
                    }
                }
            }
        }

        // Duplicate detection dialog
        if (uiState.showDuplicateDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(ImportIntent.DismissDuplicateDialog) },
                title = { Text(stringResource(R.string.import_duplicate_dialog_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.import_duplicate_dialog_message,
                                uiState.duplicateIndices.size,
                            ),
                        )
                        if (uiState.duplicatesWithChangedMetadata.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(
                                    R.string.import_duplicate_metadata_changed,
                                    uiState.duplicatesWithChangedMetadata.size,
                                ),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onIntent(ImportIntent.ImportDuplicatesAnyway) }) {
                        Text(stringResource(R.string.import_duplicate_import_anyway))
                    }
                },
                dismissButton = {
                    Column {
                        if (uiState.duplicatesWithChangedMetadata.isNotEmpty()) {
                            TextButton(onClick = { viewModel.onIntent(ImportIntent.UpdateDuplicateMetadata) }) {
                                Text(stringResource(R.string.import_duplicate_update_metadata))
                            }
                        }
                        TextButton(onClick = { viewModel.onIntent(ImportIntent.SkipDuplicates) }) {
                            Text(stringResource(R.string.import_duplicate_skip))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ImportResultSummary(
    result: ImportResult,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (result.failureCount == 0) "✅" else "⚠️",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.import_result_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_result_summary, result.successCount, result.failureCount),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone) {
            Text(stringResource(R.string.import_result_done))
        }
        if (result.failureCount > 0) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.import_result_retry, result.failureCount))
            }
        }
    }
}

@Composable
internal fun EmptyImportContent(
    onSelectImages: () -> Unit,
    onImportBundle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.AddPhotoAlternate,
            contentDescription = stringResource(R.string.import_content_description_add_photos),
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.import_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSelectImages) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_content_description_add_images))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_button_select_images))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.import_hint_max_images),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.import_tip_emoji_tagging),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onImportBundle) {
            Text(stringResource(R.string.import_button_import_bundle))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_bundle_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportGridContent(
    images: List<ImportImage>,
    editingIndex: Int?,
    onImageClick: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onAddMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding =
            PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 80.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(images.size, key = { images[it].uri.toString() }) { index ->
            val image = images[index]
            val emojiStrings = remember(image.emojis) { image.emojis.map { it.emoji } }
            ImportImageCard(
                uri = image.uri,
                emojis = emojiStrings,
                isSelected = index == editingIndex,
                hasError = image.error != null,
                isProcessing = image.isProcessing,
                onClick = { onImageClick(index) },
                onRemove = { onRemoveImage(index) },
            )
        }

        item {
            AddMoreCard(onClick = onAddMore)
        }
    }
}

@Composable
private fun ImportImageCard(
    uri: Uri,
    emojis: List<String>,
    isSelected: Boolean,
    hasError: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .aspectRatio(1f)
                .then(
                    when {
                        hasError ->
                            Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.error,
                                shape = MaterialTheme.shapes.medium,
                            )
                        isSelected ->
                            Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.medium,
                            )
                        else -> Modifier
                    },
                ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.import_content_description_image_to_import),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = CircleShape,
                        ),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.import_content_description_remove),
                    modifier = Modifier.size(18.dp),
                )
            }

            // Processing indicator
            if (isProcessing) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            // Error indicator
            if (hasError) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = stringResource(R.string.import_content_description_error),
                    tint = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(32.dp),
                )
            }

            // Emoji preview
            if (emojis.isNotEmpty()) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    emojis.take(3).forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (emojis.size > 3) {
                        Text(
                            text = stringResource(R.string.import_emoji_overflow, emojis.size - 3),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMoreCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.import_button_add_more),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.import_button_add_more),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditImageSheet(
    imageUri: Uri,
    currentEmojis: List<EmojiTag>,
    suggestedEmojis: List<EmojiTag>,
    title: String,
    description: String,
    onEmojiToggle: (EmojiTag) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onApplySuggestions: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        // Image preview
        AsyncImage(
            model = imageUri,
            contentDescription = stringResource(R.string.import_content_description_image_preview),
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(MaterialTheme.shapes.medium),
        )

        Spacer(Modifier.height(16.dp))

        // Title field
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.import_field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // Description field
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.import_field_description)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // AI Suggested Emojis
        if (suggestedEmojis.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.import_section_ai_suggested),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onApplySuggestions,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.import_button_apply_all), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestedEmojis, key = { it.emoji }) { emojiTag ->
                    EmojiChip(
                        emojiTag = emojiTag,
                        onClick = { onEmojiToggle(emojiTag) },
                        showName = true,
                        backgroundColor =
                            if (currentEmojis.contains(
                                    emojiTag,
                                )
                            ) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                null
                            },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Emoji selection
        Text(
            text = stringResource(R.string.import_section_select_emojis),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CommonEmojis.forEach { emojiTag ->
                EmojiChip(
                    emojiTag = emojiTag,
                    onClick = { onEmojiToggle(emojiTag) },
                    backgroundColor =
                        if (currentEmojis.contains(
                                emojiTag,
                            )
                        ) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            null
                        },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Done button
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_button_done))
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun ImportProgressContent(
    progress: Float,
    total: Int,
    currentFileName: String?,
    isIndeterminate: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressCount = (progress * total).toInt()
    val progressPercent = (progress * 100).toInt()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isIndeterminate) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
            )
        } else {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.import_progress_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        if (!isIndeterminate) {
            Text(
                text = stringResource(R.string.import_progress_count, progressCount, total, progressPercent),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (isIndeterminate) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        currentFileName?.let { fileName ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.import_button_cancel))
        }
    }
}

// region Previews

@Preview(name = "Empty Import", showBackground = true)
@Preview(name = "Empty Import Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyImportContentPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        EmptyImportContent(
            onSelectImages = {},
            onImportBundle = {},
        )
    }
}

@Preview(name = "Importing", showBackground = true)
@Composable
private fun ImportProgressContentPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        ImportProgressContent(
            progress = 0.45f,
            total = 5,
            currentFileName = "funny_cat.jpg",
            isIndeterminate = false,
            onCancel = {},
        )
    }
}

@Preview(name = "Indeterminate Progress", showBackground = true)
@Composable
private fun ImportProgressIndeterminatePreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        ImportProgressContent(
            progress = 0f,
            total = 0,
            currentFileName = "Extracting ZIP bundle...",
            isIndeterminate = true,
            onCancel = {},
        )
    }
}

// endregion
