package com.adsamcik.riposte.feature.gallery.presentation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.ui.component.EmojiFilterRail
import com.adsamcik.riposte.core.ui.component.EmptyState
import com.adsamcik.riposte.core.ui.component.ErrorState
import com.adsamcik.riposte.core.ui.component.LoadingScreen
import com.adsamcik.riposte.core.ui.component.MemeCardCompact
import com.adsamcik.riposte.core.ui.modifier.animatedPressScale
import com.adsamcik.riposte.core.ui.theme.RiposteShapes
import com.adsamcik.riposte.core.ui.theme.rememberGridColumns
import com.adsamcik.riposte.feature.gallery.R
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    initialEmojiFilter: String? = null,
    onEmojiFilterConsumed: () -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel(),
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
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is GalleryEffect.NavigateToMeme -> onNavigateToMeme(effect.memeId)
                is GalleryEffect.NavigateToImport -> onNavigateToImport()
                is GalleryEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is GalleryEffect.ShowDeleteConfirmation -> {
                    deleteCount = effect.count
                    showDeleteDialog = true
                }
                is GalleryEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is GalleryEffect.LaunchShareIntent -> {
                    try {
                        context.startActivity(effect.intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        Timber.w(e, "No app found to handle share intent")
                        snackbarHostState.showSnackbar("Unable to share — app not found")
                    }
                }
                is GalleryEffect.TriggerHapticFeedback -> { /* Handled by Compose haptic feedback */ }
            }
        }
    }

    // Handle emoji filter passed from detail screen (now as search query)
    LaunchedEffect(initialEmojiFilter) {
        if (initialEmojiFilter != null) {
            viewModel.onIntent(GalleryIntent.UpdateSearchQuery(initialEmojiFilter))
            onEmojiFilterConsumed()
        }
    }

    GalleryScreenContent(
        uiState = uiState,
        pagedMemes = pagedMemes,
        onIntent = viewModel::onIntent,
        onNavigateToMeme = onNavigateToMeme,
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
    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }
    }

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
                    },
                ) {
                    Text(stringResource(R.string.gallery_button_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onShowDeleteDialogChange(false)
                        onIntent(GalleryIntent.CancelDelete)
                    },
                ) {
                    Text(stringResource(R.string.gallery_button_cancel))
                }
            },
        )
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = uiState.screenMode == ScreenMode.Searching) {
        if (uiState.isSearchFocused) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onIntent(GalleryIntent.ClearSearch)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            pluralStringResource(
                                R.plurals.gallery_selected_count,
                                uiState.selectionCount,
                                uiState.selectionCount,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onIntent(GalleryIntent.ClearSelection) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.gallery_cd_cancel_selection),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onIntent(GalleryIntent.SelectAll) }) {
                            Icon(
                                Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.gallery_cd_select_all),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconButton(onClick = { onIntent(GalleryIntent.ShareSelected) }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.gallery_cd_share),
                                )
                            }
                            Text(
                                text = stringResource(R.string.gallery_cd_share),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconButton(onClick = { onIntent(GalleryIntent.DeleteSelected) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.gallery_cd_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(
                                text = stringResource(R.string.gallery_cd_delete),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            val isGalleryEmpty = uiState.isEmpty ||
                (uiState.usePaging && pagedMemes != null && pagedMemes.itemCount == 0)
            AnimatedVisibility(
                visible = !uiState.isSelectionMode && !isGalleryEmpty &&
                    uiState.screenMode != ScreenMode.Searching,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = { onIntent(GalleryIntent.NavigateToImport) },
                    shape = RiposteShapes.FABDefault,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.gallery_cd_import_memes))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Content area with top space for floating search bar
            val floatingBarSpace = if (uiState.isSelectionMode) 0.dp else 64.dp
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = floatingBarSpace),
            ) {
                when {
                    uiState.screenMode == ScreenMode.Searching -> {
                        GalleryContent(
                        uiState = uiState,
                        uniqueEmojis = uiState.uniqueEmojis,
                        onIntent = onIntent,
                        columns = columns,
                        gridState = gridState,
                    ) {
                        // Recent searches (when query is empty and not searched yet)
                        if (uiState.searchState.query.isBlank() && !uiState.searchState.hasSearched) {
                            if (uiState.searchState.recentSearches.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "recent_header") {
                                    RecentSearchesHeader(
                                        onClearAll = { onIntent(GalleryIntent.ClearRecentSearches) },
                                    )
                                }
                                items(
                                    count = uiState.searchState.recentSearches.size,
                                    key = { "recent_$it" },
                                    span = { GridItemSpan(maxLineSpan) },
                                ) { index ->
                                    val search = uiState.searchState.recentSearches[index]
                                    RecentSearchItem(
                                        query = search,
                                        onClick = { onIntent(GalleryIntent.SelectRecentSearch(search)) },
                                        onDelete = { onIntent(GalleryIntent.DeleteRecentSearch(search)) },
                                    )
                                }
                            }
                        }

                        if (uiState.searchState.isSearching && uiState.searchState.results.isEmpty()) {
                            // Loading indicator — only when no results to show yet
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search_loading") {
                                val searchingDescription = stringResource(R.string.gallery_cd_searching)
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp)
                                            .semantics {
                                                contentDescription = searchingDescription
                                                liveRegion = LiveRegionMode.Polite
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (uiState.searchState.errorMessage != null) {
                            // Error state
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search_error") {
                                ErrorState(
                                    message = uiState.searchState.errorMessage.orEmpty(),
                                    onRetry = { onIntent(GalleryIntent.UpdateSearchQuery(uiState.searchState.query)) },
                                )
                            }
                        } else if (uiState.searchState.hasSearched && uiState.searchState.results.isEmpty()) {
                            // No results
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search_no_results") {
                                EmptyState(
                                    icon = "🔍",
                                    title =
                                        stringResource(
                                            com.adsamcik.riposte.core.search.R.string.search_no_results_title,
                                        ),
                                    message =
                                        stringResource(
                                            com.adsamcik.riposte.core.search.R.string.search_no_results_description,
                                            uiState.searchState.query,
                                        ),
                                    actionLabel = stringResource(
                                        com.adsamcik.riposte.core.ui.R.string.ui_loading_no_results_clear,
                                    ),
                                    onAction = { onIntent(GalleryIntent.ClearSearch) },
                                )
                            }
                        } else if (uiState.searchState.results.isNotEmpty()) {
                            // Search results
                            // Results header
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search_results_header") {
                                com.adsamcik.riposte.core.ui.component.SearchResultsHeader(
                                    query = uiState.searchState.query,
                                    resultCount = uiState.searchState.totalResultCount,
                                    durationMs = uiState.searchState.searchDurationMs,
                                    isTextOnly = uiState.searchState.isTextOnly,
                                )
                            }
                            // Result items - reuse MemeGridItem
                            items(
                                items = uiState.searchState.results,
                                key = { "search_${it.meme.id}" },
                            ) { result ->
                                val isSelected = result.meme.id in uiState.selectedMemeIds
                                MemeGridItem(
                                    meme = result.meme,
                                    isSelected = isSelected,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onIntent = onIntent,
                                    showEmojis = true,
                                )
                            }
                        }
                    }
                }
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = "🖼️",
                            title = stringResource(R.string.gallery_empty_title),
                            message = stringResource(R.string.gallery_empty_message),
                            actionLabel = stringResource(R.string.gallery_button_import_memes),
                            onAction = { onIntent(GalleryIntent.NavigateToImport) },
                            primaryAction = true,
                        )
                    }
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
                                message = error.message ?: stringResource(R.string.gallery_error_load_failed),
                                onRetry = { pagedMemes.retry() },
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            )
                        }
                        pagedMemes.itemCount == 0 -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyState(
                                    icon = "🖼️",
                                    title = stringResource(R.string.gallery_empty_title),
                                    message = stringResource(R.string.gallery_empty_message),
                                    actionLabel = stringResource(R.string.gallery_button_import_memes),
                                    onAction = { onIntent(GalleryIntent.NavigateToImport) },
                                    primaryAction = true,
                                )
                            }
                        }
                        else -> {
                            val suggestionIds =
                                remember(uiState.suggestions) {
                                    uiState.suggestions.map { it.id }.toSet()
                                }

                            GalleryContent(
                                uiState = uiState,
                                uniqueEmojis = uiState.uniqueEmojis,
                                onIntent = onIntent,
                                columns = columns,
                                gridState = gridState,
                            ) {
                                // Suggestions first
                                items(
                                    items = uiState.suggestions,
                                    key = { "suggestion_${it.id}" },
                                ) { meme ->
                                    val isSelected = meme.id in uiState.selectedMemeIds

                                    MemeGridItem(
                                        meme = meme,
                                        isSelected = isSelected,
                                        isSelectionMode = uiState.isSelectionMode,
                                        onIntent = onIntent,
                                    )
                                }

                                // Remaining paged items (emoji filtering now happens at SQL level)
                                val pagedItemCount = pagedMemes.itemCount
                                for (index in 0 until pagedItemCount) {
                                    val peeked = pagedMemes.peek(index)
                                    // Skip suggestions — they are already shown above
                                    if (peeked != null && peeked.id in suggestionIds) continue

                                    item(
                                        key = peeked?.id ?: "paged_$index",
                                    ) {
                                        val meme = pagedMemes[index]
                                        if (meme != null) {
                                            val isSelected = meme.id in uiState.selectedMemeIds
                                            MemeGridItem(
                                                meme = meme,
                                                isSelected = isSelected,
                                                isSelectionMode = uiState.isSelectionMode,
                                                onIntent = onIntent,
                                            )
                                        }
                                    }
                                }

                                // Paging append loading indicator
                                if (loadState.append is LoadState.Loading) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "append_loading",
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val loadingDescription = stringResource(R.string.gallery_cd_loading_more)
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .semantics {
                                                        contentDescription = loadingDescription
                                                    },
                                            )
                                        }
                                    }
                                }

                                // Paging append error with retry
                                if (loadState.append is LoadState.Error) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "append_error",
                                    ) {
                                        TextButton(
                                            onClick = { pagedMemes.retry() },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(stringResource(R.string.gallery_action_retry))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Non-paged list view (Favorites, ByEmoji filters)
                    val suggestionIds =
                        remember(uiState.suggestions) {
                            uiState.suggestions.map { it.id }.toSet()
                        }
                    val nonSuggestionMemes =
                        remember(uiState.memes, suggestionIds) {
                            uiState.memes.filter { it.id !in suggestionIds }
                        }

                    GalleryContent(
                        uiState = uiState,
                        uniqueEmojis = uiState.uniqueEmojis,
                        onIntent = onIntent,
                        columns = columns,
                        gridState = gridState,
                    ) {
                        // Suggestions first
                        items(
                            items = uiState.suggestions,
                            key = { "suggestion_${it.id}" },
                        ) { meme ->
                            val isSelected = meme.id in uiState.selectedMemeIds

                            MemeGridItem(
                                meme = meme,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                onIntent = onIntent,
                            )
                        }

                        // Remaining items
                        items(
                            items = nonSuggestionMemes,
                            key = { it.id },
                        ) { meme ->
                            val isSelected = meme.id in uiState.selectedMemeIds
                            MemeGridItem(
                                meme = meme,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                onIntent = onIntent,
                            )
                        }
                    }
                }
            }
            }

            // Floating search bar overlay
            if (!uiState.isSelectionMode) {
                FloatingSearchBar(
                    uiState = uiState,
                    isScrolled = isScrolled,
                    showMenu = showMenu,
                    onIntent = onIntent,
                    onShowMenuChange = onShowMenuChange,
                    onNavigateToSettings = onNavigateToSettings,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // Notification banners below the search bar
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 68.dp),
            ) {
                ImportProgressBanner(
                    status = uiState.importStatus,
                )

                NotificationBanner(
                    notification = uiState.notification,
                    onDismiss = { onIntent(GalleryIntent.DismissNotification) },
                )
            }
        }
    }
}

/**
 * Shared gallery content layout used by both paged and non-paged paths.
 * Contains emoji filter rail (search mode only), a LazyVerticalGrid whose items are
 * provided by [gridContent].
 * Suggestions are prepended as grid items by the caller.
 */
@Composable
private fun GalleryContent(
    uiState: GalleryUiState,
    uniqueEmojis: List<Pair<String, Int>>,
    onIntent: (GalleryIntent) -> Unit,
    columns: Int,
    gridState: LazyGridState = rememberLazyGridState(),
    gridContent: LazyGridScope.() -> Unit,
) {
    Column {
        GalleryEmojiFilterRail(
            uiState = uiState,
            uniqueEmojis = uniqueEmojis,
            onIntent = onIntent,
        )

        Box {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 4.dp,
                    bottom = when {
                        uiState.screenMode == ScreenMode.Searching -> 24.dp
                        else -> 120.dp
                    },
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = gridContent,
            )
        }
    }
}

@Composable
private fun GalleryEmojiFilterRail(
    uiState: GalleryUiState,
    uniqueEmojis: List<Pair<String, Int>>,
    onIntent: (GalleryIntent) -> Unit,
) {
    val showEmojiRail = !uiState.isSelectionMode

    if (showEmojiRail) {
        val showFavoritesChip = uiState.favoritesCount > 0
        val isFavoritesActive = uiState.filter is GalleryFilter.Favorites

        val activeEmojiFilter = remember(uiState.searchState.query, uniqueEmojis) {
            val query = uiState.searchState.query.trim()
            if (uniqueEmojis.any { it.first == query }) query else null
        }

        if (uniqueEmojis.isNotEmpty() || showFavoritesChip) {
            EmojiFilterRail(
                emojis = uniqueEmojis,
                activeFilter = activeEmojiFilter,
                onEmojiSelected = { emoji -> onIntent(GalleryIntent.UpdateSearchQuery(emoji)) },
                leadingContent = if (showFavoritesChip) {
                    {
                        item(key = "favorites_chip") {
                            FilterChip(
                                selected = isFavoritesActive,
                                onClick = {
                                    val newFilter = if (isFavoritesActive) {
                                        GalleryFilter.All
                                    } else {
                                        GalleryFilter.Favorites
                                    }
                                    onIntent(GalleryIntent.SetFilter(newFilter))
                                },
                                label = { Text(stringResource(R.string.gallery_chip_favorites)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isFavoritesActive) {
                                            Icons.Filled.Favorite
                                        } else {
                                            Icons.Outlined.FavoriteBorder
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("EmojiFilterRail"),
            )
        }
    }
}

/**
 * Floating search bar overlay with animated menu icon background.
 * The three-dots icon shows a circle background when the user scrolls.
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
private fun FloatingSearchBar(
    uiState: GalleryUiState,
    isScrolled: Boolean,
    showMenu: Boolean,
    onIntent: (GalleryIntent) -> Unit,
    onShowMenuChange: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuBgAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "menuBgAlpha",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Navigation icon (close for active filters when not searching)
        if (uiState.screenMode != ScreenMode.Searching &&
            uiState.filter !is GalleryFilter.All
        ) {
            IconButton(onClick = {
                onIntent(GalleryIntent.SetFilter(GalleryFilter.All))
            }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.gallery_cd_clear_filter),
                )
            }
        }

        // Search bar
        com.adsamcik.riposte.core.ui.component.SearchBar(
            query = uiState.searchState.query,
            onQueryChange = { onIntent(GalleryIntent.UpdateSearchQuery(it)) },
            onSearch = { /* debounce handles it */ },
            placeholder = stringResource(com.adsamcik.riposte.core.search.R.string.search_placeholder),
            onFocusChanged = { focused -> onIntent(GalleryIntent.SearchFieldFocusChanged(focused)) },
            modifier = Modifier.weight(1f),
        )

        // More options with animated circle background
        Box {
            IconButton(
                onClick = { onShowMenuChange(true) },
                modifier =
                    Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = menuBgAlpha),
                            shape = CircleShape,
                        ),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.gallery_cd_more_options),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onShowMenuChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.gallery_menu_select)) },
                    leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                    onClick = {
                        onIntent(GalleryIntent.EnterSelectionMode)
                        onShowMenuChange(false)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.gallery_menu_settings)) },
                    onClick = {
                        onNavigateToSettings()
                        onShowMenuChange(false)
                    },
                )
            }
        }
    }
}

/**
 * Individual meme grid item with selection support and hold-to-share gesture.
 *
 * In normal mode, tap opens the meme and holding for 600ms triggers quick share
 * with a circular progress overlay. In selection mode, tap toggles selection
 * with a circular check indicator and animated overlay.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemeGridItem(
    meme: Meme,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onIntent: (GalleryIntent) -> Unit,
    showEmojis: Boolean = false,
) {
    if (isSelectionMode) {
        SelectionModeGridItem(
            meme = meme,
            isSelected = isSelected,
            onIntent = onIntent,
            showEmojis = showEmojis,
        )
    } else {
        NormalModeGridItem(
            meme = meme,
            onIntent = onIntent,
            showEmojis = showEmojis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectionModeGridItem(
    meme: Meme,
    isSelected: Boolean,
    onIntent: (GalleryIntent) -> Unit,
    showEmojis: Boolean = false,
) {
    val selectedText = stringResource(com.adsamcik.riposte.core.ui.R.string.ui_state_selected)
    val notSelectedText = stringResource(com.adsamcik.riposte.core.ui.R.string.ui_state_not_selected)
    val interactionSource = remember { MutableInteractionSource() }
    val primaryColor = MaterialTheme.colorScheme.primary

    val overlayAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.25f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "overlayAlpha",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "borderWidth",
    )

    Box(
        modifier =
            Modifier
                .animatedPressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { onIntent(GalleryIntent.ToggleSelection(meme.id)) },
                    onLongClick = { /* No long click in selection mode */ },
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = if (isSelected) selectedText else notSelectedText
                    role = Role.Checkbox
                },
    ) {
        MemeCardCompact(
            meme = meme,
            showEmojis = showEmojis,
        )

        SelectionOverlay(
            isSelected = isSelected,
            overlayAlpha = overlayAlpha,
            borderWidth = borderWidth,
            primaryColor = primaryColor,
        )
    }
}

@Composable
private fun BoxScope.SelectionOverlay(
    isSelected: Boolean,
    overlayAlpha: Float,
    borderWidth: Dp,
    primaryColor: Color,
) {
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.6f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "checkScale",
    )
    val checkBgColor by animateColorAsState(
        targetValue = if (isSelected) primaryColor else MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
        animationSpec = tween(durationMillis = 150),
        label = "checkBgColor",
    )

    // Animated scrim overlay (respects card shape)
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .clip(RiposteShapes.MemeCard)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = overlayAlpha)),
    )

    // Primary color border for selected items
    if (borderWidth > 0.dp) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .border(
                        width = borderWidth,
                        color = primaryColor,
                        shape = RiposteShapes.MemeCard,
                    ),
        )
    }

    // Circular check indicator
    val selectionStateDescription = if (isSelected) {
        stringResource(R.string.gallery_cd_selected)
    } else {
        stringResource(R.string.gallery_cd_not_selected)
    }
    Box(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .size(24.dp)
                .semantics {
                    role = Role.Checkbox
                    stateDescription = selectionStateDescription
                }
                .graphicsLayer {
                    scaleX = checkScale
                    scaleY = checkScale
                }
                .background(
                    color = checkBgColor,
                    shape = CircleShape,
                )
                .then(
                    if (!isSelected) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.gallery_cd_selected),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NormalModeGridItem(
    meme: Meme,
    onIntent: (GalleryIntent) -> Unit,
    showEmojis: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier =
            Modifier
                .animatedPressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { onIntent(GalleryIntent.OpenMeme(meme.id)) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onIntent(GalleryIntent.QuickShare(meme.id))
                    },
                )
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                },
    ) {
        MemeCardCompact(
            meme = meme,
            showEmojis = showEmojis,
        )
    }
}

@Composable
private fun RecentSearchesHeader(
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(com.adsamcik.riposte.core.search.R.string.search_recent_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onClearAll) {
            Text(
                text = stringResource(com.adsamcik.riposte.core.search.R.string.search_recent_clear_all),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentSearchItem(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = stringResource(com.adsamcik.riposte.core.search.R.string.search_recent_icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(com.adsamcik.riposte.core.search.R.string.search_recent_delete),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Banner showing active import progress with an animated progress indicator.
 */
@Composable
private fun ImportProgressBanner(
    status: ImportWorkStatus,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = status is ImportWorkStatus.InProgress,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
    ) {
        val inProgress = status as? ImportWorkStatus.InProgress
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = {
                    if (inProgress != null && inProgress.total > 0) {
                        inProgress.completed.toFloat() / inProgress.total
                    } else {
                        0f
                    }
                },
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
            )
            Text(
                text =
                    if (inProgress != null) {
                        stringResource(R.string.gallery_import_in_progress, inProgress.completed, inProgress.total)
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Banner for one-shot notifications (import complete, indexing complete, etc.).
 * Slides down from below the search bar with a spring animation.
 * Uses M3 Expressive styling with Surface, rounded shape, and notification-type icons.
 */
@Composable
private fun NotificationBanner(
    notification: GalleryNotification?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialOffsetY = { -it },
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
        ) + fadeOut(),
    ) {
        val containerColor =
            when (notification) {
                is GalleryNotification.ImportComplete -> MaterialTheme.colorScheme.secondaryContainer
                is GalleryNotification.ImportFailed -> MaterialTheme.colorScheme.errorContainer
                is GalleryNotification.IndexingComplete -> MaterialTheme.colorScheme.tertiaryContainer
                null -> MaterialTheme.colorScheme.surface
            }
        val contentColor =
            when (notification) {
                is GalleryNotification.ImportComplete -> MaterialTheme.colorScheme.onSecondaryContainer
                is GalleryNotification.ImportFailed -> MaterialTheme.colorScheme.onErrorContainer
                is GalleryNotification.IndexingComplete -> MaterialTheme.colorScheme.onTertiaryContainer
                null -> MaterialTheme.colorScheme.onSurface
            }

        val icon =
            when (notification) {
                is GalleryNotification.ImportComplete -> Icons.Default.Check
                is GalleryNotification.ImportFailed -> Icons.Default.Close
                is GalleryNotification.IndexingComplete -> Icons.Default.Search
                null -> Icons.Default.Check
            }

        val text =
            when (notification) {
                is GalleryNotification.ImportComplete ->
                    if (notification.failed > 0) {
                        stringResource(
                            R.string.gallery_import_completed_with_errors,
                            notification.count,
                            notification.failed,
                        )
                    } else {
                        stringResource(R.string.gallery_import_completed, notification.count)
                    }
                is GalleryNotification.ImportFailed ->
                    stringResource(R.string.gallery_import_failed)
                is GalleryNotification.IndexingComplete ->
                    stringResource(R.string.gallery_indexing_complete, notification.count)
                null -> ""
            }

        Surface(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.gallery_cd_dismiss_notification),
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// region Previews

private val previewMeme =
    Meme(
        id = 1L,
        filePath = "/preview/meme1.jpg",
        fileName = "meme1.jpg",
        mimeType = "image/jpeg",
        width = 800,
        height = 600,
        fileSizeBytes = 150_000L,
        importedAt = System.currentTimeMillis(),
        emojiTags =
            listOf(
                com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("😂"),
                com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("🔥"),
            ),
        title = "Funny cat meme",
        isFavorite = true,
    )

private val previewMemes =
    listOf(
        previewMeme,
        previewMeme.copy(
            id = 2L,
            title = "Surprised Pikachu",
            emojiTags = listOf(com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("😮")),
        ),
        previewMeme.copy(id = 3L, title = "Drake meme", isFavorite = false),
    )

@Preview(name = "Loading", showBackground = true)
@Composable
private fun GalleryScreenLoadingPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        GalleryScreen(
            uiState = GalleryUiState(isLoading = true),
            onIntent = {},
            onNavigateToMeme = {},
            onNavigateToImport = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(name = "Content", showBackground = true)
@Preview(name = "Content Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GalleryScreenContentPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        GalleryScreen(
            uiState =
                GalleryUiState(
                    memes = previewMemes,
                    isLoading = false,
                    usePaging = false,
                    suggestions = previewMemes.take(2),
                    uniqueEmojis = listOf("😂" to 3, "🔥" to 2, "😮" to 1),
                ),
            onIntent = {},
            onNavigateToMeme = {},
            onNavigateToImport = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun GalleryScreenEmptyPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        GalleryScreen(
            uiState = GalleryUiState(isLoading = false, usePaging = false),
            onIntent = {},
            onNavigateToMeme = {},
            onNavigateToImport = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(name = "Search Results", showBackground = true)
@Composable
private fun GalleryScreenSearchResultsPreview() {
    com.adsamcik.riposte.core.ui.theme.RiposteTheme {
        GalleryScreen(
            uiState =
                GalleryUiState(
                    isLoading = false,
                    usePaging = false,
                    screenMode = ScreenMode.Searching,
                    searchState =
                        SearchSliceState(
                            query = "funny cat",
                            results = previewMemes.map { SearchResult(it, 0.9f, MatchType.HYBRID) },
                            hasSearched = true,
                            totalResultCount = 3,
                        ),
                ),
            onIntent = {},
            onNavigateToMeme = {},
            onNavigateToImport = {},
            onNavigateToSettings = {},
        )
    }
}

// endregion
