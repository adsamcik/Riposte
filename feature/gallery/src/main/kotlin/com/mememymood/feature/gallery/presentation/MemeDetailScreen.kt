package com.mememymood.feature.gallery.presentation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.ui.component.EmojiChip
import com.mememymood.core.ui.component.LoadingScreen
import com.mememymood.feature.gallery.R
import com.mememymood.feature.gallery.presentation.component.EditEmojiDialog
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemeDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShare: (Long) -> Unit = {},
    viewModel: MemeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MemeDetailEffect.NavigateBack -> onNavigateBack()
                is MemeDetailEffect.NavigateToShare -> onNavigateToShare(effect.memeId)
                is MemeDetailEffect.LaunchShareIntent -> {
                    context.startActivity(Intent.createChooser(effect.intent, "Share Meme"))
                }
                is MemeDetailEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is MemeDetailEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    MemeDetailScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        scale = scale,
        offset = offset,
        showControls = showControls,
        onScaleChange = { scale = it },
        onOffsetChange = { offset = it },
        onShowControlsChange = { showControls = it },
    )
}

/**
 * Test-friendly overload that accepts UI state directly.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemeDetailScreen(
    uiState: MemeDetailUiState,
    onIntent: (MemeDetailIntent) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }

    MemeDetailScreenContent(
        uiState = uiState,
        onIntent = onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        scale = scale,
        offset = offset,
        showControls = showControls,
        onScaleChange = { scale = it },
        onOffsetChange = { offset = it },
        onShowControlsChange = { showControls = it },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MemeDetailScreenContent(
    uiState: MemeDetailUiState,
    onIntent: (MemeDetailIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    scale: Float,
    offset: Offset,
    showControls: Boolean,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onShowControlsChange: (Boolean) -> Unit,
) {
    // Delete confirmation dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(MemeDetailIntent.DismissDeleteDialog) },
            title = { Text(stringResource(R.string.gallery_delete_single_title)) },
            text = { Text(stringResource(R.string.gallery_delete_single_message)) },
            confirmButton = {
                TextButton(onClick = { onIntent(MemeDetailIntent.ConfirmDelete) }) {
                    Text(stringResource(R.string.gallery_button_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(MemeDetailIntent.DismissDeleteDialog) }) {
                    Text(stringResource(R.string.gallery_button_cancel))
                }
            },
        )
    }

    // Emoji picker dialog
    if (uiState.showEmojiPicker) {
        EditEmojiDialog(
            selectedEmojis = uiState.editedEmojis,
            onAddEmoji = { emoji ->
                onIntent(MemeDetailIntent.AddEmoji(emoji))
            },
            onRemoveEmoji = { emoji ->
                onIntent(MemeDetailIntent.RemoveEmoji(emoji))
            },
            onDismiss = { onIntent(MemeDetailIntent.DismissEmojiPicker) },
        )
    }

    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    when {
        uiState.isLoading -> {
            LoadingScreen(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }

        uiState.errorMessage != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.errorMessage.orEmpty().ifEmpty { stringResource(R.string.gallery_error_default) },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        uiState.meme != null -> {
            val meme = uiState.meme
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = 200.dp,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                sheetContent = {
                    MemeInfoSheet(
                        uiState = uiState,
                        onIntent = onIntent,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    )
                },
                containerColor = Color.Black,
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onShowControlsChange(!showControls) },
                                onDoubleTap = {
                                    onScaleChange(if (scale > 1f) 1f else 2f)
                                    onOffsetChange(Offset.Zero)
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                onScaleChange((scale * zoom).coerceIn(0.5f, 4f))
                                onOffsetChange(offset + pan)
                            }
                        },
                ) {
                    // Zoomable image
                    AsyncImage(
                        model = meme.filePath,
                        contentDescription = stringResource(
                            R.string.gallery_cd_meme_image,
                            meme.title ?: meme.fileName,
                        ),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )

                    // Top bar with back button
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically(),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.gallery_cd_navigate_back),
                                    tint = Color.White,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            
                            // Favorite button
                            IconButton(
                                onClick = { onIntent(MemeDetailIntent.ToggleFavorite) },
                            ) {
                                Icon(
                                    if (meme.isFavorite) {
                                        Icons.Filled.Favorite
                                    } else {
                                        Icons.Filled.FavoriteBorder
                                    },
                                    contentDescription = stringResource(R.string.gallery_cd_favorite),
                                    tint = if (meme.isFavorite) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Color.White
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemeInfoSheet(
    uiState: MemeDetailUiState,
    onIntent: (MemeDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meme = uiState.meme ?: return
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FilledTonalIconButton(
                onClick = { onIntent(MemeDetailIntent.ToggleEditMode) },
            ) {
                Icon(
                    if (uiState.isEditMode) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = if (uiState.isEditMode) stringResource(R.string.gallery_cd_cancel_edit) else stringResource(R.string.gallery_cd_edit),
                )
            }
            FilledTonalIconButton(
                onClick = { onIntent(MemeDetailIntent.Share) },
            ) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.gallery_cd_share))
            }
            FilledTonalIconButton(
                onClick = { onIntent(MemeDetailIntent.ToggleFavorite) },
                colors = if (meme.isFavorite) {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    )
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors()
                },
                modifier = Modifier.semantics {
                    role = Role.Button
                    stateDescription = if (meme.isFavorite) "Favorited" else "Not favorited"
                },
            ) {
                Icon(
                    if (meme.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.gallery_cd_favorite),
                )
            }
            FilledTonalIconButton(
                onClick = { onIntent(MemeDetailIntent.ShowDeleteDialog) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.gallery_cd_delete))
            }
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.isEditMode) {
            // Edit mode
            OutlinedTextField(
                value = uiState.editedTitle,
                onValueChange = { onIntent(MemeDetailIntent.UpdateTitle(it)) },
                label = { Text(stringResource(R.string.gallery_detail_label_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.editedDescription,
                onValueChange = { onIntent(MemeDetailIntent.UpdateDescription(it)) },
                label = { Text(stringResource(R.string.gallery_detail_label_description)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // Emoji tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.gallery_detail_label_emoji_tags),
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(
                    onClick = { onIntent(MemeDetailIntent.ShowEmojiPicker) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.gallery_cd_add_emoji))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.editedEmojis.forEach { emoji ->
                    EmojiChip(
                        emojiTag = EmojiTag.fromEmoji(emoji),
                        onClick = { onIntent(MemeDetailIntent.RemoveEmoji(emoji)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onIntent(MemeDetailIntent.DiscardChanges) }) {
                    Text(stringResource(R.string.gallery_button_discard))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onIntent(MemeDetailIntent.SaveChanges) },
                    enabled = uiState.hasUnsavedChanges,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.gallery_button_save))
                }
            }
        } else {
            // View mode
            meme.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            } ?: Text(
                text = meme.fileName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )

            meme.description?.let { description ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Emoji tags
            if (meme.emojiTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    meme.emojiTags.forEach { tag ->
                        EmojiChip(
                            emojiTag = tag,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Metadata
            Text(
                text = stringResource(R.string.gallery_detail_label_imported, java.time.Instant.ofEpochMilli(meme.importedAt).atZone(java.time.ZoneId.systemDefault()).format(dateFormatter)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            meme.textContent?.takeIf { it.isNotBlank() }?.let { text ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.gallery_detail_label_extracted_text),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
