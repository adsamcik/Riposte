package com.mememymood.feature.search.presentation

import android.content.Context
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.common.suggestion.GetSuggestionsUseCase
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.MatchType
import com.mememymood.core.model.Meme
import com.mememymood.core.model.SearchResult
import com.mememymood.feature.search.domain.usecase.SearchUseCases
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var searchUseCases: SearchUseCases
    private lateinit var getSuggestionsUseCase: GetSuggestionsUseCase
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var viewModel: SearchViewModel

    private val testMemes = listOf(
        createTestMeme(1, "meme1.jpg"),
        createTestMeme(2, "meme2.jpg"),
        createTestMeme(3, "meme3.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😂")))
    )

    private val testSearchResults = testMemes.mapIndexed { index, meme ->
        SearchResult(
            meme = meme,
            relevanceScore = 1.0f - (index * 0.1f),
            matchType = MatchType.TEXT
        )
    }

    private val recentSearches = listOf("funny", "cat", "dog")
    private val suggestions = listOf("funny meme", "funny cat", "funny dog")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        searchUseCases = mockk()
        getSuggestionsUseCase = GetSuggestionsUseCase()
        preferencesDataStore = mockk()

        // Default mock setup
        every { preferencesDataStore.hasShownSearchTip } returns flowOf(true)
        coEvery { preferencesDataStore.setSearchTipShown() } returns Unit

        // Default mock setup
        every { searchUseCases.getRecentSearches() } returns flowOf(recentSearches)
        every { searchUseCases.getEmojiCounts() } returns flowOf(emptyList())
        every { searchUseCases.search(any()) } returns flowOf(testSearchResults)
        coEvery { searchUseCases.semanticSearch(any(), any()) } returns testSearchResults
        coEvery { searchUseCases.hybridSearch(any(), any()) } returns testSearchResults
        coEvery { searchUseCases.getSearchSuggestions(any()) } returns suggestions
        every { searchUseCases.getAllMemes() } returns flowOf(testMemes)
        every { searchUseCases.getFavoriteMemes() } returns flowOf(testSearchResults)
        every { searchUseCases.getRecentMemes() } returns flowOf(testSearchResults)
        coEvery { searchUseCases.addRecentSearch(any()) } returns Unit
        coEvery { searchUseCases.deleteRecentSearch(any()) } returns Unit
        coEvery { searchUseCases.clearRecentSearches() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SearchViewModel {
        return SearchViewModel(context, searchUseCases, getSuggestionsUseCase, preferencesDataStore, testDispatcher)
    }

    // region Initialization Tests

    @Test
    fun `initial state has default values`() = runTest {
        viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertThat(state.query).isEmpty()
        assertThat(state.searchMode).isEqualTo(SearchMode.HYBRID)
        assertThat(state.results).isEmpty()
        assertThat(state.isSearching).isFalse()
        assertThat(state.hasSearched).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `loads recent searches on initialization`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.recentSearches).isEqualTo(recentSearches)
    }

    // endregion

    // region UpdateQuery Intent Tests

    @Test
    fun `UpdateQuery updates query in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.query).isEqualTo("test")
    }

    @Test
    fun `UpdateQuery triggers debounced search`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350) // Debounce is 300ms
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasSearched).isTrue()
        assertThat(viewModel.uiState.value.results).isNotEmpty()
    }

    @Test
    fun `UpdateQuery with blank query clears results`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // First perform a search
        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.results).isNotEmpty()

        // Then clear
        viewModel.onIntent(SearchIntent.UpdateQuery(""))
        advanceTimeBy(350)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.results).isEmpty()
        assertThat(viewModel.uiState.value.hasSearched).isFalse()
    }

    @Test
    fun `UpdateQuery loads suggestions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("funny"))
        advanceTimeBy(350)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.suggestions).isEqualTo(suggestions)
    }

    // endregion

    // region Search Mode Tests

    @Test
    fun `SetSearchMode updates mode in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SetSearchMode(SearchMode.TEXT))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.searchMode).isEqualTo(SearchMode.TEXT)
    }

    @Test
    fun `SetSearchMode to TEXT uses text search`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SetSearchMode(SearchMode.TEXT))
        advanceUntilIdle()

        verify { searchUseCases.search("test") }
    }

    @Test
    fun `SetSearchMode to SEMANTIC uses semantic search`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SetSearchMode(SearchMode.SEMANTIC))
        advanceUntilIdle()

        coVerify { searchUseCases.semanticSearch("test", any()) }
    }

    @Test
    fun `SetSearchMode to HYBRID uses hybrid search`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SetSearchMode(SearchMode.TEXT))
        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SetSearchMode(SearchMode.HYBRID))
        advanceUntilIdle()

        coVerify { searchUseCases.hybridSearch("test", any()) }
    }

    // endregion

    // region Search Intent Tests

    @Test
    fun `Search performs search and adds to recent`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test query"))
        viewModel.onIntent(SearchIntent.Search)
        advanceUntilIdle()

        coVerify { searchUseCases.addRecentSearch("test query") }
    }

    @Test
    fun `Search sets hasSearched true immediately`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test query"))
        viewModel.onIntent(SearchIntent.Search)

        assertThat(viewModel.uiState.value.hasSearched).isTrue()
    }

    @Test
    fun `Search with blank query does nothing`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.Search)
        advanceUntilIdle()

        coVerify(exactly = 0) { searchUseCases.addRecentSearch(any()) }
    }

    // endregion

    // region ClearQuery Intent Tests

    @Test
    fun `ClearQuery clears query and results`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ClearQuery)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.query).isEmpty()
        assertThat(state.results).isEmpty()
        assertThat(state.suggestions).isEmpty()
        assertThat(state.hasSearched).isFalse()
    }

    // endregion

    // region Emoji Filter Tests

    @Test
    fun `ToggleEmojiFilter adds emoji to filters`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("😂"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedEmojiFilters).contains("😂")
    }

    @Test
    fun `ToggleEmojiFilter removes existing emoji from filters`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("😂"))
        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("😂"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedEmojiFilters).doesNotContain("😂")
    }

    @Test
    fun `ClearEmojiFilters removes all filters`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("😂"))
        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("😀"))
        viewModel.onIntent(SearchIntent.ClearEmojiFilters)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedEmojiFilters).isEmpty()
    }

    @Test
    fun `emoji filter applies to search results`() = runTest {
        val resultsWithEmoji = listOf(
            SearchResult(
                meme = createTestMeme(1, "meme1.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😂"))),
                relevanceScore = 1.0f,
                matchType = MatchType.TEXT
            ),
            SearchResult(
                meme = createTestMeme(2, "meme2.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😀"))),
                relevanceScore = 0.9f,
                matchType = MatchType.TEXT
            )
        )
        coEvery { searchUseCases.hybridSearch(any(), any()) } returns resultsWithEmoji

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("😂"))
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertThat(results).hasSize(1)
        assertThat(results[0].meme.emojiTags[0].emoji).isEqualTo("😂")
    }

    // endregion

    // region Emoji Filter With Empty Query Tests

    @Test
    fun `ToggleEmojiFilter with empty query triggers search results`() = runTest {
        val memesWithEmoji = listOf(
            createTestMeme(1, "meme1.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😂"))),
            createTestMeme(2, "meme2.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😀"))),
            createTestMeme(3, "meme3.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😂"))),
        )
        every { searchUseCases.getAllMemes() } returns flowOf(memesWithEmoji)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Toggle emoji filter with no text query
        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("😂"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.hasSearched).isTrue()
        assertThat(state.results).hasSize(2)
        assertThat(state.results.all { result ->
            result.meme.emojiTags.any { it.emoji == "😂" }
        }).isTrue()
    }

    @Test
    fun `ClearEmojiFilters resets hasSearched when query is empty`() = runTest {
        val memesWithEmoji = listOf(
            createTestMeme(1, "meme1.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😂"))),
        )
        every { searchUseCases.getAllMemes() } returns flowOf(memesWithEmoji)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Toggle emoji filter to trigger search
        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("😂"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.hasSearched).isTrue()

        // Clear filters — should reset hasSearched
        viewModel.onIntent(SearchIntent.ClearEmojiFilters)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.hasSearched).isFalse()
        assertThat(state.results).isEmpty()
        assertThat(state.selectedEmojiFilters).isEmpty()
    }

    @Test
    fun `Search intent triggers performSearch`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("hello"))
        viewModel.onIntent(SearchIntent.Search)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasSearched).isTrue()
        assertThat(viewModel.uiState.value.results).isNotEmpty()
    }

    // endregion

    @Test
    fun `SelectRecentSearch updates query and performs search`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SelectRecentSearch("funny"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.query).isEqualTo("funny")
        assertThat(viewModel.uiState.value.hasSearched).isTrue()
        coVerify { searchUseCases.addRecentSearch("funny") }
    }

    @Test
    fun `ClearRecentSearches clears searches`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ClearRecentSearches)
        advanceUntilIdle()

        coVerify { searchUseCases.clearRecentSearches() }
        assertThat(viewModel.uiState.value.recentSearches).isEmpty()
    }

    // endregion

    // region Suggestion Tests

    @Test
    fun `SelectSuggestion updates query with suggestion`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("fun"))
        viewModel.onIntent(SearchIntent.SelectSuggestion("funny"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.query).isEqualTo("funny")
    }

    @Test
    fun `SelectSuggestion replaces last word with suggestion`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("very fun"))
        viewModel.onIntent(SearchIntent.SelectSuggestion("funny"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.query).isEqualTo("very funny")
    }

    // endregion

    // region Quick Filter Tests

    @Test
    fun `SelectQuickFilter updates selected filter in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val filter = QuickFilter.defaultFilters().first()
        viewModel.onIntent(SearchIntent.SelectQuickFilter(filter))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedQuickFilter).isEqualTo(filter)
    }

    @Test
    fun `SelectQuickFilter with emojiFilter adds to emoji filters`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val funnyFilter = QuickFilter.defaultFilters().find { it.id == "funny" }!!
        viewModel.onIntent(SearchIntent.SelectQuickFilter(funnyFilter))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedEmojiFilters).contains("😂")
    }

    @Test
    fun `SelectQuickFilter with query searches without updating display query`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val favoritesFilter = QuickFilter.defaultFilters().find { it.id == "favorites" }!!
        viewModel.onIntent(SearchIntent.SelectQuickFilter(favoritesFilter))
        advanceUntilIdle()

        // Display query must remain empty — internal syntax should never leak to UI
        assertThat(viewModel.uiState.value.query).isEmpty()
        // But the filter is selected and search results are present
        assertThat(viewModel.uiState.value.selectedQuickFilter).isEqualTo(favoritesFilter)
        assertThat(viewModel.uiState.value.hasSearched).isTrue()
    }

    @Test
    fun `ClearQuickFilter clears selected filter`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val filter = QuickFilter.defaultFilters().first()
        viewModel.onIntent(SearchIntent.SelectQuickFilter(filter))
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ClearQuickFilter)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedQuickFilter).isNull()
    }

    @Test
    fun `SelectQuickFilter emits haptic feedback effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val effectsFlow = viewModel.effects.testIn(backgroundScope)

            val filter = QuickFilter.defaultFilters().first()
            viewModel.onIntent(SearchIntent.SelectQuickFilter(filter))
            advanceUntilIdle()

            val effect = effectsFlow.awaitItem()
            assertThat(effect).isInstanceOf(SearchEffect.TriggerHapticFeedback::class.java)

            effectsFlow.cancel()
        }
    }

    // endregion

    // region Sort Order Tests

    @Test
    fun `initial sort order is RELEVANCE`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.sortOrder).isEqualTo(SearchSortOrder.RELEVANCE)
    }

    @Test
    fun `SetSortOrder updates sort order in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SetSortOrder(SearchSortOrder.NEWEST))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.sortOrder).isEqualTo(SearchSortOrder.NEWEST)
    }

    @Test
    fun `SetSortOrder re-sorts existing results`() = runTest {
        val olderMeme = createTestMeme(1, "old.jpg").copy(createdAt = 1000L)
        val newerMeme = createTestMeme(2, "new.jpg").copy(createdAt = 2000L)
        val results = listOf(
            SearchResult(meme = olderMeme, relevanceScore = 1.0f, matchType = MatchType.TEXT),
            SearchResult(meme = newerMeme, relevanceScore = 0.5f, matchType = MatchType.TEXT)
        )
        coEvery { searchUseCases.hybridSearch(any(), any()) } returns results

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        // By default sorted by relevance, so older meme first
        assertThat(viewModel.uiState.value.results.first().meme.id).isEqualTo(1)

        viewModel.onIntent(SearchIntent.SetSortOrder(SearchSortOrder.NEWEST))
        advanceUntilIdle()

        // Now sorted by newest, so newer meme first
        assertThat(viewModel.uiState.value.results.first().meme.id).isEqualTo(2)
    }

    @Test
    fun `SetSortOrder emits haptic feedback effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val effectsFlow = viewModel.effects.testIn(backgroundScope)

            viewModel.onIntent(SearchIntent.SetSortOrder(SearchSortOrder.OLDEST))
            advanceUntilIdle()

            val effect = effectsFlow.awaitItem()
            assertThat(effect).isInstanceOf(SearchEffect.TriggerHapticFeedback::class.java)

            effectsFlow.cancel()
        }
    }

    // endregion

    // region View Mode Tests

    @Test
    fun `initial view mode is GRID`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.viewMode).isEqualTo(SearchViewMode.GRID)
    }

    @Test
    fun `SetViewMode updates view mode in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SetViewMode(SearchViewMode.LIST))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.viewMode).isEqualTo(SearchViewMode.LIST)
    }

    @Test
    fun `SetViewMode emits haptic feedback effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val effectsFlow = viewModel.effects.testIn(backgroundScope)

            viewModel.onIntent(SearchIntent.SetViewMode(SearchViewMode.LIST))
            advanceUntilIdle()

            val effect = effectsFlow.awaitItem()
            assertThat(effect).isInstanceOf(SearchEffect.TriggerHapticFeedback::class.java)

            effectsFlow.cancel()
        }
    }

    // endregion

    // region Delete Recent Search Tests

    @Test
    fun `DeleteRecentSearch removes search from list`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.DeleteRecentSearch("cat"))
        advanceUntilIdle()

        coVerify { searchUseCases.deleteRecentSearch("cat") }
        assertThat(viewModel.uiState.value.recentSearches).doesNotContain("cat")
    }

    // endregion

    // region Voice Search Tests

    @Test
    fun `StartVoiceSearch sets voice search active and emits effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val effectsFlow = viewModel.effects.testIn(backgroundScope)

            viewModel.onIntent(SearchIntent.StartVoiceSearch)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isVoiceSearchActive).isTrue()

            val effect = effectsFlow.awaitItem()
            assertThat(effect).isInstanceOf(SearchEffect.StartVoiceRecognition::class.java)

            effectsFlow.cancel()
        }
    }

    @Test
    fun `StopVoiceSearch sets voice search inactive`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.StartVoiceSearch)
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.StopVoiceSearch)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isVoiceSearchActive).isFalse()
    }

    @Test
    fun `VoiceSearchResult updates query and performs search`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.StartVoiceSearch)
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.VoiceSearchResult("voice query"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.query).isEqualTo("voice query")
        assertThat(viewModel.uiState.value.isVoiceSearchActive).isFalse()
        coVerify { searchUseCases.addRecentSearch("voice query") }
    }

    @Test
    fun `VoiceSearchResult with blank text does not search`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.StartVoiceSearch)
        viewModel.onIntent(SearchIntent.VoiceSearchResult(""))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.query).isEmpty()
        assertThat(viewModel.uiState.value.isVoiceSearchActive).isFalse()
    }

    // endregion

    // region Search Stats Tests

    @Test
    fun `search updates totalResultCount and searchDurationMs`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.totalResultCount).isEqualTo(testSearchResults.size)
        // In test environment, execution is instant so duration may be 0
        assertThat(state.searchDurationMs).isAtLeast(0L)
    }

    @Test
    fun `resultSummary shows correct format when has results`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.resultSummary).contains("result")
        assertThat(state.resultSummary).contains("ms")
    }

    @Test
    fun `resultSummary is empty when not searched`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.resultSummary).isEmpty()
    }

    @Test
    fun `hasActiveFilters is true when emoji filters selected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("😂"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasActiveFilters).isTrue()
    }

    @Test
    fun `hasActiveFilters is true when quick filter selected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SelectQuickFilter(QuickFilter.defaultFilters().first()))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasActiveFilters).isTrue()
    }

    @Test
    fun `hasActiveFilters is false when no filters`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasActiveFilters).isFalse()
    }

    // endregion

    // region Navigation Tests

    @Test
    fun `MemeClicked emits navigation effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val effectsFlow = viewModel.effects.testIn(backgroundScope)

            val meme = testMemes[0]
            viewModel.onIntent(SearchIntent.MemeClicked(meme))
            advanceUntilIdle()

            val effect = effectsFlow.awaitItem()
            assertThat(effect).isInstanceOf(SearchEffect.NavigateToMeme::class.java)
            assertThat((effect as SearchEffect.NavigateToMeme).memeId).isEqualTo(1)

            effectsFlow.cancel()
        }
    }

    // endregion

    // region Error Handling Tests

    @Test
    fun `search error updates state and emits effect`() = runTest {
        coEvery { searchUseCases.hybridSearch(any(), any()) } throws RuntimeException("Search failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val effectsFlow = viewModel.effects.testIn(backgroundScope)

            viewModel.onIntent(SearchIntent.UpdateQuery("test"))
            advanceTimeBy(350)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("Search failed")
            assertThat(state.isSearching).isFalse()

            val effect = effectsFlow.awaitItem()
            assertThat(effect).isInstanceOf(SearchEffect.ShowError::class.java)
            assertThat((effect as SearchEffect.ShowError).message).isEqualTo("Search failed")

            effectsFlow.cancel()
        }
    }

    @Test
    fun `hybrid search falls back to FTS when UnsatisfiedLinkError is thrown`() = runTest {
        coEvery { searchUseCases.hybridSearch(any(), any()) } throws UnsatisfiedLinkError("libgemma_embedding_model_jni.so not found")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        // Should fall back to text search and show results
        verify { searchUseCases.search("test") }
        val state = viewModel.uiState.value
        assertThat(state.hasSearched).isTrue()
        assertThat(state.results).isNotEmpty()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `semantic search falls back to FTS when UnsatisfiedLinkError is thrown`() = runTest {
        coEvery { searchUseCases.semanticSearch(any(), any()) } throws UnsatisfiedLinkError("libgemma_embedding_model_jni.so not found")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SetSearchMode(SearchMode.SEMANTIC))
        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        // Should fall back to text search
        verify { searchUseCases.search("test") }
        val state = viewModel.uiState.value
        assertThat(state.hasSearched).isTrue()
        assertThat(state.results).isNotEmpty()
    }

    @Test
    fun `hybrid search falls back to FTS when ExceptionInInitializerError is thrown`() = runTest {
        coEvery { searchUseCases.hybridSearch(any(), any()) } throws ExceptionInInitializerError(
            UnsatisfiedLinkError("libgemma_embedding_model_jni.so not found")
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        // Should fall back to text search
        verify { searchUseCases.search("test") }
        val state = viewModel.uiState.value
        assertThat(state.hasSearched).isTrue()
        assertThat(state.results).isNotEmpty()
        assertThat(state.errorMessage).isNull()
    }

    // endregion

    // region Quick Filter Interception Tests (p1-9)

    @Test
    fun `is_favorite query routes to getFavoriteMemes instead of FTS`() = runTest {
        val favoriteResults = listOf(
            SearchResult(
                meme = createTestMeme(10, "fav.jpg", isFavorite = true),
                relevanceScore = 1.0f,
                matchType = MatchType.TEXT
            )
        )
        every { searchUseCases.getFavoriteMemes() } returns flowOf(favoriteResults)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("is:favorite"))
        advanceTimeBy(350)
        advanceUntilIdle()

        verify { searchUseCases.getFavoriteMemes() }
        assertThat(viewModel.uiState.value.results).hasSize(1)
        assertThat(viewModel.uiState.value.results[0].meme.isFavorite).isTrue()
        assertThat(viewModel.uiState.value.hasSearched).isTrue()
    }

    @Test
    fun `is_recent query routes to getRecentMemes instead of FTS`() = runTest {
        val recentResults = listOf(
            SearchResult(
                meme = createTestMeme(20, "recent.jpg"),
                relevanceScore = 1.0f,
                matchType = MatchType.TEXT
            )
        )
        every { searchUseCases.getRecentMemes() } returns flowOf(recentResults)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("is:recent"))
        advanceTimeBy(350)
        advanceUntilIdle()

        verify { searchUseCases.getRecentMemes() }
        assertThat(viewModel.uiState.value.results).hasSize(1)
        assertThat(viewModel.uiState.value.hasSearched).isTrue()
    }

    @Test
    fun `type_reaction query falls through to regular search`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("type:reaction"))
        advanceTimeBy(350)
        advanceUntilIdle()

        verify { searchUseCases.search("reaction") }
        assertThat(viewModel.uiState.value.hasSearched).isTrue()
    }

    @Test
    fun `regular query is not intercepted by quick filter`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.UpdateQuery("funny cats"))
        advanceTimeBy(350)
        advanceUntilIdle()

        coVerify { searchUseCases.hybridSearch("funny cats", any()) }
    }

    // endregion

    // region Regression: Trending Quick Filter (p2-ux)

    @Test
    fun `when trending filter selected then results are returned or filter is skipped`() = runTest {
        // "Trending" is not in default quick filters and has no tryQuickFilterQuery handler.
        // Selecting a quick filter whose query doesn't match a known filter should fall through
        // to regular search. Verify the ViewModel gracefully handles an unknown quick filter query.
        val trendingFilter = QuickFilter("trending", 0, "📈", query = "is:trending")
        every { searchUseCases.search(any()) } returns flowOf(testSearchResults)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SelectQuickFilter(trendingFilter))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedQuickFilter).isEqualTo(trendingFilter)
        // "is:trending" is not intercepted, so it falls through to hybrid search
        assertThat(state.hasSearched).isTrue()
    }

    // endregion

    // region Regression: Search Mode Affects Search (p2-ux)

    @Test
    fun `when search mode changed then subsequent search uses new mode`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Start a search in HYBRID mode
        viewModel.onIntent(SearchIntent.UpdateQuery("cats"))
        advanceTimeBy(350)
        advanceUntilIdle()

        coVerify { searchUseCases.hybridSearch("cats", any()) }

        // Switch to TEXT mode — should re-search with text search
        viewModel.onIntent(SearchIntent.SetSearchMode(SearchMode.TEXT))
        advanceUntilIdle()

        verify { searchUseCases.search("cats") }
        assertThat(viewModel.uiState.value.searchMode).isEqualTo(SearchMode.TEXT)

        // Switch to SEMANTIC — should re-search with semantic search
        viewModel.onIntent(SearchIntent.SetSearchMode(SearchMode.SEMANTIC))
        advanceUntilIdle()

        coVerify { searchUseCases.semanticSearch("cats", any()) }
        assertThat(viewModel.uiState.value.searchMode).isEqualTo(SearchMode.SEMANTIC)
    }

    // endregion

    // region Search Mode Renaming Tests (p1-12)

    @Test
    fun `default search mode is HYBRID (Both)`() = runTest {
        viewModel = createViewModel()

        assertThat(viewModel.uiState.value.searchMode).isEqualTo(SearchMode.HYBRID)
    }

    @Test
    fun `activeFilterCount increments when mode is not HYBRID`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeFilterCount).isEqualTo(0)

        viewModel.onIntent(SearchIntent.SetSearchMode(SearchMode.TEXT))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeFilterCount).isEqualTo(1)
    }

    // endregion

    // region Regression: Quick Filter Display Query Leak

    @Test
    fun `selectQuickFilter Favorites does not set display query to is favorite`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val favoritesFilter = QuickFilter.defaultFilters().find { it.id == "favorites" }!!
        viewModel.onIntent(SearchIntent.SelectQuickFilter(favoritesFilter))
        advanceUntilIdle()

        // The SearchBar query must remain empty — "is:favorite" must not leak to UI
        assertThat(viewModel.uiState.value.query).isEmpty()
        // But search must still execute via the quick filter
        assertThat(viewModel.uiState.value.hasSearched).isTrue()
        assertThat(viewModel.uiState.value.results).isNotEmpty()
    }

    // endregion

    // region Regression: Internal Syntax in Recent Searches

    @Test
    fun `recent searches filter out entries with is prefix`() = runTest {
        val searchesWithInternal = listOf("funny", "is:favorite", "cat", "type:reaction", "is:recent")
        every { searchUseCases.getRecentSearches() } returns flowOf(searchesWithInternal)

        viewModel = createViewModel()
        advanceUntilIdle()

        val recentSearches = viewModel.uiState.value.recentSearches
        assertThat(recentSearches).containsExactly("funny", "cat")
        assertThat(recentSearches).doesNotContain("is:favorite")
        assertThat(recentSearches).doesNotContain("type:reaction")
        assertThat(recentSearches).doesNotContain("is:recent")
    }

    // endregion

    // region Regression: Emoji Unicode Normalization

    @Test
    fun `emoji filter matches heart with and without variation selector`() = runTest {
        // Meme has ❤ (U+2764 without variation selector)
        val heartMeme = createTestMeme(1, "heart.jpg", emojiTags = listOf(EmojiTag.fromEmoji("❤")))
        val otherMeme = createTestMeme(2, "other.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😀")))
        val allMemes = listOf(heartMeme, otherMeme)
        every { searchUseCases.getAllMemes() } returns flowOf(allMemes)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Toggle filter with ❤️ (U+2764 U+FE0F — with variation selector)
        viewModel.onIntent(SearchIntent.ToggleEmojiFilter("❤️"))
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertThat(results).hasSize(1)
        assertThat(results.first().meme.id).isEqualTo(1L)
    }

    // endregion

    // region Helper Functions

    private fun createTestMeme(
        id: Long,
        fileName: String,
        emojiTags: List<EmojiTag> = emptyList(),
        isFavorite: Boolean = false,
        title: String? = null,
        description: String? = null
    ): Meme {
        return Meme(
            id = id,
            filePath = "/test/path/$fileName",
            fileName = fileName,
            mimeType = "image/jpeg",
            width = 1920,
            height = 1080,
            fileSizeBytes = 1024L,
            importedAt = System.currentTimeMillis(),
            emojiTags = emojiTags,
            title = title,
            description = description,
            textContent = null,
            isFavorite = isFavorite
        )
    }

    // endregion
}
