package com.mememymood.feature.search.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.SearchResult
import com.mememymood.core.ui.component.EmojiChip
import com.mememymood.core.ui.component.EmptyState
import com.mememymood.core.ui.component.MemeCardCompact
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMeme: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SearchEffect.NavigateToMeme -> onNavigateToMeme(effect.memeId)
                is SearchEffect.ShowError -> { /* Show snackbar */ }
                is SearchEffect.ShowSnackbar -> { /* Show snackbar */ }
            }
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Search bar
            SearchBar(
                query = uiState.query,
                onQueryChange = { viewModel.onIntent(SearchIntent.UpdateQuery(it)) },
                onSearch = { viewModel.onIntent(SearchIntent.Search) },
                active = isSearchActive,
                onActiveChange = { isSearchActive = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSearchActive) 0.dp else 16.dp),
                placeholder = { Text("Search memes...") },
                leadingIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.onIntent(SearchIntent.ClearQuery)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onIntent(SearchIntent.ClearQuery) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
            ) {
                // Search suggestions content
                SearchSuggestionsContent(
                    suggestions = uiState.suggestions,
                    recentSearches = uiState.recentSearches,
                    onSuggestionClick = { viewModel.onIntent(SearchIntent.SelectSuggestion(it)) },
                    onRecentSearchClick = { viewModel.onIntent(SearchIntent.SelectRecentSearch(it)) },
                    onClearRecentSearches = { viewModel.onIntent(SearchIntent.ClearRecentSearches) },
                )
            }

            // Search mode selector
            if (!isSearchActive) {
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
                        ) {
                            Text(
                                text = when (mode) {
                                    SearchMode.TEXT -> "Text"
                                    SearchMode.SEMANTIC -> "AI"
                                    SearchMode.HYBRID -> "Hybrid"
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                // Emoji filters
                EmojiFilterRow(
                    selectedEmojis = uiState.selectedEmojiFilters,
                    onEmojiToggle = { viewModel.onIntent(SearchIntent.ToggleEmojiFilter(it)) },
                    onClearFilters = { viewModel.onIntent(SearchIntent.ClearEmojiFilters) },
                )

                // Loading indicator
                AnimatedVisibility(visible = uiState.isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }

                // Results or empty state
                when {
                    !uiState.hasSearched && uiState.recentSearches.isNotEmpty() -> {
                        RecentSearchesSection(
                            recentSearches = uiState.recentSearches,
                            onSearchClick = { viewModel.onIntent(SearchIntent.SelectRecentSearch(it)) },
                            onClearAll = { viewModel.onIntent(SearchIntent.ClearRecentSearches) },
                        )
                    }

                    !uiState.hasSearched -> {
                        SearchEmptyPrompt()
                    }

                    uiState.results.isEmpty() && uiState.hasSearched -> {
                        EmptyState(
                            emoji = "🔍",
                            title = "No Results Found",
                            message = "Try different keywords or adjust your filters",
                        )
                    }

                    else -> {
                        SearchResultsGrid(
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

@Composable
private fun EmojiFilterRow(
    selectedEmojis: List<String>,
    onEmojiToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Filter by Emoji",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedEmojis.isNotEmpty()) {
                TextButton(onClick = onClearFilters) {
                    Text("Clear (${selectedEmojis.size})")
                }
            }
        }

        val commonEmojis = listOf(
            "😂" to "Joy", "❤️" to "Heart", "🔥" to "Fire",
            "😍" to "Heart Eyes", "🤣" to "ROFL", "😊" to "Blush",
            "🙏" to "Pray", "😭" to "Crying", "😘" to "Kiss",
            "👍" to "Thumbs Up", "💯" to "100", "🎉" to "Party",
            "😎" to "Cool", "🥺" to "Pleading", "✨" to "Sparkles"
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(commonEmojis) { (emoji, _) ->
                FilterChip(
                    selected = emoji in selectedEmojis,
                    onClick = { onEmojiToggle(emoji) },
                    label = { Text(emoji) },
                )
            }
        }
    }
}

@Composable
private fun SearchSuggestionsContent(
    suggestions: List<String>,
    recentSearches: List<String>,
    onSuggestionClick: (String) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (suggestions.isNotEmpty()) {
            Text(
                text = "Suggestions",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            suggestions.forEach { suggestion ->
                ListItem(
                    headlineContent = { Text(suggestion) },
                    leadingContent = {
                        Icon(Icons.Default.TrendingUp, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onSuggestionClick(suggestion) },
                )
            }
        }

        if (recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = onClearRecentSearches) {
                    Text("Clear All")
                }
            }
            recentSearches.take(5).forEach { search ->
                ListItem(
                    headlineContent = { Text(search) },
                    leadingContent = {
                        Icon(Icons.Default.History, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onRecentSearchClick(search) },
                )
            }
        }
    }
}

@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent Searches",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onClearAll) {
                Text("Clear All")
            }
        }
        Spacer(Modifier.height(8.dp))
        recentSearches.forEach { search ->
            ListItem(
                headlineContent = { Text(search) },
                leadingContent = {
                    Icon(Icons.Default.History, contentDescription = null)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSearchClick(search) },
            )
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
            text = "🔍",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Search Your Memes",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Find memes by text, description, or use AI-powered semantic search",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultsGrid(
    results: List<SearchResult>,
    query: String,
    onMemeClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(results, key = { it.meme.id }) { result ->
            SearchResultCard(
                result = result,
                query = query,
                onClick = { onMemeClick(result) },
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
    val relevancePercent = (result.relevanceScore * 100).toInt()
    
    Box(modifier = modifier) {
        MemeCardCompact(
            meme = result.meme,
            onClick = onClick,
        )
        
        // Relevance badge overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(
                    color = when {
                        relevancePercent >= 80 -> MaterialTheme.colorScheme.primary
                        relevancePercent >= 50 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiary
                    }.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "${relevancePercent}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
