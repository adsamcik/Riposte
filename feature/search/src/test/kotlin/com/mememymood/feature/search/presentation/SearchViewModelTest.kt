package com.mememymood.feature.search.presentation

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
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

    private lateinit var searchUseCases: SearchUseCases
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
        searchUseCases = mockk()

        // Default mock setup
        every { searchUseCases.getRecentSearches() } returns flowOf(recentSearches)
        every { searchUseCases.search(any()) } returns flowOf(testSearchResults)
        coEvery { searchUseCases.semanticSearch(any(), any()) } returns testSearchResults
        coEvery { searchUseCases.hybridSearch(any(), any()) } returns testSearchResults
        coEvery { searchUseCases.getSearchSuggestions(any()) } returns suggestions
        coEvery { searchUseCases.addRecentSearch(any()) } returns Unit
        coEvery { searchUseCases.clearRecentSearches() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SearchViewModel {
        return SearchViewModel(searchUseCases)
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

    // region Recent Searches Tests

    @Test
    fun `SelectRecentSearch updates query and performs search`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(SearchIntent.SelectRecentSearch("funny"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.query).isEqualTo("funny")
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
