package com.mememymood.feature.gallery.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.mememymood.core.model.Meme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ripple
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mememymood.core.ui.component.EmptyState
import com.mememymood.core.ui.component.EmojiFilterRail
import com.mememymood.core.ui.component.ErrorState
import com.mememymood.core.ui.component.LoadingScreen
import com.mememymood.core.ui.component.MemeCardCompact
import com.mememymood.core.ui.modifier.animatedPressScale
import com.mememymood.core.ui.theme.rememberGridColumns
import com.mememymood.feature.gallery.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToShare: (Long) -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagedMemes = viewModel.pagedMemes.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteCount by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GalleryEffect.NavigateToMeme -> onNavigateToMeme(effect.memeId)
                is GalleryEffect.NavigateToImport -> onNavigateToImport()
                is GalleryEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is GalleryEffect.ShowDeleteConfirmation -> {
                    deleteCount = effect.count
                    showDeleteDialog = true
                }
                is GalleryEffect.OpenShareSheet -> {
                    if (effect.memeIds.size == 1) {
                        onNavigateToMeme(effect.memeIds.first())
                    }
                }
                is GalleryEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is GalleryEffect.NavigateToShare -> onNavigateToShare(effect.memeId)
                is GalleryEffect.LaunchShareIntent -> {
                    context.startActivity(android.content.Intent.createChooser(effect.intent, "Share Meme"))
                }
                is GalleryEffect.LaunchQuickShare -> {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = effect.meme.mimeType
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            java.io.File(effect.meme.filePath),
                        )
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setClassName(effect.target.packageName, effect.target.activityName)
                    }
                    context.startActivity(shareIntent)
                }
            }
        }
    }

    GalleryScreenContent(
        uiState = uiState,
        pagedMemes = pagedMemes,
        onIntent = viewModel::onIntent,
        onNavigateToMeme = onNavigateToMeme,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToImport = onNavigateToImport,
        onNavigateToSettings = onNavigateToSettings,
        snackbarHostState = snackbarHostState,
        showDeleteDialog = showDeleteDialog,
        deleteCount = deleteCount,
        showMenu = showMenu,
        onShowDeleteDialogChange = { showDeleteDialog = it },
        onDeleteCountChange = { deleteCount = it },
        onShowMenuChange = { showMenu = it },
    )
}

/**
 * Test-friendly overload that accepts UI state directly.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    uiState: GalleryUiState,
    onIntent: (GalleryIntent) -> Unit,
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    pagedMemes: LazyPagingItems<Meme>? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteCount by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    GalleryScreenContent(
        uiState = uiState,
        pagedMemes = pagedMemes,
        onIntent = onIntent,
        onNavigateToMeme = onNavigateToMeme,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToImport = onNavigateToImport,
        onNavigateToSettings = onNavigateToSettings,
        snackbarHostState = snackbarHostState,
        showDeleteDialog = showDeleteDialog,
        deleteCount = deleteCount,
        showMenu = showMenu,
        onShowDeleteDialogChange = { showDeleteDialog = it },
        onDeleteCountChange = { deleteCount = it },
        onShowMenuChange = { showMenu = it },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GalleryScreenContent(
    uiState: GalleryUiState,
    pagedMemes: LazyPagingItems<Meme>? = null,
    onIntent: (GalleryIntent) -> Unit,
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    showDeleteDialog: Boolean,
    deleteCount: Int,
    showMenu: Boolean,
    onShowDeleteDialogChange: (Boolean) -> Unit,
    onDeleteCountChange: (Int) -> Unit,
    onShowMenuChange: (Boolean) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val columns = rememberGridColumns(uiState.densityPreference)

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                onShowDeleteDialogChange(false)
                onIntent(GalleryIntent.CancelDelete)
            },
            title = { Text(pluralStringResource(R.plurals.gallery_delete_count_title, deleteCount, deleteCount)) },
            text = { Text(pluralStringResource(R.plurals.gallery_delete_count_message, deleteCount, deleteCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onShowDeleteDialogChange(false)
                        onIntent(GalleryIntent.ConfirmDelete)
                    }
                ) {
                    Text(stringResource(R.string.gallery_button_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onShowDeleteDialogChange(false)
                        onIntent(GalleryIntent.CancelDelete)
                    }
                ) {
                    Text(stringResource(R.string.gallery_button_cancel))
                }
            }
        )
    }

    // Quick share bottom sheet
    val quickShareMeme = uiState.quickShareMeme
    if (quickShareMeme != null) {
        com.mememymood.feature.gallery.presentation.component.QuickShareBottomSheet(
            meme = quickShareMeme,
            frequentTargets = uiState.quickShareTargets,
            onTargetSelected = { target -> onIntent(GalleryIntent.SelectShareTarget(target)) },
            onMoreClick = { onIntent(GalleryIntent.QuickShareMore) },
            onDismiss = { onIntent(GalleryIntent.DismissQuickShare) },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isSelectionMode) {
                            pluralStringResource(R.plurals.gallery_selected_count, uiState.selectionCount, uiState.selectionCount)
                        } else {
                            when (uiState.filter) {
                                is GalleryFilter.Favorites -> stringResource(R.string.gallery_title_favorites)
                                is GalleryFilter.ByEmoji -> uiState.filter.emoji
                                is GalleryFilter.All -> stringResource(R.string.gallery_title)
                            }
                        }
                    )
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { onIntent(GalleryIntent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.gallery_cd_cancel_selection))
                        }
                    } else if (uiState.filter !is GalleryFilter.All || uiState.activeEmojiFilters.isNotEmpty()) {
                        IconButton(onClick = {
                            onIntent(GalleryIntent.ClearEmojiFilters)
                            onIntent(GalleryIntent.SetFilter(GalleryFilter.All))
                        }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.gallery_cd_clear_filter))
                        }
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { onIntent(GalleryIntent.SelectAll) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.gallery_cd_select_all))
                        }
                    } else {
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.gallery_cd_search))
                        }
                        IconButton(onClick = { onShowMenuChange(true) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.gallery_cd_more_options))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { onShowMenuChange(false) }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gallery_menu_all_memes)) },
                                onClick = {
                                    onIntent(GalleryIntent.SetFilter(GalleryFilter.All))
                                    onShowMenuChange(false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gallery_menu_favorites)) },
                                leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null) },
                                onClick = {
                                    onIntent(GalleryIntent.SetFilter(GalleryFilter.Favorites))
                                    onShowMenuChange(false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gallery_menu_select)) },
                                leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                onClick = {
                                    onIntent(GalleryIntent.EnterSelectionMode)
                                    onShowMenuChange(false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gallery_menu_settings)) },
                                onClick = {
                                    onNavigateToSettings()
                                    onShowMenuChange(false)
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                BottomAppBar(
                    actions = {
                        IconButton(onClick = { onIntent(GalleryIntent.ShareSelected) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.gallery_cd_share))
                        }
                        IconButton(onClick = { onIntent(GalleryIntent.DeleteSelected) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.gallery_cd_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !uiState.isSelectionMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { onIntent(GalleryIntent.NavigateToImport) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.gallery_cd_import_memes))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingScreen(
                        message = stringResource(R.string.gallery_loading_message),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error.orEmpty(),
                        onRetry = { onIntent(GalleryIntent.LoadMemes) },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
                uiState.isEmpty && !uiState.usePaging -> {
                    EmptyState(
                        icon = "🖼️",
                        title = stringResource(R.string.gallery_empty_title),
                        message = stringResource(R.string.gallery_empty_message),
                        actionLabel = stringResource(R.string.gallery_button_import_memes),
                        onAction = { onIntent(GalleryIntent.NavigateToImport) },
                    )
                }
                uiState.usePaging && pagedMemes != null -> {
                    // Handle paging load states
                    val loadState = pagedMemes.loadState
                    
                    when {
                        loadState.refresh is LoadState.Loading -> {
                            LoadingScreen(
                                message = stringResource(R.string.gallery_loading_message),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        loadState.refresh is LoadState.Error -> {
                            val error = (loadState.refresh as LoadState.Error).error
                            ErrorState(
                                message = error.message ?: "Failed to load memes",
                                onRetry = { pagedMemes.retry() },
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            )
                        }
                        pagedMemes.itemCount == 0 -> {
                            EmptyState(
                                icon = "🖼️",
                                title = stringResource(R.string.gallery_empty_title),
                                message = stringResource(R.string.gallery_empty_message),
                                actionLabel = stringResource(R.string.gallery_button_import_memes),
                                onAction = { onIntent(GalleryIntent.NavigateToImport) },
                            )
                        }
                        else -> {
                            // Compute paging-specific derived state in Compose
                            // (paged items are not available in ViewModel)
                            val uniqueEmojis = remember(pagedMemes.itemCount) {
                                (0 until pagedMemes.itemCount)
                                    .mapNotNull { pagedMemes.peek(it) }
                                    .flatMap { meme -> meme.emojiTags.map { it.emoji } }
                                    .groupingBy { it }
                                    .eachCount()
                                    .toList()
                                    .sortedByDescending { it.second }
                            }

                            val suggestionIds = remember(uiState.suggestions) {
                                uiState.suggestions.map { it.id }.toSet()
                            }

                            val filteredMemeIndices = remember(pagedMemes.itemCount, uiState.activeEmojiFilters, suggestionIds) {
                                val indices = if (uiState.activeEmojiFilters.isEmpty()) {
                                    (0 until pagedMemes.itemCount).toList()
                                } else {
                                    (0 until pagedMemes.itemCount).filter { index ->
                                        val meme = pagedMemes.peek(index)
                                        meme != null && meme.emojiTags.any { it.emoji in uiState.activeEmojiFilters }
                                    }
                                }
                                // Exclude suggestions — they are shown first
                                indices.filter { index ->
                                    val meme = pagedMemes.peek(index)
                                    meme == null || meme.id !in suggestionIds
                                }
                            }

                            GalleryContent(
                                uiState = uiState,
                                uniqueEmojis = uniqueEmojis,
                                onIntent = onIntent,
                                columns = columns,
                            ) {
                                // Suggested section header
                                if (uiState.suggestions.isNotEmpty()) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "suggested_header",
                                    ) {
                                        Text(
                                            text = stringResource(R.string.gallery_section_suggested),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }

                                // Suggestions first
                                items(
                                    items = uiState.suggestions,
                                    key = { it.id },
                                ) { meme ->
                                    val isSelected = meme.id in uiState.selectedMemeIds
                                    val memeDescription = meme.title ?: meme.fileName

                                    MemeGridItem(
                                        meme = meme,
                                        isSelected = isSelected,
                                        isSelectionMode = uiState.isSelectionMode,
                                        memeDescription = memeDescription,
                                        onIntent = onIntent,
                                    )
                                }

                                // Remaining paged items
                                items(
                                    count = filteredMemeIndices.size,
                                    key = { filteredIndex -> 
                                        val index = filteredMemeIndices[filteredIndex]
                                        pagedMemes.peek(index)?.id ?: index 
                                    },
                                ) { filteredIndex ->
                                    val index = filteredMemeIndices[filteredIndex]
                                    val meme = pagedMemes[index]
                                    if (meme != null) {
                                        val isSelected = meme.id in uiState.selectedMemeIds
                                        val memeDescription = meme.title ?: meme.fileName

                                        MemeGridItem(
                                            meme = meme,
                                            isSelected = isSelected,
                                            isSelectionMode = uiState.isSelectionMode,
                                            memeDescription = memeDescription,
                                            onIntent = onIntent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Non-paged list view (Favorites, ByEmoji filters)
                    val suggestionIds = remember(uiState.suggestions) {
                        uiState.suggestions.map { it.id }.toSet()
                    }
                    val nonSuggestionMemes = remember(uiState.filteredMemes, suggestionIds) {
                        uiState.filteredMemes.filter { it.id !in suggestionIds }
                    }

                    GalleryContent(
                        uiState = uiState,
                        uniqueEmojis = uiState.uniqueEmojis,
                        onIntent = onIntent,
                        columns = columns,
                    ) {
                        // Suggested section header
                        if (uiState.suggestions.isNotEmpty()) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "suggested_header",
                            ) {
                                Text(
                                    text = stringResource(R.string.gallery_section_suggested),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }

                        // Suggestions first
                        items(
                            items = uiState.suggestions,
                            key = { it.id },
                        ) { meme ->
                            val isSelected = meme.id in uiState.selectedMemeIds
                            val memeDescription = meme.title ?: meme.fileName

                            MemeGridItem(
                                meme = meme,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                memeDescription = memeDescription,
                                onIntent = onIntent,
                            )
                        }

                        // Remaining items
                        items(
                            items = nonSuggestionMemes,
                            key = { it.id },
                        ) { meme ->
                            val isSelected = meme.id in uiState.selectedMemeIds
                            val memeDescription = meme.title ?: meme.fileName

                            MemeGridItem(
                                meme = meme,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                memeDescription = memeDescription,
                                onIntent = onIntent,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared gallery content layout used by both paged and non-paged paths.
 * Contains emoji filter rail, sort chips, section header,
 * and a LazyVerticalGrid whose items are provided by [gridContent].
 * Suggestions are prepended as grid items by the caller.
 */
@Composable
private fun GalleryContent(
    uiState: GalleryUiState,
    uniqueEmojis: List<Pair<String, Int>>,
    onIntent: (GalleryIntent) -> Unit,
    columns: Int,
    gridContent: LazyGridScope.() -> Unit,
) {
    Column {
        // Emoji Filter Rail
        if (uniqueEmojis.isNotEmpty() && !uiState.isSelectionMode) {
            EmojiFilterRail(
                emojis = uniqueEmojis,
                activeFilters = uiState.activeEmojiFilters,
                onEmojiToggle = { emoji -> onIntent(GalleryIntent.ToggleEmojiFilter(emoji)) },
                onClearAll = { onIntent(GalleryIntent.ClearEmojiFilters) },
            )
        }

        // Sort chips row
        if (!uiState.isSelectionMode) {
            SortChipRow(
                currentSort = uiState.sortOption,
                onSortSelected = { onIntent(GalleryIntent.SetSortOption(it)) },
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = gridContent,
        )
    }
}

/**
 * Row of sort option chips.
 */
@Composable
private fun SortChipRow(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.gallery_cd_sort),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SortOption.entries.forEach { option ->
            FilterChip(
                selected = currentSort == option,
                onClick = { onSortSelected(option) },
                label = {
                    Text(
                        text = when (option) {
                            SortOption.Recent -> stringResource(R.string.gallery_sort_recent)
                            SortOption.MostUsed -> stringResource(R.string.gallery_sort_most_used)
                            SortOption.EmojiGroup -> stringResource(R.string.gallery_sort_emoji_group)
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

/**
 * Individual meme grid item with selection support and hold-to-share gesture.
 *
 * In normal mode, tap opens the meme and holding for 600ms triggers quick share
 * with a circular progress overlay. In selection mode, tap toggles selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemeGridItem(
    meme: Meme,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    memeDescription: String,
    onIntent: (GalleryIntent) -> Unit,
) {
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)
    val selectedText = stringResource(com.mememymood.core.ui.R.string.ui_state_selected)
    val notSelectedText = stringResource(com.mememymood.core.ui.R.string.ui_state_not_selected)

    if (isSelectionMode) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .animatedPressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { onIntent(GalleryIntent.ToggleSelection(meme.id)) },
                    onLongClick = { /* No long click in selection mode */ },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = memeDescription
                    stateDescription = if (isSelected) selectedText else notSelectedText
                    role = Role.Checkbox
                },
        ) {
            MemeCardCompact(
                meme = meme,
            )

            // Selection overlay
            androidx.compose.foundation.Canvas(
                modifier = Modifier.matchParentSize()
            ) {
                if (isSelected) {
                    drawRect(
                        color = scrimColor
                    )
                }
            }

            androidx.compose.material3.Checkbox(
                checked = isSelected,
                onCheckedChange = { onIntent(GalleryIntent.ToggleSelection(meme.id)) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            )
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .animatedPressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { onIntent(GalleryIntent.OpenMeme(meme.id)) },
                    onLongClick = {
                        onIntent(GalleryIntent.QuickShare(meme.id))
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = memeDescription
                },
        ) {
            MemeCardCompact(
                meme = meme,
            )
        }
    }
}

// region Previews

private val previewMeme = Meme(
    id = 1L,
    filePath = "/preview/meme1.jpg",
    fileName = "meme1.jpg",
    mimeType = "image/jpeg",
    width = 800,
    height = 600,
    fileSizeBytes = 150_000L,
    importedAt = System.currentTimeMillis(),
    emojiTags = listOf(
        com.mememymood.core.model.EmojiTag.fromEmoji("😂"),
        com.mememymood.core.model.EmojiTag.fromEmoji("🔥"),
    ),
    title = "Funny cat meme",
    isFavorite = true,
)

private val previewMemes = listOf(
    previewMeme,
    previewMeme.copy(id = 2L, title = "Surprised Pikachu", emojiTags = listOf(com.mememymood.core.model.EmojiTag.fromEmoji("😮"))),
    previewMeme.copy(id = 3L, title = "Drake meme", isFavorite = false),
)

@Preview(name = "Loading", showBackground = true)
@Composable
private fun GalleryScreenLoadingPreview() {
    com.mememymood.core.ui.theme.MemeMoodTheme {
        GalleryScreen(
            uiState = GalleryUiState(isLoading = true),
            onIntent = {},
            onNavigateToMeme = {},
            onNavigateToSearch = {},
            onNavigateToImport = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(name = "Content", showBackground = true)
@Preview(name = "Content Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GalleryScreenContentPreview() {
    com.mememymood.core.ui.theme.MemeMoodTheme {
        GalleryScreen(
            uiState = GalleryUiState(
                memes = previewMemes,
                isLoading = false,
                usePaging = false,
                suggestions = previewMemes.take(2),
                uniqueEmojis = listOf("😂" to 3, "🔥" to 2, "😮" to 1),
            ),
            onIntent = {},
            onNavigateToMeme = {},
            onNavigateToSearch = {},
            onNavigateToImport = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun GalleryScreenEmptyPreview() {
    com.mememymood.core.ui.theme.MemeMoodTheme {
        GalleryScreen(
            uiState = GalleryUiState(isLoading = false, usePaging = false),
            onIntent = {},
            onNavigateToMeme = {},
            onNavigateToSearch = {},
            onNavigateToImport = {},
            onNavigateToSettings = {},
        )
    }
}

// endregion
