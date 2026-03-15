package com.adsamcik.riposte.core.search.data.repository

import app.cash.turbine.test
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.EmojiUsageBySharing
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeSearchDao
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.search.data.SearchOrchestrator
import com.adsamcik.riposte.core.search.data.SearchRepositoryImpl
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SearchRepositoryImplTest {
    private lateinit var memeDao: MemeDao
    private lateinit var memeSearchDao: MemeSearchDao
    private lateinit var emojiTagDao: EmojiTagDao
    private lateinit var searchOrchestrator: SearchOrchestrator
    private lateinit var preferencesDataStore: PreferencesDataStore

    private lateinit var repository: SearchRepositoryImpl

    private val testMemeEntities =
        listOf(
            createTestMemeEntity(1, "meme1.jpg", title = "Funny cat"),
            createTestMemeEntity(2, "meme2.jpg", description = "Dog meme"),
            createTestMemeEntity(3, "meme3.jpg", emojiTagsJson = "😂,😀"),
        )

    private val recentSearches = listOf("funny", "cat", "dog")
    private val suggestions = listOf("funny meme", "funny cat")

    private val defaultPreferences =
        AppPreferences(
            darkMode = DarkMode.SYSTEM,
            dynamicColors = true,
            gridColumns = 2,
            showEmojiNames = false,
            enableSemanticSearch = true,
            autoExtractText = true,
            saveSearchHistory = true,
        )

    @Before
    fun setup() {
        memeDao = mockk()
        memeSearchDao = mockk()
        emojiTagDao = mockk()
        searchOrchestrator = mockk()
        preferencesDataStore = mockk()

        every { preferencesDataStore.appPreferences } returns flowOf(defaultPreferences)
        every { preferencesDataStore.recentSearches } returns flowOf(recentSearches)
        coEvery { searchOrchestrator.search(any(), any()) } returns emptyList()
        every { memeDao.getFavoriteMemes() } returns flowOf(emptyList())
        every { memeDao.getRecentlyViewedMemes(any()) } returns flowOf(emptyList())

        repository =
            SearchRepositoryImpl(
                memeDao = memeDao,
                memeSearchDao = memeSearchDao,
                emojiTagDao = emojiTagDao,
                searchOrchestrator = searchOrchestrator,
                preferencesDataStore = preferencesDataStore,
            )
    }

    // region searchMemes Tests

    @Test
    fun `searchMemes returns flow of search results`() =
        runTest {
            every { memeSearchDao.searchMemes(any()) } returns flowOf(testMemeEntities)

            repository.searchMemes("funny").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                assertThat(results[0].relevanceScore).isGreaterThan(0f)
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes returns empty list for blank query`() =
        runTest {
            repository.searchMemes("").test {
                val results = awaitItem()
                assertThat(results).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes returns empty list for whitespace query`() =
        runTest {
            repository.searchMemes("   ").test {
                val results = awaitItem()
                assertThat(results).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes calculates descending relevance scores`() =
        runTest {
            // title match: 0.5 + 0.3 = 0.8, desc match: 0.5 + 0.15 = 0.65, no match: 0.5
            val entities =
                listOf(
                    createTestMemeEntity(1, "meme1.jpg", title = "test content"),
                    createTestMemeEntity(2, "meme2.jpg", description = "test stuff"),
                    createTestMemeEntity(3, "meme3.jpg"),
                )
            every { memeSearchDao.searchMemes(any()) } returns flowOf(entities)

            repository.searchMemes("test").test {
                val results = awaitItem()
                assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
                assertThat(results[1].relevanceScore).isGreaterThan(results[2].relevanceScore)
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes determines TEXT match type for title matches`() =
        runTest {
            val entitiesWithTitle = listOf(createTestMemeEntity(1, "test.jpg", title = "funny cat"))
            every { memeSearchDao.searchMemes(any()) } returns flowOf(entitiesWithTitle)

            repository.searchMemes("funny").test {
                val results = awaitItem()
                assertThat(results[0].matchType).isEqualTo(MatchType.TEXT)
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes determines EMOJI match type for emoji matches`() =
        runTest {
            val entitiesWithEmoji = listOf(createTestMemeEntity(1, "test.jpg", emojiTagsJson = "😂"))
            every { memeSearchDao.searchMemes(any()) } returns flowOf(entitiesWithEmoji)

            repository.searchMemes("😂").test {
                val results = awaitItem()
                assertThat(results[0].matchType).isEqualTo(MatchType.EMOJI)
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes caps relevance score at 1_0 when all fields match`() =
        runTest {
            val entity = createTestMemeEntity(
                1L,
                "match.jpg",
                title = "match",
                description = "match",
                emojiTagsJson = "match",
            )
            every { memeSearchDao.searchMemes(any()) } returns flowOf(listOf(entity))

            repository.searchMemes("match").test {
                val results = awaitItem()
                assertThat(results).hasSize(1)
                assertThat(results[0].relevanceScore).isAtMost(1.0f)
                awaitComplete()
            }
        }

    // endregion

    // region searchByText Tests

    @Test
    fun `searchByText returns same results as searchMemes`() =
        runTest {
            every { memeSearchDao.searchMemes(any()) } returns flowOf(testMemeEntities)

            repository.searchByText("test").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                awaitComplete()
            }
        }

    // endregion

    // region searchSemantic Tests

    @Test
    fun `searchSemantic returns empty list for blank query`() =
        runTest {
            val results = repository.searchSemantic("")
            assertThat(results).isEmpty()
        }

    @Test
    fun `searchSemantic returns empty list when orchestrator returns no results`() =
        runTest {
            val results = repository.searchSemantic("test")
            assertThat(results).isEmpty()
        }

    @Test
    fun `searchSemantic delegates to search orchestrator`() =
        runTest {
            val semanticResults =
                testMemeEntities.mapIndexed { index, entity ->
                    SearchResult(
                        meme = entity.toDomainMeme(),
                        relevanceScore = 1.0f - (index * 0.1f),
                        matchType = MatchType.SEMANTIC,
                    )
                }
            coEvery { searchOrchestrator.search("test", 20) } returns semanticResults

            val results = repository.searchSemantic("test", 20)

            assertThat(results).hasSize(3)
            coVerify { searchOrchestrator.search("test", 20) }
        }

    @Test
    fun `searchSemantic groups multiple embeddings per meme into single result`() =
        runTest {
            val entity = createTestMemeEntity(1, "meme1.jpg", title = "Funny cat")

            val semanticResult =
                listOf(
                    SearchResult(
                        meme = entity.toDomainMeme(),
                        relevanceScore = 0.9f,
                        matchType = MatchType.SEMANTIC,
                    ),
                )
            coEvery { searchOrchestrator.search("test", 20, any()) } returns semanticResult

            val results = repository.searchSemantic("test", 20)

            assertThat(results).hasSize(1)
            assertThat(results[0].meme.id).isEqualTo(1)
        }

    @Test
    fun `searchSemantic returns empty when orchestrator returns no results`() =
        runTest {
            coEvery { searchOrchestrator.search("test", 20, any()) } returns emptyList()

            val results = repository.searchSemantic("test", 20)

            assertThat(results).isEmpty()
        }

    @Test
    fun `searchSemantic delegates to orchestrator with correct search mode`() =
        runTest {
            coEvery { searchOrchestrator.search("test", 20, any()) } returns emptyList()

            repository.searchSemantic("test", 20)

            coVerify { searchOrchestrator.search("test", 20, any()) }
        }

    @Test
    fun `searchSemantic returns multiple results from orchestrator`() =
        runTest {
            val results = listOf(
                SearchResult(
                    meme = createTestMemeEntity(1, "meme1.jpg").toDomainMeme(),
                    relevanceScore = 0.9f,
                    matchType = MatchType.SEMANTIC,
                ),
                SearchResult(
                    meme = createTestMemeEntity(2, "meme2.jpg").toDomainMeme(),
                    relevanceScore = 0.7f,
                    matchType = MatchType.SEMANTIC,
                ),
            )
            coEvery { searchOrchestrator.search("test", 20, any()) } returns results

            val actual = repository.searchSemantic("test", 20)

            assertThat(actual).hasSize(2)
            assertThat(actual[0].relevanceScore).isGreaterThan(actual[1].relevanceScore)
        }

    @Test
    fun `searchSemantic preserves result order from orchestrator`() =
        runTest {
            val results = listOf(
                SearchResult(
                    meme = createTestMemeEntity(1, "first.jpg").toDomainMeme(),
                    relevanceScore = 0.95f,
                    matchType = MatchType.SEMANTIC,
                ),
                SearchResult(
                    meme = createTestMemeEntity(2, "second.jpg").toDomainMeme(),
                    relevanceScore = 0.5f,
                    matchType = MatchType.SEMANTIC,
                ),
            )
            coEvery { searchOrchestrator.search("test", 20, any()) } returns results

            val actual = repository.searchSemantic("test", 20)

            assertThat(actual).hasSize(2)
            assertThat(actual[0].meme.id).isEqualTo(1)
            assertThat(actual[1].meme.id).isEqualTo(2)
        }

    // endregion

    // region searchHybrid Tests

    @Test
    fun `searchHybrid returns empty list for blank query`() =
        runTest {
            val results = repository.searchHybrid("")
            assertThat(results).isEmpty()
        }

    @Test
    fun `searchHybrid delegates to search orchestrator`() =
        runTest {
            val orchestratorResults =
                testMemeEntities.map { entity ->
                    SearchResult(
                        meme = entity.toDomainMeme(),
                        relevanceScore = 0.9f,
                        matchType = MatchType.HYBRID,
                    )
                }
            coEvery { searchOrchestrator.search("test", 20) } returns orchestratorResults

            val results = repository.searchHybrid("test", 20)

            assertThat(results).hasSize(3)
            coVerify { searchOrchestrator.search("test", 20) }
        }

    @Test
    fun `searchHybrid returns orchestrator results when semantic search disabled`() =
        runTest {
            val disabledPrefs = defaultPreferences.copy(enableSemanticSearch = false)
            every { preferencesDataStore.appPreferences } returns flowOf(disabledPrefs)

            repository =
                SearchRepositoryImpl(
                    memeDao = memeDao,
                    memeSearchDao = memeSearchDao,
                    emojiTagDao = emojiTagDao,
                    searchOrchestrator = searchOrchestrator,
                    preferencesDataStore = preferencesDataStore,
                )

            val orchestratorResults =
                testMemeEntities.map { entity ->
                    SearchResult(
                        meme = entity.toDomainMeme(),
                        relevanceScore = 0.7f,
                        matchType = MatchType.TEXT,
                    )
                }
            coEvery { searchOrchestrator.search("test", 20) } returns orchestratorResults

            val results = repository.searchHybrid("test", 20)

            assertThat(results).hasSize(3)
            coVerify { searchOrchestrator.search("test", 20) }
        }

    @Test
    fun `searchHybrid respects limit parameter`() =
        runTest {
            val manyResults =
                (1..10).map {
                    SearchResult(
                        meme = createTestMemeEntity(it.toLong(), "meme$it.jpg").toDomainMeme(),
                        relevanceScore = 0.5f,
                        matchType = MatchType.HYBRID,
                    )
                }
            coEvery { searchOrchestrator.search("test", 10) } returns manyResults

            val results = repository.searchHybrid("test", 10)

            assertThat(results).hasSize(10)
            coVerify { searchOrchestrator.search("test", 10) }
        }

    @Test
    fun `searchHybrid returns results with HYBRID match type from orchestrator`() =
        runTest {
            val hybridResult =
                listOf(
                    SearchResult(
                        meme = testMemeEntities[0].toDomainMeme(),
                        relevanceScore = 0.9f,
                        matchType = MatchType.HYBRID,
                    ),
                )
            coEvery { searchOrchestrator.search("test", 20) } returns hybridResult

            val results = repository.searchHybrid("test", 20)

            assertThat(results).hasSize(1)
            assertThat(results[0].matchType).isEqualTo(MatchType.HYBRID)
        }

    @Test
    fun `searchHybrid deduplicates when orchestrator returns duplicate meme IDs`() =
        runTest {
            val duplicateEntity = createTestMemeEntity(1, "meme1.jpg", title = "Duplicate meme")
            val orchestratorResults =
                listOf(
                    SearchResult(
                        meme = duplicateEntity.toDomainMeme(),
                        relevanceScore = 0.9f,
                        matchType = MatchType.HYBRID,
                    ),
                )
            coEvery { searchOrchestrator.search("test", 20, any()) } returns orchestratorResults

            val results = repository.searchHybrid("test", 20)

            assertThat(results).hasSize(1)
            assertThat(results[0].meme.id).isEqualTo(1)
        }

    @Test
    fun `searchHybrid combined score is greater than individual FTS score for overlapping result`() =
        runTest {
            val overlappingEntity = createTestMemeEntity(1, "test.jpg", title = "test meme")

            val ftsOnlyResult =
                listOf(
                    SearchResult(
                        meme = overlappingEntity.toDomainMeme(),
                        relevanceScore = 0.5f,
                        matchType = MatchType.TEXT,
                    ),
                )
            val hybridResult =
                listOf(
                    SearchResult(
                        meme = overlappingEntity.toDomainMeme(),
                        relevanceScore = 0.9f,
                        matchType = MatchType.HYBRID,
                    ),
                )

            // First call returns FTS-only, second call returns hybrid
            coEvery { searchOrchestrator.search("test", 20, any()) } returnsMany listOf(ftsOnlyResult, hybridResult)

            val ftsOnlyResults = repository.searchHybrid("test", 20)
            val ftsOnlyScore = ftsOnlyResults.first().relevanceScore

            val hybridResults = repository.searchHybrid("test", 20)
            val combinedScore = hybridResults.first().relevanceScore

            assertThat(combinedScore).isGreaterThan(ftsOnlyScore)
            assertThat(hybridResults.first().matchType).isEqualTo(MatchType.HYBRID)
        }

    // endregion

    // region searchByEmoji Tests

    @Test
    fun `searchByEmoji returns flow of emoji search results`() =
        runTest {
            val emojiEntities = listOf(createTestMemeEntity(1, "emoji.jpg", emojiTagsJson = "😂"))
            every { memeSearchDao.searchByEmoji(any()) } returns flowOf(emojiEntities)

            repository.searchByEmoji("😂").test {
                val results = awaitItem()
                assertThat(results).hasSize(1)
                assertThat(results[0].matchType).isEqualTo(MatchType.EMOJI)
                awaitComplete()
            }
        }

    // endregion

    // region getSearchSuggestions Tests

    @Test
    fun `getSearchSuggestions returns empty list for blank prefix`() =
        runTest {
            val result = repository.getSearchSuggestions("")
            assertThat(result).isEmpty()
        }

    @Test
    fun `getSearchSuggestions returns suggestions from dao`() =
        runTest {
            coEvery { memeSearchDao.getSearchSuggestions("fun") } returns suggestions
            coEvery { memeSearchDao.getDescriptionSuggestions("fun") } returns emptyList()

            val result = repository.getSearchSuggestions("fun")

            assertThat(result).isEqualTo(suggestions)
            coVerify { memeSearchDao.getSearchSuggestions("fun") }
        }

    @Test
    fun `getSearchSuggestions extracts phrase when prefix at start of description`() =
        runTest {
            coEvery { memeSearchDao.getSearchSuggestions("hello") } returns emptyList()
            coEvery { memeSearchDao.getDescriptionSuggestions("hello") } returns
                listOf("hello world this is a test")

            val result = repository.getSearchSuggestions("hello")

            assertThat(result).hasSize(1)
            assertThat(result[0]).startsWith("hello")
        }

    @Test
    fun `getSearchSuggestions extracts phrase when prefix at end of description`() =
        runTest {
            coEvery { memeSearchDao.getSearchSuggestions("end") } returns emptyList()
            coEvery { memeSearchDao.getDescriptionSuggestions("end") } returns
                listOf("this is the end")

            val result = repository.getSearchSuggestions("end")

            assertThat(result).hasSize(1)
            assertThat(result[0]).contains("end")
        }

    @Test
    fun `getSearchSuggestions returns snippet when prefix not found in description`() =
        runTest {
            coEvery { memeSearchDao.getSearchSuggestions("xyz") } returns emptyList()
            coEvery { memeSearchDao.getDescriptionSuggestions("xyz") } returns
                listOf("A long description that does not contain the search prefix at all and keeps going")

            val result = repository.getSearchSuggestions("xyz")

            assertThat(result).hasSize(1)
            // DESCRIPTION_SNIPPET_LENGTH is 50, so fallback truncates to first 50 chars
            assertThat(result[0].length).isAtMost(50)
        }

    @Test
    fun `getSearchSuggestions handles very short description`() =
        runTest {
            coEvery { memeSearchDao.getSearchSuggestions("hi") } returns emptyList()
            coEvery { memeSearchDao.getDescriptionSuggestions("hi") } returns listOf("hi")

            val result = repository.getSearchSuggestions("hi")

            assertThat(result).hasSize(1)
            assertThat(result[0]).isEqualTo("hi")
        }

    // endregion

    // region getRecentSearches Tests

    @Test
    fun `getRecentSearches returns flow from preferences datastore`() =
        runTest {
            repository.getRecentSearches().test {
                val searches = awaitItem()
                assertThat(searches).isEqualTo(recentSearches)
                awaitComplete()
            }

            verify { preferencesDataStore.recentSearches }
        }

    // endregion

    // region addRecentSearch Tests

    @Test
    fun `addRecentSearch does nothing for blank query`() =
        runTest {
            repository.addRecentSearch("")

            coVerify(exactly = 0) { preferencesDataStore.addRecentSearch(any()) }
        }

    @Test
    fun `addRecentSearch trims and adds to datastore`() =
        runTest {
            coEvery { preferencesDataStore.addRecentSearch("test") } just Runs

            repository.addRecentSearch("  test  ")

            coVerify { preferencesDataStore.addRecentSearch("test") }
        }

    // endregion

    // region clearRecentSearches Tests

    @Test
    fun `clearRecentSearches clears datastore`() =
        runTest {
            coEvery { preferencesDataStore.clearRecentSearches() } just Runs

            repository.clearRecentSearches()

            coVerify { preferencesDataStore.clearRecentSearches() }
        }

    // endregion

    // region getFavoriteMemes Tests

    @Test
    fun `getFavoriteMemes returns flow of favorite memes as search results`() =
        runTest {
            val favoriteEntities =
                listOf(
                    createTestMemeEntity(1, "fav1.jpg", isFavorite = true),
                    createTestMemeEntity(2, "fav2.jpg", isFavorite = true),
                )
            every { memeDao.getFavoriteMemes() } returns flowOf(favoriteEntities)

            repository.getFavoriteMemes().test {
                val results = awaitItem()
                assertThat(results).hasSize(2)
                assertThat(results[0].relevanceScore).isGreaterThan(0f)
                awaitComplete()
            }

            verify { memeDao.getFavoriteMemes() }
        }

    // endregion

    // region getRecentMemes Tests

    @Test
    fun `getRecentMemes returns flow of recently viewed memes as search results`() =
        runTest {
            val recentEntities =
                listOf(
                    createTestMemeEntity(3, "recent1.jpg"),
                    createTestMemeEntity(4, "recent2.jpg"),
                )
            every { memeDao.getRecentlyViewedMemes(any()) } returns flowOf(recentEntities)

            repository.getRecentMemes().test {
                val results = awaitItem()
                assertThat(results).hasSize(2)
                assertThat(results[0].relevanceScore).isGreaterThan(0f)
                awaitComplete()
            }

            verify { memeDao.getRecentlyViewedMemes(any()) }
        }

    // endregion

    // region Favorite Prioritization Tests

    @Test
    fun `searchMemes prioritizes favorited memes above threshold`() =
        runTest {
            // Field-based scoring with query "test":
            // id=1 (title "test normal"): 0.5 + 0.3 = 0.8
            // id=2 (title "test favorite", favorite): 0.5 + 0.3 = 0.8 (≥0.5 threshold, will be boosted)
            // id=3 (no matching fields): 0.5
            val entities =
                listOf(
                    createTestMemeEntity(1, "meme1.jpg", title = "test normal"),
                    createTestMemeEntity(2, "meme2.jpg", title = "test favorite", isFavorite = true),
                    createTestMemeEntity(3, "meme3.jpg"),
                )
            every { memeSearchDao.searchMemes(any()) } returns flowOf(entities)

            repository.searchMemes("test").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                // Favorited meme (id=2) should appear first
                assertThat(results[0].meme.id).isEqualTo(2)
                assertThat(results[0].meme.isFavorite).isTrue()
                awaitComplete()
            }
        }

    @Test
    fun `searchMemes prioritizes all favorites since FTS scores are above threshold`() =
        runTest {
            // With field-based scoring, all entities with title "test content"
            // get score = 0.5 + 0.3 = 0.8, which meets FAVORITE_BOOST_THRESHOLD
            val entities =
                (1..60).map { i ->
                    createTestMemeEntity(
                        id = i.toLong(),
                        fileName = "meme$i.jpg",
                        title = "test content",
                        isFavorite = i == 60,
                    )
                }
            every { memeSearchDao.searchMemes(any()) } returns flowOf(entities)

            repository.searchMemes("test").test {
                val results = awaitItem()
                assertThat(results[0].meme.id).isEqualTo(60)
                assertThat(results[0].meme.isFavorite).isTrue()
                awaitComplete()
            }
        }

    @Test
    fun `searchHybrid does not prioritize favorites with low relevance score`() =
        runTest {
            // The orchestrator handles scoring and favorite boosting internally.
            // When a favorite has low relevance, it should not be boosted to the top.
            val orchestratorResults =
                (1..60).map { i ->
                    SearchResult(
                        meme = createTestMemeEntity(
                            id = i.toLong(),
                            fileName = "meme$i.jpg",
                            title = if (i == 60) "unrelated content" else "test content",
                            isFavorite = i == 60,
                        ).toDomainMeme(),
                        relevanceScore = if (i == 60) 0.1f else 0.8f,
                        matchType = MatchType.HYBRID,
                    )
                }
            coEvery { searchOrchestrator.search("test", 100) } returns orchestratorResults

            val results = repository.searchHybrid("test", 100)

            // The favorite (id=60) has low relevance and should NOT be first
            assertThat(results[0].meme.id).isNotEqualTo(60)
        }

    @Test
    fun `searchByEmoji prioritizes favorited memes above threshold`() =
        runTest {
            val entities =
                listOf(
                    createTestMemeEntity(1, "meme1.jpg", emojiTagsJson = "😂"),
                    createTestMemeEntity(2, "meme2.jpg", emojiTagsJson = "😂", isFavorite = true),
                    createTestMemeEntity(3, "meme3.jpg", emojiTagsJson = "😂"),
                )
            every { memeSearchDao.searchByEmoji(any()) } returns flowOf(entities)

            repository.searchByEmoji("😂").test {
                val results = awaitItem()
                assertThat(results).hasSize(3)
                assertThat(results[0].meme.id).isEqualTo(2)
                assertThat(results[0].meme.isFavorite).isTrue()
                awaitComplete()
            }
        }

    @Test
    fun `searchHybrid prioritizes favorited memes above threshold`() =
        runTest {
            // The orchestrator returns results with favorites ranked higher
            val orchestratorResults =
                listOf(
                    SearchResult(
                        meme = createTestMemeEntity(2, "favorite.jpg", title = "test", description = "test desc", isFavorite = true).toDomainMeme(),
                        relevanceScore = 0.95f,
                        matchType = MatchType.HYBRID,
                    ),
                    SearchResult(
                        meme = createTestMemeEntity(1, "normal.jpg", title = "test", description = "test desc").toDomainMeme(),
                        relevanceScore = 0.85f,
                        matchType = MatchType.HYBRID,
                    ),
                )
            coEvery { searchOrchestrator.search("test", 20) } returns orchestratorResults

            val results = repository.searchHybrid("test", 20)

            assertThat(results[0].meme.id).isEqualTo(2)
            assertThat(results[0].meme.isFavorite).isTrue()
        }

    @Test
    fun `favorite prioritization preserves relative order within favorites and non-favorites`()=
        runTest {
            // Field-based scoring with query "test":
            // id=1 (title "test first"): 0.5 + 0.3 = 0.8 (non-favorite)
            // id=2 (title "test fav2", desc "test desc"): 0.5 + 0.3 + 0.15 = 0.95 (favorite, ≥0.5)
            // id=3 (no match): 0.5 (non-favorite)
            // id=4 (title "test fav1"): 0.5 + 0.3 = 0.8 (favorite, ≥0.5)
            val entities =
                listOf(
                    createTestMemeEntity(1, "meme1.jpg", title = "test first"),
                    createTestMemeEntity(2, "meme2.jpg", title = "test fav2", description = "test desc", isFavorite = true),
                    createTestMemeEntity(3, "meme3.jpg"),
                    createTestMemeEntity(4, "meme4.jpg", title = "test fav1", isFavorite = true),
                )
            every { memeSearchDao.searchMemes(any()) } returns flowOf(entities)

            repository.searchMemes("test").test {
                val results = awaitItem()
                assertThat(results).hasSize(4)
                // Favorites first (in their original relevance order: id=2 at 0.95, id=4 at 0.8)
                assertThat(results[0].meme.id).isEqualTo(2)
                assertThat(results[1].meme.id).isEqualTo(4)
                // Then non-favorites (id=1 at 0.8, id=3 at 0.5)
                assertThat(results[2].meme.id).isEqualTo(1)
                assertThat(results[3].meme.id).isEqualTo(3)
                awaitComplete()
            }
        }

    // endregion

    // region Scoring Weights and Priorities Tests

    @Test
    fun `title match scores higher than description match for same query`() =
        runTest {
            // title match: 0.5 + 0.3 = 0.8
            // description match: 0.5 + 0.15 = 0.65
            val entities =
                listOf(
                    createTestMemeEntity(1, "title.jpg", title = "unique_query"),
                    createTestMemeEntity(2, "desc.jpg", description = "unique_query"),
                )
            every { memeSearchDao.searchMemes(any()) } returns flowOf(entities)

            repository.searchMemes("unique_query").test {
                val results = awaitItem()
                assertThat(results).hasSize(2)
                assertThat(results[0].meme.id).isEqualTo(1)
                assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
                awaitComplete()
            }
        }

    @Test
    fun `description match scores higher than emoji match for same query`() =
        runTest {
            // description match: 0.5 + 0.15 = 0.65
            // emoji match: 0.5 + 0.1 = 0.6
            val entities =
                listOf(
                    createTestMemeEntity(1, "desc.jpg", description = "smiley"),
                    createTestMemeEntity(2, "emoji.jpg", emojiTagsJson = "smiley"),
                )
            every { memeSearchDao.searchMemes(any()) } returns flowOf(entities)

            repository.searchMemes("smiley").test {
                val results = awaitItem()
                assertThat(results).hasSize(2)
                assertThat(results[0].meme.id).isEqualTo(1)
                assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
                awaitComplete()
            }
        }

    @Test
    fun `favorite meme with sufficient score is boosted above non-favorite`() =
        runTest {
            // Both have description match only: score = 0.5 + 0.15 = 0.65, above FAVORITE_BOOST_THRESHOLD (0.5)
            // Favorite should be boosted to appear first despite same score
            val entities =
                listOf(
                    createTestMemeEntity(1, "normal.jpg", description = "keyword match"),
                    createTestMemeEntity(2, "fav.jpg", description = "keyword match", isFavorite = true),
                )
            every { memeSearchDao.searchMemes(any()) } returns flowOf(entities)

            repository.searchMemes("keyword").test {
                val results = awaitItem()
                assertThat(results).hasSize(2)
                assertThat(results[0].meme.id).isEqualTo(2)
                assertThat(results[0].meme.isFavorite).isTrue()
                assertThat(results[1].meme.id).isEqualTo(1)
                assertThat(results[1].meme.isFavorite).isFalse()
                awaitComplete()
            }
        }

    @Test
    fun `FTS results are weighted higher than semantic results in hybrid search`() =
        runTest {
            // Orchestrator returns pre-ranked results with FTS ranked higher
            val orchestratorResults =
                listOf(
                    SearchResult(
                        meme = createTestMemeEntity(1, "fts.jpg", title = "testquery").toDomainMeme(),
                        relevanceScore = 0.48f,
                        matchType = MatchType.TEXT,
                    ),
                    SearchResult(
                        meme = createTestMemeEntity(2, "semantic.jpg").toDomainMeme(),
                        relevanceScore = 0.32f,
                        matchType = MatchType.SEMANTIC,
                    ),
                )
            coEvery { searchOrchestrator.search("testquery", 20, any()) } returns orchestratorResults

            val results = repository.searchHybrid("testquery", 20)

            assertThat(results).hasSize(2)
            assertThat(results[0].meme.id).isEqualTo(1)
            assertThat(results[0].relevanceScore).isGreaterThan(results[1].relevanceScore)
            assertThat(results[1].meme.id).isEqualTo(2)
        }

    // endregion

    // region Orchestrator Error Propagation Tests

    @Test
    fun `searchSemantic propagates orchestrator exceptions`() =
        runTest {
            coEvery { searchOrchestrator.search("test", 20) } throws RuntimeException("Orchestrator failed")

            var caughtError: Throwable? = null
            try {
                repository.searchSemantic("test", 20)
            } catch (e: RuntimeException) {
                caughtError = e
            }

            assertThat(caughtError).isInstanceOf(RuntimeException::class.java)
            assertThat(caughtError!!.message).isEqualTo("Orchestrator failed")
        }

    @Test
    fun `searchHybrid propagates orchestrator exceptions`() =
        runTest {
            coEvery { searchOrchestrator.search("test", 20) } throws RuntimeException("Orchestrator failed")

            var caughtError: Throwable? = null
            try {
                repository.searchHybrid("test", 20)
            } catch (e: RuntimeException) {
                caughtError = e
            }

            assertThat(caughtError).isInstanceOf(RuntimeException::class.java)
            assertThat(caughtError!!.message).isEqualTo("Orchestrator failed")
        }

    // endregion

    // region getEmojiCounts Tests

    @Test
    fun `getEmojiCounts returns usage-ordered emojis from dao`() =
        runTest {
            val daoResult = listOf(
                EmojiUsageBySharing("🔥", "fire", 30),
                EmojiUsageBySharing("😂", "face_with_tears_of_joy", 15),
                EmojiUsageBySharing("❤️", "red_heart", 5),
            )
            every { emojiTagDao.getEmojisOrderedByUsage() } returns flowOf(daoResult)

            repository.getEmojiCounts().test {
                val result = awaitItem()
                assertThat(result).hasSize(3)
                assertThat(result[0]).isEqualTo("🔥" to 30)
                assertThat(result[1]).isEqualTo("😂" to 15)
                assertThat(result[2]).isEqualTo("❤️" to 5)
                awaitComplete()
            }

            verify { emojiTagDao.getEmojisOrderedByUsage() }
        }

    @Test
    fun `getEmojiCounts returns empty list when no emojis exist`() =
        runTest {
            every { emojiTagDao.getEmojisOrderedByUsage() } returns flowOf(emptyList())

            repository.getEmojiCounts().test {
                val result = awaitItem()
                assertThat(result).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `getEmojiCounts maps totalUsage not tag count`() =
        runTest {
            // Verify we get totalUsage (share count), not plain count
            val daoResult = listOf(
                EmojiUsageBySharing("🔥", "fire", 100),
            )
            every { emojiTagDao.getEmojisOrderedByUsage() } returns flowOf(daoResult)

            repository.getEmojiCounts().test {
                val result = awaitItem()
                assertThat(result[0].second).isEqualTo(100)
                awaitComplete()
            }
        }

    @Test
    fun `getEmojiCounts preserves usage-based ordering from dao`() =
        runTest {
            // The DAO returns usage-ordered: highest first
            val daoResult = listOf(
                EmojiUsageBySharing("🎉", "party_popper", 50),
                EmojiUsageBySharing("🔥", "fire", 25),
                EmojiUsageBySharing("😂", "face_with_tears_of_joy", 10),
            )
            every { emojiTagDao.getEmojisOrderedByUsage() } returns flowOf(daoResult)

            repository.getEmojiCounts().test {
                val result = awaitItem()
                // Order should be preserved: highest usage first
                assertThat(result.map { it.first }).containsExactly("🎉", "🔥", "😂").inOrder()
                awaitComplete()
            }
        }

    // endregion

    // region Deduplication Tests

    @Test
    fun `searchMemes deduplicates results with same meme id`() =
        runTest {
            val duplicateEntities =
                listOf(
                    createTestMemeEntity(1, "meme1.jpg", title = "Funny cat"),
                    createTestMemeEntity(1, "meme1.jpg", title = "Funny cat"),
                    createTestMemeEntity(2, "meme2.jpg", description = "Dog meme"),
                    createTestMemeEntity(2, "meme2.jpg", description = "Dog meme"),
                    createTestMemeEntity(3, "meme3.jpg"),
                )
            every { memeSearchDao.searchMemes(any()) } returns flowOf(duplicateEntities)

            repository.searchMemes("funny").test {
                val results = awaitItem()
                assertThat(results.map { it.meme.id }).containsNoDuplicates()
                assertThat(results).hasSize(3)
                awaitComplete()
            }
        }

    @Test
    fun `searchByEmoji deduplicates results with same meme id`() =
        runTest {
            val duplicateEntities =
                listOf(
                    createTestMemeEntity(1, "meme1.jpg", emojiTagsJson = "😂"),
                    createTestMemeEntity(1, "meme1.jpg", emojiTagsJson = "😂"),
                    createTestMemeEntity(2, "meme2.jpg", emojiTagsJson = "😂"),
                )
            every { memeSearchDao.searchByEmoji(any()) } returns flowOf(duplicateEntities)

            repository.searchByEmoji("😂").test {
                val results = awaitItem()
                assertThat(results.map { it.meme.id }).containsNoDuplicates()
                assertThat(results).hasSize(2)
                awaitComplete()
            }
        }

    @Test
    fun `getAllMemes deduplicates memes with same id`() =
        runTest {
            val duplicateEntities =
                listOf(
                    createTestMemeEntity(1, "meme1.jpg"),
                    createTestMemeEntity(1, "meme1.jpg"),
                    createTestMemeEntity(2, "meme2.jpg"),
                    createTestMemeEntity(2, "meme2.jpg"),
                    createTestMemeEntity(2, "meme2.jpg"),
                )
            every { memeDao.getAllMemes() } returns flowOf(duplicateEntities)

            repository.getAllMemes().test {
                val results = awaitItem()
                assertThat(results.map { it.id }).containsNoDuplicates()
                assertThat(results).hasSize(2)
                awaitComplete()
            }
        }

    @Test
    fun `getFavoriteMemes deduplicates results with same meme id`() =
        runTest {
            val duplicateEntities =
                listOf(
                    createTestMemeEntity(1, "fav1.jpg", isFavorite = true),
                    createTestMemeEntity(1, "fav1.jpg", isFavorite = true),
                    createTestMemeEntity(2, "fav2.jpg", isFavorite = true),
                )
            every { memeDao.getFavoriteMemes() } returns flowOf(duplicateEntities)

            repository.getFavoriteMemes().test {
                val results = awaitItem()
                assertThat(results.map { it.meme.id }).containsNoDuplicates()
                assertThat(results).hasSize(2)
                awaitComplete()
            }
        }

    @Test
    fun `getRecentMemes deduplicates results with same meme id`() =
        runTest {
            val duplicateEntities =
                listOf(
                    createTestMemeEntity(3, "recent1.jpg"),
                    createTestMemeEntity(3, "recent1.jpg"),
                    createTestMemeEntity(4, "recent2.jpg"),
                    createTestMemeEntity(4, "recent2.jpg"),
                )
            every { memeDao.getRecentlyViewedMemes(any()) } returns flowOf(duplicateEntities)

            repository.getRecentMemes().test {
                val results = awaitItem()
                assertThat(results.map { it.meme.id }).containsNoDuplicates()
                assertThat(results).hasSize(2)
                awaitComplete()
            }
        }

    // endregion

    // region Helper Functions

    private fun createTestMemeEntity(
        id: Long,
        fileName: String,
        title: String? = null,
        description: String? = null,
        emojiTagsJson: String = "",
        textContent: String? = null,
        embedding: ByteArray? = null,
        isFavorite: Boolean = false,
    ): MemeEntity {
        return MemeEntity(
            id = id,
            filePath = "/test/path/$fileName",
            fileName = fileName,
            mimeType = "image/jpeg",
            width = 1920,
            height = 1080,
            fileSizeBytes = 1024L,
            importedAt = System.currentTimeMillis(),
            title = title,
            description = description,
            emojiTagsJson = emojiTagsJson,
            textContent = textContent,
            embedding = embedding,
            isFavorite = isFavorite,
        )
    }

    private fun MemeEntity.toDomainMeme(): Meme {
        return Meme(
            id = id,
            filePath = filePath,
            fileName = fileName,
            mimeType = mimeType,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            importedAt = importedAt,
            title = title,
            description = description,
            emojiTags =
                emojiTagsJson.split(",")
                    .filter { it.isNotEmpty() }
                    .map { com.adsamcik.riposte.core.model.EmojiTag.fromEmoji(it.trim()) },
            textContent = textContent,
            isFavorite = isFavorite,
            createdAt = createdAt,
            useCount = useCount,
        )
    }

    // endregion
}
