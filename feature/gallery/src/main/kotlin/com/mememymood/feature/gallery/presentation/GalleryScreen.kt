package com.mememymood.feature.gallery.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.mememymood.core.model.Meme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mememymood.core.ui.component.EmptyState
import com.mememymood.core.ui.component.ErrorState
import com.mememymood.core.ui.component.LoadingScreen
import com.mememymood.core.ui.component.MemeCardCompact
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
                    // Handle share - navigate to meme detail for sharing
                    if (effect.memeIds.size == 1) {
                        onNavigateToMeme(effect.memeIds.first())
                    }
                }
                is GalleryEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is GalleryEffect.NavigateToShare -> onNavigateToShare(effect.memeId)
                is GalleryEffect.LaunchShareIntent -> {
                    context.startActivity(android.content.Intent.createChooser(effect.intent, "Share Meme"))
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isSelectionMode) {
                            pluralStringResource(R.plurals.gallery_selected_count, uiState.selectionCount, uiState.selectionCount)
                        } else {
                            stringResource(R.string.gallery_title)
                        }
                    )
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { onIntent(GalleryIntent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.gallery_cd_cancel_selection))
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
                        emoji = "🖼️",
                        title = stringResource(R.string.gallery_empty_title),
                        message = stringResource(R.string.gallery_empty_message),
                        actionLabel = stringResource(R.string.gallery_button_import_memes),
                        onAction = { onIntent(GalleryIntent.NavigateToImport) }
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
                                emoji = "🖼️",
                                title = stringResource(R.string.gallery_empty_title),
                                message = stringResource(R.string.gallery_empty_message),
                                actionLabel = stringResource(R.string.gallery_button_import_memes),
                                onAction = { onIntent(GalleryIntent.NavigateToImport) }
                            )
                        }
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(uiState.gridColumns),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    count = pagedMemes.itemCount,
                                    key = { index -> pagedMemes.peek(index)?.id ?: index }
                                ) { index ->
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(uiState.gridColumns),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.memes,
                            key = { it.id }
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
 * Individual meme grid item with selection support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemeGridItem(
    meme: Meme,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    memeDescription: String,
    onIntent: (GalleryIntent) -> Unit,
) {
    Box(
        modifier = Modifier
            .combinedClickable(
                onClick = { onIntent(GalleryIntent.OpenMeme(meme.id)) },
                onLongClick = { onIntent(GalleryIntent.QuickShare(meme.id)) },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = memeDescription
                if (isSelectionMode) {
                    stateDescription = if (isSelected) "Selected" else "Not selected"
                    role = Role.Checkbox
                }
            },
    ) {
        MemeCardCompact(
            meme = meme,
            onClick = { onIntent(GalleryIntent.OpenMeme(meme.id)) }
        )

        // Selection overlay
        if (isSelectionMode) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.matchParentSize()
            ) {
                if (isSelected) {
                    drawRect(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)
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
    }
}
