package com.mememymood.feature.search.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.mememymood.feature.search.R
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.SearchResult
import com.mememymood.core.ui.component.EmojiChip
import com.mememymood.core.ui.component.EmojiFilterRail
import com.mememymood.core.ui.component.EmptyState
import com.mememymood.core.ui.component.MemeCardCompact
import com.mememymood.core.ui.component.SearchResultsHeader
import com.mememymood.core.ui.modifier.animatedPressScale
import com.mememymood.core.ui.modifier.relevanceOpacity
import com.mememymood.core.ui.theme.rememberGridColumns
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMeme: (Long) -> Unit,
    onStartVoiceSearch: (() -> Unit)? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SearchEffect.NavigateToMeme -> onNavigateToMeme(effect.memeId)
                is SearchEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is SearchEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is SearchEffect.StartVoiceRecognition -> onStartVoiceSearch?.invoke()
                is SearchEffect.TriggerHapticFeedback -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Hoist string resources for accessibility
            val searchContentDescription = stringResource(R.string.search_content_description)
            val closeSearchDescription = stringResource(R.string.search_action_close)
            val voiceSearchDescription = stringResource(R.string.search_action_voice)
            val clearSearchDescription = stringResource(R.string.search_action_clear)

            // Enhanced Search bar with voice search
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.query,
                        onQueryChange = { viewModel.onIntent(SearchIntent.UpdateQuery(it)) },
                        onSearch = {
                            viewModel.onIntent(SearchIntent.Search)
                            isSearchActive = false
                            focusManager.clearFocus()
                        },
                        expanded = isSearchActive,
                        onExpandedChange = { isSearchActive = it },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = {
                            if (isSearchActive) {
                                IconButton(
                                    onClick = {
                                        isSearchActive = false
                                        focusManager.clearFocus()
                                        viewModel.onIntent(SearchIntent.ResetSearch)
                                    },
                                    modifier = Modifier.semantics { contentDescription = closeSearchDescription }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            } else {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Voice search button
                                AnimatedVisibility(
                                    visible = uiState.query.isEmpty() && onStartVoiceSearch != null,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    IconButton(
                                        onClick = { viewModel.onIntent(SearchIntent.StartVoiceSearch) },
                                        modifier = Modifier.semantics { contentDescription = voiceSearchDescription }
                                    ) {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = if (uiState.isVoiceSearchActive) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Search submit button — visible when query has text and bar is expanded
                                AnimatedVisibility(
                                    visible = uiState.query.isNotEmpty() && isSearchActive,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    IconButton(
                                        onClick = {
                                            viewModel.onIntent(SearchIntent.Search)
                                            isSearchActive = false
                                            focusManager.clearFocus()
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = stringResource(R.string.search_action_search),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                // Clear button
                                AnimatedVisibility(
                                    visible = uiState.query.isNotEmpty(),
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    IconButton(
                                        onClick = { viewModel.onIntent(SearchIntent.ResetSearch) },
                                        modifier = Modifier.semantics { contentDescription = clearSearchDescription }
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = null)
                                    }
                                }
                            }
                        },
                    )
                },
                expanded = isSearchActive,
                onExpandedChange = { isSearchActive = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSearchActive) 0.dp else 16.dp)
                    .semantics { contentDescription = searchContentDescription },
            ) {
                // Search suggestions content
                SearchSuggestionsContent(
                    suggestions = uiState.suggestions,
                    recentSearches = uiState.recentSearches,
                    onSuggestionClick = {
                        viewModel.onIntent(SearchIntent.SelectSuggestion(it))
                        isSearchActive = false
                        focusManager.clearFocus()
                    },
                    onRecentSearchClick = {
                        viewModel.onIntent(SearchIntent.SelectRecentSearch(it))
                        isSearchActive = false
                        focusManager.clearFocus()
                    },
                    onDeleteRecentSearch = { viewModel.onIntent(SearchIntent.DeleteRecentSearch(it)) },
                    onClearRecentSearches = { viewModel.onIntent(SearchIntent.ClearRecentSearches) },
                )
            }

            // Main content when search is not active
            if (!isSearchActive) {
                // Quick Filter Chips
                QuickFilterRow(
                    quickFilters = uiState.quickFilters,
                    selectedFilter = uiState.selectedQuickFilter,
                    onFilterClick = { viewModel.onIntent(SearchIntent.SelectQuickFilter(it)) },
                    onClearFilter = { viewModel.onIntent(SearchIntent.ClearQuickFilter) },
                )

                // Search mode selector
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SearchMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = uiState.searchMode == mode,
                            onClick = { viewModel.onIntent(SearchIntent.SetSearchMode(mode)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = SearchMode.entries.size,
                            ),
                            modifier = Modifier.semantics { 
                                contentDescription = context.getString(R.string.search_mode_content_description, mode.name.lowercase())
                            }
                        ) {
                            Text(
                                text = when (mode) {
                                    SearchMode.TEXT -> stringResource(R.string.search_mode_text)
                                    SearchMode.SEMANTIC -> stringResource(R.string.search_mode_ai)
                                    SearchMode.HYBRID -> stringResource(R.string.search_mode_hybrid)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                // Emoji filters
                EmojiFilterRail(
                    emojis = uiState.emojiCounts,
                    activeFilters = uiState.selectedEmojiFilters.toSet(),
                    onEmojiToggle = { viewModel.onIntent(SearchIntent.ToggleEmojiFilter(it)) },
                    onClearAll = { viewModel.onIntent(SearchIntent.ClearEmojiFilters) },
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                // Results toolbar with sort/view options
                AnimatedVisibility(
                    visible = uiState.hasSearched && uiState.results.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SearchResultsToolbar(
                        resultSummary = uiState.resultSummary,
                        sortOrder = uiState.sortOrder,
                        viewMode = uiState.viewMode,
                        onSortOrderChange = { viewModel.onIntent(SearchIntent.SetSortOrder(it)) },
                        onViewModeChange = { viewModel.onIntent(SearchIntent.SetViewMode(it)) },
                    )
                }

                // Loading indicator with animation
                AnimatedVisibility(
                    visible = uiState.isSearching,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        // Shimmer loading placeholders
                        ShimmerSearchResults()
                    }
                }

                // Results or empty state with animated transitions
                AnimatedContent(
                    targetState = Triple(uiState.hasSearched, uiState.results.isEmpty(), uiState.isSearching),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "search_content_transition"
                ) { (hasSearched, isEmpty, isSearching) ->
                    when {
                        isSearching -> {
                            // Already showing shimmer above
                            Box(Modifier.fillMaxSize())
                        }
                        !hasSearched && uiState.recentSearches.isNotEmpty() -> {
                            RecentSearchesSection(
                                recentSearches = uiState.recentSearches,
                                onSearchClick = { viewModel.onIntent(SearchIntent.SelectRecentSearch(it)) },
                                onDeleteSearch = { viewModel.onIntent(SearchIntent.DeleteRecentSearch(it)) },
                                onClearAll = { viewModel.onIntent(SearchIntent.ClearRecentSearches) },
                            )
                        }
                        !hasSearched -> {
                            SearchEmptyPrompt()
                        }
                        isEmpty -> {
                            val filterLabel = uiState.selectedQuickFilter?.let {
                                stringResource(it.labelResId)
                            }
                            val displayQuery = uiState.query.ifEmpty { filterLabel ?: "" }
                            EmptyState(
                                icon = "🔍",
                                title = stringResource(R.string.search_no_results_title),
                                message = if (displayQuery.isNotEmpty()) {
                                    stringResource(R.string.search_no_results_description, displayQuery)
                                } else {
                                    stringResource(R.string.search_no_results_title)
                                },
                                actionLabel = stringResource(R.string.search_no_results_clear_filters),
                                onAction = {
                                    viewModel.onIntent(SearchIntent.ResetSearch)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        else -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                SearchResultsHeader(
                                    query = uiState.query,
                                    resultCount = uiState.results.size,
                                )
                                when (uiState.viewMode) {
                                    SearchViewMode.GRID -> SearchResultsGrid(
                                        results = uiState.results,
                                        query = uiState.query,
                                        onMemeClick = { viewModel.onIntent(SearchIntent.MemeClicked(it.meme)) },
                                    )
                                    SearchViewMode.LIST -> SearchResultsList(
                                        results = uiState.results,
                                        query = uiState.query,
                                        onMemeClick = { viewModel.onIntent(SearchIntent.MemeClicked(it.meme)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickFilterRow(
    quickFilters: List<QuickFilter>,
    selectedFilter: QuickFilter?,
    onFilterClick: (QuickFilter) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(quickFilters) { filter ->
            val isSelected = selectedFilter?.id == filter.id
            AssistChip(
                onClick = {
                    if (isSelected) onClearFilter() else onFilterClick(filter)
                },
                label = { Text(stringResource(filter.labelResId)) },
                leadingIcon = { Text(filter.emoji) },
                colors = if (isSelected) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
                modifier = Modifier.semantics { 
                    contentDescription = context.getString(
                        if (isSelected) R.string.search_quick_filter_selected_content_description 
                        else R.string.search_quick_filter_content_description,
                        context.getString(filter.labelResId)
                    )
                }
            )
        }
    }
}

@Composable
private fun SearchResultsToolbar(
    resultSummary: String,
    sortOrder: SearchSortOrder,
    viewMode: SearchViewMode,
    onSortOrderChange: (SearchSortOrder) -> Unit,
    onViewModeChange: (SearchViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showSortMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Result summary with animation
        AnimatedContent(
            targetState = resultSummary,
            transitionSpec = { 
                slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut() 
            },
            label = "result_count_animation"
        ) { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Sort dropdown
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.semantics { contentDescription = context.getString(R.string.search_results_sort_content_description, sortOrder.label) }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    SearchSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    order.label,
                                    fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSortOrderChange(order)
                                showSortMenu = false
                            },
                        )
                    }
                }
            }
            
            // View mode toggle
            IconToggleButton(
                checked = viewMode == SearchViewMode.LIST,
                onCheckedChange = { isListMode ->
                    onViewModeChange(if (isListMode) SearchViewMode.LIST else SearchViewMode.GRID)
                },
                modifier = Modifier.semantics { 
                    contentDescription = context.getString(
                        if (viewMode == SearchViewMode.GRID) R.string.search_results_view_switch_list 
                        else R.string.search_results_view_switch_grid
                    )
                }
            ) {
                Icon(
                    if (viewMode == SearchViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSuggestionsContent(
    suggestions: List<String>,
    recentSearches: List<String>,
    onSuggestionClick: (String) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onDeleteRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (suggestions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.search_suggestions_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { heading() },
                )
            }
            items(suggestions) { suggestion ->
                ListItem(
                    headlineContent = { Text(suggestion) },
                    leadingContent = {
                        Icon(
                            Icons.Default.TrendingUp, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .clickable { onSuggestionClick(suggestion) }
                        .semantics { contentDescription = context.getString(R.string.search_suggestions_content_description, suggestion) },
                )
            }
        }

        if (recentSearches.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.search_recent_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearRecentSearches) {
                        Text(stringResource(R.string.search_recent_clear_all))
                    }
                }
            }
            items(recentSearches.take(5)) { search ->
                val recentSearchDescription = stringResource(R.string.search_recent_item_content_description, search)
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { dismissValue ->
                        if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                            onDeleteRecentSearch(search)
                            true
                        } else {
                            false
                        }
                    }
                )
                
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_recent_delete),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    },
                    enableDismissFromStartToEnd = false,
                ) {
                    Surface {
                        ListItem(
                            headlineContent = { Text(search) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.History, 
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier
                                .clickable { onRecentSearchClick(search) }
                                .semantics { contentDescription = recentSearchDescription },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onDeleteSearch: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clearAllDescription = stringResource(R.string.search_recent_clear_all_content_description)
    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_recent_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            TextButton(
                onClick = onClearAll,
                modifier = Modifier.semantics { contentDescription = clearAllDescription }
            ) {
                Text(stringResource(R.string.search_recent_clear_all))
            }
        }
        Spacer(Modifier.height(8.dp))
        recentSearches.forEach { search ->
            val recentSearchDescription = stringResource(R.string.search_recent_item_content_description, search)
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                        onDeleteSearch(search)
                        true
                    } else {
                        false
                    }
                }
            )
            
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_recent_delete),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                },
                enableDismissFromStartToEnd = false,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 1.dp,
                ) {
                    ListItem(
                        headlineContent = { Text(search) },
                        leadingContent = {
                            Icon(
                                Icons.Default.History, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSearchClick(search) }
                            .semantics { contentDescription = recentSearchDescription },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SearchEmptyPrompt(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.search_empty_icon),
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.search_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.search_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        // Search tips
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.search_tips_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                SearchTip(stringResource(R.string.search_tip_keywords))
                SearchTip(stringResource(R.string.search_tip_ai_mode))
                SearchTip(stringResource(R.string.search_tip_emoji_filter))
            }
        }
    }
}

@Composable
private fun SearchTip(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShimmerSearchResults(
    modifier: Modifier = Modifier,
) {
    val columns = rememberGridColumns()
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    )
    
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(shimmerProgress * 1000f - 500f, 0f),
        end = Offset(shimmerProgress * 1000f, 0f)
    )
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.height(400.dp),
        userScrollEnabled = false,
    ) {
        items(columns * 2) {
            ShimmerCard(brush = brush)
        }
    }
}

@Composable
private fun ShimmerCard(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(brush)
            )
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }
    }
}

@Composable
private fun SearchResultsGrid(
    results: List<SearchResult>,
    query: String,
    onMemeClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = rememberGridColumns()
    val totalResults = results.size
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = results,
            key = { _, result -> result.meme.id },
        ) { index, result ->
            SearchResultCard(
                result = result,
                query = query,
                onClick = { onMemeClick(result) },
                modifier = Modifier
                    .animateItem()
                    .relevanceOpacity(rank = index, total = totalResults)
                    .animatedPressScale(),
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    query: String,
    onMemeClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalResults = results.size
    
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = results,
            key = { _, result -> result.meme.id },
        ) { index, result ->
            SearchResultListItem(
                result = result,
                query = query,
                onClick = { onMemeClick(result) },
                modifier = Modifier
                    .animateItem()
                    .relevanceOpacity(rank = index, total = totalResults),
            )
        }
    }
}

@Composable
private fun SearchResultListItem(
    result: SearchResult,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relevancePercent = (result.relevanceScore * 100).toInt()
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            AsyncImage(
                model = java.io.File(result.meme.filePath),
                contentDescription = result.meme.title ?: result.meme.fileName,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = result.meme.title ?: result.meme.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                
                result.meme.description?.let { desc ->
                    HighlightedText(
                        text = desc,
                        query = query,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
                
                // Emoji tags
                if (result.meme.emojiTags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        result.meme.emojiTags.take(5).forEach { tag ->
                            Text(
                                text = tag.emoji,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (result.meme.emojiTags.size > 5) {
                            Text(
                                text = "+${result.meme.emojiTags.size - 5}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            
            // Relevance badge
            RelevanceBadge(
                percent = relevancePercent,
                modifier = Modifier.align(Alignment.Top)
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val relevancePercent = (result.relevanceScore * 100).toInt()
    
    Box(
        modifier = modifier.semantics { 
            contentDescription = context.getString(
                R.string.search_result_card_content_description,
                result.meme.title ?: result.meme.fileName,
                relevancePercent
            )
        }
    ) {
        MemeCardCompact(
            meme = result.meme,
            onClick = onClick,
        )
        
        // Relevance badge overlay
        RelevanceBadge(
            percent = relevancePercent,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
        )
    }
}

@Composable
private fun RelevanceBadge(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when {
        percent >= 80 -> MaterialTheme.colorScheme.primary
        percent >= 50 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    
    Box(
        modifier = modifier
            .background(
                color = backgroundColor.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "${percent}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: androidx.compose.ui.text.TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    val annotatedString = remember(text, query) {
        buildAnnotatedString {
            if (query.isBlank()) {
                append(text)
                return@buildAnnotatedString
            }
            
            var currentIndex = 0
            val lowercaseText = text.lowercase()
            val lowercaseQuery = query.lowercase()
            
            while (currentIndex < text.length) {
                val matchIndex = lowercaseText.indexOf(lowercaseQuery, currentIndex)
                if (matchIndex == -1) {
                    append(text.substring(currentIndex))
                    break
                }
                
                // Append text before match
                if (matchIndex > currentIndex) {
                    append(text.substring(currentIndex, matchIndex))
                }
                
                // Append highlighted match
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        background = Color(0x40FFEB3B)
                    )
                ) {
                    append(text.substring(matchIndex, matchIndex + query.length))
                }
                
                currentIndex = matchIndex + query.length
            }
        }
    }
    
    Text(
        text = annotatedString,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
