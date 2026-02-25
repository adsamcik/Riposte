package com.adsamcik.riposte.core.search.domain.usecase

import app.cash.turbine.test
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.search.domain.repository.SearchRepository
import com.adsamcik.riposte.core.testing.TestDataFactory
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SearchUseCasesTest {
    private lateinit var repository: SearchRepository

    private val testMemes =
        listOf(
            TestDataFactory.createMeme(id = 1, fileName = "meme1.jpg", filePath = "/test/path/meme1.jpg", emojiTags = emptyList(), title = null),
            TestDataFactory.createMeme(id = 2, fileName = "meme2.jpg", filePath = "/test/path/meme2.jpg", emojiTags = emptyList(), title = null),
            TestDataFactory.createMeme(id = 3, fileName = "meme3.jpg", filePath = "/test/path/meme3.jpg", emojiTags = emptyList(), title = null),
        )

    private val testSearchResults =
        testMemes.mapIndexed { index, meme ->
            SearchResult(
                meme = meme,
                relevanceScore = 1.0f - (index * 0.1f),
                matchType = MatchType.TEXT,
            )
        }

    private val recentSearches = listOf("funny", "cat", "dog")
    private val suggestions = listOf("funny meme", "funny cat", "funny dog")

    @Before
    fun setup() {
        repository = mockk()
    }

    // region TextSearchUseCase Tests

    @Test
    fun `TextSearchUseCase returns flow of results from repository`() =
        runTest {
            every { repository.searchByText("funny") } returns flowOf(testSearchResults)
            val useCase = TextSearchUseCase(repository)

            useCase("funny").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
                awaitComplete()
            }

            verify { repository.searchByText("funny") }
        }

    @Test
    fun `TextSearchUseCase returns empty list for no matches`() =
        runTest {
            every { repository.searchByText("xyz") } returns flowOf(emptyList())
            val useCase = TextSearchUseCase(repository)

            useCase("xyz").test {
                val results = awaitItem()
                assertThat(results).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `TextSearchUseCase emits updated results when repository updates`() =
        runTest {
            val mutableFlow = MutableStateFlow(testSearchResults)
            every { repository.searchByText("test") } returns mutableFlow
            val useCase = TextSearchUseCase(repository)

            useCase("test").test {
                assertThat(awaitItem()).hasSize(3)

                val newResults =
                    testSearchResults +
                        SearchResult(
                            meme = TestDataFactory.createMeme(id = 4, fileName = "new.jpg", filePath = "/test/path/new.jpg", emojiTags = emptyList(), title = null),
                            relevanceScore = 0.5f,
                            matchType = MatchType.TEXT,
                        )
                mutableFlow.value = newResults
                assertThat(awaitItem()).hasSize(4)
            }
        }

    // endregion

    // region SemanticSearchUseCase Tests

    @Test
    fun `SemanticSearchUseCase returns results from repository`() =
        runTest {
            coEvery { repository.searchSemantic("funny", 20) } returns testSearchResults
            val useCase = SemanticSearchUseCase(repository)

            val results = useCase("funny")

            assertThat(results).hasSize(3)
            assertThat(results[0].matchType).isEqualTo(MatchType.TEXT)
            coVerify { repository.searchSemantic("funny", 20) }
        }

    @Test
    fun `SemanticSearchUseCase uses custom limit`() =
        runTest {
            coEvery { repository.searchSemantic("test", 10) } returns testSearchResults.take(1)
            val useCase = SemanticSearchUseCase(repository)

            val results = useCase("test", limit = 10)

            assertThat(results).hasSize(1)
            coVerify { repository.searchSemantic("test", 10) }
        }

    @Test
    fun `SemanticSearchUseCase returns empty list for no matches`() =
        runTest {
            coEvery { repository.searchSemantic("xyz", any()) } returns emptyList()
            val useCase = SemanticSearchUseCase(repository)

            val results = useCase("xyz")

            assertThat(results).isEmpty()
        }

    // endregion

    // region HybridSearchUseCase Tests

    @Test
    fun `HybridSearchUseCase returns results from repository`() =
        runTest {
            coEvery { repository.searchHybrid("funny", 20) } returns testSearchResults
            val useCase = HybridSearchUseCase(repository)

            val results = useCase("funny")

            assertThat(results).hasSize(3)
            coVerify { repository.searchHybrid("funny", 20) }
        }

    @Test
    fun `HybridSearchUseCase uses custom limit`() =
        runTest {
            coEvery { repository.searchHybrid("test", 5) } returns testSearchResults.take(2)
            val useCase = HybridSearchUseCase(repository)

            val results = useCase("test", limit = 5)

            assertThat(results).hasSize(2)
            coVerify { repository.searchHybrid("test", 5) }
        }

    // endregion

    // region EmojiSearchUseCase Tests

    @Test
    fun `EmojiSearchUseCase returns flow of results`() =
        runTest {
            val emojiResults = testSearchResults.map { it.copy(matchType = MatchType.EMOJI) }
            every { repository.searchByEmoji("😂") } returns flowOf(emojiResults)
            val useCase = EmojiSearchUseCase(repository)

            useCase("😂").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                assertThat(results[0].matchType).isEqualTo(MatchType.EMOJI)
                awaitComplete()
            }

            verify { repository.searchByEmoji("😂") }
        }

    @Test
    fun `EmojiSearchUseCase returns empty list for no matches`() =
        runTest {
            every { repository.searchByEmoji("🤷") } returns flowOf(emptyList())
            val useCase = EmojiSearchUseCase(repository)

            useCase("🤷").test {
                val results = awaitItem()
                assertThat(results).isEmpty()
                awaitComplete()
            }
        }

    // endregion

    // region GetSearchSuggestionsUseCase Tests

    @Test
    fun `GetSearchSuggestionsUseCase returns suggestions from repository`() =
        runTest {
            coEvery { repository.getSearchSuggestions("fun") } returns suggestions
            val useCase = GetSearchSuggestionsUseCase(repository)

            val result = useCase("fun")

            assertThat(result).isEqualTo(suggestions)
            coVerify { repository.getSearchSuggestions("fun") }
        }

    @Test
    fun `GetSearchSuggestionsUseCase returns empty list for no suggestions`() =
        runTest {
            coEvery { repository.getSearchSuggestions("xyz") } returns emptyList()
            val useCase = GetSearchSuggestionsUseCase(repository)

            val result = useCase("xyz")

            assertThat(result).isEmpty()
        }

    // endregion

    // region RecentSearchesUseCase Tests

    @Test
    fun `RecentSearchesUseCase getRecentSearches returns flow from repository`() =
        runTest {
            every { repository.getRecentSearches() } returns flowOf(recentSearches)
            val useCase = RecentSearchesUseCase(repository)

            useCase.getRecentSearches().test {
                val searches = awaitItem()
                assertThat(searches).isEqualTo(recentSearches)
                awaitComplete()
            }

            verify { repository.getRecentSearches() }
        }

    @Test
    fun `RecentSearchesUseCase addRecentSearch calls repository`() =
        runTest {
            coEvery { repository.addRecentSearch("new search") } returns Unit
            val useCase = RecentSearchesUseCase(repository)

            useCase.addRecentSearch("new search")

            coVerify { repository.addRecentSearch("new search") }
        }

    @Test
    fun `RecentSearchesUseCase clearRecentSearches calls repository`() =
        runTest {
            coEvery { repository.clearRecentSearches() } returns Unit
            val useCase = RecentSearchesUseCase(repository)

            useCase.clearRecentSearches()

            coVerify { repository.clearRecentSearches() }
        }

    // endregion

    // region SearchUseCases (Aggregated) Tests

    @Test
    fun `SearchUseCases search returns flow from repository`() =
        runTest {
            every { repository.searchMemes("test") } returns flowOf(testSearchResults)
            val useCases = SearchUseCases(repository)

            useCases.search("test").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                awaitComplete()
            }

            verify { repository.searchMemes("test") }
        }

    @Test
    fun `SearchUseCases semanticSearch returns results from repository`() =
        runTest {
            coEvery { repository.searchSemantic("test", 20) } returns testSearchResults
            val useCases = SearchUseCases(repository)

            val results = useCases.semanticSearch("test")

            assertThat(results).hasSize(3)
            coVerify { repository.searchSemantic("test", 20) }
        }

    @Test
    fun `SearchUseCases hybridSearch returns results from repository`() =
        runTest {
            coEvery { repository.searchHybrid("test", 20) } returns testSearchResults
            val useCases = SearchUseCases(repository)

            val results = useCases.hybridSearch("test")

            assertThat(results).hasSize(3)
            coVerify { repository.searchHybrid("test", 20) }
        }

    @Test
    fun `SearchUseCases getRecentSearches returns flow from repository`() =
        runTest {
            every { repository.getRecentSearches() } returns flowOf(recentSearches)
            val useCases = SearchUseCases(repository)

            useCases.getRecentSearches().test {
                val searches = awaitItem()
                assertThat(searches).isEqualTo(recentSearches)
                awaitComplete()
            }
        }

    @Test
    fun `SearchUseCases getSearchSuggestions returns suggestions`() =
        runTest {
            coEvery { repository.getSearchSuggestions("fun") } returns suggestions
            val useCases = SearchUseCases(repository)

            val result = useCases.getSearchSuggestions("fun")

            assertThat(result).isEqualTo(suggestions)
        }

    @Test
    fun `SearchUseCases addRecentSearch calls repository`() =
        runTest {
            coEvery { repository.addRecentSearch("query") } returns Unit
            val useCases = SearchUseCases(repository)

            useCases.addRecentSearch("query")

            coVerify { repository.addRecentSearch("query") }
        }

    @Test
    fun `SearchUseCases clearRecentSearches calls repository`() =
        runTest {
            coEvery { repository.clearRecentSearches() } returns Unit
            val useCases = SearchUseCases(repository)

            useCases.clearRecentSearches()

            coVerify { repository.clearRecentSearches() }
        }

    // endregion

    // region SearchUseCases getFavoriteMemes/getRecentMemes Tests

    @Test
    fun `SearchUseCases getFavoriteMemes returns flow from repository`() =
        runTest {
            val favoriteResults = testSearchResults.take(1)
            every { repository.getFavoriteMemes() } returns flowOf(favoriteResults)
            val useCases = SearchUseCases(repository)

            useCases.getFavoriteMemes().test {
                val results = awaitItem()
                assertThat(results).hasSize(1)
                awaitComplete()
            }

            verify { repository.getFavoriteMemes() }
        }

    @Test
    fun `SearchUseCases getRecentMemes returns flow from repository`() =
        runTest {
            val recentResults = testSearchResults.take(2)
            every { repository.getRecentMemes() } returns flowOf(recentResults)
            val useCases = SearchUseCases(repository)

            useCases.getRecentMemes().test {
                val results = awaitItem()
                assertThat(results).hasSize(2)
                awaitComplete()
            }

            verify { repository.getRecentMemes() }
        }

    // endregion

    // region getEmojiCounts Tests

    @Test
    fun `SearchUseCases getEmojiCounts returns flow from repository`() =
        runTest {
            val emojiCounts = listOf("🔥" to 30, "😂" to 15)
            every { repository.getEmojiCounts() } returns flowOf(emojiCounts)
            val useCases = SearchUseCases(repository)

            useCases.getEmojiCounts().test {
                val result = awaitItem()
                assertThat(result).hasSize(2)
                assertThat(result[0]).isEqualTo("🔥" to 30)
                assertThat(result[1]).isEqualTo("😂" to 15)
                awaitComplete()
            }

            verify { repository.getEmojiCounts() }
        }

    @Test
    fun `SearchUseCases getEmojiCounts returns empty list when no emojis`() =
        runTest {
            every { repository.getEmojiCounts() } returns flowOf(emptyList())
            val useCases = SearchUseCases(repository)

            useCases.getEmojiCounts().test {
                val result = awaitItem()
                assertThat(result).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `SearchUseCases getEmojiCounts emits updates reactively`() =
        runTest {
            val emojiFlow = MutableStateFlow(listOf("🔥" to 10))
            every { repository.getEmojiCounts() } returns emojiFlow
            val useCases = SearchUseCases(repository)

            useCases.getEmojiCounts().test {
                assertThat(awaitItem()).hasSize(1)

                emojiFlow.value = listOf("🔥" to 20, "😂" to 5)
                assertThat(awaitItem()).hasSize(2)
            }
        }

    // endregion

    // endregion
}
