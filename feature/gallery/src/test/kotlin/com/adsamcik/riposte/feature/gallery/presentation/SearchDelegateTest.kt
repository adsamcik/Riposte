package com.adsamcik.riposte.feature.gallery.presentation

import app.cash.turbine.test
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.search.domain.usecase.SearchUseCases
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
class SearchDelegateTest {
    private val testDispatcher = StandardTestDispatcher()

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
        Dispatchers.setMain(testDispatcher)
        searchUseCases = mockk()

        every { searchUseCases.getRecentSearches() } returns flowOf(listOf("cat", "dog", "funny"))
        coEvery { searchUseCases.hybridSearch(any(), any()) } returns testSearchResults
        coEvery { searchUseCases.addRecentSearch(any()) } returns Unit
        coEvery { searchUseCases.deleteRecentSearch(any()) } returns Unit
        coEvery { searchUseCases.clearRecentSearches() } returns Unit

        delegate = SearchDelegate(searchUseCases)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDelegateScope() = CoroutineScope(testDispatcher + SupervisorJob())

    @Test
    fun `initial state has empty query and no results`() =
        runTest(testDispatcher) {
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
        runTest(testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.recentSearches).containsExactly("cat", "dog", "funny")
            scope.cancel()
        }

    @Test
    fun `init filters out internal query syntax from recent searches`() =
        runTest(testDispatcher) {
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
        runTest(testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(
                GalleryIntent.UpdateSearchQuery("cat"),
                scope,
                emptySet(),
            )

            assertThat(delegate.state.value.query).isEqualTo("cat")
            scope.cancel()
        }

    @Test
    fun `search is debounced and performs hybrid search`() =
        runTest(testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(
                GalleryIntent.UpdateSearchQuery("funny"),
                scope,
                emptySet(),
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
        runTest(testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            // Perform a search first
            delegate.onIntent(GalleryIntent.UpdateSearchQuery("funny"), scope, emptySet())
            advanceTimeBy(400)
            advanceUntilIdle()
            assertThat(delegate.state.value.hasSearched).isTrue()

            // Clear
            delegate.onIntent(GalleryIntent.ClearSearch, scope, emptySet())

            val state = delegate.state.value
            assertThat(state.query).isEmpty()
            assertThat(state.results).isEmpty()
            assertThat(state.hasSearched).isFalse()
            scope.cancel()
        }

    @Test
    fun `selectRecentSearch performs immediate search`() =
        runTest(testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(
                GalleryIntent.SelectRecentSearch("cat"),
                scope,
                emptySet(),
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
        runTest(testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()
            assertThat(delegate.state.value.recentSearches).contains("cat")

            delegate.onIntent(
                GalleryIntent.DeleteRecentSearch("cat"),
                scope,
                emptySet(),
            )
            advanceUntilIdle()

            assertThat(delegate.state.value.recentSearches).doesNotContain("cat")
            coVerify { searchUseCases.deleteRecentSearch("cat") }
            scope.cancel()
        }

    @Test
    fun `clearRecentSearches empties the list`() =
        runTest(testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()
            assertThat(delegate.state.value.recentSearches).isNotEmpty()

            delegate.onIntent(GalleryIntent.ClearRecentSearches, scope, emptySet())
            advanceUntilIdle()

            assertThat(delegate.state.value.recentSearches).isEmpty()
            coVerify { searchUseCases.clearRecentSearches() }
            scope.cancel()
        }

    @Test
    fun `refilter applies emoji filters to existing results`() =
        runTest(testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            // Search first
            delegate.onIntent(GalleryIntent.UpdateSearchQuery("meme"), scope, emptySet())
            advanceTimeBy(400)
            advanceUntilIdle()
            assertThat(delegate.state.value.results).hasSize(3)

            // Refilter with 🔥 — only meme3 has it
            delegate.refilter(scope, setOf("🔥"))
            advanceUntilIdle()

            assertThat(delegate.state.value.results).hasSize(1)
            assertThat(delegate.state.value.results[0].meme.id).isEqualTo(3)
            scope.cancel()
        }

    @Test
    fun `search error sets errorMessage`() =
        runTest(testDispatcher) {
            coEvery { searchUseCases.hybridSearch(any(), any()) } throws RuntimeException("Network error")

            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(GalleryIntent.UpdateSearchQuery("fail"), scope, emptySet())
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
        runTest(testDispatcher) {
            coEvery { searchUseCases.hybridSearch(any(), any()) } throws UnsatisfiedLinkError("ML not available")
            every { searchUseCases.search("cat") } returns flowOf(testSearchResults.take(1))

            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            delegate.onIntent(GalleryIntent.UpdateSearchQuery("cat"), scope, emptySet())
            advanceTimeBy(400)
            advanceUntilIdle()

            val state = delegate.state.value
            assertThat(state.hasSearched).isTrue()
            assertThat(state.results).hasSize(1)
            scope.cancel()
        }

    @Test
    fun `blank query clears results`() =
        runTest(testDispatcher) {
            val scope = createDelegateScope()
            delegate.init(scope)
            advanceUntilIdle()

            // Search first
            delegate.onIntent(GalleryIntent.UpdateSearchQuery("funny"), scope, emptySet())
            advanceTimeBy(400)
            advanceUntilIdle()
            assertThat(delegate.state.value.hasSearched).isTrue()

            // Set blank query
            delegate.onIntent(GalleryIntent.UpdateSearchQuery(""), scope, emptySet())
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
