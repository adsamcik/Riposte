package com.adsamcik.riposte.feature.gallery.presentation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.ui.component.EmojiChip
import com.adsamcik.riposte.core.ui.component.ErrorState
import com.adsamcik.riposte.core.ui.component.LoadingScreen
import com.adsamcik.riposte.feature.gallery.R
import com.adsamcik.riposte.feature.gallery.domain.usecase.SimilarMemesStatus
import com.adsamcik.riposte.feature.gallery.presentation.component.EditEmojiDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemeDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMeme: (Long) -> Unit = {},
    onNavigateToGalleryWithEmoji: (String) -> Unit = {},
    viewModel: MemeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val zoomState = rememberZoomState()
    val shareNoAppMessage = stringResource(R.string.gallery_error_share_no_app)

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MemeDetailEffect.NavigateBack -> onNavigateBack()
                is MemeDetailEffect.LaunchShareIntent -> {
                    try {
                        context.startActivity(effect.intent)
                    } catch (_: android.content.ActivityNotFoundException) {
                        snackbarHostState.showSnackbar(shareNoAppMessage)
                    }
                }
                is MemeDetailEffect.NavigateToMeme -> onNavigateToMeme(effect.memeId)
                is MemeDetailEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is MemeDetailEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is MemeDetailEffect.NavigateToGalleryWithEmoji ->
                    onNavigateToGalleryWithEmoji(effect.emoji)
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

    val onRequestBack = {
        when {
            uiState.isEditMode && uiState.hasUnsavedChanges -> showDiscardDialog = true
            uiState.isEditMode -> {
                onIntent(MemeDetailIntent.DiscardChanges)
                onNavigateBack()
            }
            else -> onNavigateBack()
        }
    }

    val onCancelEdit = {
        if (uiState.hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onIntent(MemeDetailIntent.ToggleEditMode)
        }
    }

    BackHandler(enabled = true) {
        when {
            uiState.isEditMode && uiState.hasUnsavedChanges -> showDiscardDialog = true
            uiState.isEditMode -> onIntent(MemeDetailIntent.Dismiss)
            else -> onNavigateBack()
        }
    }

    MemeDetailDialogs(
        uiState = uiState,
        showDiscardDialog = showDiscardDialog,
        onDismissDiscardDialog = { showDiscardDialog = false },
        onConfirmDiscard = {
            showDiscardDialog = false
            onIntent(MemeDetailIntent.DiscardChanges)
            onNavigateBack()
        },
        onIntent = onIntent,
    )

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
            MemeDetailContent(
                uiState = uiState,
                onIntent = onIntent,
                onNavigateBack = onRequestBack,
                onCancelEdit = onCancelEdit,
                snackbarHostState = snackbarHostState,
                zoomState = zoomState,
            )
        }
    }
}

@Composable
private fun MemeDetailDialogs(
    uiState: MemeDetailUiState,
    showDiscardDialog: Boolean,
    onDismissDiscardDialog: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onIntent: (MemeDetailIntent) -> Unit,
) {
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscardDialog,
            title = { Text(stringResource(R.string.gallery_discard_changes_title)) },
            text = { Text(stringResource(R.string.gallery_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard) {
                    Text(stringResource(R.string.gallery_button_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscardDialog) {
                    Text(stringResource(R.string.gallery_button_keep_editing))
                }
            },
        )
    }

    if (uiState.showDeleteDialog) {
        val memeName = uiState.meme?.title ?: uiState.meme?.fileName
        AlertDialog(
            onDismissRequest = { onIntent(MemeDetailIntent.DismissDeleteDialog) },
            title = { Text(stringResource(R.string.gallery_delete_single_title)) },
            text = {
                Text(
                    if (memeName != null) {
                        stringResource(R.string.gallery_delete_single_message_named, memeName)
                    } else {
                        stringResource(R.string.gallery_delete_single_message)
                    },
                )
            },
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

    if (uiState.showEmojiPicker) {
        EditEmojiDialog(
            selectedEmojis = uiState.editedEmojis,
            onAddEmoji = { emoji -> onIntent(MemeDetailIntent.AddEmoji(emoji)) },
            onRemoveEmoji = { emoji -> onIntent(MemeDetailIntent.RemoveEmoji(emoji)) },
            onDismiss = { onIntent(MemeDetailIntent.DismissEmojiPicker) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MemeDetailContent(
    uiState: MemeDetailUiState,
    onIntent: (MemeDetailIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onCancelEdit: () -> Unit,
    snackbarHostState: SnackbarHostState,
    zoomState: ZoomState,
) {
    val meme = uiState.meme ?: return
    val bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val adaptivePeekHeight = (screenHeightDp * 0.25f).coerceIn(120.dp, 280.dp)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = adaptivePeekHeight,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetDragHandle =
            if (zoomState.isZoomed) {
                { /* empty — suppress drag handle when zoomed */ }
            } else {
                null // default drag handle
            },
        sheetContent = {
            MemeInfoSheet(
                uiState = uiState,
                onIntent = onIntent,
                onCancelEdit = onCancelEdit,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        },
        containerColor = MaterialTheme.colorScheme.scrim,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.scrim),
        ) {
            MemeImagePager(
                uiState = uiState,
                meme = meme,
                zoomState = zoomState,
                onIntent = onIntent,
            )

            IconButton(
                onClick = onNavigateBack,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                            shape = CircleShape,
                        ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.gallery_cd_navigate_back),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

@Composable
private fun MemeImagePager(
    uiState: MemeDetailUiState,
    meme: com.adsamcik.riposte.core.model.Meme,
    zoomState: ZoomState,
    onIntent: (MemeDetailIntent) -> Unit,
) {
    val allMemeIds = uiState.allMemeIds
    if (allMemeIds.size > 1) {
        val initialPage = allMemeIds.indexOf(meme.id).coerceAtLeast(0)
        val pagerState =
            rememberPagerState(
                initialPage = initialPage,
                pageCount = { allMemeIds.size },
            )

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collect { page ->
                    val newMemeId = allMemeIds[page]
                    if (newMemeId != uiState.meme?.id) {
                        zoomState.reset()
                        onIntent(MemeDetailIntent.ChangeMeme(newMemeId))
                    }
                }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !uiState.isEditMode && !zoomState.isZoomed,
            key = { allMemeIds[it] },
        ) { page ->
            val pageMemeId = allMemeIds[page]
            MemeImage(
                filePath = if (pageMemeId == meme.id) meme.filePath else null,
                contentDescription =
                    if (pageMemeId == meme.id) {
                        stringResource(R.string.gallery_cd_meme_image, meme.title ?: meme.fileName)
                    } else {
                        null
                    },
                zoomState = if (pageMemeId == meme.id) zoomState else null,
            )
        }
    } else {
        MemeImage(
            filePath = meme.filePath,
            contentDescription = stringResource(R.string.gallery_cd_meme_image, meme.title ?: meme.fileName),
            zoomState = zoomState,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemeInfoSheet(
    uiState: MemeDetailUiState,
    onIntent: (MemeDetailIntent) -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val meme = uiState.meme ?: return
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM) }
    val favoritedText = stringResource(com.adsamcik.riposte.core.ui.R.string.ui_state_favorited)
    val notFavoritedText = stringResource(com.adsamcik.riposte.core.ui.R.string.ui_state_not_favorited)

    Column(
        modifier =
            modifier
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
                    onClick = { if (uiState.isEditMode) onCancelEdit() else onIntent(MemeDetailIntent.ToggleEditMode) },
                ) {
                    Icon(
                        if (uiState.isEditMode) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription =
                            if (uiState.isEditMode) {
                                stringResource(
                                    R.string.gallery_cd_cancel_edit,
                                )
                            } else {
                                stringResource(R.string.gallery_cd_edit)
                            },
                    )
                }
                Text(
                    text =
                        if (uiState.isEditMode) {
                            stringResource(
                                R.string.gallery_detail_action_cancel,
                            )
                        } else {
                            stringResource(R.string.gallery_detail_action_edit)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = { onIntent(MemeDetailIntent.Share) },
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
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
                val haptic = LocalHapticFeedback.current
                var animateTrigger by remember { mutableStateOf(0) }
                val animatedScale by animateFloatAsState(
                    targetValue = if (animateTrigger > 0) 1.3f else 1f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    finishedListener = { animateTrigger = 0 },
                    label = "favorite_bounce",
                )
                FilledTonalIconButton(
                    onClick = {
                        animateTrigger++
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onIntent(MemeDetailIntent.ToggleFavorite)
                    },
                    colors =
                        if (meme.isFavorite) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        } else {
                            IconButtonDefaults.filledTonalIconButtonColors()
                        },
                    modifier =
                        Modifier
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }
                            .semantics {
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
                    colors =
                        IconButtonDefaults.outlinedIconButtonColors(
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
                    if (uiState.isSaving) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.gallery_cd_confirm))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.gallery_button_save))
                }
            }
        } else {
            // View mode — Title
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

            // Description
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
                            onClick = { onIntent(MemeDetailIntent.SearchByEmoji(tag.emoji)) },
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.gallery_detail_empty_emoji_tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Divider between content and metadata
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Metadata section — always visible, no collapsible
            Text(
                text =
                    stringResource(
                        R.string.gallery_detail_label_imported,
                        java.time.Instant.ofEpochMilli(
                            meme.importedAt,
                        ).atZone(java.time.ZoneId.systemDefault()).format(dateFormatter),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.gallery_detail_dimensions_value, meme.width, meme.height),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatFileSize(meme.fileSizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = meme.mimeType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Extracted text
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

            // Similar memes section
            SimilarMemesSection(
                status = uiState.similarMemesStatus,
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

private fun Modifier.zoomGestures(
    zoomState: ZoomState,
    viewportSize: () -> IntSize,
): Modifier =
    this
        .pointerInput(zoomState) {
            detectTapGestures(
                onTap = { zoomState.toggleControls() },
                onDoubleTap = { zoomState.doubleTapToggle() },
            )
        }
        .pointerInput(zoomState, zoomState.isZoomed) {
            if (zoomState.isZoomed) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomState.zoomBy(zoom)
                    zoomState.panBy(
                        delta = pan,
                        viewportWidth = viewportSize().width.toFloat(),
                        viewportHeight = viewportSize().height.toFloat(),
                    )
                }
            }
        }

@Composable
private fun MemeImage(
    filePath: String?,
    contentDescription: String?,
    zoomState: ZoomState?,
    modifier: Modifier = Modifier,
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .then(
                    if (zoomState != null) {
                        Modifier.zoomGestures(zoomState) { viewportSize }
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (filePath != null) {
            ZoomableImage(
                filePath = filePath,
                contentDescription = contentDescription,
                zoomState = zoomState,
            )
        } else {
            MemeImagePlaceholder()
        }
    }
}

@Composable
private fun ZoomableImage(
    filePath: String,
    contentDescription: String?,
    zoomState: ZoomState?,
    modifier: Modifier = Modifier,
) {
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val backgroundColor =
        when (imageState) {
            is AsyncImagePainter.State.Error -> MaterialTheme.colorScheme.errorContainer
            is AsyncImagePainter.State.Loading -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundColor)
                .graphicsLayer {
                    val scale = zoomState?.scale ?: 1f
                    scaleX = scale
                    scaleY = scale
                    translationX = zoomState?.offset?.x ?: 0f
                    translationY = zoomState?.offset?.y ?: 0f
                },
    ) {
        AsyncImage(
            model = filePath,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onState = { imageState = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MemeImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SimilarMemesSection(
    status: SimilarMemesStatus?,
    isLoading: Boolean,
    onMemeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing to show yet — still initializing
    if (!isLoading && status == null) return

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.gallery_detail_section_similar),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (isLoading) {
            Box(
                modifier =
                    Modifier
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
            when (status) {
                is SimilarMemesStatus.Found -> {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = status.memes,
                            key = { it.id },
                        ) { meme ->
                            SimilarMemeCard(
                                meme = meme,
                                onClick = { onMemeClick(meme.id) },
                            )
                        }
                    }
                }
                is SimilarMemesStatus.NoEmbeddingForMeme -> {
                    SimilarMemesHint(stringResource(R.string.gallery_similar_hint_generating))
                }
                is SimilarMemesStatus.NoCandidates -> {
                    SimilarMemesHint(stringResource(R.string.gallery_similar_hint_no_candidates))
                }
                is SimilarMemesStatus.NoSimilarFound -> {
                    SimilarMemesHint(stringResource(R.string.gallery_similar_hint_none_found))
                }
                is SimilarMemesStatus.Error -> {
                    SimilarMemesHint(stringResource(R.string.gallery_similar_hint_error))
                }
                null -> { /* handled by early return above */ }
            }
        }
    }
}

@Composable
private fun SimilarMemesHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun SimilarMemeCard(
    meme: com.adsamcik.riposte.core.model.Meme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(100.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick, role = Role.Button),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = meme.filePath,
            contentDescription = stringResource(R.string.gallery_cd_similar_meme, meme.title ?: meme.fileName),
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
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

// region Previews

private val detailPreviewMeme =
    com.adsamcik.riposte.core.model.Meme(
        id = 1L,
        filePath = "/preview/meme.jpg",
        fileName = "meme.jpg",
        mimeType = "image/jpeg",
        width = 1024,
        height = 768,
        fileSizeBytes = 256_000L,
        importedAt = System.currentTimeMillis(),
        emojiTags =
            listOf(
                com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("😂"),
                com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("🔥"),
            ),
        title = "Funny cat meme",
        description = "A hilarious cat wearing a tiny hat",
        textContent = "When you finally fix the bug but break two more",
        isFavorite = true,
    )

@Preview(name = "Loading", showBackground = true)
@Composable
private fun MemeDetailLoadingPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
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
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        MemeDetailScreen(
            uiState =
                MemeDetailUiState(
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
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        MemeDetailScreen(
            uiState =
                MemeDetailUiState(
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
