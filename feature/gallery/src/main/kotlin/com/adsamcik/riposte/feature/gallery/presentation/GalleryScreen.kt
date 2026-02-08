package com.adsamcik.riposte.feature.gallery.presentation

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.adsamcik.riposte.core.model.Meme
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.derivedStateOf
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
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.ui.component.EmptyState
import com.adsamcik.riposte.core.ui.component.EmojiFilterRail
import com.adsamcik.riposte.core.ui.component.ErrorState
import com.adsamcik.riposte.core.ui.component.LoadingScreen
import com.adsamcik.riposte.core.ui.component.MemeCardCompact
import com.adsamcik.riposte.core.ui.modifier.animatedPressScale
import com.adsamcik.riposte.core.ui.theme.rememberGridColumns
import com.adsamcik.riposte.feature.gallery.R
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToImport: () -> Unit,
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
                    context.startActivity(android.content.Intent.createChooser(effect.intent, context.getString(R.string.gallery_share_chooser_title)))
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
                is GalleryEffect.TriggerHapticFeedback -> { /* Handled by Compose haptic feedback */ }
            }
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
        com.adsamcik.riposte.core.ui.component.QuickShareBottomSheet(
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
                    if (uiState.isSelectionMode) {
                        Text(pluralStringResource(R.plurals.gallery_selected_count, uiState.selectionCount, uiState.selectionCount))
                    } else {
                        com.adsamcik.riposte.core.ui.component.SearchBar(
                            query = uiState.searchState.query,
                            onQueryChange = { onIntent(GalleryIntent.UpdateSearchQuery(it)) },
                            onSearch = { /* debounce handles it */ },
                            placeholder = stringResource(com.adsamcik.riposte.core.search.R.string.search_placeholder),
                        )
                    }
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { onIntent(GalleryIntent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.gallery_cd_cancel_selection))
                        }
                    } else if (uiState.screenMode == ScreenMode.Searching) {
                        IconButton(onClick = { onIntent(GalleryIntent.ClearSearch) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.gallery_cd_clear_filter))
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
                uiState.screenMode == ScreenMode.Searching -> {
                    GalleryContent(
                        uiState = uiState,
                        uniqueEmojis = uiState.uniqueEmojis,
                        onIntent = onIntent,
                        columns = columns,
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
                        // Loading indicator
                        else if (uiState.searchState.isSearching) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        // Error state
                        else if (uiState.searchState.errorMessage != null) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search_error") {
                                ErrorState(
                                    message = uiState.searchState.errorMessage.orEmpty(),
                                    onRetry = { onIntent(GalleryIntent.UpdateSearchQuery(uiState.searchState.query)) },
                                )
                            }
                        }
                        // No results
                        else if (uiState.searchState.hasSearched && uiState.searchState.results.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search_no_results") {
                                EmptyState(
                                    icon = "🔍",
                                    title = stringResource(com.adsamcik.riposte.core.search.R.string.search_no_results_title),
                                    message = stringResource(com.adsamcik.riposte.core.search.R.string.search_no_results_description, uiState.searchState.query),
                                )
                            }
                        }
                        // Search results
                        else if (uiState.searchState.results.isNotEmpty()) {
                            // Results header
                            item(span = { GridItemSpan(maxLineSpan) }, key = "search_results_header") {
                                com.adsamcik.riposte.core.ui.component.SearchResultsHeader(
                                    query = uiState.searchState.query,
                                    resultCount = uiState.searchState.totalResultCount,
                                )
                            }
                            // Result items - reuse MemeGridItem
                            items(
                                items = uiState.searchState.results,
                                key = { "search_${it.meme.id}" },
                            ) { result ->
                                val isSelected = result.meme.id in uiState.selectedMemeIds
                                val memeDescription = result.meme.title ?: result.meme.fileName
                                MemeGridItem(
                                    meme = result.meme,
                                    isSelected = isSelected,
                                    isSelectionMode = uiState.isSelectionMode,
                                    memeDescription = memeDescription,
                                    onIntent = onIntent,
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
                                message = error.message ?: stringResource(R.string.gallery_error_load_failed),
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

                            // Detect emoji group boundaries for section headers (paged)
                            val emojiSectionHeaders = remember(filteredMemeIndices, uiState.sortOption) {
                                if (uiState.sortOption != SortOption.EmojiGroup) emptyMap()
                                else {
                                    val headers = mutableMapOf<Int, Pair<String, Int>>()
                                    var currentEmoji: String? = null
                                    var currentStartIdx = 0
                                    var currentCount = 0
                                    filteredMemeIndices.forEachIndexed { filteredIdx, originalIdx ->
                                        val meme = pagedMemes.peek(originalIdx)
                                        val emoji = meme?.emojiTags?.firstOrNull()?.emoji ?: "❓"
                                        if (emoji != currentEmoji) {
                                            if (currentEmoji != null) {
                                                headers[currentStartIdx] = currentEmoji!! to currentCount
                                            }
                                            currentEmoji = emoji
                                            currentStartIdx = filteredIdx
                                            currentCount = 1
                                        } else {
                                            currentCount++
                                        }
                                    }
                                    if (currentEmoji != null) {
                                        headers[currentStartIdx] = currentEmoji!! to currentCount
                                    }
                                    headers
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
                                if (uiState.sortOption == SortOption.EmojiGroup && emojiSectionHeaders.isNotEmpty()) {
                                    filteredMemeIndices.forEachIndexed { filteredIndex, originalIndex ->
                                        // Insert header before group start
                                        emojiSectionHeaders[filteredIndex]?.let { (emoji, count) ->
                                            item(
                                                span = { GridItemSpan(maxLineSpan) },
                                                key = "emoji_header_$emoji",
                                            ) {
                                                EmojiSectionHeader(emoji = emoji, count = count)
                                            }
                                        }
                                        // Regular meme item
                                        item(
                                            key = pagedMemes.peek(originalIndex)?.id ?: originalIndex,
                                        ) {
                                            val meme = pagedMemes[originalIndex]
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
                                } else {
                                    // Existing flat rendering
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

                                // Paging append loading indicator
                                if (loadState.append is LoadState.Loading) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "append_loading",
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(32.dp),
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
                    val suggestionIds = remember(uiState.suggestions) {
                        uiState.suggestions.map { it.id }.toSet()
                    }
                    val nonSuggestionMemes = remember(uiState.filteredMemes, suggestionIds) {
                        uiState.filteredMemes.filter { it.id !in suggestionIds }
                    }

                    // Compute grouped memes for EmojiGroup sort
                    val emojiGroups = remember(nonSuggestionMemes, uiState.sortOption) {
                        if (uiState.sortOption == SortOption.EmojiGroup) {
                            nonSuggestionMemes.groupBy { meme ->
                                meme.emojiTags.firstOrNull()?.emoji ?: "❓"
                            }
                        } else {
                            emptyMap()
                        }
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
                        if (uiState.sortOption == SortOption.EmojiGroup && emojiGroups.isNotEmpty()) {
                            emojiGroups.forEach { (emoji, groupMemes) ->
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "emoji_header_$emoji",
                                ) {
                                    EmojiSectionHeader(emoji = emoji, count = groupMemes.size)
                                }
                                items(
                                    items = groupMemes,
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
                        } else {
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
    val gridState = rememberLazyGridState()

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

        Box {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = gridContent,
            )

            // Floating section indicator when scrolling in EmojiGroup sort
            if (uiState.sortOption == SortOption.EmojiGroup) {
                EmojiSectionScrollIndicator(
                    gridState = gridState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * Floating overlay that shows the current emoji section while scrolling in EmojiGroup sort.
 * Derives the current section from visible grid item keys (headers use "emoji_header_X" keys).
 * Fades in during scroll and fades out after scrolling stops.
 */
@Composable
private fun EmojiSectionScrollIndicator(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    var lastKnownEmoji by remember { mutableStateOf<String?>(null) }
    var showIndicator by remember { mutableStateOf(false) }

    val currentEmoji by remember {
        derivedStateOf {
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            // Find the topmost header that has scrolled to or past the top of the viewport
            visibleItems
                .filter { item ->
                    val key = item.key as? String ?: return@filter false
                    key.startsWith("emoji_header_")
                }
                .lastOrNull { it.offset.y <= 0 }
                ?.let { (it.key as String).removePrefix("emoji_header_") }
                // If no header scrolled past top, take the first visible header
                ?: visibleItems.firstOrNull { item ->
                    val key = item.key as? String ?: return@firstOrNull false
                    key.startsWith("emoji_header_")
                }?.let { (it.key as String).removePrefix("emoji_header_") }
        }
    }

    val isScrolling by remember {
        derivedStateOf { gridState.isScrollInProgress }
    }

    // Show during scroll, hide after a delay
    LaunchedEffect(isScrolling, currentEmoji) {
        if (isScrolling && currentEmoji != null) {
            lastKnownEmoji = currentEmoji
            showIndicator = true
        } else if (!isScrolling) {
            kotlinx.coroutines.delay(800L)
            showIndicator = false
        }
    }

    AnimatedVisibility(
        visible = showIndicator && lastKnownEmoji != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(top = 8.dp),
    ) {
        lastKnownEmoji?.let { emoji ->
            Text(
                text = emoji,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
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
    val selectedText = stringResource(com.adsamcik.riposte.core.ui.R.string.ui_state_selected)
    val notSelectedText = stringResource(com.adsamcik.riposte.core.ui.R.string.ui_state_not_selected)

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

/**
 * Full-width section header showing emoji and meme count for EmojiGroup sort.
 */
@Composable
private fun EmojiSectionHeader(
    emoji: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentSearchesHeader(
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(com.adsamcik.riposte.core.search.R.string.search_recent_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClearAll) {
            Text(
                text = stringResource(com.adsamcik.riposte.core.search.R.string.search_recent_clear_all),
                style = MaterialTheme.typography.labelMedium,
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
        modifier = modifier
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
                imageVector = Icons.Default.Search,
                contentDescription = null,
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
        com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("😂"),
        com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("🔥"),
    ),
    title = "Funny cat meme",
    isFavorite = true,
)

private val previewMemes = listOf(
    previewMeme,
    previewMeme.copy(id = 2L, title = "Surprised Pikachu", emojiTags = listOf(com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("😮"))),
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
            uiState = GalleryUiState(
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
            uiState = GalleryUiState(
                isLoading = false,
                usePaging = false,
                screenMode = ScreenMode.Searching,
                searchState = SearchSliceState(
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
