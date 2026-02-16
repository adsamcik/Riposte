package com.adsamcik.riposte.feature.gallery.presentation

import app.cash.turbine.test
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.search.domain.usecase.SearchUseCases
import com.adsamcik.riposte.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchDelegateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var searchUseCases: SearchUseCases
    private lateinit var delegate: SearchDelegate

    private val testMemes =
        listOf(
            createTestMeme(1, "meme1.jpg", title = "Funny cat"),
            createTestMeme(2, "meme2.jpg", title = "Surprised Pikachu"),
            createTestMeme(
                3,
                "meme3.jpg",
                title = "Fire meme",
                emojiTags = listOf(EmojiTag.fromEmoji("🔥")),
            ),
        )

    private val testSearchResults =
        testMemes.mapIndexed { index, meme ->
            SearchResult(
                meme = meme,
                relevanceScore = 1.0f - (index * 0.1f),
                matchType = MatchType.HYBRID,
            )
        }

    @Before
    fun setup() {
        searchUseCases = mockk()

        every { searchUseCases.getRecentSearches() } returns flowOf(listOf("cat", "dog", "funny"))
        coEvery { searchUseCases.hybridSearch(any(), any()) } returns testSearchResults
        coEvery { searchUseCases.addRecentSearch(any()) } returns Unit
        coEvery { searchUseCases.deleteRecentSearch(any()) } returns Unit
        coEvery { searchUseCases.clearRecentSearches() } returns Unit
        coEvery { searchUseCases.getSearchSuggestions(any()) } returns emptyList()

        delegate = SearchDelegate(searchUseCases)
    }

    private fun createDelegateScope() = CoroutineScope(mainDispatcherRule.testDispatcher + SupervisorJob())

    @Test
    fun `initial state has empty query and no results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            delegate.state.test {
                val initial = awaitItem()
                assertThat(initial.query).isEmpty()
                assertThat(initial.results).isEmpty()
                assertThat(initial.isSearching).isFalse()
                assertThat(initial.hasSearched).isFalse()
            }
        }

    @Test
    fun `init loads recent searches`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.recentSearches).containsExactly("cat", "dog", "funny")
            scope.cancel()
        }

    @Test
    fun `init filters out internal query syntax from recent searches`() =
        runTest(mainDispatcherRule.testDispatcher) {
            every { searchUseCases.getRecentSearches() } returns
                flowOf(
                    listOf("cat", "is:favorite", "type:reaction", "funny"),
                )

            delegate = SearchDelegate(searchUseCases)
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.recentSearches).containsExactly("cat", "funny")
            scope.cancel()
        }

    @Test
    fun `updateQuery updates query in state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(
                GalleryIntent.UpdateSearchQuery("cat"),
                scope,
                null,
            )

            assertThat(delegate.state.value.query).isEqualTo("cat")
            scope.cancel()
        }

    @Test
    fun `search is debounced and performs hybrid search`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(
                GalleryIntent.UpdateSearchQuery("funny"),
                scope,
                null,
            )

            // Before debounce, no search yet
            advanceTimeBy(200)
            assertThat(delegate.state.value.hasSearched).isFalse()

            // After debounce (300ms total), search should fire
            advanceTimeBy(200)
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.hasSearched).isTrue()
            assertThat(state.results).hasSize(3)
            assertThat(state.totalResultCount).isEqualTo(3)
            assertThat(state.isSearching).isFalse()
            scope.cancel()
        }

    @Test
    fun `clearSearch resets state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            // Perform a search first
            delegate.onIntent(GalleryIntent.UpdateSearchQuery("funny"), scope, null)
            advanceTimeBy(400)
            advanceUntilIdle()
            assertThat(delegate.state.value.hasSearched).isTrue()

            // Clear
            delegate.onIntent(GalleryIntent.ClearSearch, scope, null)

            val state = delegate.state.value
            assertThat(state.query).isEmpty()
            assertThat(state.results).isEmpty()
            assertThat(state.hasSearched).isFalse()
            scope.cancel()
        }

    @Test
    fun `selectRecentSearch performs immediate search`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(
                GalleryIntent.SelectRecentSearch("cat"),
                scope,
                null,
            )
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.query).isEqualTo("cat")
            assertThat(state.hasSearched).isTrue()
            coVerify { searchUseCases.addRecentSearch("cat") }
            scope.cancel()
        }

    @Test
    fun `deleteRecentSearch removes from state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()
            assertThat(delegate.state.value.recentSearches).contains("cat")

            delegate.onIntent(
                GalleryIntent.DeleteRecentSearch("cat"),
                scope,
                null,
            )
            advanceUntilIdle()

            assertThat(delegate.state.value.recentSearches).doesNotContain("cat")
            coVerify { searchUseCases.deleteRecentSearch("cat") }
            scope.cancel()
        }

    @Test
    fun `clearRecentSearches empties the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()
            assertThat(delegate.state.value.recentSearches).isNotEmpty()

            delegate.onIntent(GalleryIntent.ClearRecentSearches, scope, null)
            advanceUntilIdle()

            assertThat(delegate.state.value.recentSearches).isEmpty()
            coVerify { searchUseCases.clearRecentSearches() }
            scope.cancel()
        }

    @Test
    fun `refilter applies emoji filter to existing results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            // Search first
            delegate.onIntent(GalleryIntent.UpdateSearchQuery("meme"), scope, null)
            advanceTimeBy(400)
            advanceUntilIdle()
            assertThat(delegate.state.value.results).hasSize(3)

            // Refilter with 🔥 — only meme3 has it
            delegate.refilter(scope, "🔥")
            advanceUntilIdle()

            assertThat(delegate.state.value.results).hasSize(1)
            assertThat(delegate.state.value.results[0].meme.id).isEqualTo(3)
            scope.cancel()
        }

    @Test
    fun `search error sets errorMessage`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { searchUseCases.hybridSearch(any(), any()) } throws RuntimeException("Network error")

            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(GalleryIntent.UpdateSearchQuery("fail"), scope, null)
            advanceTimeBy(400)
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.hasSearched).isTrue()
            assertThat(state.isSearching).isFalse()
            assertThat(state.errorMessage).isEqualTo("Network error")
            scope.cancel()
        }

    @Test
    fun `falls back to text search on UnsatisfiedLinkError`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { searchUseCases.hybridSearch(any(), any()) } throws UnsatisfiedLinkError("ML not available")
            every { searchUseCases.search("cat") } returns flowOf(testSearchResults.take(1))

            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(GalleryIntent.UpdateSearchQuery("cat"), scope, null)
            advanceTimeBy(400)
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.hasSearched).isTrue()
            assertThat(state.results).hasSize(1)
            scope.cancel()
        }

    // region Suggestion Tests

    @Test
    fun `suggestions fetched after short debounce for query with 2+ chars`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { searchUseCases.getSearchSuggestions(any()) } returns listOf("cat meme", "car meme")
            delegate = SearchDelegate(searchUseCases)

            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(GalleryIntent.UpdateSearchQuery("cat"), scope, null)

            // Advance past suggestion debounce (150ms) but not search debounce (300ms)
            advanceTimeBy(200)
            // runCurrent() processes only currently scheduled work, not future
            runCurrent()

            // Suggestions should be populated (search hasn't fired yet at 200ms)
            assertThat(delegate.state.value.suggestions).containsExactly("cat meme", "car meme")
            // Search hasn't fired yet
            assertThat(delegate.state.value.hasSearched).isFalse()
            scope.cancel()
        }

    @Test
    fun `no suggestions for query shorter than 2 chars`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(GalleryIntent.UpdateSearchQuery("c"), scope, null)
            advanceTimeBy(400)
            advanceUntilIdle()

            assertThat(delegate.state.value.suggestions).isEmpty()
            scope.cancel()
        }

    @Test
    fun `suggestions cleared when search results arrive`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { searchUseCases.getSearchSuggestions("funny") } returns listOf("funny cat", "fun times")

            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(GalleryIntent.UpdateSearchQuery("funny"), scope, null)

            // After full search debounce (300ms) — performSearch clears suggestions
            advanceTimeBy(400)
            advanceUntilIdle()

            assertThat(delegate.state.value.suggestions).isEmpty()
            assertThat(delegate.state.value.hasSearched).isTrue()
            scope.cancel()
        }

    @Test
    fun `selectSuggestion clears suggestions and triggers search`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(GalleryIntent.SelectSuggestion("funny cat"), scope, null)
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.query).isEqualTo("funny cat")
            assertThat(state.suggestions).isEmpty()
            assertThat(state.hasSearched).isTrue()
            assertThat(state.results).hasSize(3)
            coVerify { searchUseCases.addRecentSearch("funny cat") }
            scope.cancel()
        }

    @Test
    fun `suggestions cleared on blank query`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            // Type to get into search mode, then clear
            delegate.onIntent(GalleryIntent.UpdateSearchQuery("cat"), scope, null)
            advanceTimeBy(400)
            advanceUntilIdle()

            // Clear the query — should clear suggestions
            delegate.onIntent(GalleryIntent.UpdateSearchQuery(""), scope, null)
            advanceTimeBy(400)
            advanceUntilIdle()

            assertThat(delegate.state.value.suggestions).isEmpty()
            assertThat(delegate.state.value.hasSearched).isFalse()
            scope.cancel()
        }

    // endregion

    @Test
    fun `blank query clears results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            // Search first
            delegate.onIntent(GalleryIntent.UpdateSearchQuery("funny"), scope, null)
            advanceTimeBy(400)
            advanceUntilIdle()
            assertThat(delegate.state.value.hasSearched).isTrue()

            // Set blank query
            delegate.onIntent(GalleryIntent.UpdateSearchQuery(""), scope, null)
            advanceTimeBy(400)
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.results).isEmpty()
            assertThat(state.hasSearched).isFalse()
            scope.cancel()
        }

    private fun createTestMeme(
        id: Long,
        fileName: String,
        emojiTags: List<EmojiTag> = emptyList(),
        isFavorite: Boolean = false,
        title: String? = null,
    ): Meme =
        Meme(
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
            isFavorite = isFavorite,
        )
}
