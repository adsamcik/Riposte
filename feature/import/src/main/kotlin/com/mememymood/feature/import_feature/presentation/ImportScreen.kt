package com.mememymood.feature.import_feature.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetScaffold
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.ui.component.EmojiChip
import com.mememymood.core.ui.component.EmptyState
import com.mememymood.core.ui.component.LoadingScreen
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
        onResult = { uris ->
            viewModel.onIntent(ImportIntent.ImagesSelected(uris))
        }
    )

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ImportEffect.OpenImagePicker -> {
                    multiplePhotoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                is ImportEffect.ImportComplete -> {
                    onImportComplete()
                }
                is ImportEffect.ShowError -> {
                    // Handle error - could show snackbar
                }
                is ImportEffect.NavigateToGallery -> {
                    onNavigateBack()
                }
                is ImportEffect.ShowSnackbar -> {
                    // Handle snackbar
                }
            }
        }
    }

    val bottomSheetState = rememberStandardBottomSheetState(
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
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Import Memes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.selectedImages.isNotEmpty()) {
                        Text(
                            text = "${uiState.selectedImages.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isImporting -> {
                    ImportProgressContent(
                        progress = uiState.importProgress,
                        total = uiState.selectedImages.size,
                        currentFileName = uiState.statusMessage,
                    )
                }

                uiState.selectedImages.isEmpty() -> {
                    EmptyImportContent(
                        onSelectImages = {
                            viewModel.onIntent(ImportIntent.PickMoreImages)
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
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import ${uiState.selectedImages.size} Meme${if (uiState.selectedImages.size > 1) "s" else ""}")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyImportContent(
    onSelectImages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "📥",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Import Your Memes",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Select images from your gallery to add to your meme collection",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSelectImages) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select Images")
        }
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
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
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
            ImportImageCard(
                uri = image.uri,
                emojis = image.emojis.map { it.emoji },
                isSelected = index == editingIndex,
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
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp),
                )
            }

            // Emoji preview
            if (emojis.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp),
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
                            text = "+${emojis.size - 3}",
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
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
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Add More",
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
    val commonEmojis = listOf(
        EmojiTag("😂", "face_with_tears_of_joy"),
        EmojiTag("❤️", "red_heart"),
        EmojiTag("🔥", "fire"),
        EmojiTag("😍", "smiling_face_with_heart_eyes"),
        EmojiTag("🤣", "rolling_on_the_floor_laughing"),
        EmojiTag("😊", "smiling_face_with_smiling_eyes"),
        EmojiTag("🙏", "folded_hands"),
        EmojiTag("😭", "loudly_crying_face"),
        EmojiTag("😘", "face_blowing_a_kiss"),
        EmojiTag("💯", "hundred_points"),
        EmojiTag("🤔", "thinking_face"),
        EmojiTag("👀", "eyes"),
        EmojiTag("💀", "skull"),
        EmojiTag("🎉", "party_popper"),
        EmojiTag("✨", "sparkles"),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Image preview
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp)),
        )

        Spacer(Modifier.height(16.dp))

        // Title field
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // Description field
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
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
                    text = "AI Suggested",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onApplySuggestions,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text("Apply All", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestedEmojis) { emojiTag ->
                    EmojiChip(
                        emojiTag = emojiTag,
                        onClick = { onEmojiToggle(emojiTag) },
                        showName = true,
                        backgroundColor = if (currentEmojis.contains(emojiTag)) MaterialTheme.colorScheme.primaryContainer else null,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Emoji selection
        Text(
            text = "Select Emojis",
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            commonEmojis.forEach { emojiTag ->
                EmojiChip(
                    emojiTag = emojiTag,
                    onClick = { onEmojiToggle(emojiTag) },
                    backgroundColor = if (currentEmojis.contains(emojiTag)) MaterialTheme.colorScheme.primaryContainer else null,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Done button
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ImportProgressContent(
    progress: Float,
    total: Int,
    currentFileName: String?,
    modifier: Modifier = Modifier,
) {
    val progressCount = (progress * total).toInt()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Importing Memes",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$progressCount of $total",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
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
    }
}
