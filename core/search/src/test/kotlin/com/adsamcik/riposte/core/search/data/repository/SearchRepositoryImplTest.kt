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
