package com.mememymood.feature.gallery.presentation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.ui.component.EmojiChip
import com.mememymood.core.ui.component.ErrorState
import com.mememymood.core.ui.component.LoadingScreen
import com.mememymood.feature.gallery.R
import com.mememymood.feature.gallery.presentation.component.EditEmojiDialog
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemeDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShare: (Long) -> Unit = {},
    onNavigateToMeme: (Long) -> Unit = {},
    viewModel: MemeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val zoomState = rememberZoomState()

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MemeDetailEffect.NavigateBack -> onNavigateBack()
                is MemeDetailEffect.NavigateToShare -> onNavigateToShare(effect.memeId)
                is MemeDetailEffect.NavigateToMeme -> onNavigateToMeme(effect.memeId)
                is MemeDetailEffect.LaunchShareIntent -> {
                    context.startActivity(Intent.createChooser(effect.intent, context.getString(R.string.gallery_share_chooser_title)))
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
        zoomState = zoomState,
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
    val zoomState = rememberZoomState()

    MemeDetailScreenContent(
        uiState = uiState,
        onIntent = onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        zoomState = zoomState,
    )
}

// TODO: Add HorizontalPager for swipe-between-memes support.
//  - Add `initialIndex: Int = 0` to MemeDetailRoute
//  - ViewModel exposes list of meme IDs (sorted by current gallery order)
//  - Wrap content in HorizontalPager(state = rememberPagerState(initialPage = initialIndex) { memeIds.size })
//  - Load meme data on demand per page
//  - Update GalleryNavigation and MemeMoodNavHost for new route parameter
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MemeDetailScreenContent(
    uiState: MemeDetailUiState,
    onIntent: (MemeDetailIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    zoomState: ZoomState,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }

    // BackHandler for unsaved changes guard
    BackHandler(enabled = true) {
        when {
            uiState.isEditMode && uiState.hasUnsavedChanges -> showDiscardDialog = true
            uiState.isEditMode -> onIntent(MemeDetailIntent.Dismiss)
            else -> onNavigateBack()
        }
    }

    // Discard changes confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.gallery_discard_changes_title)) },
            text = { Text(stringResource(R.string.gallery_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onIntent(MemeDetailIntent.DiscardChanges)
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.gallery_button_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.gallery_button_keep_editing))
                }
            },
        )
    }

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
            ErrorState(
                message = uiState.errorMessage.orEmpty().ifEmpty { stringResource(R.string.gallery_error_default) },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                onRetry = { onIntent(MemeDetailIntent.LoadMeme) },
            )
        }

        uiState.meme != null -> {
            val meme = uiState.meme
            val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
            val adaptivePeekHeight = (screenHeightDp * 0.25f).coerceIn(120.dp, 280.dp)
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = adaptivePeekHeight,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                sheetDragHandle = if (zoomState.isZoomed) {
                    { /* empty — suppress drag handle when zoomed */ }
                } else {
                    null // default drag handle
                },
                sheetContent = {
                    MemeInfoSheet(
                        uiState = uiState,
                        onIntent = onIntent,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    )
                },
                containerColor = MaterialTheme.colorScheme.scrim,
            ) { paddingValues ->
                var viewportSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.scrim)
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(zoomState) {
                            detectTapGestures(
                                onTap = { zoomState.toggleControls() },
                                onDoubleTap = { zoomState.doubleTapToggle() },
                            )
                        }
                        .pointerInput(zoomState) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomState.zoomBy(zoom)
                                zoomState.panBy(
                                    delta = pan,
                                    viewportWidth = viewportSize.width.toFloat(),
                                    viewportHeight = viewportSize.height.toFloat(),
                                )
                            }
                        },
                ) {
                    // Zoomable image with placeholder/error background
                    var imageState by remember {
                        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
                    }
                    val backgroundColor = when (imageState) {
                        is AsyncImagePainter.State.Error -> MaterialTheme.colorScheme.errorContainer
                        is AsyncImagePainter.State.Loading -> MaterialTheme.colorScheme.surfaceVariant
                        else -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor)
                            .graphicsLayer {
                                scaleX = zoomState.scale
                                scaleY = zoomState.scale
                                translationX = zoomState.offset.x
                                translationY = zoomState.offset.y
                            },
                    ) {
                        AsyncImage(
                            model = meme.filePath,
                            contentDescription = stringResource(
                                R.string.gallery_cd_meme_image,
                                meme.title ?: meme.fileName,
                            ),
                            // Crop fills the container but may clip edges on extreme aspect ratios;
                            // acceptable here since users can pinch-to-zoom to see the full image.
                            contentScale = ContentScale.Crop,
                            onState = { imageState = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Back button — always visible for navigation
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.gallery_cd_navigate_back),
                            tint = Color.White,
                        )
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
    val favoritedText = stringResource(com.mememymood.core.ui.R.string.ui_state_favorited)
    val notFavoritedText = stringResource(com.mememymood.core.ui.R.string.ui_state_not_favorited)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Action buttons row with labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconButton(
                    onClick = { onIntent(MemeDetailIntent.ToggleEditMode) },
                ) {
                    Icon(
                        if (uiState.isEditMode) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = if (uiState.isEditMode) stringResource(R.string.gallery_cd_cancel_edit) else stringResource(R.string.gallery_cd_edit),
                    )
                }
                Text(
                    text = if (uiState.isEditMode) stringResource(R.string.gallery_detail_action_cancel) else stringResource(R.string.gallery_detail_action_edit),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = { onIntent(MemeDetailIntent.Share) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.gallery_cd_share))
                }
                Text(
                    text = stringResource(R.string.gallery_detail_action_share),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconButton(
                    onClick = { onIntent(MemeDetailIntent.ToggleFavorite) },
                    colors = if (meme.isFavorite) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors()
                    },
                    modifier = Modifier.semantics {
                        role = Role.Button
                        stateDescription = if (meme.isFavorite) favoritedText else notFavoritedText
                    },
                ) {
                    Icon(
                        if (meme.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.gallery_cd_favorite),
                    )
                }
                Text(
                    text = stringResource(R.string.gallery_detail_action_favorite),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedIconButton(
                    onClick = { onIntent(MemeDetailIntent.ShowDeleteDialog) },
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.gallery_cd_delete))
                }
                Text(
                    text = stringResource(R.string.gallery_detail_action_delete),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
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
                    enabled = uiState.hasUnsavedChanges && !uiState.isSaving,
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
            } else {
                Text(
                    text = stringResource(R.string.gallery_detail_empty_emoji_tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                )
            }

            // Collapsible Details section
            Spacer(Modifier.height(16.dp))
            MemeDetailsSection(meme = meme)

            // Similar memes section
            SimilarMemesSection(
                similarMemes = uiState.similarMemes,
                isLoading = uiState.isLoadingSimilar,
                onMemeClick = { onIntent(MemeDetailIntent.NavigateToSimilarMeme(it)) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Computes adaptive peek height as 25% of screen height, clamped between 120dp and 280dp.
 */
internal fun computeAdaptivePeekHeight(screenHeightDp: Float): Float {
    return (screenHeightDp * 0.25f).coerceIn(120f, 280f)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

@Composable
private fun SimilarMemesSection(
    similarMemes: List<com.mememymood.core.model.Meme>,
    isLoading: Boolean,
    onMemeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && similarMemes.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.gallery_detail_section_similar),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = similarMemes,
                    key = { it.id },
                ) { meme ->
                    SimilarMemeCard(
                        meme = meme,
                        onClick = { onMemeClick(meme.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarMemeCard(
    meme: com.mememymood.core.model.Meme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(100.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick, role = Role.Button),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = meme.filePath,
            contentDescription = stringResource(R.string.gallery_cd_similar_meme, meme.title ?: meme.fileName),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(100.dp)
                .clip(MaterialTheme.shapes.medium),
        )
        meme.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MemeDetailsSection(
    meme: com.mememymood.core.model.Meme,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.gallery_detail_section_details),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                DetailRow(
                    label = stringResource(R.string.gallery_detail_dimensions),
                    value = stringResource(R.string.gallery_detail_dimensions_value, meme.width, meme.height),
                )
                DetailRow(
                    label = stringResource(R.string.gallery_detail_file_size),
                    value = formatFileSize(meme.fileSizeBytes),
                )
                DetailRow(
                    label = stringResource(R.string.gallery_detail_format),
                    value = meme.mimeType,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// region Previews

private val detailPreviewMeme = com.mememymood.core.model.Meme(
    id = 1L,
    filePath = "/preview/meme.jpg",
    fileName = "meme.jpg",
    mimeType = "image/jpeg",
    width = 1024,
    height = 768,
    fileSizeBytes = 256_000L,
    importedAt = System.currentTimeMillis(),
    emojiTags = listOf(
        com.mememymood.core.model.EmojiTag.fromEmoji("😂"),
        com.mememymood.core.model.EmojiTag.fromEmoji("🔥"),
    ),
    title = "Funny cat meme",
    description = "A hilarious cat wearing a tiny hat",
    textContent = "When you finally fix the bug but break two more",
    isFavorite = true,
)

@Preview(name = "Loading", showBackground = true)
@Composable
private fun MemeDetailLoadingPreview() {
    com.mememymood.core.ui.theme.MemeMoodTheme {
        MemeDetailScreen(
            uiState = MemeDetailUiState(isLoading = true),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Content", showBackground = true)
@Preview(name = "Content Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MemeDetailContentPreview() {
    com.mememymood.core.ui.theme.MemeMoodTheme {
        MemeDetailScreen(
            uiState = MemeDetailUiState(
                meme = detailPreviewMeme,
                isLoading = false,
            ),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Edit Mode", showBackground = true)
@Composable
private fun MemeDetailEditModePreview() {
    com.mememymood.core.ui.theme.MemeMoodTheme {
        MemeDetailScreen(
            uiState = MemeDetailUiState(
                meme = detailPreviewMeme,
                isLoading = false,
                isEditMode = true,
                editedTitle = "Funny cat meme",
                editedDescription = "A hilarious cat wearing a tiny hat",
                editedEmojis = listOf("😂", "🔥"),
            ),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}

// endregion
